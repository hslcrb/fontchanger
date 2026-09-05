package com.openfont.changer.engine

import com.openfont.changer.data.model.FontMetadata
import java.io.File
import java.io.RandomAccessFile
import java.nio.charset.Charset

/**
 * Pure Kotlin TrueType/OpenType Font Header & 'name' Table Parser
 * TTF/OTF 파일의 내부 테이블(name table)을 파싱하여 패밀리 이름, 스타일(Weight), 풀 네임을 추출합니다.
 */
object FontParser {

    fun parse(file: File): FontMetadata {
        if (!file.exists() || file.length() < 12) {
            return FontMetadata(familyName = file.nameWithoutExtension, filePath = file.absolutePath)
        }

        var familyName = file.nameWithoutExtension
        var styleName = "Regular"
        var fullName = file.nameWithoutExtension
        var isVariable = false

        try {
            RandomAccessFile(file, "r").use { raf ->
                // Check Sfnt version
                val sfntVersion = raf.readInt()
                val numTables = raf.readUnsignedShort()
                raf.skipBytes(6) // searchRange, entrySelector, rangeShift

                var nameTableOffset = 0L
                var fvarTableFound = false

                for (i in 0 until numTables) {
                    val tag = raf.readInt()
                    val checkSum = raf.readInt()
                    val offset = raf.readInt().toLong() and 0xFFFFFFFFL
                    val length = raf.readInt().toLong() and 0xFFFFFFFFL

                    // 'name' table tag = 0x6E616D65
                    if (tag == 0x6E616D65) {
                        nameTableOffset = offset
                    }
                    // 'fvar' table tag = 0x66766172 (가변 폰트 여부 확인)
                    if (tag == 0x66766172) {
                        fvarTableFound = true
                    }
                }

                isVariable = fvarTableFound

                if (nameTableOffset > 0) {
                    raf.seek(nameTableOffset)
                    val format = raf.readUnsignedShort()
                    val count = raf.readUnsignedShort()
                    val stringOffset = raf.readUnsignedShort()

                    val storageStart = nameTableOffset + stringOffset
                    val nameRecords = mutableListOf<NameRecord>()

                    for (i in 0 until count) {
                        val platformId = raf.readUnsignedShort()
                        val encodingId = raf.readUnsignedShort()
                        val languageId = raf.readUnsignedShort()
                        val nameId = raf.readUnsignedShort()
                        val length = raf.readUnsignedShort()
                        val offset = raf.readUnsignedShort()

                        nameRecords.add(
                            NameRecord(platformId, encodingId, languageId, nameId, length, offset)
                        )
                    }

                    // Name ID 1: Font Family
                    // Name ID 2: Font Subfamily (Style)
                    // Name ID 4: Full Font Name
                    for (record in nameRecords) {
                        if (record.nameId in listOf(1, 2, 4)) {
                            raf.seek(storageStart + record.offset)
                            val bytes = ByteArray(record.length)
                            raf.readFully(bytes)

                            val charset = when (record.platformId) {
                                0 -> Charsets.UTF_16BE // Unicode
                                3 -> Charsets.UTF_16BE // Windows
                                else -> Charsets.ISO_8859_1
                            }
                            val str = String(bytes, charset).trim().replace("\u0000", "")

                            if (str.isNotBlank()) {
                                when (record.nameId) {
                                    1 -> if (familyName == file.nameWithoutExtension || record.platformId == 3) familyName = str
                                    2 -> if (styleName == "Regular" || record.platformId == 3) styleName = str
                                    4 -> if (fullName == file.nameWithoutExtension || record.platformId == 3) fullName = str
                                }
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        return FontMetadata(
            familyName = familyName,
            styleName = styleName,
            fullName = fullName,
            isVariable = isVariable,
            fileSize = file.length(),
            filePath = file.absolutePath
        )
    }

    private data class NameRecord(
        val platformId: Int,
        val encodingId: Int,
        val languageId: Int,
        val nameId: Int,
        val length: Int,
        val offset: Int
    )
}

package com.openfont.changer.engine

import android.content.Context
import com.openfont.changer.data.model.FontMetadata
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Magisk, KernelSU, APatch 환경에서 플래시 가능한 Systemless 폰트 모듈 ZIP 생성 엔진
 */
object MagiskModuleGenerator {

    fun generateModule(
        context: Context,
        sourceFontFile: File,
        fontMeta: FontMetadata,
        outputZipFile: File,
        includeKoreanCJK: Boolean = true,
        includeEmoji: Boolean = false
    ): Result<File> {
        return try {
            outputZipFile.parentFile?.mkdirs()
            if (outputZipFile.exists()) outputZipFile.delete()

            val fontBytes = sourceFontFile.readBytes()

            ZipOutputStream(FileOutputStream(outputZipFile)).use { zos ->
                // 1. module.prop
                val moduleProp = """
                    id=fontcraft_${sanitizeId(fontMeta.familyName)}
                    name=FontCraft - ${fontMeta.fullName}
                    version=v1.0.0
                    versionCode=1
                    author=FontCraft Community
                    description=Systemless custom font replacement for Android. Font: ${fontMeta.fullName}
                """.trimIndent()
                writeZipEntry(zos, "module.prop", moduleProp.toByteArray(Charsets.UTF_8))

                // 2. META-INF/com/google/android/update-binary & updater-script (Magisk Installer template)
                val updateBinary = """
                    #!/sbin/sh
                    O3X=$1; O3Y=$2; O3Z=$3
                    [ -z "${"$"}{MAGISKTMP}" ] && MAGISKTMP=/sbin/.magisk
                    exec sh ${"$"}{MAGISKTMP}/busybox/magisk --install-module "${"$"}{O3Z}"
                """.trimIndent()
                writeZipEntry(zos, "META-INF/com/google/android/update-binary", updateBinary.toByteArray(Charsets.UTF_8))
                writeZipEntry(zos, "META-INF/com/google/android/updater-script", "#MAGISK\n".toByteArray(Charsets.UTF_8))

                // 3. customize.sh (권한 세팅)
                val customizeSh = """
                    ui_print "************************************"
                    ui_print "  FontCraft - Custom Font Installer "
                    ui_print "  Font: ${fontMeta.fullName}"
                    ui_print "************************************"
                    set_perm_recursive ${"$"}{MODPATH}/system/fonts 0 0 0755 0644
                    set_perm ${"$"}{MODPATH}/system/etc/fonts.xml 0 0 0644
                """.trimIndent()
                writeZipEntry(zos, "customize.sh", customizeSh.toByteArray(Charsets.UTF_8))

                // 4. system/fonts/ 에 주요 시스템 폰트명으로 배치
                // AOSP Roboto 대체
                val targetFontNames = listOf(
                    "Roboto-Regular.ttf",
                    "Roboto-Medium.ttf",
                    "Roboto-Bold.ttf",
                    "Roboto-Light.ttf",
                    "Roboto-Thin.ttf"
                )
                for (name in targetFontNames) {
                    writeZipEntry(zos, "system/fonts/$name", fontBytes)
                }

                // CJK (한글 등) 대체
                if (includeKoreanCJK) {
                    val cjkNames = listOf(
                        "NotoSansCJK-Regular.ttc",
                        "NotoSansKR-Regular.otf",
                        "NotoSansKR-Medium.otf",
                        "NotoSansKR-Bold.otf",
                        "SamsungOneKorean-Regular.ttf"
                    )
                    for (name in cjkNames) {
                        writeZipEntry(zos, "system/fonts/$name", fontBytes)
                    }
                }

                // 5. system/etc/fonts.xml 오버레이 파일
                val fontsXml = generateFontsXml(fontMeta)
                writeZipEntry(zos, "system/etc/fonts.xml", fontsXml.toByteArray(Charsets.UTF_8))
            }

            Result.success(outputZipFile)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun generateFontsXml(fontMeta: FontMetadata): String {
        return """
            <?xml version="1.0" encoding="utf-8"?>
            <familyset version="23">
                <family name="sans-serif">
                    <font weight="400" style="normal">Roboto-Regular.ttf</font>
                    <font weight="500" style="normal">Roboto-Medium.ttf</font>
                    <font weight="700" style="normal">Roboto-Bold.ttf</font>
                </family>
                <family name="sans-serif-medium">
                    <font weight="500" style="normal">Roboto-Medium.ttf</font>
                </family>
                <family name="sans-serif-black">
                    <font weight="900" style="normal">Roboto-Bold.ttf</font>
                </family>
                <family lang="ko">
                    <font weight="400" style="normal">NotoSansKR-Regular.otf</font>
                    <font weight="700" style="normal">NotoSansKR-Bold.otf</font>
                </family>
            </familyset>
        """.trimIndent()
    }

    private fun writeZipEntry(zos: ZipOutputStream, entryName: String, data: ByteArray) {
        val entry = ZipEntry(entryName)
        zos.putNextEntry(entry)
        zos.write(data)
        zos.closeEntry()
    }

    private fun sanitizeId(name: String): String {
        return name.lowercase().replace(Regex("[^a-z0-9_]"), "_").trim('_')
    }
}

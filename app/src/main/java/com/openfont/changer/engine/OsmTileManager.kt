package com.openfont.changer.engine

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import kotlin.math.*

/**
 * OpenStreetMap (OSM) 표준 타일 다운로더 및 캐시 매니저
 * 외부 상용 SDK 없이 순수 OSM 타일 맵을 백그라운드 캔버스에 렌더링합니다.
 */
object OsmTileManager {

    private val memoryCache = object : LruCache<String, Bitmap>(64 * 1024 * 1024) { // 64MB
        override fun sizeOf(key: String, value: Bitmap): Int {
            return value.byteCount
        }
    }

    suspend fun getTile(context: Context, zoom: Int, x: Int, y: Int): Bitmap? = withContext(Dispatchers.IO) {
        val key = "$zoom/$x/$y"
        memoryCache.get(key)?.let { return@withContext it }

        val diskCacheDir = File(context.cacheDir, "osm_tiles/$zoom/$x")
        diskCacheDir.mkdirs()
        val diskFile = File(diskCacheDir, "$y.png")

        if (diskFile.exists() && diskFile.length() > 0) {
            val bitmap = BitmapFactory.decodeFile(diskFile.absolutePath)
            if (bitmap != null) {
                memoryCache.put(key, bitmap)
                return@withContext bitmap
            }
        }

        // 다운로드
        try {
            val tileUrl = "https://tile.openstreetmap.org/$zoom/$x/$y.png"
            val conn = URL(tileUrl).openConnection() as HttpURLConnection
            conn.setRequestProperty("User-Agent", "FontCraft-Android-App/1.1 (https://github.com/hslcrb/fontchanger)")
            conn.connectTimeout = 4000
            conn.readTimeout = 4000
            conn.connect()

            if (conn.responseCode == 200) {
                val bytes = conn.inputStream.readBytes()
                val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                if (bitmap != null) {
                    memoryCache.put(key, bitmap)
                    FileOutputStream(diskFile).use { it.write(bytes) }
                    return@withContext bitmap
                }
            }
        } catch (e: Exception) {
            // 네트워크 미연결 시 오프라인 모드로 안전 동작
        }

        null
    }

    fun lonToTileX(lon: Double, zoom: Int): Int {
        val n = 2.0.pow(zoom.toDouble())
        return floor((lon + 180.0) / 360.0 * n).toInt()
    }

    fun latToTileY(lat: Double, zoom: Int): Int {
        val n = 2.0.pow(zoom.toDouble())
        val latRad = Math.toRadians(lat)
        return floor((1.0 - asinh(tan(latRad)) / Math.PI) / 2.0 * n).toInt()
    }
}

package com.openfont.changer.engine

import android.content.Context
import com.openfont.changer.data.model.LocationPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.*

/**
 * 오프라인 환경에서도 인터넷 없이 지도를 볼 수 있도록 특정 영역의 OpenStreetMap 타일을 일괄 다운로드하는 엔진
 */
object OfflineMapDownloader {

    data class DownloadProgress(
        val totalTiles: Int,
        val downloadedTiles: Int,
        val isFinished: Boolean = false
    )

    suspend fun downloadTilesForTrack(
        context: Context,
        points: List<LocationPoint>,
        zoomLevels: List<Int> = listOf(14, 15),
        onProgress: (DownloadProgress) -> Unit
    ) = withContext(Dispatchers.IO) {
        if (points.isEmpty()) return@withContext

        val minLat = points.minOf { it.latitude }
        val maxLat = points.maxOf { it.latitude }
        val minLon = points.minOf { it.longitude }
        val maxLon = points.maxOf { it.longitude }

        val targetTiles = mutableListOf<Triple<Int, Int, Int>>() // zoom, x, y

        for (z in zoomLevels) {
            val minX = OsmTileManager.lonToTileX(minLon, z) - 1
            val maxX = OsmTileManager.lonToTileX(maxLon, z) + 1
            val minY = OsmTileManager.latToTileY(maxLat, z) - 1
            val maxY = OsmTileManager.latToTileY(minLat, z) + 1

            for (x in minX..maxX) {
                for (y in minY..maxY) {
                    targetTiles.add(Triple(z, x, y))
                }
            }
        }

        val total = targetTiles.size
        var downloaded = 0

        for (tile in targetTiles) {
            OsmTileManager.getTile(context, tile.first, tile.second, tile.third)
            downloaded++
            if (downloaded % 5 == 0 || downloaded == total) {
                onProgress(DownloadProgress(total, downloaded, downloaded == total))
            }
        }
    }
}

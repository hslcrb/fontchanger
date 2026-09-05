package com.openfont.changer.engine

import com.openfont.changer.data.model.GpkFileInfo
import com.openfont.changer.data.model.GpkTrack
import com.openfont.changer.data.model.LocationPoint
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * GPK (GeoPackage / GPS Tracker Format) 및 GPX 표준 직렬화/역직렬화 엔진
 * 구글 타임라인(Google Timeline)의 완벽한 오프라인 대용으로 위치 경로를 정확히 보존합니다.
 */
object GpkSerializer {

    private val isoFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US)
    private val displayFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.KOREA)
    private val fileDateFormat = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US)
    private val dailyFileFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)

    /**
     * 위치 포인트 리스트를 호환성이 높은 GPK / GPX XML 포맷으로 변환하여 파일에 저장
     */
    fun saveToGpkFile(
        outputFile: File,
        trackName: String,
        points: List<LocationPoint>,
        description: String = "FontCraft Google Timeline Alternative GPK Tracker"
    ): Result<File> {
        return try {
            outputFile.parentFile?.mkdirs()
            val tempFile = File(outputFile.parentFile, "${outputFile.name}.tmp")

            FileOutputStream(tempFile).bufferedWriter(Charsets.UTF_8).use { writer ->
                writer.write("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n")
                writer.write("<gpk version=\"1.1\" creator=\"FontCraft Timeline Engine\" xmlns=\"http://www.topografix.com/GPX/1/1\">\n")
                writer.write("  <metadata>\n")
                writer.write("    <name><![CDATA[$trackName]]></name>\n")
                writer.write("    <desc><![CDATA[$description]]></desc>\n")
                writer.write("    <time>${isoFormat.format(Date())}</time>\n")
                writer.write("  </metadata>\n")
                writer.write("  <trk>\n")
                writer.write("    <name><![CDATA[$trackName]]></name>\n")
                writer.write("    <trkseg>\n")

                for (p in points) {
                    val timeStr = isoFormat.format(Date(p.timestamp))
                    writer.write("      <trkpt lat=\"${p.latitude}\" lon=\"${p.longitude}\">\n")
                    writer.write("        <ele>${p.altitude}</ele>\n")
                    writer.write("        <time>$timeStr</time>\n")
                    writer.write("        <speed>${p.speed}</speed>\n")
                    writer.write("        <accuracy>${p.accuracy}</accuracy>\n")
                    writer.write("      </trkpt>\n")
                }

                writer.write("    </trkseg>\n")
                writer.write("  </trk>\n")
                writer.write("</gpk>\n")
            }

            if (outputFile.exists()) outputFile.delete()
            tempFile.renameTo(outputFile)

            Result.success(outputFile)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * 일일 자동 타임라인 GPK 파일 경로 생성 (매일 23:59 저장용)
     */
    fun getDailyGpkFile(baseDir: File, date: Date = Date()): File {
        val dailyFolder = File(baseDir, "DailyTimeline")
        dailyFolder.mkdirs()
        val filename = "daily_${dailyFileFormat.format(date)}.gpk"
        return File(dailyFolder, filename)
    }

    /**
     * 앱 실행~종료 세션 전용 GPK 파일 경로 생성
     */
    fun getSessionGpkFile(baseDir: File, startDate: Date = Date()): File {
        val sessionFolder = File(baseDir, "AppSessions")
        sessionFolder.mkdirs()
        val filename = "session_${fileDateFormat.format(startDate)}.gpk"
        return File(sessionFolder, filename)
    }

    /**
     * 저장된 모든 GPK 파일 리스트를 날짜 최신순으로 반환
     */
    fun listGpkFiles(baseDir: File): List<GpkFileInfo> {
        val results = mutableListOf<GpkFileInfo>()
        val folders = listOf(File(baseDir, "DailyTimeline"), File(baseDir, "AppSessions"))

        for (folder in folders) {
            if (folder.exists() && folder.isDirectory) {
                folder.listFiles()?.filter { it.extension == "gpk" || it.extension == "gpx" }?.forEach { file ->
                    val isSession = file.name.startsWith("session_")
                    results.add(
                        GpkFileInfo(
                            file = file,
                            name = file.name,
                            formattedDate = displayFormat.format(Date(file.lastModified())),
                            pointCount = countPointsFast(file),
                            isSession = isSession,
                            sizeBytes = file.length()
                        )
                    )
                }
            }
        }

        return results.sortedByDescending { it.file.lastModified() }
    }

    /**
     * GPK 파일로부터 전체 LocationPoint 목록 파싱 (특정 일자 타임라인 재생용)
     */
    fun loadPointsFromGpkFile(file: File): List<LocationPoint> {
        val points = mutableListOf<LocationPoint>()
        if (!file.exists()) return points

        try {
            var currentLat = 0.0
            var currentLon = 0.0
            var currentEle = 0.0
            var currentTime = 0L
            var currentSpeed = 0f
            var currentAccuracy = 0f
            var inPoint = false

            file.forEachLine { rawLine ->
                val line = rawLine.trim()
                if (line.startsWith("<trkpt")) {
                    inPoint = true
                    val latMatch = Regex("""lat="([^"]+)"""").find(line)
                    val lonMatch = Regex("""lon="([^"]+)"""").find(line)
                    currentLat = latMatch?.groupValues?.get(1)?.toDoubleOrNull() ?: 0.0
                    currentLon = lonMatch?.groupValues?.get(1)?.toDoubleOrNull() ?: 0.0
                } else if (line.startsWith("</trkpt>")) {
                    if (inPoint && (currentLat != 0.0 || currentLon != 0.0)) {
                        points.add(
                            LocationPoint(
                                latitude = currentLat,
                                longitude = currentLon,
                                altitude = currentEle,
                                accuracy = currentAccuracy,
                                speed = currentSpeed,
                                timestamp = if (currentTime != 0L) currentTime else System.currentTimeMillis()
                            )
                        )
                    }
                    inPoint = false
                } else if (inPoint) {
                    when {
                        line.startsWith("<ele>") -> {
                            currentEle = line.removeSurrounding("<ele>", "</ele>").toDoubleOrNull() ?: 0.0
                        }
                        line.startsWith("<speed>") -> {
                            currentSpeed = line.removeSurrounding("<speed>", "</speed>").toFloatOrNull() ?: 0f
                        }
                        line.startsWith("<accuracy>") -> {
                            currentAccuracy = line.removeSurrounding("<accuracy>", "</accuracy>").toFloatOrNull() ?: 0f
                        }
                        line.startsWith("<time>") -> {
                            val timeStr = line.removeSurrounding("<time>", "</time>")
                            try {
                                currentTime = isoFormat.parse(timeStr)?.time ?: 0L
                            } catch (e: Exception) {
                                currentTime = 0L
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        return points
    }

    /**
     * XML 빠른 라인 카운팅을 통한 위치 포인트 개수 산출
     */
    private fun countPointsFast(file: File): Int {
        return try {
            var count = 0
            file.forEachLine { line ->
                if (line.contains("<trkpt")) count++
            }
            count
        } catch (e: Exception) {
            0
        }
    }
}

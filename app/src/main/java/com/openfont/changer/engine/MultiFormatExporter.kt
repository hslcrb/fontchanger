package com.openfont.changer.engine

import com.openfont.changer.data.model.LocationPoint
import com.openfont.changer.data.model.TimelinePhoto
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*

/**
 * 타임라인 이동 경로 및 사진 위치를 KML, GPX, GeoJSON 등 다양한 표준 지도 포맷으로 내보내는 변환기
 */
object MultiFormatExporter {

    private val isoFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US)

    /**
     * KML (Keyhole Markup Language) 포맷으로 변환 (Google Earth, 구글 타임라인 완전 호환)
     */
    fun exportToKml(
        outputFile: File,
        trackName: String,
        points: List<LocationPoint>,
        photos: List<TimelinePhoto> = emptyList()
    ): Result<File> {
        return try {
            outputFile.parentFile?.mkdirs()
            FileOutputStream(outputFile).bufferedWriter(Charsets.UTF_8).use { writer ->
                writer.write("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n")
                writer.write("<kml xmlns=\"http://www.opengis.net/kml/2.2\">\n")
                writer.write("  <Document>\n")
                writer.write("    <name><![CDATA[$trackName]]></name>\n")
                writer.write("    <description><![CDATA[FontCraft Timeline Track & Photo Markers]]></description>\n")

                // 스타일 정의
                writer.write("    <Style id=\"trackLine\">\n")
                writer.write("      <LineStyle>\n")
                writer.write("        <color>ffd4543b</color>\n") // AABBGGRR
                writer.write("        <width>6</width>\n")
                writer.write("      </LineStyle>\n")
                writer.write("    </Style>\n")

                // 이동 경로 Placemark
                if (points.isNotEmpty()) {
                    writer.write("    <Placemark>\n")
                    writer.write("      <name>이동 경로 (Timeline Track)</name>\n")
                    writer.write("      <styleUrl>#trackLine</styleUrl>\n")
                    writer.write("      <LineString>\n")
                    writer.write("        <extrude>1</extrude>\n")
                    writer.write("        <tessellate>1</tessellate>\n")
                    writer.write("        <coordinates>\n")
                    for (p in points) {
                        writer.write("          ${p.longitude},${p.latitude},${p.altitude}\n")
                    }
                    writer.write("        </coordinates>\n")
                    writer.write("      </LineString>\n")
                    writer.write("    </Placemark>\n")
                }

                // 사진 위치 Placemark들
                for (photo in photos) {
                    writer.write("    <Placemark>\n")
                    writer.write("      <name><![CDATA[📸 ${photo.fileName}]]></name>\n")
                    writer.write("      <description><![CDATA[촬영시각: ${photo.formattedTime}]]></description>\n")
                    writer.write("      <Point>\n")
                    writer.write("        <coordinates>${photo.longitude},${photo.latitude},0</coordinates>\n")
                    writer.write("      </Point>\n")
                    writer.write("    </Placemark>\n")
                }

                writer.write("  </Document>\n")
                writer.write("</kml>\n")
            }
            Result.success(outputFile)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * GeoJSON 포맷으로 변환 (웹 GIS 및 Leaflet / Mapbox 완벽 호환)
     */
    fun exportToGeoJson(
        outputFile: File,
        trackName: String,
        points: List<LocationPoint>,
        photos: List<TimelinePhoto> = emptyList()
    ): Result<File> {
        return try {
            outputFile.parentFile?.mkdirs()
            FileOutputStream(outputFile).bufferedWriter(Charsets.UTF_8).use { writer ->
                writer.write("{\n")
                writer.write("  \"type\": \"FeatureCollection\",\n")
                writer.write("  \"name\": \"$trackName\",\n")
                writer.write("  \"features\": [\n")

                // 1. LineString Feature (이동 경로)
                if (points.isNotEmpty()) {
                    writer.write("    {\n")
                    writer.write("      \"type\": \"Feature\",\n")
                    writer.write("      \"properties\": { \"name\": \"Timeline Track\", \"pointCount\": ${points.size} },\n")
                    writer.write("      \"geometry\": {\n")
                    writer.write("        \"type\": \"LineString\",\n")
                    writer.write("        \"coordinates\": [\n")
                    for (i in points.indices) {
                        val p = points[i]
                        val comma = if (i == points.size - 1) "" else ","
                        writer.write("          [${p.longitude}, ${p.latitude}, ${p.altitude}]$comma\n")
                    }
                    writer.write("        ]\n")
                    writer.write("      }\n")
                    writer.write("    }")
                }

                // 2. Photo Points
                for (photo in photos) {
                    writer.write(",\n    {\n")
                    writer.write("      \"type\": \"Feature\",\n")
                    writer.write("      \"properties\": {\n")
                    writer.write("        \"name\": \"${photo.fileName}\",\n")
                    writer.write("        \"capturedAt\": \"${photo.formattedTime}\"\n")
                    writer.write("      },\n")
                    writer.write("      \"geometry\": {\n")
                    writer.write("        \"type\": \"Point\",\n")
                    writer.write("        \"coordinates\": [${photo.longitude}, ${photo.latitude}]\n")
                    writer.write("      }\n")
                    writer.write("    }")
                }

                writer.write("\n  ]\n")
                writer.write("}\n")
            }
            Result.success(outputFile)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}

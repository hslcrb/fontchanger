package com.openfont.changer.engine

import android.content.Context
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import com.openfont.changer.data.model.TimelinePhoto
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.InputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 이미지(사진) 파일의 EXIF 메타데이터(GPS 위경도, 촬영 일시) 파싱 엔진
 */
object PhotoExifParser {

    private val exifDateFormat = SimpleDateFormat("yyyy:MM:dd HH:mm:ss", Locale.US)
    private val displayDateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.KOREA)

    suspend fun parsePhotoExif(context: Context, uri: Uri): TimelinePhoto? = withContext(Dispatchers.IO) {
        try {
            var inputStream: InputStream? = context.contentResolver.openInputStream(uri)
            inputStream?.use { stream ->
                val exif = ExifInterface(stream)
                val latLong = FloatArray(2)
                val hasGps = exif.getLatLong(latLong)

                if (hasGps) {
                    val dateStr = exif.getAttribute(ExifInterface.TAG_DATETIME_ORIGINAL)
                        ?: exif.getAttribute(ExifInterface.TAG_DATETIME)

                    val timestamp = if (dateStr != null) {
                        try {
                            exifDateFormat.parse(dateStr)?.time ?: System.currentTimeMillis()
                        } catch (e: Exception) {
                            System.currentTimeMillis()
                        }
                    } else {
                        System.currentTimeMillis()
                    }

                    val formatted = displayDateFormat.format(Date(timestamp))

                    TimelinePhoto(
                        uriString = uri.toString(),
                        fileName = uri.lastPathSegment ?: "photo.jpg",
                        latitude = latLong[0].toDouble(),
                        longitude = latLong[1].toDouble(),
                        timestamp = timestamp,
                        formattedTime = formatted
                    )
                } else {
                    null
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}

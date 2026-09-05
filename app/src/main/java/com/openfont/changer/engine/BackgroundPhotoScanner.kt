package com.openfont.changer.engine

import android.content.ContentUris
import android.content.Context
import android.database.ContentObserver
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import com.openfont.changer.data.model.TimelinePhoto
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.atomic.AtomicBoolean

/**
 * 기기 내 사진을 백그라운드에서 병렬(Parallel)로 자동 스캔하여 
 * GPS EXIF 위치 메타데이터가 포함된 사진들을 실시간으로 색인화하는 엔진
 */
object BackgroundPhotoScanner {

    private val scannerScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val isScanning = AtomicBoolean(false)

    private val _scannedPhotos = MutableStateFlow<List<TimelinePhoto>>(emptyList())
    val scannedPhotos: StateFlow<List<TimelinePhoto>> = _scannedPhotos.asStateFlow()

    private val _scanProgress = MutableStateFlow(0)
    val scanProgress: StateFlow<Int> = _scanProgress.asStateFlow()

    private var contentObserver: ContentObserver? = null

    /**
     * 사진 저장소 감시자(ContentObserver) 및 자동 병렬 스캐너 시작
     */
    fun startAutoScanning(context: Context) {
        if (contentObserver == null) {
            contentObserver = object : ContentObserver(Handler(Looper.getMainLooper())) {
                override fun onChange(selfChange: Boolean, uri: Uri?) {
                    super.onChange(selfChange, uri)
                    // 새 사진 추가 감지 시 즉시 백그라운드 증분 스캔 트리거
                    triggerScan(context.applicationContext)
                }
            }

            try {
                context.contentResolver.registerContentObserver(
                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                    true,
                    contentObserver!!
                )
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        triggerScan(context.applicationContext)
    }

    /**
     * MediaStore 이미지 전체를 병렬 청크로 분할하여 고속 EXIF 스캔 수행
     */
    fun triggerScan(context: Context) {
        if (isScanning.getAndSet(true)) return

        scannerScope.launch {
            try {
                val imageUris = queryAllImageUris(context)
                _scanProgress.value = 0

                val existingUriSet = _scannedPhotos.value.map { it.uriString }.toSet()
                val targetUris = imageUris.filter { !existingUriSet.contains(it.toString()) }

                val results = mutableListOf<TimelinePhoto>()
                val chunkSize = 20 // 20개씩 병렬 청크 처리

                for (chunk in targetUris.chunked(chunkSize)) {
                    val deferreds = chunk.map { uri ->
                        async(Dispatchers.IO) {
                            PhotoExifParser.parsePhotoExif(context, uri)
                        }
                    }
                    val chunkResults = deferreds.awaitAll().filterNotNull()
                    results.addAll(chunkResults)

                    _scanProgress.value = _scanProgress.value + chunk.size
                    if (chunkResults.isNotEmpty()) {
                        _scannedPhotos.value = (_scannedPhotos.value + chunkResults).distinctBy { it.uriString }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                isScanning.set(false)
            }
        }
    }

    private fun queryAllImageUris(context: Context): List<Uri> {
        val uris = mutableListOf<Uri>()
        val projection = arrayOf(MediaStore.Images.Media._ID, MediaStore.Images.Media.DATE_ADDED)
        val sortOrder = "${MediaStore.Images.Media.DATE_ADDED} DESC"

        try {
            context.contentResolver.query(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                projection,
                null,
                null,
                sortOrder
            )?.use { cursor ->
                val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
                while (cursor.moveToNext()) {
                    val id = cursor.getLong(idColumn)
                    val contentUri = ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id)
                    uris.add(contentUri)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return uris
    }
}

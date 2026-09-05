package com.openfont.changer.ui.screens

import android.Manifest
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import com.openfont.changer.R
import com.openfont.changer.data.model.GpkFileInfo
import com.openfont.changer.data.model.LocationPoint
import com.openfont.changer.data.model.TimelinePhoto
import com.openfont.changer.engine.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimelineMapScreen(
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    val isTracking by TimelineTrackingService.isTracking.collectAsState()
    val currentLocation by TimelineTrackingService.currentLocation.collectAsState()
    val liveTodayPoints by TimelineTrackingService.todayPoints.collectAsState()
    val autoScannedPhotos by BackgroundPhotoScanner.scannedPhotos.collectAsState()

    var gpkFiles by remember { mutableStateOf<List<GpkFileInfo>>(emptyList()) }
    var selectedFile by remember { mutableStateOf<GpkFileInfo?>(null) } // null = 오늘 실시간
    var activePoints by remember { mutableStateOf<List<LocationPoint>>(emptyList()) }
    var selectedPhoto by remember { mutableStateOf<TimelinePhoto?>(null) }

    // 지도 다운로드 상태
    var isDownloadingMap by remember { mutableStateOf(false) }
    var mapDownloadProgress by remember { mutableStateOf("") }

    // 다중 포맷 내보내기 모달 다이얼로그
    var exportTargetFile by remember { mutableStateOf<GpkFileInfo?>(null) }

    // 구간 필터링 및 재생 상태
    var rangeStartRatio by remember { mutableStateOf(0f) }
    var rangeEndRatio by remember { mutableStateOf(1f) }
    var isPlaying by remember { mutableStateOf(false) }
    var playbackProgress by remember { mutableStateOf(0f) }
    var playbackSpeed by remember { mutableStateOf(1.0f) }

    var selectedTab by remember { mutableStateOf(0) } // 0: OpenStreetMap 지도, 1: 파일 보관함

    // 사진 미디어 권한 런처 (백그라운드 자동 병렬 스캔 권한)
    val mediaPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { perms ->
        val granted = perms.values.any { it }
        if (granted) {
            BackgroundPhotoScanner.startAutoScanning(context)
            Toast.makeText(context, "백그라운드 사진 EXIF 자동 스캔이 활성화되었습니다.", Toast.LENGTH_SHORT).show()
        }
    }

    // 위치 권한 런처
    val locationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { perms ->
        val fineGranted = perms[Manifest.permission.ACCESS_FINE_LOCATION] == true
        if (fineGranted) {
            val intent = Intent(context, TimelineTrackingService::class.java).apply {
                action = TimelineTrackingService.ACTION_START
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }
    }

    fun refreshFileList() {
        val baseDir = context.getExternalFilesDir(null) ?: context.filesDir
        gpkFiles = GpkSerializer.listGpkFiles(baseDir)
    }

    LaunchedEffect(Unit) {
        refreshFileList()
        // 자동 사진 스캔 시작 시도
        BackgroundPhotoScanner.startAutoScanning(context)
    }

    // 표시할 포인트 결정
    LaunchedEffect(selectedFile, liveTodayPoints) {
        activePoints = if (selectedFile != null) {
            GpkSerializer.loadPointsFromGpkFile(selectedFile!!.file)
        } else {
            liveTodayPoints
        }
        playbackProgress = 0f
        isPlaying = false
    }

    // 재생 루프 (Playback Loop)
    LaunchedEffect(isPlaying, playbackSpeed, rangeStartRatio, rangeEndRatio, activePoints) {
        if (isPlaying && activePoints.isNotEmpty()) {
            val start = rangeStartRatio
            val end = rangeEndRatio.coerceAtLeast(start + 0.05f)
            if (playbackProgress < start || playbackProgress >= end) {
                playbackProgress = start
            }

            while (isPlaying && playbackProgress < end) {
                val step = 0.01f * playbackSpeed
                playbackProgress = (playbackProgress + step).coerceAtMost(end)
                delay(50L)
            }
            isPlaying = false
        }
    }

    // 필터링된 포인트 목록
    val filteredPoints = remember(activePoints, rangeStartRatio, rangeEndRatio) {
        if (activePoints.isEmpty()) emptyList()
        else {
            val startIdx = (rangeStartRatio * (activePoints.size - 1)).toInt().coerceIn(0, activePoints.size - 1)
            val endIdx = (rangeEndRatio * (activePoints.size - 1)).toInt().coerceIn(startIdx, activePoints.size - 1)
            activePoints.subList(startIdx, endIdx + 1)
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // 상단 헤더: 프리미엄 SVG 로고 심볼 & 상태 바
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_logo_symbol),
                            contentDescription = "FontCraft Logo",
                            tint = Color.Unspecified,
                            modifier = Modifier.size(38.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "FontCraft Timeline",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = if (selectedFile == null) {
                                    if (isTracking) "초절전 기록 중 (${activePoints.size}pt, 사진 ${autoScannedPhotos.size}장)" else "실시간 기록 중지됨"
                                } else {
                                    "${selectedFile!!.name} (${activePoints.size}pt)"
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = if (isTracking && selectedFile == null) Color(0xFF10B981) else MaterialTheme.colorScheme.primary
                            )
                        }
                    }

                    Switch(
                        checked = isTracking,
                        onCheckedChange = { start ->
                            if (start) {
                                val permissions = mutableListOf(
                                    Manifest.permission.ACCESS_FINE_LOCATION,
                                    Manifest.permission.ACCESS_COARSE_LOCATION
                                )
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                    permissions.add(Manifest.permission.POST_NOTIFICATIONS)
                                }
                                locationPermissionLauncher.launch(permissions.toTypedArray())
                            } else {
                                val intent = Intent(context, TimelineTrackingService::class.java).apply {
                                    action = TimelineTrackingService.ACTION_STOP
                                }
                                context.startService(intent)
                                refreshFileList()
                            }
                        }
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                // 빠른 액션 버튼 행: 날짜 선택, 사진 자동 스캔 권한, 오프라인 지도 다운로드
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    OutlinedButton(
                        onClick = { selectedTab = 1 },
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 4.dp)
                    ) {
                        Icon(Icons.Default.DateRange, contentDescription = null, modifier = Modifier.size(15.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(if (selectedFile == null) "일자 선택" else "과거 기록", fontSize = 11.sp)
                    }

                    FilledTonalButton(
                        onClick = {
                            val perms = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                arrayOf(Manifest.permission.READ_MEDIA_IMAGES)
                            } else {
                                arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
                            }
                            mediaPermissionLauncher.launch(perms)
                        },
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.weight(1.3f),
                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 4.dp)
                    ) {
                        Icon(Icons.Default.PhotoCamera, contentDescription = null, modifier = Modifier.size(15.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("사진 자동스캔 (${autoScannedPhotos.size})", fontSize = 11.sp)
                    }

                    Button(
                        onClick = {
                            if (activePoints.isEmpty()) {
                                Toast.makeText(context, "다운로드할 위치 경로가 없습니다.", Toast.LENGTH_SHORT).show()
                                return@Button
                            }
                            isDownloadingMap = true
                            coroutineScope.launch {
                                OfflineMapDownloader.downloadTilesForTrack(context, activePoints) { progress ->
                                    mapDownloadProgress = "${progress.downloadedTiles}/${progress.totalTiles}"
                                    if (progress.isFinished) {
                                        isDownloadingMap = false
                                        Toast.makeText(context, "오프라인 지도 팩 다운로드 완료!", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            }
                        },
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.weight(1.1f),
                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 4.dp)
                    ) {
                        Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(15.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(if (isDownloadingMap) mapDownloadProgress else "지도 저장", fontSize = 11.sp)
                    }

                    if (selectedFile != null) {
                        IconButton(
                            onClick = { selectedFile = null },
                            modifier = Modifier.size(34.dp)
                        ) {
                            Icon(Icons.Default.Refresh, contentDescription = "오늘로 복귀", tint = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            }
        }

        // 재생 컨트롤러 및 구간 필터
        if (activePoints.isNotEmpty()) {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            FilledIconButton(
                                onClick = { isPlaying = !isPlaying },
                                shape = CircleShape,
                                modifier = Modifier.size(34.dp)
                            ) {
                                Icon(
                                    imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                    contentDescription = "재생",
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "타임랩스 재생",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                        }

                        SuggestionChip(
                            onClick = {
                                playbackSpeed = when (playbackSpeed) {
                                    1.0f -> 2.0f
                                    2.0f -> 5.0f
                                    5.0f -> 10.0f
                                    else -> 1.0f
                                }
                            },
                            label = { Text("${playbackSpeed.toInt()}x 속도", fontSize = 10.sp) }
                        )
                    }

                    Slider(
                        value = playbackProgress,
                        onValueChange = {
                            playbackProgress = it
                            isPlaying = false
                        },
                        valueRange = 0f..1f,
                        modifier = Modifier.height(26.dp)
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "구간 필터: ${(rangeStartRatio * 100).toInt()}% ~ ${(rangeEndRatio * 100).toInt()}% (${filteredPoints.size}개 지점)",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "전체 구간",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.clickable {
                                rangeStartRatio = 0f
                                rangeEndRatio = 1f
                            }
                        )
                    }
                }
            }
        }

        if (selectedTab == 0) {
            // 구글 지도급 네이티브 OpenStreetMap (osmdroid) 뷰어
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .clip(RoundedCornerShape(16.dp))
                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(16.dp))
            ) {
                com.openfont.changer.ui.components.OsmDroidMapView(
                    points = filteredPoints,
                    playbackPoint = if (playbackProgress > 0f && activePoints.isNotEmpty()) {
                        val idx = (playbackProgress * (activePoints.size - 1)).toInt().coerceIn(0, activePoints.size - 1)
                        activePoints[idx]
                    } else null,
                    photos = autoScannedPhotos,
                    onPhotoClick = { selectedPhoto = it },
                    modifier = Modifier.fillMaxSize()
                )

                // 사진 터치 팝업
                selectedPhoto?.let { photo ->
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(10.dp)
                            .fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(text = "📸 자동 색인된 사진 (EXIF)", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                Text(text = photo.fileName, fontSize = 11.sp, color = MaterialTheme.colorScheme.primary)
                                Text(text = "촬영일시: ${photo.formattedTime}", fontSize = 10.sp, color = Color.Gray)
                            }
                            IconButton(onClick = { selectedPhoto = null }) {
                                Icon(Icons.Default.Close, contentDescription = "닫기")
                            }
                        }
                    }
                }
            }
        } else {
            // GPK 파일 보관함 및 KML/GPX/GeoJSON 다중 포맷 내보내기
            Column(modifier = Modifier.fillMaxSize()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(text = "저장된 기록 목록 (${gpkFiles.size})", fontWeight = FontWeight.Bold)
                    TextButton(onClick = { selectedTab = 0 }) {
                        Text("지도로 돌아가기")
                    }
                }

                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(gpkFiles) { gpk ->
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = if (selectedFile?.file == gpk.file) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface
                            ),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    selectedFile = gpk
                                    selectedTab = 0
                                }
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(text = gpk.name, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                    Text(text = "${gpk.formattedDate} · ${gpk.pointCount} 포인트 · ${gpk.sizeBytes / 1024} KB", fontSize = 11.sp, color = Color.Gray)
                                }
                                Row {
                                    FilledTonalButton(
                                        onClick = { exportTargetFile = gpk },
                                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                        shape = RoundedCornerShape(8.dp)
                                    ) {
                                        Text("KML/내보내기", fontSize = 11.sp)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // KML / GPX / GeoJSON 다중 포맷 변환 다이얼로그
    exportTargetFile?.let { gpk ->
        AlertDialog(
            onDismissRequest = { exportTargetFile = null },
            title = { Text("다중 지도 포맷으로 내보내기") },
            text = { Text("선택한 기록 (${gpk.name})을 어떤 포맷으로 내보내시겠습니까?") },
            confirmButton = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Button(
                        onClick = {
                            val pts = GpkSerializer.loadPointsFromGpkFile(gpk.file)
                            val kmlFile = File(context.cacheDir, "${gpk.file.nameWithoutExtension}.kml")
                            MultiFormatExporter.exportToKml(kmlFile, gpk.name, pts, autoScannedPhotos)
                            shareFile(context, kmlFile, "application/vnd.google-earth.kml+xml")
                            exportTargetFile = null
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("KML 포맷 (Google Earth / 타임라인 완벽 호환)")
                    }

                    Button(
                        onClick = {
                            val pts = GpkSerializer.loadPointsFromGpkFile(gpk.file)
                            val geoJsonFile = File(context.cacheDir, "${gpk.file.nameWithoutExtension}.geojson")
                            MultiFormatExporter.exportToGeoJson(geoJsonFile, gpk.name, pts, autoScannedPhotos)
                            shareFile(context, geoJsonFile, "application/geo+json")
                            exportTargetFile = null
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("GeoJSON 포맷 (웹 지도 및 GIS)")
                    }

                    OutlinedButton(
                        onClick = {
                            shareFile(context, gpk.file, "application/octet-stream")
                            exportTargetFile = null
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("원본 GPK / GPX 파일 공유")
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = { exportTargetFile = null }) {
                    Text("취소")
                }
            }
        )
    }
}

private fun shareFile(context: Context, file: File, mimeType: String) {
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.provider", file)
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = mimeType
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(intent, "파일 내보내기"))
}

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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import com.openfont.changer.data.model.GpkFileInfo
import com.openfont.changer.data.model.LocationPoint
import com.openfont.changer.data.model.TimelinePhoto
import com.openfont.changer.engine.GpkSerializer
import com.openfont.changer.engine.OsmTileManager
import com.openfont.changer.engine.PhotoExifParser
import com.openfont.changer.engine.TimelineTrackingService
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File
import kotlin.math.*

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

    var gpkFiles by remember { mutableStateOf<List<GpkFileInfo>>(emptyList()) }
    var selectedFile by remember { mutableStateOf<GpkFileInfo?>(null) } // null = 오늘 실시간
    var activePoints by remember { mutableStateOf<List<LocationPoint>>(emptyList()) }
    var photoMarkers by remember { mutableStateOf<List<TimelinePhoto>>(emptyList()) }
    var selectedPhoto by remember { mutableStateOf<TimelinePhoto?>(null) }

    // 구간 필터링 및 재생 상태
    var rangeStartRatio by remember { mutableStateOf(0f) }
    var rangeEndRatio by remember { mutableStateOf(1f) }
    var isPlaying by remember { mutableStateOf(false) }
    var playbackProgress by remember { mutableStateOf(0f) }
    var playbackSpeed by remember { mutableStateOf(1.0f) }

    var selectedTab by remember { mutableStateOf(0) } // 0: OpenStreetMap 지도, 1: 파일 보관함

    // 사진 다중 선택 런처 (EXIF 메타 파싱)
    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetMultipleContents()
    ) { uris: List<Uri> ->
        if (uris.isNotEmpty()) {
            coroutineScope.launch {
                val parsed = mutableListOf<TimelinePhoto>()
                for (u in uris) {
                    val p = PhotoExifParser.parsePhotoExif(context, u)
                    if (p != null) parsed.add(p)
                }
                photoMarkers = photoMarkers + parsed
                Toast.makeText(context, "${parsed.size}장의 사진에서 GPS EXIF 메타를 불러왔습니다.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // 위치 권한 런처
    val permissionLauncher = rememberLauncherForActivityResult(
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
        } else {
            Toast.makeText(context, "위치 권한이 필요합니다.", Toast.LENGTH_SHORT).show()
        }
    }

    fun refreshFileList() {
        val baseDir = context.getExternalFilesDir(null) ?: context.filesDir
        gpkFiles = GpkSerializer.listGpkFiles(baseDir)
    }

    LaunchedEffect(Unit) {
        refreshFileList()
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
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // 상단 제어 바: 실시간 트래커 토글 + 특정 일자 선택 드롭다운
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "구글 타임라인 OpenStreetMap",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = if (selectedFile == null) {
                                if (isTracking) "오늘 실시간 수집 중 (${activePoints.size}pt)" else "오늘 실시간 기록 (정지됨)"
                            } else {
                                "선택된 과거 기록: ${selectedFile!!.name} (${activePoints.size}pt)"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = if (isTracking && selectedFile == null) Color(0xFF10B981) else MaterialTheme.colorScheme.primary
                        )
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
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
                                    permissionLauncher.launch(permissions.toTypedArray())
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
                }

                Spacer(modifier = Modifier.height(8.dp))

                // 특정 일자/세션 선택 버튼 및 사진 EXIF 불러오기 버튼
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = { selectedTab = 1 },
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp)
                    ) {
                        Icon(Icons.Default.DateRange, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(if (selectedFile == null) "일자/세션 선택" else selectedFile!!.name.take(12) + "..", fontSize = 12.sp)
                    }

                    FilledTonalButton(
                        onClick = { photoPickerLauncher.launch("image/*") },
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp)
                    ) {
                        Icon(Icons.Default.AddPhotoAlternate, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("사진 EXIF 추가 (${photoMarkers.size})", fontSize = 12.sp)
                    }

                    if (selectedFile != null) {
                        IconButton(
                            onClick = { selectedFile = null },
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(Icons.Default.Refresh, contentDescription = "오늘로 복귀", tint = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            }
        }

        // 재생 컨트롤러 & 특정 구간 필터 슬라이더
        if (activePoints.isNotEmpty()) {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            FilledIconButton(
                                onClick = { isPlaying = !isPlaying },
                                shape = CircleShape,
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(
                                    imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                    contentDescription = "재생/정지",
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "타임랩스 재생",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.SemiBold
                            )
                        }

                        // 속도 배율 토글 (1x -> 2x -> 5x -> 10x)
                        SuggestionChip(
                            onClick = {
                                playbackSpeed = when (playbackSpeed) {
                                    1.0f -> 2.0f
                                    2.0f -> 5.0f
                                    5.0f -> 10.0f
                                    else -> 1.0f
                                }
                            },
                            label = { Text("${playbackSpeed.toInt()}x 속도", fontSize = 11.sp) }
                        )
                    }

                    // 재생 진행도 슬라이더
                    Slider(
                        value = playbackProgress,
                        onValueChange = {
                            playbackProgress = it
                            isPlaying = false
                        },
                        valueRange = 0f..1f,
                        modifier = Modifier.height(28.dp)
                    )

                    // 구간 설정 (시작% ~ 종료%)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "구간 필터: ${(rangeStartRatio * 100).toInt()}% ~ ${(rangeEndRatio * 100).toInt()}% (${filteredPoints.size}pt)",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(
                                text = "전체",
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
        }

        if (selectedTab == 0) {
            // OpenStreetMap 타일 배경 인터랙티브 맵 캔버스
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color(0xFFE2E8F0))
                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(16.dp))
            ) {
                if (activePoints.isEmpty()) {
                    Column(
                        modifier = Modifier.align(Alignment.Center),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(Icons.Default.Map, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(48.dp))
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("기록된 타임라인 경로가 없습니다.", style = MaterialTheme.typography.bodyMedium, color = Color(0xFF64748B))
                    }
                } else {
                    OsmTimelineCanvas(
                        points = filteredPoints,
                        playbackPoint = if (playbackProgress > 0f && activePoints.isNotEmpty()) {
                            val idx = (playbackProgress * (activePoints.size - 1)).toInt().coerceIn(0, activePoints.size - 1)
                            activePoints[idx]
                        } else null,
                        photos = photoMarkers,
                        onPhotoClick = { selectedPhoto = it },
                        modifier = Modifier.fillMaxSize()
                    )
                }

                // 사진 클릭 시 EXIF 상세 팝업
                selectedPhoto?.let { photo ->
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(12.dp)
                            .fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(text = "📸 사진 위치 (EXIF)", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                Text(text = photo.fileName, fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)
                                Text(text = "촬영일시: ${photo.formattedTime}", fontSize = 11.sp, color = Color.Gray)
                                Text(text = "GPS: ${String.format("%.5f, %.5f", photo.latitude, photo.longitude)}", fontSize = 11.sp, color = Color.Gray)
                            }
                            IconButton(onClick = { selectedPhoto = null }) {
                                Icon(Icons.Default.Close, contentDescription = "닫기")
                            }
                        }
                    }
                }
            }
        } else {
            // GPK 파일 보관함
            Column(modifier = Modifier.fillMaxSize()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(text = "저장된 타임라인 목록 (${gpkFiles.size})", fontWeight = FontWeight.Bold)
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
                                modifier = Modifier.padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(text = gpk.name, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                    Text(text = "${gpk.formattedDate} · ${gpk.pointCount} 포인트 · ${gpk.sizeBytes / 1024} KB", fontSize = 12.sp, color = Color.Gray)
                                }
                                Row {
                                    IconButton(onClick = {
                                        val uri = FileProvider.getUriForFile(context, "${context.packageName}.provider", gpk.file)
                                        val intent = Intent(Intent.ACTION_SEND).apply {
                                            type = "application/octet-stream"
                                            putExtra(Intent.EXTRA_STREAM, uri)
                                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                        }
                                        context.startActivity(Intent.createChooser(intent, "GPK 타임라인 파일 내보내기"))
                                    }) {
                                        Icon(Icons.Default.Share, contentDescription = "공유")
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * OpenStreetMap 타일 다운로드 및 타임라인/사진 렌더러 캔버스
 */
@Composable
fun OsmTimelineCanvas(
    points: List<LocationPoint>,
    playbackPoint: LocationPoint?,
    photos: List<TimelinePhoto>,
    onPhotoClick: (TimelinePhoto) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var scale by remember { mutableStateOf(1f) }
    var panOffset by remember { mutableStateOf(Offset.Zero) }

    // OSM 타일 캐시 상태
    val tileBitmaps = remember { mutableStateMapOf<String, Bitmap>() }

    val minLat = remember(points) { points.minOfOrNull { it.latitude } ?: 37.5665 }
    val maxLat = remember(points) { points.maxOfOrNull { it.latitude } ?: 37.5670 }
    val minLon = remember(points) { points.minOfOrNull { it.longitude } ?: 126.9780 }
    val maxLon = remember(points) { points.maxOfOrNull { it.longitude } ?: 126.9785 }

    val centerLat = (minLat + maxLat) / 2.0
    val centerLon = (minLon + maxLon) / 2.0
    val zoom = 15 // 중간 줌 레벨

    // 중앙 타일 다운로드
    LaunchedEffect(centerLat, centerLon) {
        val centerTileX = OsmTileManager.lonToTileX(centerLon, zoom)
        val centerTileY = OsmTileManager.latToTileY(centerLat, zoom)

        // 주변 3x3 타일 로드
        for (dx in -1..1) {
            for (dy in -1..1) {
                val tx = centerTileX + dx
                val ty = centerTileY + dy
                val key = "$zoom/$tx/$ty"
                if (!tileBitmaps.containsKey(key)) {
                    coroutineScope.launch {
                        val bmp = OsmTileManager.getTile(context, zoom, tx, ty)
                        if (bmp != null) tileBitmaps[key] = bmp
                    }
                }
            }
        }
    }

    Canvas(
        modifier = modifier
            .pointerInput(Unit) {
                detectTransformGestures { _, pan, zoomFactor, _ ->
                    scale = (scale * zoomFactor).coerceIn(0.5f, 6.0f)
                    panOffset += pan
                }
            }
    ) {
        val latSpan = (maxLat - minLat).coerceAtLeast(0.001)
        val lonSpan = (maxLon - minLon).coerceAtLeast(0.001)

        val padding = 60f
        val usableWidth = size.width - (padding * 2)
        val usableHeight = size.height - (padding * 2)

        fun toScreenCoord(lat: Double, lon: Double): Offset {
            val x = padding + ((lon - minLon) / lonSpan * usableWidth).toFloat()
            val y = padding + ((maxLat - lat) / latSpan * usableHeight).toFloat()
            return (Offset(x, y) * scale) + panOffset
        }

        // 1. OpenStreetMap 타일 배경 그리기
        val centerScr = toScreenCoord(centerLat, centerLon)
        val centerTileX = OsmTileManager.lonToTileX(centerLon, zoom)
        val centerTileY = OsmTileManager.latToTileY(centerLat, zoom)

        for (dx in -1..1) {
            for (dy in -1..1) {
                val tx = centerTileX + dx
                val ty = centerTileY + dy
                val key = "$zoom/$tx/$ty"
                tileBitmaps[key]?.let { bmp ->
                    val tileSize = 256f * scale
                    val tileX = centerScr.x + (dx * tileSize) - (tileSize / 2)
                    val tileY = centerScr.y + (dy * tileSize) - (tileSize / 2)
                    drawImage(
                        image = bmp.asImageBitmap(),
                        dstOffset = IntOffset(tileX.toInt(), tileY.toInt()),
                        dstSize = IntSize(tileSize.toInt(), tileSize.toInt())
                    )
                }
            }
        }

        // 2. 타임라인 이동 경로 폴리라인 (선명한 인디고 블루 & 외곽선)
        if (points.size >= 2) {
            val path = Path()
            val firstScreen = toScreenCoord(points.first().latitude, points.first().longitude)
            path.moveTo(firstScreen.x, firstScreen.y)

            for (i in 1 until points.size) {
                val scr = toScreenCoord(points[i].latitude, points[i].longitude)
                path.lineTo(scr.x, scr.y)
            }

            // 그림자 외곽선
            drawPath(
                path = path,
                color = Color.White.copy(alpha = 0.8f),
                style = Stroke(width = 10f)
            )
            // 메인 트랙
            drawPath(
                path = path,
                color = Color(0xFF4F46E5),
                style = Stroke(width = 6f)
            )
        }

        // 3. 출발점 마커 (초록)
        val startScr = toScreenCoord(points.first().latitude, points.first().longitude)
        drawCircle(color = Color.White, radius = 12f, center = startScr)
        drawCircle(color = Color(0xFF10B981), radius = 9f, center = startScr)

        // 4. 도착점 마커 (주황)
        val endScr = toScreenCoord(points.last().latitude, points.last().longitude)
        drawCircle(color = Color.White, radius = 12f, center = endScr)
        drawCircle(color = Color(0xFFF59E0B), radius = 9f, center = endScr)

        // 5. 사진 EXIF 마커 (보라색 카메라 핀)
        for (photo in photos) {
            val pScr = toScreenCoord(photo.latitude, photo.longitude)
            drawCircle(color = Color.White, radius = 14f, center = pScr)
            drawCircle(color = Color(0xFF8B5CF6), radius = 11f, center = pScr)
            drawCircle(color = Color.White, radius = 4f, center = pScr)
        }

        // 6. 타임랩스 재생 현재 위치 마커 (붉은색 펄스 링)
        playbackPoint?.let { pb ->
            val pbScr = toScreenCoord(pb.latitude, pb.longitude)
            drawCircle(color = Color(0xFFF43F5E).copy(alpha = 0.35f), radius = 24f, center = pbScr)
            drawCircle(color = Color.White, radius = 14f, center = pbScr)
            drawCircle(color = Color(0xFFF43F5E), radius = 10f, center = pbScr)
        }
    }
}

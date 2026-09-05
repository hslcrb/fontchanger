package com.openfont.changer.ui.screens

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
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
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.openfont.changer.data.model.GpkFileInfo
import com.openfont.changer.data.model.LocationPoint
import com.openfont.changer.engine.GpkSerializer
import com.openfont.changer.engine.TimelineTrackingService
import java.io.File
import kotlin.math.*

@Composable
fun TimelineMapScreen(
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val isTracking by TimelineTrackingService.isTracking.collectAsState()
    val currentLocation by TimelineTrackingService.currentLocation.collectAsState()
    val todayPoints by TimelineTrackingService.todayPoints.collectAsState()

    var gpkFiles by remember { mutableStateOf<List<GpkFileInfo>>(emptyList()) }
    var selectedTab by remember { mutableStateOf(0) } // 0: 실시간 지도, 1: 저장된 GPK 파일 보관함

    // 권한 요청 런처
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

    LaunchedEffect(Unit) {
        val baseDir = context.getExternalFilesDir(null) ?: context.filesDir
        gpkFiles = GpkSerializer.listGpkFiles(baseDir)
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // 상단 제어 카드
        Card(
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            ),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "구글 타임라인 GPK 엔진",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = if (isTracking) "초절전 백그라운드 기록 중 (매일 23:59 자동 저장)" else "위치 기록 일시 중지됨",
                            style = MaterialTheme.typography.bodySmall,
                            color = if (isTracking) Color(0xFF10B981) else MaterialTheme.colorScheme.onSurfaceVariant
                        )
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
                                permissionLauncher.launch(permissions.toTypedArray())
                            } else {
                                val intent = Intent(context, TimelineTrackingService::class.java).apply {
                                    action = TimelineTrackingService.ACTION_STOP
                                }
                                context.startService(intent)
                                val baseDir = context.getExternalFilesDir(null) ?: context.filesDir
                                gpkFiles = GpkSerializer.listGpkFiles(baseDir)
                            }
                        }
                    )
                }

                // 실시간 요약 통계
                if (todayPoints.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceAround
                    ) {
                        StatItem(title = "기록 포인트", value = "${todayPoints.size}개")
                        StatItem(title = "총 이동거리", value = String.format("%.2f km", calculateTotalDistance(todayPoints)))
                        StatItem(title = "실시간 속도", value = String.format("%.1f km/h", (currentLocation?.speed ?: 0f) * 3.6f))
                    }
                }
            }
        }

        // 탭: 실시간 지도 vs 저장된 GPK 파일
        TabRow(
            selectedTabIndex = selectedTab,
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
            modifier = Modifier.clip(RoundedCornerShape(12.dp))
        ) {
            Tab(
                selected = selectedTab == 0,
                onClick = { selectedTab = 0 },
                text = { Text("타임라인 지도") }
            )
            Tab(
                selected = selectedTab == 1,
                onClick = {
                    selectedTab = 1
                    val baseDir = context.getExternalFilesDir(null) ?: context.filesDir
                    gpkFiles = GpkSerializer.listGpkFiles(baseDir)
                },
                text = { Text("GPK 파일 보관함 (${gpkFiles.size})") }
            )
        }

        if (selectedTab == 0) {
            // 실시간 인터랙티브 타임라인 맵 캔버스
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color(0xFF0F172A))
                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(16.dp))
            ) {
                if (todayPoints.isEmpty()) {
                    Column(
                        modifier = Modifier.align(Alignment.Center),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            Icons.Default.MyLocation,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(48.dp)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "스위치를 켜면 백그라운드에서 실시간 이동 경로가 기록됩니다",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFF94A3B8)
                        )
                    }
                } else {
                    TimelineCanvasMap(
                        points = todayPoints,
                        currentPoint = currentLocation,
                        modifier = Modifier.fillMaxSize()
                    )
                }

                // 즉시 GPK 수동 저장 버튼
                if (todayPoints.isNotEmpty()) {
                    SmallFloatingActionButton(
                        onClick = {
                            val intent = Intent(context, TimelineTrackingService::class.java).apply {
                                action = TimelineTrackingService.ACTION_FORCE_SAVE_DAILY
                            }
                            context.startService(intent)
                            Toast.makeText(context, "현재 타임라인이 GPK 파일로 저장되었습니다.", Toast.LENGTH_SHORT).show()
                            val baseDir = context.getExternalFilesDir(null) ?: context.filesDir
                            gpkFiles = GpkSerializer.listGpkFiles(baseDir)
                        },
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(16.dp),
                        containerColor = MaterialTheme.colorScheme.primary
                    ) {
                        Icon(Icons.Default.Save, contentDescription = "GPK 저장")
                    }
                }
            }
        } else {
            // 저장된 GPK 파일 목록
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (gpkFiles.isEmpty()) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(32.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "아직 저장된 GPK 파일이 없습니다.\n매일 23시 59분에 자동으로 생성됩니다.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                items(gpkFiles) { gpk ->
                    GpkFileCard(
                        gpk = gpk,
                        onShare = {
                            val uri = FileProvider.getUriForFile(context, "${context.packageName}.provider", gpk.file)
                            val intent = Intent(Intent.ACTION_SEND).apply {
                                type = "application/octet-stream"
                                putExtra(Intent.EXTRA_STREAM, uri)
                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            }
                            context.startActivity(Intent.createChooser(intent, "GPK 타임라인 파일 내보내기"))
                        },
                        onDelete = {
                            gpk.file.delete()
                            val baseDir = context.getExternalFilesDir(null) ?: context.filesDir
                            gpkFiles = GpkSerializer.listGpkFiles(baseDir)
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun TimelineCanvasMap(
    points: List<LocationPoint>,
    currentPoint: LocationPoint?,
    modifier: Modifier = Modifier
) {
    var scale by remember { mutableStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }

    Canvas(
        modifier = modifier
            .pointerInput(Unit) {
                detectTransformGestures { _, pan, zoom, _ ->
                    scale = (scale * zoom).coerceIn(0.5f, 5.0f)
                    offset += pan
                }
            }
    ) {
        val minLat = points.minOf { it.latitude }
        val maxLat = points.maxOf { it.latitude }
        val minLon = points.minOf { it.longitude }
        val maxLon = points.maxOf { it.longitude }

        val latSpan = (maxLat - minLat).coerceAtLeast(0.0005)
        val lonSpan = (maxLon - minLon).coerceAtLeast(0.0005)

        val padding = 60f
        val usableWidth = size.width - (padding * 2)
        val usableHeight = size.height - (padding * 2)

        fun toScreenCoord(point: LocationPoint): Offset {
            val x = padding + ((point.longitude - minLon) / lonSpan * usableWidth).toFloat()
            val y = padding + ((maxLat - point.latitude) / latSpan * usableHeight).toFloat()
            return (Offset(x, y) * scale) + offset
        }

        // 경로 폴리라인 그리기
        if (points.size >= 2) {
            val path = Path()
            val firstScreen = toScreenCoord(points.first())
            path.moveTo(firstScreen.x, firstScreen.y)

            for (i in 1 until points.size) {
                val scr = toScreenCoord(points[i])
                path.lineTo(scr.x, scr.y)
            }

            drawPath(
                path = path,
                color = Color(0xFF6366F1),
                style = Stroke(width = 6f)
            )
        }

        // 시작점 마커 (초록)
        val startScr = toScreenCoord(points.first())
        drawCircle(color = Color(0xFF10B981), radius = 10f, center = startScr)

        // 각 포인트 닷 (보라)
        for (p in points) {
            val pScr = toScreenCoord(p)
            drawCircle(color = Color(0xFFA5B4FC), radius = 4f, center = pScr)
        }

        // 현재 위치 마커 (붉은색/핑크 펄스 링)
        val cur = currentPoint ?: points.last()
        val curScr = toScreenCoord(cur)
        drawCircle(color = Color(0xFFF43F5E).copy(alpha = 0.3f), radius = 22f, center = curScr)
        drawCircle(color = Color(0xFFF43F5E), radius = 12f, center = curScr)
    }
}

@Composable
fun GpkFileCard(
    gpk: GpkFileInfo,
    onShare: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = if (gpk.isSession) Icons.Default.Timeline else Icons.Default.CalendarToday,
                        contentDescription = null,
                        tint = if (gpk.isSession) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (gpk.isSession) "앱 세션 기록" else "일일 타임라인 (23:59)",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = gpk.name,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "${gpk.formattedDate} · ${gpk.pointCount}개 위치 · ${gpk.sizeBytes / 1024} KB",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline
                )
            }

            Row {
                IconButton(onClick = onShare) {
                    Icon(Icons.Default.Share, contentDescription = "공유")
                }
                IconButton(onClick = onDelete) {
                    Icon(Icons.Default.Delete, contentDescription = "삭제", tint = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}

@Composable
fun StatItem(title: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text = title, style = MaterialTheme.typography.labelSmall, color = Color(0xFF94A3B8))
        Text(text = value, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
    }
}

private fun calculateTotalDistance(points: List<LocationPoint>): Double {
    if (points.size < 2) return 0.0
    var totalMeters = 0.0
    val earthRadius = 6371000.0

    for (i in 0 until points.size - 1) {
        val p1 = points[i]
        val p2 = points[i + 1]
        val dLat = Math.toRadians(p2.latitude - p1.latitude)
        val dLon = Math.toRadians(p2.longitude - p1.longitude)
        val a = sin(dLat / 2) * sin(dLat / 2) +
                cos(Math.toRadians(p1.latitude)) * cos(Math.toRadians(p2.latitude)) *
                sin(dLon / 2) * sin(dLon / 2)
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        totalMeters += earthRadius * c
    }
    return totalMeters / 1000.0
}

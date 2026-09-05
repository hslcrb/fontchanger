package com.openfont.changer.ui.components

import android.content.Context
import android.graphics.Color as AndroidColor
import android.graphics.Paint
import android.view.MotionEvent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import com.openfont.changer.data.model.LocationPoint
import com.openfont.changer.data.model.TimelinePhoto
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.CustomZoomButtonsController
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polyline
import org.osmdroid.views.overlay.compass.CompassOverlay
import org.osmdroid.views.overlay.compass.InternalCompassOrientationProvider

/**
 * 구글 지도와 동일한 수준의 부드러운 타일 렌더링, 줌/팬 제스처,
 * 트랙 폴리라인 및 마커를 제공하는 네이티브 osmdroid 맵 컴포저블
 */
@Composable
fun OsmDroidMapView(
    points: List<LocationPoint>,
    playbackPoint: LocationPoint?,
    photos: List<TimelinePhoto>,
    onPhotoClick: (TimelinePhoto) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var mapViewRef by remember { mutableStateOf<MapView?>(null) }

    AndroidView(
        modifier = modifier.fillMaxSize(),
        factory = { ctx ->
            MapView(ctx).apply {
                // 오픈스트리트맵 표준 맵닉 타일 소스 지정 (무한 타일 스트리밍)
                setTileSource(TileSourceFactory.MAPNIK)
                setMultiTouchControls(true)
                zoomController.setVisibility(CustomZoomButtonsController.Visibility.NEVER)
                isTilesScaledToDpi = true

                // 나침반 오버레이
                val compassOverlay = CompassOverlay(ctx, InternalCompassOrientationProvider(ctx), this)
                compassOverlay.enableCompass()
                overlays.add(compassOverlay)

                // 기본 줌 및 대한민국 서울 중심 좌표
                controller.setZoom(16.0)
                controller.setCenter(GeoPoint(37.5665, 126.9780))

                mapViewRef = this
            }
        },
        update = { mapView ->
            // 나침반 오버레이만 보존하고 마커/트랙만 다시 그림
            val compassOverlays = mapView.overlays.filterIsInstance<CompassOverlay>()
            mapView.overlays.clear()
            mapView.overlays.addAll(compassOverlays)

            // 1. 구글 지도 스타일 트랙 폴리라인 (Polyline)
            if (points.isNotEmpty()) {
                val geoPoints = points.map { GeoPoint(it.latitude, it.longitude) }

                // 외곽선 (외곽 섀도우)
                val outline = Polyline(mapView).apply {
                    setPoints(geoPoints)
                    outlinePaint.color = AndroidColor.WHITE
                    outlinePaint.strokeWidth = 14f
                    outlinePaint.strokeCap = Paint.Cap.ROUND
                    outlinePaint.strokeJoin = Paint.Join.ROUND
                    outlinePaint.isAntiAlias = true
                }
                mapView.overlays.add(outline)

                // 메인 트랙선 (선명한 인디고 블루)
                val polyline = Polyline(mapView).apply {
                    setPoints(geoPoints)
                    outlinePaint.color = AndroidColor.parseColor("#4F46E5")
                    outlinePaint.strokeWidth = 8f
                    outlinePaint.strokeCap = Paint.Cap.ROUND
                    outlinePaint.strokeJoin = Paint.Join.ROUND
                    outlinePaint.isAntiAlias = true
                }
                mapView.overlays.add(polyline)

                // 출발 지점 마커
                val startGeo = geoPoints.first()
                val startMarker = Marker(mapView).apply {
                    position = startGeo
                    title = "출발 지점"
                    setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                }
                mapView.overlays.add(startMarker)

                // 도착/현재 지점 마커
                val endGeo = geoPoints.last()
                val endMarker = Marker(mapView).apply {
                    position = endGeo
                    title = "최신 위치"
                    setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                }
                mapView.overlays.add(endMarker)

                // 최초 로드 시 경로 중앙으로 부드럽게 이동
                if (playbackPoint == null && points.size == 1) {
                    mapView.controller.animateTo(startGeo)
                }
            }

            // 2. 사진 EXIF 위치 마커들
            for (photo in photos) {
                val photoMarker = Marker(mapView).apply {
                    position = GeoPoint(photo.latitude, photo.longitude)
                    title = "📸 ${photo.fileName}"
                    snippet = "촬영일시: ${photo.formattedTime}"
                    setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                    setOnMarkerClickListener { marker, _ ->
                        marker.showInfoWindow()
                        onPhotoClick(photo)
                        true
                    }
                }
                mapView.overlays.add(photoMarker)
            }

            // 3. 타임랩스 재생 마커 (부드럽게 이동)
            playbackPoint?.let { pb ->
                val pbGeo = GeoPoint(pb.latitude, pb.longitude)
                val playbackMarker = Marker(mapView).apply {
                    position = pbGeo
                    title = "타임랩스 재생 중"
                    setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                }
                mapView.overlays.add(playbackMarker)
                mapView.controller.animateTo(pbGeo)
            }

            mapView.invalidate()
        }
    )

    DisposableEffect(Unit) {
        onDispose {
            mapViewRef?.onDetach()
        }
    }
}

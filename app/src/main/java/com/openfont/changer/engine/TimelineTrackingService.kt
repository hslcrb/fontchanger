package com.openfont.changer.engine

import android.annotation.SuppressLint
import android.app.*
import android.content.Context
import android.content.Intent
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.openfont.changer.MainActivity
import com.openfont.changer.data.model.LocationPoint
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

/**
 * 구글 타임라인의 완벽한 대용 - 초절전 백그라운드 GPS/GPK 타임라인 트래킹 서비스
 * - 시스템 자원(배터리/CPU) 최소화: 스마트 이동 감지 적응형 간격
 * - 매일 23시 59분 자동 GPK 저장 & 무중단 연속 기록
 * - 앱 실행~종료 시까지의 세션 전용 GPK 별도 분리 기록
 */
class TimelineTrackingService : Service(), LocationListener {

    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var locationManager: LocationManager? = null

    private var lastRecordedLocation: Location? = null
    private var lastRecordedTime = 0L

    private val sessionStartDate = Date()

    companion object {
        const val ACTION_START = "ACTION_START_TRACKING"
        const val ACTION_STOP = "ACTION_STOP_TRACKING"
        const val ACTION_FORCE_SAVE_DAILY = "ACTION_FORCE_SAVE_DAILY"
        private const val NOTIFICATION_CHANNEL_ID = "fontcraft_timeline_channel"
        private const val NOTIFICATION_ID = 9901

        // 전역 메모리 버퍼 (실시간 UI 반영용)
        private val _todayPoints = MutableStateFlow<List<LocationPoint>>(emptyList())
        val todayPoints: StateFlow<List<LocationPoint>> = _todayPoints.asStateFlow()

        private val _sessionPoints = MutableStateFlow<List<LocationPoint>>(emptyList())
        val sessionPoints: StateFlow<List<LocationPoint>> = _sessionPoints.asStateFlow()

        private val _currentLocation = MutableStateFlow<LocationPoint?>(null)
        val currentLocation: StateFlow<LocationPoint?> = _currentLocation.asStateFlow()

        private val _isTracking = MutableStateFlow(false)
        val isTracking: StateFlow<Boolean> = _isTracking.asStateFlow()
    }

    override fun onCreate() {
        super.onCreate()
        locationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                startForeground(NOTIFICATION_ID, buildNotification("위치 타임라인 기록 중", "초절전 모드로 백그라운드 기록 중입니다."))
                startTracking()
                scheduleDaily2359Saver()
            }
            ACTION_STOP -> {
                stopTracking()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
            ACTION_FORCE_SAVE_DAILY -> {
                serviceScope.launch {
                    saveDailyTimelineNow()
                }
            }
        }
        return START_STICKY
    }

    @SuppressLint("MissingPermission")
    private fun startTracking() {
        if (_isTracking.value) return
        _isTracking.value = true

        try {
            // 외부 종속성(Play Services) 없이 순수 안드로이드 LocationManager로 자원 절약 동작
            // Passive/Network/GPS 중 사용 가능한 최적 프로바이더 선택
            val providers = locationManager?.getProviders(true) ?: emptyList()

            // 1. 네트워크 프로바이더 (기지국/와이파이 기반: 배터리 소모 극소)
            if (providers.contains(LocationManager.NETWORK_PROVIDER)) {
                locationManager?.requestLocationUpdates(
                    LocationManager.NETWORK_PROVIDER,
                    30000L, // 30초
                    15.0f,  // 15미터
                    this
                )
            }

            // 2. GPS 프로바이더 (정밀 이동 감지)
            if (providers.contains(LocationManager.GPS_PROVIDER)) {
                locationManager?.requestLocationUpdates(
                    LocationManager.GPS_PROVIDER,
                    20000L, // 20초
                    10.0f,  // 10미터
                    this
                )
            }

            // 3. 패시브 프로바이더 (다른 앱이 수신한 위치 가로채기 - 배터리 소모 0%)
            if (providers.contains(LocationManager.PASSIVE_PROVIDER)) {
                locationManager?.requestLocationUpdates(
                    LocationManager.PASSIVE_PROVIDER,
                    10000L,
                    5.0f,
                    this
                )
            }

            // 마지막 유효 위치 즉시 반영
            val lastKnown = locationManager?.getLastKnownLocation(LocationManager.GPS_PROVIDER)
                ?: locationManager?.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
            lastKnown?.let { onLocationChanged(it) }

        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun stopTracking() {
        _isTracking.value = false
        try {
            locationManager?.removeUpdates(this)
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // 앱 세션 및 일일 데이터 최종 저장
        serviceScope.launch {
            saveDailyTimelineNow()
            saveSessionTimelineNow()
        }
    }

    override fun onLocationChanged(location: Location) {
        val now = System.currentTimeMillis()

        // 배터리 절전 필터:
        // 마지막 기록된 위치와 5미터 미만으로 가깝고 20초 이내면 중복 무시하여 CPU/I/O 절약
        lastRecordedLocation?.let { last ->
            val distance = last.distanceTo(location)
            val timeDiff = now - lastRecordedTime
            if (distance < 5.0f && timeDiff < 20000L) {
                return
            }
        }

        lastRecordedLocation = location
        lastRecordedTime = now

        val point = LocationPoint(
            latitude = location.latitude,
            longitude = location.longitude,
            altitude = location.altitude,
            accuracy = location.accuracy,
            speed = location.speed,
            timestamp = now
        )

        _currentLocation.value = point
        _todayPoints.value = _todayPoints.value + point
        _sessionPoints.value = _sessionPoints.value + point

        // 알림 상태 업데이트 (수집된 포인트 수)
        updateNotification("타임라인 수집 중 (${_todayPoints.value.size} 포인트 기록됨)")
    }

    /**
     * 매일 23시 59분에 하루 타임라인을 GPK 파일로 자동 저장하고,
     * 새 하루 버퍼를 준비하는 무중단 백그라운드 타이머 스케줄러
     */
    private fun scheduleDaily2359Saver() {
        serviceScope.launch {
            while (isActive && _isTracking.value) {
                val calendar = Calendar.getInstance()
                val hour = calendar.get(Calendar.HOUR_OF_DAY)
                val minute = calendar.get(Calendar.MINUTE)

                // 23시 59분 감지
                if (hour == 23 && minute == 59) {
                    saveDailyTimelineNow()
                    // 2분간 대기하여 자정 00:01까지 재실행 방지 및 새 하루로 롤오버
                    delay(120000L)
                    // 일일 포인트 버퍼 리셋 (기록은 계속 유지)
                    _todayPoints.value = emptyList()
                } else {
                    // 30초마다 분 확인
                    delay(30000L)
                }
            }
        }
    }

    /**
     * 일일 타임라인 GPK 파일 저장
     */
    private suspend fun saveDailyTimelineNow() = withContext(Dispatchers.IO) {
        val points = _todayPoints.value
        if (points.isNotEmpty()) {
            val baseDir = getExternalFilesDir(null) ?: filesDir
            val targetFile = GpkSerializer.getDailyGpkFile(baseDir, Date())
            val dateStr = SimpleDateFormat("yyyy-MM-dd", Locale.KOREA).format(Date())
            GpkSerializer.saveToGpkFile(
                outputFile = targetFile,
                trackName = "FontCraft Daily Timeline - $dateStr",
                points = points,
                description = "Google Timeline Alternative - 23:59 Daily Auto Archive"
            )
        }
    }

    /**
     * 앱 실행 세션 전용 GPK 파일 별도 저장
     */
    private suspend fun saveSessionTimelineNow() = withContext(Dispatchers.IO) {
        val points = _sessionPoints.value
        if (points.isNotEmpty()) {
            val baseDir = getExternalFilesDir(null) ?: filesDir
            val targetFile = GpkSerializer.getSessionGpkFile(baseDir, sessionStartDate)
            val sessionName = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.KOREA).format(sessionStartDate)
            GpkSerializer.saveToGpkFile(
                outputFile = targetFile,
                trackName = "FontCraft App Session Track ($sessionName)",
                points = points,
                description = "Isolated App Lifetime Session Tracking Log"
            )
        }
    }

    override fun onDestroy() {
        stopTracking()
        serviceScope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    // LocationListener 오버라이드
    override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
    override fun onProviderEnabled(provider: String) {}
    override fun onProviderDisabled(provider: String) {}

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                NOTIFICATION_CHANNEL_ID,
                "FontCraft 타임라인 로거",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "초절전 백그라운드 GPS 타임라인 및 GPK 자동 기록 서비스"
                setShowBadge(false)
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(title: String, content: String): Notification {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(content)
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setOngoing(true)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun updateNotification(content: String) {
        val notification = buildNotification("FontCraft 타임라인 기록 중", content)
        val manager = getSystemService(NotificationManager::class.java)
        manager?.notify(NOTIFICATION_ID, notification)
    }
}

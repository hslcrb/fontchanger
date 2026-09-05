package com.openfont.changer.data.model

import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 단일 위치 로그 포인트
 */
data class LocationPoint(
    val latitude: Double,
    val longitude: Double,
    val altitude: Double = 0.0,
    val accuracy: Float = 0.0f,
    val speed: Float = 0.0f,
    val timestamp: Long = System.currentTimeMillis()
)

/**
 * 방문 지점 (Stay Point) 분석 모델: 구글 타임라인처럼 특정 위치에 머문 시간 산출
 */
data class StayPoint(
    val latitude: Double,
    val longitude: Double,
    val arrivalTime: Long,
    val departureTime: Long,
    val label: String = "체류 지점"
) {
    val durationMinutes: Long
        get() = (departureTime - arrivalTime) / (1000 * 60)
}

/**
 * GPK / GPX 트랙 데이터 컨테이너
 */
data class GpkTrack(
    val name: String,
    val description: String,
    val startTime: Long,
    val points: List<LocationPoint> = emptyList(),
    val isDaily: Boolean = false
)

/**
 * 저장된 GPK 파일 목록 항목
 */
data class GpkFileInfo(
    val file: File,
    val name: String,
    val formattedDate: String,
    val pointCount: Int,
    val isSession: Boolean,
    val sizeBytes: Long
)

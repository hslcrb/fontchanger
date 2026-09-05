package com.openfont.changer.data.model

import java.io.File

enum class ApplyTarget(val displayName: String, val description: String) {
    MAGISK_MODULE("Magisk / KernelSU / APatch", "루팅 환경에서 systemless 방식으로 안전하게 적용 (가장 권장)"),
    SAMSUNG_FLIPFONT("Samsung One UI (FlipFont)", "#mono_ 및 테마 파크 방식을 이용한 루팅 없는 갤럭시 전용 적용"),
    ROOT_DIRECT("Root Direct Overlay", "/system/fonts 직접 마운트 및 fonts.xml 덮어쓰기"),
    SHIZUKU_COMMAND("Shizuku ADB Runner", "Shizuku 특권을 통한 AOSP 오버레이 / 시스템 설정 변경")
}

data class FontMetadata(
    val familyName: String,
    val styleName: String = "Regular",
    val fullName: String = familyName,
    val version: String = "1.0",
    val isVariable: Boolean = false,
    val fileSize: Long = 0L,
    val filePath: String? = null
)

data class PresetFont(
    val id: String,
    val name: String,
    val designer: String,
    val license: String,
    val previewText: String,
    val downloadUrl: String,
    val tags: List<String> = emptyList(),
    val sampleFile: File? = null
)

data class CustomFontConfig(
    val fontSizeScale: Float = 1.0f,
    val letterSpacing: Float = 0.0f, // em
    val lineHeightMultiplier: Float = 1.3f,
    val targetWeight: Int = 400, // 100 ~ 900
    val targetStyle: String = "Normal"
)

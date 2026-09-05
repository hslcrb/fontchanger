package com.openfont.changer.data.model

import java.io.File

enum class ApplyTarget(val displayName: String, val description: String) {
    STANDALONE_EXPORT("단독 내보내기 (루팅/추가앱 불필요)", "외부 도구나 루팅 없이 커스텀된 폰트 파일 및 GPK 타임라인을 바로 생성 및 저장"),
    MAGISK_MODULE("Magisk / KernelSU / APatch", "루팅 환경에서 systemless 방식으로 안전하게 적용"),
    SAMSUNG_FLIPFONT("Samsung One UI (FlipFont)", "#mono_ 및 테마 파크 방식을 이용한 갤럭시 전용 적용"),
    ROOT_DIRECT("Root Direct Overlay", "/system/fonts 직접 마운트 및 fonts.xml 덮어쓰기"),
    SHIZUKU_COMMAND("Shizuku ADB Runner", "Shizuku 특권을 통한 AOSP 오버레이")
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

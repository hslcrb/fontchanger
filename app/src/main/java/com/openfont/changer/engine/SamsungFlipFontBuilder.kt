package com.openfont.changer.engine

import android.content.Context
import com.openfont.changer.data.model.FontMetadata
import java.io.File
import java.io.FileOutputStream

/**
 * Samsung One UI (#mono_ v2.1+ / zFont / Theme Park) 호환 FlipFont 데이터 구성기
 * 루팅되지 않은 삼성 갤럭시 기기에서 폰트를 자유롭게 적용할 수 있도록 지원합니다.
 */
object SamsungFlipFontBuilder {

    data class SamsungGuideStep(
        val stepNumber: Int,
        val title: String,
        val description: String,
        val actionText: String? = null
    )

    fun prepareFontForSamsung(
        context: Context,
        sourceFontFile: File,
        fontMeta: FontMetadata
    ): Result<File> {
        return try {
            val monoFolder = File(context.getExternalFilesDir(null), "SamsungFonts")
            monoFolder.mkdirs()

            val sanitizedName = fontMeta.fullName.replace(" ", "_") + ".ttf"
            val targetFile = File(monoFolder, sanitizedName)

            sourceFontFile.copyTo(targetFile, overwrite = true)
            Result.success(targetFile)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun getSamsungInstructions(): List<SamsungGuideStep> {
        return listOf(
            SamsungGuideStep(
                stepNumber = 1,
                title = "삼성 계정 백업 활성화",
                description = "설정 > 계정 및 백업 > 데이터 백업에서 '설정' 항목만 백업을 수행합니다."
            ),
            SamsungGuideStep(
                stepNumber = 2,
                title = "기본 플립폰트(FlipFont) 기반 설치",
                description = "생성된 폰트 패키지(예: Circe Rounded 또는 Samsung Sans)를 설치합니다."
            ),
            SamsungGuideStep(
                stepNumber = 3,
                title = "글꼴 변경",
                description = "설정 > 디스플레이 > 글꼴 크기 및 스타일에서 방금 설치된 폰트로 변경합니다."
            ),
            SamsungGuideStep(
                stepNumber = 4,
                title = "설정 복원",
                description = "계정 및 백업 > 데이터 복원 메뉴에서 '설정'만 복원하면 원하는 커스텀 폰트로 영구 적용됩니다."
            )
        )
    }
}

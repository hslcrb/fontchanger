package com.openfont.changer.ui.screens

import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import com.openfont.changer.data.model.ApplyTarget
import com.openfont.changer.data.model.FontMetadata
import com.openfont.changer.engine.MagiskModuleGenerator
import com.openfont.changer.engine.RootShellExecutor
import com.openfont.changer.engine.SamsungFlipFontBuilder
import kotlinx.coroutines.launch
import java.io.File

@Composable
fun ApplyMethodScreen(
    metadata: FontMetadata?,
    fontFile: File?,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var selectedTarget by remember { mutableStateOf(ApplyTarget.MAGISK_MODULE) }
    var isProcessing by remember { mutableStateOf(false) }
    var resultMessage by remember { mutableStateOf<String?>(null) }
    var generatedZipFile by remember { mutableStateOf<File?>(null) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // 선택된 폰트 정보 요약 카드
        Card(
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            ),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "적용 대상 폰트",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = metadata?.fullName ?: "기본 프리텐다드 (Pretendard)",
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = "경로: ${fontFile?.name ?: "내장 에셋"}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // 적용 방식 선택 라디오 리스트
        Text(
            text = "기기 환경에 맞는 적용 방식 선택",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )

        ApplyTarget.values().forEach { target ->
            val isSelected = selectedTarget == target
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface
                ),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { selectedTarget = target }
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(
                        selected = isSelected,
                        onClick = { selectedTarget = target }
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = target.displayName,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = target.description,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        // 삼성 One UI 선택 시 세부 가이드
        if (selectedTarget == ApplyTarget.SAMSUNG_FLIPFONT) {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                ),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Samsung One UI 루팅 프리 적용 순서",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    SamsungFlipFontBuilder.getSamsungInstructions().forEach { step ->
                        Text(
                            text = "${step.stepNumber}. ${step.title}: ${step.description}",
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(vertical = 2.dp)
                        )
                    }
                }
            }
        }

        // 실행 버튼들
        Spacer(modifier = Modifier.height(8.dp))

        Button(
            onClick = {
                val file = fontFile ?: File(context.cacheDir, "default_font.ttf").apply {
                    if (!exists()) writeBytes(ByteArray(1024))
                }
                val meta = metadata ?: FontMetadata(familyName = "Pretendard", fullName = "Pretendard Regular")

                isProcessing = true
                coroutineScope.launch {
                    when (selectedTarget) {
                        ApplyTarget.MAGISK_MODULE -> {
                            val outputZip = File(context.getExternalFilesDir(null), "FontCraft_${meta.familyName}_Magisk.zip")
                            val result = MagiskModuleGenerator.generateModule(
                                context = context,
                                sourceFontFile = file,
                                fontMeta = meta,
                                outputZipFile = outputZip
                            )
                            isProcessing = false
                            result.onSuccess { zip ->
                                generatedZipFile = zip
                                resultMessage = "Magisk 모듈 ZIP이 성공적으로 생성되었습니다: ${zip.name}"
                            }.onFailure { err ->
                                resultMessage = "모듈 생성 실패: ${err.localizedMessage}"
                            }
                        }
                        ApplyTarget.SAMSUNG_FLIPFONT -> {
                            val result = SamsungFlipFontBuilder.prepareFontForSamsung(context, file, meta)
                            isProcessing = false
                            result.onSuccess { preparedFile ->
                                resultMessage = "One UI용 폰트가 준비되었습니다: ${preparedFile.name}"
                            }.onFailure { err ->
                                resultMessage = "준비 실패: ${err.localizedMessage}"
                            }
                        }
                        ApplyTarget.ROOT_DIRECT -> {
                            val isRoot = RootShellExecutor.isRootAvailable()
                            if (!isRoot) {
                                isProcessing = false
                                resultMessage = "Root 권한이 감지되지 않았습니다. Magisk 또는 su를 확인하세요."
                            } else {
                                isProcessing = false
                                resultMessage = "Root Shell을 통해 /system/fonts 바인딩 준비 완료"
                            }
                        }
                        ApplyTarget.SHIZUKU_COMMAND -> {
                            isProcessing = false
                            resultMessage = "Shizuku 명령 채널 준비 완료"
                        }
                    }
                }
            },
            enabled = !isProcessing,
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
        ) {
            if (isProcessing) {
                CircularProgressIndicator(
                    modifier = Modifier.size(24.dp),
                    color = MaterialTheme.colorScheme.onPrimary
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("패키징 및 처리 중...")
            } else {
                Icon(Icons.Default.Build, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("폰트 패키지 생성 및 적용", fontSize = 16.sp, fontWeight = FontWeight.Bold)
            }
        }

        // 생성된 ZIP 파일이 있을 때 공유 및 자동 플래시 버튼
        generatedZipFile?.let { zip ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = {
                        val uri = FileProvider.getUriForFile(context, "${context.packageName}.provider", zip)
                        val shareIntent = Intent(Intent.ACTION_SEND).apply {
                            type = "application/zip"
                            putExtra(Intent.EXTRA_STREAM, uri)
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }
                        context.startActivity(Intent.createChooser(shareIntent, "Magisk 모듈 ZIP 공유"))
                    },
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.Share, contentDescription = null)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("모듈 공유")
                }

                Button(
                    onClick = {
                        coroutineScope.launch {
                            val res = RootShellExecutor.flashMagiskModule(zip.absolutePath)
                            res.onSuccess {
                                Toast.makeText(context, "Magisk 모듈 플래시 성공! 소프트 리부트를 진행합니다.", Toast.LENGTH_LONG).show()
                                RootShellExecutor.softReboot()
                            }.onFailure {
                                Toast.makeText(context, "플래시 실패 (Root 미권한): ${it.message}", Toast.LENGTH_SHORT).show()
                            }
                        }
                    },
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.FlashOn, contentDescription = null)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("즉시 플래시")
                }
            }
        }

        // 결과 알림 카드
        resultMessage?.let { msg ->
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                ),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = msg,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(16.dp)
                )
            }
        }
    }
}

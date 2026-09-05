package com.openfont.changer.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.openfont.changer.data.model.CustomFontConfig
import com.openfont.changer.data.model.FontMetadata
import com.openfont.changer.engine.FontParser
import java.io.File
import java.io.FileOutputStream

@Composable
fun CustomFontScreen(
    currentMetadata: FontMetadata?,
    onMetadataChanged: (FontMetadata, File) -> Unit,
    onNavigateToApply: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var config by remember { mutableStateOf(CustomFontConfig()) }
    var selectedPreviewTab by remember { mutableStateOf(0) } // 0: 문장, 1: 숫자/기호, 2: UI 모의체험
    var activeFontFile by remember { mutableStateOf<File?>(null) }

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            val tempFile = File(context.cacheDir, "imported_font.ttf")
            context.contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(tempFile).use { output ->
                    input.copyTo(output)
                }
            }
            val meta = FontParser.parse(tempFile)
            activeFontFile = tempFile
            onMetadataChanged(meta, tempFile)
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // 타이틀 및 파일 선택 버튼
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
                            text = currentMetadata?.fullName ?: "커스텀 폰트 불러오기",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = if (currentMetadata != null) {
                                "패밀리: ${currentMetadata.familyName} · 스타일: ${currentMetadata.styleName}"
                            } else {
                                "내 기기의 .ttf / .otf 글꼴 파일을 직접 선택하세요"
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                Button(
                    onClick = { filePickerLauncher.launch("*/*") },
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.FolderOpen, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("TTF / OTF 글꼴 파일 열기")
                }
            }
        }

        // 실시간 커스텀 슬라이더 컨트롤러
        Card(
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            ),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "글꼴 타이포그래피 미세 조정",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.height(12.dp))

                // 글꼴 크기 배율
                Text(
                    text = "폰트 크기 배율: ${(config.fontSizeScale * 100).toInt()}%",
                    style = MaterialTheme.typography.labelMedium
                )
                Slider(
                    value = config.fontSizeScale,
                    onValueChange = { config = config.copy(fontSizeScale = it) },
                    valueRange = 0.8f..1.4f
                )

                // 자간 (Letter Spacing)
                Text(
                    text = "자간 (Letter Spacing): ${String.format("%.2f", config.letterSpacing)} em",
                    style = MaterialTheme.typography.labelMedium
                )
                Slider(
                    value = config.letterSpacing,
                    onValueChange = { config = config.copy(letterSpacing = it) },
                    valueRange = -0.05f..0.20f
                )

                // 행간 (Line Height)
                Text(
                    text = "행간 (Line Height): ${String.format("%.2f", config.lineHeightMultiplier)}x",
                    style = MaterialTheme.typography.labelMedium
                )
                Slider(
                    value = config.lineHeightMultiplier,
                    onValueChange = { config = config.copy(lineHeightMultiplier = it) },
                    valueRange = 1.0f..2.0f
                )

                // 웨이트 (가중치)
                Text(
                    text = "목표 굵기 (Weight): ${config.targetWeight}",
                    style = MaterialTheme.typography.labelMedium
                )
                Slider(
                    value = config.targetWeight.toFloat(),
                    onValueChange = { config = config.copy(targetWeight = it.toInt()) },
                    valueRange = 100f..900f,
                    steps = 7
                )
            }
        }

        // 실시간 미리보기 캔버스
        Card(
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            ),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                TabRow(
                    selectedTabIndex = selectedPreviewTab,
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                ) {
                    Tab(
                        selected = selectedPreviewTab == 0,
                        onClick = { selectedPreviewTab = 0 },
                        text = { Text("문장 미리보기") }
                    )
                    Tab(
                        selected = selectedPreviewTab == 1,
                        onClick = { selectedPreviewTab = 1 },
                        text = { Text("숫자/기호") }
                    )
                    Tab(
                        selected = selectedPreviewTab == 2,
                        onClick = { selectedPreviewTab = 2 },
                        text = { Text("시스템 UI 모의") }
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.background)
                        .padding(16.dp)
                ) {
                    val fontSize = (16 * config.fontSizeScale).sp
                    val letterSpacing = config.letterSpacing.sp
                    val lineHeight = (fontSize.value * config.lineHeightMultiplier).sp
                    val fontWeight = FontWeight(config.targetWeight)

                    when (selectedPreviewTab) {
                        0 -> {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(
                                    text = "다람쥐 헌 쳇바퀴에 타고파. 모든 인간은 존엄성을 지닌다.",
                                    fontSize = fontSize,
                                    letterSpacing = letterSpacing,
                                    lineHeight = lineHeight,
                                    fontWeight = fontWeight
                                )
                                Text(
                                    text = "The quick brown fox jumps over the lazy dog.",
                                    fontSize = fontSize,
                                    letterSpacing = letterSpacing,
                                    lineHeight = lineHeight,
                                    fontWeight = fontWeight
                                )
                            }
                        }
                        1 -> {
                            Text(
                                text = "0123456789\n!@#$%^&*()_+-=[]{}|;':\",./<>?",
                                fontSize = fontSize,
                                letterSpacing = letterSpacing,
                                lineHeight = lineHeight,
                                fontWeight = fontWeight
                            )
                        }
                        2 -> {
                            // 모바일 알림창 / 메시지 UI 시뮬레이션
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(MaterialTheme.colorScheme.surfaceVariant)
                                    .padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text(
                                    text = "메시지 · 지금",
                                    fontSize = (12 * config.fontSizeScale).sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = "오늘 저녁 7시에 약속 있는 거 잊지 않았지?",
                                    fontSize = fontSize,
                                    letterSpacing = letterSpacing,
                                    fontWeight = fontWeight
                                )
                            }
                        }
                    }
                }
            }
        }

        // 적용 화면 이동 버튼
        Button(
            onClick = onNavigateToApply,
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
        ) {
            Icon(Icons.Default.RocketLaunch, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text("시스템에 적용하기", fontSize = 16.sp, fontWeight = FontWeight.Bold)
        }
    }
}

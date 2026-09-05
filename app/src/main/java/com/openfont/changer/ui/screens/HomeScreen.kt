package com.openfont.changer.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.FontDownload
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.openfont.changer.FontCraftApp
import com.openfont.changer.data.model.PresetFont
import com.openfont.changer.data.repository.FontRepository
import com.openfont.changer.engine.RootShellExecutor
import kotlinx.coroutines.launch

@Composable
fun HomeScreen(
    onSelectPreset: (PresetFont) -> Unit,
    modifier: Modifier = Modifier
) {
    val repository = remember { FontRepository() }
    val presetFonts = remember { repository.getPopularPresetFonts() }
    val coroutineScope = rememberCoroutineScope()

    var isRooted by remember { mutableStateOf(false) }
    var isShizukuAvailable by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        coroutineScope.launch {
            isRooted = RootShellExecutor.isRootAvailable()
            isShizukuAvailable = FontCraftApp.isShizukuAvailable()
        }
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding = PaddingValues(vertical = 16.dp)
    ) {
        item {
            // 헤더 배너
            Column(modifier = Modifier.padding(vertical = 8.dp)) {
                Text(
                    text = "FontCraft",
                    style = MaterialTheme.typography.headlineLarge,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = "안드로이드 시스템 폰트 커스텀 & 변환 엔진",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        item {
            // 시스템 환경 진단 카드
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                ),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "시스템 환경 진단",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = if (isRooted) Icons.Default.CheckCircle else Icons.Default.Warning,
                            contentDescription = null,
                            tint = if (isRooted) Color(0xFF10B981) else Color(0xFFF59E0B)
                        )
                        Text(
                            text = if (isRooted) "Root (Magisk / KSU) 활성화됨" else "Root 권한 없음 (삼성 One UI 또는 Shizuku 모드 권장)",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = if (isShizukuAvailable) Icons.Default.CheckCircle else Icons.Default.Warning,
                            contentDescription = null,
                            tint = if (isShizukuAvailable) Color(0xFF10B981) else Color(0xFF94A3B8)
                        )
                        Text(
                            text = if (isShizukuAvailable) "Shizuku 바인더 연동됨" else "Shizuku 비활성화",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }
        }

        item {
            Text(
                text = "인기 오픈소스 폰트",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(top = 8.dp)
            )
        }

        items(presetFonts) { preset ->
            FontPresetCard(
                preset = preset,
                onClick = { onSelectPreset(preset) }
            )
        }
    }
}

@Composable
fun FontPresetCard(
    preset: PresetFont,
    onClick: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = preset.name,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "${preset.designer} · ${preset.license}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                FilledTonalButton(
                    onClick = onClick,
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.FontDownload,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("커스텀")
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // 프리뷰 텍스트 박스
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                    .padding(12.dp)
            ) {
                Text(
                    text = preset.previewText,
                    fontSize = 15.sp,
                    lineHeight = 22.sp
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            // 태그 목록
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                preset.tags.forEach { tag ->
                    SuggestionChip(
                        onClick = {},
                        label = { Text(tag, fontSize = 11.sp) }
                    )
                }
            }
        }
    }
}

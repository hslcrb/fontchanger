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

    // 100% 단독 실행 모드


    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding = PaddingValues(vertical = 16.dp)
    ) {
        item {
            // 헤더 배너
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(vertical = 8.dp)
            ) {
                Icon(
                    painter = androidx.compose.ui.res.painterResource(id = com.openfont.changer.R.drawable.ic_logo_symbol),
                    contentDescription = "Logo",
                    tint = Color.Unspecified,
                    modifier = Modifier.size(48.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = "FontCraft",
                        style = MaterialTheme.typography.headlineLarge,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "안드로이드 시스템 폰트 커스텀 & 구글 타임라인 GPK 엔진",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        item {
            // 시스템 단독 실행 상태 카드 (루트 및 Shizuku 완전 불필요)
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                ),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = Color(0xFF10B981),
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "단독 실행 모드 활성화됨",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Shizuku나 루팅 등 어떤 외부 프로그램도 필요 없습니다. 폰트 커스텀 렌더링, 인앱 지도, 백그라운드 GPK 타임라인이 앱 자체만으로 완벽하게 동작합니다.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
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

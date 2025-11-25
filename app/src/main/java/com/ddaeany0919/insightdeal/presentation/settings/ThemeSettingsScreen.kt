package com.ddaeany0919.insightdeal.presentation.settings

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.*
import com.ddaeany0919.insightdeal.presentation.theme.ThemeManager
import com.ddaeany0919.insightdeal.presentation.theme.ThemeMode
import com.ddaeany0919.insightdeal.presentation.theme.AppColorScheme

/**
 * 🎨 테마 설정 화면 (AMOLED 및 컴러 테마 지원)
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ThemeSettingsScreen(
    onBackClick: () -> Unit
) {
    val context = LocalContext.current
    val themeManager = remember { ThemeManager.getInstance(context) }

    val currentTheme by themeManager.themeMode.collectAsState()
    val currentColorScheme by themeManager.colorScheme.collectAsState()
    val amoledMode by themeManager.amoledMode.collectAsState()
    var showThemePreview by remember { mutableStateOf(false) }

    // 현재 시간 기반 자동 다크모드 상태
    val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
    val isNightTime = hour >= 19 || hour < 7

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "🎨 테마 설정",
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "뒤로가기")
                    }
                },
                actions = {
                    IconButton(
                        onClick = { showThemePreview = !showThemePreview }
                    ) {
                        Icon(
                            if (showThemePreview) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                            contentDescription = "미리보기",
                            tint = if (showThemePreview) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            )
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 📱 테마 모드 선택
            item {
                ThemeModeSection(
                    currentTheme = currentTheme,
                    onThemeChange = { newTheme ->
                        themeManager.setThemeMode(newTheme)
                    },
                    isNightTime = isNightTime,
                    amoledMode = amoledMode,
                    onAmoledToggle = { enabled ->
                        themeManager.setAmoledMode(enabled)
                    }
                )
            }

            // 🎨 컬러 테마 선택
            item {
                ColorThemeSection(
                    currentColorScheme = currentColorScheme,
                    onColorSchemeChange = { newScheme ->
                        themeManager.setColorScheme(newScheme)
                    }
                )
            }

            // 🔍 테마 미리보기 (토글시 표시)
            if (showThemePreview) {
                item {
                    ThemePreviewSection(currentTheme = currentTheme)
                }
            }

            // ℹ️ 설명 섹션
            item {
                ThemeInfoSection(isNightTime = isNightTime)
            }
        }
    }
}

@Composable
private fun ThemeModeSection(
    currentTheme: ThemeMode,
    onThemeChange: (ThemeMode) -> Unit,
    isNightTime: Boolean,
    amoledMode: Boolean,
    onAmoledToggle: (Boolean) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = "🌙 다크모드 설정",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 12.dp)
            )

            // LIGHT 모드
            ThemeOptionItem(
                title = "☀️ 라이트 모드",
                subtitle = "밝은 화면 고정 (낮에 최적)",
                icon = Icons.Default.LightMode,
                isSelected = currentTheme == ThemeMode.LIGHT,
                onClick = { onThemeChange(ThemeMode.LIGHT) }
            )

            Spacer(modifier = Modifier.height(8.dp))

            // DARK 모드
            ThemeOptionItem(
                title = "🌙 다크 모드",
                subtitle = "어두운 화면 고정 (눈의 피로 감소)",
                icon = Icons.Default.DarkMode,
                isSelected = currentTheme == ThemeMode.DARK,
                onClick = { onThemeChange(ThemeMode.DARK) }
            )

            Spacer(modifier = Modifier.height(8.dp))

            // AMOLED 모드
            ThemeOptionItem(
                title = "🖤 AMOLED 블랙",
                subtitle = "완전한 검은색 배경 (배터리 절약)",
                icon = Icons.Default.Brightness2,
                isSelected = currentTheme == ThemeMode.AMOLED,
                onClick = { onThemeChange(ThemeMode.AMOLED) }
            )

            Spacer(modifier = Modifier.height(8.dp))

            // SYSTEM 모드
            ThemeOptionItem(
                title = "📱 시스템 따라가기",
                subtitle = "기기 다크모드 설정에 따라 자동 변경",
                icon = Icons.Default.PhoneAndroid,
                isSelected = currentTheme == ThemeMode.SYSTEM,
                onClick = { onThemeChange(ThemeMode.SYSTEM) }
            )

            Spacer(modifier = Modifier.height(16.dp))

            if (currentTheme != ThemeMode.AMOLED) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
                
                Spacer(modifier = Modifier.height(16.dp))

                // AMOLED 토글
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { onAmoledToggle(!amoledMode) }
                        .background(
                            if (amoledMode) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                            else Color.Transparent
                        )
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Brightness2,
                        contentDescription = null,
                        modifier = Modifier.size(24.dp),
                        tint = if (amoledMode) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.width(12.dp))

                    Column(
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(
                            text = "🖤 AMOLED 강화",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Medium,
                            color = if (amoledMode) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurface
                        )

                        Text(
                            text = "다크모드에서 완전 검은색 사용",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Switch(
                        checked = amoledMode,
                        onCheckedChange = onAmoledToggle,
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = MaterialTheme.colorScheme.primary
                        )
                    )
                }
            }
        }
    }
}

@Composable
private fun ColorThemeSection(
    currentColorScheme: AppColorScheme,
    onColorSchemeChange: (AppColorScheme) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = "🎨 컬러 테마",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 12.dp)
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                // 오렌지
                ColorThemeButton(
                    color = Color(0xFFFF6B35),
                    name = "오렌지",
                    emoji = "🍊",
                    isSelected = currentColorScheme == AppColorScheme.ORANGE_CLASSIC,
                    onClick = { onColorSchemeChange(AppColorScheme.ORANGE_CLASSIC) }
                )

                // 블루
                ColorThemeButton(
                    color = Color(0xFF2196F3),
                    name = "블루",
                    emoji = "🔵",
                    isSelected = currentColorScheme == AppColorScheme.BLUE_MODERN,
                    onClick = { onColorSchemeChange(AppColorScheme.BLUE_MODERN) }
                )

                // 그린
                ColorThemeButton(
                    color = Color(0xFF4CAF50),
                    name = "그린",
                    emoji = "🔍",
                    isSelected = currentColorScheme == AppColorScheme.GREEN_NATURAL,
                    onClick = { onColorSchemeChange(AppColorScheme.GREEN_NATURAL) }
                )

                // 퍼플
                ColorThemeButton(
                    color = Color(0xFF9C27B0),
                    name = "퍼플",
                    emoji = "🔮",
                    isSelected = currentColorScheme == AppColorScheme.PURPLE_LUXURY,
                    onClick = { onColorSchemeChange(AppColorScheme.PURPLE_LUXURY) }
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                )
            ) {
                Column(
                    modifier = Modifier.padding(12.dp)
                ) {
                    Text(
                        text = "선택한 테마: ${currentColorScheme.displayName}",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.primary
                    )

                    Text(
                        text = currentColorScheme.description,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun ThemeOptionItem(
    title: String,
    subtitle: String,
    icon: ImageVector,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable { onClick() }
            .background(
                if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                else Color.Transparent
            )
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(24.dp),
            tint = if (isSelected) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.width(12.dp))

        Column(
            modifier = Modifier.weight(1f)
        ) {
            Text(
                text = title,
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
                color = if (isSelected) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurface
            )

            Text(
                text = subtitle,
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        RadioButton(
            selected = isSelected,
            onClick = onClick,
            colors = RadioButtonDefaults.colors(
                selectedColor = MaterialTheme.colorScheme.primary
            )
        )
    }
}

@Composable
private fun ColorThemeButton(
    color: Color,
    name: String,
    emoji: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clickable { onClick() }
            .padding(8.dp)
        ) {
        Box(
            modifier = Modifier
                .size(56.dp)
                .clip(CircleShape)
                .background(color)
                .then(
                    if (isSelected) {
                        Modifier.background(
                            MaterialTheme.colorScheme.outline,
                            CircleShape
                        )
                    } else Modifier
                ),
            contentAlignment = Alignment.Center
        ) {
            if (isSelected) {
                Icon(
                    Icons.Default.Check,
                    contentDescription = "선택됨",
                    tint = Color.White,
                    modifier = Modifier.size(28.dp)
                )
            } else {
                Text(
                    text = emoji,
                    fontSize = 24.sp
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = name,
            fontSize = 12.sp,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
            color = if (isSelected) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun ThemePreviewSection(
    currentTheme: ThemeMode
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = "🔍 테마 미리보기",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 12.dp)
            )

            // 미리보기 카드
            Card(
                modifier = Modifier.fillMaxWidth(),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "🔥 갤럭시 S24 Ultra",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = MaterialTheme.colorScheme.primary
                        ) {
                            Text(
                                text = "30% 할인",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = "1,299,000원 → 909,300원",
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = { },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("구매하기")
                        }

                        OutlinedButton(
                            onClick = { },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("즐겨찾기")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ThemeInfoSection(
    isNightTime: Boolean
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Default.Info,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    tint = MaterialTheme.colorScheme.primary
                )

                Spacer(modifier = Modifier.width(8.dp))

                Text(
                    text = "💡 테마 정보",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = "현재 시간: ${if (isNightTime) "야간 (다크모드 권장)" else "주간 (라이트모드 권장)"}",
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurface
            )
            
            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = "시스템 설정에 따라 자동으로 테마가 변경될 수 있습니다.",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
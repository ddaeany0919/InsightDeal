package com.ddaeany0919.insightdeal

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ThemeSettingsScreen(
    onBackClick: () -> Unit
) {
    val context = LocalContext.current
    val themeManager = remember { ThemeManager.getInstance(context) }
    
    val currentThemeMode by themeManager.themeMode.collectAsState()
    val currentColorScheme by themeManager.colorScheme.collectAsState()
    val amoledMode by themeManager.amoledMode.collectAsState()
    
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
                }
            )
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            verticalArrangement = Arrangement.spacedBy(24.dp),
            contentPadding = PaddingValues(16.dp)
        ) {
            // 🌙 다크모드 설정
            item {
                ThemeModeSection(
                    currentMode = currentThemeMode,
                    onModeChange = themeManager::setThemeMode
                )
            }
            
            // 🖤 AMOLED 모드
            item {
                AmoledModeSection(
                    enabled = amoledMode,
                    onToggle = themeManager::setAmoledMode
                )
            }
            
            // 🎨 컴러 스킴 선택
            item {
                ColorSchemeSection(
                    currentScheme = currentColorScheme,
                    onSchemeChange = themeManager::setColorScheme
                )
            }
            
            // 🎆 테마 미리보기
            item {
                ThemePreviewSection()
            }
        }
    }
}

@Composable
private fun ThemeModeSection(
    currentMode: ThemeMode,
    onModeChange: (ThemeMode) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(
            modifier = Modifier.padding(20.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(bottom = 16.dp)
            ) {
                Icon(
                    Icons.Default.DarkMode,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
                
                Spacer(modifier = Modifier.width(8.dp))
                
                Text(
                    text = "🌙 다크모드 설정",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
            }
            
            ThemeMode.values().forEach { mode ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .selectable(
                            selected = currentMode == mode,
                            onClick = { onModeChange(mode) }
                        )
                        .padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(
                        selected = currentMode == mode,
                        onClick = { onModeChange(mode) }
                    )
                    
                    Spacer(modifier = Modifier.width(12.dp))
                    
                    Column {
                        Text(
                            text = mode.displayName,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Medium
                        )
                        
                        val description = when (mode) {
                            ThemeMode.LIGHT -> "낮에 눈이 편한 밝은 테마"
                            ThemeMode.DARK -> "밤에 눈이 편한 어두운 테마"
                            ThemeMode.SYSTEM -> "핸드폰 시스템 설정을 따라갑니다"
                            ThemeMode.AUTO_TIME -> "오후 7시에 자동으로 다크모드로 전환"
                        }
                        
                        Text(
                            text = description,
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AmoledModeSection(
    enabled: Boolean,
    onToggle: (Boolean) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onToggle(!enabled) }
                .padding(20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Default.PhoneAndroid,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp)
            )
            
            Spacer(modifier = Modifier.width(12.dp))
            
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = "🖤 AMOLED 모드",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
                
                Text(
                    text = "완전 검은색 배경으로 배터리 절약 (갤럭시 S24 최적화)",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
            
            Switch(
                checked = enabled,
                onCheckedChange = onToggle
            )
        }
    }
}

@Composable
private fun ColorSchemeSection(
    currentScheme: AppColorScheme,
    onSchemeChange: (AppColorScheme) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(
            modifier = Modifier.padding(20.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(bottom = 16.dp)
            ) {
                Icon(
                    Icons.Default.Palette,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
                
                Spacer(modifier = Modifier.width(8.dp))
                
                Text(
                    text = "🎨 컴러 테마",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
            }
            
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                items(AppColorScheme.values()) { scheme ->
                    ColorSchemeOption(
                        scheme = scheme,
                        isSelected = scheme == currentScheme,
                        onClick = { onSchemeChange(scheme) }
                    )
                }
            }
        }
    }
}

@Composable
private fun ColorSchemeOption(
    scheme: AppColorScheme,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.clickable { onClick() }
    ) {
        // 컴러 샘플 원
        Box(
            modifier = Modifier
                .size(60.dp)
                .clip(CircleShape)
                .background(scheme.primaryColor)
                .then(
                    if (isSelected) {
                        Modifier.padding(4.dp)
                    } else Modifier
                ),
            contentAlignment = Alignment.Center
        ) {
            if (isSelected) {
                Icon(
                    Icons.Default.Check,
                    contentDescription = "선택됨",
                    tint = Color.White,
                    modifier = Modifier.size(24.dp)
                )
            }
        }
        
        Spacer(modifier = Modifier.height(8.dp))
        
        Text(
            text = scheme.displayName,
            fontSize = 12.sp,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
            color = if (isSelected) MaterialTheme.colorScheme.primary 
                   else MaterialTheme.colorScheme.onSurface
        )
        
        Text(
            text = scheme.description,
            fontSize = 10.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 2,
            modifier = Modifier.width(80.dp)
        )
    }
}

@Composable
private fun ThemePreviewSection() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(
            modifier = Modifier.padding(20.dp)
        ) {
            Text(
                text = "🎆 테마 미리보기",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 16.dp)
            )
            
            // 새로운 테마로 미리보기 상자
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp),
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceVariant
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    // 가상의 핫딜 카드
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surface
                        )
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .background(
                                        MaterialTheme.colorScheme.primary,
                                        RoundedCornerShape(6.dp)
                                    )
                            )
                            
                            Spacer(modifier = Modifier.width(12.dp))
                            
                            Column {
                                Text(
                                    text = "[뽐뿌] 갤럭시 버즈 특가",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Medium
                                )
                                
                                Text(
                                    text = "89,000원",
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                            
                            Spacer(modifier = Modifier.weight(1f))
                            
                            // 꿀딜 지수 배지
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.primary
                            ) {
                                Text(
                                    text = "🔥 95",
                                    fontSize = 12.sp,
                                    color = Color.White,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                        }
                    }
                    
                    // 가상의 버튼
                    Button(
                        onClick = { },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("🚪 실제 앱에서 보기")
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Text(
                text = "💡 테마를 변경하면 전체 앱에 즉시 적용됩니다",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
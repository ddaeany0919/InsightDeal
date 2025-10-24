package com.ddaeany0919.insightdeal

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
import com.ddaeany0919.insightdeal.theme.*
import java.util.*

/**
 * 🎨 테마 설정 화면
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ThemeSettingsScreen(
    onBackClick: () -> Unit
) {
    val context = LocalContext.current
    var currentTheme by remember { mutableStateOf(loadThemeMode(context)) }
    var showThemePreview by remember { mutableStateOf(false) }
    
    // 현재 시간 기반 자동 다크모드 상태
    val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
    val isNightTime = hour >= 19 || hour < 7
    
    LaunchedEffect(currentTheme) {
        saveThemeMode(context, currentTheme)
    }
    
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
                        currentTheme = newTheme
                    },
                    isNightTime = isNightTime
                )
            }
            
            // 🎨 컬러 테마 선택
            item {
                ColorThemeSection(
                    currentTheme = currentTheme,
                    onThemeChange = { newTheme ->
                        currentTheme = newTheme
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
    isNightTime: Boolean
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
            
            // AUTO 모드
            ThemeOptionItem(
                title = "⏰ 자동 전환",
                subtitle = if (isNightTime) "현재 다크모드 (저녁 19시~오전 7시)"
                          else "현재 라이트모드 (오전 7시~저녁 19시)",
                icon = Icons.Default.Schedule,
                isSelected = currentTheme == ThemeMode.AUTO,
                onClick = { onThemeChange(ThemeMode.AUTO) }
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            // LIGHT 모드
            ThemeOptionItem(
                title = "☀️ 라이트 모드",
                subtitle = "밝은 화면 고정",
                icon = Icons.Default.LightMode,
                isSelected = currentTheme == ThemeMode.LIGHT,
                onClick = { onThemeChange(ThemeMode.LIGHT) }
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            // DARK 모드
            ThemeOptionItem(
                title = "🌙 다크 모드",
                subtitle = "어두운 화면 고정",
                icon = Icons.Default.DarkMode,
                isSelected = currentTheme == ThemeMode.DARK,
                onClick = { onThemeChange(ThemeMode.DARK) }
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            // AMOLED 모드
            ThemeOptionItem(
                title = "⚫ AMOLED 블랙",
                subtitle = "완전 검정 배경 (배터리 절약)",
                icon = Icons.Default.PhoneAndroid,
                isSelected = currentTheme == ThemeMode.AMOLED,
                onClick = { onThemeChange(ThemeMode.AMOLED) }
            )
        }
    }
}

@Composable
private fun ColorThemeSection(
    currentTheme: ThemeMode,
    onThemeChange: (ThemeMode) -> Unit
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
                    color = Color(0xFFFF9800),
                    name = "오렌지",
                    isSelected = currentTheme == ThemeMode.ORANGE,
                    onClick = { onThemeChange(ThemeMode.ORANGE) }
                )
                
                // 블루
                ColorThemeButton(
                    color = Color(0xFF2196F3),
                    name = "블루",
                    isSelected = currentTheme == ThemeMode.BLUE,
                    onClick = { onThemeChange(ThemeMode.BLUE) }
                )
                
                // 그린
                ColorThemeButton(
                    color = Color(0xFF4CAF50),
                    name = "그린",
                    isSelected = currentTheme == ThemeMode.GREEN,
                    onClick = { onThemeChange(ThemeMode.GREEN) }
                )
                
                // 퍼플
                ColorThemeButton(
                    color = Color(0xFF9C27B0),
                    name = "퍼플",
                    isSelected = currentTheme == ThemeMode.PURPLE,
                    onClick = { onThemeChange(ThemeMode.PURPLE) }
                )
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
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.clickable { onClick() }
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
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
                    modifier = Modifier.size(24.dp)
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
                text = "• ⏰ 자동 전환: 저녁 19시부터 다음날 오전 7시까지 다크모드\n" +
                      "• 🔋 AMOLED 블랙: 완전 검정 배경으로 배터리 절약\n" +
                      "• 🎨 컬러 테마: 4가지 개성있는 컬러 선택 가능\n" +
                      "• 📱 현재 시간: ${if (isNightTime) "밤 (다크모드 적용 시간)" else "낮 (라이트모드 적용 시간)"}",
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                lineHeight = 20.sp
            )
        }
    }
}
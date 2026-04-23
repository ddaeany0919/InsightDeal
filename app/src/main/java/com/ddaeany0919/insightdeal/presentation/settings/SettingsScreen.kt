package com.ddaeany0919.insightdeal.presentation.settings

import android.content.Context
import android.util.Log
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForwardIos
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ddaeany0919.insightdeal.presentation.theme.ThemeManager
import com.ddaeany0919.insightdeal.presentation.theme.ThemeMode
import coil.imageLoader
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, coil.annotation.ExperimentalCoilApi::class)
@Composable
fun SettingsScreen() {
    val ctx = LocalContext.current
    val tm = remember { ThemeManager.getInstance(ctx) }
    val mode by tm.themeMode.collectAsState()
    rememberCoroutineScope()
    val scrollState = rememberScrollState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("설정", fontWeight = FontWeight.Bold) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onBackground
                )
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(scrollState)
                .padding(16.dp)
        ) {
            // 사용자 계정 섹션
            SettingsSectionTitle("사용자 계정")
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    var currentUserId by remember {
                        val prefs = ctx.getSharedPreferences("app", Context.MODE_PRIVATE)
                        mutableStateOf(prefs.getString("user_id", "guest") ?: "guest")
                    }
                    
                    Text("User ID", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = currentUserId,
                            onValueChange = { currentUserId = it },
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                            textStyle = MaterialTheme.typography.bodyLarge
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(onClick = {
                            ctx.getSharedPreferences("app", Context.MODE_PRIVATE)
                                .edit().putString("user_id", currentUserId).apply()
                        }) {
                            Text("저장")
                        }
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        "서버 연동을 위한 고유 ID입니다.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(24.dp))
            
            // 앱 설정 섹션
            SettingsSectionTitle("앱 설정")
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column {
                    // 테마 설정
                    ExpandableThemeSetting(currentMode = mode) { newMode ->
                        tm.setThemeMode(newMode)
                    }
                    
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                    
                    // 알림 설정 (Mock)
                    var notificationsEnabled by remember { mutableStateOf(ctx.getSharedPreferences("app", Context.MODE_PRIVATE).getBoolean("push_enabled", true)) }
                    SettingsSwitchRow(
                        icon = Icons.Default.Notifications,
                        title = "관심 키워드 푸시 알림",
                        checked = notificationsEnabled,
                        onCheckedChange = { 
                            notificationsEnabled = it 
                            ctx.getSharedPreferences("app", Context.MODE_PRIVATE).edit().putBoolean("push_enabled", it).apply()
                        }
                    )
                    
                    // 데이터 절약 모드 
                    var dataSaverEnabled by remember { mutableStateOf(ctx.getSharedPreferences("app", Context.MODE_PRIVATE).getBoolean("data_saver", false)) }
                    SettingsSwitchRow(
                        icon = Icons.Default.DataUsage,
                        title = "데이터 절약 모드 (저화질 이미지)",
                        checked = dataSaverEnabled,
                        onCheckedChange = { 
                            dataSaverEnabled = it 
                            ctx.getSharedPreferences("app", Context.MODE_PRIVATE).edit().putBoolean("data_saver", it).apply()
                        }
                    )
                    
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                    
                    // 캐시 지우기
                    var cacheSize by remember { mutableStateOf("계산 중...") }
                    
                    LaunchedEffect(Unit) {
                        try {
                            val size = ctx.cacheDir.walkTopDown().filter { it.isFile }.map { it.length() }.sum()
                            cacheSize = if (size > 1024 * 1024) "${size / (1024 * 1024)}MB" else "${size / 1024}KB"
                        } catch (e: Exception) {
                            cacheSize = "0MB"
                        }
                    }

                    SettingsNavigationRow(
                        icon = Icons.Default.CleaningServices, 
                        title = "캐시 데이터 비우기", 
                        value = cacheSize,
                        onClick = {
                            val imageLoader = ctx.imageLoader
                            imageLoader.diskCache?.clear()
                            imageLoader.memoryCache?.clear()
                            try {
                                ctx.cacheDir.deleteRecursively()
                            } catch (e: Exception) {}
                            cacheSize = "0MB"
                            android.widget.Toast.makeText(ctx, "캐시가 정리되었습니다.", android.widget.Toast.LENGTH_SHORT).show()
                        }
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(24.dp))
            
            // 정보 섹션
            SettingsSectionTitle("정보")
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column {
                    SettingsNavigationRow(icon = Icons.Default.Info, title = "앱 버전", value = "1.0.0")
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                    
                    var showLicenses by remember { mutableStateOf(false) }
                    SettingsNavigationRow(icon = Icons.Default.Description, title = "오픈소스 라이선스", onClick = {
                        showLicenses = true
                    })
                    if (showLicenses) {
                        AlertDialog(
                            onDismissRequest = { showLicenses = false },
                            title = { Text("오픈소스 라이선스") },
                            text = { Text("Jetpack Compose\nRetrofit2\nCoil\nMaterial3\n등 구글 및 오픈소스 재단의 라이선스를 준수하여 제작되었습니다.") },
                            confirmButton = { TextButton(onClick = { showLicenses = false }) { Text("확인") } }
                        )
                    }
                    
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                    SettingsNavigationRow(icon = Icons.Default.Email, title = "문의하기", onClick = {
                        val intent = android.content.Intent(android.content.Intent.ACTION_SENDTO, android.net.Uri.parse("mailto:support@insightdeal.com"))
                        intent.putExtra(android.content.Intent.EXTRA_SUBJECT, "[InsightDeal] 고객 문의")
                        try {
                            ctx.startActivity(intent)
                        } catch (e: Exception) {
                            android.widget.Toast.makeText(ctx, "메일 앱을 찾을 수 없습니다.", android.widget.Toast.LENGTH_SHORT).show()
                        }
                    })
                }
            }
            
            Spacer(modifier = Modifier.height(32.dp))
            Text(
                "Insight Deal © 2024",
                modifier = Modifier.fillMaxWidth(),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
        }
    }
}

@Composable
fun SettingsSectionTitle(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(bottom = 8.dp, start = 4.dp)
    )
}

@Composable
fun SettingsSwitchRow(
    icon: ImageVector,
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(imageVector = icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(modifier = Modifier.width(16.dp))
        Text(text = title, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
fun SettingsNavigationRow(
    icon: ImageVector,
    title: String,
    value: String? = null,
    onClick: () -> Unit = {}
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(imageVector = icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(modifier = Modifier.width(16.dp))
        Text(text = title, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        if (value != null) {
            Text(text = value, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(modifier = Modifier.width(8.dp))
        }
        Icon(
            imageVector = Icons.AutoMirrored.Filled.ArrowForwardIos,
            contentDescription = null,
            modifier = Modifier.size(16.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
        )
    }
}

@Composable
fun ExpandableThemeSetting(
    currentMode: ThemeMode,
    onModeSelected: (ThemeMode) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { expanded = !expanded }
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(imageVector = Icons.Default.Palette, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(modifier = Modifier.width(16.dp))
            Text(text = "테마 설정", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
            Text(
                text = when(currentMode) {
                    ThemeMode.SYSTEM -> "시스템 기본"
                    ThemeMode.LIGHT -> "라이트 모드"
                    ThemeMode.DARK -> "다크 모드"
                    ThemeMode.AMOLED -> "블랙(AMOLED)"
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary
            )
        }
        
        if (expanded) {
            Column(modifier = Modifier.padding(start = 56.dp, end = 16.dp, bottom = 16.dp)) {
                ThemeOptionRow("시스템 기본", ThemeMode.SYSTEM, currentMode, onModeSelected)
                ThemeOptionRow("라이트 모드", ThemeMode.LIGHT, currentMode, onModeSelected)
                ThemeOptionRow("다크 모드", ThemeMode.DARK, currentMode, onModeSelected)
                ThemeOptionRow("블랙(AMOLED)", ThemeMode.AMOLED, currentMode, onModeSelected)
            }
        }
    }
}

@Composable
private fun ThemeOptionRow(
    label: String,
    value: ThemeMode,
    current: ThemeMode,
    onSelect: (ThemeMode) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onSelect(value) }
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(
            selected = current == value,
            onClick = { onSelect(value) },
            modifier = Modifier.size(20.dp)
        )
        Spacer(Modifier.width(12.dp))
        Text(label, style = MaterialTheme.typography.bodyMedium)
    }
}

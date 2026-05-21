package com.ddaeany0919.insightdeal.presentation.settings

import android.content.Context
import android.util.Log
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForwardIos
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ddaeany0919.insightdeal.presentation.theme.ThemeManager
import com.ddaeany0919.insightdeal.presentation.theme.ThemeMode
import coil.imageLoader
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import com.ddaeany0919.insightdeal.presentation.auth.AuthManager
import com.ddaeany0919.insightdeal.presentation.components.PinLockDialog
import com.ddaeany0919.insightdeal.presentation.mypage.history.RecentDealManager
import com.ddaeany0919.insightdeal.core.security.EncryptedPrefsManager

@OptIn(ExperimentalMaterial3Api::class, coil.annotation.ExperimentalCoilApi::class)
@Composable
fun SettingsScreen(
    onBackClick: () -> Unit,
    onNavigateToKeywordManager: () -> Unit
) {
    val ctx = LocalContext.current
    val tm = remember { ThemeManager.getInstance(ctx) }
    val mode by tm.themeMode.collectAsState()
    val scope = rememberCoroutineScope()
    val scrollState = rememberScrollState()

    val savedUsername by AuthManager.getUsername(ctx).collectAsState(initial = "admin")
    val savedNickname by AuthManager.getNickname(ctx).collectAsState(initial = "admin")

    // 하드코딩된 진한 빨간색 대신 앱 테마의 기본 색상(Primary)을 사용합니다.
    val brandAccent = MaterialTheme.colorScheme.primary

    // SharedPreferences 연동
    val dealPrefs = remember { EncryptedPrefsManager.getEncryptedPrefs(ctx) }
    val appPrefs = remember { ctx.getSharedPreferences("app", Context.MODE_PRIVATE) }

    // PIN 다이얼로그 제어 상태들
    var showPinDialog by remember { mutableStateOf(false) }
    var pinDialogSetupMode by remember { mutableStateOf(true) }
    var pinCorrectPin by remember { mutableStateOf("") }
    var pinSuccessCallback by remember { mutableStateOf<((String) -> Unit)?>(null) }

    var isPinLockedEnabled by remember { 
        mutableStateOf(dealPrefs.getBoolean("app_lock_enabled", false)) 
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("설정", fontWeight = FontWeight.Black, fontSize = 22.sp) },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "뒤로가기"
                        )
                    }
                },
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
            // ==========================================
            // 1. 사용자 계정 섹션
            // ==========================================
            SettingsSectionTitle("사용자 계정")
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp),
                shape = RoundedCornerShape(20.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    var currentUserId by remember(savedUsername) { mutableStateOf(savedUsername ?: "admin") }
                    
                    Text("고유 User ID", style = MaterialTheme.typography.labelLarge, color = brandAccent, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = currentUserId,
                            onValueChange = { currentUserId = it },
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                            textStyle = MaterialTheme.typography.bodyLarge,
                            shape = RoundedCornerShape(14.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = brandAccent,
                                focusedLabelColor = brandAccent
                            )
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Button(
                            onClick = {
                                scope.launch {
                                    AuthManager.saveUser(ctx, currentUserId, savedNickname ?: "admin")
                                    android.widget.Toast.makeText(ctx, "사용자 계정 정보가 성공적으로 저장되었습니다.", android.widget.Toast.LENGTH_SHORT).show()
                                }
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primaryContainer,
                                contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                            ),
                            shape = RoundedCornerShape(14.dp),
                            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
                            modifier = Modifier.height(56.dp)
                        ) {
                            Text("저장", fontWeight = FontWeight.ExtraBold, fontSize = 15.sp)
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "서버 데이터 연동 및 맞춤 추천 제공을 위한 고유 ID 식별 값입니다.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(24.dp))
            
            // ==========================================
            // 2. 앱 설정 섹션
            // ==========================================
            SettingsSectionTitle("앱 설정")
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp),
                shape = RoundedCornerShape(20.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column {
                    // 테마 설정
                    ExpandableThemeSetting(currentMode = mode) { newMode ->
                        tm.setThemeMode(newMode)
                    }
                    
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                    
                    // 알림 설정
                    var notificationsEnabled by remember { 
                        mutableStateOf(appPrefs.getBoolean("push_enabled", true)) 
                    }
                    SettingsSwitchRow(
                        icon = Icons.Default.Notifications,
                        title = "관심 키워드 실시간 알림",
                        checked = notificationsEnabled,
                        onCheckedChange = { 
                            notificationsEnabled = it 
                            appPrefs.edit().putBoolean("push_enabled", it).apply()
                        }
                    )
                    
                    // 🔔 키워드 및 푸시 알림 설정 (DND 포함) 진입로 추가
                    SettingsNavigationRow(
                        icon = Icons.Default.NotificationsActive,
                        title = "키워드 및 푸시 알림 설정",
                        value = "키워드 등록/DND",
                        onClick = onNavigateToKeywordManager
                    )
                    
                    // 신규 피처: 야간 방해금지 시간대 설정 UI
                    var nightDndEnabled by remember {
                        mutableStateOf(dealPrefs.getBoolean("night_push_consent", false))
                    }
                    SettingsSwitchRow(
                        icon = Icons.Default.DoNotDisturbOn,
                        title = "야간 방해금지 모드 (21:00 ~ 08:00)",
                        subtitle = "활성화 시 밤 9시부터 다음날 아침 8시까지 푸시 알림 수신이 완전히 차단됩니다.",
                        checked = nightDndEnabled,
                        onCheckedChange = { isChecked ->
                            nightDndEnabled = isChecked
                            dealPrefs.edit().putBoolean("night_push_consent", isChecked).apply()
                            
                            android.widget.Toast.makeText(
                                ctx, 
                                if (isChecked) "야간 방해금지가 설정되었습니다." else "야간 방해금지가 해제되었습니다.", 
                                android.widget.Toast.LENGTH_SHORT
                            ).show()
                        }
                    )
                    
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                    
                    // 데이터 절약 모드 
                    var dataSaverEnabled by remember { mutableStateOf(appPrefs.getBoolean("data_saver", false)) }
                    SettingsSwitchRow(
                        icon = Icons.Default.DataUsage,
                        title = "데이터 절약 모드 (저화질 이미지)",
                        checked = dataSaverEnabled,
                        onCheckedChange = { 
                            dataSaverEnabled = it 
                            appPrefs.edit().putBoolean("data_saver", it).apply()
                        }
                    )
                    
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                    
                    // 신규 피처: 캐시 소모량 시각적 프로그레스 바 영역
                    var cacheSize by remember { mutableStateOf("계산 중...") }
                    val maxRecommendedSize = 50f // 50MB 기준
                    
                    LaunchedEffect(Unit) {
                        try {
                            val size = ctx.cacheDir.walkTopDown().filter { it.isFile }.map { it.length() }.sum()
                            cacheSize = if (size > 1024 * 1024) "${size / (1024 * 1024)}MB" else "${size / 1024}KB"
                        } catch (e: Exception) {
                            cacheSize = "0MB"
                        }
                    }

                    val cacheMegabytes = remember(cacheSize) {
                        try {
                            if (cacheSize.endsWith("MB")) {
                                cacheSize.removeSuffix("MB").toFloatOrNull() ?: 0f
                            } else if (cacheSize.endsWith("KB")) {
                                (cacheSize.removeSuffix("KB").toFloatOrNull() ?: 0f) / 1024f
                            } else 0f
                        } catch (e: Exception) {
                            0f
                        }
                    }

                    val cacheProgress = (cacheMegabytes / maxRecommendedSize).coerceIn(0f, 1f)

                    Column(modifier = Modifier.fillMaxWidth()) {
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
                                android.widget.Toast.makeText(ctx, "임시 캐시 및 이미지 캐시가 깔끔하게 비워졌습니다.", android.widget.Toast.LENGTH_SHORT).show()
                            }
                        )

                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp)
                                .padding(bottom = 16.dp)
                        ) {
                            LinearProgressIndicator(
                                progress = { cacheProgress },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(8.dp)
                                    .background(
                                        MaterialTheme.colorScheme.surfaceVariant,
                                        shape = RoundedCornerShape(4.dp)
                                    ),
                                color = brandAccent,
                                trackColor = MaterialTheme.colorScheme.surfaceVariant
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "원활한 앱 이용을 위해 50MB 이내 유지를 권장합니다.",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = "${String.format("%.1f", cacheProgress * 100)}%",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = brandAccent,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(24.dp))

            // ==========================================
            // 3. 신규 피처: 보안 설정 섹션 (PIN 잠금)
            // ==========================================
            SettingsSectionTitle("보안 설정")
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp),
                shape = RoundedCornerShape(20.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column {
                    SettingsSwitchRow(
                        icon = Icons.Default.Lock,
                        title = "PIN 비밀번호 앱 잠금",
                        subtitle = "앱 시작 시 4자리 PIN 입력을 강제하여 소중한 개인정보를 안전하게 보호합니다.",
                        checked = isPinLockedEnabled,
                        onCheckedChange = { isChecked ->
                            if (isChecked) {
                                // 켜려고 할 때 -> PIN 신규 등록 다이얼로그 호출
                                pinDialogSetupMode = true
                                pinCorrectPin = ""
                                pinSuccessCallback = { newPin ->
                                    dealPrefs.edit()
                                        .putBoolean("app_lock_enabled", true)
                                        .putString("app_lock_pin", newPin)
                                        .apply()
                                    isPinLockedEnabled = true
                                    android.widget.Toast.makeText(ctx, "PIN 잠금이 활성화되었습니다.", android.widget.Toast.LENGTH_SHORT).show()
                                }
                                showPinDialog = true
                            } else {
                                // 끄려고 할 때 -> 기존 PIN 검증 다이얼로그 호출
                                val correctPin = dealPrefs.getString("app_lock_pin", "") ?: ""
                                if (correctPin.isEmpty()) {
                                    // 기존 PIN이 없으면 바로 꺼줌
                                    dealPrefs.edit().putBoolean("app_lock_enabled", false).apply()
                                    isPinLockedEnabled = false
                                } else {
                                    pinDialogSetupMode = false
                                    pinCorrectPin = correctPin
                                    pinSuccessCallback = {
                                        dealPrefs.edit().putBoolean("app_lock_enabled", false).apply()
                                        isPinLockedEnabled = false
                                        android.widget.Toast.makeText(ctx, "보안 PIN 잠금이 성공적으로 해제되었습니다.", android.widget.Toast.LENGTH_SHORT).show()
                                    }
                                    showPinDialog = true
                                }
                            }
                        }
                    )

                    if (isPinLockedEnabled) {
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                        SettingsNavigationRow(
                            icon = Icons.Default.LockReset,
                            title = "PIN 비밀번호 변경",
                            onClick = {
                                pinDialogSetupMode = true
                                pinCorrectPin = ""
                                pinSuccessCallback = { newPin ->
                                    dealPrefs.edit()
                                        .putString("app_lock_pin", newPin)
                                        .apply()
                                    android.widget.Toast.makeText(ctx, "PIN 비밀번호가 새롭게 변경되었습니다.", android.widget.Toast.LENGTH_SHORT).show()
                                }
                                showPinDialog = true
                            }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
            
            // ==========================================
            // 4. 플랫폼(스크래퍼) 필터링 섹션
            // ==========================================
            SettingsSectionTitle("플랫폼(출처) 필터링")
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp),
                shape = RoundedCornerShape(20.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
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
                        Icon(
                            imageVector = Icons.Default.FilterList, 
                            contentDescription = null, 
                            tint = brandAccent
                        )
                        Spacer(modifier = Modifier.width(16.dp))
                        Text(
                            text = "표시할 커뮤니티 선택", 
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold), 
                            modifier = Modifier.weight(1f)
                        )
                        Icon(
                            imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    AnimatedVisibility(
                        visible = expanded,
                        enter = expandVertically(),
                        exit = shrinkVertically()
                    ) {
                        val allPlatforms = listOf("뽐뿌", "퀘이사존", "펨코", "루리웹", "클리앙", "알리뽐뿌", "빠삭국내", "빠삭해외")
                        var disabledPlatforms by remember { 
                            mutableStateOf(appPrefs.getStringSet("disabled_platforms", emptySet()) ?: emptySet()) 
                        }
                        
                        Column(modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp)) {
                            allPlatforms.forEachIndexed { index, platform ->
                                SettingsSwitchRow(
                                    icon = Icons.Default.CheckCircle,
                                    title = "$platform",
                                    checked = !disabledPlatforms.contains(platform),
                                    onCheckedChange = { isEnabled ->
                                        val newSet = disabledPlatforms.toMutableSet()
                                        if (isEnabled) {
                                            newSet.remove(platform)
                                        } else {
                                            newSet.add(platform)
                                        }
                                        disabledPlatforms = newSet
                                        appPrefs.edit().putStringSet("disabled_platforms", newSet).apply()
                                    }
                                )
                                if (index < allPlatforms.size - 1) {
                                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                                }
                            }
                        }
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(24.dp))
            
            // ==========================================
            // 5. 정보 섹션
            // ==========================================
            SettingsSectionTitle("정보")
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp),
                shape = RoundedCornerShape(20.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
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
                            title = { Text("오픈소스 라이선스", fontWeight = FontWeight.Bold) },
                            text = { Text("Jetpack Compose\nRetrofit2\nCoil\nMaterial3\n등 구글 및 오픈소스 재단의 라이선스를 준수하여 제작되었습니다.") },
                            confirmButton = { 
                                TextButton(onClick = { showLicenses = false }) { 
                                    Text("확인", color = brandAccent, fontWeight = FontWeight.Bold) 
                                } 
                            }
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

            Spacer(modifier = Modifier.height(24.dp))

            // ==========================================
            // 6. 계정 관리 / 데이터 파기 섹션
            // ==========================================
            SettingsSectionTitle("계정 관리")
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp),
                shape = RoundedCornerShape(20.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                var showDeleteDialog by remember { mutableStateOf(false) }
                
                Column {
                    SettingsNavigationRow(
                        icon = Icons.Default.DeleteForever,
                        title = "회원 탈퇴",
                        onClick = { showDeleteDialog = true }
                    )
                    
                    if (showDeleteDialog) {
                        AlertDialog(
                            onDismissRequest = { showDeleteDialog = false },
                            title = { Text("회원 탈퇴", fontWeight = FontWeight.Bold) },
                            text = { 
                                Text(
                                    "정말 탈퇴하시겠습니까?\n" +
                                    "탈퇴 시 기기에 저장된 찜 목록(위시리스트), 최근 본 내역 및 설정 데이터가 영구적으로 파기되며 복구할 수 없습니다.",
                                    style = MaterialTheme.typography.bodyMedium
                                ) 
                            },
                            confirmButton = {
                                TextButton(
                                    onClick = {
                                        showDeleteDialog = false
                                        scope.launch {
                                            try {
                                                // 1. DataStore 계정 정보 삭제
                                                AuthManager.logout(ctx)
                                                
                                                // 2. Room 로컬 DB 초기화 (위시리스트 등)
                                                withContext(Dispatchers.IO) {
                                                    com.ddaeany0919.insightdeal.local.db.AppDatabase.getDatabase(ctx).clearAllTables()
                                                }
                                                
                                                // 3. 최근 본 내역 삭제
                                                RecentDealManager.clearRecentDeals(ctx)
                                                
                                                // 4. SharedPreferences 설정 데이터 삭제
                                                appPrefs.edit().clear().apply()
                                                dealPrefs.edit().clear().apply()
                                                
                                                android.widget.Toast.makeText(ctx, "회원 탈퇴가 완료되어 앱을 종료합니다.", android.widget.Toast.LENGTH_LONG).show()
                                                
                                                // 5. 프로세스 종료 및 앱 닫기
                                                (ctx as? android.app.Activity)?.finishAffinity()
                                                android.os.Process.killProcess(android.os.Process.myPid())
                                            } catch (e: Exception) {
                                                Log.e("SettingsScreen", "회원 탈퇴 중 오류 발생", e)
                                                android.widget.Toast.makeText(ctx, "탈퇴 처리 중 오류가 발생했습니다. 다시 시도해 주세요.", android.widget.Toast.LENGTH_SHORT).show()
                                            }
                                        }
                                    },
                                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                                ) {
                                    Text("탈퇴", fontWeight = FontWeight.Bold)
                                }
                            },
                            dismissButton = {
                                TextButton(onClick = { showDeleteDialog = false }) {
                                    Text("취소")
                                }
                            }
                        )
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(32.dp))
            Text(
                "Insight Deal © 2026",
                modifier = Modifier.fillMaxWidth(),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
        }
    }

    // PIN 잠금 다이얼로그 노출
    if (showPinDialog) {
        PinLockDialog(
            title = if (pinDialogSetupMode) "보안 PIN 번호 등록" else "PIN 비밀번호 입력",
            subtitle = if (pinDialogSetupMode) "개인 정보 보호를 위해 새 4자리 PIN을 설정하세요." else "보안 PIN 검증이 필요합니다.",
            correctPin = pinCorrectPin,
            isSetupMode = pinDialogSetupMode,
            onDismiss = { showPinDialog = false },
            onSuccess = { pin ->
                showPinDialog = false
                pinSuccessCallback?.invoke(pin)
            }
        )
    }
}

@Composable
fun SettingsSectionTitle(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Black),
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(bottom = 10.dp, start = 4.dp, top = 8.dp)
    )
}

@Composable
fun SettingsSwitchRow(
    icon: ImageVector,
    title: String,
    subtitle: String? = null,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(horizontal = 16.dp, vertical = 18.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon, 
            contentDescription = null, 
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(24.dp)
        )
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title, 
                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold)
            )
            if (subtitle != null) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = subtitle, 
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Spacer(modifier = Modifier.width(8.dp))
        Switch(
            checked = checked, 
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = Color(0xFFFF3B30)
            )
        )
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
        Icon(
            imageVector = icon, 
            contentDescription = null, 
            tint = Color(0xFFFF3B30),
            modifier = Modifier.size(24.dp)
        )
        Spacer(modifier = Modifier.width(16.dp))
        Text(
            text = title, 
            style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold), 
            modifier = Modifier.weight(1f)
        )
        if (value != null) {
            Text(text = value, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(modifier = Modifier.width(8.dp))
        }
        Icon(
            imageVector = Icons.AutoMirrored.Filled.ArrowForwardIos,
            contentDescription = null,
            modifier = Modifier.size(14.dp),
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
            Icon(
                imageVector = Icons.Default.Palette, 
                contentDescription = null, 
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.width(16.dp))
            Text(
                text = "테마 설정", 
                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold), 
                modifier = Modifier.weight(1f)
            )
            Text(
                text = when(currentMode) {
                    ThemeMode.SYSTEM -> "시스템 기본"
                    ThemeMode.LIGHT -> "라이트 모드"
                    ThemeMode.DARK -> "다크 모드"
                    ThemeMode.AMOLED -> "블랙(AMOLED)"
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.width(8.dp))
            Icon(
                imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        
        AnimatedVisibility(
            visible = expanded,
            enter = expandVertically(),
            exit = shrinkVertically()
        ) {
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
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(
            selected = current == value,
            onClick = { onSelect(value) },
            modifier = Modifier.size(20.dp),
            colors = RadioButtonDefaults.colors(
                selectedColor = MaterialTheme.colorScheme.primary
            )
        )
        Spacer(Modifier.width(12.dp))
        Text(label, style = MaterialTheme.typography.bodyMedium)
    }
}

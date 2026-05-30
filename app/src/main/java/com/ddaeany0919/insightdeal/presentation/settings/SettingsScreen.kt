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
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import com.ddaeany0919.insightdeal.network.UserWithdrawRequest
import com.ddaeany0919.insightdeal.network.ApiService

@OptIn(ExperimentalMaterial3Api::class, coil.annotation.ExperimentalCoilApi::class)
@Composable
fun SettingsScreen(
    onBackClick: () -> Unit,
    onNavigateToKeywordManager: () -> Unit,
    onNavigateToAuth: () -> Unit
) {
    val ctx = LocalContext.current
    val tm = remember { ThemeManager.getInstance(ctx) }
    val mode by tm.themeMode.collectAsState()
    val scope = rememberCoroutineScope()
    val scrollState = rememberScrollState()

    val savedUsername by AuthManager.getUsername(ctx).collectAsState(initial = "guest")
    val savedNickname by AuthManager.getNickname(ctx).collectAsState(initial = "guest")

    // 앱 테마 본연의 Primary 브랜드 컬러 연동 (하드코딩 컬러 100% 척결)
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

    // 회원 탈퇴 제어 상태들
    var showWithdrawPasswordDialog by remember { mutableStateOf(false) }
    var showWithdrawConfirmDialog by remember { mutableStateOf(false) }
    var withdrawPasswordInput by remember { mutableStateOf("") }
    var isWithdrawPasswordVisible by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("설정", fontWeight = FontWeight.ExtraBold, fontSize = 22.sp) },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "뒤로가기",
                            tint = MaterialTheme.colorScheme.onBackground
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
                .background(MaterialTheme.colorScheme.background)
                .padding(paddingValues)
                .verticalScroll(scrollState)
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp) // 카드 간의 시원한 20.dp 여백 보장
        ) {
            // ==========================================
            // 1. 사용자 계정 섹션 - 아크릴릭 18.dp 둥글기 프로필 링크 카드로 대전환 (설정과 가입 완벽 분리!)
            // ==========================================
            val isUserGuest = savedUsername == "guest" || savedUsername.isNullOrEmpty()
            if (!isUserGuest) {
                Column {
                    SettingsSectionTitle("사용자 계정")
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onNavigateToAuth() },
                        shape = RoundedCornerShape(18.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f))
                    ) {
                        Column(modifier = Modifier.padding(20.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "👤 내 계정 정보",
                                        style = MaterialTheme.typography.titleMedium,
                                        color = brandAccent,
                                        fontWeight = FontWeight.Black
                                    )
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text(
                                        text = "현재 로그인 상태: $savedNickname ($savedUsername 님)",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Spacer(modifier = Modifier.width(12.dp))
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowForwardIos,
                                    contentDescription = "이동",
                                    tint = brandAccent,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                            
                            Spacer(modifier = Modifier.height(10.dp))
                            Text(
                                text = "👉 터치하여 회원 정보 수정 및 정책 설정 화면으로 가기",
                                style = MaterialTheme.typography.labelSmall,
                                color = brandAccent.copy(alpha = 0.8f),
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
            
            // ==========================================
            // 2. 앱 설정 섹션 - 20.dp 넓은 간격의 설정 아이템
            // ==========================================
            Column {
                SettingsSectionTitle("앱 설정")
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f))
                ) {
                    Column {
                        // 테마 설정
                        ExpandableThemeSetting(currentMode = mode) { newMode ->
                            tm.setThemeMode(newMode)
                        }
                        
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.12f))
                        
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
                        
                        // 🔔 키워드 및 푸시 알림 설정
                        SettingsNavigationRow(
                            icon = Icons.Default.NotificationsActive,
                            title = "키워드 및 푸시 알림 설정",
                            onClick = onNavigateToKeywordManager
                        )
                        
                        // 야간 방해금지 시간대 설정
                        var nightDndEnabled by remember {
                            mutableStateOf(dealPrefs.getBoolean("night_push_consent", false))
                        }
                        SettingsSwitchRow(
                            icon = Icons.Default.DoNotDisturbOn,
                            title = "야간 방해금지 모드 (21:00 ~ 08:00)",
                            subtitle = "활성화 시 밤 9시부터 다음날 아침 8시까지 푸시 알림 수신이 차단됩니다.",
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
                        
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.12f))
                        
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
                        
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.12f))
                        
                        // 캐시 비우기
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
                                    android.widget.Toast.makeText(ctx, "임시 캐시 및 이미지 캐시가 깔끔하게 비워졌습니다. ✨", android.widget.Toast.LENGTH_SHORT).show()
                                }
                            )

                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp)
                                    .padding(bottom = 18.dp)
                            ) {
                                LinearProgressIndicator(
                                    progress = { cacheProgress },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(6.dp)
                                        .background(
                                            MaterialTheme.colorScheme.surfaceVariant,
                                            shape = RoundedCornerShape(3.dp)
                                        ),
                                    color = brandAccent,
                                    trackColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = "원활한 앱 이용을 위해 50MB 이내 유지를 권장합니다.",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
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
            }
            
            // ==========================================
            // 3. 보안 설정 섹션 (PIN 잠금)
            // ==========================================
            Column {
                SettingsSectionTitle("보안 설정")
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f))
                ) {
                    Column {
                        SettingsSwitchRow(
                            icon = Icons.Default.Lock,
                            title = "PIN 비밀번호 앱 잠금",
                            subtitle = "앱 시작 시 4자리 PIN 입력을 강제하여 소중한 개인정보를 안전하게 보호합니다.",
                            checked = isPinLockedEnabled,
                            onCheckedChange = { isChecked ->
                                if (isChecked) {
                                    pinDialogSetupMode = true
                                    pinCorrectPin = ""
                                    pinSuccessCallback = { newPin ->
                                        dealPrefs.edit()
                                            .putBoolean("app_lock_enabled", true)
                                            .putString("app_lock_pin", newPin)
                                            .apply()
                                        isPinLockedEnabled = true
                                        android.widget.Toast.makeText(ctx, "PIN 잠금이 활성화되었습니다. 🔒", android.widget.Toast.LENGTH_SHORT).show()
                                    }
                                    showPinDialog = true
                                } else {
                                    val correctPin = dealPrefs.getString("app_lock_pin", "") ?: ""
                                    if (correctPin.isEmpty()) {
                                        dealPrefs.edit().putBoolean("app_lock_enabled", false).apply()
                                        isPinLockedEnabled = false
                                    } else {
                                        pinDialogSetupMode = false
                                        pinCorrectPin = correctPin
                                        pinSuccessCallback = {
                                            dealPrefs.edit().putBoolean("app_lock_enabled", false).apply()
                                            isPinLockedEnabled = false
                                            android.widget.Toast.makeText(ctx, "보안 PIN 잠금이 해제되었습니다.", android.widget.Toast.LENGTH_SHORT).show()
                                        }
                                        showPinDialog = true
                                    }
                                }
                            }
                        )

                        if (isPinLockedEnabled) {
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.12f))
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
                                        android.widget.Toast.makeText(ctx, "PIN 비밀번호가 변경되었습니다.", android.widget.Toast.LENGTH_SHORT).show()
                                    }
                                    showPinDialog = true
                                }
                            )
                        }
                    }
                }
            }
            
            // ==========================================
            // 4. 플랫폼(출처) 필터링 섹션
            // ==========================================
            Column {
                SettingsSectionTitle("플랫폼(출처) 필터링")
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f))
                ) {
                    var expanded by remember { mutableStateOf(false) }
                    
                    Column {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { expanded = !expanded }
                                .padding(20.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.FilterList, 
                                contentDescription = null, 
                                tint = brandAccent
                            )
                            Spacer(modifier = Modifier.width(16.dp))
                            Text(
                                text = "표시할 플랫폼 선택", 
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold), 
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
                            
                            Column(modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 20.dp)) {
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
                                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.08f))
                                    }
                                }
                            }
                        }
                    }
                }
            }
            
            // ==========================================
            // 5. 정보 섹션
            // ==========================================
            Column {
                SettingsSectionTitle("정보")
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f))
                ) {
                    Column {
                        SettingsNavigationRow(icon = Icons.Default.Info, title = "앱 버전", value = "1.0.0")
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.12f))
                        
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
                        
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.12f))
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
            }

            Column {
                SettingsSectionTitle("계정 관리")
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f))
                ) {
                    Column {
                        SettingsNavigationRow(
                            icon = Icons.Default.DeleteForever,
                            title = "회원 탈퇴",
                            onClick = {
                                if (savedUsername == "guest" || savedUsername.isNullOrEmpty()) {
                                    android.widget.Toast.makeText(ctx, "게스트 계정은 탈퇴할 수 없습니다.", android.widget.Toast.LENGTH_SHORT).show()
                                } else {
                                    withdrawPasswordInput = ""
                                    isWithdrawPasswordVisible = false
                                    showWithdrawPasswordDialog = true
                                }
                            }
                        )
                        
                        // 1단계: 본인 확인용 비밀번호 입력 다이얼로그 (2번째 캡처의 둥근 격리 박스 리듬 차용)
                        if (showWithdrawPasswordDialog) {
                            AlertDialog(
                                onDismissRequest = { showWithdrawPasswordDialog = false },
                                title = { 
                                    Text(
                                        "회원 탈퇴 - 비밀번호 확인", 
                                        fontWeight = FontWeight.ExtraBold,
                                        fontSize = 18.sp
                                    ) 
                                },
                                text = {
                                    Column(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalArrangement = Arrangement.spacedBy(12.dp)
                                    ) {
                                        // 2번째 캡처 느낌의 연회색 둥근 격리 박스 디자인
                                        Card(
                                            modifier = Modifier.fillMaxWidth(),
                                            shape = RoundedCornerShape(14.dp),
                                            colors = CardDefaults.cardColors(
                                                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                                            )
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(14.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.Security,
                                                    contentDescription = null,
                                                    tint = brandAccent,
                                                    modifier = Modifier.size(20.dp)
                                                )
                                                Spacer(modifier = Modifier.width(10.dp))
                                                Text(
                                                    text = "본인 확인 및 안전한 탈퇴를 위해 현재 계정의 비밀번호를 입력해 주세요.",
                                                    style = MaterialTheme.typography.bodyMedium,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                    fontWeight = FontWeight.Medium,
                                                    lineHeight = 18.sp
                                                )
                                            }
                                        }

                                        Spacer(modifier = Modifier.height(4.dp))

                                        OutlinedTextField(
                                            value = withdrawPasswordInput,
                                            onValueChange = { withdrawPasswordInput = it },
                                            label = { Text("비밀번호") },
                                            singleLine = true,
                                            modifier = Modifier.fillMaxWidth(),
                                            visualTransformation = if (isWithdrawPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                                            trailingIcon = {
                                                IconButton(onClick = { isWithdrawPasswordVisible = !isWithdrawPasswordVisible }) {
                                                    Icon(
                                                        imageVector = if (isWithdrawPasswordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                                        contentDescription = if (isWithdrawPasswordVisible) "비밀번호 숨기기" else "비밀번호 표시",
                                                        tint = brandAccent
                                                    )
                                                }
                                            },
                                            shape = RoundedCornerShape(12.dp),
                                            colors = OutlinedTextFieldDefaults.colors(
                                                focusedBorderColor = brandAccent,
                                                focusedLabelColor = brandAccent,
                                                unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
                                            )
                                        )
                                    }
                                },
                                confirmButton = {
                                    TextButton(
                                        onClick = {
                                            if (withdrawPasswordInput.isBlank()) {
                                                android.widget.Toast.makeText(ctx, "비밀번호를 입력해 주세요.", android.widget.Toast.LENGTH_SHORT).show()
                                            } else {
                                                showWithdrawPasswordDialog = false
                                                showWithdrawConfirmDialog = true
                                            }
                                        }
                                    ) {
                                        Text("다음", fontWeight = FontWeight.Bold, color = brandAccent)
                                    }
                                },
                                dismissButton = {
                                    TextButton(
                                        onClick = { showWithdrawPasswordDialog = false }
                                    ) {
                                        Text("취소", color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                },
                                shape = RoundedCornerShape(20.dp),
                                containerColor = MaterialTheme.colorScheme.surface
                            )
                        }

                        // 2단계: 최종 오동작 방지 탈퇴 컨펌 다이얼로그 (2번째 캡처 느낌의 경고용 적색 격리 박스 차용)
                        if (showWithdrawConfirmDialog) {
                            AlertDialog(
                                onDismissRequest = { showWithdrawConfirmDialog = false },
                                title = { 
                                    Text(
                                        "회원 탈퇴 최종 확인", 
                                        fontWeight = FontWeight.ExtraBold, 
                                        color = MaterialTheme.colorScheme.error,
                                        fontSize = 18.sp
                                    ) 
                                },
                                text = { 
                                    Column(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalArrangement = Arrangement.spacedBy(12.dp)
                                    ) {
                                        Text(
                                            text = "정말 회원 탈퇴를 진행하시겠습니까?",
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )

                                        // 2번째 캡처 느낌의 경고 색상이 가미된 격리 박스
                                        Card(
                                            modifier = Modifier.fillMaxWidth(),
                                            shape = RoundedCornerShape(14.dp),
                                            colors = CardDefaults.cardColors(
                                                containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.25f)
                                            )
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(14.dp),
                                                verticalAlignment = Alignment.Top
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.Warning,
                                                    contentDescription = "경고",
                                                    tint = MaterialTheme.colorScheme.error,
                                                    modifier = Modifier.size(20.dp).padding(top = 2.dp)
                                                )
                                                Spacer(modifier = Modifier.width(10.dp))
                                                Text(
                                                    text = "탈퇴 즉시 백엔드 데이터베이스의 회원 정보가 완전히 삭제(DB 반영)되며, 기기에 저장된 위시리스트, 최근 본 내역 및 모든 설정 데이터가 영구적으로 파기되어 다시는 복구할 수 없습니다.",
                                                    style = MaterialTheme.typography.bodyMedium,
                                                    color = MaterialTheme.colorScheme.onErrorContainer,
                                                    fontWeight = FontWeight.SemiBold,
                                                    lineHeight = 18.sp
                                                )
                                            }
                                        }
                                    }
                                },
                                confirmButton = {
                                    TextButton(
                                        onClick = {
                                            showWithdrawConfirmDialog = false
                                            scope.launch {
                                                try {
                                                    val apiService = com.ddaeany0919.insightdeal.network.NetworkModule.createService<com.ddaeany0919.insightdeal.network.ApiService>()
                                                    val response = apiService.withdrawUser(
                                                        UserWithdrawRequest(
                                                            username = savedUsername ?: "",
                                                            password = withdrawPasswordInput
                                                        )
                                                    )
                                                    
                                                    if (response.isSuccessful && response.body()?.success == true) {
                                                        // 백엔드 영구 DB 삭제 대성공 시 로컬 자격증명 및 데이터 클리어
                                                        AuthManager.logout(ctx)
                                                        withContext(Dispatchers.IO) {
                                                            com.ddaeany0919.insightdeal.local.db.AppDatabase.getDatabase(ctx).clearAllTables()
                                                        }
                                                        RecentDealManager.clearRecentDeals(ctx)
                                                        appPrefs.edit().clear().apply()
                                                        dealPrefs.edit().clear().apply()
                                                        
                                                        android.widget.Toast.makeText(ctx, "회원 탈퇴가 완료되어 DB에 실시간 반영되었습니다. 앱을 안전하게 종료합니다.", android.widget.Toast.LENGTH_LONG).show()
                                                        
                                                        (ctx as? android.app.Activity)?.finishAffinity()
                                                        android.os.Process.killProcess(android.os.Process.myPid())
                                                    } else {
                                                        val errorMsg = response.body()?.message ?: "비밀번호가 올바르지 않거나 탈퇴 요청 처리에 실패했습니다."
                                                        android.widget.Toast.makeText(ctx, errorMsg, android.widget.Toast.LENGTH_LONG).show()
                                                    }
                                                } catch (e: Exception) {
                                                    Log.e("SettingsScreen", "회원 탈퇴 네트워크 통신 실패", e)
                                                    android.widget.Toast.makeText(ctx, "네트워크 통신 중 오류가 발생했습니다. 잠시 후 다시 시도해 주세요.", android.widget.Toast.LENGTH_SHORT).show()
                                                }
                                            }
                                        },
                                        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                                    ) {
                                        Text("탈퇴", fontWeight = FontWeight.Bold)
                                    }
                                },
                                dismissButton = {
                                    TextButton(
                                        onClick = { showWithdrawConfirmDialog = false }
                                    ) {
                                        Text("취소", color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                },
                                shape = RoundedCornerShape(20.dp),
                                containerColor = MaterialTheme.colorScheme.surface
                            )
                        }
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                "Insight Deal © 2026",
                modifier = Modifier.fillMaxWidth(),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
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
        modifier = Modifier.padding(bottom = 12.dp, start = 4.dp, top = 8.dp)
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
            .padding(horizontal = 20.dp, vertical = 20.dp),
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
                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold)
            )
            if (subtitle != null) {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = subtitle, 
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                )
            }
        }
        Spacer(modifier = Modifier.width(12.dp))
        Switch(
            checked = checked, 
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = MaterialTheme.colorScheme.primary
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
            .padding(20.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon, 
            contentDescription = null, 
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(24.dp)
        )
        Spacer(modifier = Modifier.width(16.dp))
        Text(
            text = title, 
            style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold), 
            modifier = Modifier.weight(1f)
        )
        if (value != null) {
            Text(text = value, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold), color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(modifier = Modifier.width(8.dp))
        }
        Icon(
            imageVector = Icons.AutoMirrored.Filled.ArrowForwardIos,
            contentDescription = null,
            modifier = Modifier.size(14.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
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
                .padding(20.dp),
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
                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold), 
                modifier = Modifier.weight(1f)
            )
            Text(
                text = when(currentMode) {
                    ThemeMode.SYSTEM -> "시스템 기본"
                    ThemeMode.LIGHT -> "라이트 모드"
                    ThemeMode.DARK -> "다크 모드"
                    ThemeMode.AMOLED -> "블랙(AMOLED)"
                },
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
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
            Column(modifier = Modifier.padding(start = 56.dp, end = 20.dp, bottom = 20.dp)) {
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
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(
            selected = current == value,
            onClick = { onSelect(value) },
            colors = RadioButtonDefaults.colors(selectedColor = MaterialTheme.colorScheme.primary)
        )
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
            color = if (current == value) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
        )
    }
}

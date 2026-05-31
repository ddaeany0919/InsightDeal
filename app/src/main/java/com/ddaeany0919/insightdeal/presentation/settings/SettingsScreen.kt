package com.ddaeany0919.insightdeal.presentation.settings

import android.content.Context
import android.util.Log
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.FastOutSlowInEasing
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.graphicsLayer
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
    var isBiometricEnabled by remember {
        mutableStateOf(dealPrefs.getBoolean("biometric_lock_enabled", false))
    }
    var isSecurityAccordionExpanded by remember { mutableStateOf(false) }



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
                        
                        // 🔔 알림 키워드 관리
                        SettingsNavigationRow(
                            icon = Icons.Default.NotificationsActive,
                            title = "알림 키워드 관리",
                            onClick = onNavigateToKeywordManager
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
            
            Column {
                SettingsSectionTitle("보안 설정")
                
                val arrowRotation by animateFloatAsState(
                    targetValue = if (isSecurityAccordionExpanded) 180f else 0f,
                    animationSpec = tween(durationMillis = 300, easing = FastOutSlowInEasing),
                    label = "ArrowRotation"
                )
                
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f))
                ) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { isSecurityAccordionExpanded = !isSecurityAccordionExpanded }
                                .padding(20.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Lock,
                                    contentDescription = null,
                                    tint = brandAccent,
                                    modifier = Modifier.size(22.dp)
                                )
                                Spacer(modifier = Modifier.width(16.dp))
                                Column {
                                    Text(
                                        text = "앱 잠금 및 생체 인증 설정 🔒", 
                                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = if (isPinLockedEnabled || isBiometricEnabled) "보안 잠금이 작동 중입니다." else "비활성화됨 (보안 강화를 위해 탭하세요)",
                                        fontSize = 12.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                                    )
                                }
                            }
                            Icon(
                                imageVector = Icons.Default.KeyboardArrowDown,
                                contentDescription = "펼치기",
                                modifier = Modifier
                                    .size(24.dp)
                                    .graphicsLayer { rotationZ = arrowRotation },
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        
                        AnimatedVisibility(
                            visible = isSecurityAccordionExpanded,
                            enter = expandVertically(animationSpec = tween(300)) + fadeIn(animationSpec = tween(300)),
                            exit = shrinkVertically(animationSpec = tween(300)) + fadeOut(animationSpec = tween(300))
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.15f))
                                    .padding(horizontal = 16.dp, vertical = 8.dp)
                            ) {
                                HorizontalDivider(
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.08f),
                                    thickness = 1.dp,
                                    modifier = Modifier.padding(bottom = 8.dp)
                                )
                                
                                // 1) PIN 비밀번호 앱 잠금
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
                                                    .putBoolean("biometric_lock_enabled", false)
                                                    .apply()
                                                isPinLockedEnabled = true
                                                isBiometricEnabled = false
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
                                            val currentPin = dealPrefs.getString("app_lock_pin", "") ?: ""
                                            if (currentPin.isEmpty()) {
                                                pinDialogSetupMode = true
                                                pinCorrectPin = ""
                                                pinSuccessCallback = { newPin ->
                                                    dealPrefs.edit()
                                                        .putString("app_lock_pin", newPin)
                                                        .apply()
                                                    android.widget.Toast.makeText(ctx, "PIN 비밀번호가 설정되었습니다.", android.widget.Toast.LENGTH_SHORT).show()
                                                }
                                                showPinDialog = true
                                            } else {
                                                // 1단계: 기존 PIN 비밀번호 대조
                                                pinDialogSetupMode = false
                                                pinCorrectPin = currentPin
                                                pinSuccessCallback = {
                                                    // 1단계 검증 성공 시 즉시 2단계 신규 PIN 설정 모드로 전환 기동
                                                    pinDialogSetupMode = true
                                                    pinCorrectPin = ""
                                                    pinSuccessCallback = { newPin ->
                                                        dealPrefs.edit()
                                                            .putString("app_lock_pin", newPin)
                                                            .apply()
                                                        android.widget.Toast.makeText(ctx, "PIN 비밀번호가 성공적으로 변경되었습니다. ✨", android.widget.Toast.LENGTH_SHORT).show()
                                                    }
                                                    showPinDialog = true
                                                }
                                                showPinDialog = true
                                            }
                                        }
                                    )
                                }
                                
                                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.12f))
                                
                                // 2) 지문 인식 간편 앱 잠금
                                SettingsSwitchRow(
                                    icon = Icons.Default.Fingerprint,
                                    title = "지문 인식 간편 앱 잠금",
                                    subtitle = "비밀번호 대신 지문/생체 인증으로 앱 잠금을 빠르고 안전하게 해제합니다.",
                                    checked = isBiometricEnabled,
                                    onCheckedChange = { isChecked ->
                                        if (isChecked) {
                                            val activity = ctx as? androidx.fragment.app.FragmentActivity
                                            if (activity != null) {
                                                val executor = androidx.core.content.ContextCompat.getMainExecutor(activity)
                                                val biometricPrompt = androidx.biometric.BiometricPrompt(activity, executor,
                                                    object : androidx.biometric.BiometricPrompt.AuthenticationCallback() {
                                                        override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                                                            super.onAuthenticationError(errorCode, errString)
                                                            android.widget.Toast.makeText(ctx, "생체 인식 거부: $errString", android.widget.Toast.LENGTH_SHORT).show()
                                                        }
                                                        override fun onAuthenticationSucceeded(result: androidx.biometric.BiometricPrompt.AuthenticationResult) {
                                                            super.onAuthenticationSucceeded(result)
                                                            dealPrefs.edit()
                                                                .putBoolean("biometric_lock_enabled", true)
                                                                .putBoolean("app_lock_enabled", false)
                                                                .apply()
                                                            isBiometricEnabled = true
                                                            isPinLockedEnabled = false
                                                            android.widget.Toast.makeText(ctx, "지문 인식 간편 잠금이 활성화되었습니다. 🔒", android.widget.Toast.LENGTH_SHORT).show()
                                                        }
                                                        override fun onAuthenticationFailed() {
                                                            super.onAuthenticationFailed()
                                                        }
                                                    })

                                                val promptInfo = androidx.biometric.BiometricPrompt.PromptInfo.Builder()
                                                    .setTitle("생체 인식 보안 활성화")
                                                    .setSubtitle("지문 센서에 손가락을 대주세요.")
                                                    .setNegativeButtonText("취소")
                                                    .build()

                                                biometricPrompt.authenticate(promptInfo)
                                            } else {
                                                dealPrefs.edit()
                                                    .putBoolean("biometric_lock_enabled", true)
                                                    .putBoolean("app_lock_enabled", false)
                                                    .apply()
                                                isBiometricEnabled = true
                                                isPinLockedEnabled = false
                                                android.widget.Toast.makeText(ctx, "생체 지문 잠금이 활성화되었습니다. (폴백 활성)", android.widget.Toast.LENGTH_SHORT).show()
                                            }
                                        } else {
                                            dealPrefs.edit().putBoolean("biometric_lock_enabled", false).apply()
                                            isBiometricEnabled = false
                                            android.widget.Toast.makeText(ctx, "지문 인식 잠금이 해제되었습니다.", android.widget.Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                )
                            }
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
            // 5. 정보 및 지원 섹션 (버전 정보, 의견 남기기, 약관 및 정책 통합)
            // ==========================================
            Column {
                SettingsSectionTitle("정보 및 지원")
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f))
                ) {
                    Column {
                        // 1) 버전 정보 및 업데이트 유도 배너
                        Column(modifier = Modifier.fillMaxWidth()) {
                            SettingsNavigationRow(
                                icon = Icons.Default.Info, 
                                title = "버전 정보", 
                                value = "1.0.0 (최신: 1.0.2)"
                            )
                            
                            // 🚀 [Wow-Factor] 버전이 낮을 시 최신 버전 업데이트 가이드 배너
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 20.dp)
                                    .padding(bottom = 16.dp)
                                    .background(Color(0xFFFFECE6), RoundedCornerShape(12.dp))
                                    .clickable {
                                        val playStoreIntent = android.content.Intent(
                                            android.content.Intent.ACTION_VIEW, 
                                            android.net.Uri.parse("market://details?id=" + ctx.packageName)
                                        )
                                        try {
                                            ctx.startActivity(playStoreIntent)
                                        } catch (e: Exception) {
                                            val webIntent = android.content.Intent(
                                                android.content.Intent.ACTION_VIEW, 
                                                android.net.Uri.parse("https://play.google.com/store/apps/details?id=" + ctx.packageName)
                                            )
                                            ctx.startActivity(webIntent)
                                        }
                                    }
                                    .padding(horizontal = 14.dp, vertical = 10.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Text("🚀", fontSize = 14.sp)
                                    Text(
                                        text = "최신 버전으로 업데이트할 수 있어요.",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFFFF9800) // 은은한 브랜드 오렌지
                                    )
                                }
                            }
                        }
                        
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.12f))
                        
                        // 2) 의견 남기기 (기존 문의하기 개편)
                        SettingsNavigationRow(
                            icon = Icons.Default.Feedback, 
                            title = "의견 남기기", 
                            onClick = {
                                val intent = android.content.Intent(android.content.Intent.ACTION_SENDTO, android.net.Uri.parse("mailto:support@insightdeal.com"))
                                intent.putExtra(android.content.Intent.EXTRA_SUBJECT, "[InsightDeal] 소중한 의견 남기기")
                                intent.putExtra(android.content.Intent.EXTRA_TEXT, "앱 이용에 관한 소중한 의견이나 버그 제보를 작성해 주세요.\n\n사용자 ID: $savedUsername\n앱 버전: 1.0.0\n의견 내용:")
                                try {
                                    ctx.startActivity(intent)
                                } catch (e: Exception) {
                                    android.widget.Toast.makeText(ctx, "이메일 앱을 찾을 수 없습니다. support@insightdeal.com 으로 의견을 보내주세요.", android.widget.Toast.LENGTH_LONG).show()
                                }
                            }
                        )
                        
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.12f))
                        
                        // 3) 약관 및 정책 3대 공식 약관 탑재
                        var showPolicyDialog by remember { mutableStateOf(false) }
                        var selectedPolicyTab by remember { mutableStateOf(0) } // 0: 서비스 약관, 1: 운영 정책, 2: 개인정보 처리방침
                        
                        SettingsNavigationRow(
                            icon = Icons.Default.Assignment, 
                            title = "약관 및 정책", 
                            onClick = {
                                selectedPolicyTab = 0
                                showPolicyDialog = true
                            }
                        )
                        
                        if (showPolicyDialog) {
                            AlertDialog(
                                onDismissRequest = { showPolicyDialog = false },
                                title = { 
                                    Column(modifier = Modifier.fillMaxWidth()) {
                                        Text("약관 및 정책", fontWeight = FontWeight.Black, fontSize = 18.sp, color = brandAccent)
                                        Spacer(modifier = Modifier.height(14.dp))
                                        
                                        // 탭 셀렉터 비주얼
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f), RoundedCornerShape(10.dp))
                                                .padding(2.dp),
                                            horizontalArrangement = Arrangement.spacedBy(2.dp)
                                        ) {
                                            val tabs = listOf("서비스 약관", "운영 정책", "개인정보")
                                            tabs.forEachIndexed { index, tabTitle ->
                                                val isSelected = selectedPolicyTab == index
                                                Box(
                                                    modifier = Modifier
                                                        .weight(1f)
                                                        .background(
                                                            if (isSelected) brandAccent else Color.Transparent,
                                                            RoundedCornerShape(8.dp)
                                                        )
                                                        .clickable { selectedPolicyTab = index }
                                                        .padding(vertical = 8.dp),
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    Text(
                                                        text = tabTitle,
                                                        color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                                                        fontWeight = FontWeight.Bold,
                                                        fontSize = 11.sp
                                                    )
                                                }
                                            }
                                        }
                                    }
                                },
                                text = {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(280.dp)
                                            .verticalScroll(rememberScrollState())
                                    ) {
                                        Text(
                                            text = when(selectedPolicyTab) {
                                                0 -> """
                                                    제 1 조 (목적)
                                                    본 약관은 InsightDeal(이하 "회사")이 기기 내 로컬 핫딜 수집 및 공유 서비스(이하 "서비스")를 제공함에 있어 회사와 이용자 간의 권리, 의무 및 책임 사항을 규정함을 목적으로 합니다.
                                                    
                                                    제 2 조 (용어의 정의)
                                                    1. "서비스"라 함은 뽐뿌, 펨코, 클리앙 등 국내 주요 오픈 핫딜 채널의 할인율 및 추천 가격 지표를 AI로 분석하여 기기 내부의 로컬 랭킹 엔진으로 제공하는 어플리케이션을 의미합니다.
                                                    2. "이용자"라 함은 본 약관에 동의하고 서비스를 이용하는 회원 및 비회원을 말합니다.
                                                    
                                                    제 3 조 (약관의 효력 및 변경)
                                                    1. 본 약관은 이용자가 동의함으로써 효력이 발생하며, 회사는 관련 법령을 위배하지 않는 범위 내에서 본 약관을 개정할 수 있습니다.
                                                    2. 약관 개정 시 회사는 최소 7일 전에 앱 내 공지사항을 통해 고지합니다.
                                                    
                                                    제 4 조 (이용자의 의무)
                                                    1. 이용자는 타인의 명의를 도용하여 회원가입을 하거나 부정 정보를 등록하여서는 안 됩니다.
                                                    2. 기기 내 로컬 DB를 인위적으로 무단 덤프하거나 상업적 목적으로 활용하는 것을 금지합니다.
                                                """.trimIndent()
                                                
                                                1 -> """
                                                    [InsightDeal 커뮤니티 운영 정책]
                                                    
                                                    1. 핫딜 게시물 및 댓글 작성 규칙
                                                    - 특정 판매업자와의 금전적 제휴 및 백링크 어뷰징 목적의 리베이트 링크 업로드는 강력히 제한됩니다. 발견 시 경고 없이 영구 이용 제재 처리됩니다.
                                                    - 타인에 대한 인신공격, 욕설, 허위 사실 유포 및 합리적이지 않은 비방 댓글은 필터링 엔진에 의해 자동 마스킹 및 노출 제재를 받습니다.
                                                    
                                                    2. AI 점수(꿀딜 투표) 남용 금지
                                                    - 특정 판매처의 어뷰징 목적 투표 선점 행위(동일 IP/동일 계정의 비정상 반복 클릭)는 로컬 방어 블로킹 필터에 의해 점수 환산에서 제외됩니다.
                                                    
                                                    3. 이용 제재 기준
                                                    - 1회 적발: 경고 및 해당 게시글 블라인드
                                                    - 2회 적발: 작성 제한 7일
                                                    - 3회 적발: 영구 계정 제재 및 회원탈퇴 조치
                                                """.trimIndent()
                                                
                                                else -> """
                                                    [개인정보 처리방침 (로컬 안심 보호)]
                                                    
                                                    InsightDeal은 이용자의 개인정보 보호를 최우선 가치로 수호하며, 현행 개인정보보호법 및 관계 법령을 철저히 준수합니다.
                                                    
                                                    1. 개인정보의 수집 항목 및 목적
                                                    - 회사는 회원가입 및 본인 인증 시점에 고유 ID, 비밀번호, 닉네임을 수집합니다.
                                                    - 수집 목적: 계정 세션 동기화, 마이페이지 꿀 내공 서비스 관리 및 1:1 로컬 AI 브리핑 분석.
                                                    
                                                    2. 개인정보의 기기 내 로컬 수호 원칙
                                                    - 본 앱은 이용자가 조회한 최근 본 핫딜 내역 및 선호 키워드를 외부 서버로 일절 전송하지 않고 기기 내부의 보안 저장소(EncryptedSharedPreferences)에만 100% 암호화 보존합니다.
                                                    
                                                    3. 파기 절차 및 영구 삭제
                                                    - 이용자가 회원탈퇴를 단행할 시, 백엔드 데이터베이스의 세션 레코드(DB)가 즉각 파기되며 기기에 축적된 모든 암호화 캐시가 E2E로 영구 포맷되어 재생이 불가능하도록 완전 소멸됩니다.
                                                """.trimIndent()
                                            },
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                },
                                confirmButton = { 
                                    TextButton(onClick = { showPolicyDialog = false }) { 
                                        Text("동의 및 확인", color = brandAccent, fontWeight = FontWeight.Bold) 
                                    } 
                                }
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

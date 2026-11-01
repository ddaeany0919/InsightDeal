package com.ddaeany0919.insightdeal.presentation.settings

import android.content.Context
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ddaeany0919.insightdeal.presentation.theme.ThemeManager
import com.ddaeany0919.insightdeal.presentation.theme.ThemeMode
import com.ddaeany0919.insightdeal.presentation.bounceClick
import coil.imageLoader
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import com.ddaeany0919.insightdeal.presentation.auth.AuthManager
import com.ddaeany0919.insightdeal.presentation.components.PinLockDialog
import com.ddaeany0919.insightdeal.core.security.EncryptedPrefsManager

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

    val brandAccent = MaterialTheme.colorScheme.primary

    val dealPrefs = remember { EncryptedPrefsManager.getEncryptedPrefs(ctx) }
    val appPrefs = remember { ctx.getSharedPreferences("app", Context.MODE_PRIVATE) }

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
                title = { Text("설정", fontWeight = FontWeight.Bold, fontSize = 19.sp, letterSpacing = (-0.4).sp) },
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
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            // 1. 사용자 계정 섹션
            val isUserGuest = savedUsername == "guest" || savedUsername.isNullOrEmpty()
            if (!isUserGuest) {
                Column {
                    SettingsSectionTitle("사용자 계정")
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onNavigateToAuth() },
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.65f)),
                        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.weight(1f)
                            ) {
                                SettingsIconBadge(
                                    icon = Icons.Outlined.Person,
                                    tint = brandAccent
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Column {
                                    Text(
                                        text = "내 계정 정보",
                                        fontSize = 15.sp,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = "$savedNickname ($savedUsername)",
                                        fontSize = 12.5.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                contentDescription = "이동",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
            }

            // 2. 앱 설정 섹션
            Column {
                SettingsSectionTitle("앱 설정")
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.65f)),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                ) {
                    Column {
                        // 테마 설정
                        ExpandableThemeSetting(currentMode = mode) { newMode ->
                            tm.setThemeMode(newMode)
                        }

                        HorizontalDivider(
                            color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
                            modifier = Modifier.padding(horizontal = 16.dp)
                        )

                        // 알림 설정
                        var notificationsEnabled by remember {
                            mutableStateOf(appPrefs.getBoolean("push_enabled", true))
                        }
                        SettingsSwitchRow(
                            icon = Icons.Outlined.Notifications,
                            iconTint = Color(0xFFFF5A26),
                            title = "관심 키워드 실시간 알림",
                            checked = notificationsEnabled,
                            onCheckedChange = {
                                notificationsEnabled = it
                                appPrefs.edit().putBoolean("push_enabled", it).apply()
                            }
                        )

                        HorizontalDivider(
                            color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
                            modifier = Modifier.padding(horizontal = 16.dp)
                        )

                        // 알림 키워드 관리
                        SettingsNavigationRow(
                            icon = Icons.Outlined.NotificationsActive,
                            iconTint = Color(0xFFFF5A26),
                            title = "알림 키워드 관리",
                            onClick = onNavigateToKeywordManager
                        )

                        HorizontalDivider(
                            color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
                            modifier = Modifier.padding(horizontal = 16.dp)
                        )

                        // 데이터 절약 모드
                        var dataSaverEnabled by remember { mutableStateOf(appPrefs.getBoolean("data_saver", false)) }
                        SettingsSwitchRow(
                            icon = Icons.Outlined.DataUsage,
                            iconTint = Color(0xFF0D9488),
                            title = "데이터 절약 모드 (저화질 이미지)",
                            checked = dataSaverEnabled,
                            onCheckedChange = {
                                dataSaverEnabled = it
                                appPrefs.edit().putBoolean("data_saver", it).apply()
                            }
                        )

                        HorizontalDivider(
                            color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
                            modifier = Modifier.padding(horizontal = 16.dp)
                        )

                        // 캐시 비우기
                        var cacheSize by remember { mutableStateOf("계산 중...") }

                        LaunchedEffect(Unit) {
                            cacheSize = withContext(Dispatchers.IO) {
                                try {
                                    val size = ctx.cacheDir.walkTopDown().filter { it.isFile }.map { it.length() }.sum()
                                    if (size > 1024 * 1024) "${size / (1024 * 1024)}MB" else "${size / 1024}KB"
                                } catch (e: Exception) {
                                    "0MB"
                                }
                            }
                        }

                        SettingsNavigationRow(
                            icon = Icons.Outlined.CleaningServices,
                            iconTint = Color(0xFF4E5968),
                            title = "캐시 데이터 비우기",
                            value = cacheSize,
                            onClick = {
                                val imageLoader = ctx.imageLoader
                                imageLoader.memoryCache?.clear()
                                cacheSize = "0MB"
                                scope.launch(Dispatchers.IO) {
                                    try {
                                        imageLoader.diskCache?.clear()
                                        ctx.cacheDir.deleteRecursively()
                                    } catch (e: Exception) {}
                                }
                                android.widget.Toast.makeText(ctx, "임시 캐시와 이미지 캐시를 비웠습니다.", android.widget.Toast.LENGTH_SHORT).show()
                            }
                        )
                    }
                }
            }

            // 3. 보안 설정 섹션
            Column {
                SettingsSectionTitle("보안 설정")

                val arrowRotation by animateFloatAsState(
                    targetValue = if (isSecurityAccordionExpanded) 180f else 0f,
                    animationSpec = tween(durationMillis = 250, easing = FastOutSlowInEasing),
                    label = "ArrowRotation"
                )

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.65f)),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                ) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { isSecurityAccordionExpanded = !isSecurityAccordionExpanded }
                                .padding(horizontal = 16.dp, vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.weight(1f)
                            ) {
                                SettingsIconBadge(
                                    icon = Icons.Outlined.Lock,
                                    tint = brandAccent
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Column {
                                    Text(
                                        text = "앱 잠금 및 생체 인증",
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = if (isPinLockedEnabled || isBiometricEnabled) "사용 중" else "사용 안 함",
                                        fontSize = 12.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            Icon(
                                imageVector = Icons.Default.KeyboardArrowDown,
                                contentDescription = "펼치기",
                                modifier = Modifier
                                    .size(20.dp)
                                    .graphicsLayer { rotationZ = arrowRotation },
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        AnimatedVisibility(
                            visible = isSecurityAccordionExpanded,
                            enter = expandVertically(animationSpec = tween(250)) + fadeIn(animationSpec = tween(250)),
                            exit = shrinkVertically(animationSpec = tween(250)) + fadeOut(animationSpec = tween(250))
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f))
                            ) {
                                HorizontalDivider(
                                    color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
                                    thickness = 1.dp
                                )

                                // 1) PIN 비밀번호 앱 잠금
                                SettingsSwitchRow(
                                    icon = Icons.Outlined.Password,
                                    iconTint = brandAccent,
                                    title = "PIN 비밀번호 앱 잠금",
                                    subtitle = "앱 시작 시 4자리 PIN 입력으로 개인정보를 보호합니다.",
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
                                                android.widget.Toast.makeText(ctx, "PIN 잠금이 설정되었어요.", android.widget.Toast.LENGTH_SHORT).show()
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
                                    HorizontalDivider(
                                        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
                                        modifier = Modifier.padding(horizontal = 16.dp)
                                    )
                                    SettingsNavigationRow(
                                        icon = Icons.Outlined.LockReset,
                                        iconTint = brandAccent,
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
                                                pinDialogSetupMode = false
                                                pinCorrectPin = currentPin
                                                pinSuccessCallback = {
                                                    pinDialogSetupMode = true
                                                    pinCorrectPin = ""
                                                    pinSuccessCallback = { newPin ->
                                                        dealPrefs.edit()
                                                            .putString("app_lock_pin", newPin)
                                                            .apply()
                                                        android.widget.Toast.makeText(ctx, "PIN 번호가 변경되었어요.", android.widget.Toast.LENGTH_SHORT).show()
                                                    }
                                                    showPinDialog = true
                                                }
                                                showPinDialog = true
                                            }
                                        }
                                    )
                                }

                                HorizontalDivider(
                                    color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
                                    modifier = Modifier.padding(horizontal = 16.dp)
                                )

                                // 2) 지문 인식 간편 앱 잠금
                                SettingsSwitchRow(
                                    icon = Icons.Outlined.Fingerprint,
                                    iconTint = brandAccent,
                                    title = "지문 인식 간편 앱 잠금",
                                    subtitle = "생체 인증으로 앱 잠금을 빠르게 해제합니다.",
                                    checked = isBiometricEnabled,
                                    onCheckedChange = { isChecked ->
                                        if (isChecked) {
                                            val biometricManager = androidx.biometric.BiometricManager.from(ctx)
                                            val authenticators = androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG or
                                                                 androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL

                                            if (biometricManager.canAuthenticate(authenticators) != androidx.biometric.BiometricManager.BIOMETRIC_SUCCESS) {
                                                isBiometricEnabled = false
                                                dealPrefs.edit().putBoolean("biometric_lock_enabled", false).apply()
                                                android.widget.Toast.makeText(ctx, "기기에 등록된 생체 인증 정보가 없어요.", android.widget.Toast.LENGTH_LONG).show()
                                            } else {
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
                                                                android.widget.Toast.makeText(ctx, "생체 인증 잠금이 설정되었어요.", android.widget.Toast.LENGTH_SHORT).show()
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
                                                    android.widget.Toast.makeText(ctx, "생체 지문 잠금이 활성화되었습니다.", android.widget.Toast.LENGTH_SHORT).show()
                                                }
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

            // 4. 플랫폼(출처) 필터링 섹션
            Column {
                SettingsSectionTitle("플랫폼(출처) 필터링")
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.65f)),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                ) {
                    var expanded by remember { mutableStateOf(false) }

                    Column {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { expanded = !expanded }
                                .padding(horizontal = 16.dp, vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            SettingsIconBadge(
                                icon = Icons.Outlined.Tune,
                                tint = Color(0xFF4F46E5)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = "표시할 커뮤니티 출처 선택",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.weight(1f)
                            )
                            Icon(
                                imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        AnimatedVisibility(
                            visible = expanded,
                            enter = expandVertically(),
                            exit = shrinkVertically()
                        ) {
                            val allPlatforms = listOf(
                                "뽐뿌" to Color(0xFF1565C0),
                                "퀘이사존" to Color(0xFFE65100),
                                "펨코" to Color(0xFF0288D1),
                                "루리웹" to Color(0xFF0D47A1),
                                "클리앙" to Color(0xFF37474F),
                                "알리뽐뿌" to Color(0xFFD84315),
                                "빠삭국내" to Color(0xFFC2185B),
                                "빠삭해외" to Color(0xFF880E4F)
                            )
                            var disabledPlatforms by remember {
                                mutableStateOf(appPrefs.getStringSet("disabled_platforms", emptySet()) ?: emptySet())
                            }

                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f))
                            ) {
                                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
                                allPlatforms.forEachIndexed { index, (platform, dotColor) ->
                                    val isEnabled = !disabledPlatforms.contains(platform)
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable {
                                                val newSet = disabledPlatforms.toMutableSet()
                                                if (!isEnabled) newSet.remove(platform) else newSet.add(platform)
                                                disabledPlatforms = newSet
                                                appPrefs.edit().putStringSet("disabled_platforms", newSet).apply()
                                            }
                                            .padding(horizontal = 16.dp, vertical = 10.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(8.dp)
                                                .clip(CircleShape)
                                                .background(dotColor)
                                        )
                                        Spacer(modifier = Modifier.width(12.dp))
                                        Text(
                                            text = platform,
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.Medium,
                                            color = MaterialTheme.colorScheme.onSurface,
                                            modifier = Modifier.weight(1f)
                                        )
                                        Switch(
                                            checked = isEnabled,
                                            onCheckedChange = { nextEnabled ->
                                                val newSet = disabledPlatforms.toMutableSet()
                                                if (nextEnabled) newSet.remove(platform) else newSet.add(platform)
                                                disabledPlatforms = newSet
                                                appPrefs.edit().putStringSet("disabled_platforms", newSet).apply()
                                            },
                                            colors = SwitchDefaults.colors(
                                                checkedThumbColor = Color.White,
                                                checkedTrackColor = MaterialTheme.colorScheme.primary
                                            )
                                        )
                                    }
                                    if (index < allPlatforms.size - 1) {
                                        HorizontalDivider(
                                            color = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f),
                                            modifier = Modifier.padding(horizontal = 16.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // 5. 정보 및 지원 섹션
            Column {
                SettingsSectionTitle("정보 및 지원")
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.65f)),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                ) {
                    Column {
                        // 1) 버전 정보
                        SettingsNavigationRow(
                            icon = Icons.Outlined.Info,
                            iconTint = Color(0xFF4E5968),
                            title = "버전 정보",
                            value = "1.0.0",
                            showChevron = false
                        )

                        HorizontalDivider(
                            color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
                            modifier = Modifier.padding(horizontal = 16.dp)
                        )

                        // 2) 의견 남기기
                        SettingsNavigationRow(
                            icon = Icons.Outlined.ChatBubbleOutline,
                            iconTint = brandAccent,
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

                        HorizontalDivider(
                            color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
                            modifier = Modifier.padding(horizontal = 16.dp)
                        )

                        // 3) 약관 및 정책
                        var showPolicyDialog by remember { mutableStateOf(false) }
                        var selectedPolicyTab by remember { mutableStateOf(0) }

                        SettingsNavigationRow(
                            icon = Icons.Outlined.Description,
                            iconTint = Color(0xFF4E5968),
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
                                        Text("약관 및 정책", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = MaterialTheme.colorScheme.onSurface)
                                        Spacer(modifier = Modifier.height(14.dp))

                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(10.dp))
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
                                                    본 약관은 인사이트딜(이하 "서비스")의 이용과 관련하여 서비스와 이용자 간의 권리, 의무 및 책임 사항을 규정함을 목적으로 합니다.

                                                    제 2 조 (용어의 정의)
                                                    1. "서비스"란 국내 주요 커뮤니티의 할인 정보 및 시세 추이를 모아 제공하는 모바일 애플리케이션 및 관련 서비스를 의미합니다.
                                                    2. "이용자"란 본 약관에 따라 서비스를 이용하는 회원 및 비회원을 말합니다.

                                                    제 3 조 (약관의 효력 및 변경)
                                                    1. 본 약관은 서비스 화면에 게시하거나 기타의 방법으로 공지함으로써 효력이 발생합니다.
                                                    2. 관련 법령을 위배하지 않는 범위 내에서 약관을 개정할 수 있으며, 변경 시 사전에 공지합니다.

                                                    제 4 조 (이용자의 의무)
                                                    1. 타인의 정보를 도용하거나 허위 사실을 등록해서는 안 됩니다.
                                                    2. 서비스의 데이터를 무단으로 수집·복제하거나 상업적 목적으로 활용하는 행위를 금지합니다.
                                                """.trimIndent()

                                                1 -> """
                                                    [커뮤니티 운영 정책]

                                                    1. 게시물 및 댓글 작성 기준
                                                    - 홍보·제휴 링크 무단 게시, 도배, 사기성 게시물은 사전 통보 없이 삭제 및 이용 제한될 수 있습니다.
                                                    - 타인에 대한 비방, 욕설, 허위 사실 유포 게시물은 신고 및 모니터링을 통해 제한됩니다.

                                                    2. 투표 및 참여 제한
                                                    - 비정상적인 반복 참여나 여론 조작 목적의 행위는 집계에서 제외될 수 있습니다.

                                                    3. 이용 제한 기준
                                                    - 운영 정책 위반 시 게시물 숨김 처리 및 계정 이용이 제한될 수 있습니다.
                                                """.trimIndent()

                                                else -> """
                                                    [개인정보 처리방침]

                                                    인사이트딜은 개인정보보호법 등 관련 법령을 준수하며 이용자의 개인정보를 안전하게 관리합니다.

                                                    1. 수집하는 개인정보 항목 및 목적
                                                    - 수집 항목: 아이디, 비밀번호, 닉네임, 기기 알림 토큰(선택)
                                                    - 이용 목적: 회원 식별, 키워드 알림 발송, 찜 목록 및 커뮤니티 활동 관리

                                                    2. 보관 및 보호
                                                    - 비밀번호는 암호화되어 저장되며, 최근 본 상품 등 기기 설정 정보는 기기 내 저장소에 안전하게 보관됩니다.

                                                    3. 보유 기간 및 파기
                                                    - 회원 탈퇴 시 수집된 개인정보 및 활동 내역은 지체 없이 파기됩니다.
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

            Spacer(modifier = Modifier.height(12.dp))
            Text(
                "InsightDeal © 2026",
                modifier = Modifier.fillMaxWidth(),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
            Spacer(modifier = Modifier.height(16.dp))
        }
    }

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
private fun SettingsIconBadge(
    icon: ImageVector,
    tint: Color
) {
    Box(
        modifier = Modifier
            .size(34.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(tint.copy(alpha = 0.10f)),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(19.dp)
        )
    }
}

@Composable
fun SettingsSectionTitle(title: String) {
    Text(
        text = title,
        fontSize = 13.sp,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(bottom = 8.dp, start = 4.dp, top = 4.dp)
    )
}

@Composable
fun SettingsSwitchRow(
    icon: ImageVector,
    iconTint: Color = MaterialTheme.colorScheme.primary,
    title: String,
    subtitle: String? = null,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(horizontal = 16.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        SettingsIconBadge(icon = icon, tint = iconTint)
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
            if (subtitle != null) {
                Spacer(modifier = Modifier.height(3.dp))
                Text(
                    text = subtitle,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 16.sp
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
    iconTint: Color = MaterialTheme.colorScheme.primary,
    title: String,
    value: String? = null,
    showChevron: Boolean = true,
    onClick: () -> Unit = {}
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .bounceClick(scaleDown = 0.98f) { onClick() }
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        SettingsIconBadge(icon = icon, tint = iconTint)
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = title,
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f)
        )
        if (value != null) {
            Text(
                text = value,
                fontSize = 13.5.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (showChevron) {
                Spacer(modifier = Modifier.width(6.dp))
            }
        }
        if (showChevron) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
            )
        }
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
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            SettingsIconBadge(
                icon = Icons.Outlined.Palette,
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = "테마 설정",
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = when(currentMode) {
                    ThemeMode.SYSTEM -> "시스템 기본"
                    ThemeMode.LIGHT -> "라이트 모드"
                    ThemeMode.DARK -> "다크 모드"
                    ThemeMode.AMOLED -> "블랙(AMOLED)"
                },
                fontSize = 13.5.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.width(6.dp))
            Icon(
                imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp)
            )
        }

        AnimatedVisibility(
            visible = expanded,
            enter = expandVertically(),
            exit = shrinkVertically()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f))
                    .padding(start = 52.dp, end = 16.dp, bottom = 12.dp, top = 4.dp)
            ) {
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
            colors = RadioButtonDefaults.colors(selectedColor = MaterialTheme.colorScheme.primary)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = label,
            fontSize = 14.sp,
            fontWeight = if (current == value) FontWeight.Bold else FontWeight.Medium,
            color = if (current == value) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
        )
    }
}

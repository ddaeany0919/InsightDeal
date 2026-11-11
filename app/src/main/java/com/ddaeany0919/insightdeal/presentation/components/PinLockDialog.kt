package com.ddaeany0919.insightdeal.presentation.components

import android.content.Context
import android.content.ContextWrapper
import android.widget.Toast
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Backspace
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import com.ddaeany0919.insightdeal.core.security.EncryptedPrefsManager
import kotlinx.coroutines.delay

private tailrec fun Context.findFragmentActivity(): FragmentActivity? = when (this) {
    is FragmentActivity -> this
    is ContextWrapper -> baseContext.findFragmentActivity()
    else -> null
}

@Composable
fun PinLockDialog(
    title: String = "보안 PIN 번호 입력",
    subtitle: String = "개인 정보 보호를 위해 4자리 PIN을 입력하세요.",
    correctPin: String, // 비교할 정답 PIN (설정 모드일 때는 "")
    isSetupMode: Boolean = false, // PIN 번호를 신규로 설정하는 모드인지 여부
    onDismiss: () -> Unit,
    onSuccess: (String) -> Unit // 올바른 PIN 입력 시 혹은 설정 완료 시 콜백
) {
    val context = LocalContext.current
    val activity = remember(context) { context.findFragmentActivity() }
    val prefs = remember { EncryptedPrefsManager.getEncryptedPrefs(context) }

    var pinInput by remember { mutableStateOf("") }
    var errorCount by remember { prefs.getInt("app_lock_fail_count", 0).let { mutableStateOf(it) } }
    var setupStep by remember { mutableStateOf(1) } // 1: 첫 입력, 2: 확인 입력
    var firstPinInput by remember { mutableStateOf("") }

    // 차단(락다운) 상태 관리
    var blockedUntil by remember { prefs.getLong("app_lock_blocked_until", 0L).let { mutableStateOf(it) } }
    var isBlocked by remember { mutableStateOf(false) }
    var remainingSeconds by remember { mutableStateOf(0L) }

    var instructionText by remember { mutableStateOf(subtitle) }

    val canUseBiometric = remember(activity, isSetupMode) {
        if (isSetupMode || activity == null) {
            false
        } else {
            val biometricManager = BiometricManager.from(activity)
            biometricManager.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG) == BiometricManager.BIOMETRIC_SUCCESS
        }
    }

    val launchHardwareBiometricAuth = {
        val hostActivity = activity
        if (hostActivity == null || !canUseBiometric) {
            Toast.makeText(context, "기기에 등록된 생체 인증 정보가 없습니다. PIN 번호를 입력해 주세요.", Toast.LENGTH_SHORT).show()
        } else {
            val executor = ContextCompat.getMainExecutor(hostActivity)
            val biometricPrompt = BiometricPrompt(
                hostActivity,
                executor,
                object : BiometricPrompt.AuthenticationCallback() {
                    override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                        super.onAuthenticationError(errorCode, errString)
                        if (errorCode != BiometricPrompt.ERROR_NEGATIVE_BUTTON &&
                            errorCode != BiometricPrompt.ERROR_USER_CANCELED &&
                            errorCode != BiometricPrompt.ERROR_CANCELED
                        ) {
                            Toast.makeText(hostActivity, errString, Toast.LENGTH_SHORT).show()
                        }
                    }

                    override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                        super.onAuthenticationSucceeded(result)
                        prefs.edit().putInt("app_lock_fail_count", 0).apply()
                        errorCount = 0
                        onSuccess(correctPin)
                    }
                }
            )
            val promptInfo = BiometricPrompt.PromptInfo.Builder()
                .setTitle("앱 보안 잠금 해제")
                .setSubtitle("등록된 생체 정보로 본인 인증을 진행합니다.")
                .setNegativeButtonText("PIN 번호 입력")
                .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG)
                .build()
            biometricPrompt.authenticate(promptInfo)
        }
    }

    val mainColor = Color(0xFFFF3B30)
    val gradientColors = listOf(Color(0xFFFF5722), Color(0xFFFF3B30))

    // 실시간 차단 카운트다운 타이머 연동
    LaunchedEffect(blockedUntil) {
        while (true) {
            val currentTime = System.currentTimeMillis()
            if (blockedUntil > currentTime) {
                isBlocked = true
                remainingSeconds = ((blockedUntil - currentTime) / 1000) + 1
                instructionText = "보안 오입력 한도(5회)를 초과하여 임시 차단되었습니다. 잠시 후 다시 시도하세요.\n(남은 시간: ${remainingSeconds}초)"
            } else {
                if (isBlocked) {
                    isBlocked = false
                    prefs.edit().putInt("app_lock_fail_count", 0).apply()
                    errorCount = 0
                    instructionText = subtitle
                }
            }
            delay(1000)
        }
    }

    Dialog(
        onDismissRequest = { 
            if (!isSetupMode) {
                // 앱 기동 시 강제 잠금인 경우는 dismiss 불가
            } else {
                onDismiss()
            }
        },
        properties = DialogProperties(
            dismissOnBackPress = isSetupMode,
            dismissOnClickOutside = false,
            usePlatformDefaultWidth = false
        )
    ) {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background),
            color = MaterialTheme.colorScheme.background
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                // 상단 닫기 단추 (설정 모드에서만 제공)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    if (isSetupMode) {
                        TextButton(onClick = onDismiss) {
                            Text("취소", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    } else {
                        // 앱 기동 잠금일 때는 종료 버튼
                        TextButton(onClick = {
                            (context as? android.app.Activity)?.finishAffinity()
                            android.os.Process.killProcess(android.os.Process.myPid())
                        }) {
                            Text("앱 종료", color = MaterialTheme.colorScheme.error)
                        }
                    }
                }

                // 중앙 상단 헤더 & 비밀번호 표시기
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.Center
                ) {
                    // 미려한 락 아이콘 박스 (그라디언트)
                    Box(
                        modifier = Modifier
                            .size(72.dp)
                            .clip(CircleShape)
                            .background(Brush.linearGradient(gradientColors)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Lock,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(36.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    Text(
                        text = if (isSetupMode) {
                            if (setupStep == 1) "신규 PIN 번호 설정" else "PIN 번호 재입력"
                        } else title,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.onBackground
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = instructionText,
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (isBlocked) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )

                    Spacer(modifier = Modifier.height(36.dp))

                    // 4자리 원형 인디케이터
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        for (i in 1..4) {
                            val isFilled = pinInput.length >= i
                            Box(
                                modifier = Modifier
                                    .size(16.dp)
                                    .clip(CircleShape)
                                    .background(
                                        if (isFilled) mainColor else MaterialTheme.colorScheme.surfaceVariant
                                    )
                            )
                        }
                    }
                }

                // 키패드 (Touch-First) - 차단 상태일 때는 비활성화
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 36.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    val keys = listOf(
                        listOf("1", "2", "3"),
                        listOf("4", "5", "6"),
                        listOf("7", "8", "9"),
                        listOf("biometric", "0", "delete")
                    )

                    for (row in keys) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            for (key in row) {
                                val isEnabled = key.isNotEmpty() && !isBlocked && (key != "biometric" || canUseBiometric)
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .aspectRatio(1.8f)
                                        .clip(RoundedCornerShape(16.dp))
                                        .background(
                                            if (isEnabled) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f) 
                                            else Color.Transparent
                                        )
                                        .clickable(enabled = isEnabled) {
                                            if (key == "delete") {
                                                if (pinInput.isNotEmpty()) {
                                                    pinInput = pinInput.dropLast(1)
                                                }
                                            } else if (key == "biometric") {
                                                launchHardwareBiometricAuth()
                                            } else {
                                                if (pinInput.length < 4) {
                                                    pinInput += key
                                                }

                                                // 4자리 완성 시 자동 확인 돌입
                                                if (pinInput.length == 4) {
                                                    if (isSetupMode) {
                                                        if (setupStep == 1) {
                                                            firstPinInput = pinInput
                                                            pinInput = ""
                                                            setupStep = 2
                                                            instructionText = "확인을 위해 설정할 PIN 번호를 다시 한 번 입력해 주세요."
                                                        } else {
                                                            if (pinInput == firstPinInput) {
                                                                onSuccess(pinInput)
                                                            } else {
                                                                pinInput = ""
                                                                setupStep = 1
                                                                firstPinInput = ""
                                                                instructionText = "비밀번호가 일치하지 않습니다. 처음부터 다시 입력하세요."
                                                                android.widget.Toast.makeText(context, "PIN 번호 불일치!", android.widget.Toast.LENGTH_SHORT).show()
                                                            }
                                                        }
                                                    } else {
                                                        if (pinInput == correctPin) {
                                                            prefs.edit().putInt("app_lock_fail_count", 0).apply()
                                                            errorCount = 0
                                                            onSuccess(pinInput)
                                                        } else {
                                                            pinInput = ""
                                                            val newFailCount = errorCount + 1
                                                            prefs.edit().putInt("app_lock_fail_count", newFailCount).apply()
                                                            errorCount = newFailCount
                                                            
                                                            if (newFailCount >= 5) {
                                                                val blockTime = System.currentTimeMillis() + (5 * 60 * 1000)
                                                                prefs.edit()
                                                                    .putLong("app_lock_blocked_until", blockTime)
                                                                    .putInt("app_lock_fail_count", 0)
                                                                    .apply()
                                                                blockedUntil = blockTime
                                                                errorCount = 0
                                                                android.widget.Toast.makeText(context, "5회 실패! 5분간 입력이 제한됩니다.", android.widget.Toast.LENGTH_LONG).show()
                                                            } else {
                                                                instructionText = "비밀번호가 올바르지 않습니다. (틀린 횟수: ${newFailCount}/5회)"
                                                                android.widget.Toast.makeText(context, "비밀번호 오류! (${newFailCount}/5)", android.widget.Toast.LENGTH_SHORT).show()
                                                            }
                                                        }
                                                    }
                                                }
                                            }
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (key == "delete") {
                                        Icon(
                                            imageVector = Icons.Default.Backspace,
                                            contentDescription = "지우기",
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    } else if (key == "biometric") {
                                        Icon(
                                            imageVector = Icons.Default.Fingerprint,
                                            contentDescription = "지문 인식",
                                            tint = if (isEnabled) MaterialTheme.colorScheme.primary else Color.Transparent,
                                            modifier = Modifier.size(28.dp)
                                        )
                                    } else {
                                        Text(
                                            text = key,
                                            fontSize = 22.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onBackground
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

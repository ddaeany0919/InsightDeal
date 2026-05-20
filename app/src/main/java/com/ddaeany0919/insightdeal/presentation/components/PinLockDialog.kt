package com.ddaeany0919.insightdeal.presentation.components

import android.content.Context
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Backspace
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

@Composable
fun PinLockDialog(
    title: String = "보안 PIN 번호 입력",
    subtitle: String = "개인 정보 보호를 위해 4자리 PIN을 입력하세요.",
    correctPin: String, // 비교할 정답 PIN (설정 모드일 때는 "")
    isSetupMode: Boolean = false, // PIN 번호를 신규로 설정하는 모드인지 여부
    onDismiss: () -> Unit,
    onSuccess: (String) -> Unit // 올바른 PIN 입력 시 혹은 설정 완료 시 콜백
) {
    var pinInput by remember { mutableStateOf("") }
    var errorCount by remember { mutableStateOf(0) }
    var setupStep by remember { mutableStateOf(1) } // 1: 첫 입력, 2: 확인 입력
    var firstPinInput by remember { mutableStateOf("") }
    var instructionText by remember { mutableStateOf(subtitle) }
    val context = LocalContext.current

    val mainColor = Color(0xFFFF3B30)
    val gradientColors = listOf(Color(0xFFFF5722), Color(0xFFFF3B30))

    Dialog(
        onDismissRequest = { 
            if (!isSetupMode) {
                // 앱 기동 시 강제 잠금인 경우는 dismiss 불가하게 하거나 finishAffinity 호출
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
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
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

                // 키패드 (Touch-First)
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
                        listOf("", "0", "delete")
                    )

                    for (row in keys) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            for (key in row) {
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .aspectRatio(1.8f)
                                        .clip(RoundedCornerShape(16.dp))
                                        .background(
                                            if (key.isNotEmpty()) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f) 
                                            else Color.Transparent
                                        )
                                        .clickable(enabled = key.isNotEmpty()) {
                                            if (key == "delete") {
                                                if (pinInput.isNotEmpty()) {
                                                    pinInput = pinInput.dropLast(1)
                                                }
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
                                                            onSuccess(pinInput)
                                                        } else {
                                                            pinInput = ""
                                                            errorCount++
                                                            instructionText = "비밀번호가 올바르지 않습니다. (틀린 횟수: ${errorCount}회)"
                                                            android.widget.Toast.makeText(context, "비밀번호 오류!", android.widget.Toast.LENGTH_SHORT).show()
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

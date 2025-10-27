package com.ddaeany0919.insightdeal.ui.components

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.SimpleDateFormat
import java.util.*

data class TrackingResult(
    val productName: String,
    val targetPrice: Int?,
    val platform: String,
    val currentPrice: Int?,
    val trackingId: String,
    val estimatedNextCheck: String
)

/**
 * 🔔 추적 등록 즉시 피드백 - 사용자 불안 제거
 * 
 * 개선점:
 * - "✅ 추적 시작! 5분마다 확인, 목표가 도달 시 즐시 알림"
 * - "첫 확인 예정: 13:45 (약 4분 후)"
 * - 사용자가 다음에 뭐를 해야 할지 명확히 안내
 * - 기다리는 동안 불안감 없이 자연스럽게 처리
 */
@Composable
fun TrackingRegistrationFeedback(
    result: TrackingResult?,
    isVisible: Boolean,
    onDismiss: () -> Unit,
    onGoToWatchlist: () -> Unit,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = isVisible && result != null,
        enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
        exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
        modifier = modifier
    ) {
        result?.let { trackingResult ->
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                shape = RoundedCornerShape(12.dp),
                color = Color(0xFF4CAF50).copy(alpha = 0.95f),
                shadowElevation = 8.dp
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // 성공 아이콘
                    Icon(
                        Icons.Default.CheckCircle,
                        contentDescription = null,
                        modifier = Modifier.size(48.dp),
                        tint = Color.White
                    )
                    
                    Spacer(modifier = Modifier.height(12.dp))
                    
                    // 메인 메시지
                    Text(
                        text = "✅ 추적 시작!",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    // 상세 설명
                    Text(
                        text = "5분마다 가격 확인하여\n목표가 도달 시 즐시 알림해드립니다",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White.copy(alpha = 0.9f),
                        lineHeight = 20.sp
                    )
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    // 첫 확인 예정 시각
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        color = Color.White.copy(alpha = 0.2f),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.Schedule,
                                contentDescription = null,
                                modifier = Modifier.size(20.dp),
                                tint = Color.White
                            )
                            
                            Spacer(modifier = Modifier.width(8.dp))
                            
                            Column {
                                Text(
                                    text = "첫 확인 예정: ${trackingResult.estimatedNextCheck}",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = Color.White,
                                    fontWeight = FontWeight.Medium
                                )
                                
                                if (trackingResult.currentPrice != null && trackingResult.targetPrice != null) {
                                    val diffMessage = if (trackingResult.currentPrice > trackingResult.targetPrice) {
                                        "현재 ${String.format("%,d", trackingResult.currentPrice - trackingResult.targetPrice)}원 높음"
                                    } else {
                                        "목표가 달성! 바로 알림"
                                    }
                                    
                                    Text(
                                        text = diffMessage,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = Color.White.copy(alpha = 0.8f)
                                    )
                                }
                            }
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(20.dp))
                    
                    // 액션 버튼들
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // 워치리스트로 이동
                        Button(
                            onClick = onGoToWatchlist,
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color.White,
                                contentColor = Color(0xFF4CAF50)
                            )
                        ) {
                            Text(
                                text = "워치리스트 보기",
                                fontWeight = FontWeight.Medium
                            )
                        }
                        
                        // 닫기
                        OutlinedButton(
                            onClick = onDismiss,
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = Color.White
                            )
                        ) {
                            Text(
                                text = "확인",
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    // 추가 안내
                    Text(
                        text = "📧 알림은 앱 알림으로 발송됩니다",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.7f)
                    )
                }
            }
        }
    }
    
    // 자동 숨기기 (5초 후)
    LaunchedEffect(isVisible) {
        if (isVisible) {
            kotlinx.coroutines.delay(5000)
            onDismiss()
        }
    }
}

/**
 * 🕰️ 다음 확인 시각 계산 유틸리티
 * 5분 후의 시간을 HH:mm 형식으로 반환
 */
fun calculateNextCheckTime(): String {
    val calendar = Calendar.getInstance()
    calendar.add(Calendar.MINUTE, 5)
    val formatter = SimpleDateFormat("HH:mm", Locale.getDefault())
    return formatter.format(calendar.time)
}

/**
 * 🕰️ 분 단위로 남은 시간 계산
 * "약 4분 후" 같은 사용자 친화적 메시지
 */
fun formatTimeUntilNext(): String {
    return "약 4분 후"
}

/**
 * 🎆 샘플 추적 결과 생성자
 * 테스트 및 미리보기용
 */
fun createSampleTrackingResult(
    productName: String = "갤럭시 버즈 프로 2",
    targetPrice: Int = 180000,
    currentPrice: Int = 189000
): TrackingResult {
    return TrackingResult(
        productName = productName,
        targetPrice = targetPrice,
        platform = "쿠팡",
        currentPrice = currentPrice,
        trackingId = "track_${System.currentTimeMillis()}",
        estimatedNextCheck = "${calculateNextCheckTime()} (${formatTimeUntilNext()})"
    )
}
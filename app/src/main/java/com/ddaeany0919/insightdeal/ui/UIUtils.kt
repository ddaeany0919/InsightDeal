package com.ddaeany0919.insightdeal.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

/**
 * 🌟 Pull-to-Refresh 토스트 매니저
 * 목표: 200ms 이내 즉시 반응, 2초 내 결과 반영
 */
@Composable
fun RefreshToastManager(
    isRefreshing: Boolean,
    refreshSuccess: Boolean?,
    newItemsCount: Int = 0,
    onDismiss: () -> Unit
) {
    var showToast by remember { mutableStateOf(false) }
    var toastMessage by remember { mutableStateOf("") }
    var toastIcon by remember { mutableStateOf(Icons.Default.Info) }
    var toastColor by remember { mutableStateOf(Color.Blue) }
    
    // 새로고침 시작 시 200ms 이내 즈바른 토스트
    LaunchedEffect(isRefreshing) {
        if (isRefreshing) {
            toastMessage = "새 딜 확인 중…"
            toastIcon = Icons.Default.Info
            toastColor = MaterialTheme.colorScheme.primary
            showToast = true
        }
    }
    
    // 2초 내 결과 반영 완료 시 메시지 업데이트
    LaunchedEffect(refreshSuccess) {
        if (refreshSuccess != null && !isRefreshing) {
            if (refreshSuccess && newItemsCount > 0) {
                toastMessage = "${newItemsCount}개의 새 딜을 찾았습니다"
                toastIcon = Icons.Default.CheckCircle
                toastColor = Color(0xFF4CAF50) // Green
            } else {
                toastMessage = "새로운 딜 없음"
                toastIcon = Icons.Default.Info
                toastColor = MaterialTheme.colorScheme.outline
            }
            showToast = true
            
            // 2초 후 자동 사라짐
            delay(2000)
            showToast = false
            onDismiss()
        }
    }
    
    // 토스트 UI
    AnimatedVisibility(
        visible = showToast,
        enter = fadeIn(),
        exit = fadeOut()
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            color = toastColor.copy(alpha = 0.9f),
            shape = SnackbarDefaults.shape
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = toastIcon,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(20.dp)
                )
                
                Spacer(modifier = Modifier.width(12.dp))
                
                Text(
                    text = toastMessage,
                    color = Color.White,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
    }
}

/**
 * ⌚ 상대시간 포매터 ("5분 전", "1시간 전" 형태)
 */
fun formatRelativeTime(updatedAt: String): String {
    return try {
        // ISO 8601 타임스탬프를 상대시간으로 변환
        // 임시 구현: 실제로는 java.time.Instant.parse() 등 사용
        val minutesAgo = (0..60).random() // Mock data
        
        when {
            minutesAgo < 1 -> "방금 전"
            minutesAgo < 60 -> "${minutesAgo}분 전"
            minutesAgo < 1440 -> "${minutesAgo / 60}시간 전"
            else -> "${minutesAgo / 1440}일 전"
        }
    } catch (e: Exception) {
        "방금 전"
    }
}

/**
 * 🏷️ 커뮤니티 백지 컴포넌트
 */
@Composable
fun CommunityBadge(community: String) {
    val badgeColor = when (community.lowercase()) {
        "뽐뿌", "ppomppu" -> Color(0xFFFF5722) // Deep Orange
        "클리앙", "clien" -> Color(0xFF2196F3) // Blue
        "루리웹", "ruliweb" -> Color(0xFF9C27B0) // Purple
        "퀘이사존", "quasarzone" -> Color(0xFF607D8B) // Blue Grey
        "쿠팡", "coupang" -> Color(0xFFFF9800) // Orange
        "11번가", "eleventh" -> Color(0xFF4CAF50) // Green
        "지마켓", "gmarket" -> Color(0xFFF44336) // Red
        "옥션", "auction" -> Color(0xFF795548) // Brown
        else -> MaterialTheme.colorScheme.primary
    }
    
    Surface(
        color = badgeColor,
        shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp)
    ) {
        Text(
            text = community,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            style = MaterialTheme.typography.labelSmall,
            color = Color.White,
            maxLines = 1
        )
    }
}

/**
 * 💰 가격 포매터
 */
fun formatPrice(price: Int?): String {
    return if (price != null && price > 0) {
        String.format("%,d원", price)
    } else {
        "-"
    }
}

/**
 * 📦 디버깅용 네트워크 상태 인디케이터
 */
@Composable
fun NetworkStatusIndicator(
    responseTimeMs: Int,
    successCount: Int,
    totalPlatforms: Int = 4
) {
    if (responseTimeMs > 0) {
        val statusColor = when {
            responseTimeMs <= 1000 -> Color(0xFF4CAF50) // Fast - Green
            responseTimeMs <= 2000 -> Color(0xFFFF9800) // OK - Orange  
            else -> Color(0xFFF44336) // Slow - Red
        }
        
        Surface(
            color = statusColor.copy(alpha = 0.1f),
            shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp)
        ) {
            Text(
                text = "${responseTimeMs}ms・${successCount}/${totalPlatforms}몰",
                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                style = MaterialTheme.typography.labelSmall,
                color = statusColor
            )
        }
    }
}

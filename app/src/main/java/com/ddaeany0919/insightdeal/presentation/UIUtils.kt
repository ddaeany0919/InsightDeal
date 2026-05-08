package com.ddaeany0919.insightdeal.presentation

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
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
    onDismiss: () -> Unit = {}
) {
    var showToast by remember { mutableStateOf(false) }
    var toastMessage by remember { mutableStateOf("") }
    var toastIcon by remember { mutableStateOf(Icons.Default.Info) }

    LaunchedEffect(isRefreshing) {
        if (isRefreshing) {
            showToast = true
            toastMessage = "새 딜 확인 중…"
            toastIcon = Icons.Default.Info
        }
    }

    LaunchedEffect(refreshSuccess) {
        if (refreshSuccess != null && !isRefreshing) {
            showToast = true
            if (refreshSuccess && newItemsCount > 0) {
                toastMessage = "${newItemsCount}개의 새 딜 발견!"
                toastIcon = Icons.Default.CheckCircle
            } else {
                toastMessage = "새로운 딜 없음"
                toastIcon = Icons.Default.Info
            }

            delay(2000)
            showToast = false
            onDismiss()
        }
    }

    AnimatedVisibility(
        visible = showToast,
        enter = fadeIn(),
        exit = fadeOut()
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            colors = CardDefaults.cardColors(
                containerColor = if (refreshSuccess == true) Color(0xFF4CAF50) else MaterialTheme.colorScheme.surfaceVariant
            ),
            elevation = CardDefaults.cardElevation(6.dp),
            shape = RoundedCornerShape(12.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = toastIcon,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = toastMessage,
                    color = Color.White,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}

/**
 * 🌐 네트워크 상태 인디케이터
 */
@Composable
fun NetworkStatusIndicator(
    responseTimeMs: Int,
    successCount: Int,
    totalPlatforms: Int
) {
    val statusColor = when {
        successCount >= totalPlatforms -> Color(0xFF4CAF50)
        successCount >= totalPlatforms / 2 -> Color(0xFFFF9800)
        else -> Color(0xFFE53935)
    }

    Surface(
        color = statusColor.copy(alpha = 0.1f),
        shape = RoundedCornerShape(8.dp)
    ) {
        Text(
            text = "${successCount}/${totalPlatforms} (${responseTimeMs}ms)",
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
            style = MaterialTheme.typography.labelSmall,
            color = statusColor,
            fontWeight = FontWeight.Medium
        )
    }
}

/**
 * 🏷️ 커뮤니티 배지
 */
@Composable
fun CommunityBadge(community: String) {
    val displayName = getPlatformDisplayName(community)
    val badgeColor = when (community.lowercase()) {
        "ppomppu", "뽐뿌" -> Color(0xFFE65100)
        "clien", "클리앙" -> Color(0xFF1565C0)
        "ruliweb", "루리웹" -> Color(0xFF6A1B9A)
        "eleventh", "11번가" -> Color(0xFF2E7D32)
        else -> MaterialTheme.colorScheme.primary
    }

    Surface(
        color = badgeColor,
        shape = RoundedCornerShape(12.dp)
    ) {
        Text(
            text = displayName,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            style = MaterialTheme.typography.labelSmall,
            color = Color.White,
            fontWeight = FontWeight.Medium
        )
    }
}

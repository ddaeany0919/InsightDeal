package com.ddaeany0919.insightdeal.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * EnhancedHomeScreen의 접근성/가독성 보강 요소
 * - IconButton → A11yIconButton로 치환 예시 컴포넌트들
 * - 대비 향상된 배지/텍스트 색상 프리셋
 */

// 대비 강화 프리셋
val BadgeBlue = Color(0xFF1565C0)      // 진한 블루 (Clien 같은 톤)
val BadgeOrange = Color(0xFFE65100)    // 진한 오렌지 (Ppomppu 강조)
val BadgePurple = Color(0xFF6A1B9A)    // 진한 퍼플 (Ruliweb 톤)
val BadgeGreen = Color(0xFF2E7D32)     // 진한 그린 (11st 등)
val SavingGreen = Color(0xFF1B5E20)    // 절약 텍스트용 그린
val OnBadge = Color(0xFFFFFFFF)

@Composable
fun DealCardActionsA11y(
    isBookmarked: Boolean,
    onTrackClick: () -> Unit,
    onBookmarkClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // 추적 버튼 (48dp 터치 타겟은 Button 자체가 보장)
        OutlinedButton(
            onClick = onTrackClick,
            modifier = Modifier.width(80.dp)
        ) {
            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(4.dp))
            Text("추적", style = MaterialTheme.typography.labelSmall)
        }

        // 북마크 버튼 - A11yIconButton 사용
        A11yIconButton(
            onClick = onBookmarkClick,
            contentDescription = if (isBookmarked) "북마크 제거" else "북마크 추가"
        ) {
            Icon(
                imageVector = if (isBookmarked) Icons.Default.Bookmark else Icons.Default.BookmarkBorder,
                contentDescription = null,
                tint = if (isBookmarked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@Composable
fun CommunityBadgeStrong(community: String) {
    val (label, color) = when (community.lowercase()) {
        "ppomppu", "뽐뿌" -> community to BadgeOrange
        "clien", "클리앙" -> community to BadgeBlue
        "ruliweb", "루리웹" -> community to BadgePurple
        "eleventh", "11번가" -> community to BadgeGreen
        else -> community to MaterialTheme.colorScheme.primary
    }
    Surface(color = color, shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp)) {
        Text(
            text = label,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            style = MaterialTheme.typography.labelSmall,
            color = OnBadge
        )
    }
}

@Composable
fun SavingTextStrong(amountText: String) {
    Text(
        text = amountText,
        style = MaterialTheme.typography.labelMedium,
        color = SavingGreen,
        fontWeight = MaterialTheme.typography.labelMedium.fontWeight
    )
}

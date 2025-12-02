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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ddaeany0919.insightdeal.models.ApiDeal
import com.ddaeany0919.insightdeal.ui.formatPrice
import com.ddaeany0919.insightdeal.ui.formatRelativeTime
/**
 * EnhancedHomeScreen의 DealCard를 접근성/대비 강화 버전으로 대체
 */
@Composable
internal fun DealCard(
    deal: ApiDeal,
    onClick: () -> Unit,
    onBookmarkClick: () -> Unit,
    onTrackClick: () -> Unit
) {
    Card(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = deal.title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Medium,
                    maxLines = 2,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "최저가: ${formatPrice(deal.lowestPrice)}",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    if (deal.maxSaving > 0) {
                        Spacer(modifier = Modifier.width(8.dp))
                        SavingTextStrongApplied(deal.maxSaving)
                    }
                }
                Spacer(modifier = Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val communityName = deal.lowestPlatform?.uppercase() ?: "UNKNOWN"
                    CommunityBadgeStrongApplied(communityName)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = formatRelativeTime(deal.updatedAt),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    NetworkStatusIndicator(
                        responseTimeMs = deal.responseTimeMs,
                        successCount = deal.successCount,
                        totalPlatforms = 4
                    )
                }
            }
            Spacer(modifier = Modifier.width(12.dp))
            DealCardActions(
                isBookmarked = deal.isBookmarked,
                onTrackClick = onTrackClick,
                onBookmarkClick = onBookmarkClick
            )
        }
    }
}

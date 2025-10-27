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

@Composable
fun DealCardActions(
    isBookmarked: Boolean,
    onTrackClick: () -> Unit,
    onBookmarkClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        OutlinedButton(onClick = onTrackClick, modifier = Modifier.width(80.dp)) {
            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(4.dp))
            Text("추적", style = MaterialTheme.typography.labelSmall)
        }
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
fun CommunityBadgeStrongApplied(community: String) {
    CommunityBadgeStrong(community)
}

@Composable
fun SavingTextStrongApplied(amount: Int) {
    SavingTextStrong("${String.format("%,d원", amount)} 절약")
}

package com.ddaeany0919.insightdeal.presentation.wishlist

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.material3.DismissDirection
import androidx.compose.material3.DismissValue
import androidx.compose.material3.SwipeToDismiss
import androidx.compose.material3.rememberDismissState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WishlistSwipeToDismiss(
    modifier: Modifier = Modifier,
    onConfirmDelete: () -> Unit,
    content: @Composable RowScope.() -> Unit
) {
    val dismissState = rememberDismissState(
        confirmValueChange = { value: DismissValue ->
            if (value == DismissValue.DismissedToStart || value == DismissValue.DismissedToEnd) {
                onConfirmDelete()
                true
            } else false
        }
    )

    SwipeToDismiss(
        state = dismissState,
        background = {
            val color = if (
                dismissState.targetValue == DismissValue.DismissedToEnd ||
                dismissState.targetValue == DismissValue.DismissedToStart
            ) Color(0xFFB00020) else MaterialTheme.colorScheme.surfaceVariant

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(color)
                    .padding(16.dp),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Filled.Delete, contentDescription = "삭제", tint = Color.White)
            }
        },
        dismissContent = { Row(modifier = modifier, content = content) },
        directions = setOf(DismissDirection.StartToEnd, DismissDirection.EndToStart)
    )
}

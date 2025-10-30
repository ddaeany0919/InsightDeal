package com.ddaeany0919.insightdeal.presentation.wishlist

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.material3.DismissDirection
import androidx.compose.material3.DismissState
import androidx.compose.material3.DismissValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SwipeToDismiss
import androidx.compose.material3.rememberDismissState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WishlistSwipeToDismiss(
    modifier: Modifier = Modifier,
    content: @Composable RowScope.() -> Unit,
    onConfirmDelete: () -> Unit
) {
    val dismissState = rememberDismissState(
        confirmValueChange = { value ->
            if (value == DismissValue.DismissedToStart || value == DismissValue.DismissedToEnd) {
                onConfirmDelete()
                true
            } else false
        }
    )

    SwipeToDismiss(
        state = dismissState,
        background = {
            val color = when (dismissState.targetValue) {
                DismissValue.DismissedToEnd, DismissValue.DismissedToStart -> Color(0xFFB00020)
                else -> MaterialTheme.colorScheme.surfaceVariant
            }
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(color)
                    .padding(16.dp),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.Delete, contentDescription = "삭제", tint = Color.White)
            }
        },
        dismissContent = { Row(modifier = modifier, content = content) },
        directions = setOf(DismissDirection.StartToEnd, DismissDirection.EndToStart)
    )
}

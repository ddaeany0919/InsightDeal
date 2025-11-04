package com.ddaeany0919.insightdeal.presentation.wishlist

import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WishlistSwipeToDismiss(
    modifier: Modifier = Modifier,
    onConfirmDelete: () -> Unit,
    allowStartToEnd: Boolean = false,
    allowEndToStart: Boolean = true,
    positionalThresholdFraction: Float = 0.3f,
    content: @Composable RowScope.() -> Unit
) {
    val TAG = "SwipeDismiss"
    val dismissState = rememberSwipeToDismissBoxState(
        positionalThreshold = { fullDistance -> fullDistance * positionalThresholdFraction },
        confirmValueChange = { value: SwipeToDismissBoxValue ->
            Log.d(TAG, "confirmValueChange called with value = $value")
            val deleteTriggered = value == SwipeToDismissBoxValue.EndToStart || value == SwipeToDismissBoxValue.StartToEnd
            if (deleteTriggered) {
                Log.d(TAG, "delete callback triggered")
                onConfirmDelete()
                true
            } else false
        }
    )

    LaunchedEffect(dismissState.currentValue) {
        Log.d(TAG, "dismissState.currentValue changed: ${dismissState.currentValue}")
    }

    SwipeToDismissBox(
        state = dismissState,
        enableDismissFromStartToEnd = allowStartToEnd,
        enableDismissFromEndToStart = allowEndToStart,
        backgroundContent = {
            val active = dismissState.targetValue == SwipeToDismissBoxValue.EndToStart || dismissState.targetValue == SwipeToDismissBoxValue.StartToEnd
            val color = if (active) Color(0xFFB00020) else MaterialTheme.colorScheme.surfaceVariant

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
        content = { Row(modifier = modifier, content = content) }
    )
}

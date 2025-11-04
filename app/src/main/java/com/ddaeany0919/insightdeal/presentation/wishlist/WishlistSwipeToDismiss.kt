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
    
    Log.d(TAG, "WishlistSwipeToDismiss 초기화 - allowStartToEnd=$allowStartToEnd, allowEndToStart=$allowEndToStart")
    
    val dismissState = rememberSwipeToDismissBoxState(
        positionalThreshold = { fullDistance -> 
            val threshold = fullDistance * positionalThresholdFraction
            Log.d(TAG, "positionalThreshold 계산 - fullDistance=$fullDistance, threshold=$threshold")
            threshold
        },
        confirmValueChange = { value: SwipeToDismissBoxValue ->
            Log.d(TAG, "confirmValueChange 호출 - value=$value")
            val deleteTriggered = value == SwipeToDismissBoxValue.EndToStart || value == SwipeToDismissBoxValue.StartToEnd
            if (deleteTriggered) {
                Log.d(TAG, ">>> 삭제 동작 감지됨! 스와이프 방향: $value")
                Log.d(TAG, ">>> onConfirmDelete 콜백 호출 시작 (이것이 ViewModel.deleteItem으로 이어짐)")
                onConfirmDelete()
                Log.d(TAG, ">>> onConfirmDelete 콜백 호출 완료")
                true
            } else {
                Log.d(TAG, "삭제 동작 아님 - value=$value")
                false
            }
        }
    )

    LaunchedEffect(dismissState.currentValue) {
        Log.d(TAG, "dismissState.currentValue 변경 감지: ${dismissState.currentValue}")
        if (dismissState.currentValue != SwipeToDismissBoxValue.Settled) {
            Log.d(TAG, ">>> 사용자가 스와이프 진행 중: ${dismissState.currentValue}")
        }
    }

    LaunchedEffect(dismissState.targetValue) {
        Log.d(TAG, "dismissState.targetValue 변경 감지: ${dismissState.targetValue}")
        if (dismissState.targetValue != SwipeToDismissBoxValue.Settled) {
            Log.d(TAG, ">>> 스와이프 대상 위치: ${dismissState.targetValue}")
        }
    }

    SwipeToDismissBox(
        state = dismissState,
        enableDismissFromStartToEnd = allowStartToEnd,
        enableDismissFromEndToStart = allowEndToStart,
        backgroundContent = {
            val active = dismissState.targetValue == SwipeToDismissBoxValue.EndToStart || dismissState.targetValue == SwipeToDismissBoxValue.StartToEnd
            val color = if (active) {
                Log.d(TAG, "배경 활성 상태 - 빨간색 삭제 배경 표시")
                Color(0xFFB00020)
            } else {
                MaterialTheme.colorScheme.surfaceVariant
            }

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
        content = { 
            Row(modifier = modifier, content = content) 
        }
    )
}
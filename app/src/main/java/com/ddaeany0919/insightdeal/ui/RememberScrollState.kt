package com.ddaeany0919.insightdeal.ui

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable

/**
 * 💾 리스트 스크롤 위치 저장/복원 유틸
 * 사용자 재진입 시 잔상 없이 동일 위치로 표시
 */
@Composable
fun rememberSavedLazyListState(
    key: String,
    initialFirstVisibleItemIndex: Int = 0,
    initialFirstVisibleItemScrollOffset: Int = 0
): LazyListState {
    val saver: Saver<LazyListState, Any> = listSaver(
        save = { listOf(it.firstVisibleItemIndex, it.firstVisibleItemScrollOffset) },
        restore = { values ->
            LazyListState(
                firstVisibleItemIndex = values.getOrNull(0) ?: 0,
                firstVisibleItemScrollOffset = values.getOrNull(1) ?: 0
            )
        }
    )
    return rememberSaveable(key, saver = saver) {
        LazyListState(
            firstVisibleItemIndex = initialFirstVisibleItemIndex,
            firstVisibleItemScrollOffset = initialFirstVisibleItemScrollOffset
        )
    }
}

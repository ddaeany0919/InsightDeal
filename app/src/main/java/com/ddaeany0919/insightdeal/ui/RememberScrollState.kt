package com.ddaeany0919.insightdeal.ui

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable

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

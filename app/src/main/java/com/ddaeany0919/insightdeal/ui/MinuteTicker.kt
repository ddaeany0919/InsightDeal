package com.ddaeany0919.insightdeal.ui

import androidx.compose.runtime.*
import kotlinx.coroutines.delay

/**
 * ⏱️ 1분 주기 틱 - 상대시간 실시간 갱신용
 */
@Composable
fun rememberTimeTicker(): State<Long> {
    val tick = remember { mutableStateOf(System.currentTimeMillis()) }

    LaunchedEffect(Unit) {
        while (true) {
            delay(60_000)
            tick.value = System.currentTimeMillis()
        }
    }

    return tick
}
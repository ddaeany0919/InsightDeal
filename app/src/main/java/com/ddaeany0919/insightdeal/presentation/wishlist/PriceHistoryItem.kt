package com.ddaeany0919.insightdeal.presentation.wishlist

import java.time.LocalDateTime

data class PriceHistoryItem(
    val recordedAt: LocalDateTime,
    val lowestPrice: Int,
    val platform: String,
    val productTitle: String?
)

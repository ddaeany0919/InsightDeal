package com.ddaeany0919.insightdeal.models

data class PriceHistoryPoint(
    val date: String,
    val price: Int,
    val platform: String = ""
)

data class MallPrice(
    val platform: String,
    val price: Int,
    val url: String,
    val isLowest: Boolean = false
)

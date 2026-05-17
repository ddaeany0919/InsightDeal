package com.ddaeany0919.insightdeal.models

import com.google.gson.annotations.SerializedName

data class PriceHistoryPoint(
    @SerializedName(value = "recordedAt", alternate = ["date"])
    val date: String,
    @SerializedName("price")
    val price: Int,
    val platform: String = ""
)

data class MallPrice(
    val platform: String,
    val price: Int,
    val currency: String? = "KRW",
    val url: String,
    val isLowest: Boolean = false
)

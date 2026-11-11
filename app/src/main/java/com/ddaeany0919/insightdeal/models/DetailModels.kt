package com.ddaeany0919.insightdeal.models

import com.google.gson.annotations.SerializedName

data class PriceHistoryPoint(
    @SerializedName(value = "recordedAt", alternate = ["date"])
    val date: String,
    @SerializedName("price")
    val price: Int,
    @SerializedName("platform")
    val platform: String = ""
)

data class MallPrice(
    @SerializedName("platform") val platform: String,
    @SerializedName("price") val price: Int,
    @SerializedName("currency") val currency: String? = "KRW",
    @SerializedName("url") val url: String,
    @SerializedName("isLowest") val isLowest: Boolean = false
)

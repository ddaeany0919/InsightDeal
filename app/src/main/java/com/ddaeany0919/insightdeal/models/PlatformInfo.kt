package com.ddaeany0919.insightdeal.models

import com.google.gson.annotations.SerializedName

data class PlatformInfo(
    @SerializedName("price") val price: Int,
    @SerializedName("original_price") val originalPrice: Int? = null,
    @SerializedName("discount_rate") val discountRate: Int = 0,
    @SerializedName("url") val url: String = "",
    @SerializedName("shipping_fee") val shippingFee: Int = 0,
    @SerializedName("seller") val seller: String = "",
    @SerializedName("rating") val rating: Float = 0f,
    @SerializedName("is_available") val isAvailable: Boolean = true
)

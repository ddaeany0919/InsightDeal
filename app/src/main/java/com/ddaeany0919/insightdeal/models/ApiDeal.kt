package com.ddaeany0919.insightdeal.models

import com.google.gson.annotations.JsonAdapter
import com.google.gson.annotations.SerializedName

data class ApiDeal(
    @SerializedName("id") val id: String,
    @SerializedName("title") val title: String,
    @SerializedName("query") val query: String = "",
    @SerializedName("lowest_price") val lowestPrice: Int? = null,
    @SerializedName("max_saving") val maxSaving: Int = 0,
    @SerializedName("average_price") val averagePrice: Int? = null,
    @SerializedName("platforms") val platforms: Map<String, PlatformInfo?> = emptyMap(),
    @SerializedName("lowest_platform") val lowestPlatform: String? = null,
    @SerializedName("success_count") val successCount: Int = 0,
    @SerializedName("response_time_ms") val responseTimeMs: Int = 0,
    @SerializedName("updated_at") val updatedAt: String = "",
    val isBookmarked: Boolean = false,
    @SerializedName("image_url") val dealImageUrl: String? = null,
    @SerializedName("ecommerce_url") val ecommerceUrl: String? = null,
    @SerializedName("shop_name") val shopName: String? = null,
    @SerializedName("has_options") val hasOptions: Boolean = false,
    @SerializedName("options_count") val optionsCount: Int = 0,
    @SerializedName("options_data")
    @JsonAdapter(OptionsDataDeserializer::class)
    val optionsData: List<DealOptionItem> = emptyList(),
    @SerializedName("sub_urls") val subUrls: List<String> = emptyList()
) {
    companion object {
        fun fromDealItem(deal: DealItem): ApiDeal {
            return ApiDeal(
                id = deal.id.toString(),
                title = deal.title,
                query = deal.title,
                lowestPrice = deal.price.toInt(),
                maxSaving = ((deal.originalPrice ?: deal.price) - deal.price).coerceAtLeast(0L).toInt(),
                averagePrice = deal.originalPrice?.toInt(),
                platforms = emptyMap(),
                lowestPlatform = deal.siteName,
                successCount = maxOf(1, deal.siteNames.size),
                responseTimeMs = 0,
                updatedAt = deal.createdAt ?: "",
                dealImageUrl = deal.imageUrl,
                ecommerceUrl = deal.ecommerceUrl ?: deal.postUrl,
                shopName = deal.shopName,
                hasOptions = deal.hasMultipleOptions,
                optionsCount = deal.resolvedOptionCount,
                optionsData = deal.optionsData,
                subUrls = deal.resolvedSubUrls
            )
        }
    }

    val imageUrl: String? get() = dealImageUrl
    val url: String get() = ecommerceUrl ?: platforms.values.firstOrNull()?.url ?: ""
    val siteName: String get() = lowestPlatform ?: "Unknown"
    val originalPrice: Int? get() = averagePrice ?: platforms.values.firstOrNull()?.originalPrice
    val price: Int get() = lowestPrice ?: 0
}

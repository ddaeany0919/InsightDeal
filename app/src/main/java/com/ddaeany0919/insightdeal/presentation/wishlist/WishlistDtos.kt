package com.ddaeany0919.insightdeal.presentation.wishlist

import com.google.gson.annotations.SerializedName
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

// Gson snake_case → camelCase 매핑을 위해 @SerializedName 추가

data class WishlistCreateRequest(
    @SerializedName("keyword") val keyword: String,
    @SerializedName("target_price") val targetPrice: Int,
    @SerializedName("user_id") val userId: String
)

data class WishlistApiResponse(
    @SerializedName("id") val id: Int,
    @SerializedName("keyword") val keyword: String,
    @SerializedName("target_price") val targetPrice: Int,
    @SerializedName("current_lowest_price") val currentLowestPrice: Int? = null,
    @SerializedName("current_lowest_platform") val currentLowestPlatform: String? = null,
    @SerializedName("current_lowest_product_title") val currentLowestProductTitle: String? = null,
    @SerializedName("price_drop_percentage") val priceDropPercentage: Double = 0.0,
    @SerializedName("is_target_reached") val isTargetReached: Boolean = false,
    @SerializedName("is_active") val isActive: Boolean = true,
    @SerializedName("alert_enabled") val alertEnabled: Boolean = true,
    @SerializedName("created_at") val createdAt: String,
    @SerializedName("updated_at") val updatedAt: String,
    @SerializedName("last_checked") val lastChecked: String? = null
) {
    fun toWishlistItem(): WishlistItem = WishlistItem(
        id = id,
        keyword = keyword,
        targetPrice = targetPrice,
        currentLowestPrice = currentLowestPrice,
        currentLowestPlatform = currentLowestPlatform,
        currentLowestProductTitle = currentLowestProductTitle,
        priceDropPercentage = priceDropPercentage,
        isTargetReached = isTargetReached,
        isActive = isActive,
        alertEnabled = alertEnabled,
        createdAt = parseDateTime(createdAt),
        updatedAt = parseDateTime(updatedAt),
        lastChecked = lastChecked?.let { parseDateTime(it) }
    )

    private fun parseDateTime(dateTimeString: String): LocalDateTime = try {
        LocalDateTime.parse(
            dateTimeString.substring(0, 19),
            DateTimeFormatter.ISO_LOCAL_DATE_TIME
        )
    } catch (e: Exception) {
        LocalDateTime.now()
    }
}

data class PriceHistoryApiResponse(
    @SerializedName("recorded_at") val recordedAt: String,
    @SerializedName("lowest_price") val lowestPrice: Int,
    @SerializedName("platform") val platform: String,
    @SerializedName("product_title") val productTitle: String?
) {
    fun toPriceHistoryItem(): PriceHistoryItem = PriceHistoryItem(
        recordedAt = parseDateTime(recordedAt),
        lowestPrice = lowestPrice,
        platform = platform,
        productTitle = productTitle
    )

    private fun parseDateTime(dateTimeString: String): LocalDateTime = try {
        LocalDateTime.parse(
            dateTimeString.substring(0, 19),
            DateTimeFormatter.ISO_LOCAL_DATE_TIME
        )
    } catch (e: Exception) {
        LocalDateTime.now()
    }
}

data class PriceCheckResponse(
    @SerializedName("message") val message: String,
    @SerializedName("keyword") val keyword: String,
    @SerializedName("current_price") val currentPrice: Int?,
    @SerializedName("target_price") val targetPrice: Int,
    @SerializedName("updated_at") val updatedAt: String
)

data class DeleteResponse(
    @SerializedName("message") val message: String
)

package com.ddaeany0919.insightdeal.presentation.wishlist

import com.google.gson.annotations.SerializedName
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

// Gson snake_case → camelCase 매핑을 위해 @SerializedName 추가

// ❌ 제거: WishlistCreateRequest는 이제 WishlistApiService.kt에서 분리됨
// - WishlistCreateFromKeywordRequest
// - WishlistCreateFromUrlRequest

data class WishlistApiResponse(
    @SerializedName("id") val id: Int,
    @SerializedName("keyword") val keyword: String,
    @SerializedName("target_price") val targetPrice: Int,
    @SerializedName("user_id") val userId: String? = null,
    @SerializedName("current_lowest_price") val currentLowestPrice: Int? = null,
    @SerializedName("current_lowest_platform") val currentLowestPlatform: String? = null,
    @SerializedName("current_lowest_product_title") val currentLowestProductTitle: String? = null,
    @SerializedName("price_drop_percentage") val priceDropPercentage: Double = 0.0,
    @SerializedName("is_target_reached") val isTargetReached: Boolean = false,
    @SerializedName("is_active") val isActive: Boolean = true,
    @SerializedName("alert_enabled") val alertEnabled: Boolean = true,
    @SerializedName("created_at") val createdAt: String,
    @SerializedName("updated_at") val updatedAt: String,
    @SerializedName("last_checked") val lastChecked: String? = null,
    // 추가 마켓 확장 필드 (현재는 네이버만 사용, 나머지는 null 허용)
    @SerializedName("naver_price") val naverPrice: Int? = null,
    @SerializedName("naver_url") val naverUrl: String? = null,
    @SerializedName("coupang_price") val coupangPrice: Int? = null,
    @SerializedName("coupang_url") val coupangUrl: String? = null,
    @SerializedName("gmarket_price") val gmarketPrice: Int? = null,
    @SerializedName("gmarket_url") val gmarketUrl: String? = null,
    @SerializedName("elevenst_price") val elevenstPrice: Int? = null,
    @SerializedName("elevenst_url") val elevenstUrl: String? = null
) {
    fun toWishlistItem(): WishlistItem = WishlistItem(
        id = id,
        keyword = keyword,
        targetPrice = targetPrice,
        currentLowestPrice = currentLowestPrice ?: naverPrice,
        currentLowestPlatform = currentLowestPlatform ?: (if (naverPrice != null) "naver_shopping" else null),
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

// ✅ 추가: PriceHistoryApiResponse
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

// ✅ 추가: PriceCheckResponse
data class PriceCheckResponse(
    @SerializedName("message") val message: String,
    @SerializedName("keyword") val keyword: String? = null,
    @SerializedName("current_price") val currentPrice: Int? = null,
    @SerializedName("target_price") val targetPrice: Int? = null,
    @SerializedName("platform") val platform: String? = null,
    @SerializedName("product_url") val productUrl: String? = null,
    @SerializedName("title") val title: String? = null,
    @SerializedName("is_target_reached") val isTargetReached: Boolean? = null,
    @SerializedName("updated_at") val updatedAt: String? = null,
    // 확장: 나머지 마켓은 추후에 채움
    @SerializedName("naver_price") val naverPrice: Int? = null,
    @SerializedName("naver_url") val naverUrl: String? = null,
    @SerializedName("coupang_price") val coupangPrice: Int? = null,
    @SerializedName("coupang_url") val coupangUrl: String? = null
)

data class DeleteResponse(
    @SerializedName("message") val message: String
)

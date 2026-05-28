package com.ddaeany0919.insightdeal.models

import com.google.gson.annotations.SerializedName
import androidx.compose.runtime.Stable

/**
 * 🔥 핫딜 아이템 데이터 클래스
 */
@Stable
data class DealSource(
    @SerializedName("site_name") val siteName: String,
    @SerializedName("post_url") val postUrl: String,
    @SerializedName("ecommerce_url") val ecommerceUrl: String? = null,
    @SerializedName("price") val price: Long? = null,
    @SerializedName("currency") val currency: String? = null
)

@Stable
data class DealItem(
    @SerializedName("id")
    val id: Int,
    
    @SerializedName("title")
    val title: String,
    
    @SerializedName("price")
    val price: Long,
    
    @SerializedName("currency")
    val currency: String = "KRW",
    
    @SerializedName("original_price")
    val originalPrice: Long? = null,
    
    @SerializedName("discount_rate")
    val discountRate: Int? = null,
    
    @SerializedName("image_url")
    val imageUrl: String? = null,
    
    @SerializedName("ecommerce_url")
    val ecommerceUrl: String? = null,

    @SerializedName("post_url")
    val postUrl: String? = null,
    
    @SerializedName("site_name")
    val siteName: String,
    
    @SerializedName("site_names")
    val siteNames: List<String> = emptyList(),

    @SerializedName("sources")
    val sources: List<DealSource>? = emptyList(),

    @SerializedName("shipping_fee")
    val shippingFee: String? = null,
    
    @SerializedName("category")
    val category: String? = null,
    
    @SerializedName(value = "created_at", alternate = ["indexed_at"])
    val createdAt: String? = null,
    
    @SerializedName(value = "view_count", alternate = ["views"])
    val viewCount: Int = 0,
    
    @SerializedName(value = "comment_count", alternate = ["comments"])
    val commentCount: Int = 0,
    
    @SerializedName(value = "like_count", alternate = ["recommendations"])
    val likeCount: Int = 0,

    @SerializedName("dislike_count")
    val dislikeCount: Int = 0,

    @SerializedName("tags")
    val tags: List<String> = emptyList(),

    @SerializedName(value = "honey_score", alternate = ["score"])
    val honeyScore: Int = 0,

    @SerializedName("ai_summary")
    val aiSummary: String? = null,

    @SerializedName("content_html")
    val contentHtml: String? = null,

    @SerializedName("is_closed")
    val isClosed: Boolean = false
) {
    /**
     * 💰 할인된 가격 계산
     */
    val discountedPrice: Long
        get() = originalPrice?.let { original ->
            discountRate?.let { rate ->
                (original * (100 - rate) / 100)
            } ?: price
        } ?: price
    
    /**
     * 🔥 핫딜 여부 (할인율 20% 이상)
     */
    val isHotDeal: Boolean
        get() = (discountRate ?: 0) >= 20
    
    /**
     * ⭐ 인기도 점수 (조회수, 댓글수, 좋아요 기반)
     */
    val popularityScore: Int
        get() = viewCount + (commentCount * 5) + (likeCount * 10) - (dislikeCount * 5)
}

/**
 * 📊 핫딜 통계 데이터
 */
data class DealStats(
    val totalDeals: Int,
    val hotDeals: Int,
    val averageDiscount: Double,
    val topCategories: List<CategoryStats>
)

data class CategoryStats(
    val category: String,
    val count: Int,
    val percentage: Double
)

/**
 * 🔍 검색 필터
 */
data class DealFilter(
    val keyword: String? = null,
    val category: String? = null,
    val site: String? = null,
    val minPrice: Int? = null,
    val maxPrice: Int? = null,
    val minDiscount: Int? = null,
    val sortBy: SortType = SortType.LATEST
)

enum class SortType {
    LATEST,     // 최신순
    POPULAR,    // 인기순
    DISCOUNT,   // 할인율순
    PRICE_LOW,  // 가격 낮은순
    PRICE_HIGH  // 가격 높은순
}
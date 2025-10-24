package com.ddaeany0919.insightdeal.models

import com.google.gson.annotations.SerializedName

/**
 * 🔥 핫딜 아이템 데이터 클래스
 */
data class DealItem(
    @SerializedName("id")
    val id: Long,
    
    @SerializedName("title")
    val title: String,
    
    @SerializedName("price")
    val price: Int,
    
    @SerializedName("original_price")
    val originalPrice: Int? = null,
    
    @SerializedName("discount_rate")
    val discountRate: Int? = null,
    
    @SerializedName("image_url")
    val imageUrl: String? = null,
    
    @SerializedName("url")
    val url: String,
    
    @SerializedName("site_name")
    val siteName: String,
    
    @SerializedName("category")
    val category: String? = null,
    
    @SerializedName("created_at")
    val createdAt: String? = null,
    
    @SerializedName("view_count")
    val viewCount: Int = 0,
    
    @SerializedName("comment_count")
    val commentCount: Int = 0,
    
    @SerializedName("like_count")
    val likeCount: Int = 0,
    
    @SerializedName("tags")
    val tags: List<String> = emptyList()
) {
    /**
     * 💰 할인된 가격 계산
     */
    val discountedPrice: Int
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
        get() = viewCount + (commentCount * 5) + (likeCount * 10)
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
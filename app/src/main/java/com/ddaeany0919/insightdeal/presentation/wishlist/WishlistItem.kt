package com.ddaeany0919.insightdeal.presentation.wishlist

import java.time.LocalDateTime
import com.ddaeany0919.insightdeal.presentation.wishlist.PriceCheckResponse
import android.util.Log

/** 관심상품 아이템 (UI 모델) */
data class WishlistItem(
    val id: Int,
    val keyword: String,
    val productUrl: String = "",  // productUrl 필드 추가 (기본값: 빈 문자열)
    val targetPrice: Int,
    val currentLowestPrice: Int? = null,
    val currentLowestPlatform: String? = null,
    val currentLowestProductTitle: String? = null,
    val priceDropPercentage: Double = 0.0,
    val isTargetReached: Boolean = false,
    val isActive: Boolean = true,
    val alertEnabled: Boolean = true,
    val createdAt: LocalDateTime,
    val updatedAt: LocalDateTime,
    val lastChecked: LocalDateTime? = null,
    val latestPriceCheckResult: PriceCheckResponse? = null,
    val isLoading: Boolean = false
) {
    init {
        Log.d("WishlistItem", "WishlistItem 생성: id=$id, keyword=$keyword, productUrl=$productUrl, targetPrice=$targetPrice")
    }
}

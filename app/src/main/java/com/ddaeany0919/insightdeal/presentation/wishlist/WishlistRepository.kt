package com.ddaeany0919.insightdeal.presentation.wishlist

import com.ddaeany0919.insightdeal.data.network.WishlistApiService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * 💎 관심상품 Repository
 * 백엔드 API와 통신하여 관심상품 데이터를 관리
 */
class WishlistRepository(
    private val apiService: WishlistApiService = WishlistApiService.create()
) {
    /** 관심상품 목록 조회 */
    suspend fun getWishlist(userId: String = "default"): List<WishlistItem> = withContext(Dispatchers.IO) {
        try {
            val response = apiService.getWishlist(userId)
            response.map { it.toWishlistItem() }
        } catch (e: Exception) {
            throw Exception("관심상품 목록을 불러오는 데 실패했습니다: ${e.message}")
        }
    }

    /** 관심상품 추가 */
    suspend fun createWishlist(
        keyword: String,
        targetPrice: Int,
        userId: String = "default"
    ): WishlistItem = withContext(Dispatchers.IO) {
        try {
            val request = WishlistCreateRequest(
                keyword = keyword,
                targetPrice = targetPrice,
                userId = userId
            )
            val response = apiService.createWishlist(request)
            response.toWishlistItem()
        } catch (e: Exception) {
            throw Exception("관심상품 추가에 실패했습니다: ${e.message}")
        }
    }

    /** 관심상품 삭제 */
    suspend fun deleteWishlist(wishlistId: Int, userId: String = "default") = withContext(Dispatchers.IO) {
        try {
            apiService.deleteWishlist(wishlistId, userId)
        } catch (e: Exception) {
            throw Exception("관심상품 삭제에 실패했습니다: ${e.message}")
        }
    }

    /** 수동 가격 체크 */
    suspend fun checkPrice(wishlistId: Int, userId: String = "default"): String = withContext(Dispatchers.IO) {
        try {
            val response = apiService.checkWishlistPrice(wishlistId, userId)
            response.message
        } catch (e: Exception) {
            throw Exception("가격 체크에 실패했습니다: ${e.message}")
        }
    }

    /** 관심상품 가격 히스토리 조회 */
    suspend fun getPriceHistory(
        wishlistId: Int,
        days: Int = 30,
        userId: String = "default"
    ): List<PriceHistoryItem> = withContext(Dispatchers.IO) {
        try {
            val response = apiService.getWishlistPriceHistory(wishlistId, userId, days)
            response.map { it.toPriceHistoryItem() }
        } catch (e: Exception) {
            throw Exception("가격 히스토리를 불러오는 데 실패했습니다: ${e.message}")
        }
    }
}

// ===== UI 모델만 유지 =====

data class WishlistItem(
    val id: Int,
    val keyword: String,
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
    val lastChecked: LocalDateTime? = null
)

// DTO → UI 변환 확장 (WishlistDtos.kt의 DTO 사용)

fun WishlistApiResponse.toWishlistItem(): WishlistItem = WishlistItem(
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

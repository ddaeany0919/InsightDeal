package com.ddaeany0919.insightdeal.presentation.wishlist

import com.ddaeany0919.insightdeal.data.network.WishlistApiService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 💎 관심상품 Repository
 * 백엔드 API와 통신하여 관심상품 데이터를 관리
 */
class WishlistRepository(
    private val apiService: WishlistApiService = WishlistApiService.create()
) {
    /** 관심상품 목록 조회 */
    suspend fun getWishlist(userId: String): List<WishlistItem> = withContext(Dispatchers.IO) {
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
        userId: String
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
    suspend fun deleteWishlist(wishlistId: Int, userId: String) = withContext(Dispatchers.IO) {
        try {
            val res = apiService.deleteWishlist(wishlistId, userId)
            res
        } catch (e: Exception) {
            throw Exception("관심상품 삭제에 실패했습니다: ${e.message}")
        }
    }

    /** 수동 가격 체크 */
    suspend fun checkPrice(wishlistId: Int, userId: String): String = withContext(Dispatchers.IO) {
        try {
            val response = apiService.checkWishlistPrice(wishlistId, userId)
            response.message
        } catch (e: Exception) {
            throw Exception("가격 체크에 실패했습니다: ${e.message}")
        }
    }
}

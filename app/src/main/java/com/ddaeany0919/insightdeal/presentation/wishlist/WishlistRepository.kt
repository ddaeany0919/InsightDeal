package com.ddaeany0919.insightdeal.presentation.wishlist

import android.util.Log
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
    private val TAG = "WishlistRepo"

    /** 관심상품 목록 조회 */
    suspend fun getWishlist(userId: String): List<WishlistItem> = withContext(Dispatchers.IO) {
        Log.d(TAG, "getWishlist: 요청 userId=$userId")
        try {
            val response = apiService.getWishlist(userId)
            Log.d(TAG, "getWishlist: 성공 count=${response.size}")
            response.map { it.toWishlistItem() }
        } catch (e: Exception) {
            Log.e(TAG, "getWishlist: 오류 ${e.message}", e)
            throw Exception("관심상품 목록을 불러오는 데 실패했습니다: "+e.message)
        }
    }

    /** 관심상품 추가 */
    suspend fun createWishlist(
        keyword: String,
        targetPrice: Int,
        userId: String
    ): WishlistItem = withContext(Dispatchers.IO) {
        Log.d(TAG, "createWishlist: 요청 keyword=$keyword targetPrice=$targetPrice userId=$userId")
        try {
            val request = WishlistCreateRequest(
                keyword = keyword,
                targetPrice = targetPrice,
                userId = userId
            )
            val response = apiService.createWishlist(request)
            Log.d(TAG, "createWishlist: 성공 id=${response.id}")
            response.toWishlistItem()
        } catch (e: Exception) {
            Log.e(TAG, "createWishlist: 오류 ${e.message}", e)
            throw Exception("관심상품 추가에 실패했습니다: "+e.message)
        }
    }

    /** 관심상품 삭제 */
    suspend fun deleteWishlist(wishlistId: Int, userId: String) = withContext(Dispatchers.IO) {
        Log.d(TAG, "deleteWishlist: 요청 id=$wishlistId userId=$userId")
        try {
            val res = apiService.deleteWishlist(wishlistId, userId)
            Log.d(TAG, "deleteWishlist: 성공 res=$res")
            res
        } catch (e: Exception) {
            Log.e(TAG, "deleteWishlist: 오류 ${e.message}", e)
            throw Exception("관심상품 삭제에 실패했습니다: "+e.message)
        }
    }

    /** 수동 가격 체크 */
    suspend fun checkPrice(wishlistId: Int, userId: String): String = withContext(Dispatchers.IO) {
        Log.d(TAG, "checkPrice: 요청 id=$wishlistId userId=$userId")
        try {
            val response = apiService.checkWishlistPrice(wishlistId, userId)
            Log.d(TAG, "checkPrice: 성공 message=${response.message}")
            response.message
        } catch (e: Exception) {
            Log.e(TAG, "checkPrice: 오류 ${e.message}", e)
            throw Exception("가격 체크에 실패했습니다: "+e.message)
        }
    }
}

package com.ddaeany0919.insightdeal.presentation.wishlist

import android.util.Log
import com.ddaeany0919.insightdeal.data.network.DeleteRequest
import com.ddaeany0919.insightdeal.data.network.WishlistApiService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class WishlistRepository(
    private val apiService: WishlistApiService = WishlistApiService.create()
) {
    private val TAG = "WishlistRepo"

    suspend fun getWishlist(userId: String) = withContext(Dispatchers.IO) {
        Log.d(TAG, "getWishlist: API 호출 시작 - userId=$userId")
        val response = apiService.getWishlist(userId)
        Log.d(TAG, "getWishlist: API 응답 성공 - userId=$userId, count=${response.size}")
        response.map { it.toWishlistItem() }
    }

    suspend fun createWishlist(keyword: String, targetPrice: Int, userId: String) = withContext(Dispatchers.IO) {
        Log.d(TAG, "createWishlist: API 호출 시작 - keyword=$keyword, targetPrice=$targetPrice, userId=$userId")
        val request = WishlistCreateRequest(keyword, targetPrice, userId)
        apiService.createWishlist(request).toWishlistItem()
    }

    suspend fun deleteWishlist(wishlistId: Int, userId: String): Boolean = withContext(Dispatchers.IO) {
        Log.d(TAG, "deleteWishlist: API 호출 시작 - id=$wishlistId, userId=$userId (DELETE with body)")
        try {
            val res = apiService.deleteWishlist(wishlistId, DeleteRequest(userId))
            Log.d(TAG, "deleteWishlist: API 응답 - message='${res.message}'")
            true
        } catch (e: Exception) {
            Log.e(TAG, "deleteWishlist: 실패 - id=$wishlistId, userId=$userId, error=${e.message}", e)
            throw Exception("관심상품 삭제에 실패했습니다: "+e.message)
        }
    }

    suspend fun checkPrice(wishlistId: Int, userId: String): String = withContext(Dispatchers.IO) {
        Log.d(TAG, "checkPrice: API 호출 시작 - id=$wishlistId, userId=$userId")
        apiService.checkWishlistPrice(wishlistId, userId).message
    }
}

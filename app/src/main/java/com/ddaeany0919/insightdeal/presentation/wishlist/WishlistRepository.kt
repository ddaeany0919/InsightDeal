package com.ddaeany0919.insightdeal.presentation.wishlist

import android.util.Log
import com.ddaeany0919.insightdeal.data.network.DeleteRequest
import com.ddaeany0919.insightdeal.data.network.WishlistApiService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import retrofit2.Response

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
        Log.d(TAG, "deleteWishlist: 시도 #1 DELETE body - id=$wishlistId userId=$userId")
        tryCall(apiService.deleteWithBody(wishlistId, DeleteRequest(userId)))?.let { return@withContext it }

        Log.d(TAG, "deleteWishlist: 시도 #2 DELETE header - id=$wishlistId userId=$userId")
        tryCall(apiService.deleteWithHeader(wishlistId, userId))?.let { return@withContext it }

        Log.d(TAG, "deleteWishlist: 시도 #3 DELETE query - id=$wishlistId userId=$userId")
        tryCall(apiService.deleteWithQuery(wishlistId, userId))?.let { return@withContext it }

        Log.d(TAG, "deleteWishlist: 시도 #4 DELETE alt path - id=$wishlistId userId=$userId")
        tryCall(apiService.deleteAltPath(wishlistId, DeleteRequest(userId)))?.let { return@withContext it }

        Log.d(TAG, "deleteWishlist: 시도 #5 POST /delete - id=$wishlistId userId=$userId")
        tryCall(apiService.postDelete(wishlistId, DeleteRequest(userId)))?.let { return@withContext it }

        Log.e(TAG, "deleteWishlist: 모든 폴백 실패 - id=$wishlistId userId=$userId")
        throw Exception("관심상품 삭제에 실패했습니다: 모든 폴백 시도 불가")
    }

    private fun tryCall(resp: Response<Unit>): Boolean? {
        return if (resp.isSuccessful) {
            Log.d(TAG, "deleteWishlist: 성공 - http=${resp.code()}")
            true
        } else {
            Log.w(TAG, "deleteWishlist: 실패 - http=${resp.code()} msg=${resp.message()}")
            null
        }
    }

    suspend fun checkPrice(wishlistId: Int, userId: String): String = withContext(Dispatchers.IO) {
        Log.d(TAG, "checkPrice: API 호출 시작 - id=$wishlistId, userId=$userId")
        apiService.checkWishlistPrice(wishlistId, userId).message
    }
}

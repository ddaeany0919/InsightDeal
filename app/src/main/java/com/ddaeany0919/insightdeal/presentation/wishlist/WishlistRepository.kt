package com.ddaeany0919.insightdeal.presentation.wishlist

import android.util.Log
import com.ddaeany0919.insightdeal.data.network.WishlistApiService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 📎 관심상품 Repository
 * 백엔드 API와 통신하여 관심상품 데이터를 관리
 */
class WishlistRepository(
    private val apiService: WishlistApiService = WishlistApiService.create()
) {
    private val TAG = "WishlistRepo"

    /** 관심상품 목록 조회 */
    suspend fun getWishlist(userId: String): List<WishlistItem> = withContext(Dispatchers.IO) {
        Log.d(TAG, "getWishlist: API 호출 시작 - userId=$userId")
        try {
            val response = apiService.getWishlist(userId)
            Log.d(TAG, "getWishlist: API 응답 성공 - userId=$userId, count=${response.size}")
            val items = response.map { it.toWishlistItem() }
            Log.d(TAG, "getWishlist: 데이터 변환 완료 - userId=$userId")
            items
        } catch (e: Exception) {
            Log.e(TAG, "getWishlist: API 호출 실패 - userId=$userId, error: ${e.message}", e)
            throw Exception("관심상품 목록을 불러오는 데 실패했습니다: "+e.message)
        }
    }

    /** 관심상품 추가 */
    suspend fun createWishlist(
        keyword: String,
        targetPrice: Int,
        userId: String
    ): WishlistItem = withContext(Dispatchers.IO) {
        Log.d(TAG, "createWishlist: API 호출 시작 - keyword=$keyword, targetPrice=$targetPrice, userId=$userId")
        try {
            val request = WishlistCreateRequest(
                keyword = keyword,
                targetPrice = targetPrice,
                userId = userId
            )
            Log.d(TAG, "createWishlist: 요청 데이터 준비 완료 - userId=$userId")
            val response = apiService.createWishlist(request)
            Log.d(TAG, "createWishlist: API 응답 성공 - userId=$userId, id=${response.id}")
            val item = response.toWishlistItem()
            Log.d(TAG, "createWishlist: 데이터 변환 완료 - userId=$userId")
            item
        } catch (e: Exception) {
            Log.e(TAG, "createWishlist: API 호출 실패 - userId=$userId, error: ${e.message}", e)
            throw Exception("관심상품 추가에 실패했습니다: "+e.message)
        }
    }

    /** 관심상품 삭제 */
    suspend fun deleteWishlist(wishlistId: Int, userId: String) = withContext(Dispatchers.IO) {
        Log.d(TAG, "deleteWishlist: API 호출 시작 - id=$wishlistId, userId=$userId (DELETE 요청)")
        try {
            Log.d(TAG, "deleteWishlist: 서버로 DELETE 요청 전송 중 - id=$wishlistId, userId=$userId")
            val res = apiService.deleteWishlist(wishlistId, userId)
            Log.d(TAG, "deleteWishlist: API 응답 성공 - id=$wishlistId, userId=$userId, result=$res")
            if (res) {
                Log.d(TAG, "deleteWishlist: 삭제 성공 확인 - id=$wishlistId, userId=$userId")
            } else {
                Log.w(TAG, "deleteWishlist: 삭제 실패 응답 - id=$wishlistId, userId=$userId")
            }
            res
        } catch (e: Exception) {
            Log.e(TAG, "deleteWishlist: API 호출 실패 - id=$wishlistId, userId=$userId, error: ${e.message}", e)
            if (e.message?.contains("404") == true) {
                Log.e(TAG, "deleteWishlist: 404 오류 발생 - 아마 userId 문제일 가능성 높음 - id=$wishlistId, userId=$userId")
            }
            throw Exception("관심상품 삭제에 실패했습니다: "+e.message)
        }
    }

    /** 수동 가격 체크 */
    suspend fun checkPrice(wishlistId: Int, userId: String): String = withContext(Dispatchers.IO) {
        Log.d(TAG, "checkPrice: API 호출 시작 - id=$wishlistId, userId=$userId")
        try {
            val response = apiService.checkWishlistPrice(wishlistId, userId)
            Log.d(TAG, "checkPrice: API 응답 성공 - id=$wishlistId, userId=$userId, message=${response.message}")
            response.message
        } catch (e: Exception) {
            Log.e(TAG, "checkPrice: API 호출 실패 - id=$wishlistId, userId=$userId, error: ${e.message}", e)
            throw Exception("가격 체크에 실패했습니다: "+e.message)
        }
    }
}
package com.ddaeany0919.insightdeal.presentation.wishlist

import android.util.Log
import com.ddaeany0919.insightdeal.data.network.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import retrofit2.HttpException
import java.io.IOException
import java.net.SocketTimeoutException

/**
 * Production-ready WishlistRepository
 * - Single Retrofit path for ALL network calls (no OkHttp direct)
 * - No hardcoded URLs; uses AppConfig/NetworkConfig
 * - User ID will be provided by Auth layer later (OAuth/SNS login)
 */
class WishlistRepository(
    private val apiServiceProvider: suspend () -> WishlistApiService = {
        WishlistApiService.createWithStableConfig()
    }
) {
    private val TAG = "WishlistRepo"
    private val MAX_RETRIES = 3
    private val RETRY_DELAY_MS = 1000L

    private var apiService: WishlistApiService? = null
    private suspend fun service(): WishlistApiService {
        if (apiService == null) apiService = apiServiceProvider()
        return apiService!!
    }

    suspend fun getWishlist(userId: String) = withContext(Dispatchers.IO) {
        Log.d(TAG, "getWishlist: start userId=$userId")
        executeWithRetry("getWishlist") {
            service().getWishlist(userId).map { it.toWishlistItem() }
        }
    }

    suspend fun createWishlist(keyword: String, targetPrice: Int, userId: String) = withContext(Dispatchers.IO) {
        Log.d(TAG, "createWishlist: start keyword=$keyword target=$targetPrice userId=$userId")
        executeWithRetry("createWishlist") {
            service().createWishlist(WishlistCreateRequest(keyword, targetPrice, userId)).toWishlistItem()
        }
    }

    suspend fun addFromLink(url: String, targetPrice: Int, userId: String) = withContext(Dispatchers.IO) {
        Log.d(TAG, "addFromLink: start url=$url target=$targetPrice userId=$userId")
        executeWithRetry("addFromLink") {
            // Phase 2: use real endpoint
            // val res = service().addFromLink(LinkAddRequest(url, targetPrice, userId))
            // res.toWishlistItem()

            // Phase 1 fallback: convert link→name then create
            val productName = extractProductNameFromUrl(url)
            service().createWishlist(WishlistCreateRequest(productName, targetPrice, userId)).toWishlistItem()
        }
    }

    suspend fun deleteWishlist(wishlistId: Int, userId: String): Boolean = withContext(Dispatchers.IO) {
        Log.d(TAG, "deleteWishlist: start id=$wishlistId userId=$userId")
        executeWithRetry("deleteWishlist") {
            val resp = service().deleteWithQuery(wishlistId, userId)
            resp.isSuccessful
        }
    }

    suspend fun checkPrice(wishlistId: Int, userId: String): String = withContext(Dispatchers.IO) {
        Log.d(TAG, "checkPrice: start id=$wishlistId userId=$userId")
        executeWithRetry("checkPrice") {
            service().checkWishlistPrice(wishlistId, userId).message
        }
    }

    private suspend fun <T> executeWithRetry(name: String, op: suspend () -> T): T {
        var last: Exception? = null
        repeat(MAX_RETRIES) { attempt ->
            try {
                Log.d(TAG, "$name: try ${attempt + 1}/$MAX_RETRIES")
                return op()
            } catch (e: SocketTimeoutException) {
                last = e; Log.w(TAG, "$name: timeout: ${e.message}"); if (attempt < MAX_RETRIES - 1) delay(RETRY_DELAY_MS * (attempt + 1))
            } catch (e: IOException) {
                last = e; Log.w(TAG, "$name: io: ${e.message}"); if (attempt < MAX_RETRIES - 1) delay(RETRY_DELAY_MS)
            } catch (e: HttpException) {
                Log.e(TAG, "$name: http ${e.code()}"); throw e
            } catch (e: Exception) {
                Log.e(TAG, "$name: unknown", e); throw e
            }
        }
        throw last ?: Exception("$name failed")
    }

    private fun extractProductNameFromUrl(url: String): String {
        return try {
            val m = Regex("""/vp/products/(\d+)""").find(url)
            if (m != null) "쿠팡 상품 (${m.groupValues[1]})" else when {
                url.contains("11st.co.kr") -> "11번가 상품"
                url.contains("shopping.naver.com") -> "네이버 쇼핑 상품"
                else -> "링크로 추가된 상품"
            }
        } catch (e: Exception) {
            Log.w(TAG, "extractProductNameFromUrl fail", e); "링크로 추가된 상품"
        }
    }
}

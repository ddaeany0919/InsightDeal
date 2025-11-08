package com.ddaeany0919.insightdeal.presentation.wishlist

import android.util.Log
import com.ddaeany0919.insightdeal.data.network.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import retrofit2.HttpException
import java.io.IOException
import java.net.SocketTimeoutException

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

    /**
     * 위시리스트 추가 - 키워드 또는 URL 자동 판단
     * @param keyword 검색어 또는 URL
     * @param productUrl URL (선택적, 비어있으면 keyword가 URL인지 판단)
     */
    suspend fun createWishlist(
        keyword: String, 
        productUrl: String, 
        targetPrice: Int, 
        userId: String
    ) = withContext(Dispatchers.IO) {
        Log.d(TAG, "createWishlist: keyword=$keyword url=$productUrl target=$targetPrice userId=$userId")
        
        executeWithRetry("createWishlist") {
            // URL 판단: productUrl이 있거나 keyword가 http로 시작하면 URL 방식
            val isUrl = productUrl.isNotBlank() || keyword.startsWith("http://") || keyword.startsWith("https://")
            
            if (isUrl) {
                // URL 방식으로 추가
                val url = if (productUrl.isNotBlank()) productUrl else keyword
                Log.d(TAG, "createWishlist: using from-url endpoint with url=$url")
                service().createWishlistFromUrl(
                    WishlistCreateFromUrlRequest(
                        productUrl = url,
                        targetPrice = targetPrice,
                        userId = userId
                    )
                ).toWishlistItem()
            } else {
                // 키워드 방식으로 추가
                Log.d(TAG, "createWishlist: using from-keyword endpoint with keyword=$keyword")
                service().createWishlistFromKeyword(
                    WishlistCreateFromKeywordRequest(
                        keyword = keyword,
                        targetPrice = targetPrice,
                        userId = userId
                    )
                ).toWishlistItem()
            }
        }
    }

    suspend fun deleteWishlist(wishlistId: Int, userId: String): Boolean = withContext(Dispatchers.IO) {
        Log.d(TAG, "deleteWishlist: start id=$wishlistId userId=$userId")
        executeWithRetry("deleteWishlist") {
            val resp = service().deleteWithQuery(wishlistId, userId)
            Log.d(TAG, "deleteWishlist: response code=${resp.code()}, success=${resp.isSuccessful}")
            resp.isSuccessful
        }
    }

    suspend fun checkPrice(wishlistId: Int, userId: String): String = withContext(Dispatchers.IO) {
        Log.d(TAG, "checkPrice: start id=$wishlistId userId=$userId")
        executeWithRetry("checkPrice") {
            val response = service().checkWishlistPrice(wishlistId, userId)
            Log.d(TAG, "checkPrice: response=$response")
            response.message
        }
    }

    private suspend fun <T> executeWithRetry(name: String, op: suspend () -> T): T {
        var last: Exception? = null
        repeat(MAX_RETRIES) { attempt ->
            try {
                Log.d(TAG, "$name: try ${attempt + 1}/$MAX_RETRIES")
                return op()
            } catch (e: SocketTimeoutException) {
                last = e
                Log.w(TAG, "$name: timeout: ${e.message}")
                if (attempt < MAX_RETRIES - 1) delay(RETRY_DELAY_MS * (attempt + 1))
            } catch (e: IOException) {
                last = e
                Log.w(TAG, "$name: io: ${e.message}")
                if (attempt < MAX_RETRIES - 1) delay(RETRY_DELAY_MS)
            } catch (e: HttpException) {
                Log.e(TAG, "$name: http ${e.code()} - ${e.message()}")
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "$name: unknown", e)
                throw e
            }
        }
        throw last ?: Exception("$name failed after $MAX_RETRIES retries")
    }
}

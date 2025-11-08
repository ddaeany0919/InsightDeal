package com.ddaeany0919.insightdeal.presentation.wishlist

import android.util.Log
import com.ddaeany0919.insightdeal.data.network.*
import com.ddaeany0919.insightdeal.presentation.wishlist.PriceCheckResponse
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

    suspend fun createWishlist(
        keyword: String, 
        productUrl: String, 
        targetPrice: Int, 
        userId: String
    ) = withContext(Dispatchers.IO) {
        Log.d(TAG, "createWishlist: keyword=$keyword url=$productUrl target=$targetPrice userId=$userId")
        
        executeWithRetry("createWishlist") {
            val isUrl = productUrl.isNotBlank() || keyword.startsWith("http://") || keyword.startsWith("https://")
            if (isUrl) {
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

    suspend fun checkPrice(wishlistId: Int, userId: String): PriceCheckResponse = withContext(Dispatchers.IO) {
        Log.d(TAG, "checkPrice: start id=$wishlistId userId=$userId")
        executeWithRetry("checkPrice") {
            val response = service().checkWishlistPrice(wishlistId, userId)
            Log.d(TAG, "checkPrice: response=$response")
            response
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

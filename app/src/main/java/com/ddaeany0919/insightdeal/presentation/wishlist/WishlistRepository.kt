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

    suspend fun createWishlist(keyword: String, productUrl: String, targetPrice: Int, userId: String) = withContext(Dispatchers.IO) {
        Log.d(TAG, "createWishlist: start keyword=$keyword url=$productUrl target=$targetPrice userId=$userId")
        executeWithRetry("createWishlist") {
            service().createWishlist(WishlistCreateRequest(keyword, productUrl, targetPrice, userId)).toWishlistItem()
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

    // Phase-out legacy link-only add: now handled by dialog and ViewModel
}

package com.ddaeany0919.insightdeal.presentation.wishlist

import android.content.Context
import android.util.Log
import com.ddaeany0919.insightdeal.network.ApiService
import com.ddaeany0919.insightdeal.data.network.*
import com.ddaeany0919.insightdeal.local.db.AppDatabase
import com.ddaeany0919.insightdeal.local.db.WishlistEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import retrofit2.HttpException
import java.io.IOException
import java.net.SocketTimeoutException
import java.time.LocalDateTime

fun WishlistEntity.toWishlistItem(): WishlistItem {
    return WishlistItem(
        id = this.id,
        keyword = this.keyword,
        productUrl = this.productUrl,
        targetPrice = this.targetPrice,
        currentLowestPrice = this.currentLowestPrice,
        currentLowestPlatform = this.currentLowestPlatform,
        currentLowestProductTitle = this.currentLowestProductTitle,
        priceDropPercentage = this.priceDropPercentage,
        isTargetReached = this.isTargetReached,
        isActive = this.isActive,
        alertEnabled = this.alertEnabled,
        createdAt = this.createdAt,
        updatedAt = this.updatedAt,
        lastChecked = this.lastChecked
    )
}

class WishlistRepository(
    context: Context,
    private val apiServiceProvider: suspend () -> ApiService = {
        com.ddaeany0919.insightdeal.network.NetworkModule.createService<ApiService>()
    }
) {
    private val TAG = "WishlistRepo"
    private val MAX_RETRIES = 3
    private val RETRY_DELAY_MS = 1000L

    private val dao = AppDatabase.getDatabase(context).wishlistDao()
    private var apiService: ApiService? = null

    private suspend fun service(): ApiService {
        if (apiService == null) {
            apiService = apiServiceProvider()
        }
        return apiService!!
    }

    // 로컬 DB를 구독(Flow)하여 UI단에 즉시 방출
    fun getWishlistFlow(): Flow<List<WishlistItem>> {
        return dao.getAllWishlistsFlow().map { entities ->
            entities.map { it.toWishlistItem() }
        }
    }

    suspend fun createWishlist(
        keyword: String, 
        productUrl: String, 
        targetPrice: Int
    ) = withContext(Dispatchers.IO) {
        Log.d(TAG, "createWishlist: Local db insert -> keyword=$keyword url=$productUrl target=$targetPrice")
        val entity = WishlistEntity(
            keyword = keyword,
            productUrl = productUrl,
            targetPrice = targetPrice
        )
        dao.insertWishlist(entity)
    }

    suspend fun updateWishlist(item: WishlistItem) = withContext(Dispatchers.IO) {
        val entity = WishlistEntity(
            id = item.id,
            keyword = item.keyword,
            productUrl = item.productUrl,
            targetPrice = item.targetPrice,
            currentLowestPrice = item.currentLowestPrice,
            currentLowestPlatform = item.currentLowestPlatform,
            currentLowestProductTitle = item.currentLowestProductTitle,
            priceDropPercentage = item.priceDropPercentage,
            isTargetReached = item.isTargetReached,
            isActive = item.isActive,
            alertEnabled = item.alertEnabled,
            createdAt = item.createdAt,
            updatedAt = LocalDateTime.now(),
            lastChecked = item.lastChecked
        )
        dao.updateWishlist(entity)
    }

    suspend fun deleteWishlist(wishlistId: Int): Boolean = withContext(Dispatchers.IO) {
        Log.d(TAG, "deleteWishlist: start id=$wishlistId")
        dao.deleteWishlistById(wishlistId)
        true
    }

    // 서버 API 호출 로직은 그대로 유지 (특정 가격 갱신 등에 활용)
    suspend fun checkPrice(wishlistId: Int, userId: String): PriceCheckResponse = withContext(Dispatchers.IO) {
        Log.d(TAG, "checkPrice: start id=$wishlistId userId=$userId")
        executeWithRetry("checkPrice") {
            service().checkWishlistPrice(wishlistId, userId)
        }
    }

    suspend fun getPriceHistory(userId: String): List<PriceHistoryItem> = withContext(Dispatchers.IO) {
        executeWithRetry("getPriceHistory") {
            try {
                val response = service().getPriceHistory(userId)
                if (response.isSuccessful && response.body() != null) {
                    response.body()!!.map { it.toPriceHistoryItem() }
                } else {
                    emptyList()
                }
            } catch (e: Exception) {
                emptyList()
            }
        }
    }

    suspend fun updateAlarmState(isEnabled: Boolean, userId: String): Boolean = withContext(Dispatchers.IO) {
        executeWithRetry("updateAlarmState") {
            try {
                service().updateAlarmState(UpdateAlarmRequest(userId = userId, isEnabled = isEnabled)).isSuccessful
            } catch (e: Exception) {
                false
            }
        }
    }

    private suspend fun <T> executeWithRetry(name: String, op: suspend () -> T): T {
        var last: Exception? = null
        repeat(MAX_RETRIES) { attempt ->
            try {
                return op()
            } catch (e: SocketTimeoutException) {
                last = e
                if (attempt < MAX_RETRIES - 1) delay(RETRY_DELAY_MS * (attempt + 1))
            } catch (e: IOException) {
                last = e
                if (attempt < MAX_RETRIES - 1) delay(RETRY_DELAY_MS)
            } catch (e: HttpException) {
                throw e
            } catch (e: Exception) {
                throw e
            }
        }
        throw last ?: Exception("$name failed after $MAX_RETRIES retries")
    }
}

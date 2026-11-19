package com.ddaeany0919.insightdeal.presentation.wishlist

import android.content.Context
import android.util.Log
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.map
import com.ddaeany0919.insightdeal.data.LocalDataProvider
import com.ddaeany0919.insightdeal.data.network.WishlistCreateFromKeywordRequest
import com.ddaeany0919.insightdeal.data.network.WishlistUpdateRequest
import com.ddaeany0919.insightdeal.local.db.AppDatabase
import com.ddaeany0919.insightdeal.local.db.WishlistEntity
import com.ddaeany0919.insightdeal.network.ApiService
import kotlinx.coroutines.CancellationException
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
        serverWishlistId = this.serverWishlistId,
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

    // Room DB와 Paging 3를 결합한 페이징 조회 스트림
    fun getWishlistPagedFlow(): Flow<PagingData<WishlistItem>> {
        return Pager(
            config = PagingConfig(
                pageSize = 20,
                enablePlaceholders = false,
                prefetchDistance = 5
            ),
            pagingSourceFactory = { dao.getAllWishlistsPaged() }
        ).flow.map { pagingData ->
            pagingData.map { entity -> entity.toWishlistItem() }
        }
    }

    suspend fun createWishlist(
        keyword: String,
        productUrl: String,
        targetPrice: Int,
        currentLowestPrice: Int? = null,
        currentLowestPlatform: String? = null,
        userId: String = LocalDataProvider.getDeviceUuid()
    ) = withContext(Dispatchers.IO) {
        val resolvedUserId = userId.ifBlank { LocalDataProvider.getDeviceUuid() }
        Log.d(TAG, "createWishlist: Local db insert -> keyword=$keyword url=$productUrl target=$targetPrice lowest=$currentLowestPrice")
        val entity = WishlistEntity(
            keyword = keyword,
            productUrl = productUrl,
            targetPrice = targetPrice,
            currentLowestPrice = currentLowestPrice,
            currentLowestPlatform = currentLowestPlatform
        )
        val localId = dao.insertWishlist(entity).toInt()

        // 백엔드 목표가 알림 테이블(wishlist)과 1-RTT 비동기 동기화 및 serverWishlistId 저장
        runCatching {
            service().createWishlistFromKeyword(
                WishlistCreateFromKeywordRequest(
                    keyword = keyword,
                    targetPrice = targetPrice,
                    userId = resolvedUserId
                )
            )
        }.onSuccess { created ->
            if (localId > 0 && created.id > 0) {
                dao.updateServerWishlistId(localId, created.id)
                Log.d(TAG, "서버 위시리스트 ID 매핑 저장: localId=$localId -> serverId=${created.id}")
            }
        }.onFailure { e ->
            if (e is CancellationException) throw e
            Log.w(TAG, "서버 목표가 알림 동기화 생략 (로컬 Room 저장 완료): ${e.message}")
        }
    }

    suspend fun updateWishlist(
        item: WishlistItem,
        userId: String = LocalDataProvider.getDeviceUuid()
    ) = withContext(Dispatchers.IO) {
        val resolvedUserId = userId.ifBlank { LocalDataProvider.getDeviceUuid() }
        val existing = dao.getWishlistById(item.id)
        val resolvedServerId = item.serverWishlistId ?: existing?.serverWishlistId

        val entity = WishlistEntity(
            id = item.id,
            serverWishlistId = resolvedServerId,
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

        val targetServerId = resolvedServerId ?: runCatching {
            service().getWishlist(resolvedUserId).firstOrNull { it.keyword == item.keyword }?.id
        }.onFailure { e ->
            if (e is CancellationException) throw e
        }.getOrNull()?.also { foundId ->
            dao.updateServerWishlistId(item.id, foundId)
        }

        if (targetServerId != null) {
            runCatching {
                service().updateWishlist(
                    wishlistId = targetServerId,
                    request = WishlistUpdateRequest(
                        targetPrice = item.targetPrice,
                        isActive = item.isActive,
                        alertEnabled = item.alertEnabled
                    ),
                    userId = resolvedUserId
                )
            }.onFailure { e ->
                if (e is CancellationException) throw e
                Log.w(TAG, "서버 위시리스트 PATCH 동기화 실패 (로컬 반영 완료): ${e.message}")
            }
        }
    }

    suspend fun deleteWishlist(
        wishlistId: Int,
        userId: String = LocalDataProvider.getDeviceUuid()
    ): Boolean = withContext(Dispatchers.IO) {
        val resolvedUserId = userId.ifBlank { LocalDataProvider.getDeviceUuid() }
        Log.d(TAG, "deleteWishlist: start id=$wishlistId")
        val target = dao.getWishlistById(wishlistId)
        dao.deleteWishlistById(wishlistId)

        val serverId = target?.serverWishlistId ?: runCatching {
            service().getWishlist(resolvedUserId).firstOrNull { it.keyword == target?.keyword }?.id
        }.onFailure { e ->
            if (e is CancellationException) throw e
        }.getOrNull()

        if (serverId != null) {
            runCatching {
                service().deleteWithQuery(serverId, resolvedUserId)
            }.onFailure { e ->
                if (e is CancellationException) throw e
                Log.w(TAG, "서버 위시리스트 DELETE 동기화 실패: ${e.message}")
            }
        }
        true
    }

    suspend fun checkPrice(wishlistId: Int, userId: String): PriceCheckResponse? = withContext(Dispatchers.IO) {
        val resolvedUserId = userId.ifBlank { LocalDataProvider.getDeviceUuid() }
        val localItem = dao.getWishlistById(wishlistId)
        val serverId = localItem?.serverWishlistId ?: run {
            val keyword = localItem?.keyword
            if (keyword.isNullOrBlank()) {
                null
            } else {
                runCatching {
                    service().getWishlist(resolvedUserId)
                        .firstOrNull { it.keyword == keyword }
                        ?.id
                }.onFailure { e ->
                    if (e is CancellationException) throw e
                }.getOrNull()?.also { foundId ->
                    dao.updateServerWishlistId(wishlistId, foundId)
                }
            }
        }
        if (serverId == null) {
            Log.w(TAG, "checkPrice: 서버 위시리스트 ID 미존재로 가격 확인 중단 (localId=$wishlistId)")
            return@withContext null
        }
        Log.d(TAG, "checkPrice: start localId=$wishlistId serverId=$serverId userId=$resolvedUserId")
        executeWithRetry("checkPrice") {
            service().checkWishlistPrice(serverId, resolvedUserId)
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
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                emptyList()
            }
        }
    }

    suspend fun updateAlarmState(isEnabled: Boolean, userId: String): Boolean = withContext(Dispatchers.IO) {
        executeWithRetry("updateAlarmState") {
            try {
                service().updateAlarmState(UpdateAlarmRequest(userId = userId, isEnabled = isEnabled)).isSuccessful
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                false
            }
        }
    }

    /**
     * 서버 관심 상품 목록(/api/wishlist/)을 조회하여 로컬 Room 테이블에 병합(하이드레이션)합니다.
     */
    suspend fun syncFromServer(userId: String = LocalDataProvider.getDeviceUuid()): Int = withContext(Dispatchers.IO) {
        val resolvedUserId = userId.ifBlank { LocalDataProvider.getDeviceUuid() }
        if (resolvedUserId.isBlank()) return@withContext 0

        val remoteItems = service().getWishlist(resolvedUserId)
        val localItems = dao.getAllWishlistsOnce()
        var mergedCount = 0

        for (remote in remoteItems) {
            val mapped = remote.toWishlistItem()
            val existing = localItems.firstOrNull { it.serverWishlistId == remote.id }
                ?: localItems.firstOrNull { it.keyword.trim().equals(remote.keyword.trim(), ignoreCase = true) }

            if (existing == null) {
                dao.insertWishlist(
                    WishlistEntity(
                        serverWishlistId = remote.id,
                        keyword = mapped.keyword,
                        productUrl = mapped.productUrl,
                        targetPrice = mapped.targetPrice,
                        currentLowestPrice = mapped.currentLowestPrice,
                        currentLowestPlatform = mapped.currentLowestPlatform,
                        currentLowestProductTitle = mapped.currentLowestProductTitle,
                        priceDropPercentage = mapped.priceDropPercentage,
                        isTargetReached = mapped.isTargetReached,
                        isActive = mapped.isActive,
                        alertEnabled = mapped.alertEnabled,
                        createdAt = mapped.createdAt,
                        updatedAt = mapped.updatedAt,
                        lastChecked = mapped.lastChecked
                    )
                )
                mergedCount++
            } else {
                dao.updateWishlist(
                    existing.copy(
                        serverWishlistId = remote.id,
                        productUrl = existing.productUrl.ifBlank { mapped.productUrl },
                        targetPrice = mapped.targetPrice,
                        currentLowestPrice = mapped.currentLowestPrice ?: existing.currentLowestPrice,
                        currentLowestPlatform = mapped.currentLowestPlatform ?: existing.currentLowestPlatform,
                        currentLowestProductTitle = mapped.currentLowestProductTitle ?: existing.currentLowestProductTitle,
                        priceDropPercentage = mapped.priceDropPercentage,
                        isTargetReached = mapped.isTargetReached,
                        isActive = mapped.isActive,
                        alertEnabled = mapped.alertEnabled,
                        updatedAt = mapped.updatedAt,
                        lastChecked = mapped.lastChecked ?: existing.lastChecked
                    )
                )
            }
        }
        mergedCount
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

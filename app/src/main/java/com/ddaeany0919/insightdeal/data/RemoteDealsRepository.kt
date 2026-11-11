package com.ddaeany0919.insightdeal.data

import android.util.Log
import com.ddaeany0919.insightdeal.models.ApiDeal
import com.ddaeany0919.insightdeal.models.DealItem
import com.ddaeany0919.insightdeal.network.ApiService
import com.ddaeany0919.insightdeal.network.HealthResponse
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap
import kotlin.system.measureTimeMillis

/**
 * 실제 커뮤니티 핫딜 API를 사용하는 Repository 구현 (외부 가격비교 API 배제 원칙 준수)
 */
class RemoteDealsRepository(
    private val apiService: ApiService
) : DealsRepository {

    companion object {
        private const val TAG = "RemoteDealsRepository"
        private const val CACHE_TTL_MINUTES = 5
        private const val POPULAR_DEALS_CACHE_KEY = "popular_deals"
    }

    private val cache = ConcurrentHashMap<String, CacheEntry<*>>()
    private val cacheStats = CacheStatsImpl()

    override fun searchDeal(
        query: String,
        forceRefresh: Boolean
    ): Flow<Resource<List<DealItem>>> = flow<Resource<List<DealItem>>> {
        val cacheKey = "search_$query"
        if (!forceRefresh) {
            val cachedData = getCachedData<List<DealItem>>(cacheKey)
            if (cachedData != null) {
                cacheStats.recordHit()
                emit(Resource.Success(cachedData))
                return@flow
            }
        }

        emit(Resource.Loading<List<DealItem>>())

        try {
            val responseTime = measureTimeMillis {
                val response = apiService.getCommunityHotDeals(
                    limit = 20,
                    offset = 0,
                    keyword = query
                )
                if (response.isSuccessful) {
                    val deals: List<DealItem> = response.body()?.deals ?: emptyList()
                    setCachedData(cacheKey, deals)
                    emit(Resource.Success(deals))
                } else {
                    emit(Resource.Error<List<DealItem>>("서버 오류가 발생했습니다 (${response.code()})"))
                }
            }
            cacheStats.recordMiss(responseTime)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "API 호출 예외: query='$query'", e)
            val cachedData = getCachedData<List<DealItem>>(cacheKey, ignoreExpiry = true)
            if (cachedData != null) {
                emit(Resource.Error("오프라인 모드 - 이전 데이터", cachedData, e))
            } else {
                emit(Resource.Error<List<DealItem>>("네트워크 연결을 확인해주세요", null, e))
            }
        }
    }.flowOn(Dispatchers.IO)

    override fun getPopularDeals(
        popularQueries: List<String>,
        maxResults: Int
    ): Flow<Resource<List<ApiDeal>>> = flow<Resource<List<ApiDeal>>> {
        val cachedDeals = getCachedData<List<ApiDeal>>(POPULAR_DEALS_CACHE_KEY)
        if (!cachedDeals.isNullOrEmpty()) {
            emit(Resource.Success(cachedDeals.take(maxResults)))
            return@flow
        }

        emit(Resource.Loading<List<ApiDeal>>())
        try {
            val response = apiService.getCommunityHotDeals(
                limit = maxResults,
                offset = 0,
                sort = "popular"
            )
            if (response.isSuccessful) {
                val deals = response.body()?.deals.orEmpty().map { ApiDeal.fromDealItem(it) }
                if (deals.isNotEmpty()) {
                    setCachedData(POPULAR_DEALS_CACHE_KEY, deals)
                    emit(Resource.Success(deals))
                } else {
                    emit(Resource.Error<List<ApiDeal>>("인기 딜을 불러올 수 없습니다", null))
                }
            } else {
                emit(Resource.Error<List<ApiDeal>>("서버 오류가 발생했습니다 (${response.code()})", null))
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Popular deals 예외 발생", e)
            emit(Resource.Error<List<ApiDeal>>("네트워크 오류가 발생했습니다", null, e))
        }
    }.flowOn(Dispatchers.IO)

    override fun checkServerHealth(): Flow<Resource<HealthResponse>> = flow<Resource<HealthResponse>> {
        try {
            val response = apiService.healthCheck()
            if (response.isSuccessful) {
                val healthData = response.body()
                if (healthData != null) {
                    emit(Resource.Success(healthData))
                } else {
                    emit(Resource.Error<HealthResponse>("서버 상태 확인 실패", null))
                }
            } else {
                emit(Resource.Error<HealthResponse>("서버 상태 불량 (${response.code()})", null))
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            emit(Resource.Error<HealthResponse>("서버 연결 단절", null, e))
        }
    }.flowOn(Dispatchers.IO)

    override suspend fun clearCache(olderThanMinutes: Int): Unit = withContext(Dispatchers.IO) {
        val cutoffTime = System.currentTimeMillis() - (olderThanMinutes * 60 * 1000)
        cache.entries.removeIf { (_, entry) -> entry.timestamp < cutoffTime }
    }

    override fun getCacheStats(): CacheStats = cacheStats.getStats()

    private fun <T> getCachedData(key: String, ignoreExpiry: Boolean = false): T? {
        @Suppress("UNCHECKED_CAST")
        val entry = cache[key] as? CacheEntry<T> ?: return null
        val isExpired = System.currentTimeMillis() - entry.timestamp > (CACHE_TTL_MINUTES * 60 * 1000)
        if (!ignoreExpiry && isExpired) {
            cache.remove(key)
            return null
        }
        return entry.data
    }

    private fun <T> setCachedData(key: String, data: T) {
        cache[key] = CacheEntry(data, System.currentTimeMillis())
    }

    private data class CacheEntry<T>(
        val data: T,
        val timestamp: Long
    )

    private inner class CacheStatsImpl {
        private var hitCount = 0
        private var missCount = 0
        private var totalResponseTime = 0L

        fun recordHit() {
            hitCount++
        }

        fun recordMiss(responseTime: Long) {
            missCount++
            totalResponseTime += responseTime
        }

        fun getStats(): CacheStats {
            val totalRequests = hitCount + missCount
            val hitRate = if (totalRequests > 0) hitCount.toFloat() / totalRequests else 0f
            val avgResponseTime = if (missCount > 0) totalResponseTime / missCount else 0L
            return CacheStats(
                totalEntries = cache.size,
                hitRate = hitRate,
                averageResponseTime = avgResponseTime,
                oldestEntryMinutes = 0,
                newestEntryMinutes = 0
            )
        }
    }
}

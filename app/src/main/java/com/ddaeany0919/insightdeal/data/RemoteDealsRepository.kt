package com.ddaeany0919.insightdeal.data

import android.util.Log
import com.ddaeany0919.insightdeal.models.ApiDeal
import com.ddaeany0919.insightdeal.models.ComparisonResponse
import com.ddaeany0919.insightdeal.network.DealsApiService
import com.ddaeany0919.insightdeal.network.HealthResponse
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap
import kotlin.system.measureTimeMillis

/**
 * 🌐 실제 백엔드 API를 사용하는 Repository 구현
 */
class RemoteDealsRepository(
    private val apiService: DealsApiService
) : DealsRepository {

    companion object {
        private const val TAG = "RemoteDealsRepository"
        private const val CACHE_TTL_MINUTES = 5
        private const val POPULAR_DEALS_CACHE_KEY = "popular_deals"
    }

    // 💾 인메모리 캐시
    private val cache = ConcurrentHashMap<String, CacheEntry<*>>()
    private val cacheStats = CacheStatsImpl()

    /**
     * 🔍 단일 상품 가격 비교
     */
    override fun searchDeal(
        query: String,
        forceRefresh: Boolean
    ): Flow<Resource<ComparisonResponse>> = flow {
        val cacheKey = "search_$query"
        Log.d(TAG, "searchDeal 시작: query='$query', forceRefresh=$forceRefresh")

        // 캐시 확인
        if (!forceRefresh) {
            // 라인 40 오류 수정: getCachedData에 타입 인자 명시
            val cachedData = getCachedData<ComparisonResponse>(cacheKey)
            if (cachedData != null) {
                Log.d(TAG, "Cache hit for query: $query")
                cacheStats.recordHit()
                emit(Resource.Success(cachedData))
                return@flow
            }
        }

        // 로딩 상태
        emit(Resource.Loading())

        // API 호출
        try {
            val responseTime = measureTimeMillis {
                val response = apiService.comparePrice(query)
                if (response.isSuccessful) {
                    val comparisonResult = response.body()
                    if (comparisonResult != null) {
                        // 캐시 저장
                        setCachedData(cacheKey, comparisonResult)
                        Log.d(TAG, "API 성공: query='$query'")
                        emit(Resource.Success(comparisonResult))
                    } else {
                        // 라인 56 오류 수정: Resource.Error에 타입 인자 명시
                        emit(Resource.Error<ComparisonResponse>("상품 정보를 찾을 수 없습니다"))
                    }
                } else {
                    // 라인 56 오류 수정: Resource.Error에 타입 인자 명시
                    emit(Resource.Error<ComparisonResponse>("서버 오류가 발생했습니다 (${response.code()})"))
                }
            }

            // 블록 밖에서 responseTime 사용
            cacheStats.recordMiss(responseTime)
            Log.d(TAG, "API 호출 완료: query='$query', responseTime=${responseTime}ms")
        } catch (e: Exception) {
            Log.e(TAG, "API 호출 예외: query='$query'", e)
            // 캐시된 데이터 확인
            // 라인 71 오류 수정: getCachedData에 타입 인자 명시
            val cachedData = getCachedData<ComparisonResponse>(cacheKey, ignoreExpiry = true)
            if (cachedData != null) {
                Log.d(TAG, "Network error, 이전 캐시 데이터 사용: query='$query'")
                emit(Resource.Error("오프라인 모드 - 이전 데이터", cachedData, e))
            } else {
                // 라인 75 오류 수정: data 인자에 null 명시적으로 전달 (타입 추론 도움)
                emit(Resource.Error<ComparisonResponse>("네트워크 연결을 확인해주세요", null, e))
            }
        }
    }.flowOn(Dispatchers.IO)

    /**
     * 📱 홈 화면용 인기 딜 목록
     */
    override fun getPopularDeals(
        popularQueries: List<String>,
        maxResults: Int
    ): Flow<Resource<List<ApiDeal>>> = flow {
        Log.d(TAG, "getPopularDeals 시작: queries=${popularQueries.size}")

        // 캐시 확인
        val cachedDeals = getCachedData<List<ApiDeal>>(POPULAR_DEALS_CACHE_KEY)
        if (cachedDeals != null && cachedDeals.isNotEmpty()) {
            Log.d(TAG, "Popular deals cache hit: ${cachedDeals.size}개")
            emit(Resource.Success(cachedDeals.take(maxResults)))
            return@flow
        }

        emit(Resource.Loading())
        val deals = mutableListOf<ApiDeal>()

        try {
            for (query in popularQueries.take(maxResults)) {
                try {
                    val response = apiService.comparePrice(query)
                    if (response.isSuccessful) {
                        response.body()?.let { comparisonResponse ->
                            if (comparisonResponse.successCount >= 2) {
                                deals.add(ApiDeal.fromComparisonResponse(comparisonResponse))
                            }
                        }
                    }
                    delay(100) // 서버 부하 방지
                } catch (e: Exception) {
                    Log.w(TAG, "Popular deal API error for query: $query", e)
                }
            }

            if (deals.isNotEmpty()) {
                setCachedData(POPULAR_DEALS_CACHE_KEY, deals)
                Log.d(TAG, "Popular deals 성공: ${deals.size}개 획득")
                emit(Resource.Success(deals))
            } else {
                // data 인자에 null을 명시적으로 전달 (타입 추론 도움)
                emit(Resource.Error<List<ApiDeal>>("인기 딜을 불러올 수 없습니다", null))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Popular deals 예외 발생", e)
            // data 인자에 null을 명시적으로 전달 (타입 추론 도움)
            emit(Resource.Error<List<ApiDeal>>("네트워크 오류가 발생했습니다", null, e))
        }
    }.flowOn(Dispatchers.IO)

    /**
     * ⚡ 서버 상태 확인
     */
    override fun checkServerHealth(): Flow<Resource<HealthResponse>> = flow {
        try {
            Log.d(TAG, "Server health check 시작")
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
        } catch (e: Exception) {
            Log.e(TAG, "Health check 예외", e)
            emit(Resource.Error<HealthResponse>("서버 연결 단절", null, e))
        }
    }.flowOn(Dispatchers.IO)

    /**
     * 캐시 정리 (Unit 반환타입 명시)
     */
    override suspend fun clearCache(olderThanMinutes: Int): Unit = withContext(Dispatchers.IO) {
        val cutoffTime = System.currentTimeMillis() - (olderThanMinutes * 60 * 1000)
        val initialSize = cache.size
        cache.entries.removeIf { (_, entry) ->
            entry.timestamp < cutoffTime
        }
        val removedCount = initialSize - cache.size
        Log.d(TAG, "Cache 정리 완료: ${removedCount}개 제거")
    }

    override fun getCacheStats(): CacheStats {
        return cacheStats.getStats()
    }

    // 캐시 유틸리티
    private fun <T> getCachedData(
        key: String,
        ignoreExpiry: Boolean = false
    ): T? {
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

    /**
     * 캐시 엔트리
     */
    private data class CacheEntry<T>(
        val data: T,
        val timestamp: Long
    )

    /**
     * 캐시 통계 구현
     */
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
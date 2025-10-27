package com.ddaeany0919.insightdeal.data

import android.util.Log
import com.ddaeany0919.insightdeal.models.ApiDeal
import com.ddaeany0919.insightdeal.models.ComparisonResponse
import com.ddaeany0919.insightdeal.network.DealsApiService
import com.ddaeany0919.insightdeal.network.DealsRetrofitClient
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
 * 
 * 사용자 중심 설계:
 * - 홈 로딩 1초 내 목표: 캐시 우선 + 백그라운드 업데이트
 * - 네트워크 오류 시 이전 데이터로 사용성 보장
 * - 5분 TTL 캐시로 반복 요청 최적화
 */
class RemoteDealsRepository(
    private val apiService: DealsApiService = DealsRetrofitClient.dealsApiService
) : DealsRepository {
    
    companion object {
        private const val TAG = "RemoteDealsRepository"
        private const val CACHE_TTL_MINUTES = 5 // 캐시 유효시간
        private const val POPULAR_DEALS_CACHE_KEY = "popular_deals"
    }
    
    // 💾 인메모리 캐시 (빠른 접근을 위해)
    private val cache = ConcurrentHashMap<String, CacheEntry<*>>()
    private val cacheStats = CacheStatsImpl()
    
    /**
     * 🔍 단일 상품 가격 비교 - 핵심 기능!
     */
    override fun searchDeal(
        query: String,
        forceRefresh: Boolean
    ): Flow<Resource<ComparisonResponse>> = flow {
        val cacheKey = "search_$query"
        
        Log.d(TAG, "searchDeal 시작: query='$query', forceRefresh=$forceRefresh")
        
        // 1번 단계: 캐시 확인 (빠른 사용자 경험)
        if (!forceRefresh) {
            val cachedData = getCachedData<ComparisonResponse>(cacheKey)
            if (cachedData != null) {
                Log.d(TAG, "Cache hit for query: $query")
                cacheStats.recordHit()
                emit(Resource.Success(cachedData))
                return@flow
            }
        }
        
        // 2번 단계: 로딩 상태 알림
        emit(Resource.Loading())
        
        // 3번 단계: API 호출
        try {
            val responseTime = measureTimeMillis {
                val response = apiService.comparePrice(query)
                
                if (response.isSuccessful) {
                    val comparisonResult = response.body()
                    if (comparisonResult != null) {
                        // 성공: 캐시 저장 후 반환
                        setCachedData(cacheKey, comparisonResult)
                        cacheStats.recordMiss(responseTime)
                        
                        Log.d(TAG, "API 성공: query='$query', responseTime=${responseTime}ms, successCount=${comparisonResult.successCount}")
                        emit(Resource.Success(comparisonResult))
                    } else {
                        Log.w(TAG, "API 응답 바디 null: query='$query'")
                        emit(Resource.Error("상품 정보를 찾을 수 없습니다"))
                    }
                } else {
                    Log.w(TAG, "API 오류 응답: query='$query', code=${response.code()}")
                    emit(Resource.Error("서버 오류가 발생했습니다 (${response.code()})"))
                }
            }
            
            Log.d(TAG, "API 호출 완료: query='$query', responseTime=${responseTime}ms")
            
        } catch (e: Exception) {
            Log.e(TAG, "API 호출 예외: query='$query'", e)
            
            // 네트워크 오류 시 캐시된 데이터 확인
            val cachedData = getCachedData<ComparisonResponse>(cacheKey, ignoreExpiry = true)
            if (cachedData != null) {
                Log.d(TAG, "Network error, 이전 캐시 데이터 사용: query='$query'")
                emit(Resource.Error("오프라인 모드 - 이전 데이터", cachedData, e))
            } else {
                emit(Resource.Error("네트워크 연결을 확인해주세요", throwable = e))
            }
        }
    }.flowOn(Dispatchers.IO)
    
    /**
     * 📱 홈 화면용 인기 딜 목록
     * 사용자가 앱을 열었을 때 즉시 볼 수 있는 콘텐츠
     */
    override fun getPopularDeals(
        popularQueries: List<String>,
        maxResults: Int
    ): Flow<Resource<List<ApiDeal>>> = flow {
        Log.d(TAG, "getPopularDeals 시작: queries=${popularQueries.size}, maxResults=$maxResults")
        
        // 1번 단계: 캐시 확인
        val cachedDeals = getCachedData<List<ApiDeal>>(POPULAR_DEALS_CACHE_KEY)
        if (cachedDeals != null && cachedDeals.isNotEmpty()) {
            Log.d(TAG, "Popular deals cache hit: ${cachedDeals.size}개")
            cacheStats.recordHit()
            emit(Resource.Success(cachedDeals.take(maxResults)))
            return@flow
        }
        
        // 2번 단계: 로딩 상태
        emit(Resource.Loading())
        
        // 3번 단계: 인기 검색어로 다중 API 호출
        val deals = mutableListOf<ApiDeal>()
        val errors = mutableListOf<String>()
        
        try {
            val limitedQueries = popularQueries.take(maxResults) // 성능 제한
            
            for (query in limitedQueries) {
                try {
                    val response = apiService.comparePrice(query)
                    if (response.isSuccessful) {
                        response.body()?.let { comparisonResponse ->
                            if (comparisonResponse.successCount >= 2) { // 신뢰도 필터
                                deals.add(ApiDeal.fromComparisonResponse(comparisonResponse))
                            }
                        }
                    } else {
                        errors.add("$query 검색 실패")
                    }
                    
                    // API 요청 간 짧은 딸레이 (서버 부하 고려)
                    delay(100)
                    
                } catch (e: Exception) {
                    Log.w(TAG, "Popular deal API error for query: $query", e)
                    errors.add("$query 오류")
                }
            }
            
            if (deals.isNotEmpty()) {
                // 성공: 캐시 저장
                setCachedData(POPULAR_DEALS_CACHE_KEY, deals)
                Log.d(TAG, "Popular deals 성공: ${deals.size}개 획득")
                emit(Resource.Success(deals))
            } else {
                Log.w(TAG, "Popular deals 실패: 얻은 딜 0개, 에러 ${errors.size}개")
                emit(Resource.Error("인기 딜을 불러올 수 없습니다"))
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Popular deals 예외 발생", e)
            emit(Resource.Error("네트워크 오류가 발생했습니다", throwable = e))
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
                    Log.d(TAG, "Health check 성공: status=${healthData.status}")
                    emit(Resource.Success(healthData))
                } else {
                    emit(Resource.Error("서버 상태 확인 실패"))
                }
            } else {
                Log.w(TAG, "Health check 실패: code=${response.code()}")
                emit(Resource.Error("서버 상태 불량 (${response.code()})"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Health check 예외", e)
            emit(Resource.Error("서버 연결 단절", throwable = e))
        }
    }.flowOn(Dispatchers.IO)
    
    /**
     * 🧹 캐시 정리
     */
    override suspend fun clearCache(olderThanMinutes: Int) = withContext(Dispatchers.IO) {
        val cutoffTime = System.currentTimeMillis() - (olderThanMinutes * 60 * 1000)
        val initialSize = cache.size
        
        cache.entries.removeIf { (_, entry) ->
            entry.timestamp < cutoffTime
        }
        
        val removedCount = initialSize - cache.size
        Log.d(TAG, "Cache 정리 완료: ${removedCount}개 제거, 남은 개수: ${cache.size}")
    }
    
    /**
     * 📊 캐시 통계
     */
    override fun getCacheStats(): CacheStats {
        return cacheStats.getStats()
    }
    
    // === 비공개 캐시 유틸리티 메서드들 ===
    
    private fun <T> getCachedData(
        key: String, 
        ignoreExpiry: Boolean = false
    ): T? {
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
     * 💾 캐시 엔트리
     */
    private data class CacheEntry<T>(
        val data: T,
        val timestamp: Long
    )
    
    /**
     * 📊 캐시 통계 구현
     */
    private class CacheStatsImpl {
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
                oldestEntryMinutes = getOldestEntryAge(),
                newestEntryMinutes = getNewestEntryAge()
            )
        }
        
        private fun getOldestEntryAge(): Int {
            val oldestTimestamp = cache.values.minOfOrNull { it.timestamp } ?: System.currentTimeMillis()
            return ((System.currentTimeMillis() - oldestTimestamp) / (60 * 1000)).toInt()
        }
        
        private fun getNewestEntryAge(): Int {
            val newestTimestamp = cache.values.maxOfOrNull { it.timestamp } ?: System.currentTimeMillis()
            return ((System.currentTimeMillis() - newestTimestamp) / (60 * 1000)).toInt()
        }
    }
}
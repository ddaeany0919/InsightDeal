package com.ddaeany0919.insightdeal.data

import android.util.Log
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query
import java.util.concurrent.ConcurrentHashMap

/**
 * 📈 가격 히스토리 데이터 모델
 */
data class PricePoint(
    val date: String, // ISO 8601 형식
    val price: Int,
    val platform: String,
    val isAvailable: Boolean = true
)

data class PriceHistory(
    val productName: String,
    val periodDays: Int,
    val dataPoints: List<PricePoint>,
    val platforms: List<String>,
    val lowestEver: Int,
    val highestEver: Int,
    val currentTrend: String, // "up", "down", "stable"
    val lastUpdated: String,
    val traceId: String
)

/**
 * 🌐 가격 히스토리 API 인터페이스
 */
interface PriceHistoryApi {
    @GET("/api/product/{productId}/history")
    suspend fun getPriceHistory(
        @Path("productId") productId: Int
    ): List<ApiPricePoint>

    @POST("/api/product/track")
    suspend fun trackProduct(
        @Query("product_id") productId: String,
        @Query("target_price") targetPrice: Int,
        @Query("user_id") userId: String
    ): Response<Unit>
}

/**
 * 💬 가격 히스토리 API 응답
 */
data class ApiHistoryResponse(
    val product_name: String,
    val period_days: Int,
    val data_points: List<ApiPricePoint>,
    val platforms: List<String>,
    val lowest_ever: Int,
    val highest_ever: Int,
    val current_trend: String,
    val last_updated: String,
    val trace_id: String
)

data class ApiPricePoint(
    val date: String,
    val price: Int,
    val platform: String,
    val is_available: Boolean
)

/**
 * 🔄 가격 히스토리 로딩 상태
 */
sealed class HistoryState {
    object Idle : HistoryState()
    object Loading : HistoryState()
    data class Success(val history: PriceHistory) : HistoryState()
    data class Error(val message: String, val canRetry: Boolean = true) : HistoryState()
    data class Cached(val history: PriceHistory, val isStale: Boolean = false) : HistoryState()
}

/**
 * 📈 90일 가격 히스토리 리포지토리
 */
class PriceHistoryRepository {
    companion object {
        private const val TAG = "PriceHistory"
        private const val BASE_URL = "http://192.168.0.36:8000/"
        private const val CACHE_DURATION_MS = 300_000L // 5분 캐시
        private const val REQUEST_TIMEOUT_MS = 2000L // 2초 타임아웃
    }

    private val api: PriceHistoryApi
    private val historyCache = ConcurrentHashMap<String, Pair<PriceHistory, Long>>()
    private val activeJobs = ConcurrentHashMap<String, Job>()

    // UI 상태 관리
    private val _historyState = MutableStateFlow<HistoryState>(HistoryState.Idle)
    val historyState: StateFlow<HistoryState> = _historyState.asStateFlow()

    init {
        // 사용자 응답속도 최우선 Retrofit 설정
        val retrofit = Retrofit.Builder()
            .baseUrl(BASE_URL)
            .addConverterFactory(GsonConverterFactory.create())
            .build()

        api = retrofit.create(PriceHistoryApi::class.java)
        Log.i(TAG, "📈 PriceHistoryRepository 초기화 - 90일 히스토리 준비 완료")
    }

    /**
     * 📈 가격 히스토리 조회
     */
    suspend fun getPriceHistory(
        productName: String,
        productId: Int, // Added productId
        periodDays: Int = 30,
        forceRefresh: Boolean = false
    ): PriceHistory? {
        val cacheKey = "${productId}_${periodDays}"
        
        // 캐시 확인
        if (!forceRefresh) {
            getCachedHistory(cacheKey)?.let { cached ->
                _historyState.value = HistoryState.Cached(cached)
                return cached
            }
        }

        // 로딩 상태 알림
        _historyState.value = HistoryState.Loading
        System.currentTimeMillis()

        val job = CoroutineScope(Dispatchers.IO).launch {
            try {
                val response = api.getPriceHistory(productId)
                
                // API 응답이 List<ApiPricePoint>이므로 직접 처리
                if (response.isNotEmpty()) {
                    val history = mapToHistory(productName, response)
                    historyCache[cacheKey] = history to System.currentTimeMillis()
                    _historyState.value = HistoryState.Success(history)
                } else {
                    _historyState.value = HistoryState.Error("데이터가 없습니다")
                }
            } catch (e: Exception) {
                Log.e(TAG, "❌ 히스토리 조회 실패", e)
                _historyState.value = HistoryState.Error("네트워크 오류")
            } finally {
                activeJobs.remove(cacheKey)
            }
        }
        
        activeJobs[cacheKey] = job
        job.join()
        return getCachedHistory(cacheKey)
    }

    suspend fun trackProduct(productId: String, targetPrice: Int, userId: String): Boolean {
        return try {
            val response = api.trackProduct(productId, targetPrice, userId)
            response.isSuccessful
        } catch (e: Exception) {
            Log.e(TAG, "❌ 추적 등록 실패", e)
            false
        }
    }

    /**
     * 💨 캐시된 히스토리 반환
     */
    private fun getCachedHistory(cacheKey: String): PriceHistory? {
        val cached = historyCache[cacheKey] ?: return null
        val (history, timestamp) = cached
        return if (System.currentTimeMillis() - timestamp < CACHE_DURATION_MS) {
            history
        } else {
            historyCache.remove(cacheKey) // 만료된 캐시 제거
            null
        }
    }

    /**
     * 🔄 API 응답을 앱 내부 모델로 매핑
     */
    private fun mapToHistory(productName: String, apiPoints: List<ApiPricePoint>): PriceHistory {
        val dataPoints = apiPoints.map { point ->
            PricePoint(
                date = point.date,
                price = point.price,
                platform = point.platform,
                isAvailable = point.is_available
            )
        }
        
        val prices = dataPoints.map { it.price }
        val lowest = prices.minOrNull() ?: 0
        val highest = prices.maxOrNull() ?: 0
        
        // 간단한 트렌드 분석
        val trend = if (prices.size >= 2) {
            val last = prices.first()
            val prev = prices[1]
            if (last < prev) "down" else if (last > prev) "up" else "stable"
        } else "stable"

        return PriceHistory(
            productName = productName,
            periodDays = 30, // Default
            dataPoints = dataPoints,
            platforms = dataPoints.map { it.platform }.distinct(),
            lowestEver = lowest,
            highestEver = highest,
            currentTrend = trend,
            lastUpdated = dataPoints.firstOrNull()?.date ?: "",
            traceId = ""
        )
    }

    /**
     * 🧹 캐시 정리
     */
    fun clearExpiredCache() {
        val now = System.currentTimeMillis()
        val expired = historyCache.filter { (_, timestampPair) ->
            now - timestampPair.second >= CACHE_DURATION_MS
        }

        expired.keys.forEach { historyCache.remove(it) }
        if (expired.isNotEmpty()) {
            Log.d(TAG, "🧹 만료된 히스토리 캐시 ${expired.size}개 정리")
        }
    }

    fun clearAllCache() {
        val size = historyCache.size
        historyCache.clear()
        Log.d(TAG, "🧹 전체 히스토리 캐시 ${size}개 청소")
    }

    fun getCacheStats(): Pair<Int, Int> {
        val total = historyCache.size
        val expired = historyCache.values.count { (_, timestamp) ->
            System.currentTimeMillis() - timestamp >= CACHE_DURATION_MS
        }
        return total to (total - expired)
    }
}

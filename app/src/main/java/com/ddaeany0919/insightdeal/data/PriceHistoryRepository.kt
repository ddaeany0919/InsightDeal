package com.ddaeany0919.insightdeal.data

import android.util.Log
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
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
    @GET("/api/history")
    suspend fun getPriceHistory(
        @Query("product") product: String,
        @Query("period") period: Int = 30,
        @Query("platform") platform: String? = null
    ): Response<ApiHistoryResponse>
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
        private const val BASE_URL = "http://10.0.2.2:8000/"
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
        periodDays: Int = 30,
        platform: String? = null,
        forceRefresh: Boolean = false
    ): PriceHistory? {
        val cacheKey = "${productName}_${periodDays}_${platform ?: "all"}"
        val cleanProductName = productName.trim().take(50)

        if (cleanProductName.isBlank()) {
            Log.w(TAG, "⚠️ 빈 상품명 - 히스토리 조회 스킵")
            return null
        }

        // 캐시 확인
        if (!forceRefresh) {
            getCachedHistory(cacheKey)?.let { cached ->
                Log.d(TAG, "💨 히스토리 캐시 히트: $cleanProductName (${periodDays}일)")
                _historyState.value = HistoryState.Cached(cached)
                return cached
            }
        }

        // 중복 요청 방지
        activeJobs[cacheKey]?.let { job ->
            if (job.isActive) {
                Log.d(TAG, "🔄 진행 중인 히스토리 요청 대기: $cleanProductName")
                job.join()
                return getCachedHistory(cacheKey)
            }
        }

        // 로딩 상태 알림
        _historyState.value = HistoryState.Loading
        val startTime = System.currentTimeMillis()
        Log.i(TAG, "📈 ${periodDays}일 가격 히스토리 조회: $cleanProductName")

        val job = CoroutineScope(Dispatchers.IO).launch {
            try {
                // 2초 내 응답 또는 타임아웃
                val response = withTimeoutOrNull(REQUEST_TIMEOUT_MS) {
                    api.getPriceHistory(
                        product = cleanProductName,
                        period = periodDays.coerceIn(7, 90),
                        platform = platform
                    )
                }

                val elapsedMs = System.currentTimeMillis() - startTime

                if (response == null) {
                    Log.w(TAG, "⏱️ 히스토리 요청 타임아웃 ($elapsedMs ms): $cleanProductName")
                    _historyState.value = HistoryState.Error(
                        "네트워크 연결이 느립니다. 다시 시도해주세요.",
                        canRetry = true
                    )
                    return@launch
                }

                when {
                    response.isSuccessful -> {
                        response.body()?.let { apiResponse ->
                            val history = mapToHistory(apiResponse)
                            // 캐시 저장
                            historyCache[cacheKey] = history to System.currentTimeMillis()

                            val isFastResponse = elapsedMs <= 1000
                            Log.i(TAG, "✅ 히스토리 조회 성공 ($elapsedMs ms, fast: $isFastResponse): " +
                                    "${history.dataPoints.size}개 데이터 포인트, " +
                                    "최저가 ${history.lowestEver}원, 트렌드 ${history.currentTrend}")
                            _historyState.value = HistoryState.Success(history)
                        } ?: run {
                            Log.e(TAG, "❌ API 응답 바디 비어있음: $cleanProductName")
                            _historyState.value = HistoryState.Error("데이터를 불러올 수 없습니다")
                        }
                    }
                    response.code() == 404 -> {
                        Log.w(TAG, "📋 가격 히스토리 데이터 없음: $cleanProductName")
                        _historyState.value = HistoryState.Error(
                            "아직 가격 데이터가 수집되지 않았습니다. 잠시 후 다시 시도해주세요.",
                            canRetry = true
                        )
                    }
                    else -> {
                        Log.e(TAG, "❌ API 요청 실패 ${response.code()}: $cleanProductName")
                        _historyState.value = HistoryState.Error(
                            "서버 오류가 발생했습니다. 잠시 후 다시 시도해주세요.",
                            canRetry = response.code() != 400
                        )
                    }
                }
            } catch (e: Exception) {
                val elapsedMs = System.currentTimeMillis() - startTime
                Log.e(TAG, "❌ 히스토리 조회 예외 ($elapsedMs ms): $cleanProductName", e)
                _historyState.value = HistoryState.Error(
                    "네트워크 오류가 발생했습니다. 다시 시도해주세요.",
                    canRetry = true
                )
            } finally {
                activeJobs.remove(cacheKey)
            }
        }

        activeJobs[cacheKey] = job
        job.join()
        return getCachedHistory(cacheKey)
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
    private fun mapToHistory(apiResponse: ApiHistoryResponse): PriceHistory {
        val dataPoints = apiResponse.data_points.map { point ->
            PricePoint(
                date = point.date,
                price = point.price,
                platform = point.platform,
                isAvailable = point.is_available
            )
        }

        return PriceHistory(
            productName = apiResponse.product_name,
            periodDays = apiResponse.period_days,
            dataPoints = dataPoints,
            platforms = apiResponse.platforms,
            lowestEver = apiResponse.lowest_ever,
            highestEver = apiResponse.highest_ever,
            currentTrend = apiResponse.current_trend,
            lastUpdated = apiResponse.last_updated,
            traceId = apiResponse.trace_id
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

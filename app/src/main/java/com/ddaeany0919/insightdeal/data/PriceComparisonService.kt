"""
📱 PriceComparisonService - 4몰 가격 비교 API 클라이언트

사용자 중심 설계:
- 사용자는 딩 카드를 보는 순간 "최저가 어디지?" 궁금해함
- 2초 내에 4몰 가격을 보여주지 못하면 기다리지 않음
- 실패해도 사용자 경험 방해 없이 자연스럽게 숨기기
- 캐시로 사용자가 스크롤하며 같은 상품 볼 때 즉시 표시

"매일 쓰고 싶은 앱"을 위한 빠르고 신뢰되는 가격 비교
"""

package com.ddaeany0919.insightdeal.data

import android.util.Log
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import retrofit2.http.Query
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 📊 4몰 가격 비교 데이터 모델
 * 사용자가 카드에서 보게 될 핵심 정보만 간단하게
 */
data class PlatformPrice(
    val platform: String,
    val price: Int,
    val originalPrice: Int = 0,
    val discountRate: Int = 0,
    val url: String = "",
    val shippingFee: Int = 0,
    val seller: String = "",
    val isAvailable: Boolean = true
)

data class PriceComparison(
    val query: String,
    val platforms: Map<String, PlatformPrice>,
    val lowestPlatform: String?,
    val lowestPrice: Int?,
    val maxSaving: Int,
    val averagePrice: Int?,
    val successCount: Int,
    val responseTimeMs: Int,
    val updatedAt: String,
    val traceId: String = ""
)

/**
 * 🌐 4몰 가격 비교 API 인터페이스
 */
interface PriceComparisonApi {
    @GET("/api/compare")
    suspend fun comparePrice(
        @Query("query") query: String
    ): Response<ApiResponse>
    
    @GET("/api/health")
    suspend fun healthCheck(): Response<HealthResponse>
}

/**
 * 💬 API 응답 데이터 구조
 */
data class ApiResponse(
    val trace_id: String,
    val query: String,
    val platforms: Map<String, PlatformData>,
    val lowest_platform: String?,
    val lowest_price: Int?,
    val max_saving: Int,
    val average_price: Int?,
    val success_count: Int,
    val response_time_ms: Int,
    val updated_at: String,
    val errors: List<String>
)

data class PlatformData(
    val price: Int,
    val original_price: Int = 0,
    val discount_rate: Int = 0,
    val url: String = "",
    val shipping_fee: Int = 0,
    val seller: String = "",
    val rating: Float = 0f,
    val is_available: Boolean = true
)

data class HealthResponse(
    val status: String,
    val scrapers: Int,
    val timestamp: String
)

/**
 * 🔄 가격 비교 상태
 */
sealed class PriceComparisonState {
    object Idle : PriceComparisonState()
    object Loading : PriceComparisonState()
    data class Success(val comparison: PriceComparison) : PriceComparisonState()
    data class Error(val message: String, val canRetry: Boolean = true) : PriceComparisonState()
    data class Timeout(val partialResults: PriceComparison?) : PriceComparisonState()
}

/**
 * 🚀 4몰 가격 비교 서비스
 * 사용자 경험을 위한 성능 최적화 및 캐시
 */
@Singleton
class PriceComparisonService @Inject constructor() {
    
    companion object {
        private const val TAG = "PriceComparison"
        private const val BASE_URL = "http://10.0.2.2:8000/" // Android Emulator
        private const val TIMEOUT_MS = 3000L // 3초 사용자 기다리기 상한
        private const val CACHE_DURATION_MS = 300_000L // 5분 캐시 (사용자 스크롤 시 즉시 표시)
    }
    
    private val api: PriceComparisonApi
    private val cache = ConcurrentHashMap<String, Pair<PriceComparison, Long>>()
    private val activeJobs = ConcurrentHashMap<String, Job>()
    
    // UI 상태 관리
    private val _comparisonState = MutableStateFlow<PriceComparisonState>(PriceComparisonState.Idle)
    val comparisonState: StateFlow<PriceComparisonState> = _comparisonState.asStateFlow()
    
    init {
        // 사용자 응답 속도 우선 Retrofit 설정
        val retrofit = Retrofit.Builder()
            .baseUrl(BASE_URL)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            
        api = retrofit.create(PriceComparisonApi::class.java)
        
        Log.i(TAG, "🚀 PriceComparisonService 초기화 완료 - 빠른 4몰 비교 준비")
    }
    
    /**
     * 🔍 사용자가 카드를 본 순간 4몰 가격 비교 시작
     * 반드시 2초 내 응답 또는 사용자에게 알림
     */
    suspend fun comparePrices(
        productName: String,
        showLoadingImmediately: Boolean = true
    ): PriceComparison? {
        val cleanQuery = productName.trim().take(30) // 성능을 위한 길이 제한
        if (cleanQuery.isBlank()) {
            Log.w(TAG, "⚠️ 빈 상품명 - 비교 스킵")
            return null
        }
        
        // 🚀 캐시 확인 - 사용자 스크롤 시 즉시 표시
        getCachedComparison(cleanQuery)?.let { cached ->
            Log.d(TAG, "💨 캐시 히트: $cleanQuery")
            _comparisonState.value = PriceComparisonState.Success(cached)
            return cached
        }
        
        // 이미 진행 중인 요청이 있으면 기다리기 (중복 요청 방지)
        activeJobs[cleanQuery]?.let { job ->
            if (job.isActive) {
                Log.d(TAG, "🔄 진행 중인 비교 요청 대기: $cleanQuery")
                job.join()
                return getCachedComparison(cleanQuery)
            }
        }
        
        // 사용자에게 로딩 상태 알림
        if (showLoadingImmediately) {
            _comparisonState.value = PriceComparisonState.Loading
        }
        
        val startTime = System.currentTimeMillis()
        Log.i(TAG, "🔍 4몰 가격 비교 시작: $cleanQuery")
        
        val job = CoroutineScope(Dispatchers.IO).launch {
            try {
                // 🔥 핵심: 3초 내 응답 또는 타임아웃
                val response = withTimeoutOrNull(TIMEOUT_MS) {
                    api.comparePrice(cleanQuery)
                }
                
                val elapsedMs = System.currentTimeMillis() - startTime
                
                if (response == null) {
                    // 타임아웃 - 사용자에게 친화적 알림
                    Log.w(TAG, "⏱️ 비교 요청 타임아웃 ($elapsedMs ms): $cleanQuery")
                    _comparisonState.value = PriceComparisonState.Timeout(null)
                    return@launch
                }
                
                if (response.isSuccessful) {
                    response.body()?.let { apiResponse ->
                        val comparison = mapToComparison(apiResponse)
                        
                        // 캐시 저장
                        cache[cleanQuery] = comparison to System.currentTimeMillis()
                        
                        val isFastResponse = elapsedMs <= 2000
                        Log.i(TAG, "✅ 비교 성공 ($elapsedMs ms, fast: $isFastResponse): " +
                              "${comparison.lowestPlatform} ${comparison.lowestPrice}원, " +
                              "절약 ${comparison.maxSaving}원")
                        
                        _comparisonState.value = PriceComparisonState.Success(comparison)
                    } ?: run {
                        Log.e(TAG, "❌ API 응답 바디 비어있음: $cleanQuery")
                        _comparisonState.value = PriceComparisonState.Error("데이터를 불러올 수 없습니다")
                    }
                } else {
                    Log.e(TAG, "❌ API 요청 실패 ${response.code()}: $cleanQuery")
                    _comparisonState.value = PriceComparisonState.Error(
                        "잠시 후 다시 시도해주세요",
                        canRetry = response.code() != 400
                    )
                }
                
            } catch (e: Exception) {
                val elapsedMs = System.currentTimeMillis() - startTime
                Log.e(TAG, "❌ 비교 요청 예외 ($elapsedMs ms): $cleanQuery", e)
                
                _comparisonState.value = PriceComparisonState.Error(
                    "네트워크 오류가 발생했습니다",
                    canRetry = true
                )
            } finally {
                activeJobs.remove(cleanQuery)
            }
        }
        
        activeJobs[cleanQuery] = job
        job.join()
        
        return getCachedComparison(cleanQuery)
    }
    
    /**
     * 💨 캐시된 비교 결과 반환
     */
    private fun getCachedComparison(query: String): PriceComparison? {
        val cached = cache[query] ?: return null
        val (comparison, timestamp) = cached
        
        return if (System.currentTimeMillis() - timestamp < CACHE_DURATION_MS) {
            comparison
        } else {
            cache.remove(query) // 만료된 캐시 제거
            null
        }
    }
    
    /**
     * 🔄 API 응답을 앱 내부 모델로 매핑
     */
    private fun mapToComparison(apiResponse: ApiResponse): PriceComparison {
        val platformPrices = apiResponse.platforms.mapValues { (platform, data) ->
            PlatformPrice(
                platform = platform,
                price = data.price,
                originalPrice = data.original_price,
                discountRate = data.discount_rate,
                url = data.url,
                shippingFee = data.shipping_fee,
                seller = data.seller,
                isAvailable = data.is_available
            )
        }
        
        return PriceComparison(
            query = apiResponse.query,
            platforms = platformPrices,
            lowestPlatform = apiResponse.lowest_platform,
            lowestPrice = apiResponse.lowest_price,
            maxSaving = apiResponse.max_saving,
            averagePrice = apiResponse.average_price,
            successCount = apiResponse.success_count,
            responseTimeMs = apiResponse.response_time_ms,
            updatedAt = apiResponse.updated_at,
            traceId = apiResponse.trace_id
        )
    }
    
    /**
     * 🧹 캐시 청소 (메모리 관리)
     */
    fun clearCache() {
        cache.clear()
        Log.d(TAG, "🧹 가격 비교 캐시 청소")
    }
    
    /**
     * 📈 캐시 통계 정보
     */
    fun getCacheStats(): Pair<Int, Int> {
        val total = cache.size
        val expired = cache.values.count { (_, timestamp) ->
            System.currentTimeMillis() - timestamp >= CACHE_DURATION_MS
        }
        return total to (total - expired)
    }
}
package com.ddaeany0919.insightdeal.network

import com.ddaeany0919.insightdeal.models.DealItem
import com.ddaeany0919.insightdeal.models.DealStats
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.*
import java.util.concurrent.TimeUnit

/**
 * 🌐 InsightDeal API 서비스 인터페이스 (통합 버전)
 */
interface ApiService {

    // 🔥 핫딜 목록 조회
    @GET("deals")
    suspend fun getDeals(
        @Query("page") page: Int = 1,
        @Query("limit") limit: Int = 20,
        @Query("category") category: String? = null,
        @Query("site") site: String? = null,
        @Query("sort") sort: String = "latest"
    ): Response<DealsResponse>

    // 🔍 핫딜 검색
    @GET("deals/search")
    suspend fun searchDeals(
        @Query("q") query: String,
        @Query("page") page: Int = 1,
        @Query("limit") limit: Int = 20,
        @Query("min_price") minPrice: Int? = null,
        @Query("max_price") maxPrice: Int? = null,
        @Query("min_discount") minDiscount: Int? = null,
        @Query("category") category: String? = null,
        @Query("site") site: String? = null,
        @Query("sort") sort: String = "relevance"
    ): Response<DealsResponse>

    // 📊 핫딜 통계
    @GET("deals/stats")
    suspend fun getDealStats(): Response<DealStats>

    // 🔥 특정 핫딜 상세 정보
    @GET("deals/{id}")
    suspend fun getDealDetail(@Path("id") dealId: Int): Response<DealDetail>

    // 📈 핫딜 가격 히스토리
    @GET("deals/{dealId}/history")
    suspend fun getDealPriceHistory(@Path("dealId") dealId: Int): Response<List<PriceHistoryItem>>

    // 🎯 향상된 딜 정보
    @GET("deals/{dealId}/enhanced-info")
    suspend fun getEnhancedDealInfo(@Path("dealId") dealId: Int): Response<EnhancedDealInfo>

    // ✅ 쿠팡 상품 추적 API

    // 사용자가 추가한 쿠팡 상품 목록 조회
    @GET("products")
    suspend fun getUserProducts(
        @Query("user_id") userId: String
    ): Response<List<ApiProduct>>

    // 새 쿠팡 상품 추가
    @POST("products")
    suspend fun addProduct(
        @Body request: Map<String, Any>
    ): Response<ApiProduct>

    // 특정 상품 정보 조회
    @GET("products/{productId}")
    suspend fun getProduct(
        @Path("productId") productId: Int
    ): Response<ApiProduct>

    // 상품 가격 히스토리 조회
    @GET("products/{productId}/history")
    suspend fun getProductPriceHistory(
        @Path("productId") productId: Int
    ): Response<List<ApiPriceHistory>>

    // 목표 가격 업데이트
    @PUT("products/{productId}/target")
    suspend fun updateTargetPrice(
        @Path("productId") productId: Int,
        @Body request: Map<String, Any>
    ): Response<ApiProduct>

    // 상품 추적 삭제
    @DELETE("products/{productId}")
    suspend fun deleteProduct(
        @Path("productId") productId: Int
    ): Response<Unit>

    // ✅ FCM 토큰 관리 API

    // FCM 토큰 등록
    @POST("fcm/register")
    suspend fun registerFCMToken(
        @Body request: FCMTokenRequest
    ): Response<ApiResponse>

    // 테스트 푸시 알림 전송
    @POST("fcm/test")
    suspend fun sendTestPush(
        @Body request: Map<String, String>
    ): Response<ApiResponse>

    // 🔍 키워드 알림 등록
    @POST("alerts/keyword")
    suspend fun registerKeywordAlert(
        @Body request: KeywordAlertRequest
    ): Response<ApiResponse>

    // 💰 가격 추적 등록
    @POST("alerts/price")
    suspend fun registerPriceAlert(
        @Body request: PriceAlertRequest
    ): Response<ApiResponse>

    // 📈 인기 키워드 목록
    @GET("keywords/popular")
    suspend fun getPopularKeywords(
        @Query("limit") limit: Int = 10
    ): Response<List<String>>

    // 🏷️ 카테고리 목록
    @GET("categories")
    suspend fun getCategories(): Response<List<Category>>

    // 🌐 지원 사이트 목록
    @GET("sites")
    suspend fun getSupportedSites(): Response<List<Site>>
}

/**
 * 🏗️ API 클라이언트 팩토리
 */
object ApiClient {

    private const val BASE_URL_DEBUG = "http://10.0.2.2:8000/"
    private const val BASE_URL_RELEASE = "https://api.insightdeal.com/"

    // 🌐 HTTP 로깅 인터셉터
    private val loggingInterceptor = HttpLoggingInterceptor().apply {
        level = HttpLoggingInterceptor.Level.BODY // 개발 중에는 항상 활성화
    }

    // 🔗 OkHttpClient 빌더
    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .addInterceptor(loggingInterceptor)
        .build()

    // 🚀 Retrofit 인스턴스 생성
    private val retrofit: Retrofit = Retrofit.Builder()
        .baseUrl(BASE_URL_DEBUG) // 개발 중에는 디버그 URL 사용
        .client(okHttpClient)
        .addConverterFactory(GsonConverterFactory.create())
        .build()

    fun create(): ApiService {
        return retrofit.create(ApiService::class.java)
    }
}

// 📋 API 응답 데이터 모델들

/**
 * 딜 목록 응답
 */
data class DealsResponse(
    val deals: List<DealItem>,
    val total: Int,
    val page: Int,
    val totalPages: Int,
    val hasNext: Boolean
)

/**
 * 공통 API 응답
 */
data class ApiResponse(
    val success: Boolean,
    val message: String,
    val data: Any? = null
)

/**
 * 쿠팡 상품 API 모델
 */
data class ApiProduct(
    val id: Int,
    val title: String,
    val brand: String?,
    val image_url: String?,
    val current_price: Int?,
    val original_price: Int?,
    val lowest_price: Int?,
    val highest_price: Int?,
    val target_price: Int?,
    val url: String,
    val created_at: String,
    val last_checked: String?
)

/**
 * 가격 히스토리 API 모델
 */
data class ApiPriceHistory(
    val price: Int,
    val original_price: Int?,
    val discount_rate: Int?,
    val tracked_at: String,
    val is_available: Boolean
)

/**
 * 딜 상세 정보 모델
 */
data class DealDetail(
    val id: Int,
    val title: String,
    val description: String?,
    val price: Int,
    val originalPrice: Int?,
    val discountRate: Int?,
    val imageUrl: String?,
    val url: String,
    val siteName: String,
    val createdAt: String,
    val viewCount: Int,
    val likeCount: Int,
    val commentCount: Int
)

/**
 * 가격 히스토리 항목 모델
 */
data class PriceHistoryItem(
    val price: Int,
    val originalPrice: Int?,
    val discountRate: Int?,
    val recordedAt: String
)

/**
 * 향상된 딜 정보 모델
 */
data class EnhancedDealInfo(
    val qualityScore: Int,
    val priceRank: String,
    val similarDeals: List<DealItem>,
    val priceAlert: String?,
    val recommendation: String?
)

/**
 * FCM 토큰 등록 요청
 */
data class FCMTokenRequest(
    val token: String,
    val deviceId: String,
    val platform: String = "android"
)

/**
 * 키워드 알림 등록 요청
 */
data class KeywordAlertRequest(
    val keyword: String,
    val fcmToken: String,
    val minDiscount: Int? = null,
    val maxPrice: Int? = null
)

/**
 * 가격 추적 등록 요청
 */
data class PriceAlertRequest(
    val dealId: Long,
    val targetPrice: Int,
    val fcmToken: String
)

/**
 * 카테고리 모델
 */
data class Category(
    val id: String,
    val name: String,
    val icon: String? = null
)

/**
 * 사이트 모델
 */
data class Site(
    val id: String,
    val name: String,
    val url: String,
    val icon: String? = null
)
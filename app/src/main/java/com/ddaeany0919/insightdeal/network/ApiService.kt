package com.ddaeany0919.insightdeal.network

import com.ddaeany0919.insightdeal.models.DealItem
import com.ddaeany0919.insightdeal.models.DealStats
import retrofit2.Response
import retrofit2.http.*

/**
 * 🌐 InsightDeal API 서비스 인터페이스
 */
interface ApiService {
    
    /**
     * 🔥 핫딜 목록 조회
     */
    @GET("deals")
    suspend fun getDeals(
        @Query("page") page: Int = 1,
        @Query("limit") limit: Int = 20,
        @Query("category") category: String? = null,
        @Query("site") site: String? = null,
        @Query("sort") sort: String = "latest"
    ): Response<DealsResponse>
    
    /**
     * 🔍 핫딜 검색
     */
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
    
    /**
     * 📊 핫딜 통계
     */
    @GET("deals/stats")
    suspend fun getDealStats(): Response<DealStats>
    
    /**
     * 🔥 특정 핫딜 상세 정보
     */
    @GET("deals/{id}")
    suspend fun getDealDetail(
        @Path("id") dealId: Long
    ): Response<DealItem>
    
    /**
     * 🔔 FCM 토큰 등록
     */
    @POST("fcm/register")
    suspend fun registerFCMToken(
        @Body request: FCMTokenRequest
    ): Response<ApiResponse>
    
    /**
     * 🔍 키워드 알림 등록
     */
    @POST("alerts/keyword")
    suspend fun registerKeywordAlert(
        @Body request: KeywordAlertRequest
    ): Response<ApiResponse>
    
    /**
     * 💰 가격 추적 등록
     */
    @POST("alerts/price")
    suspend fun registerPriceAlert(
        @Body request: PriceAlertRequest
    ): Response<ApiResponse>
    
    /**
     * 📈 인기 키워드 목록
     */
    @GET("keywords/popular")
    suspend fun getPopularKeywords(
        @Query("limit") limit: Int = 10
    ): Response<List<String>>
    
    /**
     * 🏷️ 카테고리 목록
     */
    @GET("categories")
    suspend fun getCategories(): Response<List<Category>>
    
    /**
     * 🌐 지원 사이트 목록
     */
    @GET("sites")
    suspend fun getSupportedSites(): Response<List<Site>>
}

/**
 * 📦 API 응답 데이터 클래스들
 */
data class DealsResponse(
    val deals: List<DealItem>,
    val total: Int,
    val page: Int,
    val totalPages: Int,
    val hasNext: Boolean
)

data class ApiResponse(
    val success: Boolean,
    val message: String,
    val data: Any? = null
)

data class FCMTokenRequest(
    val token: String,
    val deviceId: String,
    val platform: String = "android"
)

data class KeywordAlertRequest(
    val keyword: String,
    val fcmToken: String,
    val minDiscount: Int? = null,
    val maxPrice: Int? = null
)

data class PriceAlertRequest(
    val dealId: Long,
    val targetPrice: Int,
    val fcmToken: String
)

data class Category(
    val id: String,
    val name: String,
    val icon: String? = null
)

data class Site(
    val id: String,
    val name: String,
    val url: String,
    val icon: String? = null
)
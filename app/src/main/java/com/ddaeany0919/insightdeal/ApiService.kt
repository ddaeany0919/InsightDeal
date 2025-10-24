package com.ddaeany0919.insightdeal

import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.*
import java.util.concurrent.TimeUnit

// 🌐 HTTP 로깅 인터셉터 (Debug 모드에서만 활성)
private val loggingInterceptor = HttpLoggingInterceptor().apply {
    level = if (BuildConfig.DEBUG) {
        HttpLoggingInterceptor.Level.BODY
    } else {
        HttpLoggingInterceptor.Level.NONE
    }
}

// OkHttpClient 빌더 (네트워크 안정성 강화)
private val okHttpClient = OkHttpClient.Builder()
    .connectTimeout(30, TimeUnit.SECONDS)
    .readTimeout(30, TimeUnit.SECONDS)
    .writeTimeout(30, TimeUnit.SECONDS)
    .addInterceptor(loggingInterceptor)
    .build()

// Retrofit 인스턴스 생성
private val retrofit: Retrofit = Retrofit.Builder()
    .baseUrl(BuildConfig.BASE_URL) // build.gradle에서 설정
    .client(okHttpClient)
    .addConverterFactory(GsonConverterFactory.create())
    .build()

interface ApiService {
    
    // ✅ 기존 커뮤니티 핫딜 API
    
    @GET("api/deals")
    suspend fun getDeals(
        @Query("page") page: Int,
        @Query("page_size") pageSize: Int = 20
    ): Response<List<DealItem>>
    
    @GET("api/deals/{dealId}")
    suspend fun getDealDetail(@Path("dealId") dealId: Int): Response<DealDetail>
    
    @GET("api/deals/{dealId}/history")
    suspend fun getDealPriceHistory(@Path("dealId") dealId: Int): Response<List<PriceHistoryItem>>
    
    @GET("api/deals/{dealId}/enhanced-info")
    suspend fun getEnhancedDealInfo(@Path("dealId") dealId: Int): Response<EnhancedDealInfo>
    
    // ✅ 새로운 쿠팡 상품 추적 API
    
    /**
     * 사용자가 추가한 쿠팡 상품 목록 조회
     */
    @GET("api/products")
    suspend fun getUserProducts(
        @Query("user_id") userId: String
    ): Response<List<ApiProduct>>
    
    /**
     * 새 쿠팡 상품 추가
     */
    @POST("api/products")
    suspend fun addProduct(
        @Body request: Map<String, Any>
    ): Response<ApiProduct>
    
    /**
     * 특정 상품 정보 조회
     */
    @GET("api/products/{productId}")
    suspend fun getProduct(
        @Path("productId") productId: Int
    ): Response<ApiProduct>
    
    /**
     * 상품 가격 히스토리 조회
     */
    @GET("api/products/{productId}/history")
    suspend fun getProductPriceHistory(
        @Path("productId") productId: Int
    ): Response<List<ApiPriceHistory>>
    
    /**
     * 목표 가격 업데이트
     */
    @PUT("api/products/{productId}/target")
    suspend fun updateTargetPrice(
        @Path("productId") productId: Int,
        @Body request: Map<String, Any>
    ): Response<ApiProduct>
    
    /**
     * 상품 추적 삭제
     */
    @DELETE("api/products/{productId}")
    suspend fun deleteProduct(
        @Path("productId") productId: Int
    ): Response<Unit>
    
    // ✅ FCM 토큰 관리 API
    
    /**
     * FCM 토큰 등록
     */
    @POST("api/fcm/register")
    suspend fun registerFCMToken(
        @Body request: Map<String, String>
    ): Response<Map<String, String>>
    
    /**
     * 테스트 푸시 알림 전송
     */
    @POST("api/fcm/test")
    suspend fun sendTestPush(
        @Body request: Map<String, String>
    ): Response<Map<String, String>>
    
    companion object {
        fun create(): ApiService {
            return retrofit.create(ApiService::class.java)
        }
    }
}

// 📋 API 응답 데이터 모델들

// 기존 모델 (DealItem, DealDetail 등은 기존 파일에서 유지)
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

data class ApiPriceHistory(
    val price: Int,
    val original_price: Int?,
    val discount_rate: Int?,
    val tracked_at: String,
    val is_available: Boolean
)
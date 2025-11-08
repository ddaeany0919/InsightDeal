package com.ddaeany0919.insightdeal.data.network

import android.util.Log
import com.ddaeany0919.insightdeal.BuildConfig
import com.ddaeany0919.insightdeal.presentation.wishlist.*
import com.google.gson.annotations.SerializedName
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.*
import java.util.concurrent.TimeUnit

interface WishlistApiService {
    /**
     * 키워드로 위시리스트 추가 (from-keyword 엔드포인트)
     */
    @POST("api/wishlist/from-keyword")
    suspend fun createWishlistFromKeyword(@Body request: WishlistCreateFromKeywordRequest): WishlistApiResponse

    /**
     * URL로 위시리스트 추가 (from-url 엔드포인트)
     */
    @POST("api/wishlist/from-url")
    suspend fun createWishlistFromUrl(@Body request: WishlistCreateFromUrlRequest): WishlistApiResponse

    @GET("api/wishlist")
    suspend fun getWishlist(
        @Query("user_id") userId: String = "default"
    ): List<WishlistApiResponse>

    @GET("api/wishlist/{wishlist_id}")
    suspend fun getWishlistItem(
        @Path("wishlist_id") wishlistId: Int,
        @Query("user_id") userId: String = "default"
    ): WishlistApiResponse

    @PATCH("api/wishlist/{wishlist_id}")
    suspend fun updateWishlist(
        @Path("wishlist_id") wishlistId: Int,
        @Body request: WishlistUpdateRequest,
        @Query("user_id") userId: String = "default"
    ): WishlistApiResponse

    /**
     * Simplified deletion approach (Phase 1)
     * Using standard DELETE with query parameter
     */
    @DELETE("api/wishlist/{wishlist_id}")
    suspend fun deleteWithQuery(
        @Path("wishlist_id") wishlistId: Int,
        @Query("user_id") userId: String
    ): Response<DeleteResponse>

    @POST("api/wishlist/{wishlist_id}/check-price")
    suspend fun checkWishlistPrice(
        @Path("wishlist_id") wishlistId: Int,
        @Query("user_id") userId: String = "default"
    ): PriceCheckResponse

    @GET("api/wishlist/{wishlist_id}/history")
    suspend fun getWishlistPriceHistory(
        @Path("wishlist_id") wishlistId: Int,
        @Query("user_id") userId: String = "default",
        @Query("days") days: Int = 30
    ): List<PriceHistoryApiResponse>

    /**
     * Phase 1 preparation: Link analysis endpoint
     * TODO: Implement in Phase 2 with actual AI integration
     */
    @POST("api/product/analyze-link")
    suspend fun analyzeLink(@Body request: AnalyzeLinkRequest): ProductAnalysisResponse

    companion object {
        private const val TAG = "ApiService"
        
        // Default configuration (legacy)
        private val BASE_URL: String = BuildConfig.BASE_URL
        
        fun create(): WishlistApiService = Retrofit.Builder()
            .baseUrl(BASE_URL)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(WishlistApiService::class.java)
            
        /**
         * Phase 1: Stable network configuration with dynamic URL detection
         * - 20-second timeouts for better stability
         * - Smart BASE_URL detection using NetworkConfig
         * - Connection pooling and retry mechanisms
         */
        suspend fun createWithStableConfig(): WishlistApiService {
            Log.d(TAG, "createWithStableConfig: 시작")
            
            // Get optimal server URL using network detection
            val baseUrl = try {
                NetworkConfig.getServerUrl()
            } catch (e: Exception) {
                Log.w(TAG, "Dynamic URL detection failed, using BuildConfig", e)
                BASE_URL
            }
            
            Log.d(TAG, "createWithStableConfig: baseUrl=$baseUrl")
            
            // Enhanced logging interceptor
            val loggingInterceptor = HttpLoggingInterceptor { message ->
                Log.d("OkHttp", message)
            }.apply {
                level = if (BuildConfig.DEBUG_MODE) {
                    HttpLoggingInterceptor.Level.BODY
                } else {
                    HttpLoggingInterceptor.Level.BASIC
                }
            }
            
            // Connection info interceptor
            val connectionInterceptor = Interceptor { chain ->
                val request = chain.request()
                Log.d(TAG, "API 요청: ${request.method} ${request.url}")
                
                val startTime = System.currentTimeMillis()
                val response = chain.proceed(request)
                val endTime = System.currentTimeMillis()
                
                Log.d(TAG, "API 응답: ${response.code} (${endTime - startTime}ms)")
                response
            }
            
            // Retry interceptor for server errors
            val retryInterceptor = Interceptor { chain ->
                val request = chain.request()
                var response = chain.proceed(request)
                
                // Retry on specific server errors
                var retryCount = 0
                while (!response.isSuccessful && retryCount < 2) {
                    when (response.code) {
                        500, 502, 503, 504 -> {
                            Log.d(TAG, "서버 오류로 인한 재시도: ${response.code} (${retryCount + 1}/2)")
                            response.close()
                            retryCount++
                            Thread.sleep(1000L * retryCount)
                            response = chain.proceed(request)
                        }
                        else -> break
                    }
                }
                response
            }
            
            // OkHttp client with stable configuration
            val okHttpClient = OkHttpClient.Builder()
                .connectTimeout(20, TimeUnit.SECONDS)
                .readTimeout(20, TimeUnit.SECONDS)
                .writeTimeout(20, TimeUnit.SECONDS)
                .addInterceptor(connectionInterceptor)
                .addInterceptor(loggingInterceptor)
                .addInterceptor(retryInterceptor)
                .retryOnConnectionFailure(true)
                .build()
                
            Log.d(TAG, "createWithStableConfig: 완료")
            
            return Retrofit.Builder()
                .baseUrl(baseUrl)
                .client(okHttpClient)
                .addConverterFactory(GsonConverterFactory.create())
                .build()
                .create(WishlistApiService::class.java)
        }
    }
}

// Data classes for API requests and responses
data class WishlistUpdateRequest(
    val targetPrice: Int? = null,
    val isActive: Boolean? = null,
    val alertEnabled: Boolean? = null
)

data class DeleteRequest(
    @SerializedName("user_id") val userId: String
)

// ✅ 서버 API에 맞게 분리된 요청 모델
data class WishlistCreateFromKeywordRequest(
    @SerializedName("keyword") val keyword: String,
    @SerializedName("target_price") val targetPrice: Int,
    @SerializedName("user_id") val userId: String
)

data class WishlistCreateFromUrlRequest(
    @SerializedName("product_url") val productUrl: String,
    @SerializedName("target_price") val targetPrice: Int,
    @SerializedName("user_id") val userId: String
)

// Phase 2 preparation: Link analysis requests
data class AnalyzeLinkRequest(
    val url: String,
    @SerializedName("user_id") val userId: String
)

data class LinkAddRequest(
    val url: String,
    @SerializedName("target_price") val targetPrice: Int,
    @SerializedName("user_id") val userId: String
)

data class ProductAnalysisResponse(
    @SerializedName("product_name") val productName: String,
    val brand: String?,
    val category: String?,
    @SerializedName("estimated_lowest_price") val estimatedLowestPrice: Int?,
    val confidence: Float,
    @SerializedName("analysis_status") val analysisStatus: String
)

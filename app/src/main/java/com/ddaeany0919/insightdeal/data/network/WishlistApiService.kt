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
import java.net.InetAddress
import java.util.concurrent.TimeUnit

interface WishlistApiService {
    @POST("api/wishlist")
    suspend fun createWishlist(@Body request: WishlistCreateRequest): WishlistApiResponse

    @GET("api/wishlist")
    suspend fun getWishlist(
        @Query("user_id") userId: String = "default",
        @Query("active_only") activeOnly: Boolean = true
    ): List<WishlistApiResponse>

    @GET("api/wishlist/{wishlist_id}")
    suspend fun getWishlistItem(
        @Path("wishlist_id") wishlistId: Int,
        @Query("user_id") userId: String = "default"
    ): WishlistApiResponse

    @PUT("api/wishlist/{wishlist_id}")
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
    ): Response<Unit>

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

    /**
     * Phase 1 preparation: Add from link endpoint  
     * TODO: Implement in Phase 2 with full link processing
     */
    @POST("api/wishlist/add-from-link")
    suspend fun addFromLink(@Body request: LinkAddRequest): WishlistApiResponse

    companion object {
        private const val TAG = "ApiService"
        
        // Default configuration
        private val BASE_URL: String = BuildConfig.BASE_URL
        
        fun create(): WishlistApiService = Retrofit.Builder()
            .baseUrl(BASE_URL)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(WishlistApiService::class.java)
            
        /**
         * Phase 1: Stable network configuration
         * - 20-second timeouts for better stability
         * - Smart BASE_URL detection
         * - Connection pooling
         * - Retry interceptor
         */
        fun createWithStableConfig(): WishlistApiService {
            Log.d(TAG, "createWithStableConfig: 네트워크 설정 시작")
            
            // Smart BASE_URL detection
            val baseUrl = getOptimalBaseUrl()
            Log.d(TAG, "createWithStableConfig: baseUrl=$baseUrl")
            
            // Logging interceptor for debugging
            val loggingInterceptor = HttpLoggingInterceptor { message ->
                Log.d("OkHttp", message)
            }.apply {
                level = if (BuildConfig.DEBUG) {
                    HttpLoggingInterceptor.Level.BODY
                } else {
                    HttpLoggingInterceptor.Level.BASIC
                }
            }
            
            // Retry interceptor for network stability
            val retryInterceptor = Interceptor { chain ->
                val request = chain.request()
                var response = chain.proceed(request)
                
                // Retry on specific network errors
                var retryCount = 0
                while (!response.isSuccessful && retryCount < 2) {
                    if (response.code in listOf(500, 502, 503, 504)) {
                        Log.d(TAG, "Retrying request due to server error: ${response.code}")
                        response.close()
                        retryCount++
                        Thread.sleep(1000L * retryCount) // Exponential backoff
                        response = chain.proceed(request)
                    } else {
                        break
                    }
                }
                response
            }
            
            // OkHttp client with stable configuration
            val okHttpClient = OkHttpClient.Builder()
                .connectTimeout(20, TimeUnit.SECONDS)
                .readTimeout(20, TimeUnit.SECONDS)
                .writeTimeout(20, TimeUnit.SECONDS)
                .addInterceptor(loggingInterceptor)
                .addInterceptor(retryInterceptor)
                .retryOnConnectionFailure(true)
                .build()
                
            Log.d(TAG, "createWithStableConfig: OkHttp client 설정 완료")
            
            return Retrofit.Builder()
                .baseUrl(baseUrl)
                .client(okHttpClient)
                .addConverterFactory(GsonConverterFactory.create())
                .build()
                .create(WishlistApiService::class.java)
        }
        
        /**
         * Smart BASE_URL detection for emulator vs real device
         */
        private fun getOptimalBaseUrl(): String {
            return try {
                // Check if we're running on emulator (10.0.2.2) or real device
                val localhost = InetAddress.getByName("localhost")
                val emulatorHost = InetAddress.getByName("10.0.2.2")
                
                // Test connectivity to determine best URL
                when {
                    isEmulator() -> {
                        Log.d(TAG, "Detected emulator environment")
                        "http://10.0.2.2:8000/"
                    }
                    else -> {
                        Log.d(TAG, "Detected real device environment")
                        // For real device, try local network IP first
                        BuildConfig.BASE_URL.takeIf { it.isNotBlank() } ?: "http://192.168.1.100:8000/"
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Base URL detection failed, using default", e)
                BuildConfig.BASE_URL.takeIf { it.isNotBlank() } ?: "http://10.0.2.2:8000/"
            }
        }
        
        /**
         * Detect if running on Android emulator
         */
        private fun isEmulator(): Boolean {
            return (android.os.Build.BRAND.startsWith("generic") && android.os.Build.DEVICE.startsWith("generic"))
                    || android.os.Build.FINGERPRINT.startsWith("generic")
                    || android.os.Build.FINGERPRINT.startsWith("unknown")
                    || android.os.Build.HARDWARE.contains("goldfish")
                    || android.os.Build.HARDWARE.contains("ranchu")
                    || android.os.Build.MODEL.contains("google_sdk")
                    || android.os.Build.MODEL.contains("Emulator")
                    || android.os.Build.MODEL.contains("Android SDK built for x86")
                    || android.os.Build.MANUFACTURER.contains("Genymotion")
                    || android.os.Build.PRODUCT.contains("sdk_google")
                    || android.os.Build.PRODUCT.contains("google_sdk")
                    || android.os.Build.PRODUCT.contains("sdk")
                    || android.os.Build.PRODUCT.contains("sdk_x86")
                    || android.os.Build.PRODUCT.contains("vbox86p")
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
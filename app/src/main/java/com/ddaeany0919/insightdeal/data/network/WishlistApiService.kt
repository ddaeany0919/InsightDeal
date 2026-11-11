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
import com.ddaeany0919.insightdeal.network.ApiService

// Interface moved to ApiService.kt

// Data classes for API requests and responses
data class WishlistUpdateRequest(
    @SerializedName("target_price") val targetPrice: Int? = null,
    @SerializedName("is_active") val isActive: Boolean? = null,
    @SerializedName("alert_enabled") val alertEnabled: Boolean? = null
)

data class DeleteRequest(
    @SerializedName("user_id") val userId: String
)

// 서버 API에 맞게 분리된 요청 모델
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
    @SerializedName("url") val url: String,
    @SerializedName("user_id") val userId: String
)

data class LinkAddRequest(
    @SerializedName("url") val url: String,
    @SerializedName("target_price") val targetPrice: Int,
    @SerializedName("user_id") val userId: String
)

data class ProductAnalysisResponse(
    @SerializedName("product_name") val productName: String,
    @SerializedName("brand") val brand: String?,
    @SerializedName("category") val category: String?,
    @SerializedName("estimated_lowest_price") val estimatedLowestPrice: Int?,
    @SerializedName("confidence") val confidence: Float,
    @SerializedName("analysis_status") val analysisStatus: String
)

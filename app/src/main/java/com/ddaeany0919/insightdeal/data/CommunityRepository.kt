package com.ddaeany0919.insightdeal.data

import android.util.Log
import com.ddaeany0919.insightdeal.network.NetworkModule
import retrofit2.http.GET
import com.ddaeany0919.insightdeal.network.ApiService

data class HotDealResponse(
    val deals: List<HotDealDto>
)

data class HotDealDto(
    val id: Long,
    val title: String,
    val price: Int,
    @com.google.gson.annotations.SerializedName("original_price") val originalPrice: Int?,
    @com.google.gson.annotations.SerializedName("discount_rate") val discountRate: Int,
    @com.google.gson.annotations.SerializedName("mall_name") val mallName: String?,
    @com.google.gson.annotations.SerializedName("image_url") val imageUrl: String?,
    val category: String?,
    @com.google.gson.annotations.SerializedName("site_name") val communityName: String?,
    @com.google.gson.annotations.SerializedName("shipping_fee") val shippingFee: String?,
    @com.google.gson.annotations.SerializedName("like_count") val likeCount: Int,
    @com.google.gson.annotations.SerializedName("dislike_count") val dislikeCount: Int = 0,
    @com.google.gson.annotations.SerializedName("comment_count") val commentCount: Int,
    @com.google.gson.annotations.SerializedName("time_ago") val timeAgo: String?,
    @com.google.gson.annotations.SerializedName("url") val link: String?,
    @com.google.gson.annotations.SerializedName("honey_score") val honeyScore: Int = 0,
    @com.google.gson.annotations.SerializedName("created_at") val createdAt: String? = null,
    @com.google.gson.annotations.SerializedName("is_closed") val isClosed: Boolean = false
)

// ApiService interface moved to ApiService.kt

class CommunityRepository {
    companion object {
        private const val TAG = "CommunityRepository"
    }
    
    private val api: ApiService = NetworkModule.createService()

    suspend fun getHotDeals(category: String? = null): List<HotDealDto> {
        Log.d(TAG, "📡 Requesting hot deals from API with category=$category...")
        try {
            val startTime = System.currentTimeMillis()
            val response = api.getHotDealsDto(category = category)
            val duration = System.currentTimeMillis() - startTime
            Log.d(TAG, "✅ API Response received: ${response.deals.size} items in ${duration}ms")
            
            response.deals.forEachIndexed { index, deal ->
                Log.d(TAG, "  [$index] ${deal.title} - ${deal.communityName}")
            }
            
            return response.deals
        } catch (e: Exception) {
            Log.e(TAG, "❌ API Request failed: ${e.message}", e)
            throw e
        }
    }
}

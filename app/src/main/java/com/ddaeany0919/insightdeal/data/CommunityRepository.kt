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
    val price: String,
    val originalPrice: String?,
    val discountRate: Int,
    val mallName: String,
    val imageUrl: String?,
    val category: String?,
    val communityName: String,
    val shippingFee: String?,
    val likeCount: Int,
    val commentCount: Int,
    val timeAgo: String?,
    val link: String?,
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

package com.ddaeany0919.insightdeal.data

import android.util.Log
import com.ddaeany0919.insightdeal.network.NetworkModule
import retrofit2.http.GET

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
    val timeAgo: String,
    val link: String?
)

interface CommunityApi {
    @GET("api/community/hot-deals")
    suspend fun getHotDeals(): HotDealResponse
}

class CommunityRepository {
    companion object {
        private const val TAG = "CommunityRepository"
    }
    
    private val api: CommunityApi = NetworkModule.createService()

    suspend fun getHotDeals(): List<HotDealDto> {
        Log.d(TAG, "📡 Requesting hot deals from API...")
        try {
            val startTime = System.currentTimeMillis()
            val response = api.getHotDeals()
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

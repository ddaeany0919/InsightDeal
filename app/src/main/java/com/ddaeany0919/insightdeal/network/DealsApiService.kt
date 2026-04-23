package com.ddaeany0919.insightdeal.network

import com.ddaeany0919.insightdeal.models.ComparisonResponse
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Query

interface DealsApiService {
    @GET("/api/compare")
    suspend fun comparePrice(
        @Query("query") query: String
    ): Response<ComparisonResponse>

    @GET("/api/health")
    suspend fun healthCheck(): Response<HealthResponse>

    @GET("/api/community/hot-deals")
    suspend fun getHotDeals(
        @Query("limit") limit: Int = 20,
        @Query("offset") offset: Int = 0,
        @Query("category") category: String? = null,
        @Query("keyword") keyword: String? = null,
        @Query("platform") platform: String? = null
    ): Response<HotDealsResponse>

    @GET("/api/community/popular-keywords")
    suspend fun getPopularKeywords(): Response<PopularKeywordsResponse>
}

data class PopularKeywordsResponse(
    val keywords: List<String>
)

data class HotDealsResponse(
    val deals: List<com.ddaeany0919.insightdeal.models.DealItem>
)

 

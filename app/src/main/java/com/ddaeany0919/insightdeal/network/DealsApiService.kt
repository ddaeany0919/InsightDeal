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

    @GET("/api/deals")
    suspend fun getDeals(
        @Query("limit") limit: Int = 20,
        @Query("offset") offset: Int = 0
    ): Response<DealsListResponse>
}

data class DealsListResponse(
    val deals: List<com.ddaeany0919.insightdeal.models.ApiDeal>,
    val total: Int,
    val has_more: Boolean
)

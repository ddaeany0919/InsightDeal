package com.ddaeany0919.insightdeal.network

import com.ddaeany0919.insightdeal.models.ComparisonResponse
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Query

// Interface moved to ApiService.kt

data class PopularKeywordsResponse(
    val keywords: List<String>
)

data class HotDealsResponse(
    val deals: List<com.ddaeany0919.insightdeal.models.DealItem>
)

 

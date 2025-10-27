package com.ddaeany0919.insightdeal.data

import com.ddaeany0919.insightdeal.models.ApiDeal
import com.ddaeany0919.insightdeal.models.ComparisonResponse
import com.ddaeany0919.insightdeal.network.HealthResponse
import kotlinx.coroutines.flow.Flow

interface DealsRepository {
    fun searchDeal(query: String, forceRefresh: Boolean = false): Flow<Resource<ComparisonResponse>>
    fun getPopularDeals(popularQueries: List<String> = defaultPopularQueries, maxResults: Int = 10): Flow<Resource<List<ApiDeal>>>
    fun checkServerHealth(): Flow<Resource<HealthResponse>>
    suspend fun clearCache(olderThanMinutes: Int = 30)
    fun getCacheStats(): CacheStats

    companion object {
        val defaultPopularQueries = listOf(
            "갤럭시 버즈", "에어팟", "아이패드", "다이슨",
            "닌텐도 스위치", "맥북", "삼성 모니터", "LG 그램"
        )
    }
}

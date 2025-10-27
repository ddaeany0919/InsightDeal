package com.ddaeany0919.insightdeal.data

import com.ddaeany0919.insightdeal.models.ApiDeal
import com.ddaeany0919.insightdeal.models.ComparisonResponse
import com.ddaeany0919.insightdeal.models.PlatformInfo
import com.ddaeany0919.insightdeal.network.HealthResponse
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * 🧪 목 딜 리포지토리 (테스트/오프라인 개발용)
 * 
 * 사용 상황:
 * - 단위 테스트
 * - 네트워크 없는 환경에서 개발
 * - 오프라인 모드 시연
 * - 백엔드 서버 없이 UI/UX 테스트
 */
class MockDealsRepository : DealsRepository {
    
    /**
     * 🔍 목 가격 비교 (다양한 시나리오 지원)
     */
    override fun searchDeal(query: String, forceRefresh: Boolean): Flow<Resource<ComparisonResponse>> = flow {
        // 로딩 시뮬레이션
        emit(Resource.Loading())
        delay(if (forceRefresh) 1500 else 800) // 새로고침은 조금 더 오래
        
        // 검색어별 다른 결과 제공
        val mockResponse = when {
            query.contains("갤럭시", ignoreCase = true) || query.contains("버즈", ignoreCase = true) -> {
                createMockResponse(
                    query = query,
                    traceId = "mock_trace_galaxy_${System.currentTimeMillis()}",
                    platforms = mapOf(
                        "coupang" to PlatformInfo(
                            price = 220000,
                            originalPrice = 350000,
                            discountRate = 37,
                            url = "https://coupang.com/mock",
                            shippingFee = 0,
                            seller = "쿠팡",
                            rating = 4.5f,
                            isAvailable = true
                        ),
                        "eleventh" to PlatformInfo(
                            price = 198000,
                            originalPrice = 350000,
                            discountRate = 43,
                            url = "https://11st.co.kr/mock",
                            shippingFee = 0,
                            seller = "11번가",
                            rating = 4.3f,
                            isAvailable = true
                        ),
                        "gmarket" to PlatformInfo(
                            price = 205000,
                            originalPrice = 350000,
                            discountRate = 41,
                            url = "https://gmarket.co.kr/mock",
                            shippingFee = 2500,
                            seller = "G마켓",
                            rating = 4.2f,
                            isAvailable = true
                        ),
                        "auction" to null // 올션에서는 없음
                    ),
                    successCount = 3
                )
            }
            
            query.contains("에어팟", ignoreCase = true) -> {
                createMockResponse(
                    query = query,
                    traceId = "mock_trace_airpods_${System.currentTimeMillis()}",
                    platforms = mapOf(
                        "coupang" to PlatformInfo(
                            price = 329000,
                            originalPrice = 359000,
                            discountRate = 8,
                            url = "https://coupang.com/mock",
                            shippingFee = 0,
                            seller = "쿠팡",
                            rating = 4.7f,
                            isAvailable = true
                        ),
                        "eleventh" to PlatformInfo(
                            price = 299000,
                            originalPrice = 359000,
                            discountRate = 17,
                            url = "https://11st.co.kr/mock",
                            shippingFee = 0,
                            seller = "11번가",
                            rating = 4.6f,
                            isAvailable = true
                        ),
                        "gmarket" to PlatformInfo(
                            price = 315000,
                            originalPrice = 359000,
                            discountRate = 12,
                            url = "https://gmarket.co.kr/mock",
                            shippingFee = 0,
                            seller = "G마켓",
                            rating = 4.5f,
                            isAvailable = true
                        ),
                        "auction" to PlatformInfo(
                            price = 319000,
                            originalPrice = 359000,
                            discountRate = 11,
                            url = "https://auction.co.kr/mock",
                            shippingFee = 2000,
                            seller = "옛션",
                            rating = 4.4f,
                            isAvailable = true
                        )
                    ),
                    successCount = 4
                )
            }
            
            query.contains("없음", ignoreCase = true) -> {
                // 검색 결과 없음 시나리오
                createMockResponse(
                    query = query,
                    traceId = "mock_trace_empty_${System.currentTimeMillis()}",
                    platforms = emptyMap(),
                    successCount = 0
                )
            }
            
            else -> {
                // 기본 목 데이터
                createMockResponse(
                    query = query,
                    traceId = "mock_trace_default_${System.currentTimeMillis()}",
                    platforms = mapOf(
                        "coupang" to PlatformInfo(
                            price = 125000,
                            originalPrice = 150000,
                            discountRate = 17,
                            url = "https://coupang.com/mock",
                            shippingFee = 0,
                            seller = "쿠팡",
                            rating = 4.2f,
                            isAvailable = true
                        ),
                        "eleventh" to PlatformInfo(
                            price = 119000,
                            originalPrice = 150000,
                            discountRate = 21,
                            url = "https://11st.co.kr/mock",
                            shippingFee = 2500,
                            seller = "11번가",
                            rating = 4.1f,
                            isAvailable = true
                        )
                    ),
                    successCount = 2
                )
            }
        }
        
        emit(Resource.Success(mockResponse))
    }
    
    /**
     * 📱 목 인기 딜 목록
     */
    override fun getPopularDeals(
        popularQueries: List<String>,
        maxResults: Int
    ): Flow<Resource<List<ApiDeal>>> = flow {
        emit(Resource.Loading())
        delay(1200) // 여러 API 호출 시뮬레이션
        
        val mockDeals = popularQueries.take(maxResults).mapIndexed { index, query ->
            val basePrice = 100000 + (index * 50000)
            val discountRate = 15 + (index * 5)
            val originalPrice = (basePrice * (100f / (100 - discountRate))).toInt()
            
            ApiDeal(
                id = "mock_deal_${index}",
                title = query,
                query = query,
                lowestPrice = basePrice,
                maxSaving = originalPrice - basePrice,
                averagePrice = basePrice + 20000,
                platforms = mapOf(
                    "coupang" to PlatformInfo(
                        price = basePrice + 10000,
                        originalPrice = originalPrice,
                        discountRate = discountRate - 5,
                        url = "https://coupang.com/mock",
                        shippingFee = 0,
                        seller = "쿠팡",
                        rating = 4.2f + (index * 0.1f),
                        isAvailable = true
                    ),
                    "eleventh" to PlatformInfo(
                        price = basePrice,
                        originalPrice = originalPrice,
                        discountRate = discountRate,
                        url = "https://11st.co.kr/mock",
                        shippingFee = if (index % 2 == 0) 0 else 2500,
                        seller = "11번가",
                        rating = 4.0f + (index * 0.1f),
                        isAvailable = true
                    )
                ),
                lowestPlatform = "eleventh",
                successCount = 2,
                responseTimeMs = 800 + (index * 200),
                updatedAt = "2025-10-27T${10 + index}:00:00Z"
            )
        }
        
        emit(Resource.Success(mockDeals))
    }
    
    /**
     * ⚡ 목 서버 상태
     */
    override fun checkServerHealth(): Flow<Resource<HealthResponse>> = flow {
        delay(300)
        val healthResponse = HealthResponse(
            status = "healthy",
            timestamp = "2025-10-27T15:00:00Z",
            scrapers = 4,
            metrics = mapOf(
                "total_requests" to 1250,
                "successful_requests" to 1180,
                "avg_response_time" to 1800,
                "cache_hits" to 420
            )
        )
        emit(Resource.Success(healthResponse))
    }
    
    /**
     * 🧹 목 캐시 정리
     */
    override suspend fun clearCache(olderThanMinutes: Int) {
        delay(100) // 정리 작업 시뮬레이션
    }
    
    /**
     * 📊 목 캐시 통계
     */
    override fun getCacheStats(): CacheStats {
        return CacheStats(
            totalEntries = 15,
            hitRate = 0.75f,
            averageResponseTime = 1200L,
            oldestEntryMinutes = 3,
            newestEntryMinutes = 0
        )
    }
    
    // === 유틸리티 메서드 ===
    
    private fun createMockResponse(
        query: String,
        traceId: String,
        platforms: Map<String, PlatformInfo?>,
        successCount: Int
    ): ComparisonResponse {
        val availablePlatforms = platforms.filterValues { it != null }
        val prices = availablePlatforms.values.mapNotNull { it?.price }
        
        val lowestPrice = prices.minOrNull()
        val averagePrice = if (prices.isNotEmpty()) prices.average().toInt() else null
        val maxSaving = if (prices.size >= 2) prices.maxOrNull()!! - prices.minOrNull()!! else 0
        
        val lowestPlatform = if (lowestPrice != null) {
            availablePlatforms.entries.find { it.value?.price == lowestPrice }?.key
        } else null
        
        return ComparisonResponse(
            traceId = traceId,
            query = query,
            platforms = platforms,
            lowestPlatform = lowestPlatform,
            lowestPrice = lowestPrice,
            maxSaving = maxSaving,
            averagePrice = averagePrice,
            successCount = successCount,
            totalPlatforms = 4,
            responseTimeMs = (800..2000).random(),
            updatedAt = "2025-10-27T15:${(10..59).random()}:${(10..59).random()}Z",
            errors = if (successCount == 0) listOf("모든 쇼핑몰에서 상품을 찾을 수 없습니다") else emptyList()
        )
    }
}
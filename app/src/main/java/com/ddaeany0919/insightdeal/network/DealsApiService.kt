package com.ddaeany0919.insightdeal.network

import com.ddaeany0919.insightdeal.models.ComparisonResponse
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Query

/**
 * 🌐 InsightDeal 실제 백엔드 API 서비스
 * FastAPI /api/* 엔드포인트와 정확히 연동
 */
interface DealsApiService {

    /**
     * 📊 4몰 가격 비교 - 핵심 기능!
     * 사용자가 가장 많이 사용할 API
     */
    @GET("/api/compare")
    suspend fun comparePrice(
        @Query("query") query: String
    ): Response<ComparisonResponse>

    /**
     * ❤️ 서버 덕스체크
     * 앱 시작 시 서버 상태 확인용
     */
    @GET("/api/health")
    suspend fun healthCheck(): Response<HealthResponse>
}

/**
 * ❤️ 헬스체크 응답 모델
 */
data class HealthResponse(
    val status: String,
    val timestamp: String,
    val scrapers: Int,
    val metrics: Map<String, Any>?
)

/**
 * 🌐 API 에러 응답 모델
 */
data class ApiErrorResponse(
    val detail: String,
    val status_code: Int? = null,
    val trace_id: String? = null
)
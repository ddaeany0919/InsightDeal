package com.example.insightdeal.model

/**
 * 향상된 딜 정보를 담는 데이터 클래스
 * 백엔드의 /api/deals/{id}/enhanced-info API 응답과 매핑됨
 */
data class EnhancedDealInfo(
    val dealId: Int,
    val postedTime: PostedTimeInfo?,
    val productDetail: ProductDetailInfo?,
    val enhancedAt: String
)

/**
 * 게시 시간 관련 정보
 */
data class PostedTimeInfo(
    val indexedAt: String?,          // ISO 날짜 문자열
    val formattedTime: String?,      // "10월 21일 14:30" 형식
    val timeAgo: String?             // "2시간 전" 형식
)

/**
 * 상품 상세 정보 (원본 게시물 크롤링 결과)
 */
data class ProductDetailInfo(
    val images: List<ProductImage>,
    val content: String,
    val aiAnalysis: AiAnalysisInfo?,
    val crawledAt: String,
    val sourceUrl: String?
)

/**
 * 상품 이미지 정보
 */
data class ProductImage(
    val url: String,
    val alt: String,
    val description: String?
)

/**
 * AI 분석 결과 정보
 */
data class AiAnalysisInfo(
    val productSummary: String,
    val keyFeatures: List<String>,
    val recommended: Boolean,
    val analysisConfidence: Double,
    val analyzedAt: String
)

/**
 * 향상된 정보 로딩 상태
 */
sealed class EnhancedInfoState {
    object Loading : EnhancedInfoState()
    data class Success(val info: EnhancedDealInfo) : EnhancedInfoState()
    data class Error(val message: String) : EnhancedInfoState()
}
package com.ddaeany0919.insightdeal.models

import com.google.gson.annotations.SerializedName

/**
 * 🌐 백엔드 API 응답 모델들
 * FastAPI /api/compare 엔드포인트와 정확히 매칭
 */

/**
 * 📊 4몰 가격 비교 응답 (백엔드 ComparisonResponse와 매칭)
 */
data class ComparisonResponse(
    @SerializedName("trace_id")
    val traceId: String,
    
    @SerializedName("query")
    val query: String,
    
    @SerializedName("platforms")
    val platforms: Map<String, PlatformInfo?>,
    
    @SerializedName("lowest_platform")
    val lowestPlatform: String?,
    
    @SerializedName("lowest_price")
    val lowestPrice: Int?,
    
    @SerializedName("max_saving")
    val maxSaving: Int,
    
    @SerializedName("average_price")
    val averagePrice: Int?,
    
    @SerializedName("success_count")
    val successCount: Int,
    
    @SerializedName("total_platforms")
    val totalPlatforms: Int,
    
    @SerializedName("response_time_ms")
    val responseTimeMs: Int,
    
    @SerializedName("updated_at")
    val updatedAt: String,
    
    @SerializedName("errors")
    val errors: List<String> = emptyList()
)

/**
 * 🏪 개별 쇼핑몰 정보 (백엔드 platforms 데이터와 매칭)
 */
data class PlatformInfo(
    @SerializedName("price")
    val price: Int,
    
    @SerializedName("original_price")
    val originalPrice: Int?,
    
    @SerializedName("discount_rate")
    val discountRate: Int?,
    
    @SerializedName("url")
    val url: String,
    
    @SerializedName("shipping_fee")
    val shippingFee: Int?,
    
    @SerializedName("seller")
    val seller: String?,
    
    @SerializedName("rating")
    val rating: Float?,
    
    @SerializedName("is_available")
    val isAvailable: Boolean = true
) {
    /**
     * 💰 최종 가격 (배송비 포함)
     */
    val finalPrice: Int
        get() = price + (shippingFee ?: 0)
    
    /**
     * 🔥 할인 여부
     */
    val hasDiscount: Boolean
        get() = originalPrice != null && originalPrice > price
    
    /**
     * 🚚 무료배송 여부
     */
    val hasFreeship: Boolean
        get() = shippingFee == null || shippingFee == 0
}

/**
 * 📱 홈 화면에서 사용할 통합 Deal 모델
 * 기존 DealItem을 대체하여 실제 API 데이터 사용
 */
data class ApiDeal(
    val id: String, // trace_id를 unique identifier로 사용
    val title: String,
    val query: String,
    val lowestPrice: Int?,
    val maxSaving: Int,
    val averagePrice: Int?,
    val platforms: Map<String, PlatformInfo?>,
    val lowestPlatform: String?,
    val successCount: Int,
    val responseTimeMs: Int,
    val updatedAt: String,
    val isBookmarked: Boolean = false
) {
    companion object {
        /**
         * 백엔드 ComparisonResponse를 ApiDeal로 변환
         */
        fun fromComparisonResponse(response: ComparisonResponse): ApiDeal {
            return ApiDeal(
                id = response.traceId,
                title = response.query, // 검색어를 제목으로 사용
                query = response.query,
                lowestPrice = response.lowestPrice,
                maxSaving = response.maxSaving,
                averagePrice = response.averagePrice,
                platforms = response.platforms,
                lowestPlatform = response.lowestPlatform,
                successCount = response.successCount,
                responseTimeMs = response.responseTimeMs,
                updatedAt = response.updatedAt
            )
        }
    }
    
    /**
     * 🔥 핫딜 여부 (20% 이상 절약 가능)
     */
    val isHotDeal: Boolean
        get() = averagePrice?.let { avg ->
            lowestPrice?.let { lowest ->
                val savingRate = ((avg - lowest).toFloat() / avg) * 100
                savingRate >= 20f
            } ?: false
        } ?: false
    
    /**
     * 🏆 최저가 플랫폼 정보
     */
    val bestDeal: PlatformInfo?
        get() = lowestPlatform?.let { platform ->
            platforms[platform]
        }
    
    /**
     * 📊 성공률 (사용자 신뢰도 지표)
     */
    val successRate: Float
        get() = successCount.toFloat() / 4f // 4개 플랫폼 기준
    
    /**
     * ⚡ 빠른 응답 여부 (2초 이내)
     */
    val isFastResponse: Boolean
        get() = responseTimeMs <= 2000
    
    /**
     * 💎 신뢰할 수 있는 딜 여부
     */
    val isReliableDeal: Boolean
        get() = successCount >= 2 && isFastResponse
}
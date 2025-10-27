package com.ddaeany0919.insightdeal.models

data class ApiDeal(
    val id: String,
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
        fun fromComparisonResponse(response: ComparisonResponse): ApiDeal {
            return ApiDeal(
                id = response.traceId,
                title = response.query,
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

    val imageUrl: String? get() = null
    val url: String get() = platforms.values.firstOrNull()?.url ?: ""
    val siteName: String get() = lowestPlatform ?: "Unknown"
    val originalPrice: Int? get() = platforms.values.firstOrNull()?.originalPrice
    val price: Int get() = lowestPrice ?: 0
}

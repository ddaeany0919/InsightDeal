package com.ddaeany0919.insightdeal.models

import com.google.gson.annotations.SerializedName

data class ComparisonResponse(
    @SerializedName("trace_id") val traceId: String,
    @SerializedName("query") val query: String,
    @SerializedName("platforms") val platforms: Map<String, PlatformInfo?>,
    @SerializedName("lowest_platform") val lowestPlatform: String? = null,
    @SerializedName("lowest_price") val lowestPrice: Int? = null,
    @SerializedName("max_saving") val maxSaving: Int = 0,
    @SerializedName("average_price") val averagePrice: Int? = null,
    @SerializedName("success_count") val successCount: Int,
    @SerializedName("total_platforms") val totalPlatforms: Int = 4,
    @SerializedName("response_time_ms") val responseTimeMs: Int = 1000,
    @SerializedName("updated_at") val updatedAt: String,
    @SerializedName("errors") val errors: List<String> = emptyList()
)

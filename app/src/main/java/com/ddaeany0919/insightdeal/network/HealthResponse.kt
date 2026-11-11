package com.ddaeany0919.insightdeal.network

import com.google.gson.annotations.SerializedName

data class HealthResponse(
    @SerializedName("status") val status: String = "ok",
    @SerializedName("timestamp") val timestamp: String = "2025-10-27T15:00:00Z",
    @SerializedName("version") val version: String = "1.0.0",
    @SerializedName("scrapers") val scrapers: Int = 4,
    @SerializedName("metrics") val metrics: Map<String, Any>? = mapOf(
        "total_requests" to 1250,
        "successful_requests" to 1180,
        "avg_response_time" to 1800,
        "cache_hits" to 420
    )
)

package com.ddaeany0919.insightdeal.network

data class HealthResponse(
    val status: String = "ok",
    val timestamp: String = "2025-10-27T15:00:00Z",
    val version: String = "1.0.0",
    val scrapers: Int = 4,
    val metrics: Map<String, Any>? = mapOf(
        "total_requests" to 1250,
        "successful_requests" to 1180,
        "avg_response_time" to 1800,
        "cache_hits" to 420
    )
)

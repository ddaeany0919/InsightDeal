package com.ddaeany0919.insightdeal.data

data class CacheStats(
    val totalEntries: Int,
    val hitRate: Float,
    val averageResponseTime: Long,
    val oldestEntryMinutes: Int,
    val newestEntryMinutes: Int,
    val memoryUsageBytes: Long = 0L,
    val lastCleanupTime: Long = System.currentTimeMillis()
) {
    fun getPerformanceGrade(): CachePerformance {
        return when {
            hitRate >= 0.8f && averageResponseTime <= 500L -> CachePerformance.EXCELLENT
            hitRate >= 0.6f && averageResponseTime <= 1000L -> CachePerformance.GOOD
            hitRate >= 0.4f && averageResponseTime <= 2000L -> CachePerformance.FAIR
            else -> CachePerformance.POOR
        }
    }

    fun getDisplaySummary(): String {
        return "캐시 적중률 ${(hitRate * 100).toInt()}%, " +
                "평균 응답시간 ${averageResponseTime}ms, " +
                "총 ${totalEntries}개 항목"
    }
}

enum class CachePerformance(val displayName: String) {
    EXCELLENT("우수"),
    GOOD("양호"),
    FAIR("보통"),
    POOR("개선필요")
}

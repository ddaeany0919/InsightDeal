package com.ddaeany0919.insightdeal.data

data class PriceComparison(
    val productTitle: String,
    val lowestPrice: Int?,
    val lowestPlatform: String?,
    val platforms: Map<String, PlatformPriceInfo>,
    val lastUpdated: Long = System.currentTimeMillis(),
    val comparisonTimeMs: Long = 0
)

data class PlatformPriceInfo(
    val price: Int,
    val url: String = "",
    val inStock: Boolean = true,
    val shippingFee: Int = 0,
    val lastChecked: Long = System.currentTimeMillis()
)

sealed class PriceComparisonState {
    object Idle : PriceComparisonState()
    object Loading : PriceComparisonState()
    data class Success(val comparison: PriceComparison) : PriceComparisonState()
    data class Error(val message: String, val canRetry: Boolean = true) : PriceComparisonState()
    data class Timeout(val partialResults: PriceComparison?) : PriceComparisonState()
}

interface PriceComparisonService {
    suspend fun comparePrices(productTitle: String): PriceComparison?
}

class MockPriceComparisonService : PriceComparisonService {
    override suspend fun comparePrices(productTitle: String): PriceComparison? {
        kotlinx.coroutines.delay(1500)
        return if (productTitle.isNotBlank()) {
            val basePrice = (10000..100000).random()
            val platforms = mapOf(
                "gmarket" to PlatformPriceInfo(basePrice + (0..5000).random()),
                "11st" to PlatformPriceInfo(basePrice + (0..8000).random()),
                "auction" to PlatformPriceInfo(basePrice + (0..3000).random()),
                "coupang" to PlatformPriceInfo(basePrice + (0..7000).random())
            )

            val lowestEntry = platforms.minByOrNull { it.value.price }

            PriceComparison(
                productTitle = productTitle,
                lowestPrice = lowestEntry?.value?.price,
                lowestPlatform = lowestEntry?.key,
                platforms = platforms,
                comparisonTimeMs = 1500
            )
        } else null
    }
}

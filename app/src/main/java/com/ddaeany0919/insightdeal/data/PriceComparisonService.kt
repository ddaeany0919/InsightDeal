package com.ddaeany0919.insightdeal.data

import android.util.Log
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 🚀 실제 가격 비교 서비스 구현체
 */
class RealPriceComparisonService : PriceComparisonService {

    companion object {
        private const val TAG = "PriceComparison"
        private const val TIMEOUT_MS = 3000L
    }

    private val _comparisonState = MutableStateFlow<PriceComparisonState>(PriceComparisonState.Idle)
    val comparisonState: StateFlow<PriceComparisonState> = _comparisonState.asStateFlow()

    override suspend fun comparePrices(productTitle: String): PriceComparison? {
        if (productTitle.isBlank()) return null

        _comparisonState.value = PriceComparisonState.Loading

        return try {
            val response = withTimeoutOrNull(TIMEOUT_MS) {
                // 실제 API 호출 로직
                delay(2000) // 시뮬레이션
                createMockComparison(productTitle)
            }

            when (response) {
                null -> {
                    Log.w(TAG, "⏱️ 비교 요청 타임아웃: $productTitle")
                    _comparisonState.value = PriceComparisonState.Timeout(null)
                    null
                }
                else -> {
                    _comparisonState.value = PriceComparisonState.Success(response)
                    response
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ 비교 요청 오류: $productTitle", e)
            _comparisonState.value = PriceComparisonState.Error("네트워크 오류가 발생했습니다")
            null
        }
    }

    private fun createMockComparison(title: String): PriceComparison {
        val basePrice = (10000..100000).random()
        val platforms = mapOf(
            "gmarket" to PlatformPriceInfo(basePrice + (0..5000).random()),
            "11st" to PlatformPriceInfo(basePrice + (0..8000).random()),
            "auction" to PlatformPriceInfo(basePrice + (0..3000).random()),
            "coupang" to PlatformPriceInfo(basePrice + (0..7000).random())
        )

        val lowestEntry = platforms.minByOrNull { it.value.price }

        return PriceComparison(
            productTitle = title,
            lowestPrice = lowestEntry?.value?.price,
            lowestPlatform = lowestEntry?.key,
            platforms = platforms,
            comparisonTimeMs = 2000
        )
    }
}

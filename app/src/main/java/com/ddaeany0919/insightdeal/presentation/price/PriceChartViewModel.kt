package com.ddaeany0919.insightdeal.presentation.price

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ddaeany0919.insightdeal.network.ApiClient
import com.ddaeany0919.insightdeal.network.ApiProduct
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import retrofit2.HttpException
import kotlin.math.pow
import kotlin.math.sqrt

class PriceChartViewModel : ViewModel() {

    companion object {
        private const val TAG = "PriceChartVM"
    }

    private val apiService = ApiClient.create()

    // UI 상태
    private val _product = MutableStateFlow<ProductData?>(null)
    val product: StateFlow<ProductData?> = _product.asStateFlow()

    private val _priceHistory = MutableStateFlow<List<PriceHistoryData>>(emptyList())
    val priceHistory: StateFlow<List<PriceHistoryData>> = _priceHistory.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    fun loadProductData(productId: Int) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                _isLoading.value = true
                _errorMessage.value = null
                Log.d(TAG, "📈 Loading product data for ID: $productId")

                // 상품 정보 로드
                val productResponse = apiService.getProduct(productId)
                if (productResponse.isSuccessful) {
                    val apiProduct = productResponse.body()
                    if (apiProduct != null) {
                        _product.value = ProductData(
                            id = apiProduct.id,
                            title = apiProduct.title,
                            brand = apiProduct.brand ?: "",
                            imageUrl = apiProduct.image_url ?: "",
                            currentPrice = apiProduct.current_price ?: 0,
                            originalPrice = apiProduct.original_price ?: 0,  // ✅ originalPrice 추가
                            lowestPrice = apiProduct.lowest_price ?: apiProduct.current_price ?: 0,
                            highestPrice = apiProduct.highest_price ?: apiProduct.current_price ?: 0,
                            targetPrice = apiProduct.target_price ?: 0,
                            priceChangePercent = calculatePriceChange(apiProduct),
                            discountRate = calculateDiscountRate(apiProduct)
                        )
                        Log.d(TAG, "✅ Product data loaded: ${apiProduct.title}")
                    }
                }

                // 가격 히스토리 로드 (더미 데이터로 임시 구현)
                val historyData = generateSamplePriceHistory(productId)
                _priceHistory.value = historyData
                Log.d(TAG, "✅ Price history loaded: ${historyData.size} entries")

            } catch (e: HttpException) {
                val error = "네트워크 오류: ${e.code()}"
                _errorMessage.value = error
                Log.e(TAG, "❌ HTTP Exception: ${e.message}")
            } catch (e: Exception) {
                val error = "데이터 로드 중 오류 발생: ${e.message}"
                _errorMessage.value = error
                Log.e(TAG, "❌ Unexpected error: ${e.message}")
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun updateTargetPrice(newTargetPrice: Int) {
        val currentProduct = _product.value ?: return
        viewModelScope.launch(Dispatchers.IO) {
            try {
                Log.d(TAG, "🎯 Updating target price: $newTargetPrice")
                val request = mapOf("target_price" to newTargetPrice)
                val response = apiService.updateTargetPrice(currentProduct.id, request)
                if (response.isSuccessful) {
                    _product.value = currentProduct.copy(targetPrice = newTargetPrice)
                    Log.d(TAG, "✅ Target price updated successfully")
                } else {
                    _errorMessage.value = "목표 가격 설정에 실패했습니다"
                    Log.e(TAG, "❌ Update target price failed: ${response.code()}")
                }
            } catch (e: Exception) {
                _errorMessage.value = "목표 가격 설정 중 오류 발생"
                Log.e(TAG, "❌ Update target price error: ${e.message}")
            }
        }
    }

    fun refreshData() {
        val currentProduct = _product.value
        if (currentProduct != null) {
            loadProductData(currentProduct.id)
        }
    }

    fun clearError() {
        _errorMessage.value = null
    }

    fun getBuyingTimingAdvice(): BuyingAdvice {  // ✅ BuyingAdvice 객체 반환
        val product = _product.value ?: return BuyingAdvice(
            timing = "데이터 로딩 중",
            reason = "상품 정보를 불러오는 중입니다...",
            savings = 0
        )
        val history = _priceHistory.value

        return when {
            // 목표가격 달성
            product.currentPrice <= product.targetPrice -> BuyingAdvice(
                timing = "지금 구매",
                reason = "목표 가격에 도달했습니다!",
                savings = product.targetPrice - product.currentPrice
            )

            // 역대 최저가
            product.currentPrice == product.lowestPrice -> BuyingAdvice(
                timing = "지금 구매",
                reason = "역대 최저가입니다! 높은 확률로 다시 오르지 않을 가격입니다.",
                savings = product.highestPrice - product.currentPrice
            )

            // 최저가 근사 (5% 이내)
            product.currentPrice <= product.lowestPrice * 1.05 -> BuyingAdvice(
                timing = "지금 구매",
                reason = "최저가에 근접한 좋은 가격입니다.",
                savings = (product.lowestPrice * 1.05 - product.currentPrice).toInt()
            )

            // 가격 하락 중
            history.size >= 3 && isRecentlyDecreasing(history) -> BuyingAdvice(
                timing = "조금 더 기다리기",
                reason = "가격이 하락 추세입니다. 더 떨어질 가능성이 있습니다.",
                savings = (product.currentPrice * 0.1).toInt() // 예상 절약
            )

            // 가격 상승 중
            history.size >= 3 && isRecentlyIncreasing(history) -> BuyingAdvice(
                timing = "지금 구매",
                reason = "가격이 상승 추세입니다. 더 오르기 전에 구매를 고려해보세요.",
                savings = 0
            )

            // 일반적인 경우
            else -> BuyingAdvice(
                timing = "기다려보기",
                reason = "현재 가격은 일반적인 수준입니다. 목표 가격까지 기다려보시겠어요?",
                savings = product.currentPrice - product.targetPrice
            )
        }
    }

    private fun isRecentlyDecreasing(history: List<PriceHistoryData>): Boolean {
        if (history.size < 3) return false
        val recent = history.takeLast(3)
        return recent[0].price > recent[1].price && recent[1].price > recent[2].price
    }

    private fun isRecentlyIncreasing(history: List<PriceHistoryData>): Boolean {
        if (history.size < 3) return false
        val recent = history.takeLast(3)
        return recent[0].price < recent[1].price && recent[1].price < recent[2].price
    }

    fun getFilteredHistory(days: Int): List<PriceHistoryData> {
        val fullHistory = _priceHistory.value
        return when (days) {
            7 -> fullHistory.takeLast(7 * 24)
            30 -> fullHistory.takeLast(30 * 24)
            else -> fullHistory
        }
    }

    fun getPriceStatistics(): PriceStatistics {
        val product = _product.value
        val history = _priceHistory.value

        if (product == null || history.isEmpty()) {
            return PriceStatistics(
                maxPrice = 0,
                minPrice = 0,
                averagePrice = 0,
                current = 0,
                lowest = 0,
                highest = 0,
                volatility = 0f
            )
        }

        val prices = history.map { it.price }
        val average = if (prices.isNotEmpty()) prices.average().toInt() else 0
        val variance = if (prices.isNotEmpty()) {
            prices.map { (it - average).toDouble().pow(2.0) }.average()
        } else 0.0
        val volatility = if (variance > 0) sqrt(variance).toFloat() else 0f

        return PriceStatistics(
            maxPrice = product.highestPrice,
            minPrice = product.lowestPrice,
            averagePrice = average,
            current = product.currentPrice,
            lowest = product.lowestPrice,
            highest = product.highestPrice,
            volatility = if (average > 0) (volatility / average * 100) else 0f
        )
    }

    private fun calculatePriceChange(apiProduct: ApiProduct): Float {
        val current = apiProduct.current_price ?: 0
        val history = _priceHistory.value
        if (history.size < 2) return 0f

        val previousPrice = history[history.size - 2].price
        return if (previousPrice > 0) {
            ((current - previousPrice).toFloat() / previousPrice) * 100
        } else {
            0f
        }
    }

    private fun calculateDiscountRate(apiProduct: ApiProduct): Int {
        val current = apiProduct.current_price ?: 0
        val original = apiProduct.original_price ?: 0
        return if (original > 0 && current > 0 && original > current) {
            ((original - current).toFloat() / original * 100).toInt()
        } else {
            0
        }
    }

    // ✅ 임시 샘플 데이터 생성 (실제로는 API에서 가져와야 함)
    private fun generateSamplePriceHistory(productId: Int): List<PriceHistoryData> {
        val basePrice = 100000
        val random = kotlin.random.Random(productId)

        return (1..30).map { day ->
            val priceVariation = random.nextInt(-10000, 15000)
            PriceHistoryData(
                price = basePrice + priceVariation,
                date = "2024-${String.format("%02d", 10)}-${String.format("%02d", day)}",
                siteName = listOf("쿠팡", "11번가", "G마켓", "옥션").random(),
                priceChange = if (day > 1) priceVariation else 0
            )
        }.reversed() // 최신순 정렬
    }

    // ✅ 데이터 모델들 (모든 필드 포함)
    data class ProductData(
        val id: Int,
        val title: String,
        val brand: String,
        val imageUrl: String,
        val currentPrice: Int,
        val originalPrice: Int,  // ✅ originalPrice 추가
        val lowestPrice: Int,
        val highestPrice: Int,
        val targetPrice: Int,
        val priceChangePercent: Float,
        val discountRate: Int
    )

    data class PriceHistoryData(
        val price: Int,
        val date: String,
        val siteName: String = "쿠팡",  // ✅ siteName 추가
        val priceChange: Int = 0       // ✅ priceChange 추가
    )

    data class PriceStatistics(
        val maxPrice: Int,      // ✅ maxPrice 추가
        val minPrice: Int,      // ✅ minPrice 추가
        val averagePrice: Int,  // ✅ averagePrice 추가
        val current: Int,
        val lowest: Int,
        val highest: Int,
        val volatility: Float
    )

    // ✅ BuyingAdvice 데이터 클래스 추가
    data class BuyingAdvice(
        val timing: String,    // "지금 구매" / "조금 더 기다리기" / "기다려보기"
        val reason: String,    // 추천 이유
        val savings: Int       // 예상 절약 금액
    )
}
package com.ddaeany0919.insightdeal

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
        """상품 정보 및 가격 히스토리 로드"""
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
                            lowestPrice = apiProduct.lowest_price ?: apiProduct.current_price ?: 0,
                            highestPrice = apiProduct.highest_price ?: apiProduct.current_price ?: 0,
                            targetPrice = apiProduct.target_price ?: 0,
                            priceChangePercent = calculatePriceChange(apiProduct),
                            discountRate = calculateDiscountRate(apiProduct)
                        )
                        Log.d(TAG, "✅ Product data loaded: ${apiProduct.title}")
                    }
                }

                // 가격 히스토리 로드
                val historyResponse = apiService.getProductPriceHistory(productId)
                if (historyResponse.isSuccessful) {
                    val historyList = historyResponse.body()?.map { historyItem ->
                        PriceHistoryData(
                            price = historyItem.price,
                            date = historyItem.tracked_at
                        )
                    } ?: emptyList()
                    _priceHistory.value = historyList
                    Log.d(TAG, "✅ Price history loaded: ${historyList.size} entries")
                } else {
                    Log.w(TAG, "⚠️ Price history load failed: ${historyResponse.code()}")
                    _priceHistory.value = emptyList()
                }

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
        """목표 가격 업데이트"""
        val currentProduct = _product.value ?: return
        viewModelScope.launch(Dispatchers.IO) {
            try {
                Log.d(TAG, "🎯 Updating target price: $newTargetPrice")
                val request = mapOf("target_price" to newTargetPrice)
                val response = apiService.updateTargetPrice(currentProduct.id, request)
                if (response.isSuccessful) {
                    // UI 즉시 반영 (낙관적 업데이트)
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
        """데이터 새로고침"""
        val currentProduct = _product.value
        if (currentProduct != null) {
            loadProductData(currentProduct.id)
        }
    }

    fun clearError() {
        """에러 메시지 클리어"""
        _errorMessage.value = null
    }

    fun getBuyingTimingAdvice(): String {
        """구매 타이밍 조언 생성"""
        val product = _product.value ?: return "데이터를 불러오는 중..."
        val history = _priceHistory.value

        return when {
            // 목표가격 달성
            product.currentPrice <= product.targetPrice ->
                "🎉 목표 가격에 도달했습니다! 지금이 구매 타이밍입니다."

            // 역대 최저가
            product.currentPrice == product.lowestPrice ->
                "📉 역대 최저가입니다! 높은 확률로 다시 오르지 않을 가격입니다."

            // 최저가 근사 (5% 이내)
            product.currentPrice <= product.lowestPrice * 1.05 ->
                "👍 최저가에 근접한 좋은 가격입니다. 구매를 고려해보세요."

            // 가격 하락 중
            history.size >= 3 && isRecentlyDecreasing(history) ->
                "📉 가격이 하락 추세입니다. 조금 더 기다려보시는 것을 추천합니다."

            // 가격 상승 중
            history.size >= 3 && isRecentlyIncreasing(history) ->
                "📈 가격이 상승 추세입니다. 더 오르기 전에 구매를 고려해보세요."

            // 일반적인 경우
            else -> "🤔 현재 가격은 일반적인 수준입니다. 목표 가격까지 기다려보시겠어요?"
        }
    }

    private fun isRecentlyDecreasing(history: List<PriceHistoryData>): Boolean {
        """최근 3일간 가격 하락 추세 여부 판단"""
        if (history.size < 3) return false
        val recent = history.takeLast(3)
        return recent[0].price > recent[1].price && recent[1].price > recent[2].price
    }

    private fun isRecentlyIncreasing(history: List<PriceHistoryData>): Boolean {
        """최근 3일간 가격 상승 추세 여부 판단"""
        if (history.size < 3) return false
        val recent = history.takeLast(3)
        return recent[0].price < recent[1].price && recent[1].price < recent[2].price
    }

    fun getFilteredHistory(days: Int): List<PriceHistoryData> {
        """기간별 가격 히스토리 필터링"""
        val fullHistory = _priceHistory.value
        return when (days) {
            7 -> fullHistory.takeLast(7 * 24) // 7일 (1시간마다 체크 가정)
            30 -> fullHistory.takeLast(30 * 24) // 30일
            else -> fullHistory // 전체
        }
    }

    fun getPriceStatistics(): PriceStatistics {
        """가격 통계 정보 생성"""
        val product = _product.value
        val history = _priceHistory.value

        if (product == null || history.isEmpty()) {
            return PriceStatistics(
                current = 0,
                lowest = 0,
                highest = 0,
                average = 0,
                volatility = 0f
            )
        }

        val prices = history.map { it.price }
        val average = prices.average().toInt()

        // 변동성 계산 (표준편차)
        val variance = prices.map { (it - average).toDouble().pow(2.0) }.average()
        val volatility = sqrt(variance).toFloat()

        return PriceStatistics(
            current = product.currentPrice,
            lowest = product.lowestPrice,
            highest = product.highestPrice,
            average = average,
            volatility = (volatility / average * 100) // 변동성 백분율
        )
    }

    private fun calculatePriceChange(apiProduct: ApiProduct): Float {
        """가격 변동률 계산"""
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
        """할인율 계산"""
        val current = apiProduct.current_price ?: 0
        val original = apiProduct.original_price ?: 0
        return if (original > 0 && current > 0 && original > current) {
            ((original - current).toFloat() / original * 100).toInt()
        } else {
            0
        }
    }

    // 데이터 모델들
    data class ProductData(
        val id: Int,
        val title: String,
        val brand: String,
        val imageUrl: String,
        val currentPrice: Int,
        val lowestPrice: Int,
        val highestPrice: Int,
        val targetPrice: Int,
        val priceChangePercent: Float,
        val discountRate: Int
    )

    data class PriceHistoryData(
        val price: Int,
        val date: String
    )

    data class PriceStatistics(
        val current: Int,
        val lowest: Int,
        val highest: Int,
        val average: Int,
        val volatility: Float // 가격 변동성 (백분율)
    )
}

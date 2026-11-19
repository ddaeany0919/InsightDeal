package com.ddaeany0919.insightdeal.presentation.price

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ddaeany0919.insightdeal.network.ApiProduct
import com.ddaeany0919.insightdeal.network.ApiService
import com.ddaeany0919.insightdeal.network.NetworkModule
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import retrofit2.HttpException
import kotlin.math.pow
import kotlin.math.sqrt

class PriceChartViewModel(
    private val apiService: ApiService = NetworkModule.createService<ApiService>(),
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val coroutineScopeOverride: CoroutineScope? = null
) : ViewModel() {

    companion object {
        const val INSUFFICIENT_HISTORY_MESSAGE = "아직 비교할 가격 이력이 충분하지 않아요"
    }

    private val vmScope: CoroutineScope
        get() = coroutineScopeOverride ?: viewModelScope

    private val _product = MutableStateFlow<ProductData?>(null)
    val product: StateFlow<ProductData?> = _product.asStateFlow()

    private val _priceHistory = MutableStateFlow<List<PriceHistoryData>>(emptyList())
    val priceHistory: StateFlow<List<PriceHistoryData>> = _priceHistory.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    private var loadJob: Job? = null

    fun loadProductData(productId: Int) {
        loadJob?.cancel()
        loadJob = vmScope.launch {
            _isLoading.value = true
            _errorMessage.value = null

            try {
                // 1. 실제 가격 이력 API 호출 (/api/product/{productId}/history)
                val historyResult = withContext(ioDispatcher) {
                    runCatching { apiService.getProductPriceHistory(productId) }
                }

                val parsedHistory = historyResult.getOrNull()?.let { response ->
                    if (response.isSuccessful) {
                        val rawPoints = response.body()
                            .orEmpty()
                            .filter { it.price > 0 }
                            .sortedBy { it.tracked_at }

                        rawPoints.mapIndexed { index, point ->
                            val prevPrice = rawPoints.getOrNull(index - 1)?.price
                            val change = if (prevPrice != null && prevPrice > 0) point.price - prevPrice else 0
                            PriceHistoryData(
                                price = point.price,
                                date = point.tracked_at,
                                siteName = "",
                                priceChange = change
                            )
                        }
                    } else {
                        if (response.code() >= 500) {
                            _errorMessage.value = "가격 이력을 불러오지 못했어요. 잠시 후 다시 시도해 주세요."
                        }
                        emptyList()
                    }
                } ?: run {
                    val err = historyResult.exceptionOrNull()
                    if (err is CancellationException) throw err
                    if (err is HttpException) {
                        _errorMessage.value = "네트워크 오류: ${err.code()}"
                    } else {
                        _errorMessage.value = "데이터 로드 중 오류 발생: ${err?.message ?: "알 수 없는 오류"}"
                    }
                    emptyList()
                }

                // 이력 먼저 확정 (calculatePriceChange가 빈 이력을 읽던 순서 버그 해결)
                _priceHistory.value = parsedHistory

                // 2. 상품 정보 API 시도 (없으면 실이력 기반 요약 또는 null 유지)
                val productResp = withContext(ioDispatcher) {
                    runCatching { apiService.getProduct(productId) }.getOrNull()
                }
                val apiProduct = productResp?.takeIf { it.isSuccessful }?.body()
                if (apiProduct != null) {
                    _product.value = ProductData(
                        id = apiProduct.id,
                        title = apiProduct.title,
                        brand = apiProduct.brand ?: "",
                        imageUrl = apiProduct.image_url ?: "",
                        currentPrice = apiProduct.current_price ?: parsedHistory.lastOrNull()?.price ?: 0,
                        originalPrice = apiProduct.original_price ?: 0,
                        lowestPrice = apiProduct.lowest_price ?: parsedHistory.minOfOrNull { it.price } ?: apiProduct.current_price ?: 0,
                        highestPrice = apiProduct.highest_price ?: parsedHistory.maxOfOrNull { it.price } ?: apiProduct.current_price ?: 0,
                        targetPrice = apiProduct.target_price ?: 0,
                        priceChangePercent = calculatePriceChange(apiProduct.current_price ?: parsedHistory.lastOrNull()?.price ?: 0, parsedHistory),
                        discountRate = calculateDiscountRate(apiProduct)
                    )
                } else if (parsedHistory.isNotEmpty()) {
                    val current = parsedHistory.last().price
                    _product.value = ProductData(
                        id = productId,
                        title = "상품 #$productId",
                        brand = "",
                        imageUrl = "",
                        currentPrice = current,
                        originalPrice = 0,
                        lowestPrice = parsedHistory.minOf { it.price },
                        highestPrice = parsedHistory.maxOf { it.price },
                        targetPrice = 0,
                        priceChangePercent = calculatePriceChange(current, parsedHistory),
                        discountRate = 0
                    )
                } else {
                    _product.value = null
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: HttpException) {
                _priceHistory.value = emptyList()
                _errorMessage.value = "네트워크 오류: ${e.code()}"
            } catch (e: Exception) {
                _priceHistory.value = emptyList()
                _errorMessage.value = "데이터 로드 중 오류 발생: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun updateTargetPrice(newTargetPrice: Int) {
        val currentProduct = _product.value ?: return
        vmScope.launch {
            try {
                val request = mapOf("target_price" to newTargetPrice)
                val response = withContext(ioDispatcher) {
                    apiService.updateTargetPrice(currentProduct.id, request)
                }
                if (response.isSuccessful) {
                    _product.value = currentProduct.copy(targetPrice = newTargetPrice)
                } else {
                    _errorMessage.value = "목표 가격 설정에 실패했습니다"
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                _errorMessage.value = "목표 가격 설정 중 오류 발생"
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

    fun getBuyingTimingAdvice(): BuyingAdvice {
        val product = _product.value
        val history = _priceHistory.value

        if (history.size < 2) {
            return BuyingAdvice(
                timing = "데이터 부족",
                reason = INSUFFICIENT_HISTORY_MESSAGE,
                savings = 0
            )
        }

        if (product == null) {
            return BuyingAdvice(
                timing = "데이터 로딩 중",
                reason = "상품 정보를 불러오는 중입니다...",
                savings = 0
            )
        }

        return when {
            product.targetPrice > 0 && product.currentPrice in 1..product.targetPrice -> BuyingAdvice(
                timing = "지금 구매",
                reason = "목표 가격에 도달했습니다!",
                savings = product.targetPrice - product.currentPrice
            )

            product.lowestPrice > 0 && product.currentPrice == product.lowestPrice -> BuyingAdvice(
                timing = "지금 구매",
                reason = "역대 최저가입니다! 높은 확률로 다시 오르지 않을 가격입니다.",
                savings = (product.highestPrice - product.currentPrice).coerceAtLeast(0)
            )

            product.lowestPrice > 0 && product.currentPrice <= (product.lowestPrice * 1.05).toInt() -> BuyingAdvice(
                timing = "지금 구매",
                reason = "최저가에 근접한 좋은 가격입니다.",
                savings = ((product.lowestPrice * 1.05).toInt() - product.currentPrice).coerceAtLeast(0)
            )

            history.size >= 3 && isRecentlyDecreasing(history) -> BuyingAdvice(
                timing = "조금 더 기다리기",
                reason = "가격이 하락 추세입니다. 더 떨어질 가능성이 있습니다.",
                savings = (product.currentPrice * 0.1).toInt()
            )

            history.size >= 3 && isRecentlyIncreasing(history) -> BuyingAdvice(
                timing = "지금 구매",
                reason = "가격이 상승 추세입니다. 더 오르기 전에 구매를 고려해보세요.",
                savings = 0
            )

            else -> BuyingAdvice(
                timing = "기다려보기",
                reason = "현재 가격은 일반적인 수준입니다. 목표 가격까지 기다려보시겠어요?",
                savings = if (product.targetPrice > 0) (product.currentPrice - product.targetPrice).coerceAtLeast(0) else 0
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
            7 -> fullHistory.takeLast(7)
            30 -> fullHistory.takeLast(30)
            90 -> fullHistory.takeLast(90)
            180 -> fullHistory.takeLast(180)
            365 -> fullHistory.takeLast(365)
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

    private fun calculatePriceChange(current: Int, history: List<PriceHistoryData>): Float {
        if (history.size < 2 || current <= 0) return 0f

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

    data class ProductData(
        val id: Int,
        val title: String,
        val brand: String,
        val imageUrl: String,
        val currentPrice: Int,
        val originalPrice: Int,
        val lowestPrice: Int,
        val highestPrice: Int,
        val targetPrice: Int,
        val priceChangePercent: Float,
        val discountRate: Int
    )

    data class PriceHistoryData(
        val price: Int,
        val date: String,
        val siteName: String = "",
        val priceChange: Int = 0
    )

    data class PriceStatistics(
        val maxPrice: Int,
        val minPrice: Int,
        val averagePrice: Int,
        val current: Int,
        val lowest: Int,
        val highest: Int,
        val volatility: Float
    )

    data class BuyingAdvice(
        val timing: String,
        val reason: String,
        val savings: Int
    )
}

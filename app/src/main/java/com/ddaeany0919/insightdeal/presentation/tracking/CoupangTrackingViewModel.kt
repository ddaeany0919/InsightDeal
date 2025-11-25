package com.ddaeany0919.insightdeal.presentation.tracking

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

class CoupangTrackingViewModel : ViewModel() {

    companion object {
        private const val TAG = "CoupangTrackingVM"
    }

    private val apiService = ApiClient.create()

    // UI 상태
    private val _products = MutableStateFlow<List<ProductData>>(emptyList())
    val products: StateFlow<List<ProductData>> = _products.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    init {
        loadProducts()
    }

    fun loadProducts() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                _isLoading.value = true
                _errorMessage.value = null
                Log.d(TAG, "🔄 Loading user products...")
                val response = apiService.getUserProducts("anonymous")
                if (response.isSuccessful) {
                    val productList: List<ProductData> = response.body()?.map { apiProduct ->
                        ProductData(
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
                    } ?: emptyList()
                    _products.value = productList
                    Log.d(TAG, "✅ Loaded ${productList.size} products")
                } else {
                    val error = "상품 목록을 불러오는데 실패했습니다: ${response.code()}"
                    _errorMessage.value = error
                    Log.e(TAG, "❌ Load products failed: ${response.code()}")
                }
            } catch (e: HttpException) {
                val error = "네트워크 오류: ${e.code()}"
                _errorMessage.value = error
                Log.e(TAG, "❌ HTTP Exception: ${e.message}")
            } catch (e: Exception) {
                val error = "알 수 없는 오류: ${e.message}"
                _errorMessage.value = error
                Log.e(TAG, "❌ Unexpected error: ${e.message}")
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun addProduct(url: String, targetPrice: Int) {
        if (!url.contains("coupang.com")) {
            _errorMessage.value = "올바른 쿠팡 URL이 아닙니다"
            return
        }
        viewModelScope.launch(Dispatchers.IO) {
            try {
                _isLoading.value = true
                _errorMessage.value = null
                Log.d(TAG, "🛒 Adding new product: ${url.take(50)}...")
                val request = mapOf(
                    "url" to url,
                    "target_price" to targetPrice,
                    "user_id" to "anonymous"
                )
                val response = apiService.addProduct(request)
                if (response.isSuccessful) {
                    Log.d(TAG, "✅ Product added successfully")
                    loadProducts()
                } else {
                    val error = "상품 추가에 실패했습니다: ${response.code()}"
                    _errorMessage.value = error
                    Log.e(TAG, "❌ Add product failed: ${response.code()}")
                }
            } catch (e: HttpException) {
                val error = "네트워크 오류: ${e.code()}"
                _errorMessage.value = error
                Log.e(TAG, "❌ HTTP Exception: ${e.message}")
            } catch (e: Exception) {
                val error = "상품 추가 중 오류 발생: ${e.message}"
                _errorMessage.value = error
                Log.e(TAG, "❌ Unexpected error: ${e.message}")
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun clearError() {
        _errorMessage.value = null
    }

    private fun calculatePriceChange(apiProduct: ApiProduct): Float {
        val current = apiProduct.current_price ?: 0
        val lowest = apiProduct.lowest_price ?: current

        return if (lowest > 0 && current != lowest) {
            ((current - lowest).toFloat() / lowest) * 100
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
}

// UI에서 사용하는 데이터 모델
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
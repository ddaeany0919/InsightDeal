package com.yourpackage.insightdeal

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
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
    
    private val apiService = ApiService.create()
    
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
        """사용자가 추가한 쿠팡 상품 목록 로드"""
        viewModelScope.launch {
            try {
                _isLoading.value = true
                _errorMessage.value = null
                
                Log.d(TAG, "🔄 Loading user products...")
                
                val response = apiService.getUserProducts("anonymous") // 추후 실제 user_id
                
                if (response.isSuccessful) {
                    val productList = response.body()?.map { apiProduct ->
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
        """새 쿠팡 상품 추가"""
        if (!url.contains("coupang.com")) {
            _errorMessage.value = "올바른 쿠팡 URL이 아닙니다"
            return
        }
        
        viewModelScope.launch {
            try {
                _isLoading.value = true
                _errorMessage.value = null
                
                Log.d(TAG, "🛒 Adding new product: ${url.take(50)}...")
                
                val request = mapOf(
                    "url" to url,
                    "target_price" to targetPrice,
                    "user_id" to "anonymous" // 추후 실제 user_id
                )
                
                val response = apiService.addProduct(request)
                
                if (response.isSuccessful) {
                    Log.d(TAG, "✅ Product added successfully")
                    
                    // 목록 새로고침
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
    
    fun updateTargetPrice(productId: Int, newTargetPrice: Int) {
        """목표 가격 업데이트"""
        viewModelScope.launch {
            try {
                Log.d(TAG, "🎯 Updating target price for product $productId: $newTargetPrice")
                
                val request = mapOf("target_price" to newTargetPrice)
                val response = apiService.updateTargetPrice(productId, request)
                
                if (response.isSuccessful) {
                    Log.d(TAG, "✅ Target price updated successfully")
                    
                    // UI 상태 즉시 반영
                    _products.value = _products.value.map { product ->
                        if (product.id == productId) {
                            product.copy(targetPrice = newTargetPrice)
                        } else {
                            product
                        }
                    }
                    
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
    
    fun deleteProduct(productId: Int) {
        """상품 추적 삭제"""
        viewModelScope.launch {
            try {
                Log.d(TAG, "🗑️ Deleting product $productId")
                
                val response = apiService.deleteProduct(productId)
                
                if (response.isSuccessful) {
                    Log.d(TAG, "✅ Product deleted successfully")
                    
                    // UI에서 즉시 제거
                    _products.value = _products.value.filter { it.id != productId }
                    
                } else {
                    _errorMessage.value = "상품 삭제에 실패했습니다"
                    Log.e(TAG, "❌ Delete product failed: ${response.code()}")
                }
                
            } catch (e: Exception) {
                _errorMessage.value = "상품 삭제 중 오류 발생"
                Log.e(TAG, "❌ Delete product error: ${e.message}")
            }
        }
    }
    
    fun clearError() {
        """에러 메시지 클리어"""
        _errorMessage.value = null
    }
    
    private fun calculatePriceChange(apiProduct: ApiProduct): Float {
        """가격 변동률 계산 (임시 로직 - 추후 히스토리 기반으로 개선)"""
        val current = apiProduct.current_price ?: 0
        val lowest = apiProduct.lowest_price ?: current
        
        return if (lowest > 0 && current != lowest) {
            ((current - lowest).toFloat() / lowest) * 100
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
}

// API 응답 데이터 모델
data class ApiProduct(
    val id: Int,
    val title: String,
    val brand: String?,
    val image_url: String?,
    val current_price: Int?,
    val original_price: Int?,
    val lowest_price: Int?,
    val highest_price: Int?,
    val target_price: Int?,
    val url: String
)
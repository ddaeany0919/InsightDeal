package com.ddaeany0919.insightdeal.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ddaeany0919.insightdeal.models.DealItem
import com.ddaeany0919.insightdeal.models.PriceHistoryPoint
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import com.ddaeany0919.insightdeal.network.ApiService

data class DealDetailUiState(
    val isLoading: Boolean = true,
    val deal: DealItem? = null,
    val priceHistory: List<PriceHistoryPoint> = emptyList(),
    val error: String? = null,
    val isRecordLow: Boolean = false
)

class DealDetailViewModel : ViewModel() {
    private val _uiState = MutableStateFlow(DealDetailUiState())
    val uiState: StateFlow<DealDetailUiState> = _uiState.asStateFlow()

    private val apiService = com.ddaeany0919.insightdeal.network.NetworkModule.createService<ApiService>()

    fun loadDealData(dealId: Int) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            try {
                // 병렬로 두 API 호출 (단순성을 위해 순차적으로 처리하지만 coroutine async를 사용해도 무방)
                val dealResponse = apiService.getEnhancedDealInfo(dealId)
                val historyResponse = apiService.getDealPriceHistory(dealId)

                if (dealResponse.isSuccessful) {
                    val deal = dealResponse.body()
                    val history = if (historyResponse.isSuccessful) historyResponse.body() ?: emptyList() else emptyList()
                    
                    var isLow = false
                    if (history.isNotEmpty() && deal != null) {
                        val currentPrice = deal.price
                        val minPrice = history.minOfOrNull { it.price } ?: Int.MAX_VALUE
                        if (currentPrice > 0 && currentPrice <= minPrice) {
                            isLow = true
                        }
                    }

                    _uiState.value = DealDetailUiState(
                        isLoading = false,
                        deal = deal,
                        priceHistory = history,
                        error = null,
                        isRecordLow = isLow
                    )
                } else {
                    _uiState.value = DealDetailUiState(
                        isLoading = false,
                        error = "상세 정보를 불러오는 데 실패했습니다. (코드: ${dealResponse.code()})"
                    )
                }
            } catch (e: Exception) {
                _uiState.value = DealDetailUiState(
                    isLoading = false,
                    error = "네트워크 오류가 발생했습니다: ${e.message}"
                )
            }
        }
    }
}

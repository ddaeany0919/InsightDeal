package com.ddaeany0919.insightdeal.presentation

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ddaeany0919.insightdeal.models.DealItem
import com.ddaeany0919.insightdeal.models.PriceHistoryPoint
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import com.ddaeany0919.insightdeal.network.ApiService
import com.ddaeany0919.insightdeal.network.ApiDealComment
import com.ddaeany0919.insightdeal.network.ApiDealVotes
import com.ddaeany0919.insightdeal.network.ApiCommentCreate
import com.ddaeany0919.insightdeal.network.ApiVoteCreate
import java.util.UUID

@Immutable
data class DealDetailUiState(
    val isLoading: Boolean = true,
    val deal: DealItem? = null,
    val priceHistory: List<PriceHistoryPoint> = emptyList(),
    val error: String? = null,
    val isRecordLow: Boolean = false,
    val comments: List<ApiDealComment> = emptyList(),
    val votes: ApiDealVotes? = null
)

class DealDetailViewModel : ViewModel() {
    private val _uiState = MutableStateFlow(DealDetailUiState())
    val uiState: StateFlow<DealDetailUiState> = _uiState.asStateFlow()

    private val _selectedPeriod = MutableStateFlow("7d")
    val selectedPeriod: StateFlow<String> = _selectedPeriod.asStateFlow()

    private val apiService = com.ddaeany0919.insightdeal.network.NetworkModule.createService<ApiService>()
    
    private var currentDealId: Int? = null

    fun setPeriod(period: String) {
        if (_selectedPeriod.value != period) {
            _selectedPeriod.value = period
            currentDealId?.let { loadHistoryData(it, period) }
        }
    }

    private fun loadHistoryData(dealId: Int, period: String) {
        viewModelScope.launch {
            try {
                val historyResponse = apiService.getDealPriceHistory(dealId, period)
                if (historyResponse.isSuccessful) {
                    val history = historyResponse.body() ?: emptyList()
                    val deal = _uiState.value.deal
                    
                    var isLow = false
                    if (history.isNotEmpty() && deal != null) {
                        val currentPrice = deal.price
                        val minPrice = history.minOfOrNull { it.price } ?: Int.MAX_VALUE
                        if (currentPrice > 0 && currentPrice <= minPrice) {
                            isLow = true
                        }
                    }

                    _uiState.value = _uiState.value.copy(
                        priceHistory = history,
                        isRecordLow = isLow
                    )
                }
            } catch (e: Exception) {
                // 오류 처리 생략 (기존 상태 유지)
            }
        }
    }

    fun loadDealData(dealId: Int) {
        currentDealId = dealId
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            try {
                coroutineScope {
                    val requestedPeriod = if (_selectedPeriod.value == "7d") "7d_auto" else _selectedPeriod.value
                    val dealDeferred = async { apiService.getEnhancedDealInfo(dealId) }
                    val historyDeferred = async { apiService.getDealPriceHistory(dealId, requestedPeriod) }
                    val commentsDeferred = async {
                        try { apiService.getDealComments(dealId) } catch (e: Exception) { null }
                    }
                    val votesDeferred = async {
                        try { apiService.getDealVotes(dealId, getDeviceId()) } catch (e: Exception) { null }
                    }

                    val dealResponse = dealDeferred.await()
                    val historyResponse = historyDeferred.await()
                    val commentsResponse = commentsDeferred.await()
                    val votesResponse = votesDeferred.await()

                    if (dealResponse.isSuccessful) {
                        val deal = dealResponse.body()
                        var history = if (historyResponse.isSuccessful) historyResponse.body() ?: emptyList() else emptyList()
                        val comments = if (commentsResponse?.isSuccessful == true) commentsResponse.body() ?: emptyList() else emptyList()
                        val votes = if (votesResponse?.isSuccessful == true) votesResponse.body() else null

                        // 백엔드 1-RTT 7d_auto 폴백으로 3개월(YY.MM.DD 포맷) 데이터가 반환된 경우 탭 상태도 즉시 3m로 동기화
                        if (_selectedPeriod.value == "7d" && history.size >= 2 && history.any { it.date.matches(Regex("^\\d{2}\\.\\d{2}\\.\\d{2}$")) }) {
                            _selectedPeriod.value = "3m"
                        } else if (history.size <= 1 && _selectedPeriod.value == "7d") {
                            val fallbackResponse = try { apiService.getDealPriceHistory(dealId, "3m") } catch (e: Exception) { null }
                            val fallbackHistory = if (fallbackResponse?.isSuccessful == true) fallbackResponse.body() ?: emptyList() else emptyList()
                            if (fallbackHistory.size >= 2) {
                                history = fallbackHistory
                                _selectedPeriod.value = "3m"
                            }
                        }

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
                            isRecordLow = isLow,
                            comments = comments,
                            votes = votes
                        )
                    } else {
                        _uiState.value = DealDetailUiState(
                            isLoading = false,
                            error = "상세 정보를 불러오는 데 실패했습니다. (코드: ${dealResponse.code()})"
                        )
                    }
                }
            } catch (e: Exception) {
                _uiState.value = DealDetailUiState(
                    isLoading = false,
                    error = "네트워크 오류가 발생했습니다: ${e.message}"
                )
            }
        }
    }
    
    // --- 익명 디바이스 ID (투표/댓글용) ---
    private var cachedDeviceId: String? = null
    
    private fun getDeviceId(): String {
        return cachedDeviceId ?: run {
            val newId = "anon_${UUID.randomUUID().toString().take(8)}"
            cachedDeviceId = newId
            newId
        }
    }

    fun addComment(content: String) {
        val dealId = currentDealId ?: return
        if (content.isBlank()) return
        
        viewModelScope.launch {
            try {
                val req = ApiCommentCreate(
                    user_id = getDeviceId(),
                    content = content
                )
                val response = apiService.addDealComment(dealId, req)
                if (response.isSuccessful) {
                    val newComment = response.body()
                    if (newComment != null) {
                        _uiState.value = _uiState.value.copy(
                            comments = listOf(newComment) + _uiState.value.comments
                        )
                    }
                }
            } catch (e: Exception) {
                // 에러 처리
            }
        }
    }

    fun vote(voteType: String) {
        val dealId = currentDealId ?: return
        
        viewModelScope.launch {
            try {
                val req = ApiVoteCreate(
                    user_id = getDeviceId(),
                    vote_type = voteType
                )
                val response = apiService.addDealVote(dealId, req)
                if (response.isSuccessful) {
                    val newVotes = response.body()
                    if (newVotes != null) {
                        _uiState.value = _uiState.value.copy(votes = newVotes)
                    }
                }
            } catch (e: Exception) {
                // 에러 처리
            }
        }
    }
}

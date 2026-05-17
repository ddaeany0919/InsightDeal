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
import com.ddaeany0919.insightdeal.network.ApiDealComment
import com.ddaeany0919.insightdeal.network.ApiDealVotes
import com.ddaeany0919.insightdeal.network.ApiCommentCreate
import com.ddaeany0919.insightdeal.network.ApiVoteCreate
import java.util.UUID
import android.content.Context
import android.content.SharedPreferences

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
                // 병렬로 두 API 호출 (단순성을 위해 순차적으로 처리하지만 coroutine async를 사용해도 무방)
                val dealResponse = apiService.getEnhancedDealInfo(dealId)
                val historyResponse = apiService.getDealPriceHistory(dealId, _selectedPeriod.value)
                
                // --- 소비 참견 거지방 API 병렬 호출 ---
                val commentsResponse = try { apiService.getDealComments(dealId) } catch (e: Exception) { null }
                val votesResponse = try { apiService.getDealVotes(dealId, getDeviceId()) } catch (e: Exception) { null }

                if (dealResponse.isSuccessful) {
                    val deal = dealResponse.body()
                    val history = if (historyResponse.isSuccessful) historyResponse.body() ?: emptyList() else emptyList()
                    val comments = if (commentsResponse?.isSuccessful == true) commentsResponse.body() ?: emptyList() else emptyList()
                    val votes = if (votesResponse?.isSuccessful == true) votesResponse.body() else null
                    
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
            // 실제 구현에서는 Context/SharedPreferences 등을 통해 고유 ID 가져오거나 생성
            // 여기서는 임시로 랜덤 생성 (앱 재시작시 초기화됨)
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

package com.example.insightdeal.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import android.util.Log
import com.example.insightdeal.model.DealDetail
import com.example.insightdeal.model.DealItem
import com.example.insightdeal.model.PriceHistoryItem
import com.example.insightdeal.network.ApiClient
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

sealed interface DealsUiState {
    data class Success(
        val allDeals: List<DealItem> = emptyList(),
        val filteredDeals: List<DealItem> = emptyList(),
        val selectedCategory: String = "전체",
        val selectedCommunities: Set<String> = emptySet(),
        val allAvailableCommunities: Set<String> = emptySet(),
        val isPaginating: Boolean = false,
        val canLoadMore: Boolean = true
    ) : DealsUiState

    data class Error(val message: String) : DealsUiState
    object Loading : DealsUiState
}

sealed interface DealDetailState {
    data class Success(val dealDetail: DealDetail) : DealDetailState
    data class Error(val message: String) : DealDetailState
    object Loading : DealDetailState
}

sealed interface PriceHistoryState {
    data class Success(val history: List<PriceHistoryItem>) : PriceHistoryState
    data class Error(val message: String) : PriceHistoryState
    object Loading : PriceHistoryState
}

class DealViewModel : ViewModel() {

    private val _uiState = MutableStateFlow<DealsUiState>(DealsUiState.Loading)
    val uiState: StateFlow<DealsUiState> = _uiState.asStateFlow()

    private val _detailState = MutableStateFlow<DealDetailState>(DealDetailState.Loading)
    val detailState: StateFlow<DealDetailState> = _detailState.asStateFlow()

    private val _priceHistoryState = MutableStateFlow<PriceHistoryState>(PriceHistoryState.Loading)
    val priceHistoryState: StateFlow<PriceHistoryState> = _priceHistoryState.asStateFlow()

    private var currentPage = 1

    init {
        refresh()
    }

    fun refresh() {
        currentPage = 1
        _uiState.value = DealsUiState.Loading
        fetchDeals(isRefresh = true)
    }

    fun loadNextPage() {
        val currentState = _uiState.value
        if (currentState is DealsUiState.Success && !currentState.isPaginating && currentState.canLoadMore) {
            currentPage++
            fetchDeals(isRefresh = false)
        }
    }

    private fun fetchDeals(isRefresh: Boolean) {
        viewModelScope.launch {
            val currentState = _uiState.value
            if (!isRefresh && currentState is DealsUiState.Success) {
                _uiState.value = currentState.copy(isPaginating = true)
            }
            try {
                Log.d("DealViewModel", "Calling API: ${ApiClient.BASE_URL}api/deals?page=$currentPage")
                val newDeals = ApiClient.apiService.getDeals(page = currentPage)

                _uiState.update {
                    val currentSuccessState = if (it is DealsUiState.Success) it else DealsUiState.Success()
                    val combinedDeals = if (isRefresh) newDeals else currentSuccessState.allDeals + newDeals
                    val sortedDeals = combinedDeals.sortedBy { deal -> deal.id }
                    val allCommunities = sortedDeals.map { deal -> deal.community }.toSet()
                    val communitiesToFilter = if (isRefresh) allCommunities else currentSuccessState.selectedCommunities
                    val categoryToFilter = if (isRefresh) "전체" else currentSuccessState.selectedCategory
                    currentSuccessState.copy(
                        allDeals = sortedDeals,
                        filteredDeals = filterDeals(sortedDeals, categoryToFilter, communitiesToFilter),
                        selectedCategory = categoryToFilter,
                        selectedCommunities = communitiesToFilter,
                        allAvailableCommunities = allCommunities,
                        isPaginating = false,
                        canLoadMore = newDeals.isNotEmpty()
                    )
                }
                Log.d("DealViewModel", "Deals fetched page $currentPage, count: ${newDeals.size}")
            } catch (e: com.google.gson.JsonSyntaxException) {
                // ✅ JSON 파싱 오류 구체적 처리
                Log.e("DealViewModel", "JSON 파싱 오류 - 서버에서 올바르지 않은 응답을 받았습니다", e)
                _uiState.value = DealsUiState.Error("서버 응답 오류: JSON이 아닌 데이터를 받았습니다. 서버 상태를 확인해주세요.")
            } catch (e: retrofit2.HttpException) {
                // ✅ HTTP 오류 처리
                Log.e("DealViewModel", "HTTP 오류: ${e.code()}", e)
                _uiState.value = DealsUiState.Error("서버 연결 오류: ${e.code()}")
            } catch (e: java.net.ConnectException) {
                // ✅ 연결 오류 처리
                Log.e("DealViewModel", "연결 실패", e)
                _uiState.value = DealsUiState.Error("서버에 연결할 수 없습니다. 서버가 실행 중인지 확인해주세요.")
            } catch (e: Exception) {
                Log.e("DealViewModel", "API 호출 오류", e)
                _uiState.value = DealsUiState.Error("오류: ${e.message}")
            }
        }
    }

    fun selectCategory(category: String) {
        val currentState = _uiState.value
        if (currentState is DealsUiState.Success) {
            _uiState.value = currentState.copy(
                selectedCategory = category,
                filteredDeals = filterDeals(currentState.allDeals, category, currentState.selectedCommunities)
            )
            Log.d("DealViewModel", "Category selected: $category")
        }
    }

    fun toggleCommunity(community: String) {
        val currentState = _uiState.value
        if (currentState is DealsUiState.Success) {
            val newSelectedCommunities = currentState.selectedCommunities.toMutableSet()
            if (community in newSelectedCommunities) {
                newSelectedCommunities.remove(community)
                Log.d("DealViewModel", "Community removed from filter: $community")
            } else {
                newSelectedCommunities.add(community)
                Log.d("DealViewModel", "Community added to filter: $community")
            }
            _uiState.value = currentState.copy(
                selectedCommunities = newSelectedCommunities,
                filteredDeals = filterDeals(currentState.allDeals, currentState.selectedCategory, newSelectedCommunities)
            )
        }
    }

    private fun filterDeals(allDeals: List<DealItem>, category: String, communities: Set<String>): List<DealItem> {
        return allDeals.filter { deal ->
            (category == "전체" || deal.category == category) &&
                    (communities.isEmpty() || deal.community in communities)
        }
    }

    fun fetchDealDetail(dealId: Int) {
        viewModelScope.launch {
            _detailState.value = DealDetailState.Loading
            fetchPriceHistory(dealId)
            try {
                val dealDetail = ApiClient.apiService.getDealDetail(dealId)
                _detailState.value = DealDetailState.Success(dealDetail)
                Log.d("DealViewModel", "Deal detail fetched for id: $dealId")
            } catch (e: Exception) {
                Log.e("DealViewModel", "Error fetching deal detail", e)
                _detailState.value = DealDetailState.Error(e.message ?: "상세 정보를 불러오는 데 실패했습니다.")
            }
        }
    }

    fun fetchPriceHistory(dealId: Int) {
        viewModelScope.launch {
            _priceHistoryState.value = PriceHistoryState.Loading
            try {
                val history = ApiClient.apiService.getPriceHistory(dealId)
                if (history.isEmpty()) {
                    _priceHistoryState.value = PriceHistoryState.Error("가격 기록이 없습니다.")
                    Log.d("DealViewModel", "No price history for deal id: $dealId")
                } else {
                    _priceHistoryState.value = PriceHistoryState.Success(history)
                    Log.d("DealViewModel", "Price history fetched for deal id: $dealId, count: ${history.size}")
                }
            } catch (e: Exception) {
                Log.e("DealViewModel", "Error fetching price history", e)
                _priceHistoryState.value = PriceHistoryState.Error(e.message ?: "가격 기록을 불러오는 데 실패했습니다.")
            }
        }
    }
}

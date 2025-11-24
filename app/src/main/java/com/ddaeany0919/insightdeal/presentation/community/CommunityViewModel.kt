package com.ddaeany0919.insightdeal.presentation.community

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ddaeany0919.insightdeal.data.CommunityRepository
import com.ddaeany0919.insightdeal.data.HotDealDto
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import android.util.Log

sealed class CommunityUiState {
    object Loading : CommunityUiState()
    data class Success(val deals: List<HotDealDto>) : CommunityUiState()
    data class Error(val message: String) : CommunityUiState()
}

class CommunityViewModel : ViewModel() {
    private val repository = CommunityRepository()
    
    private val _uiState = MutableStateFlow<CommunityUiState>(CommunityUiState.Loading)
    val uiState: StateFlow<CommunityUiState> = _uiState

    init {
        loadHotDeals()
    }

    fun loadHotDeals() {
        viewModelScope.launch {
            _uiState.value = CommunityUiState.Loading
            try {
                Log.d("CommunityViewModel", "Fetching hot deals...")
                val deals = repository.getHotDeals()
                _uiState.value = CommunityUiState.Success(deals)
                Log.d("CommunityViewModel", "Fetched ${deals.size} deals successfully. Updating UI state to Success.")
            } catch (e: Exception) {
                Log.e("CommunityViewModel", "Error fetching deals", e)
                _uiState.value = CommunityUiState.Error("핫딜 정보를 불러오는데 실패했습니다: ${e.message}")
                Log.d("CommunityViewModel", "UI state updated to Error: ${e.message}")
            }
        }
    }
}

package com.ddaeany0919.insightdeal.presentation.wishlist

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ddaeany0919.insightdeal.presentation.wishlist.model.Period
import com.ddaeany0919.insightdeal.presentation.wishlist.PriceHistoryItem // 타입 경로 통일
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.launch
import java.time.LocalDate

sealed class WishlistUiState {
    object Loading : WishlistUiState()
    object Empty : WishlistUiState()
    data class Success(val items: List<WishlistItem>) : WishlistUiState()
    data class Error(val message: String) : WishlistUiState()
}

class WishlistViewModel(
    private val wishlistRepository: WishlistRepository,
    private val userIdProvider: () -> String
) : ViewModel() {

    private val _uiState = MutableStateFlow<WishlistUiState>(WishlistUiState.Loading)
    val uiState: StateFlow<WishlistUiState> = _uiState

    private val _selectedPeriod = MutableStateFlow(Period.THREE_MONTHS)
    val selectedPeriod: StateFlow<Period> = _selectedPeriod

    private val _priceHistory = MutableStateFlow<List<PriceHistoryItem>>(emptyList())
    val priceHistory: StateFlow<List<PriceHistoryItem>> = _priceHistory

    val filteredPriceHistory: StateFlow<List<PriceHistoryItem>> = combine(_priceHistory, _selectedPeriod) { list, period ->
        filterPriceHistoryForPeriod(list, period)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = emptyList()
    )

    private val _isAlarmOn = MutableStateFlow(false)
    val isAlarmOn: StateFlow<Boolean> = _isAlarmOn

    init {
        loadWishlist()
        loadPriceHistory()
        // 알람 상태도 서버에서 읽어오는 로직 현실적으로 추가할 것
    }

    fun setPeriod(period: Period) {
        _selectedPeriod.value = period
        loadPriceHistory()
    }

    fun toggleAlarm(on: Boolean) {
        _isAlarmOn.value = on
        syncAlarmStateToServer(on)
    }

    private fun filterPriceHistoryForPeriod(list: List<PriceHistoryItem>, period: Period): List<PriceHistoryItem> {
        val fromDate = when(period) {
            Period.THREE_MONTHS -> LocalDate.now().minusMonths(3)
            Period.ONE_MONTH -> LocalDate.now().minusMonths(1)
            Period.ONE_WEEK -> LocalDate.now().minusWeeks(1)
            Period.ONE_DAY -> LocalDate.now().minusDays(1)
        }
        // PriceHistoryItem의 date 대신 recordedAt 필드 사용
        return list.filter { it.recordedAt.toLocalDate().isAfter(fromDate) || it.recordedAt.toLocalDate().isEqual(fromDate) }
    }

    fun loadWishlist() {
        viewModelScope.launch {
            _uiState.value = WishlistUiState.Loading
            try {
                val userId = userIdProvider()
                val items = wishlistRepository.getWishlist(userId)
                _uiState.value = if (items.isEmpty()) WishlistUiState.Empty else WishlistUiState.Success(items)
            } catch (e: Exception) {
                _uiState.value = WishlistUiState.Error("목록을 불러오는데 실패했습니다: ${e.message}")
            }
        }
    }

    fun addItem(keyword: String, productUrl: String, targetPrice: Int) {
        viewModelScope.launch {
            try {
                val userId = userIdProvider()
                wishlistRepository.createWishlist(keyword, productUrl, targetPrice, userId)
                loadWishlist()
            } catch (e: Exception) {
                _uiState.value = WishlistUiState.Error("아이템 추가 실패: ${e.message}")
            }
        }
    }

    fun deleteItem(item: WishlistItem) {
        viewModelScope.launch {
            try {
                val userId = userIdProvider()
                val success = wishlistRepository.deleteWishlist(item.id, userId)
                if (success) loadWishlist()
                else _uiState.value = WishlistUiState.Error("아이템 삭제 실패")
            } catch (e: Exception) {
                _uiState.value = WishlistUiState.Error("아이템 삭제 실패: ${e.message}")
            }
        }
    }

    fun restoreItem(item: WishlistItem) {
        addItem(item.keyword, item.productUrl, item.targetPrice)
    }

    fun checkPrice(item: WishlistItem) {
        viewModelScope.launch {
            try {
                val userId = userIdProvider()
                wishlistRepository.checkPrice(item.id, userId)
                loadWishlist()
            } catch (e: Exception) {
                _uiState.value = WishlistUiState.Error("가격 확인 실패: ${e.message}")
            }
        }
    }

    fun retry() {
        loadWishlist()
    }

    private fun loadPriceHistory() {
        viewModelScope.launch {
            try {
                val userId = userIdProvider()
                val items = wishlistRepository.getPriceHistory(userId)
                _priceHistory.value = items
            } catch (e: Exception) {
                // Handle error
            }
        }
    }

    private fun syncAlarmStateToServer(on: Boolean) {
        viewModelScope.launch {
            try {
                val userId = userIdProvider()
                wishlistRepository.updateAlarmState(on, userId)
                // 서버 반영 성공 시 로그 등
            } catch (e: Exception) {
                // 동기화 실패 처리
            }
        }
    }
}

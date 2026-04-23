package com.ddaeany0919.insightdeal.presentation.wishlist

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ddaeany0919.insightdeal.presentation.wishlist.model.Period
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalDateTime
import android.util.Log

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

    private val priceHistoryRepository = com.ddaeany0919.insightdeal.data.PriceHistoryRepository()
    
    private val _itemPriceHistories = MutableStateFlow<Map<Int, com.ddaeany0919.insightdeal.data.PriceHistory>>(emptyMap())
    val itemPriceHistories: StateFlow<Map<Int, com.ddaeany0919.insightdeal.data.PriceHistory>> = _itemPriceHistories

    private val _uiState = MutableStateFlow<WishlistUiState>(WishlistUiState.Loading)
    val uiState: StateFlow<WishlistUiState> = _uiState

    private val _selectedPeriod = MutableStateFlow(Period.THREE_MONTHS)
    val selectedPeriod: StateFlow<Period> = _selectedPeriod

    private val _priceHistory = MutableStateFlow<List<PriceHistoryItem>>(emptyList())
    val priceHistory: StateFlow<List<PriceHistoryItem>> = _priceHistory

    private val _isAlarmOn = MutableStateFlow(false)
    val isAlarmOn: StateFlow<Boolean> = _isAlarmOn

    init {
        // ✨ Room Flow 구독하여 변경 사항 실시간 옵저빙 (Recomposition)
        viewModelScope.launch {
            wishlistRepository.getWishlistFlow().collect { items ->
                if (items.isEmpty()) {
                    _uiState.value = WishlistUiState.Empty
                } else {
                    _uiState.value = WishlistUiState.Success(items)
                }
            }
        }
    }

    val filteredPriceHistory: StateFlow<List<PriceHistoryItem>> = combine(_priceHistory, _selectedPeriod) { list, period ->
        val fromDate = when (period) {
            Period.THREE_MONTHS -> LocalDate.now().minusMonths(3)
            Period.ONE_MONTH -> LocalDate.now().minusMonths(1)
            Period.ONE_WEEK -> LocalDate.now().minusWeeks(1)
            Period.ONE_DAY -> LocalDate.now().minusDays(1)
        }
        list.filter { it.recordedAt.toLocalDate().isAfter(fromDate) || it.recordedAt.toLocalDate().isEqual(fromDate) }
    }.stateIn(scope = viewModelScope, started = SharingStarted.Eagerly, initialValue = emptyList())

    fun setPeriod(period: Period) {
        _selectedPeriod.value = period
        loadPriceHistory()
    }

    fun toggleAlarm(on: Boolean) {
        _isAlarmOn.value = on
        syncAlarmStateToServer(on)
    }

    fun loadWishlist() {
        // 이미 init { }에서 Flow를 collect 중이므로, 별도의 action 불필요
    }

    fun checkPrice(item: WishlistItem) {
        viewModelScope.launch {
            try {
                val userId = userIdProvider()
                val priceResponse = wishlistRepository.checkPrice(item.id, userId)

                val updatedItem = item.copy(
                    currentLowestPrice = priceResponse.currentPrice ?: item.currentLowestPrice,
                    currentLowestPlatform = priceResponse.platform ?: item.currentLowestPlatform,
                    currentLowestProductTitle = priceResponse.title ?: item.currentLowestProductTitle,
                    isTargetReached = priceResponse.isTargetReached ?: false,
                    latestPriceCheckResult = priceResponse,
                    lastChecked = LocalDateTime.now()
                )
                
                // Room DB 업데이트 (Flow를 통해 UI 반영)
                wishlistRepository.updateWishlist(updatedItem)
                
            } catch (e: Exception) {
                Log.e("WishlistViewModel", "가격 확인 실패", e)
            }
        }
    }

    fun addItem(keyword: String, productUrl: String, targetPrice: Int) {
        viewModelScope.launch {
            try {
                wishlistRepository.createWishlist(keyword, productUrl, targetPrice)
            } catch (e: Exception) {
                Log.e("WishlistViewModel", "아이템 추가 실패", e)
            }
        }
    }

    fun deleteItem(item: WishlistItem) {
        viewModelScope.launch {
            try {
                wishlistRepository.deleteWishlist(item.id)
            } catch (e: Exception) {
                Log.e("WishlistViewModel", "아이템 삭제 실패", e)
            }
        }
    }

    fun restoreItem(item: WishlistItem) {
        addItem(item.keyword, item.productUrl, item.targetPrice)
    }

    fun retry() {
        // Retry logic for empty/error cases if needed
    }

    private fun loadPriceHistory() {
        viewModelScope.launch {
            try {
                val userId = userIdProvider()
                val items = wishlistRepository.getPriceHistory(userId)
                _priceHistory.value = items
            } catch (e: Exception) {
                Log.e("WishlistViewModel", "가격 이력 불러오기 오류", e)
            }
        }
    }

    private fun syncAlarmStateToServer(on: Boolean) {
        viewModelScope.launch {
            try {
                val userId = userIdProvider()
                wishlistRepository.updateAlarmState(on, userId)
            } catch (e: Exception) {
                Log.e("WishlistViewModel", "알림 동기화 오류", e)
            }
        }
    }

    fun loadItemHistory(item: WishlistItem) {
        viewModelScope.launch {
            try {
                if (_itemPriceHistories.value.containsKey(item.id)) return@launch

                val history = priceHistoryRepository.getPriceHistory(item.keyword, item.id, 30)
                if (history != null) {
                    _itemPriceHistories.value = _itemPriceHistories.value + (item.id to history)
                }
            } catch (e: Exception) {
                Log.e("WishlistViewModel", "개별 상품 히스토리 로드 실패", e)
            }
        }
    }
}

package com.ddaeany0919.insightdeal.presentation.wishlist

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ddaeany0919.insightdeal.presentation.wishlist.model.Period
import com.ddaeany0919.insightdeal.presentation.wishlist.PriceHistoryItem
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

    private fun filterPriceHistoryForPeriod(list: List<PriceHistoryItem>, period: Period): List<PriceHistoryItem> {
        val fromDate = when (period) {
            Period.THREE_MONTHS -> LocalDate.now().minusMonths(3)
            Period.ONE_MONTH -> LocalDate.now().minusMonths(1)
            Period.ONE_WEEK -> LocalDate.now().minusWeeks(1)
            Period.ONE_DAY -> LocalDate.now().minusDays(1)
        }
        Log.d("WishlistViewModel", "필터링 기준 fromDate=$fromDate, 데이터 수=${list.size}")
        return list.filter { it.recordedAt.toLocalDate().isAfter(fromDate) || it.recordedAt.toLocalDate().isEqual(fromDate) }
    }

    fun setPeriod(period: Period) {
        _selectedPeriod.value = period
        Log.d("WishlistViewModel", "선택 기간 변경: $period, 가격 내역 리프레시 시도")
        loadPriceHistory()
    }

    fun toggleAlarm(on: Boolean) {
        _isAlarmOn.value = on
        Log.d("WishlistViewModel", "알림 상태 변경: on=$on")
        syncAlarmStateToServer(on)
    }

    private val _isAlarmOn = MutableStateFlow(false)
    val isAlarmOn: StateFlow<Boolean> = _isAlarmOn

    fun loadWishlist() {
        viewModelScope.launch {
            _uiState.value = WishlistUiState.Loading
            try {
                val userId = userIdProvider()
                val items = wishlistRepository.getWishlist(userId)
                _uiState.value = if (items.isEmpty()) WishlistUiState.Empty else WishlistUiState.Success(items)
                Log.d("WishlistViewModel", "위시리스트 로드: ${items.size}개")
            } catch (e: Exception) {
                _uiState.value = WishlistUiState.Error("목록을 불러오는데 실패했습니다: ${e.message}")
                Log.e("WishlistViewModel", "위시리스트 불러오기 오류", e)
            }
        }
    }

    fun checkPrice(item: WishlistItem) {
        viewModelScope.launch {
            val currentState = _uiState.value
            if (currentState !is WishlistUiState.Success) return@launch

            val loadingItems = currentState.items.map {
                if (it.id == item.id) it.copy(isLoading = true) else it
            }
            _uiState.value = WishlistUiState.Success(loadingItems)
            Log.d("WishlistViewModel", "${item.id} 가격 체크 시작")
            try {
                val userId = userIdProvider()
                val priceResponse = wishlistRepository.checkPrice(item.id, userId)

                val updatedItem = item.copy(
                    currentLowestPrice = priceResponse.currentPrice,
                    currentLowestPlatform = priceResponse.platform,
                    currentLowestProductTitle = priceResponse.title,
                    isTargetReached = priceResponse.isTargetReached ?: false,
                    latestPriceCheckResult = priceResponse,
                    isLoading = false,
                    lastChecked = LocalDateTime.now() // 가격 체크 시간 업데이트
                )

                val refreshedItems = _uiState.value.let { state ->
                    if (state is WishlistUiState.Success) {
                        state.items.map {
                            if (it.id == item.id) updatedItem else it
                        }
                    } else emptyList()
                }
                _uiState.value = WishlistUiState.Success(refreshedItems)
                Log.d("WishlistViewModel", "가격 체크 및 로딩 완료")
            } catch (e: Exception) {
                Log.e("WishlistViewModel", "가격 확인 실패", e)
                val errorItems = _uiState.value.let { state ->
                    if (state is WishlistUiState.Success) {
                        state.items.map {
                            if (it.id == item.id) it.copy(isLoading = false) else it
                        }
                    } else emptyList()
                }
                _uiState.value = WishlistUiState.Success(errorItems)
                _uiState.value = WishlistUiState.Error("가격 확인 실패: ${e.message}")
            }
        }
    }

    fun addItem(keyword: String, productUrl: String, targetPrice: Int) {
        viewModelScope.launch {
            try {
                val userId = userIdProvider()
                wishlistRepository.createWishlist(keyword, productUrl, targetPrice, userId)
                Log.d("WishlistViewModel", "아이템 추가: $keyword")
                loadWishlist()
            } catch (e: Exception) {
                _uiState.value = WishlistUiState.Error("아이템 추가 실패: ${e.message}")
                Log.e("WishlistViewModel", "아이템 추가 실패", e)
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
                Log.d("WishlistViewModel", "아이템 삭제 시도: ${item.id}")
            } catch (e: Exception) {
                _uiState.value = WishlistUiState.Error("아이템 삭제 실패: ${e.message}")
                Log.e("WishlistViewModel", "아이템 삭제 실패", e)
            }
        }
    }

    fun restoreItem(item: WishlistItem) {
        addItem(item.keyword, item.productUrl, item.targetPrice)
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
                Log.d("WishlistViewModel", "가격 이력 로드: ${items.size}개")
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
                Log.d("WishlistViewModel", "알림 상태 서버 반영 성공")
            } catch (e: Exception) {
                Log.e("WishlistViewModel", "알림 동기화 오류", e)
            }
        }
    }
}

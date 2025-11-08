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
    // ... 생략 부분 동일

    // 가격 체크 로직 수정!
    fun checkPrice(item: WishlistItem) {
        viewModelScope.launch {
            try {
                val userId = userIdProvider()
                val priceResponse = wishlistRepository.checkPrice(item.id, userId)

                // 가격 체크 응답으로 아이템 업데이트 (UI에 즉시 반영)
                val updatedItem = item.copy(
                    currentLowestPrice = priceResponse.currentPrice,
                    currentLowestPlatform = priceResponse.platform,
                    currentLowestProductTitle = priceResponse.title,
                    isTargetReached = priceResponse.isTargetReached ?: false,
                    latestPriceCheckResult = priceResponse
                )

                val currentState = _uiState.value
                if (currentState is WishlistUiState.Success) {
                    val updatedItems = currentState.items.map {
                        if (it.id == item.id) updatedItem else it
                    }
                    _uiState.value = WishlistUiState.Success(updatedItems)
                }

                Log.d("WishlistViewModel", "가격 체크 완료 및 UI 업데이트: ${priceResponse.currentPrice}")
            } catch (e: Exception) {
                Log.e("WishlistViewModel", "가격 확인 실패", e)
                _uiState.value = WishlistUiState.Error("가격 확인 실패: ${e.message}")
            }
        }
    }

    // ... 나머지 기존 함수 동일
}

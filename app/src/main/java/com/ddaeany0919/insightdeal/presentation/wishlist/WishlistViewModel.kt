package com.ddaeany0919.insightdeal.presentation.wishlist

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.LocalDateTime

class WishlistViewModel(
    private val wishlistRepository: WishlistRepository = WishlistRepository(),
    private val userIdProvider: () -> String = { "default" } // TODO inject real user id
) : ViewModel() {

    private val _uiState = MutableStateFlow<WishlistState>(WishlistState.Loading)
    val uiState: StateFlow<WishlistState> = _uiState.asStateFlow()

    fun loadWishlist() {
        viewModelScope.launch {
            _uiState.value = WishlistState.Loading
            try {
                val wishlists = wishlistRepository.getWishlist(userIdProvider())
                _uiState.value = if (wishlists.isEmpty()) WishlistState.Empty else WishlistState.Success(wishlists)
            } catch (e: Exception) {
                _uiState.value = WishlistState.Error("관심상품을 불러오는 중 오류가 발생했습니다: ${e.message}")
            }
        }
    }

    fun addItem(keyword: String, targetPrice: Int) {
        viewModelScope.launch {
            try {
                val newWishlist = wishlistRepository.createWishlist(keyword.trim(), targetPrice, userIdProvider())
                val currentItems = (uiState.value as? WishlistState.Success)?.items.orEmpty()
                _uiState.value = WishlistState.Success(listOf(newWishlist) + currentItems)
            } catch (e: Exception) {
                _uiState.value = WishlistState.Error("관심상품 추가 중 오류가 발생했습니다: ${e.message}")
            }
        }
    }

    fun deleteItem(item: WishlistItem) {
        viewModelScope.launch {
            try {
                // 낙관적 제거
                val currentItems = (uiState.value as? WishlistState.Success)?.items.orEmpty()
                _uiState.value = currentItems.filter { it.id != item.id }.let {
                    if (it.isEmpty()) WishlistState.Empty else WishlistState.Success(it)
                }

                // 서버 삭제
                wishlistRepository.deleteWishlist(item.id, userIdProvider())

                // 서버 상태와 재동기화
                loadWishlist()
            } catch (e: Exception) {
                _uiState.value = WishlistState.Error("관심상품 삭제 중 오류가 발생했습니다: ${e.message}")
            }
        }
    }

    fun restoreItem(item: WishlistItem) {
        viewModelScope.launch {
            try {
                wishlistRepository.createWishlist(item.keyword, item.targetPrice, userIdProvider())
                loadWishlist()
            } catch (e: Exception) {
                _uiState.value = WishlistState.Error("아이템 복원 중 오류가 발생했습니다: ${e.message}")
            }
        }
    }

    fun checkPrice(item: WishlistItem) {
        viewModelScope.launch {
            try {
                wishlistRepository.checkPrice(item.id, userIdProvider())
                val currentItems = (uiState.value as? WishlistState.Success)?.items.orEmpty().map { w ->
                    if (w.id == item.id) w.copy(lastChecked = LocalDateTime.now()) else w
                }
                _uiState.value = WishlistState.Success(currentItems)
                loadWishlist()
            } catch (e: Exception) {
                _uiState.value = WishlistState.Error("가격 체크 중 오류가 발생했습니다: ${e.message}")
            }
        }
    }

    fun retry() { loadWishlist() }
}

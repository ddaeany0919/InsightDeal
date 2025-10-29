package com.ddaeany0919.insightdeal.presentation.wishlist

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.LocalDateTime

/**
 * 💎 관심상품 ViewModel
 * 관심상품 데이터 및 비즈니스 로직 관리
 */
class WishlistViewModel(
    private val wishlistRepository: WishlistRepository = WishlistRepository()
) : ViewModel() {
    
    private val _uiState = MutableStateFlow(WishlistUiState())
    val uiState: StateFlow<WishlistUiState> = _uiState.asStateFlow()
    
    /**
     * 관심상품 목록 로드
     */
    fun loadWishlist() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)
            
            try {
                val wishlists = wishlistRepository.getWishlist()
                _uiState.value = _uiState.value.copy(
                    wishlists = wishlists,
                    isLoading = false
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    errorMessage = "관심상품을 불러오는 중 오류가 발생했습니다: ${e.message}"
                )
            }
        }
    }
    
    /**
     * 새로운 관심상품 추가
     */
    fun addWishlist(keyword: String, targetPrice: Int) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)
            
            try {
                val newWishlist = wishlistRepository.createWishlist(
                    keyword = keyword.trim(),
                    targetPrice = targetPrice
                )
                
                val updatedList = _uiState.value.wishlists.toMutableList()
                updatedList.add(0, newWishlist) // 맨 위에 추가
                
                _uiState.value = _uiState.value.copy(
                    wishlists = updatedList,
                    isLoading = false
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    errorMessage = "관심상품 추가 중 오류가 발생했습니다: ${e.message}"
                )
            }
        }
    }
    
    /**
     * 관심상품 삭제
     */
    fun deleteWishlist(wishlistId: Int) {
        viewModelScope.launch {
            try {
                wishlistRepository.deleteWishlist(wishlistId)
                
                val updatedList = _uiState.value.wishlists.filter { it.id != wishlistId }
                _uiState.value = _uiState.value.copy(
                    wishlists = updatedList
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    errorMessage = "관심상품 삭제 중 오류가 발생했습니다: ${e.message}"
                )
            }
        }
    }
    
    /**
     * 수동 가격 체크
     */
    fun checkPrice(wishlistId: Int) {
        viewModelScope.launch {
            try {
                val result = wishlistRepository.checkPrice(wishlistId)
                
                // UI에서 체크 중 상태 표시
                val updatedList = _uiState.value.wishlists.map { wishlist ->
                    if (wishlist.id == wishlistId) {
                        wishlist.copy(
                            lastChecked = LocalDateTime.now()
                        )
                    } else {
                        wishlist
                    }
                }
                
                _uiState.value = _uiState.value.copy(
                    wishlists = updatedList
                )
                
                // 다시 로드해서 최신 가격 정보 반영
                loadWishlist()
                
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    errorMessage = "가격 체크 중 오류가 발생했습니다: ${e.message}"
                )
            }
        }
    }
    
    /**
     * 에러 메시지 지우기
     */
    fun clearError() {
        _uiState.value = _uiState.value.copy(errorMessage = null)
    }
}

/**
 * 관심상품 UI 상태
 */
data class WishlistUiState(
    val wishlists: List<WishlistItem> = emptyList(),
    val isLoading: Boolean = false,
    val errorMessage: String? = null
)

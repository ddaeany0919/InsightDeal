package com.ddaeany0919.insightdeal.presentation.wishlist

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.LocalDateTime

/**
 * 📎 관심상품 ViewModel
 * 관심상품 데이터 및 비즈니스 로직 관리
 */
class WishlistViewModel(
    private val wishlistRepository: WishlistRepository = WishlistRepository()
) : ViewModel() {
    
    private val _uiState = MutableStateFlow<WishlistState>(WishlistState.Loading)
    val uiState: StateFlow<WishlistState> = _uiState.asStateFlow()
    
    // 백업용 아이템 리스트 (실행취소 기능용)
    private val deletedItems = mutableListOf<WishlistItem>()
    
    init {
        loadWishlist()
    }
    
    /**
     * 관심상품 목록 로드
     */
    fun loadWishlist() {
        viewModelScope.launch {
            _uiState.value = WishlistState.Loading
            
            try {
                val wishlists = wishlistRepository.getWishlist()
                _uiState.value = if (wishlists.isEmpty()) {
                    WishlistState.Empty
                } else {
                    WishlistState.Success(wishlists)
                }
            } catch (e: Exception) {
                _uiState.value = WishlistState.Error(
                    "관심상품을 불러오는 중 오류가 발생했습니다: ${e.message}"
                )
            }
        }
    }
    
    /**
     * 새로운 관심상품 추가
     */
    fun addItem(keyword: String, targetPrice: Int) {
        viewModelScope.launch {
            try {
                val newWishlist = wishlistRepository.createWishlist(
                    keyword = keyword.trim(),
                    targetPrice = targetPrice
                )
                
                val currentItems = when (val currentState = _uiState.value) {
                    is WishlistState.Success -> currentState.items
                    else -> emptyList()
                }
                
                val updatedList = listOf(newWishlist) + currentItems
                _uiState.value = WishlistState.Success(updatedList)
                
            } catch (e: Exception) {
                _uiState.value = WishlistState.Error(
                    "관심상품 추가 중 오류가 발생했습니다: ${e.message}"
                )
            }
        }
    }
    
    /**
     * 관심상품 삭제
     */
    fun deleteItem(item: WishlistItem) {
        viewModelScope.launch {
            try {
                // 백업을 위해 아이템 저장
                deletedItems.add(item)
                
                wishlistRepository.deleteWishlist(item.id)
                
                val currentState = _uiState.value
                if (currentState is WishlistState.Success) {
                    val updatedList = currentState.items.filter { it.id != item.id }
                    _uiState.value = if (updatedList.isEmpty()) {
                        WishlistState.Empty
                    } else {
                        WishlistState.Success(updatedList)
                    }
                }
                
            } catch (e: Exception) {
                _uiState.value = WishlistState.Error(
                    "관심상품 삭제 중 오류가 발생했습니다: ${e.message}"
                )
            }
        }
    }
    
    /**
     * 삭제된 아이템 복원 (실행취소)
     */
    fun restoreItem(item: WishlistItem) {
        viewModelScope.launch {
            try {
                // 백업에서 제거
                deletedItems.remove(item)
                
                // 새로 추가 (실제로는 전체 리로드 해야 함)
                val restoredItem = wishlistRepository.createWishlist(
                    keyword = item.keyword,
                    targetPrice = item.targetPrice
                )
                
                loadWishlist() // 전체 리로드
                
            } catch (e: Exception) {
                _uiState.value = WishlistState.Error(
                    "아이템 복원 중 오류가 발생했습니다: ${e.message}"
                )
            }
        }
    }
    
    /**
     * 수동 가격 체크
     */
    fun checkPrice(item: WishlistItem) {
        viewModelScope.launch {
            try {
                val result = wishlistRepository.checkPrice(item.id)
                
                // UI에서 체크 중 상태 표시
                val currentState = _uiState.value
                if (currentState is WishlistState.Success) {
                    val updatedList = currentState.items.map { wishlistItem ->
                        if (wishlistItem.id == item.id) {
                            wishlistItem.copy(
                                lastChecked = LocalDateTime.now()
                            )
                        } else {
                            wishlistItem
                        }
                    }
                    
                    _uiState.value = WishlistState.Success(updatedList)
                }
                
                // 다시 로드해서 최신 가격 정보 반영
                loadWishlist()
                
            } catch (e: Exception) {
                _uiState.value = WishlistState.Error(
                    "가격 체크 중 오류가 발생했습니다: ${e.message}"
                )
            }
        }
    }
    
    /**
     * 오류 상태에서 다시 시도
     */
    fun retry() {
        loadWishlist()
    }
}

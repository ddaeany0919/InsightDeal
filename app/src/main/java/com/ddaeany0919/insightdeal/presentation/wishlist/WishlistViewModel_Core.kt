package com.ddaeany0919.insightdeal.presentation.wishlist

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

// --- UI 모델 (간단 버전) ---
sealed interface WishlistState {
    data object Loading : WishlistState
    data class Success(val items: List<WishlistItem>) : WishlistState
    data class Empty(val message: String = "") : WishlistState
    data class Error(val message: String) : WishlistState
}

data class WishlistItem(
    val id: Int,
    val keyword: String,
    val targetPrice: Int,
    val currentLowestPrice: Int? = null,
    val currentLowestPlatform: String? = null,
    val lastChecked: java.time.LocalDateTime? = null
)

// --- Repository 인터페이스(스텁) ---
interface WishlistRepository {
    suspend fun getWishlist(userId: String): List<WishlistItem>
    suspend fun addItem(keyword: String, targetPrice: Int, userId: String): WishlistItem
    suspend fun deleteItem(id: Int, userId: String)
    suspend fun checkPrice(id: Int, userId: String): WishlistItem
    suspend fun analyzeLink(url: String): Unit
    suspend fun addFromLink(url: String, targetPrice: Int, userId: String): WishlistItem
}

class WishlistViewModel(
    private val repo: WishlistRepository = HttpWishlistRepository()
) : ViewModel() {
    private val _uiState = MutableStateFlow<WishlistState>(WishlistState.Loading)
    val uiState: StateFlow<WishlistState> get() = _uiState

    private val userId = "user1"

    fun loadWishlist() {
        viewModelScope.launch {
            try {
                _uiState.value = WishlistState.Loading
                val list = repo.getWishlist(userId)
                _uiState.value = if (list.isEmpty()) WishlistState.Empty() else WishlistState.Success(list)
            } catch (e: Exception) {
                _uiState.value = WishlistState.Error(e.message ?: "불명확한 오류")
            }
        }
    }

    fun addItem(keyword: String, targetPrice: Int) {
        viewModelScope.launch {
            try {
                repo.addItem(keyword, targetPrice, userId)
                loadWishlist()
            } catch (e: Exception) {
                _uiState.value = WishlistState.Error(e.message ?: "추가 실패")
            }
        }
    }

    fun deleteItem(item: WishlistItem) {
        viewModelScope.launch {
            try {
                repo.deleteItem(item.id, userId)
                loadWishlist()
            } catch (e: Exception) {
                _uiState.value = WishlistState.Error(e.message ?: "삭제 실패")
            }
        }
    }

    fun checkPrice(item: WishlistItem) {
        viewModelScope.launch {
            try {
                repo.checkPrice(item.id, userId)
                loadWishlist()
            } catch (e: Exception) {
                _uiState.value = WishlistState.Error(e.message ?: "가격 체크 실패")
            }
        }
    }

    fun retry() { loadWishlist() }

    fun addFromLink(url: String, targetPrice: Int) {
        viewModelScope.launch {
            try {
                repo.addFromLink(url, targetPrice, userId)
                loadWishlist()
            } catch (e: Exception) {
                _uiState.value = WishlistState.Error(e.message ?: "링크 추가 실패")
            }
        }
    }
}

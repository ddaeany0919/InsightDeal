package com.ddaeany0919.insightdeal.presentation.wishlist

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.LocalDateTime

private const val TAG_VM = "WishlistVM"

class WishlistViewModel(
    private val wishlistRepository: WishlistRepository = WishlistRepository(),
    private val userIdProvider: () -> String = { "default" }
) : ViewModel() {

    private val _uiState = MutableStateFlow<WishlistState>(WishlistState.Loading)
    val uiState: StateFlow<WishlistState> = _uiState.asStateFlow()

    fun loadWishlist() {
        viewModelScope.launch {
            Log.d(TAG_VM, "loadWishlist: start userId=${userIdProvider()}")
            _uiState.value = WishlistState.Loading
            try {
                val items = wishlistRepository.getWishlist(userIdProvider())
                Log.d(TAG_VM, "loadWishlist: success count=${items.size}")
                _uiState.value = if (items.isEmpty()) WishlistState.Empty else WishlistState.Success(items)
            } catch (e: Exception) {
                Log.e(TAG_VM, "loadWishlist: error ${e.message}", e)
                _uiState.value = WishlistState.Error("관심상품을 불러오는 중 오류: ${e.message}")
            }
        }
    }

    fun addItem(keyword: String, targetPrice: Int) {
        viewModelScope.launch {
            Log.d(TAG_VM, "addItem: start keyword=$keyword target=$targetPrice userId=${userIdProvider()}")
            try {
                val created = wishlistRepository.createWishlist(keyword.trim(), targetPrice, userIdProvider())
                Log.d(TAG_VM, "addItem: success id=${created.id}")
                val current = (uiState.value as? WishlistState.Success)?.items.orEmpty()
                _uiState.value = WishlistState.Success(listOf(created) + current)
            } catch (e: Exception) {
                Log.e(TAG_VM, "addItem: error ${e.message}", e)
                _uiState.value = WishlistState.Error("관심상품 추가 오류: ${e.message}")
            }
        }
    }

    fun deleteItem(item: WishlistItem) {
        viewModelScope.launch {
            Log.d(TAG_VM, "deleteItem: start id=${item.id} userId=${userIdProvider()}")
            val backup = (uiState.value as? WishlistState.Success)?.items.orEmpty()
            // optimistic remove
            _uiState.value = backup.filter { it.id != item.id }.let { if (it.isEmpty()) WishlistState.Empty else WishlistState.Success(it) }
            try {
                val res = wishlistRepository.deleteWishlist(item.id, userIdProvider())
                Log.d(TAG_VM, "deleteItem: server success -> $res")
                loadWishlist()
            } catch (e: Exception) {
                Log.e(TAG_VM, "deleteItem: server error ${e.message}", e)
                _uiState.value = WishlistState.Success(backup) // rollback UI
                _uiState.value = WishlistState.Error("관심상품 삭제 오류: ${e.message}")
            }
        }
    }

    fun restoreItem(item: WishlistItem) {
        viewModelScope.launch {
            Log.d(TAG_VM, "restoreItem: start keyword=${item.keyword} target=${item.targetPrice}")
            try {
                wishlistRepository.createWishlist(item.keyword, item.targetPrice, userIdProvider())
                loadWishlist()
            } catch (e: Exception) {
                Log.e(TAG_VM, "restoreItem: error ${e.message}", e)
                _uiState.value = WishlistState.Error("복원 오류: ${e.message}")
            }
        }
    }

    fun checkPrice(item: WishlistItem) {
        viewModelScope.launch {
            Log.d(TAG_VM, "checkPrice: start id=${item.id}")
            try {
                val msg = wishlistRepository.checkPrice(item.id, userIdProvider())
                Log.d(TAG_VM, "checkPrice: success message=$msg")
                val updated = (uiState.value as? WishlistState.Success)?.items.orEmpty().map { w -> if (w.id == item.id) w.copy(lastChecked = LocalDateTime.now()) else w }
                _uiState.value = WishlistState.Success(updated)
                loadWishlist()
            } catch (e: Exception) {
                Log.e(TAG_VM, "checkPrice: error ${e.message}", e)
                _uiState.value = WishlistState.Error("가격 체크 오류: ${e.message}")
            }
        }
    }

    fun retry() {
        Log.d(TAG_VM, "retry called")
        loadWishlist()
    }
}

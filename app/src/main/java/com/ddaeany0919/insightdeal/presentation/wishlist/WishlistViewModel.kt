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
    private val userIdProvider: () -> String = { "user1" }
) : ViewModel() {

    private val _uiState = MutableStateFlow<WishlistState>(WishlistState.Loading)
    val uiState: StateFlow<WishlistState> = _uiState.asStateFlow()
    private val httpHelper = HttpWishlistHelper()

    fun loadWishlist() {
        viewModelScope.launch {
            val userId = userIdProvider()
            Log.d(TAG_VM, "loadWishlist: start userId=$userId")
            _uiState.value = WishlistState.Loading
            try {
                val items = wishlistRepository.getWishlist(userId)
                Log.d(TAG_VM, "loadWishlist: success count=${items.size} for userId=$userId")
                _uiState.value = if (items.isEmpty()) WishlistState.Empty else WishlistState.Success(items)
            } catch (e: Exception) {
                Log.e(TAG_VM, "loadWishlist: error for userId=$userId - ${e.message}", e)
                _uiState.value = WishlistState.Error("관심상품을 불러오는 중 오류: ${e.message}")
            }
        }
    }

    fun addItem(keyword: String, targetPrice: Int) {
        viewModelScope.launch {
            val userId = userIdProvider()
            Log.d(TAG_VM, "addItem: start keyword=$keyword target=$targetPrice userId=$userId")
            try {
                val created = wishlistRepository.createWishlist(keyword.trim(), targetPrice, userId)
                Log.d(TAG_VM, "addItem: success id=${created.id} for userId=$userId")
                loadWishlist()
            } catch (e: Exception) {
                Log.e(TAG_VM, "addItem: error for userId=$userId - ${e.message}", e)
                _uiState.value = WishlistState.Error("관심상품 추가 오류: ${e.message}")
            }
        }
    }

    fun deleteItem(item: WishlistItem) {
        viewModelScope.launch {
            val userId = userIdProvider()
            Log.d(TAG_VM, "deleteItem: start id=${item.id} userId=$userId")
            try {
                val res = wishlistRepository.deleteWishlist(item.id, userId)
                Log.d(TAG_VM, "deleteItem: server success -> $res for userId=$userId")
                loadWishlist()
            } catch (e: Exception) {
                Log.e(TAG_VM, "deleteItem: server error for userId=$userId - ${e.message}", e)
                _uiState.value = WishlistState.Error("관심상품 삭제 오류: ${e.message}")
            }
        }
    }

    fun checkPrice(item: WishlistItem) {
        viewModelScope.launch {
            val userId = userIdProvider()
            Log.d(TAG_VM, "checkPrice: start id=${item.id} userId=$userId")
            try {
                val msg = wishlistRepository.checkPrice(item.id, userId)
                Log.d(TAG_VM, "checkPrice: success message=$msg for userId=$userId")
                loadWishlist()
            } catch (e: Exception) {
                Log.e(TAG_VM, "checkPrice: error for userId=$userId - ${e.message}", e)
                _uiState.value = WishlistState.Error("가격 체크 오류: ${e.message}")
            }
        }
    }

    fun addFromLink(url: String, targetPrice: Int) {
        viewModelScope.launch {
            val userId = userIdProvider()
            Log.d(TAG_VM, "addFromLink: start url=$url target=$targetPrice userId=$userId")
            try {
                val created = httpHelper.addFromLink(url, targetPrice, userId)
                Log.d(TAG_VM, "addFromLink: success id=${created.id} for userId=$userId")
                loadWishlist()
            } catch (e: Exception) {
                Log.e(TAG_VM, "addFromLink: error for userId=$userId - ${e.message}", e)
                _uiState.value = WishlistState.Error("링크 추가 오류: ${e.message}")
            }
        }
    }

    fun retry() {
        val userId = userIdProvider()
        Log.d(TAG_VM, "retry called for userId=$userId")
        loadWishlist()
    }
}
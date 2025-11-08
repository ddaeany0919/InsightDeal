package com.ddaeany0919.insightdeal.presentation.wishlist

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.UUID

private const val TAG_VM = "WishlistVM"

class WishlistViewModel(
    private val wishlistRepository: WishlistRepository = WishlistRepository(),
    private val userIdProvider: () -> String = {
        try {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                "device_${android.os.Build.getSerial().take(8)}_${UUID.randomUUID().toString().take(8)}"
            } else {
                @Suppress("DEPRECATION")
                "device_${android.os.Build.SERIAL.take(8)}_${UUID.randomUUID().toString().take(8)}"
            }
        } catch (e: Exception) {
            "device_UNKNOWN_${UUID.randomUUID().toString().take(8)}"
        }
    }
) : ViewModel() {

    private val _uiState = MutableStateFlow<WishlistState>(WishlistState.Loading)
    val uiState: StateFlow<WishlistState> = _uiState.asStateFlow()

    fun loadWishlist() {
        viewModelScope.launch {
            val userId = userIdProvider()
            Log.d(TAG_VM, "loadWishlist: start userId=$userId")
            _uiState.value = WishlistState.Loading
            try {
                val items = wishlistRepository.getWishlist(userId)
                Log.d(TAG_VM, "loadWishlist: success count=${items.size} for userId=$userId")
                _uiState.value = if (items.isEmpty()) WishlistState.Empty else WishlistState.Success(items)
            } catch (e: retrofit2.HttpException) {
                if (e.code() == 404) {
                    Log.d(TAG_VM, "loadWishlist: 404 - treating as empty list")
                    _uiState.value = WishlistState.Empty
                } else {
                    Log.e(TAG_VM, "loadWishlist: error for userId=$userId - ${e.message}", e)
                    _uiState.value = WishlistState.Error("[translate:관심상품을 불러오는 중 오류:] ${e.message}")
                }
            } catch (e: Exception) {
                Log.e(TAG_VM, "loadWishlist: error for userId=$userId - ${e.message}", e)
                _uiState.value = WishlistState.Error("[translate:관심상품을 불러오는 중 오류:] ${e.message}")
            }
        }
    }

    fun addItem(keyword: String, productUrl: String, targetPrice: Int) {
        viewModelScope.launch {
            val userId = userIdProvider()
            Log.d(TAG_VM, "addItem: start keyword=$keyword productUrl=$productUrl target=$targetPrice userId=$userId")
            try {
                val created = wishlistRepository.createWishlist(keyword.trim(), productUrl, targetPrice, userId)
                Log.d(TAG_VM, "addItem: success id=${created.id} for userId=$userId")
                loadWishlist()
            } catch (e: retrofit2.HttpException) {
                if (e.code() == 404) {
                    Log.e(TAG_VM, "addItem: 404 - API endpoint not found for userId=$userId")
                    _uiState.value = WishlistState.Error("[translate:서버 연결 오류:] [translate:관심상품 추가 기능이 준비되지 않았습니다.]")
                } else {
                    Log.e(TAG_VM, "addItem: HTTP error ${e.code()} for userId=$userId - ${e.message}", e)
                    _uiState.value = WishlistState.Error("[translate:관심상품 추가 오류:] ${e.message}")
                }
            } catch (e: Exception) {
                Log.e(TAG_VM, "addItem: error for userId=$userId - ${e.message}", e)
                _uiState.value = WishlistState.Error("[translate:관심상품 추가 오류:] ${e.message}")
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
                _uiState.value = WishlistState.Error("[translate:관심상품 삭제 오류:] ${e.message}")
            }
        }
    }

    fun restoreItem(item: WishlistItem) {
        viewModelScope.launch {
            val userId = userIdProvider()
            Log.d(TAG_VM, "restoreItem: start keyword=${item.keyword} target=${item.targetPrice} userId=$userId")
            try {
                val restored = wishlistRepository.createWishlist(item.keyword, "", item.targetPrice, userId)
                Log.d(TAG_VM, "restoreItem: success new_id=${restored.id} for userId=$userId")
                loadWishlist()
            } catch (e: Exception) {
                Log.e(TAG_VM, "restoreItem: error for userId=$userId - ${e.message}", e)
                _uiState.value = WishlistState.Error("[translate:관심상품 복원 오류:] ${e.message}")
            }
        }
    }

    fun checkPrice(item: WishlistItem) {
        viewModelScope.launch {
            val userId = userIdProvider()
            Log.d(TAG_VM, "checkPrice: start id=${item.id} userId=$userId")
            try {
                val priceCheckResponse = wishlistRepository.checkPrice(item.id, userId)
                Log.d(TAG_VM, "checkPrice: success message=${priceCheckResponse.message} for userId=$userId")

                val currentList = (_uiState.value as? WishlistState.Success)?.items?.toMutableList() ?: mutableListOf()
                val index = currentList.indexOfFirst { it.id == item.id }
                if (index != -1) {
                    val updatedItem = currentList[index].copy(
                        latestPriceCheckResult = priceCheckResponse,
                        currentLowestPrice = priceCheckResponse.currentPrice,
                        currentLowestPlatform = priceCheckResponse.platform,
                        currentLowestProductTitle = priceCheckResponse.title,
                        isTargetReached = priceCheckResponse.isTargetReached ?: false
                    )
                    currentList[index] = updatedItem
                    _uiState.value = WishlistState.Success(currentList)
                }
            } catch (e: Exception) {
                Log.e(TAG_VM, "checkPrice: error for userId=$userId - ${e.message}", e)
                _uiState.value = WishlistState.Error("[translate:가격 체크 오류:] ${e.message}")
            }
        }
    }

    fun retry() {
        val userId = userIdProvider()
        Log.d(TAG_VM, "retry called for userId=$userId")
        loadWishlist()
    }
}

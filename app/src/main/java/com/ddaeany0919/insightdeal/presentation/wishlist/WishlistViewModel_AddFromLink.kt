package com.ddaeany0919.insightdeal.presentation.wishlist

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class WishlistViewModel : ViewModel() {
    // 기존 uiState, addItem 등은 생략

    fun addFromLink(url: String, targetPrice: Int) {
        viewModelScope.launch {
            try {
                Log.d("WishlistVM", "addFromLink: start url=$url target=$targetPrice")
                // 1) 분석 호출
                // val analysis = api.analyzeLink(url)
                // 2) 생성 호출
                // val created = api.addWishlistFromLink(url, targetPrice)
                // 3) 목록 갱신
                // loadWishlist()
                Log.d("WishlistVM", "addFromLink: success url=$url target=$targetPrice")
            } catch (e: Exception) {
                Log.d("WishlistVM", "addFromLink: failed ${e.message}")
            }
        }
    }
}

package com.ddaeany0919.insightdeal.presentation.wishlist

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

// 간단한 HTTP 스텁 구현 (실제 네트워크 라이브러리로 교체 예정)
class HttpWishlistRepository : WishlistRepository {
    override suspend fun getWishlist(userId: String): List<WishlistItem> = withContext(Dispatchers.IO) {
        // TODO: 실제 GET /api/wishlist?user_id=... 호출
        delay(50)
        emptyList()
    }

    override suspend fun addItem(keyword: String, targetPrice: Int, userId: String): WishlistItem = withContext(Dispatchers.IO) {
        // TODO: POST /api/wishlist
        delay(50)
        WishlistItem(id = 1, keyword = keyword, targetPrice = targetPrice)
    }

    override suspend fun deleteItem(id: Int, userId: String) = withContext(Dispatchers.IO) {
        // TODO: DELETE /api/wishlist/{id}?user_id=...
        delay(50)
        Log.d("WishlistRepo", "delete item $id")
    }

    override suspend fun checkPrice(id: Int, userId: String): WishlistItem = withContext(Dispatchers.IO) {
        // TODO: POST /api/wishlist/{id}/check-price?user_id=...
        delay(50)
        WishlistItem(id = id, keyword = "키워드", targetPrice = 10000, currentLowestPrice = 9000, currentLowestPlatform = "네이버쇼핑")
    }

    override suspend fun analyzeLink(url: String) = withContext(Dispatchers.IO) {
        // TODO: POST /api/product/analyze-link { url }
        delay(50)
    }

    override suspend fun addFromLink(url: String, targetPrice: Int, userId: String): WishlistItem = withContext(Dispatchers.IO) {
        // TODO: POST /api/wishlist/add-from-link { url, target_price, user_id }
        delay(50)
        WishlistItem(id = 2, keyword = "AI분석키워드", targetPrice = targetPrice)
    }
}

package com.ddaeany0919.insightdeal.presentation.wishlist

/**
 * Wishlist UI 상태를 나타내는 sealed class
 */
sealed class WishlistState {
    object Loading : WishlistState()
    object Empty : WishlistState()
    data class Success(val items: List<WishlistItem>) : WishlistState()
    data class Error(val message: String) : WishlistState()
}

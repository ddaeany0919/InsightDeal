package com.ddaeany0919.insightdeal.presentation.wishlist

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.ddaeany0919.insightdeal.WishlistCard

@Composable
fun WatchlistScreen(
    viewModel: WishlistViewModel = androidx.lifecycle.viewmodel.compose.viewModel()
) {
    val state by viewModel.uiState.collectAsState()

    LaunchedEffect(Unit) { viewModel.loadWishlist() }

    Scaffold(
        floatingActionButton = { AddWishlistFab { k, p -> viewModel.addWishlist(k, p) } }
    ) { inner ->
        when {
            state.isLoading -> { CircularProgressIndicator() }
            state.errorMessage != null -> { Text(state.errorMessage ?: "오류") }
            state.wishlists.isEmpty() -> { Text("관심상품이 없습니다.") }
            else -> {
                LazyColumn(Modifier.padding(inner).padding(12.dp)) {
                    items(state.wishlists, key = { it.id }) { item ->
                        WishlistSwipeToDismiss(onConfirmDelete = { viewModel.deleteWishlist(item.id) }) {
                            WishlistCard(
                                item = item,
                                onDelete = { viewModel.deleteWishlist(item.id) },
                                onCheckPrice = { viewModel.checkPrice(item.id) }
                            )
                        }
                    }
                }
            }
        }
    }
}

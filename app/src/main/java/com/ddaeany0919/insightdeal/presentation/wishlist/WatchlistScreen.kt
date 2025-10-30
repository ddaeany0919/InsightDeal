package com.ddaeany0919.insightdeal.presentation.wishlist

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.input.KeyboardOptions
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
            state.isLoading -> {
                Box(Modifier.fillMaxSize().padding(inner), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            }
            state.errorMessage != null -> {
                Column(
                    Modifier.fillMaxSize().padding(inner).padding(16.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(text = state.errorMessage ?: "오류가 발생했습니다")
                    Spacer(Modifier.height(12.dp))
                    Button(onClick = { viewModel.loadWishlist() }) { Text("다시 시도") }
                }
            }
            state.wishlists.isEmpty() -> {
                Column(
                    Modifier.fillMaxSize().padding(inner).padding(16.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("관심상품이 없습니다. + 버튼으로 추가해보세요!")
                }
            }
            else -> {
                LazyColumn(Modifier.fillMaxSize().padding(inner).padding(12.dp)) {
                    items(state.wishlists) { item ->
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

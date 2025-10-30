package com.ddaeany0919.insightdeal.presentation.wishlist

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ddaeany0919.insightdeal.WishlistCard

@Composable
fun WatchlistScreen(
    viewModel: WishlistViewModel = androidx.lifecycle.viewmodel.compose.viewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) { viewModel.loadWishlist() }

    Scaffold(
        floatingActionButton = { AddWishlistFab { k, p -> viewModel.addItem(k, p) } }
    ) { inner ->
        when (state) {
            is WishlistState.Loading -> {
                Box(
                    modifier = Modifier.fillMaxSize().padding(inner),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }
            is WishlistState.Error -> {
                Column(
                    modifier = Modifier.fillMaxSize().padding(inner).padding(16.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(text = state.message)
                    Spacer(Modifier.height(12.dp))
                    Button(onClick = { viewModel.retry() }) { Text("다시 시도") }
                }
            }
            is WishlistState.Empty -> {
                Column(
                    modifier = Modifier.fillMaxSize().padding(inner).padding(16.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("관심상품이 없습니다.")
                }
            }
            is WishlistState.Success -> {
                LazyColumn(Modifier.fillMaxSize().padding(inner).padding(12.dp)) {
                    items(
                        items = state.items, 
                        key = { item: WishlistItem -> item.id }
                    ) { item: WishlistItem ->
                        WishlistSwipeToDismiss(
                            onConfirmDelete = { viewModel.deleteItem(item) }
                        ) {
                            WishlistCard(
                                item = item,
                                onDelete = { viewModel.deleteItem(item) },
                                onCheckPrice = { viewModel.checkPrice(item) }
                            )
                        }
                    }
                }
            }
        }
    }
}

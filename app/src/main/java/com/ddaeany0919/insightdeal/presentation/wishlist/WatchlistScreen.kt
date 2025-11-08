package com.ddaeany0919.insightdeal.presentation.wishlist

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel

@Composable
fun WatchlistScreen(
    viewModel: WishlistViewModel = viewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    var showDialog by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) { viewModel.loadWishlist() }

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(onClick = { showDialog = true }) {
                Icon(Icons.Filled.Add, contentDescription = "추가")
            }
        }
    ) { inner ->
        if (showDialog) {
            AddWishlistUI(
                onDismiss = { showDialog = false },
                onAdd = { keyword: String, productUrl: String, targetPrice: Int ->
                    viewModel.addItem(keyword, productUrl, targetPrice)
                    showDialog = false
                }
            )
        }

        val currentState = state
        when (currentState) {
            is WishlistUiState.Loading -> {
                Box(
                    modifier = Modifier.fillMaxSize().padding(inner),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }
            is WishlistUiState.Error -> {
                Column(
                    modifier = Modifier.fillMaxSize().padding(inner).padding(16.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(text = currentState.message)
                    Spacer(Modifier.height(12.dp))
                    Button(onClick = { viewModel.retry() }) { Text("다시 시도") }
                }
            }
            is WishlistUiState.Empty -> {
                Column(
                    modifier = Modifier.fillMaxSize().padding(inner).padding(16.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("관심상품이 없습니다.")
                }
            }
            is WishlistUiState.Success -> {
                LazyColumn(Modifier.fillMaxSize().padding(inner).padding(12.dp)) {
                    items(
                        items = currentState.items,
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

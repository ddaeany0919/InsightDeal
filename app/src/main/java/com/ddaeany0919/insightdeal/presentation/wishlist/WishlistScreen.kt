package com.ddaeany0919.insightdeal.presentation.wishlist

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WishlistScreen(
    viewModel: WishlistViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        snackbarHost = { SnackbarHost(hostState = viewModel.snackbarHostState) },
        floatingActionButton = {
            FloatingActionButton(onClick = { /* show add dialog */ }) {
                Icon(Icons.Default.Add, contentDescription = "추가")
            }
        }
    ) { inner ->
        if (uiState.wishlists.isEmpty()) {
            Text("관심상품이 없습니다", modifier = Modifier.padding(inner).padding(16.dp))
        } else {
            LazyColumn(
                modifier = Modifier.padding(inner).padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(uiState.wishlists, key = { it.id }) { item ->
                    WishlistSwipeToDismiss(
                        onConfirmDelete = {
                            viewModel.deleteWishlist(item.id)
                            viewModel.offerUndo(item)
                        },
                        content = {
                            WishlistCard(
                                wishlist = item,
                                onClick = { /* open */ },
                                onDeleteClick = {
                                    viewModel.deleteWishlist(item.id)
                                    viewModel.offerUndo(item)
                                },
                                onPriceCheckClick = { viewModel.checkPrice(item.id) }
                            )
                        }
                    )
                }
            }
        }
    }
}

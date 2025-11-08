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
import android.util.Log

@Composable
fun WatchlistScreen(
    viewModel: WishlistViewModel = viewModel(),
    onItemClick: (WishlistItem) -> Unit
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    var showDialog by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) { 
        Log.d("WatchlistScreen", "LaunchedEffect: loadWishlist 호출")
        viewModel.loadWishlist() 
    }

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(onClick = { 
                Log.d("WatchlistScreen", "FAB 클릭: 다이얼로그 표시")
                showDialog = true 
            }) {
                Icon(Icons.Filled.Add, contentDescription = "추가")
            }
        }
    ) { inner ->
        if (showDialog) {
            Log.d("WatchlistScreen", "AddWishlistDialogContent 표시")
            AddWishlistDialogContent(
                onDismiss = { 
                    Log.d("WatchlistScreen", "다이얼로그 닫기")
                    showDialog = false 
                },
                onSubmit = { keyword: String, targetPrice: Int ->
                    Log.d("WatchlistScreen", "상품 추가: keyword=$keyword, targetPrice=$targetPrice")
                    viewModel.addItem(keyword, "", targetPrice)
                    showDialog = false
                }
            )
        }

        val currentState = state
        Log.d("WatchlistScreen", "현재 상태: ${currentState::class.simpleName}")
        
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
                    Button(onClick = { 
                        Log.d("WatchlistScreen", "다시 시도 버튼 클릭")
                        viewModel.retry() 
                    }) { 
                        Text("다시 시도") 
                    }
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
                Log.d("WatchlistScreen", "상품 목록 표시: ${currentState.items.size}개")
                LazyColumn(Modifier.fillMaxSize().padding(inner).padding(12.dp)) {
                    items(
                        items = currentState.items,
                        key = { item: WishlistItem -> item.id }
                    ) { item: WishlistItem ->
                        WishlistSwipeToDismiss(
                            onConfirmDelete = { 
                                Log.d("WatchlistScreen", "상품 삭제: id=${item.id}")
                                viewModel.deleteItem(item) 
                            }
                        ) {
                            WishlistCard(
                                item = item,
                                onDelete = { 
                                    Log.d("WatchlistScreen", "상품 삭제 요청: id=${item.id}")
                                    viewModel.deleteItem(item) 
                                },
                                onCheckPrice = { 
                                    Log.d("WatchlistScreen", "가격 확인 요청: id=${item.id}")
                                    viewModel.checkPrice(item) 
                                },
                                onClick = { onItemClick(item) }
                            )
                        }
                    }
                }
            }
        }
    }
}

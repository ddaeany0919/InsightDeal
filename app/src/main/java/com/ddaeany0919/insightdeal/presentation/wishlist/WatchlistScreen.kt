package com.ddaeany0919.insightdeal.presentation.wishlist

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.font.FontWeight
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import android.util.Log
import com.ddaeany0919.insightdeal.ui.theme.PriceBest

@Composable
fun WatchlistDashboardHeader(items: List<WishlistItem>) {
    val totalCount = items.size
    val targetReachedCount = items.count { it.isTargetReached || (it.currentLowestPrice != null && it.targetPrice >= it.currentLowestPrice) }
    
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer
        )
    ) {
        Row(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceAround,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("전체 상품", style = MaterialTheme.typography.labelMedium)
                Text("$totalCount", style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold))
            }
            VerticalDivider(modifier = Modifier.height(40.dp).width(1.dp), color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.2f))
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("목표 달성", style = MaterialTheme.typography.labelMedium)
                Text(
                    "$targetReachedCount", 
                    style = MaterialTheme.typography.headlineMedium.copy(
                        fontWeight = FontWeight.Bold,
                        color = if (targetReachedCount > 0) PriceBest else MaterialTheme.colorScheme.onPrimaryContainer
                    )
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WatchlistScreen(
    viewModel: WishlistViewModel = viewModel(),
    onItemClick: (WishlistItem) -> Unit
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val itemPriceHistories by viewModel.itemPriceHistories.collectAsStateWithLifecycle()
    var showDialog by remember { mutableStateOf(false) }
    var expandedItemId by remember { mutableStateOf<Int?>(null) }

    LaunchedEffect(Unit) { 
        Log.d("WatchlistScreen", "LaunchedEffect: loadWishlist 호출")
        viewModel.loadWishlist() 
    }

    Scaffold(
        topBar = {
            LargeTopAppBar(
                title = {
                    Text(
                        "Insight Deal",
                        style = MaterialTheme.typography.displaySmall.copy(fontWeight = FontWeight.Bold)
                    )
                },
                colors = TopAppBarDefaults.largeTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onBackground,
                    scrolledContainerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { 
                    Log.d("WatchlistScreen", "FAB 클릭: 다이얼로그 표시")
                    showDialog = true 
                },
                containerColor = MaterialTheme.colorScheme.secondary,
                contentColor = MaterialTheme.colorScheme.onSecondary,
                elevation = FloatingActionButtonDefaults.elevation(8.dp)
            ) {
                Icon(Icons.Filled.Add, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("상품 추가", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
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
                LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(inner),
                    contentPadding = PaddingValues(bottom = 80.dp)
                ) {
                    item {
                        WatchlistDashboardHeader(items = currentState.items)
                    }
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
                                onClick = { onItemClick(item) },
                                isExpanded = expandedItemId == item.id,
                                onExpand = {
                                    Log.d("WatchlistScreen", "onExpand called for ${item.keyword}")
                                    if (expandedItemId == item.id) {
                                        expandedItemId = null
                                    } else {
                                        expandedItemId = item.id
                                        viewModel.loadItemHistory(item)
                                    }
                                },
                                priceHistory = itemPriceHistories[item.id]
                            )
                        }
                    }
                }
            }
        }
    }
}

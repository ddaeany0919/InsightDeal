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
                Text("?ÑÏ≤¥ ?ÅÌíà", style = MaterialTheme.typography.labelMedium)
                Text("$totalCount", style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold))
            }
            VerticalDivider(modifier = Modifier.height(40.dp).width(1.dp), color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.2f))
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Î™©Ìëú ?¨ÏÑ±", style = MaterialTheme.typography.labelMedium)
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
        Log.d("WatchlistScreen", "LaunchedEffect: loadWishlist ?∏Ï∂ú")
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
                    Log.d("WatchlistScreen", "FAB ?¥Î¶≠: ?§Ïù¥?ºÎ°úÍ∑??úÏãú")
                    showDialog = true 
                },
                containerColor = MaterialTheme.colorScheme.secondary,
                contentColor = MaterialTheme.colorScheme.onSecondary,
                elevation = FloatingActionButtonDefaults.elevation(8.dp)
            ) {
                Icon(Icons.Filled.Add, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("?ÅÌíà Ï∂îÍ?", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
            }
        }
    ) { inner ->
        if (showDialog) {
            Log.d("WatchlistScreen", "AddWishlistDialogContent ?úÏãú")
            AddWishlistDialogContent(
                onDismiss = { 
                    Log.d("WatchlistScreen", "?§Ïù¥?ºÎ°úÍ∑??´Í∏∞")
                    showDialog = false 
                },
                onSubmit = { keyword: String, targetPrice: Int ->
                    Log.d("WatchlistScreen", "?ÅÌíà Ï∂îÍ?: keyword=$keyword, targetPrice=$targetPrice")
                    viewModel.addItem(keyword, "", targetPrice)
                    showDialog = false
                }
            )
        }

        val currentState = state
        Log.d("WatchlistScreen", "?ÑÏû¨ ?ÅÌÉú: ${currentState::class.simpleName}")
        
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
                        Log.d("WatchlistScreen", "?§Ïãú ?úÎèÑ Î≤ÑÌäº ?¥Î¶≠")
                        viewModel.retry() 
                    }) { 
                        Text("?§Ïãú ?úÎèÑ") 
                    }
                }
            }
            is WishlistUiState.Empty -> {
                Column(
                    modifier = Modifier.fillMaxSize().padding(inner).padding(16.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("Í¥Ä?¨ÏÉÅ?àÏù¥ ?ÜÏäµ?àÎã§.")
                }
            }
            is WishlistUiState.Success -> {
                Log.d("WatchlistScreen", "?ÅÌíà Î™©Î°ù ?úÏãú: ${currentState.items.size}Í∞?)
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
                                Log.d("WatchlistScreen", "?ÅÌíà ??†ú: id=${item.id}")
                                viewModel.deleteItem(item) 
                            }
                        ) {
                            com.ddaeany0919.insightdeal.ui.components.StandardWishlistCard(style = com.ddaeany0919.insightdeal.ui.components.WishlistCardStyle.DETAILED,
                                item = item,
                                onDelete = { 
                                    Log.d("WatchlistScreen", "?ÅÌíà ??†ú ?îÏ≤≠: id=${item.id}")
                                    viewModel.deleteItem(item) 
                                },
                                onCheckPrice = { 
                                    Log.d("WatchlistScreen", "Í∞ÄÍ≤??ïÏù∏ ?îÏ≤≠: id=${item.id}")
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

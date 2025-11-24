package com.ddaeany0919.insightdeal.presentation.wishlist

import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.material3.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

// 필요한 데이터 클래스나 기타 import는 상황에 맞게 추가

suspend fun SnackbarHostState.offerUndo(
    message: String,
    actionLabel: String = "실행 취소",
    onUndo: () -> Unit
): Boolean {
    val result = showSnackbar(
        message = message,
        actionLabel = actionLabel
    )
    return if (result == SnackbarResult.ActionPerformed) {
        onUndo()
        true
    } else {
        false
    }
}

@Composable
fun LoadingState() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        CircularProgressIndicator()
    }
}

@Composable
fun EmptyWishlistState(onAddItemClick: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "위시리스트가 비어있습니다",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "관심 있는 상품을 추가해보세요",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(24.dp))
        Button(
            onClick = onAddItemClick,
            modifier = Modifier.padding(horizontal = 32.dp)
        ) {
            Icon(Icons.Default.Add, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text("아이템 추가")
        }
    }
}

@Composable
fun AddWishlistDialog(
    showDialog: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (String, String, Int) -> Unit
) {
    var keyword by remember { mutableStateOf("") }
    var productUrl by remember { mutableStateOf("") }
    var targetPrice by remember { mutableStateOf("") }
    var isError by remember { mutableStateOf(false) }

    if (showDialog) {
        AlertDialog(
            onDismissRequest = {
                keyword = ""
                productUrl = ""
                targetPrice = ""
                isError = false
                onDismiss()
            },
            title = { Text("위시리스트 아이템 추가") },
            text = {
                Column {
                    OutlinedTextField(
                        value = keyword,
                        onValueChange = { keyword = it },
                        label = { Text("상품명 또는 키워드") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = productUrl,
                        onValueChange = { productUrl = it },
                        label = { Text("상품 링크(URL)") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = targetPrice,
                        onValueChange = { value ->
                            if (value.all { it.isDigit() }) {
                                targetPrice = value
                                isError = false
                            }
                        },
                        label = { Text("목표 가격 (원)") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        isError = isError,
                        supportingText = if (isError) { { Text("올바른 가격을 입력해주세요") } } else null
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val price = targetPrice.toIntOrNull()
                    if (keyword.isNotBlank() && productUrl.isNotBlank() && price != null && price > 0) {
                        onConfirm(keyword, productUrl, price)
                        keyword = ""
                        productUrl = ""
                        targetPrice = ""
                        isError = false
                    } else {
                        isError = true
                    }
                }) {
                    Text("추가")
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    keyword = ""
                    productUrl = ""
                    targetPrice = ""
                    isError = false
                    onDismiss()
                }) {
                    Text("취소")
                }
            }
        )
    }
}

@Composable
fun DashboardHeader(items: List<WishlistItem>) {
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
                        color = if (targetReachedCount > 0) com.ddaeany0919.insightdeal.ui.theme.PriceBest else MaterialTheme.colorScheme.onPrimaryContainer
                    )
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WishlistScreen(viewModel: WishlistViewModel = viewModel()) {
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val itemPriceHistories by viewModel.itemPriceHistories.collectAsStateWithLifecycle()
    var showDialog by remember { mutableStateOf(false) }
    var expandedItemId by remember { mutableStateOf<Int?>(null) }

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
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { showDialog = true },
                containerColor = MaterialTheme.colorScheme.secondary,
                contentColor = MaterialTheme.colorScheme.onSecondary,
                elevation = FloatingActionButtonDefaults.elevation(8.dp)
            ) {
                Icon(Icons.Default.Add, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("상품 추가", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
            }
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { paddingValues ->
        val currentState = uiState
        when (currentState) {
            is WishlistUiState.Loading -> LoadingState()
            is WishlistUiState.Empty -> EmptyWishlistState { showDialog = true }
            is WishlistUiState.Success -> LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentPadding = PaddingValues(bottom = 80.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item {
                    DashboardHeader(items = currentState.items)
                }
                items(items = currentState.items, key = { it.id }) { item ->
                    WishlistCard(
                        item = item,
                        onDelete = {
                            viewModel.deleteItem(item)
                            scope.launch {
                                snackbarHostState.offerUndo(
                                    message = "${item.keyword}을(를) 삭제했습니다",
                                    onUndo = {
                                        viewModel.restoreItem(item)
                                    }
                                )
                            }
                        },
                        onCheckPrice = { viewModel.checkPrice(item) },
                        isExpanded = expandedItemId == item.id,
                        onExpand = {
                            Log.d("WishlistScreen", "onExpand called for ${item.keyword}, current expanded: $expandedItemId")
                            if (expandedItemId == item.id) {
                                expandedItemId = null
                            } else {
                                expandedItemId = item.id
                                viewModel.loadItemHistory(item)
                            }
                        },
                        priceHistory = itemPriceHistories[item.id]
                    )
                    // Add Graph below card if expanded (simplified for now, just showing placeholder)
                    // In a real app, we would fetch history for this item
                    // PriceHistoryGraph(dataPoints = emptyList(), modifier = Modifier.height(100.dp).fillMaxWidth())
                }
            }
            is WishlistUiState.Error -> {
                Column(
                    modifier = Modifier.fillMaxSize().padding(paddingValues),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = "오류가 발생했습니다",
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.error
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = currentState.message,
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(onClick = { viewModel.retry() }) { Text("다시 시도") }
                }
            }
        }
        AddWishlistDialog(
            showDialog = showDialog,
            onDismiss = { showDialog = false },
            onConfirm = { keyword: String, productUrl: String, targetPrice: Int ->
                viewModel.addItem(keyword, productUrl, targetPrice)
                showDialog = false
                scope.launch {
                    snackbarHostState.showSnackbar(message = "$keyword ($productUrl) 위시리스트에 추가됨")
                }
            }
        )
    }
}

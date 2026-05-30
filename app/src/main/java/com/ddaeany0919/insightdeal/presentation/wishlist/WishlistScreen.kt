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
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Color
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import androidx.paging.compose.collectAsLazyPagingItems

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
fun EmptyWishlistState() {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "관심 목록이 비어있습니다",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "마음에 드는 핫딜에 하트를 눌러보세요!",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
            textAlign = TextAlign.Center
        )
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
                        label = { Text("목표 가격(원)") },
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
    val isDark = isSystemInDarkTheme()
    
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        shape = RoundedCornerShape(20.dp),
        border = BorderStroke(1.dp, if (isDark) Color(0xFF2C2C2E) else Color(0xFFE5E7EB)),
        colors = CardDefaults.cardColors(
            containerColor = if (isDark) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f) else Color(0xFFF2F4F7),
            contentColor = MaterialTheme.colorScheme.onSurface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Row(
            modifier = Modifier
                .padding(vertical = 20.dp, horizontal = 16.dp)
                .fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceAround,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = "전체 상품", 
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                )
                Text(
                    text = "$totalCount", 
                    style = MaterialTheme.typography.headlineMedium.copy(
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                )
            }
            
            VerticalDivider(
                modifier = Modifier.height(36.dp).width(1.dp), 
                color = if (isDark) Color(0xFF3A3A3C) else Color(0xFFD1D5DB)
            )
            
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = "목표 달성", 
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = "$targetReachedCount", 
                        style = MaterialTheme.typography.headlineMedium.copy(
                            fontWeight = FontWeight.ExtraBold,
                            color = if (targetReachedCount > 0) Color(0xFF3182F6) else MaterialTheme.colorScheme.onSurface
                        )
                    )
                    if (targetReachedCount > 0) {
                        Text("🎉", fontSize = 18.sp)
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WishlistScreen(viewModel: WishlistViewModel = viewModel(), onBack: () -> Unit = {}) {
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val itemPriceHistories by viewModel.itemPriceHistories.collectAsStateWithLifecycle()
    val lazyPagingItems = viewModel.wishlistPagedFlow.collectAsLazyPagingItems()
    var expandedItemId by remember { mutableStateOf<Int?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "찜한 목록",
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "뒤로가기")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onBackground
                )
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = MaterialTheme.colorScheme.background
    ) { paddingValues ->
        val currentState = uiState
        when (currentState) {
            is WishlistUiState.Loading -> LoadingState()
            is WishlistUiState.Empty -> EmptyWishlistState()
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
                items(
                    count = lazyPagingItems.itemCount,
                    key = { index -> lazyPagingItems[index]?.id ?: index }
                ) { index ->
                    val item = lazyPagingItems[index] ?: return@items
                    val dismissState = rememberSwipeToDismissBoxState(
                        confirmValueChange = { dismissValue ->
                            if (dismissValue == SwipeToDismissBoxValue.EndToStart || dismissValue == SwipeToDismissBoxValue.StartToEnd) {
                                viewModel.deleteItem(item)
                                scope.launch {
                                    snackbarHostState.offerUndo(
                                        message = "${item.keyword}을(를) 삭제했습니다",
                                        onUndo = { viewModel.restoreItem(item) }
                                    )
                                }
                                true
                            } else false
                        }
                    )

                    SwipeToDismissBox(
                        state = dismissState,
                        backgroundContent = {
                            val color by androidx.compose.animation.animateColorAsState(
                                targetValue = when (dismissState.targetValue) {
                                    SwipeToDismissBoxValue.Settled -> androidx.compose.ui.graphics.Color.Transparent
                                    else -> MaterialTheme.colorScheme.errorContainer
                                }
                            )
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(vertical = 8.dp, horizontal = 4.dp)
                                    .background(color, shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp))
                                    .padding(horizontal = 20.dp),
                                contentAlignment = Alignment.CenterEnd
                            ) {
                                Icon(Icons.Default.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.onErrorContainer)
                            }
                        },
                        content = {
                            com.ddaeany0919.insightdeal.presentation.components.StandardWishlistCard(
                                style = com.ddaeany0919.insightdeal.presentation.components.WishlistCardStyle.DETAILED,
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
                        }
                    )
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
    }
}

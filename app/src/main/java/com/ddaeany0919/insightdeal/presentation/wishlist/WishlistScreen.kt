package com.ddaeany0919.insightdeal.presentation.wishlist

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.launch
import java.time.format.DateTimeFormatter
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WishlistScreenDetailed(
    viewModel: WishlistViewModel = viewModel()
) {
    var showAddDialog by remember { mutableStateOf(false) }
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(Unit) { viewModel.loadWishlist() }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            FloatingActionButton(onClick = { showAddDialog = true }) {
                Icon(Icons.Filled.Add, contentDescription = "관심상품 추가")
            }
        }
    ) { inner ->
        // Smart cast 문제 해결을 위해 지역 변수에 할당
        val currentState = uiState
        when (currentState) {
            is WishlistState.Loading -> {
                LoadingStateDetailed(modifier = Modifier.padding(inner))
            }
            is WishlistState.Empty -> {
                EmptyWishlistStateDetailed(
                    onAdd = { showAddDialog = true }, 
                    modifier = Modifier.padding(inner)
                )
            }
            is WishlistState.Success -> {
                LazyColumn(
                    modifier = Modifier.padding(inner).padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(
                        items = currentState.items, 
                        key = { item: WishlistItem -> item.id }
                    ) { item: WishlistItem ->
                        WishlistSwipeToDismiss(
                            onConfirmDelete = {
                                viewModel.deleteItem(item)
                                scope.launch {
                                    val result = snackbarHostState.showSnackbar(
                                        message = "${item.keyword}을(를) 삭제했습니다",
                                        actionLabel = "실행취소"
                                    )
                                    if (result == SnackbarResult.ActionPerformed) {
                                        viewModel.restoreItem(item)
                                    }
                                }
                            },
                            content = {
                                WishlistCardDetailed(
                                    wishlist = item,
                                    onDeleteClick = {
                                        viewModel.deleteItem(item)
                                        scope.launch {
                                            val result = snackbarHostState.showSnackbar(
                                                message = "${item.keyword}을(를) 삭제했습니다",
                                                actionLabel = "실행취소"
                                            )
                                            if (result == SnackbarResult.ActionPerformed) {
                                                viewModel.restoreItem(item)
                                            }
                                        }
                                    },
                                    onPriceCheckClick = { viewModel.checkPrice(item) }
                                )
                            }
                        )
                    }
                }
            }
            is WishlistState.Error -> {
                androidx.compose.foundation.layout.Column(
                    modifier = Modifier.fillMaxSize().padding(inner).padding(16.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
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
                    Button(
                        onClick = { viewModel.retry() }
                    ) {
                        Text("다시 시도")
                    }
                }
            }
        }

        if (showAddDialog) {
            AddWishlistDialogDetailed(
                onDismiss = { showAddDialog = false },
                onAdd = { keyword: String, targetPrice: Int ->
                    viewModel.addItem(keyword, targetPrice)
                    showAddDialog = false
                    scope.launch {
                        snackbarHostState.showSnackbar(
                            message = "$keyword 을(를) 위시리스트에 추가했습니다"
                        )
                    }
                }
            )
        }
    }
}

@Composable
private fun LoadingStateDetailed(modifier: Modifier = Modifier) {
    androidx.compose.foundation.layout.Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        CircularProgressIndicator()
    }
}

@Composable
private fun EmptyWishlistStateDetailed(
    onAdd: () -> Unit,
    modifier: Modifier = Modifier
) {
    androidx.compose.foundation.layout.Column(
        modifier = modifier.fillMaxSize(),
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
            onClick = onAdd,
            modifier = Modifier.padding(horizontal = 32.dp)
        ) {
            Icon(Icons.Default.Add, contentDescription = null)
            Spacer(modifier = Modifier.size(8.dp))
            Text("아이템 추가")
        }
    }
}

@Composable
private fun AddWishlistDialogDetailed(
    onDismiss: () -> Unit,
    onAdd: (String, Int) -> Unit
) {
    var keyword by remember { mutableStateOf("") }
    var targetPrice by remember { mutableStateOf("") }
    var isError by remember { mutableStateOf(false) }
    
    AlertDialog(
        onDismissRequest = {
            keyword = ""
            targetPrice = ""
            isError = false
            onDismiss()
        },
        title = { Text("위시리스트 아이템 추가") },
        text = {
            androidx.compose.foundation.layout.Column {
                OutlinedTextField(
                    value = keyword,
                    onValueChange = { keyword = it },
                    label = { Text("상품 키워드") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                Spacer(modifier = Modifier.height(16.dp))
                OutlinedTextField(
                    value = targetPrice,
                    onValueChange = { value ->
                        // 숫자만 입력 받도록 필터링
                        if (value.all { it.isDigit() }) {
                            targetPrice = value
                            isError = false
                        }
                    },
                    label = { Text("목표 가격 (원)") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    isError = isError,
                    supportingText = if (isError) {
                        { Text("올바른 가격을 입력해주세요") }
                    } else null
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val price = targetPrice.toIntOrNull()
                    if (keyword.isNotBlank() && price != null && price > 0) {
                        onAdd(keyword, price)
                        keyword = ""
                        targetPrice = ""
                        isError = false
                    } else {
                        isError = true
                    }
                }
            ) {
                Text("추가")
            }
        },
        dismissButton = {
            TextButton(
                onClick = {
                    keyword = ""
                    targetPrice = ""
                    isError = false
                    onDismiss()
                }
            ) {
                Text("취소")
            }
        }
    )
}

@Composable
private fun WishlistCardDetailed(
    wishlist: WishlistItem,
    onDeleteClick: () -> Unit,
    onPriceCheckClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp)),
        colors = CardDefaults.cardColors(
            containerColor = if (wishlist.isTargetReached) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surface
        )
    ) {
        androidx.compose.foundation.layout.Column(Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = wishlist.keyword,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
                // 삭제 버튼
                OutlinedIconButton(onClick = onDeleteClick, colors = IconButtonDefaults.outlinedIconButtonColors(contentColor = Color(0xFFFF5722))) {
                    Icon(Icons.Filled.Delete, contentDescription = "삭제")
                }
            }

            Spacer(Modifier.height(12.dp))

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                androidx.compose.foundation.layout.Column {
                    Text("목표 가격", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("${String.format("%,d", wishlist.targetPrice)}원", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold)
                }
                androidx.compose.foundation.layout.Column(horizontalAlignment = Alignment.End) {
                    Text("현재 최저가", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    val price = wishlist.currentLowestPrice
                    if (price != null) {
                        Text(
                            text = "${String.format("%,d", price)}원",
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Bold,
                            color = if (price <= wishlist.targetPrice) Color(0xFF4CAF50) else MaterialTheme.colorScheme.onSurface
                        )
                        wishlist.currentLowestPlatform?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    } else {
                        Text("검색 중...", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }

            Spacer(Modifier.height(12.dp))
            val progress = (wishlist.currentLowestPrice?.toFloat()?.div(wishlist.targetPrice)?.coerceAtMost(1f)) ?: 1f
            LinearProgressIndicator(progress = progress, modifier = Modifier.fillMaxWidth(), color = if (progress <= 1f) Color(0xFF4CAF50) else Color(0xFFFF5722), trackColor = MaterialTheme.colorScheme.surfaceVariant)

            Spacer(Modifier.height(12.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onPriceCheckClick, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Filled.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.size(4.dp))
                    Text("가격 체크")
                }
                // 보조 삭제 버튼(텍스트 버튼)
                OutlinedButton(onClick = onDeleteClick, colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFFF5722))) {
                    Icon(Icons.Filled.Delete, contentDescription = "삭제", modifier = Modifier.size(16.dp))
                }
            }

            wishlist.lastChecked?.let { lastChecked ->
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "마지막 체크: ${lastChecked.format(DateTimeFormatter.ofPattern("MM/dd HH:mm", Locale.KOREAN))}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

package com.ddaeany0919.insightdeal

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ddaeany0919.insightdeal.presentation.wishlist.WishlistItem
import com.ddaeany0919.insightdeal.presentation.wishlist.WishlistState
import com.ddaeany0919.insightdeal.presentation.wishlist.WishlistViewModel
import kotlinx.coroutines.launch

// SnackbarHostState 확장 함수
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

// 로딩 상태 컴포저블
@Composable
fun LoadingState() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        CircularProgressIndicator()
    }
}

// 빈 위시리스트 상태 컴포저블
@Composable
fun EmptyWishlistState(
    onAddItemClick: () -> Unit
) {
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

// 위시리스트 아이템 추가 다이얼로그
@Composable
fun AddWishlistDialog(
    showDialog: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (String, Int) -> Unit
) {
    var keyword by remember { mutableStateOf("") }
    var targetPrice by remember { mutableStateOf("") }
    var isError by remember { mutableStateOf(false) }
    
    if (showDialog) {
        AlertDialog(
            onDismissRequest = {
                keyword = ""
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
                            onConfirm(keyword, price)
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
}

// 메인 위시리스트 스크린
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WishlistScreen(
    viewModel: WishlistViewModel = viewModel()
) {
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var showDialog by remember { mutableStateOf(false) }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("위시리스트") }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showDialog = true }
            ) {
                Icon(Icons.Default.Add, contentDescription = "아이템 추가")
            }
        }
    ) { paddingValues ->
        // Smart cast 문제 해결을 위해 지역 변수에 할당
        val currentState = uiState
        when (currentState) {
            is WishlistState.Loading -> {
                LoadingState()
            }
            is WishlistState.Empty -> {
                EmptyWishlistState {
                    showDialog = true
                }
            }
            is WishlistState.Success -> {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(
                        items = currentState.items,
                        key = { item: WishlistItem -> item.id }
                    ) { item: WishlistItem ->
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
                            onCheckPrice = {
                                viewModel.checkPrice(item)
                            }
                        )
                    }
                }
            }
            is WishlistState.Error -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues),
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
                    Button(
                        onClick = { viewModel.retry() }
                    ) {
                        Text("다시 시도")
                    }
                }
            }
        }
        
        AddWishlistDialog(
            showDialog = showDialog,
            onDismiss = { showDialog = false },
            onConfirm = { keyword: String, targetPrice: Int ->
                viewModel.addItem(keyword, targetPrice)
                showDialog = false
                scope.launch {
                    snackbarHostState.showSnackbar(
                        message = "$keyword 을(를) 위시리스트에 추가했습니다"
                    )
                }
            }
        )
    }
}

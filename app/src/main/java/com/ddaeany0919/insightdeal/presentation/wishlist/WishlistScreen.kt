package com.ddaeany0919.insightdeal.presentation.wishlist

import android.widget.Toast
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.launch
import java.text.NumberFormat
import java.time.format.DateTimeFormatter
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WishlistScreenDetailed(
    viewModel: WishlistViewModel = viewModel()
) {
    var showAddDialog by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var pendingDelete: WishlistItem? by remember { mutableStateOf(null) }

    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    val numberFmt = remember { NumberFormat.getIntegerInstance(Locale.KOREAN) }
    val timeFmt = remember { DateTimeFormatter.ofPattern("MM/dd HH:mm", Locale.KOREAN) }
    val ctx = LocalContext.current

    LaunchedEffect(Unit) { viewModel.loadWishlist() }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            FloatingActionButton(onClick = { showAddDialog = true }) {
                Icon(Icons.Filled.Add, contentDescription = "관심상품 추가")
            }
        }
    ) { inner ->
        when (val currentState = uiState) {
            is WishlistState.Loading -> LoadingStateDetailed(modifier = Modifier.padding(inner))
            is WishlistState.Empty -> EmptyWishlistStateDetailed(onAdd = { showAddDialog = true }, modifier = Modifier.padding(inner))
            is WishlistState.Success -> {
                LazyColumn(
                    modifier = Modifier.padding(inner).padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(currentState.items, key = { it.id }) { item ->
                        WishlistCardDetailed(
                            wishlist = item,
                            onDeleteClick = {
                                // 버튼 삭제: 확인 다이얼로그
                                pendingDelete = item
                                showDeleteDialog = true
                            },
                            onSwipeDelete = {
                                // 스와이프 삭제: 즉시 삭제 + 짧은 토스트
                                scope.launch {
                                    val ok = runCatching { viewModel.deleteItem(item) }.isSuccess
                                    if (ok) Toast.makeText(ctx, "삭제되었습니다", Toast.LENGTH_SHORT).show()
                                }
                            },
                            onPriceCheckClick = { viewModel.checkPrice(item) },
                            numberFmt = numberFmt,
                            timeFmt = timeFmt
                        )
                    }
                }
            }
            is WishlistState.Error -> {
                Column(
                    modifier = Modifier.fillMaxSize().padding(inner).padding(16.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(text = "오류가 발생했습니다", style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.error)
                    Spacer(Modifier.height(8.dp))
                    Text(text = currentState.message, style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center)
                    Spacer(Modifier.height(16.dp))
                    Button(onClick = { viewModel.retry() }) { Text("다시 시도") }
                }
            }
        }

        if (showAddDialog) {
            AddWishlistDialogDetailed(
                onDismiss = { showAddDialog = false },
                onAdd = { keyword, targetPrice ->
                    viewModel.addItem(keyword, targetPrice)
                    showAddDialog = false
                    scope.launch { snackbarHostState.showSnackbar(message = "$keyword 을(를) 위시리스트에 추가했습니다") }
                }
            )
        }

        if (showDeleteDialog && pendingDelete != null) {
            ConfirmDeleteDialog(
                keyword = pendingDelete!!.keyword,
                onConfirm = {
                    val item = pendingDelete
                    showDeleteDialog = false
                    pendingDelete = null
                    if (item != null) {
                        scope.launch {
                            val ok = runCatching { viewModel.deleteItem(item) }.isSuccess
                            if (ok) Toast.makeText(ctx, "삭제되었습니다", Toast.LENGTH_SHORT).show()
                        }
                    }
                },
                onDismiss = {
                    showDeleteDialog = false
                    pendingDelete = null
                }
            )
        }
    }
}

@Composable
private fun LoadingStateDetailed(modifier: Modifier = Modifier) { /* unchanged */ }

@Composable
private fun EmptyWishlistStateDetailed(onAdd: () -> Unit, modifier: Modifier = Modifier) { /* unchanged */ }

@Composable
private fun AddWishlistDialogDetailed(onDismiss: () -> Unit, onAdd: (String, Int) -> Unit) { /* unchanged */ }

@Composable
private fun WishlistCardDetailed(
    wishlist: WishlistItem,
    onDeleteClick: () -> Unit,
    onSwipeDelete: () -> Unit,
    onPriceCheckClick: () -> Unit,
    numberFmt: NumberFormat,
    timeFmt: DateTimeFormatter
) {
    // 간단한 좌/우 스와이프 제스처 감지 → onSwipeDelete 호출
    val swipeModifier = Modifier.pointerInput(wishlist.id) {
        detectDragGestures { change, dragAmount ->
            if (kotlin.math.abs(dragAmount.x) > 60f) {
                change.consume()
                onSwipeDelete()
            }
        }
    }

    Card(
        modifier = Modifier.fillMaxWidth().then(swipeModifier),
        colors = CardDefaults.cardColors(containerColor = if (wishlist.isTargetReached) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surface)
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(text = wishlist.keyword, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                OutlinedIconButton(onClick = onDeleteClick, colors = IconButtonDefaults.outlinedIconButtonColors(contentColor = Color(0xFFFF5722))) {
                    Icon(Icons.Filled.Delete, contentDescription = "삭제")
                }
            }
            Spacer(Modifier.height(12.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Column {
                    Text("목표 가격", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("${'$'}{numberFmt.format(wishlist.targetPrice)}원", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold)
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text("현재 최저가", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    val price = wishlist.currentLowestPrice
                    if (price != null) {
                        Text(text = "${'$'}{numberFmt.format(price)}원", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold, color = if (price <= wishlist.targetPrice) Color(0xFF4CAF50) else MaterialTheme.colorScheme.onSurface)
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
                OutlinedButton(onClick = onPriceCheckClick, modifier = Modifier.weight(1f)) { Icon(Icons.Filled.Refresh, contentDescription = null, modifier = Modifier.size(16.dp)); Spacer(Modifier.size(4.dp)); Text("가격 체크") }
                OutlinedButton(onClick = onDeleteClick, colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFFF5722))) { Icon(Icons.Filled.Delete, contentDescription = "삭제", modifier = Modifier.size(16.dp)) }
            }
            wishlist.lastChecked?.let {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "마지막 체크: ${'$'}{it.format(timeFmt)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

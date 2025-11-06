package com.ddaeany0919.insightdeal.presentation.wishlist

import android.content.res.Resources
import android.util.Log
import android.widget.Toast
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.launch
import java.text.NumberFormat
import java.time.format.DateTimeFormatter
import java.util.Locale

private const val TAG_UI = "WishlistUI"

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
            FloatingActionButton(onClick = {
                Log.d(TAG_UI, "+ 버튼 클릭됨: showAddDialog=true")
                showAddDialog = true
            }) { Icon(Icons.Filled.Add, contentDescription = "관심상품 추가") }
        }
    ) { inner ->
        when (val currentState = uiState) {
            is WishlistState.Loading -> LoadingStateDetailed(modifier = Modifier.padding(inner))
            is WishlistState.Empty -> EmptyWishlistStateDetailed(
                onAdd = {
                    Log.d(TAG_UI, "EmptyState: + 버튼 클릭")
                    showAddDialog = true
                },
                modifier = Modifier.padding(inner)
            )
            is WishlistState.Success -> {
                LazyColumn(
                    modifier = Modifier.padding(inner).padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(currentState.items, key = { it.id }) { item ->
                        SwipeToDeleteContainer(
                            thresholdPx = 120f,
                            backgroundColor = Color(0xFFFFEBEE),
                            icon = Icons.Filled.Delete,
                            iconTint = Color(0xFFE53935),
                            onSwipedLeft = {
                                scope.launch {
                                    val ok = runCatching { viewModel.deleteItem(item) }.isSuccess
                                    Log.d(TAG_UI, "스와이프 삭제: id=${item.id} ok=$ok")
                                    if (ok) Toast.makeText(ctx, "삭제되었습니다", Toast.LENGTH_SHORT).show()
                                }
                            }
                        ) {
                            WishlistCardDetailed(
                                wishlist = item,
                                onDeleteClick = {
                                    pendingDelete = item
                                    showDeleteDialog = true
                                },
                                onPriceCheckClick = { viewModel.checkPrice(item) },
                                numberFmt = numberFmt,
                                timeFmt = timeFmt
                            )
                        }
                    }
                }
            }
            is WishlistState.Error -> {
                Column(
                    modifier = Modifier.fillMaxSize().padding(inner).padding(16.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("오류가 발생했어요. 잠시 후 다시 시도해 주세요", 
                         style = MaterialTheme.typography.bodyLarge, 
                         color = MaterialTheme.colorScheme.error, 
                         textAlign = TextAlign.Center)
                    Spacer(Modifier.height(16.dp))
                    Button(onClick = { viewModel.retry() }) { Text("다시 시도") }
                }
            }
        }
    }

    if (showAddDialog) {
        AddWishlistDialogDetailed(
            onDismiss = {
                Log.d(TAG_UI, "AddDialog: 닫힘")
                showAddDialog = false
            },
            onAdd = { keyword, targetPrice ->
                Log.d(TAG_UI, "AddDialog: onAdd 호출 keyword=$keyword target=$targetPrice")
                viewModel.addItem(keyword, targetPrice)
                showAddDialog = false
            },
            onAddFromLink = { url, target ->
                scope.launch {
                    Log.d(TAG_UI, "AddDialog: onAddFromLink 호출 url=$url target=$target")
                    try {
                        viewModel.addFromLink(url, target)
                        showAddDialog = false
                        Toast.makeText(ctx, "링크로 추가되었습니다", Toast.LENGTH_SHORT).show()
                    } catch (e: Exception) {
                        Log.e(TAG_UI, "LinkTab 실패: ${e.message}")
                        Toast.makeText(ctx, "일시적인 오류가 발생했어요. 다시 시도해 주세요", Toast.LENGTH_LONG).show()
                    }
                }
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
                        Log.d(TAG_UI, "버튼 삭제: id=${item.id} ok=$ok")
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

@Composable
private fun LoadingStateDetailed(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator()
            Spacer(Modifier.height(16.dp))
            Text("가격 정보를 가져오고 있어요...", style = MaterialTheme.typography.bodyLarge)
        }
    }
}

@Composable
private fun EmptyWishlistStateDetailed(onAdd: () -> Unit, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("아직 관심상품이 없어요", style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.height(16.dp))
            Button(onClick = onAdd) { Text("상품 추가하기") }
        }
    }
}

@Composable
private fun WishlistCardDetailed(
    wishlist: WishlistItem,
    onDeleteClick: () -> Unit,
    onPriceCheckClick: () -> Unit,
    numberFmt: NumberFormat,
    timeFmt: DateTimeFormatter
) {
    val price = wishlist.currentLowestPrice
    val isTargetReached = price != null && price <= wishlist.targetPrice
    
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (isTargetReached) 
                MaterialTheme.colorScheme.primaryContainer 
            else 
                MaterialTheme.colorScheme.surface
        )
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(wishlist.keyword, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                IconButton(onClick = onDeleteClick) {
                    Icon(Icons.Filled.Delete, "삭제", tint = MaterialTheme.colorScheme.error)
                }
            }
            Spacer(Modifier.height(12.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Column {
                    Text("목표 가격", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("${numberFmt.format(wishlist.targetPrice)}원", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold)
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text("현재 최저가", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (price != null) {
                        Text("${numberFmt.format(price)}원", 
                             style = MaterialTheme.typography.bodyLarge, 
                             fontWeight = FontWeight.Bold, 
                             color = if (isTargetReached) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
                        wishlist.currentLowestPlatform?.let { 
                            Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) 
                        }
                    } else {
                        Text("검색 중...", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            val progress = price?.toFloat()?.div(wishlist.targetPrice)?.coerceAtMost(1f) ?: 0f
            LinearProgressIndicator(
                progress = progress, 
                modifier = Modifier.fillMaxWidth(), 
                color = if (isTargetReached) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary,
                trackColor = MaterialTheme.colorScheme.surfaceVariant
            )
            Spacer(Modifier.height(12.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onPriceCheckClick, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Filled.Refresh, null, Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("가격 체크")
                }
                OutlinedButton(
                    onClick = onDeleteClick,
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) {
                    Icon(Icons.Filled.Delete, "삭제", Modifier.size(16.dp))
                }
            }
            wishlist.lastChecked?.let {
                Spacer(Modifier.height(8.dp))
                Text("마지막 체크: ${it.format(timeFmt)}", 
                     style = MaterialTheme.typography.bodySmall, 
                     color = MaterialTheme.colorScheme.onSurfaceVariant,
                     textAlign = TextAlign.Center, 
                     modifier = Modifier.fillMaxWidth())
            }
        }
    }
}

@Composable
private fun SwipeToDeleteContainer(
    thresholdPx: Float,
    backgroundColor: Color,
    icon: ImageVector,
    iconTint: Color,
    onSwipedLeft: () -> Unit,
    content: @Composable () -> Unit
) {
    var offsetX by remember { mutableStateOf(0f) }
    val progress by animateFloatAsState((kotlin.math.abs(offsetX) / thresholdPx).coerceIn(0f, 1f), label = "swipeProgress")

    Box(
        Modifier
            .fillMaxWidth()
            .background(backgroundColor.copy(alpha = progress * 0.8f))
            .pointerInput(Unit) {
                detectHorizontalDragGestures(
                    onDragEnd = {
                        if (offsetX <= -thresholdPx) onSwipedLeft()
                        offsetX = 0f
                    }
                ) { _, dragAmount ->
                    val newX = offsetX + dragAmount
                    offsetX = if (newX < 0f) newX else 0f
                }
            }
            .padding(vertical = 2.dp)
    ) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = iconTint.copy(alpha = progress), modifier = Modifier.size(24.dp))
        }
        Box(Modifier.offset(x = Dp(offsetX / Resources.getSystem().displayMetrics.density))) {
            content()
        }
    }
}

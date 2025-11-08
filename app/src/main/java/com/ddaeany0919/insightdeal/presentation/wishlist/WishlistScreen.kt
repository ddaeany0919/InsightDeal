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
import androidx.compose.ui.graphics.StrokeCap
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
import com.ddaeany0919.insightdeal.WishlistCard

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
                            WishlistCard(
                                item = item,
                                onDelete = { viewModel.deleteItem(item) },
                                onCheckPrice = { viewModel.checkPrice(item) }
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
        AddWishlistUI(
            onDismiss = {
                Log.d(TAG_UI, "AddDialog: 닫힘")
                showAddDialog = false
            },
            onAdd = { keyword: String, productUrl: String, targetPrice: Int ->
                Log.d(TAG_UI, "AddDialog: onAdd 호출 keyword=$keyword productUrl=$productUrl target=$targetPrice")
                viewModel.addItem(keyword, productUrl, targetPrice)
                showAddDialog = false
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
fun AddWishlistUI(
    onDismiss: () -> Unit,
    onAdd: (String, String, Int) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            Button(onClick = { onAdd("샘플", "https://naver.com", 10000) }) {
                Text("추가")
            }
        },
        dismissButton = {
            Button(onClick = onDismiss) { Text("취소") }
        },
        title = { Text("관심상품 추가") },
        text = { Text("이 Dialog는 예시입니다. 실제 UI를 여기에 구현하세요.") }
    )
}

@Composable
fun LoadingStateDetailed(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator()
            Spacer(Modifier.height(16.dp))
            Text("가격 정보를 가져오고 있어요...", style = MaterialTheme.typography.bodyLarge)
        }
    }
}

@Composable
fun EmptyWishlistStateDetailed(onAdd: () -> Unit, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("아직 관심상품이 없어요", style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.height(16.dp))
            Button(onClick = onAdd) { Text("상품 추가하기") }
        }
    }
}

@Composable
fun SwipeToDeleteContainer(
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

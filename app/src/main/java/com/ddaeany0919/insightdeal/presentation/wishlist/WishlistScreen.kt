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
import androidx.lifecycle.viewmodel.compose.viewModel
import java.time.format.DateTimeFormatter
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WishlistScreen(
    viewModel: WishlistViewModel = viewModel()
) {
    var showAddDialog by remember { mutableStateOf(false) }
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(Unit) { viewModel.loadWishlist() }

    Scaffold(
        snackbarHost = { SnackbarHost(viewModel.snackbarHostState) },
        floatingActionButton = {
            FloatingActionButton(onClick = { showAddDialog = true }) {
                Icon(Icons.Filled.Add, contentDescription = "관심상품 추가")
            }
        }
    ) { inner ->
        if (uiState.isLoading) {
            LoadingState(modifier = Modifier.padding(inner))
        } else if (uiState.wishlists.isEmpty()) {
            EmptyWishlistState(onAdd = { showAddDialog = true }, modifier = Modifier.padding(inner))
        } else {
            LazyColumn(
                modifier = Modifier.padding(inner).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(uiState.wishlists, key = { it.id }) { item ->
                    WishlistSwipeToDismiss(
                        onConfirmDelete = {
                            viewModel.deleteWishlist(item.id)
                            viewModel.offerUndo(item)
                        },
                        content = {
                            WishlistCard(
                                wishlist = item,
                                onDeleteClick = {
                                    viewModel.deleteWishlist(item.id)
                                    viewModel.offerUndo(item)
                                },
                                onPriceCheckClick = { viewModel.checkPrice(item.id) }
                            )
                        }
                    )
                }
            }
        }

        if (showAddDialog) {
            AddWishlistDialog(
                onDismiss = { showAddDialog = false },
                onAdd = { keyword, targetPrice ->
                    viewModel.addWishlist(keyword, targetPrice)
                    showAddDialog = false
                }
            )
        }
    }
}

@Composable
private fun WishlistCard(
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

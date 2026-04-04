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

// ?ÑÏöî???∞Ïù¥???¥Îûò?§ÎÇò Í∏∞Ì? import???ÅÌô©??ÎßûÍ≤å Ï∂îÍ?

suspend fun SnackbarHostState.offerUndo(
    message: String,
    actionLabel: String = "?§Ìñâ Ï∑®ÏÜå",
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
            text = "?ÑÏãúÎ¶¨Ïä§?∏Í? ÎπÑÏñ¥?àÏäµ?àÎã§",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "Í¥Ä???àÎäî ?ÅÌíà??Ï∂îÍ??¥Î≥¥?∏Ïöî",
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
            Text("?ÑÏù¥??Ï∂îÍ?")
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
            title = { Text("?ÑÏãúÎ¶¨Ïä§???ÑÏù¥??Ï∂îÍ?") },
            text = {
                Column {
                    OutlinedTextField(
                        value = keyword,
                        onValueChange = { keyword = it },
                        label = { Text("?ÅÌíàÎ™??êÎäî ?§Ïõå??) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = productUrl,
                        onValueChange = { productUrl = it },
                        label = { Text("?ÅÌíà ÎßÅÌÅ¨(URL)") },
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
                        label = { Text("Î™©Ìëú Í∞ÄÍ≤?(??") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        isError = isError,
                        supportingText = if (isError) { { Text("?¨Î∞îÎ•?Í∞ÄÍ≤©ÏùÑ ?ÖÎ†•?¥Ï£º?∏Ïöî") } } else null
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
                    Text("Ï∂îÍ?")
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
                    Text("Ï∑®ÏÜå")
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
                Text("?ÅÌíà Ï∂îÍ?", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
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
                    com.ddaeany0919.insightdeal.ui.components.StandardWishlistCard(style = com.ddaeany0919.insightdeal.ui.components.WishlistCardStyle.DETAILED,
                        item = item,
                        onDelete = {
                            viewModel.deleteItem(item)
                            scope.launch {
                                snackbarHostState.offerUndo(
                                    message = "${item.keyword}??Î•? ??†ú?àÏäµ?àÎã§",
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
                        text = "?§Î•òÍ∞Ä Î∞úÏÉù?àÏäµ?àÎã§",
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
                    Button(onClick = { viewModel.retry() }) { Text("?§Ïãú ?úÎèÑ") }
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
                    snackbarHostState.showSnackbar(message = "$keyword ($productUrl) ?ÑÏãúÎ¶¨Ïä§?∏Ïóê Ï∂îÍ???)
                }
            }
        )
    }
}

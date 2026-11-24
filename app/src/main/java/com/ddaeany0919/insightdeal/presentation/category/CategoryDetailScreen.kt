package com.ddaeany0919.insightdeal.presentation.category

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import androidx.paging.LoadState
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.paging.compose.itemKey
import com.ddaeany0919.insightdeal.models.CategoryCatalog
import com.ddaeany0919.insightdeal.presentation.A11yIconButton
import com.ddaeany0919.insightdeal.presentation.EmptyFeedState
import com.ddaeany0919.insightdeal.presentation.LoadingFeed
import com.ddaeany0919.insightdeal.presentation.OfflineErrorState
import com.ddaeany0919.insightdeal.presentation.home.DealCardComposable
import com.ddaeany0919.insightdeal.presentation.home.HomeViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CategoryDetailScreen(
    categoryName: String,
    viewModel: HomeViewModel,
    navController: NavController
) {
    val normalizedCategoryKey = remember(categoryName) {
        CategoryCatalog.normalizeCategoryKey(categoryName)
    }
    val displayLabel = remember(normalizedCategoryKey) {
        CategoryCatalog.getDisplayLabel(normalizedCategoryKey)
    }

    LaunchedEffect(normalizedCategoryKey) {
        viewModel.selectCategory(normalizedCategoryKey)
    }

    val context = androidx.compose.ui.platform.LocalContext.current
    val dealsPagingItems = viewModel.dealsPagingData.collectAsLazyPagingItems()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = displayLabel,
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                        letterSpacing = (-0.3).sp
                    )
                },
                navigationIcon = {
                    A11yIconButton(
                        onClick = { navController.popBackStack() },
                        contentDescription = "뒤로가기"
                    ) {
                        Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onBackground
                )
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            val refreshState = dealsPagingItems.loadState.refresh

            when {
                refreshState is LoadState.Loading -> {
                    LoadingFeed()
                }
                refreshState is LoadState.Error -> {
                    OfflineErrorState(
                        message = "핫딜 목록을 불러오지 못했어요. 잠시 후 다시 시도해 주세요.",
                        onRetry = { dealsPagingItems.retry() }
                    )
                }
                dealsPagingItems.itemCount == 0 && refreshState is LoadState.NotLoading -> {
                    EmptyFeedState(
                        onRefresh = { dealsPagingItems.refresh() }
                    )
                }
                else -> {
                    LazyColumn(
                        contentPadding = PaddingValues(start = 0.dp, end = 0.dp, top = 8.dp, bottom = 24.dp),
                        verticalArrangement = Arrangement.spacedBy(0.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        items(
                            count = dealsPagingItems.itemCount,
                            key = dealsPagingItems.itemKey { it.id },
                            contentType = { "category_deal_item" }
                        ) { index ->
                            val deal = dealsPagingItems[index]
                            if (deal != null) {
                                DealCardComposable(
                                    deal = deal,
                                    onDetailClick = {
                                        navController.navigate("deal_detail/${deal.id}")
                                    },
                                    onOpenUrl = { url ->
                                        val lowerUrl = url.lowercase()
                                        val isCommunityPost = listOf(
                                            "ppomppu.co.kr", "fmkorea.com", "quasarzone.com",
                                            "ruliweb.com", "clien.net", "bbasak.com"
                                        ).any { lowerUrl.contains(it) }
                                        if (isCommunityPost) {
                                            val finalUrl = if (url.startsWith("http")) url else "https://$url"
                                            val encodedUrl = java.net.URLEncoder.encode(finalUrl, "UTF-8")
                                            navController.navigate("webview/$encodedUrl")
                                        } else {
                                            viewModel.trackOutboundClick(url)
                                            com.ddaeany0919.insightdeal.utils.CustomTabLauncher.launchShoppingUrl(context, url)
                                        }
                                    }
                                )
                            }
                        }

                        val appendState = dealsPagingItems.loadState.append
                        when {
                            appendState is LoadState.Loading -> {
                                item(key = "append_loading", contentType = "loading") {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(16.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.size(24.dp))
                                    }
                                }
                            }
                            appendState is LoadState.Error -> {
                                item(key = "append_error", contentType = "error") {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(12.dp),
                                        horizontalArrangement = Arrangement.Center,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        OutlinedButton(
                                            onClick = { dealsPagingItems.retry() },
                                            modifier = Modifier.heightIn(min = 48.dp)
                                        ) {
                                            Text("더 불러오기 다시 시도")
                                        }
                                    }
                                }
                            }
                            appendState is LoadState.NotLoading && appendState.endOfPaginationReached && dealsPagingItems.itemCount > 0 -> {
                                item(key = "append_end", contentType = "footer") {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 12.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = "모든 핫딜을 불러왔어요",
                                            fontSize = 12.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

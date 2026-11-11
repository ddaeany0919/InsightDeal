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
import com.ddaeany0919.insightdeal.presentation.home.HomeViewModel
import com.ddaeany0919.insightdeal.presentation.home.DealCardComposable
import com.ddaeany0919.insightdeal.presentation.LoadingFeed
import com.ddaeany0919.insightdeal.presentation.OfflineErrorState
import com.ddaeany0919.insightdeal.presentation.EmptyFeedState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CategoryDetailScreen(
    categoryName: String,
    viewModel: HomeViewModel,
    navController: NavController
) {
    LaunchedEffect(categoryName) {
        viewModel.selectCategory(categoryName)
    }

    val context = androidx.compose.ui.platform.LocalContext.current
    val dealsPagingItems = viewModel.dealsPagingData.collectAsLazyPagingItems()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = categoryName,
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                        letterSpacing = (-0.3).sp
                    )
                },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "뒤로가기")
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
                        message = "상품 목록을 불러오지 못했습니다.",
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
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
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

                        if (dealsPagingItems.loadState.append is LoadState.Loading) {
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
                    }
                }
            }
        }
    }
}

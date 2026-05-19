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
import androidx.navigation.NavController
import androidx.paging.LoadState
import androidx.paging.compose.collectAsLazyPagingItems
import com.ddaeany0919.insightdeal.presentation.home.HomeViewModel
import com.ddaeany0919.insightdeal.presentation.home.DealCardComposable

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

    val dealsPagingItems = viewModel.dealsPagingData.collectAsLazyPagingItems()

    val categoriesMap = mapOf(
        "핫딜모음" to "🔥", "음식" to "🍔", "SW/게임" to "🎮", "PC제품" to "💻",
        "가전제품" to "📺", "생활용품" to "🧻", "의류" to "👕", "화장품" to "💄",
        "모바일/기프티콘" to "📱", "상품권" to "💳", "패키지/이용권" to "🎟",
        "여행.해외핫딜" to "✈️", "적립" to "💰", "이벤트" to "🎉", "기타" to "📦"
    )
    val emoji = categoriesMap[categoryName] ?: ""
    val titleText = if (emoji.isNotEmpty()) "$emoji $categoryName" else categoryName

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(titleText, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onBackground
                )
            )
        }
    ) { paddingValues ->
        LazyColumn(
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            items(dealsPagingItems.itemCount) { index ->
                val deal = dealsPagingItems[index]
                if (deal != null) {
                    DealCardComposable(
                        deal = deal,
                        onDetailClick = {
                            navController.navigate("deal_detail/${deal.id}")
                        },
                        onOpenUrl = { url ->
                            val finalUrl = if (url.startsWith("http")) url else "https://$url"
                            val encodedUrl = java.net.URLEncoder.encode(finalUrl, "UTF-8")
                            navController.navigate("webview/$encodedUrl")
                        }
                    )
                }
            }

            dealsPagingItems.apply {
                when {
                    loadState.refresh is LoadState.Loading -> {
                        item {
                            Box(Modifier.fillParentMaxSize(), contentAlignment = Alignment.Center) {
                                CircularProgressIndicator()
                            }
                        }
                    }
                    loadState.append is LoadState.Loading -> {
                        item {
                            Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                                CircularProgressIndicator()
                            }
                        }
                    }
                    loadState.refresh is LoadState.Error -> {
                        item {
                            Box(Modifier.fillParentMaxSize(), contentAlignment = Alignment.Center) {
                                Text("데이터를 불러오는데 실패했습니다.", color = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                    else -> {
                        if (dealsPagingItems.itemCount == 0 && loadState.refresh is LoadState.NotLoading) {
                            item {
                                Box(Modifier.fillParentMaxSize(), contentAlignment = Alignment.Center) {
                                    Text("해당 카테고리에 핫딜 데이터가 없습니다.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

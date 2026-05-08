package com.ddaeany0919.insightdeal.presentation.platform

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import androidx.paging.compose.collectAsLazyPagingItems
import com.ddaeany0919.insightdeal.presentation.home.DealCardComposable
import com.ddaeany0919.insightdeal.presentation.home.HomeViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlatformScreen(viewModel: HomeViewModel = viewModel()) {
    val filterState by viewModel.filterState.collectAsState()
    val currentPlatform = filterState.platform ?: "전체"
    val dealsPagingItems = viewModel.dealsPagingData.collectAsLazyPagingItems()

    val platforms = listOf("전체", "뽐뿌", "퀘이사존", "펨코", "루리웹", "클리앙", "알리뽐뿌", "빠삭국내", "빠삭해외")

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("출처별 핫딜 모아보기", fontWeight = FontWeight.Bold) }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            ScrollableTabRow(
                selectedTabIndex = platforms.indexOf(currentPlatform).takeIf { it >= 0 } ?: 0,
                edgePadding = 16.dp,
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.primary
            ) {
                platforms.forEach { platform ->
                    Tab(
                        selected = currentPlatform == platform,
                        onClick = { 
                            if (platform == "전체") {
                                viewModel.selectPlatform("")
                            } else {
                                viewModel.selectPlatform(platform)
                            }
                        },
                        text = { 
                            Text(
                                platform, 
                                fontWeight = if (currentPlatform == platform) FontWeight.Bold else FontWeight.Normal
                            ) 
                        }
                    )
                }
            }

            if (dealsPagingItems.loadState.refresh is androidx.paging.LoadState.Loading) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else if (dealsPagingItems.itemCount == 0) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("해당 출처의 핫딜이 없습니다.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 80.dp)
                ) {
                    items(dealsPagingItems.itemCount) { index ->
                        val deal = dealsPagingItems[index]
                        if (deal != null) {
                            DealCardComposable(deal = deal)
                        }
                    }
                }
            }
        }
    }
}

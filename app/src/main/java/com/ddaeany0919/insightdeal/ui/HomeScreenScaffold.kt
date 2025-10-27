package com.ddaeany0919.insightdeal.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ddaeany0919.insightdeal.data.Resource
import com.ddaeany0919.insightdeal.models.ApiDeal

/**
 * 🧩 HomeScreen 연결 스캐폴드 (최소 변경)
 * - ViewModel 상태 구독하여 기존 UI에 연결
 */
@Composable
fun HomeScreenScaffold(
    viewModel: HomeViewModel = viewModel()
) {
    val dealsState by viewModel.popularDeals.collectAsState()
    val isRefreshing by viewModel.isRefreshing.collectAsState()

    // 첫 로딩 트리거
    LaunchedEffect(Unit) { viewModel.loadInitialFeed() }

    when (dealsState) {
        is Resource.Loading -> {
            LoadingFeed()
        }
        is Resource.Success -> {
            val deals = (dealsState as Resource.Success<List<ApiDeal>>).data
            DealFeedFromApi(deals)
        }
        is Resource.Error -> {
            ErrorState(
                message = (dealsState as Resource.Error<List<ApiDeal>>).message,
                onRetry = { viewModel.refreshFeed() }
            )
        }
    }
}

@Composable
private fun DealFeedFromApi(deals: List<ApiDeal>) {
    LazyColumn(
        modifier = Modifier.padding(vertical = 8.dp)
    ) {
        items(deals.size) { idx ->
            val deal = deals[idx]
            ListItem(
                headlineContent = { Text(deal.title) },
                supportingContent = {
                    Text("최저가: ${deal.lowestPrice ?: '-'} | 최저몰: ${deal.lowestPlatform ?: '-'} | 응답: ${deal.responseTimeMs}ms")
                }
            )
            Divider()
        }
    }
}

@Composable
private fun ErrorState(message: String, onRetry: () -> Unit) {
    ColumnWithCentered {
        Text(message)
        SpacerH(8)
        Button(onClick = onRetry) { Text("재시도") }
    }
}

@Composable
private fun ColumnWithCentered(content: @Composable () -> Unit) {
    androidx.compose.foundation.layout.Column(
        modifier = Modifier.padding(24.dp)
    ) { content() }
}

@Composable
private fun SpacerH(h: Int) { androidx.compose.foundation.layout.Spacer(modifier = Modifier.padding(top = h.dp)) }

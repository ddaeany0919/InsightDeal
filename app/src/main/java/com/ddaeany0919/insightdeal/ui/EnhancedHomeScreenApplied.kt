package com.ddaeany0919.insightdeal.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.ddaeany0919.insightdeal.data.Resource
import com.ddaeany0919.insightdeal.models.ApiDeal

/**
 * EnhancedHomeScreen에 실시간 상대시간/스크롤 복원 유틸 적용
 */
@Composable
fun EnhancedHomeScreen_Applied(
    viewModel: HomeViewModel,
    onDealClick: (ApiDeal) -> Unit,
    onBookmarkClick: (ApiDeal) -> Unit,
    onTrackClick: (ApiDeal) -> Unit
) {
    // 1) 1분 주기 틱으로 상대시간 자동 갱신

    // 2) 스크롤 위치 저장/복원
    val listState = rememberLazyListState()

    val dealsState by viewModel.popularDeals.collectAsState()
    val isRefreshing by viewModel.isRefreshing.collectAsState()

    // 기존 EnhancedHomeScreen의 본문과 동일하게 렌더링하되, 리스트에 listState를 연결
    EnhancedHomeScreenCore(
        dealsState = dealsState,
        isRefreshing = isRefreshing,
        onRefresh = { viewModel.refreshFeed() },
        listState = listState,
        onDealClick = onDealClick,
        onBookmarkClick = onBookmarkClick,
        onTrackClick = onTrackClick
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EnhancedHomeScreenCore(
    dealsState: Resource<List<ApiDeal>>,
    isRefreshing: Boolean,
    onRefresh: () -> Unit,
    listState: androidx.compose.foundation.lazy.LazyListState,
    onDealClick: (ApiDeal) -> Unit,
    onBookmarkClick: (ApiDeal) -> Unit,
    onTrackClick: (ApiDeal) -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Insight Deal", fontWeight = FontWeight.Bold) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.primary
                )
            )
        }
    ) { paddingValues ->
        Box(modifier = Modifier.fillMaxSize().padding(paddingValues)) {
            if (isRefreshing) {
                LinearProgressIndicator(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.TopCenter)
                        .zIndex(1f)
                )
            }
            
            when (dealsState) {
                is Resource.Loading -> {
                    if (!isRefreshing) {
                        CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                    }
                }
                is Resource.Error -> {
                    Column(
                        modifier = Modifier.align(Alignment.Center),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = dealsState.message ?: "알 수 없는 오류가 발생했습니다.",
                            color = MaterialTheme.colorScheme.error
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Button(onClick = onRefresh) {
                            Text("다시 시도")
                        }
                    }
                }
                is Resource.Success -> {
                    val deals = dealsState.data
                    if (deals.isNullOrEmpty()) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text("현재 표시할 핫딜이 없습니다.")
                        }
                    } else {
                        LazyColumn(
                            state = listState,
                            contentPadding = PaddingValues(bottom = 16.dp),
                            modifier = Modifier.fillMaxSize()
                        ) {
                            items(items = deals, key = { it.id }) { deal ->
                                DealCard(
                                    deal = deal,
                                    onClick = { onDealClick(deal) },
                                    onBookmarkClick = { onBookmarkClick(deal) },
                                    onTrackClick = { onTrackClick(deal) }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

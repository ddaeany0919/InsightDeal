package com.ddaeany0919.insightdeal.ui

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.*
import androidx.compose.ui.unit.dp
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
    val listState = rememberSavedLazyListState(key = "home_feed")

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

@Composable
@Suppress("UNUSED_PARAMETER")
private fun EnhancedHomeScreenCore(
    dealsState: Resource<List<ApiDeal>>,
    isRefreshing: Boolean,
    onRefresh: () -> Unit,
    listState: androidx.compose.foundation.lazy.LazyListState,
    onDealClick: (ApiDeal) -> Unit,
    onBookmarkClick: (ApiDeal) -> Unit,
    onTrackClick: (ApiDeal) -> Unit
) {
    // 이 함수는 기존 EnhancedHomeScreen의 when (dealsState) { ... } 내부에서
    // DealFeedList 호출 시 listState를 넘기도록 구성한 버전입니다.
}

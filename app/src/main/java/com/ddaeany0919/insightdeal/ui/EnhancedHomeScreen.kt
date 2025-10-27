package com.ddaeany0919.insightdeal.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.ddaeany0919.insightdeal.data.Resource
import com.ddaeany0919.insightdeal.models.ApiDeal
import com.google.accompanist.swiperefresh.SwipeRefresh
import com.google.accompanist.swiperefresh.rememberSwipeRefreshState

/**
 * 🏠 완전한 홈 화면 (상태별 UI + Pull-to-Refresh 연결)
 * 목표: 1초 내 첫 렌더, 캐시 즉시 표시, 200ms 새로고침 반응
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EnhancedHomeScreen(
    viewModel: HomeViewModel = viewModel(),
    onDealClick: (ApiDeal) -> Unit = {},
    onBookmarkClick: (ApiDeal) -> Unit = {},
    onTrackClick: (ApiDeal) -> Unit = {}
) {
    val dealsState by viewModel.popularDeals.collectAsState()
    val isRefreshing by viewModel.isRefreshing.collectAsState()
    var refreshSuccess by remember { mutableStateOf<Boolean?>(null) }
    var previousDealsCount by remember { mutableStateOf(0) }
    
    // 초기 로딩
    LaunchedEffect(Unit) {
        viewModel.loadInitialFeed()
    }
    
    // 새로고침 성공/실패 판단
    LaunchedEffect(dealsState, isRefreshing) {
        if (!isRefreshing && dealsState is Resource.Success) {
            val currentCount = (dealsState as Resource.Success<List<ApiDeal>>).data.size
            refreshSuccess = currentCount != previousDealsCount
            previousDealsCount = currentCount
        }
    }
    
    Column(modifier = Modifier.fillMaxSize()) {
        // Pull-to-Refresh 토스트 (200ms 이내 반응)
        RefreshToastManager(
            isRefreshing = isRefreshing,
            refreshSuccess = refreshSuccess,
            newItemsCount = if (refreshSuccess == true) {
                ((dealsState as? Resource.Success)?.data?.size ?: 0) - previousDealsCount
            } else 0,
            onDismiss = { refreshSuccess = null }
        )
        
        // Pull-to-Refresh 래퍼
        SwipeRefresh(
            state = rememberSwipeRefreshState(isRefreshing = isRefreshing),
            onRefresh = {
                viewModel.refreshFeed()
            }
        ) {
            // 상태별 UI 렌더링
            when (dealsState) {
                is Resource.Loading -> {
                    if (dealsState.data?.isNotEmpty() == true) {
                        // 캐시 데이터 있음: 즉시 표시 + 백그라운드 업데이트
                        DealFeedList(
                            deals = dealsState.data,
                            onDealClick = onDealClick,
                            onBookmarkClick = onBookmarkClick,
                            onTrackClick = onTrackClick
                        )
                    } else {
                        // 캐시 없음: 스켈레톤 표시
                        LoadingFeed()
                    }
                }
                
                is Resource.Success -> {
                    val deals = dealsState.data
                    if (deals.isNotEmpty()) {
                        DealFeedList(
                            deals = deals,
                            onDealClick = onDealClick,
                            onBookmarkClick = onBookmarkClick,
                            onTrackClick = onTrackClick
                        )
                    } else {
                        EmptyFeedState(onRefresh = { viewModel.refreshFeed() })
                    }
                }
                
                is Resource.Error -> {
                    val cachedDeals = dealsState.data
                    if (cachedDeals?.isNotEmpty() == true) {
                        // 오프라인 모드: 이전 데이터 + 오프라인 배지
                        Column {
                            // 오프라인 배지
                            Surface(
                                modifier = Modifier.fillMaxWidth(),
                                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.1f)
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.CloudOff,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp),
                                        tint = MaterialTheme.colorScheme.outline
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "🔌 오프라인 보기 - ${dealsState.message}",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                                    )
                                }
                            }
                            
                            // 이전 데이터 표시
                            DealFeedList(
                                deals = cachedDeals,
                                onDealClick = onDealClick,
                                onBookmarkClick = onBookmarkClick,
                                onTrackClick = onTrackClick
                            )
                        }
                    } else {
                        // 완전한 에러 상태
                        OfflineErrorState(
                            message = dealsState.message,
                            isOffline = dealsState.message.contains("오프라인") || dealsState.message.contains("네트워크"),
                            onRetry = { viewModel.refreshFeed() }
                        )
                    }
                }
            }
        }
    }
}

/**
 * 📱 딜 피드 리스트 (단순화된 버전)
 */
@Composable
private fun DealFeedList(
    deals: List<ApiDeal>,
    onDealClick: (ApiDeal) -> Unit,
    onBookmarkClick: (ApiDeal) -> Unit,
    onTrackClick: (ApiDeal) -> Unit
) {
    LazyColumn(
        contentPadding = PaddingValues(vertical = 8.dp)
    ) {
        items(deals, key = { it.id }) { deal ->
            DealCard(
                deal = deal,
                onClick = { onDealClick(deal) },
                onBookmarkClick = { onBookmarkClick(deal) },
                onTrackClick = { onTrackClick(deal) }
            )
        }
    }
}

/**
 * 📱 딜 카드 (최소·정확한 정보 중심)
 */
@Composable
private fun DealCard(
    deal: ApiDeal,
    onClick: () -> Unit,
    onBookmarkClick: () -> Unit,
    onTrackClick: () -> Unit
) {
    Card(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 제목 원문 그대로 + 상대시간
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = deal.title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Medium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurface
                )
                
                Spacer(modifier = Modifier.height(6.dp))
                
                // 가격/몰 정보 한 줄 요약
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "최저가: ${formatPrice(deal.lowestPrice)}",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    
                    if (deal.maxSaving > 0) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "${formatPrice(deal.maxSaving)} 절약",
                            style = MaterialTheme.typography.labelMedium,
                            color = Color(0xFF00C853),
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
                
                Spacer(modifier = Modifier.height(6.dp))
                
                // 커뮤니티 아이콘 + 상대시간
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // 커뮤니티 백지 (추론에서 커뮤니티 이름 추출)
                    val communityName = deal.lowestPlatform?.uppercase() ?: "UNKNOWN"
                    CommunityBadge(community = communityName)
                    
                    Spacer(modifier = Modifier.width(8.dp))
                    
                    Text(
                        text = formatRelativeTime(deal.updatedAt),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                    )
                    
                    Spacer(modifier = Modifier.width(8.dp))
                    
                    // 네트워크 상태 인디케이터 (디버깅용)
                    NetworkStatusIndicator(
                        responseTimeMs = deal.responseTimeMs,
                        successCount = deal.successCount,
                        totalPlatforms = 4
                    )
                }
            }
            
            Spacer(modifier = Modifier.width(12.dp))
            
            // 액션 버튼
            Column {
                OutlinedButton(
                    onClick = onTrackClick,
                    modifier = Modifier.width(80.dp)
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            "추적",
                            style = MaterialTheme.typography.labelSmall,
                            fontSize = 10.sp
                        )
                    }
                }
                
                Spacer(modifier = Modifier.height(8.dp))
                
                IconButton(
                    onClick = onBookmarkClick,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = if (deal.isBookmarked) Icons.Default.Bookmark else Icons.Default.BookmarkBorder,
                        contentDescription = "북마크",
                        tint = if (deal.isBookmarked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}

package com.example.insightdeal.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.MonetizationOn
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.insightdeal.model.DealItem
import com.example.insightdeal.network.ApiClient
import com.example.insightdeal.viewmodel.DealsUiState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    uiState: DealsUiState,
    onDealClick: (Int) -> Unit,
    onRefresh: () -> Unit,
    onLoadMore: () -> Unit, // 다음 페이지 로드 요청 함수
    onCategorySelect: (String) -> Unit,
    onCommunityToggle: (String) -> Unit
) {
    var showFilterDialog by remember { mutableStateOf(false) }
    val gridState = rememberLazyGridState() // 그리드의 스크롤 상태를 기억

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("InsightDeal 🔥", fontWeight = FontWeight.Bold) },
                actions = {
                    IconButton(onClick = onRefresh) {
                        Icon(Icons.Default.Refresh, contentDescription = "새로고침")
                    }
                    IconButton(onClick = { showFilterDialog = true }) {
                        Icon(Icons.Default.FilterList, contentDescription = "필터")
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(modifier = Modifier.padding(paddingValues)) {
            if (uiState is DealsUiState.Success) {
                CategoryTabs(
                    categories = listOf("전체", "디지털/가전", "PC/하드웨어", "음식/식품", "의류/패션", "생활/잡화", "모바일/상품권", "패키지/이용권", "적립/이벤트", "기타", "해외핫딜", "알리익스프레스"),
                    selectedCategory = uiState.selectedCategory,
                    onCategorySelected = onCategorySelect
                )
                if (showFilterDialog) {
                    FilterDialog(
                        allAvailableCommunities = uiState.allAvailableCommunities,
                        selectedCommunities = uiState.selectedCommunities,
                        onDismiss = { showFilterDialog = false },
                        onCommunityToggle = onCommunityToggle
                    )
                }
            }

            Box(modifier = Modifier.fillMaxSize()) {
                when (uiState) {
                    is DealsUiState.Loading -> {
                        CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                    }
                    is DealsUiState.Success -> {
                        if (uiState.filteredDeals.isEmpty() && !uiState.isPaginating) {
                            Text(
                                text = "앗, 조건에 맞는 딜을 찾지 못했어요!",
                                modifier = Modifier.align(Alignment.Center),
                                textAlign = TextAlign.Center
                            )
                        } else {
                            LazyVerticalGrid(
                                columns = GridCells.Adaptive(minSize = 160.dp),
                                modifier = Modifier.fillMaxSize(),
                                state = gridState, // 스크롤 상태 연결
                                contentPadding = PaddingValues(4.dp)
                            ) {
                                items(uiState.filteredDeals, key = { it.id }) { deal ->
                                    DealCard(deal = deal, onClick = { onDealClick(deal.id) })
                                }
                                // 다음 페이지 로딩 중일 때, 맨 아래에 로딩 아이콘 표시
                                if (uiState.isPaginating) {
                                    item(span = { GridItemSpan(maxLineSpan) }) {
                                        Box(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                                            CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                                        }
                                    }
                                }
                            }

                            // 스크롤이 맨 아래 근처에 도달했는지 확인하고 다음 페이지 로드
                            val isScrolledToEnd = remember {
                                derivedStateOf {
                                    val lastVisibleItemIndex = gridState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1
                                    val totalItemsCount = gridState.layoutInfo.totalItemsCount
                                    lastVisibleItemIndex >= totalItemsCount - 5 && totalItemsCount > 0
                                }
                            }

                            if (isScrolledToEnd.value && uiState.canLoadMore && !uiState.isPaginating) {
                                LaunchedEffect(Unit) {
                                    onLoadMore()
                                }
                            }
                        }
                    }
                    is DealsUiState.Error -> {
                        Text(
                            text = "오류가 발생했어요: ${uiState.message}",
                            modifier = Modifier
                                .align(Alignment.Center)
                                .padding(16.dp),
                            color = MaterialTheme.colorScheme.error,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun CategoryTabs(
    categories: List<String>,
    selectedCategory: String,
    onCategorySelected: (String) -> Unit
) {
    ScrollableTabRow(
        selectedTabIndex = categories.indexOf(selectedCategory),
        edgePadding = 0.dp
    ) {
        categories.forEach { category ->
            Tab(
                selected = category == selectedCategory,
                onClick = { onCategorySelected(category) },
                text = { Text(category) }
            )
        }
    }
}

@Composable
fun FilterDialog(
    allAvailableCommunities: Set<String>,
    selectedCommunities: Set<String>,
    onDismiss: () -> Unit,
    onCommunityToggle: (String) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("게시물 필터링") },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                Text("선택된 사이트의 게시글만 리스트에 표시됩니다.", style = MaterialTheme.typography.bodySmall)
                Spacer(modifier = Modifier.height(16.dp))

                allAvailableCommunities.sorted().forEach { community ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onCommunityToggle(community) }
                            .padding(vertical = 4.dp)
                    ) {
                        Switch(
                            checked = community in selectedCommunities,
                            onCheckedChange = { onCommunityToggle(community) }
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(community)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("닫기")
            }
        }
    )
}

@Composable
fun DealCard(deal: DealItem, onClick: () -> Unit) {
    // 딜 유형('이벤트' 여부)에 따라 카드 스타일을 미리 결정합니다.
    val isEvent = deal.dealType == "이벤트"
    val cardBackgroundColor = if (isEvent) Color(0xFFE8F5E9) else MaterialTheme.colorScheme.surface
    val priceTextColor = if (isEvent) Color(0xFF2E7D32) else MaterialTheme.colorScheme.primary

    ElevatedCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(6.dp)
            .clickable(
                onClick = onClick,
                enabled = !deal.isClosed // isClosed가 true이면 클릭 비활성화
            ),
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        colors = CardDefaults.cardColors(containerColor = cardBackgroundColor)
    ) {
        Box {
            Column {
                // 이벤트 유형일 경우, 이미지 대신 아이콘을 표시합니다.
                if (isEvent) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(1f)
                            .background(Color(0xFFDCEDC8)), // 이벤트 아이콘 배경색
                        contentAlignment = Alignment.Center
                    ) {
                        // 예시 아이콘 (MonetizationOn), 필요에 따라 다른 아이콘으로 변경 가능
                        Icon(
                            imageVector = Icons.Default.MonetizationOn,
                            contentDescription = "이벤트",
                            modifier = Modifier.size(64.dp),
                            tint = priceTextColor
                        )
                    }
                } else {
                    // 일반 딜일 경우, 이미지를 표시합니다.
                    AsyncImage(
                        model = ImageRequest.Builder(LocalContext.current)
                            .data(ApiClient.BASE_URL.removeSuffix("/") + deal.imageUrl)
                            .crossfade(true)
                            .build(),
                        contentDescription = deal.title,
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(1f)
                            .clip(RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp)),
                        contentScale = ContentScale.Crop
                    )
                }

                Column(modifier = Modifier.padding(12.dp)) {
                    // --- 쇼핑몰 이름과 배송비 정보 ---
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = deal.shopName,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )

                        // 배송비 정보 없음'일 때 연한 회색으로 표시
                        val shippingFeeText: String
                        val shippingFeeColor: Color

                        when {
                            // 1. "무료"가 포함된 경우
                            deal.shippingFee.contains("무료") -> {
                                shippingFeeText = "배송비 무료"
                                shippingFeeColor = Color(0xFF007BFF) // 파란색
                            }
                            // 2. "정보 없음"인 경우
                            deal.shippingFee == "정보 없음" -> {
                                shippingFeeText = "배송비 정보 없음"
                                shippingFeeColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f) // 연한 회색
                            }
                            // 3. 그 외 (유료 배송비)
                            else -> {
                                shippingFeeText = "배송비 ${deal.shippingFee}"
                                shippingFeeColor = MaterialTheme.colorScheme.outline // 일반 회색
                            }
                        }

                        Text(
                            text = shippingFeeText,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Normal,
                            color = shippingFeeColor
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // --- 상품명 ---
                    Text(
                        text = deal.title,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        lineHeight = 20.sp,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.height(40.dp)
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    // --- 가격 ---
                    // ✨ 수정: '정보 없음'일 때 연한 회색 및 취소선으로 표시
                    val priceText = if (deal.price == "정보 없음") "가격 정보 없음" else deal.price
                    val finalPriceColor = if (deal.price == "정보 없음") MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f) else priceTextColor

                    Text(
                        text = priceText,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 20.sp,
                        color = finalPriceColor,
                        maxLines = 1,
                        textDecoration = if (deal.price == "정보 없음") TextDecoration.LineThrough else TextDecoration.None
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    // --- 커뮤니티 정보 ---
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        val communityColor = getCommunityColor(deal.community)
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .background(color = communityColor, shape = CircleShape)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = deal.community,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // --- 품절/종료 시 회색 오버레이 및 텍스트 표시 ---
            if (deal.isClosed) {
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .background(Color.Black.copy(alpha = 0.5f)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "종료된 딜",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    )
                }
            }
        }
    }
}

@Composable
private fun getCommunityColor(community: String): Color {
    return when (community) {
        "뽐뿌", "뽐뿌해외", "알리뽐뿌" -> Color(0xFFE53935)
        "펨코" -> Color(0xFF43A047)
        "클리앙" -> Color(0xFF007BFF)
        "퀘이사존" -> Color(0xFFF57C00)
        "빠삭", "빠삭해외" -> Color(0xFF6A1B9A)
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
}
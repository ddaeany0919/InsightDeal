package com.example.insightdeal.ui

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.pullrefresh.PullRefreshIndicator
import androidx.compose.material.pullrefresh.pullRefresh
import androidx.compose.material.pullrefresh.rememberPullRefreshState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.layout.*
import androidx.compose.ui.platform.*
import androidx.compose.ui.text.*
import androidx.compose.ui.text.font.*
import androidx.compose.ui.text.style.*
import androidx.compose.ui.unit.*
import coil.compose.*
import coil.request.*
import com.example.insightdeal.model.*
import com.example.insightdeal.network.*
import com.example.insightdeal.ui.components.SearchDialog
import com.example.insightdeal.viewmodel.*
import kotlinx.coroutines.*
import com.example.insightdeal.ui.search.*
import com.example.insightdeal.data.BookmarkManager

// ✅ 카테고리 데이터 클래스
data class CategoryItem(
    val name: String,
    val icon: androidx.compose.ui.graphics.vector.ImageVector
)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterialApi::class)
@Composable
fun MainScreen(
    uiState: DealsUiState,
    onDealClick: (Int) -> Unit,
    onRefresh: () -> Unit,
    onLoadMore: () -> Unit,
    onCategorySelect: (String) -> Unit,
    onCommunityToggle: (String) -> Unit,
    onBookmarkClick: () -> Unit
) {
    var showSearch by remember { mutableStateOf(false) }
    var showFilterDialog by remember { mutableStateOf(false) }
    var showCategoryMenu by remember { mutableStateOf(false) }
    var isDarkMode by remember { mutableStateOf(false) }
    var showMenu by remember { mutableStateOf(false) }
    var currentSearchQuery by remember { mutableStateOf("") }
    var isSearchMode by remember { mutableStateOf(false) }

    // ✅ Pull-to-Refresh 상태 추가
    val pullRefreshState = rememberPullRefreshState(
        refreshing = uiState is DealsUiState.Loading,
        onRefresh = onRefresh
    )

    // ✅ 검색된 딜 필터링
    val displayDeals = remember(uiState, currentSearchQuery, isSearchMode) {
        when {
            uiState is DealsUiState.Success && isSearchMode && currentSearchQuery.isNotBlank() -> {
                uiState.filteredDeals.filter { deal ->
                    deal.title.contains(currentSearchQuery, ignoreCase = true) ||
                            deal.shopName.contains(currentSearchQuery, ignoreCase = true) ||
                            deal.community.contains(currentSearchQuery, ignoreCase = true)
                }
            }
            uiState is DealsUiState.Success -> uiState.filteredDeals
            else -> emptyList()
        }
    }

    MaterialTheme(
        colorScheme = if (isDarkMode) darkColorScheme() else lightColorScheme()
    ) {
        Scaffold(
            topBar = {
                Box {
                    CleanTopAppBar(
                        onSearchClick = { showSearch = true },
                        onMenuClick = { showMenu = true }
                    )

                    // 햄버거 메뉴
                    TopAppBarMenu(
                        expanded = showMenu,
                        onDismiss = { showMenu = false },
                        onRefreshClick = onRefresh,
                        onFilterClick = { showFilterDialog = true },
                        onDarkModeToggle = { isDarkMode = !isDarkMode },
                        isDarkMode = isDarkMode,
                        onBookmarkClick = onBookmarkClick
                    )
                }
            }
        ) { paddingValues ->
            // ✅ Box로 감싸고 pullRefresh 추가
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .background(MaterialTheme.colorScheme.background)
                    .pullRefresh(pullRefreshState)
            ) {
                Column(
                    modifier = Modifier.fillMaxSize()
                ) {
                    // ✅ 검색 모드가 아닐 때만 카테고리 메뉴 표시
                    if (!isSearchMode) {
                        CollapsibleCategoryMenu(
                            categories = listOf(
                                CategoryItem("전체", Icons.Default.Home),
                                CategoryItem("디지털/가전", Icons.Default.Computer),
                                CategoryItem("PC/하드웨어", Icons.Default.Memory),
                                CategoryItem("음식/식품", Icons.Default.Restaurant),
                                CategoryItem("의류/패션", Icons.Default.Checkroom),
                                CategoryItem("생활/잡화", Icons.Default.Home),
                                CategoryItem("모바일/상품권", Icons.Default.PhoneAndroid),
                                CategoryItem("패키지/이용권", Icons.Default.CardGiftcard),
                                CategoryItem("적립/이벤트", Icons.Default.Stars),
                                CategoryItem("해외핫딜", Icons.Default.Flight),
                                CategoryItem("알리익스프레스", Icons.Default.ShoppingCart)
                            ),
                            selectedCategory = if (uiState is DealsUiState.Success) uiState.selectedCategory else "전체",
                            isExpanded = showCategoryMenu,
                            onExpandedChange = { showCategoryMenu = it },
                            onCategorySelect = onCategorySelect
                        )
                    } else {
                        // ✅ 검색 모드일 때 검색 결과 헤더
                        SearchResultHeader(
                            searchQuery = currentSearchQuery,
                            resultCount = displayDeals.size
                        )
                    }

                    // 메인 콘텐츠
                    when (uiState) {
                        is DealsUiState.Loading -> {
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center
                            ) {
                                CircularProgressIndicator()
                            }
                        }
                        is DealsUiState.Success -> {
                            val uniqueDeals = remember(displayDeals) {
                                displayDeals
                                    .distinctBy { "${it.id}_${it.title}_${it.community}" }
                                    .sortedByDescending { it.id }
                            }

                            if (uniqueDeals.isEmpty()) {
                                if (isSearchMode && currentSearchQuery.isNotBlank()) {
                                    SearchEmptyState(searchQuery = currentSearchQuery)
                                } else {
                                    EmptyStateMessage()
                                }
                            } else {
                                Box(modifier = Modifier.fillMaxSize()) {
                                    val listState = rememberLazyListState()
                                    val coroutineScope = rememberCoroutineScope()

                                    LazyColumn(
                                        state = listState,
                                        modifier = Modifier.fillMaxSize(),
                                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                                        verticalArrangement = Arrangement.spacedBy(12.dp)
                                    ) {
                                        items(uniqueDeals) { deal ->
                                            ImprovedDealCard(
                                                deal = deal,
                                                onClick = { onDealClick(deal.id) }
                                            )
                                        }

                                        if (uiState.isPaginating) {
                                            item {
                                                Box(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .padding(16.dp),
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    CircularProgressIndicator()
                                                }
                                            }
                                        }
                                    }

                                    // ✅ 플로팅 버튼
                                    val showScrollToTop by remember {
                                        derivedStateOf {
                                            listState.firstVisibleItemIndex > 3
                                        }
                                    }

                                    if (showScrollToTop) {
                                        FloatingActionButton(
                                            onClick = {
                                                coroutineScope.launch {
                                                    listState.animateScrollToItem(0, scrollOffset = 0)
                                                }
                                            },
                                            modifier = Modifier
                                                .align(Alignment.BottomEnd)
                                                .padding(16.dp)
                                                .size(56.dp),
                                            shape = CircleShape,
                                            containerColor = MaterialTheme.colorScheme.primary,
                                            contentColor = MaterialTheme.colorScheme.onPrimary
                                        ) {
                                            Column(
                                                horizontalAlignment = Alignment.CenterHorizontally,
                                                verticalArrangement = Arrangement.Center
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.KeyboardArrowUp,
                                                    contentDescription = "맨 위로",
                                                    modifier = Modifier.size(24.dp)
                                                )
                                                Text(
                                                    text = "TOP",
                                                    fontSize = 8.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    letterSpacing = 0.5.sp
                                                )
                                            }
                                        }
                                    }

                                    // ✅ 무한 스크롤 처리
                                    val isScrolledToEnd = remember {
                                        derivedStateOf {
                                            val lastVisibleItemIndex = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1
                                            val totalItemsCount = listState.layoutInfo.totalItemsCount
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
                        }
                        is DealsUiState.Error -> {
                            ErrorMessage(message = uiState.message)
                        }
                    }
                }

                // ✅ Pull-to-Refresh 인디케이터
                PullRefreshIndicator(
                    refreshing = uiState is DealsUiState.Loading,
                    state = pullRefreshState,
                    modifier = Modifier.align(Alignment.TopCenter),
                    backgroundColor = MaterialTheme.colorScheme.surface,
                    contentColor = MaterialTheme.colorScheme.primary
                )
            }
        }

        // 검색 다이얼로그
        if (showSearch) {
            SearchDialog(
                onDismiss = { showSearch = false },
                onSearch = { query ->
                    if (query.isNotBlank()) {
                        currentSearchQuery = query
                        isSearchMode = true
                    }
                    showSearch = false
                }
            )
        }

        // 필터 다이얼로그
        if (showFilterDialog && uiState is DealsUiState.Success) {
            FilterDialog(
                allAvailableCommunities = uiState.allAvailableCommunities,
                selectedCommunities = uiState.selectedCommunities,
                onDismiss = { showFilterDialog = false },
                onCommunityToggle = onCommunityToggle
            )
        }
    }
}

// ✅ 깔끔한 상단바
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CleanTopAppBar(
    onSearchClick: () -> Unit,
    onMenuClick: () -> Unit
) {
    TopAppBar(
        title = {
            Text(
                "InsightDeal",
                fontWeight = FontWeight.Bold,
                fontSize = 20.sp,
                color = MaterialTheme.colorScheme.primary
            )
        },
        actions = {
            // 검색 버튼
            IconButton(onClick = onSearchClick) {
                Icon(
                    Icons.Default.Search,
                    contentDescription = "검색",
                    tint = MaterialTheme.colorScheme.onSurface
                )
            }

            // 햄버거 메뉴
            IconButton(onClick = onMenuClick) {
                Icon(
                    Icons.Default.MoreVert,
                    contentDescription = "메뉴",
                    tint = MaterialTheme.colorScheme.onSurface
                )
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    )
}

// ✅ 햄버거 메뉴 (북마크 개수 포함)
@Composable
fun TopAppBarMenu(
    expanded: Boolean,
    onDismiss: () -> Unit,
    onRefreshClick: () -> Unit,
    onFilterClick: () -> Unit,
    onDarkModeToggle: () -> Unit,
    isDarkMode: Boolean,
    onBookmarkClick: () -> Unit
) {
    val context = LocalContext.current
    val bookmarkManager = remember { BookmarkManager.getInstance(context) }
    val bookmarkCount by remember {
        derivedStateOf { bookmarkManager.getBookmarkCount() }
    }

    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismiss,
        modifier = Modifier.width(200.dp),
        offset = DpOffset(x = (-16).dp, y = 2.dp)
    ) {
        // 북마크 (개수 표시)
        DropdownMenuItem(
            text = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(
                            Icons.Default.Bookmark,
                            contentDescription = null,
                            tint = if (bookmarkCount > 0) Color(0xFFFF9800) else MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.size(20.dp)
                        )
                        Text("북마크")
                    }

                    // 북마크 개수 표시
                    if (bookmarkCount > 0) {
                        Badge(
                            containerColor = Color(0xFFFF9800),
                            contentColor = Color.White
                        ) {
                            Text(
                                text = if (bookmarkCount > 99) "99+" else bookmarkCount.toString(),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            },
            onClick = {
                onBookmarkClick()
                onDismiss()
            }
        )

        HorizontalDivider()

        // 새로고침
        DropdownMenuItem(
            text = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(
                        Icons.Default.Refresh,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(20.dp)
                    )
                    Text("새로고침")
                }
            },
            onClick = {
                onRefreshClick()
                onDismiss()
            }
        )

        // 필터
        DropdownMenuItem(
            text = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(
                        Icons.Default.FilterList,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(20.dp)
                    )
                    Text("필터")
                }
            },
            onClick = {
                onFilterClick()
                onDismiss()
            }
        )

        HorizontalDivider()

        // 다크모드 토글
        DropdownMenuItem(
            text = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(
                        if (isDarkMode) Icons.Default.LightMode else Icons.Default.DarkMode,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(20.dp)
                    )
                    Text(if (isDarkMode) "라이트 모드" else "다크 모드")
                }
            },
            onClick = {
                onDarkModeToggle()
                onDismiss()
            }
        )
    }
}

@Composable
fun CollapsibleCategoryMenu(
    categories: List<CategoryItem>,
    selectedCategory: String,
    isExpanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    onCategorySelect: (String) -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = if (isExpanded) 8.dp else 4.dp
    ) {
        Column {
            // 현재 선택된 카테고리 표시
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onExpandedChange(!isExpanded) }
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                val currentCategory = categories.find { it.name == selectedCategory }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = currentCategory?.icon ?: Icons.Default.Home,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        text = selectedCategory,
                        fontWeight = FontWeight.Medium,
                        fontSize = 16.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                Icon(
                    imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = if (isExpanded) "접기" else "펼치기",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // 확장 가능한 카테고리 리스트
            AnimatedVisibility(
                visible = isExpanded,
                enter = slideInVertically() + expandVertically() + fadeIn(),
                exit = slideOutVertically() + shrinkVertically() + fadeOut()
            ) {
                LazyColumn(
                    modifier = Modifier.heightIn(max = 400.dp),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    items(categories) { category ->
                        CategoryMenuItem(
                            category = category,
                            isSelected = category.name == selectedCategory,
                            onClick = {
                                onCategorySelect(category.name)
                                onExpandedChange(false)
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun CategoryMenuItem(
    category: CategoryItem,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        shape = RoundedCornerShape(8.dp),
        color = if (isSelected)
            MaterialTheme.colorScheme.primaryContainer
        else
            Color.Transparent
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = category.icon,
                contentDescription = null,
                tint = if (isSelected)
                    MaterialTheme.colorScheme.primary
                else
                    MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp)
            )
            Text(
                text = category.name,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                color = if (isSelected)
                    MaterialTheme.colorScheme.onPrimaryContainer
                else
                    MaterialTheme.colorScheme.onSurface
            )
            if (isSelected) {
                Spacer(modifier = Modifier.weight(1f))
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = "선택됨",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

@Composable
fun ImprovedDealCard(
    deal: DealItem,
    onClick: () -> Unit
) {
    val context = LocalContext.current
    val bookmarkManager = remember { BookmarkManager.getInstance(context) }

    // ✅ 북마크 실시간 업데이트
    var isBookmarked by remember { mutableStateOf(bookmarkManager.isBookmarked(deal.id)) }

    LaunchedEffect(deal.id) {
        isBookmarked = bookmarkManager.isBookmarked(deal.id)
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // ✅ 이미지 (컴팩트하게)
            Card(
                modifier = Modifier.size(80.dp),
                shape = RoundedCornerShape(8.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
            ) {
                if (deal.dealType == "이벤트") {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color(0xFFF3E5F5)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Stars,
                            contentDescription = "이벤트",
                            modifier = Modifier.size(24.dp),
                            tint = Color(0xFF9C27B0)
                        )
                    }
                } else {
                    AsyncImage(
                        model = ImageRequest.Builder(LocalContext.current)
                            .data(ApiClient.BASE_URL.removeSuffix("/") + deal.imageUrl)
                            .crossfade(true)
                            .build(),
                        contentDescription = deal.title,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                }
            }

            // ✅ 콘텐츠 영역
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                // 상단: 커뮤니티 + 쇼핑몰 + 북마크
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // 커뮤니티 + 쇼핑몰
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        // 커뮤니티 태그
                        Text(
                            text = deal.community,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier
                                .background(
                                    MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                                    RoundedCornerShape(4.dp)
                                )
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        )

                        // 쇼핑몰 이름
                        Text(
                            text = deal.shopName,
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    // ✅ 북마크 버튼 (실시간 업데이트)
                    IconButton(
                        onClick = {
                            bookmarkManager.toggleBookmark(deal)
                            isBookmarked = bookmarkManager.isBookmarked(deal.id) // 즉시 상태 업데이트
                        },
                        modifier = Modifier.size(20.dp)
                    ) {
                        Icon(
                            imageVector = if (isBookmarked) Icons.Default.Bookmark else Icons.Default.BookmarkBorder,
                            contentDescription = if (isBookmarked) "북마크 해제" else "북마크 추가",
                            tint = if (isBookmarked) Color(0xFFFF9800) else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }

                // 제목
                Text(
                    text = deal.title,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp,
                    lineHeight = 18.sp,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )

                // 하단: 가격 + 배송비
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Bottom
                ) {
                    // 가격
                    Text(
                        text = if (deal.price == "정보 없음") "가격 미표시" else deal.price,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        color = if (deal.price == "정보 없음")
                            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        else
                            MaterialTheme.colorScheme.primary
                    )

                    // 배송비
                    Text(
                        text = when {
                            deal.shippingFee.contains("무료") -> "무료배송"
                            deal.shippingFee == "정보 없음" -> "배송비 미표시"
                            else -> deal.shippingFee
                        },
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Medium,
                        color = when {
                            deal.shippingFee.contains("무료") -> Color(0xFF4CAF50)
                            else -> MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        modifier = Modifier
                            .background(
                                when {
                                    deal.shippingFee.contains("무료") -> Color(0xFF4CAF50).copy(alpha = 0.1f)
                                    else -> MaterialTheme.colorScheme.surfaceVariant
                                },
                                RoundedCornerShape(4.dp)
                            )
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }
        }

        // 종료된 딜 오버레이
        if (deal.isClosed) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.6f)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "종료된 딜",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp,
                    modifier = Modifier
                        .background(
                            Color.Black.copy(alpha = 0.7f),
                            RoundedCornerShape(6.dp)
                        )
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                )
            }
        }
    }
}

@Composable
fun EmptyStateMessage() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Icon(
                imageVector = Icons.Default.SearchOff,
                contentDescription = null,
                modifier = Modifier.size(64.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = "조건에 맞는 딜을 찾지 못했어요",
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = "다른 카테고리나 필터를 시도해보세요",
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun ErrorMessage(message: String) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Error,
                contentDescription = null,
                modifier = Modifier.size(64.dp),
                tint = MaterialTheme.colorScheme.error
            )
            Text(
                text = "오류가 발생했어요",
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.error
            )
            Text(
                text = message,
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FilterDialog(
    allAvailableCommunities: Set<String>,
    selectedCommunities: Set<String>,
    onDismiss: () -> Unit,
    onCommunityToggle: (String) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                "게시물 필터링",
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(
                modifier = Modifier.heightIn(max = 400.dp)
            ) {
                Text(
                    "선택된 사이트의 게시글만 표시됩니다.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(16.dp))
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(allAvailableCommunities.sorted()) { community ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onCommunityToggle(community) }
                                .padding(vertical = 8.dp)
                        ) {
                            Switch(
                                checked = community in selectedCommunities,
                                onCheckedChange = { onCommunityToggle(community) }
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = community,
                                fontSize = 14.sp,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("확인")
            }
        }
    )
}

@Composable
fun SearchResultHeader(
    searchQuery: String,
    resultCount: Int
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "'$searchQuery' 검색결과 ${resultCount}개",
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

@Composable
fun SearchEmptyState(searchQuery: String) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Icon(
                imageVector = Icons.Default.SearchOff,
                contentDescription = null,
                modifier = Modifier.size(64.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = "\"$searchQuery\"에 대한 결과가 없어요",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center
            )
            Text(
                text = "다른 검색어를 시도하거나\n필터를 변경해보세요",
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                lineHeight = 20.sp
            )
        }
    }
}
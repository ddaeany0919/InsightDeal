package com.ddaeany0919.insightdeal

import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import coil.compose.AsyncImage
import kotlinx.coroutines.delay
import java.text.DecimalFormat

/**
 * 🏠 InsightDeal 메인 홈화면
 * 
 * 사용자 중심 설계: "매일 쓰고 싶은 앱"
 * - 쉬운 발견: 커뮤니티 딜 피드
 * - 간편한 추적: 원클릭 "추적 추가"
 * - 똑똑한 비교: 쿠팡 vs 커뮤니티 가격
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    navController: NavController,
    modifier: Modifier = Modifier
) {
    var searchQuery by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf("전체") }
    var isGridView by remember { mutableStateOf(false) }
    var isSearchActive by remember { mutableStateOf(false) }
    var showFilterSheet by remember { mutableStateOf(false) }
    var hasSeenOnboarding by remember { mutableStateOf(false) }
    
    // 🎯 실데이터 연결을 위한 상태
    var isLoading by remember { mutableStateOf(true) }
    var deals by remember { mutableStateOf<List<DealItem>>(emptyList()) }
    var isError by remember { mutableStateOf(false) }
    
    // 임시: 로딩 시뮬레이션
    LaunchedEffect(Unit) {
        delay(1500) // API 로딩 시뮬레이션
        
        // TODO: 실제 API 호출로 교체
        deals = getSampleDeals()
        isLoading = false
    }
    
    Column(
        modifier = modifier.fillMaxSize()
    ) {
        // 🔍 상단 검색 & 필터 영역
        TopSearchAndFilterBar(
            searchQuery = searchQuery,
            onSearchQueryChange = { searchQuery = it },
            isSearchActive = isSearchActive,
            onSearchActiveChange = { isSearchActive = it },
            selectedCategory = selectedCategory,
            onCategorySelect = { selectedCategory = it },
            isGridView = isGridView,
            onViewToggle = { isGridView = !isGridView },
            onFilterClick = { showFilterSheet = true }
        )
        
        // 📱 메인 컨텐츠
        when {
            isLoading -> {
                // ⏳ 로딩 상태
                LoadingFeed()
            }
            
            deals.isEmpty() && !hasSeenOnboarding -> {
                // 🎯 첫 사용자 온보딩 (핵심!)
                SampleDealsOnboarding(
                    onDismiss = { hasSeenOnboarding = true },
                    onStartTracking = {
                        // 위시리스트로 이동
                        navController.navigate("watchlist")
                    },
                    modifier = Modifier.fillMaxSize()
                )
            }
            
            deals.isEmpty() -> {
                // 📭 빈 상태 (온보딩 본 후)
                EmptyFeedState(
                    onRefresh = {
                        isLoading = true
                        // TODO: 새로고침 로직
                    }
                )
            }
            
            else -> {
                // 📱 실제 딜 피드
                DealFeed(
                    deals = deals,
                    isGridView = isGridView,
                    onDealClick = { deal ->
                        navController.navigate("deal_detail/${deal.id}")
                    },
                    onBookmarkClick = { deal ->
                        // TODO: 북마크 토글
                    },
                    onTrackClick = { deal ->
                        // 🎯 핵심 기능: 추적 추가
                        navController.navigate("watchlist")
                    }
                )
            }
        }
    }
    
    // 🎛️ 필터 바텀시트
    if (showFilterSheet) {
        FilterBottomSheet(
            onDismiss = { showFilterSheet = false }
        )
    }
}

/**
 * ⏳ 로딩 스켈레톤 UI
 */
@Composable
private fun LoadingFeed() {
    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items(5) {
            LoadingDealCard()
        }
    }
}

@Composable
private fun LoadingDealCard() {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(120.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 이미지 스켈레톤
            Box(
                modifier = Modifier
                    .size(80.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f))
            )
            
            Spacer(modifier = Modifier.width(12.dp))
            
            Column(
                modifier = Modifier.weight(1f)
            ) {
                // 제목 스켈레톤
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.8f)
                        .height(16.dp)
                        .background(
                            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f),
                            RoundedCornerShape(4.dp)
                        )
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                // 가격 스켈레톤
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.4f)
                        .height(20.dp)
                        .background(
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.2f),
                            RoundedCornerShape(4.dp)
                        )
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                // 배지 스켈레톤
                Row {
                    repeat(2) {
                        Box(
                            modifier = Modifier
                                .width(60.dp)
                                .height(20.dp)
                                .background(
                                    MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f),
                                    RoundedCornerShape(10.dp)
                                )
                        )
                        if (it < 1) Spacer(modifier = Modifier.width(8.dp))
                    }
                }
            }
        }
    }
}

/**
 * 📭 빈 피드 상태
 */
@Composable
private fun EmptyFeedState(
    onRefresh: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Default.SearchOff,
            contentDescription = null,
            modifier = Modifier.size(80.dp),
            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        Text(
            text = "새로운 딜을 찾고 있어요",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
        )
        
        Spacer(modifier = Modifier.height(8.dp))
        
        Text(
            text = "잠시 후 다시 확인해 주세요",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
        )
        
        Spacer(modifier = Modifier.height(24.dp))
        
        Button(
            onClick = onRefresh,
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary
            )
        ) {
            Icon(
                imageVector = Icons.Default.Refresh,
                contentDescription = null,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text("새로고침")
        }
    }
}

/**
 * 🎯 샘플 데이터 생성 (실데이터 연결 전)
 */
private fun getSampleDeals(): List<DealItem> {
    return listOf(
        DealItem(
            id = 1,
            title = "🔥 삼성 갤럭시 버즈3 Pro 노이즈캔슬링 무선이어폰",
            originalPrice = 350000,
            currentPrice = 198000,
            discountRate = 43,
            community = "뽐뿌",
            postedMinutesAgo = 3,
            imageUrl = "",
            purchaseUrl = "https://...",
            hasFreeship = true,
            hasCoupon = true,
            isOverseas = false,
            isHot = true,
            coupangPrice = 220000,
            savingAmount = 22000
        ),
        DealItem(
            id = 2,
            title = "애플 에어팟 프로 2세대 USB-C 정품 무료배송",
            originalPrice = 359000,
            currentPrice = 299000,
            discountRate = 17,
            community = "클리앙",
            postedMinutesAgo = 15,
            imageUrl = "",
            purchaseUrl = "https://...",
            hasFreeship = true,
            hasCoupon = false,
            isOverseas = false,
            isHot = true,
            coupangPrice = 329000,
            savingAmount = 30000
        ),
        DealItem(
            id = 3,
            title = "다이슨 V15 무선청소기 + 침구브러시 세트",
            originalPrice = 890000,
            currentPrice = 649000,
            discountRate = 27,
            community = "루리웹",
            postedMinutesAgo = 32,
            imageUrl = "",
            purchaseUrl = "https://...",
            hasFreeship = false,
            hasCoupon = true,
            isOverseas = true,
            isHot = false,
            coupangPrice = 719000,
            savingAmount = 70000
        )
    )
}

/**
 * 🔍 상단 검색 & 필터 영역
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TopSearchAndFilterBar(
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    isSearchActive: Boolean,
    onSearchActiveChange: (Boolean) -> Unit,
    selectedCategory: String,
    onCategorySelect: (String) -> Unit,
    isGridView: Boolean,
    onViewToggle: () -> Unit,
    onFilterClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
    ) {
        // 검색바 + 뷰 토글
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            SearchBar(
                query = searchQuery,
                onQueryChange = onSearchQueryChange,
                onSearch = { onSearchActiveChange(false) },
                active = isSearchActive,
                onActiveChange = onSearchActiveChange,
                placeholder = { Text("특가 상품 검색") },
                modifier = Modifier.weight(1f)
            ) {
                // 검색 제안
                Column {
                    Text(
                        "🔥 인기 검색어",
                        style = MaterialTheme.typography.titleSmall,
                        modifier = Modifier.padding(16.dp, 16.dp, 16.dp, 8.dp)
                    )
                    listOf("갤럭시", "에어팟", "다이슨", "아이패드", "닌텐도").forEach { keyword ->
                        ListItem(
                            headlineContent = { Text(keyword) },
                            leadingContent = {
                                Icon(Icons.Default.Search, contentDescription = null)
                            },
                            modifier = Modifier.clickable {
                                onSearchQueryChange(keyword)
                                onSearchActiveChange(false)
                            }
                        )
                    }
                }
            }
            
            Spacer(modifier = Modifier.width(12.dp))
            
            // 뷰 토글
            IconButton(onClick = onViewToggle) {
                Icon(
                    imageVector = if (isGridView) Icons.Default.List else Icons.Default.Apps,
                    contentDescription = if (isGridView) "리스트로 보기" else "그리드로 보기",
                    tint = MaterialTheme.colorScheme.primary
                )
            }
            
            // 필터
            IconButton(onClick = onFilterClick) {
                Icon(
                    imageVector = Icons.Default.FilterList,
                    contentDescription = "필터",
                    tint = MaterialTheme.colorScheme.onSurface
                )
            }
        }
        
        // 카테고리 칩
        CategoryChipRow(
            selectedCategory = selectedCategory,
            onCategorySelect = onCategorySelect
        )
    }
}

/**
 * 🏷️ 카테고리 칩 리스트
 */
@Composable
private fun CategoryChipRow(
    selectedCategory: String,
    onCategorySelect: (String) -> Unit
) {
    val categories = listOf("전체", "디지털", "가전", "패션", "생활", "식품", "해외")
    
    LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        items(categories) { category ->
            FilterChip(
                onClick = { onCategorySelect(category) },
                label = { Text(category) },
                selected = selectedCategory == category,
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.primary,
                    selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                )
            )
        }
    }
    
    Spacer(modifier = Modifier.height(8.dp))
}

/**
 * 📱 딜 피드 (리스트/그리드 전환)
 */
@Composable
private fun DealFeed(
    deals: List<DealItem>,
    isGridView: Boolean,
    onDealClick: (DealItem) -> Unit,
    onBookmarkClick: (DealItem) -> Unit,
    onTrackClick: (DealItem) -> Unit
) {
    AnimatedContent(
        targetState = isGridView,
        transitionSpec = {
            slideInHorizontally { it } + fadeIn() togetherWith
            slideOutHorizontally { -it } + fadeOut()
        },
        label = "view_transition"
    ) { isGrid ->
        if (isGrid) {
            // 🏪 그리드 뷰 (시각적)
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(deals, key = { it.id }) { deal ->
                    DealGridCard(
                        deal = deal,
                        onClick = { onDealClick(deal) },
                        onBookmarkClick = { onBookmarkClick(deal) },
                        onTrackClick = { onTrackClick(deal) }
                    )
                }
            }
        } else {
            // 📋 리스트 뷰 (정보 중심) - 메인!
            LazyColumn(
                contentPadding = PaddingValues(vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(1.dp)
            ) {
                items(deals, key = { it.id }) { deal ->
                    DealListCard(
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

/**
 * 📋 리스트 카드 (사용자가 가장 많이 쓸 뷰)
 */
@Composable
private fun DealListCard(
    deal: DealItem,
    onClick: () -> Unit,
    onBookmarkClick: () -> Unit,
    onTrackClick: () -> Unit
) {
    var showTrackingSnackbar by remember { mutableStateOf(false) }
    
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
            // 🖼️ 상품 이미지
            AsyncImage(
                model = deal.imageUrl.ifEmpty { "https://via.placeholder.com/80x80" },
                contentDescription = null,
                modifier = Modifier
                    .size(80.dp)
                    .clip(RoundedCornerShape(8.dp)),
                contentScale = ContentScale.Crop
            )
            
            Spacer(modifier = Modifier.width(12.dp))
            
            // 📄 상품 정보
            Column(
                modifier = Modifier.weight(1f)
            ) {
                // 제목
                Text(
                    text = deal.title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Medium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurface
                )
                
                Spacer(modifier = Modifier.height(6.dp))
                
                // 💰 가격 정보 (사용자 최우선 관심사)
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // 현재가
                    Text(
                        text = formatPrice(deal.currentPrice),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    
                    Spacer(modifier = Modifier.width(8.dp))
                    
                    // 원가 (취소선)
                    if (deal.originalPrice > deal.currentPrice) {
                        Text(
                            text = formatPrice(deal.originalPrice),
                            style = MaterialTheme.typography.bodySmall.copy(
                                textDecoration = androidx.compose.ui.text.style.TextDecoration.LineThrough
                            ),
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        )
                        
                        Spacer(modifier = Modifier.width(6.dp))
                        
                        // 할인율 배지
                        Surface(
                            color = Color(0xFFFF4444),
                            shape = RoundedCornerShape(4.dp)
                        ) {
                            Text(
                                text = "${deal.discountRate}%",
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.White,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(6.dp))
                
                // 🛒 쿠팡 가격 비교 (핵심 차별화!)
                deal.savingAmount?.let { saving ->
                    if (saving > 0) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.TrendingDown,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                                tint = Color(0xFF00C853)
                            )
                            
                            Spacer(modifier = Modifier.width(4.dp))
                            
                            Text(
                                text = "쿠팡 대비 ${formatPrice(saving)} 절약!",
                                style = MaterialTheme.typography.labelMedium,
                                color = Color(0xFF00C853),
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(6.dp))
                
                // 🏷️ 배지들 (사용자에게 중요한 정보)
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    if (deal.hasFreeship) {
                        item { BadgeChip("🚚무료배송", Color(0xFF1976D2)) }
                    }
                    if (deal.hasCoupon) {
                        item { BadgeChip("🎟️쿠폰", Color(0xFFFF9800)) }
                    }
                    if (deal.isOverseas) {
                        item { BadgeChip("🌏해외", Color(0xFF9C27B0)) }
                    }
                    if (deal.isHot) {
                        item { BadgeChip("🔥인기", Color(0xFFFF5722)) }
                    }
                }
                
                Spacer(modifier = Modifier.height(4.dp))
                
                // 메타 정보
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = deal.community,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Medium
                    )
                    
                    Text(
                        text = " • ${deal.postedMinutesAgo}분 전",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                    )
                }
            }
            
            Spacer(modifier = Modifier.width(12.dp))
            
            // 🎯 액션 버튼들 (사용자 중심!)
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // 🎯 추적 추가 (핵심 기능!)
                OutlinedButton(
                    onClick = {
                        onTrackClick(deal)
                        showTrackingSnackbar = true
                    },
                    modifier = Modifier.width(80.dp),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.primary
                    ),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary)
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
                
                // 구매하기
                Button(
                    onClick = onClick,
                    modifier = Modifier.width(80.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary
                    )
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = Icons.Default.ShoppingCart,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            "구매",
                            style = MaterialTheme.typography.labelSmall,
                            fontSize = 10.sp
                        )
                    }
                }
                
                // 북마크
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
    
    // 📢 추적 추가 스낵바
    if (showTrackingSnackbar) {
        LaunchedEffect(Unit) {
            delay(2000)
            showTrackingSnackbar = false
        }
    }
}

/**
 * 🏪 그리드 카드 (시각적 중심)
 */
@Composable
private fun DealGridCard(
    deal: DealItem,
    onClick: () -> Unit,
    onBookmarkClick: () -> Unit,
    onTrackClick: () -> Unit
) {
    Card(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .height(300.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize()
        ) {
            // 상품 이미지 + 배지
            Box {
                AsyncImage(
                    model = deal.imageUrl.ifEmpty { "https://via.placeholder.com/200x140" },
                    contentDescription = null,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(140.dp),
                    contentScale = ContentScale.Crop
                )
                
                // 할인율 배지
                if (deal.discountRate > 0) {
                    Surface(
                        color = Color(0xFFFF4444),
                        shape = RoundedCornerShape(bottomEnd = 8.dp),
                        modifier = Modifier.align(Alignment.TopStart)
                    ) {
                        Text(
                            text = "${deal.discountRate}%",
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            style = MaterialTheme.typography.labelMedium,
                            color = Color.White,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
                
                // 북마크
                IconButton(
                    onClick = onBookmarkClick,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(8.dp)
                        .background(
                            Color.Black.copy(alpha = 0.3f),
                            CircleShape
                        )
                ) {
                    Icon(
                        imageVector = if (deal.isBookmarked) Icons.Default.Bookmark else Icons.Default.BookmarkBorder,
                        contentDescription = "북마크",
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
            
            // 상품 정보
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(12.dp)
            ) {
                // 제목
                Text(
                    text = deal.title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurface
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                // 가격
                Text(
                    text = formatPrice(deal.currentPrice),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                
                // 절약 정보
                deal.savingAmount?.let { saving ->
                    if (saving > 0) {
                        Text(
                            text = "${formatPrice(saving)} 절약",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color(0xFF00C853),
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
                
                Spacer(modifier = Modifier.weight(1f))
                
                // 하단 액션
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = onTrackClick,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("추적", fontSize = 12.sp)
                    }
                    
                    Button(
                        onClick = onClick,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("구매", fontSize = 12.sp)
                    }
                }
                
                Spacer(modifier = Modifier.height(4.dp))
                
                // 메타 정보
                Text(
                    text = "${deal.community} • ${deal.postedMinutesAgo}분 전",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                )
            }
        }
    }
}

/**
 * 🏷️ 작은 배지 칩
 */
@Composable
private fun BadgeChip(
    text: String,
    backgroundColor: Color
) {
    Surface(
        color = backgroundColor,
        shape = RoundedCornerShape(12.dp)
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
            style = MaterialTheme.typography.labelSmall,
            color = Color.White,
            fontSize = 10.sp
        )
    }
}

/**
 * 🎛️ 필터 바텀시트
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FilterBottomSheet(
    onDismiss: () -> Unit
) {
    var showOnlyFreeship by remember { mutableStateOf(false) }
    var showOnlyHot by remember { mutableStateOf(false) }
    var excludeOverseas by remember { mutableStateOf(false) }
    var showOnlySaving by remember { mutableStateOf(false) }
    
    ModalBottomSheet(
        onDismissRequest = onDismiss
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp)
        ) {
            Text(
                text = "🎛️ 필터 설정",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
            
            Spacer(modifier = Modifier.height(24.dp))
            
            // 필터 옵션들
            FilterOption(
                title = "무료배송만",
                subtitle = "배송비 없는 상품만 표시",
                checked = showOnlyFreeship,
                onCheckedChange = { showOnlyFreeship = it }
            )
            
            FilterOption(
                title = "인기 딜만",
                subtitle = "조회수 높은 핫딜만 표시",
                checked = showOnlyHot,
                onCheckedChange = { showOnlyHot = it }
            )
            
            FilterOption(
                title = "해외직구 제외",
                subtitle = "국내 배송 상품만 표시",
                checked = excludeOverseas,
                onCheckedChange = { excludeOverseas = it }
            )
            
            FilterOption(
                title = "쿠팡보다 싸다",
                subtitle = "쿠팡 가격보다 저렴한 딜만",
                checked = showOnlySaving,
                onCheckedChange = { showOnlySaving = it }
            )
            
            Spacer(modifier = Modifier.height(24.dp))
            
            // 적용 버튼
            Button(
                onClick = {
                    // TODO: 필터 적용 로직
                    onDismiss()
                },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary
                )
            ) {
                Text("필터 적용")
            }
            
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

/**
 * 🎛️ 개별 필터 옵션
 */
@Composable
private fun FilterOption(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(
            modifier = Modifier.weight(1f)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
            )
        }
        
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = MaterialTheme.colorScheme.primary
            )
        )
    }
}

/**
 * 💰 가격 포맷터
 */
private fun formatPrice(price: Int): String {
    val formatter = DecimalFormat("#,###")
    return "${formatter.format(price)}원"
}

/**
 * 📦 딜 아이템 데이터 클래스 (사용자 중심)
 */
data class DealItem(
    val id: Int,
    val title: String,
    val originalPrice: Int,
    val currentPrice: Int,
    val discountRate: Int,
    val community: String,
    val postedMinutesAgo: Int,
    val imageUrl: String,
    val purchaseUrl: String,
    val hasFreeship: Boolean,
    val hasCoupon: Boolean,
    val isOverseas: Boolean,
    val isHot: Boolean,
    val coupangPrice: Int? = null,
    val savingAmount: Int? = null, // 쿠팡 대비 절약 금액
    val isBookmarked: Boolean = false
)
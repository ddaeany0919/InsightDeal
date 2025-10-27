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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import coil.compose.AsyncImage
import java.text.DecimalFormat

/**
 * 🏠 InsightDeal 메인 홈화면
 * 
 * 핫딜 정보 최우선 리스트 + 선택적 그리드 뷰
 * 알리익스프레스 스타일 디자인 + 한국 핫딜 특화 UX
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
    
    // 임시 데이터 (추후 ViewModel로 교체)
    val sampleDeals = remember {
        listOf(
            DealItem(
                id = 1,
                title = "삼성 갤럭시 버즈3 Pro 노이즈캔슬링 무선이어폰",
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
                coupangPrice = 220000
            ),
            DealItem(
                id = 2,
                title = "애플 에어팟 프로 2세대 USB-C",
                originalPrice = 359000,
                currentPrice = 299000,
                discountRate = 17,
                community = "클리앙",
                postedMinutesAgo = 15,
                imageUrl = "",
                purchaseUrl = "https://...",
                hasFreeship = false,
                hasCoupon = false,
                isOverseas = true,
                isHot = false,
                coupangPrice = 329000
            )
        )
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
        
        // 📱 메인 딜 피드
        DealFeed(
            deals = sampleDeals,
            isGridView = isGridView,
            onDealClick = { deal ->
                navController.navigate("deal_detail/${deal.id}")
            },
            onBookmarkClick = { deal ->
                // TODO: 북마크 토글
            }
        )
    }
    
    // 🎛️ 필터 바텀시트
    if (showFilterSheet) {
        FilterBottomSheet(
            onDismiss = { showFilterSheet = false }
        )
    }
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
                Text(
                    "최근 검색: 갤럭시, 에어팟, 다이슨",
                    modifier = Modifier.padding(16.dp)
                )
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
 * 📱 딜 피드 (리스트/그리드 전환 가능)
 */
@Composable
private fun DealFeed(
    deals: List<DealItem>,
    isGridView: Boolean,
    onDealClick: (DealItem) -> Unit,
    onBookmarkClick: (DealItem) -> Unit
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
            // 🏪 그리드 뷰
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
                        onBookmarkClick = { onBookmarkClick(deal) }
                    )
                }
            }
        } else {
            // 📋 리스트 뷰 (기본)
            LazyColumn(
                contentPadding = PaddingValues(vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(1.dp)
            ) {
                items(deals, key = { it.id }) { deal ->
                    DealListCard(
                        deal = deal,
                        onClick = { onDealClick(deal) },
                        onBookmarkClick = { onBookmarkClick(deal) }
                    )
                }
            }
        }
    }
}

/**
 * 📋 리스트 카드 (정보 최우선)
 */
@Composable
private fun DealListCard(
    deal: DealItem,
    onClick: () -> Unit,
    onBookmarkClick: () -> Unit
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
                
                // 가격 정보
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
                
                // 쿠팡 가격 비교
                deal.coupangPrice?.let { coupangPrice ->
                    val saving = coupangPrice - deal.currentPrice
                    if (saving > 0) {
                        Text(
                            text = "🛒 쿠팡 대비 ${formatPrice(saving)} 절약",
                            style = MaterialTheme.typography.labelMedium,
                            color = Color(0xFF00C853),
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
                
                Spacer(modifier = Modifier.height(6.dp))
                
                // 배지들
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    if (deal.hasFreeship) {
                        item {
                            BadgeChip("🚚무료배송", Color(0xFF1976D2))
                        }
                    }
                    if (deal.hasCoupon) {
                        item {
                            BadgeChip("🎟️쿠폰", Color(0xFFFF9800))
                        }
                    }
                    if (deal.isOverseas) {
                        item {
                            BadgeChip("🌏해외", Color(0xFF9C27B0))
                        }
                    }
                    if (deal.isHot) {
                        item {
                            BadgeChip("🔥인기", Color(0xFFFF5722))
                        }
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
            
            // 🎯 액션 버튼들
            Column(
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // 북마크
                IconButton(
                    onClick = onBookmarkClick,
                    modifier = Modifier.size(40.dp)
                ) {
                    Icon(
                        imageVector = if (deal.isBookmarked) Icons.Default.Bookmark else Icons.Default.BookmarkBorder,
                        contentDescription = "북마크",
                        tint = if (deal.isBookmarked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                }
                
                // 구매하기
                Button(
                    onClick = onClick,
                    modifier = Modifier.width(80.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary
                    )
                ) {
                    Text(
                        "구매",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
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
    onBookmarkClick: () -> Unit
) {
    Card(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .height(280.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize()
        ) {
            // 상품 이미지 + 북마크
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
                        tint = Color.White
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
                
                Spacer(modifier = Modifier.weight(1f))
                
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
 * 📦 딜 아이템 데이터 클래스
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
    val isBookmarked: Boolean = false
)
package com.ddaeany0919.insightdeal

import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
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
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import coil.compose.AsyncImage
import java.text.DecimalFormat

/**
 * 📋 위시리스트 화면 - "내가 추적하는 상품들"
 * 
 * 사용자 중심 설계:
 * - 폴센트 스타일: 링크 붙여넣기로 상품 추가
 * - 가격 히스토리: 30일 변동 그래프
 * - 목표가 알림: 원하는 가격 도달 시 푸시
 * - 스마트 매칭: 커뮤니티 딜과 자동 연결
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WatchlistScreen(
    navController: NavController,
    modifier: Modifier = Modifier
) {
    var showAddDialog by remember { mutableStateOf(false) }
    var selectedTab by remember { mutableStateOf(0) }
    
    // 임시 데이터
    val watchlistItems = remember {
        listOf(
            WatchlistItem(
                id = 1,
                title = "삼성 갤럭시 버즈3 Pro 노이즈캔슬링",
                currentPrice = 198000,
                targetPrice = 180000,
                originalPrice = 350000,
                lastChecked = "10분 전",
                priceHistory = listOf(220000, 210000, 205000, 198000),
                imageUrl = "",
                productUrl = "https://coupang.com/...",
                isOnSale = false,
                dealFound = true // 커뮤니티에서 매칭 발견
            ),
            WatchlistItem(
                id = 2,
                title = "애플 에어팟 프로 2세대 USB-C",
                currentPrice = 329000,
                targetPrice = 290000,
                originalPrice = 359000,
                lastChecked = "1시간 전",
                priceHistory = listOf(359000, 345000, 335000, 329000),
                imageUrl = "",
                productUrl = "https://coupang.com/...",
                isOnSale = false,
                dealFound = false
            ),
            WatchlistItem(
                id = 3,
                title = "다이슨 V15 무선청소기",
                currentPrice = 649000,
                targetPrice = 600000,
                originalPrice = 890000,
                lastChecked = "30분 전",
                priceHistory = listOf(719000, 689000, 669000, 649000),
                imageUrl = "",
                productUrl = "https://coupang.com/...",
                isOnSale = true, // 목표가 달성!
                dealFound = true
            )
        )
    }
    
    Column(
        modifier = modifier.fillMaxSize()
    ) {
        // 🎯 상단 헤더
        WatchlistHeader(
            onAddClick = { showAddDialog = true },
            selectedTab = selectedTab,
            onTabChange = { selectedTab = it }
        )
        
        when (selectedTab) {
            0 -> {
                // 📋 내 추적 목록
                WatchlistContent(
                    items = watchlistItems,
                    onItemClick = { item ->
                        navController.navigate("product_detail/${item.id}")
                    },
                    onDeleteClick = { item ->
                        // TODO: 삭제 로직
                    },
                    onTargetPriceEdit = { item, newPrice ->
                        // TODO: 목표가 수정
                    }
                )
            }
            1 -> {
                // 🎯 매칭된 딜들
                MatchedDealsContent(
                    items = watchlistItems.filter { it.dealFound },
                    onDealClick = { item ->
                        navController.navigate("deal_detail/${item.id}")
                    }
                )
            }
        }
    }
    
    // ➕ 상품 추가 다이얼로그
    if (showAddDialog) {
        AddProductDialog(
            onDismiss = { showAddDialog = false },
            onAdd = { url, targetPrice ->
                // TODO: 상품 추가 로직
                showAddDialog = false
            }
        )
    }
}

/**
 * 🎯 위시리스트 헤더
 */
@Composable
private fun WatchlistHeader(
    onAddClick: () -> Unit,
    selectedTab: Int,
    onTabChange: (Int) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "📋 내 위시리스트",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold
            )
            
            FloatingActionButton(
                onClick = onAddClick,
                modifier = Modifier.size(48.dp),
                containerColor = MaterialTheme.colorScheme.primary
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "상품 추가",
                    tint = MaterialTheme.colorScheme.onPrimary
                )
            }
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // 📑 탭
        TabRow(
            selectedTabIndex = selectedTab,
            containerColor = MaterialTheme.colorScheme.surface
        ) {
            Tab(
                selected = selectedTab == 0,
                onClick = { onTabChange(0) },
                text = { Text("추적 중") }
            )
            Tab(
                selected = selectedTab == 1,
                onClick = { onTabChange(1) },
                text = { 
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("매칭 딜")
                        Spacer(modifier = Modifier.width(4.dp))
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = Color(0xFFFF4444)
                        ) {
                            Text(
                                "2",
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.White
                            )
                        }
                    }
                }
            )
        }
    }
}

/**
 * 📋 위시리스트 메인 콘텐츠
 */
@Composable
private fun WatchlistContent(
    items: List<WatchlistItem>,
    onItemClick: (WatchlistItem) -> Unit,
    onDeleteClick: (WatchlistItem) -> Unit,
    onTargetPriceEdit: (WatchlistItem, Int) -> Unit
) {
    if (items.isEmpty()) {
        // 빈 상태
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = Icons.Default.BookmarkBorder,
                contentDescription = null,
                modifier = Modifier.size(64.dp),
                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            Text(
                text = "아직 추적하는 상품이 없어요",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
            )
            
            Text(
                text = "쿠팡 상품 링크를 붙여넣어서\n가격 변동을 추적해보세요!",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
        }
    } else {
        LazyColumn(
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(items, key = { it.id }) { item ->
                WatchlistItemCard(
                    item = item,
                    onClick = { onItemClick(item) },
                    onDeleteClick = { onDeleteClick(item) },
                    onTargetPriceEdit = { newPrice -> onTargetPriceEdit(item, newPrice) }
                )
            }
        }
    }
}

/**
 * 📋 위시리스트 아이템 카드
 */
@Composable
private fun WatchlistItemCard(
    item: WatchlistItem,
    onClick: () -> Unit,
    onDeleteClick: () -> Unit,
    onTargetPriceEdit: (Int) -> Unit
) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (item.isOnSale) {
                Color(0xFF00C853).copy(alpha = 0.1f)
            } else {
                MaterialTheme.colorScheme.surface
            }
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top
            ) {
                // 이미지
                AsyncImage(
                    model = item.imageUrl.ifEmpty { "https://via.placeholder.com/80x80" },
                    contentDescription = null,
                    modifier = Modifier
                        .size(80.dp)
                        .clip(RoundedCornerShape(8.dp)),
                    contentScale = ContentScale.Crop
                )
                
                Spacer(modifier = Modifier.width(12.dp))
                
                Column(
                    modifier = Modifier.weight(1f)
                ) {
                    // 🎯 상태 표시
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (item.isOnSale) {
                            Surface(
                                color = Color(0xFF00C853),
                                shape = RoundedCornerShape(4.dp)
                            ) {
                                Text(
                                    "🎯 목표가 달성!",
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        } else if (item.dealFound) {
                            Surface(
                                color = Color(0xFFFF9800),
                                shape = RoundedCornerShape(4.dp)
                            ) {
                                Text(
                                    "🔥 커뮤니티 딜 발견!",
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                        
                        Spacer(modifier = Modifier.weight(1f))
                        
                        IconButton(
                            onClick = onDeleteClick,
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = "삭제",
                                modifier = Modifier.size(16.dp),
                                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                            )
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(4.dp))
                    
                    // 제목
                    Text(
                        text = item.title,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Medium,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    // 가격 정보
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "현재: ${formatPrice(item.currentPrice)}",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        
                        Spacer(modifier = Modifier.width(8.dp))
                        
                        val priceChange = item.priceHistory.let { history ->
                            if (history.size >= 2) {
                                item.currentPrice - history[history.size - 2]
                            } else 0
                        }
                        
                        if (priceChange != 0) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = if (priceChange < 0) Icons.Default.TrendingDown else Icons.Default.TrendingUp,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp),
                                    tint = if (priceChange < 0) Color(0xFF00C853) else Color(0xFFFF4444)
                                )
                                
                                Text(
                                    text = "${if (priceChange > 0) "+" else ""}${formatPrice(priceChange)}",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = if (priceChange < 0) Color(0xFF00C853) else Color(0xFFFF4444)
                                )
                            }
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(4.dp))
                    
                    // 목표가
                    Text(
                        text = "목표: ${formatPrice(item.targetPrice)}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                    )
                    
                    Text(
                        text = "마지막 확인: ${item.lastChecked}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(12.dp))
            
            // 📊 간단한 가격 히스토리 (미니 그래프)
            PriceTrendIndicator(
                priceHistory = item.priceHistory,
                targetPrice = item.targetPrice
            )
        }
    }
}

/**
 * 🎯 매칭된 딜 콘텐츠
 */
@Composable
private fun MatchedDealsContent(
    items: List<WatchlistItem>,
    onDealClick: (WatchlistItem) -> Unit
) {
    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text(
                text = "🎯 추적 중인 상품이 커뮤니티에 특가로 올라왔어요!",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            
            Spacer(modifier = Modifier.height(8.dp))
        }
        
        items(items, key = { it.id }) { item ->
            MatchedDealCard(
                item = item,
                onClick = { onDealClick(item) }
            )
        }
    }
}

/**
 * 🔥 매칭된 딜 카드
 */
@Composable
private fun MatchedDealCard(
    item: WatchlistItem,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFFFF9800).copy(alpha = 0.1f)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AsyncImage(
                model = item.imageUrl.ifEmpty { "https://via.placeholder.com/60x60" },
                contentDescription = null,
                modifier = Modifier
                    .size(60.dp)
                    .clip(RoundedCornerShape(8.dp)),
                contentScale = ContentScale.Crop
            )
            
            Spacer(modifier = Modifier.width(12.dp))
            
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Surface(
                    color = Color(0xFFFF9800),
                    shape = RoundedCornerShape(4.dp)
                ) {
                    Text(
                        "🔥 커뮤니티 특가 발견!",
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White,
                        fontWeight = FontWeight.Bold
                    )
                }
                
                Spacer(modifier = Modifier.height(4.dp))
                
                Text(
                    text = item.title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                
                Text(
                    text = "목표가 ${formatPrice(item.targetPrice)} vs 커뮤니티 ${formatPrice(item.currentPrice - 20000)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                )
            }
            
            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
            )
        }
    }
}

/**
 * ➕ 상품 추가 다이얼로그
 */
@Composable
private fun AddProductDialog(
    onDismiss: () -> Unit,
    onAdd: (String, Int) -> Unit
) {
    var productUrl by remember { mutableStateOf("") }
    var targetPrice by remember { mutableStateOf("") }
    val clipboardManager = LocalClipboardManager.current
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text("➕ 상품 추가")
        },
        text = {
            Column {
                Text(
                    "쿠팡, 11번가 등의 상품 링크를 붙여넣어주세요",
                    style = MaterialTheme.typography.bodyMedium
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                
                OutlinedTextField(
                    value = productUrl,
                    onValueChange = { productUrl = it },
                    label = { Text("상품 URL") },
                    placeholder = { Text("https://coupang.com/...") },
                    modifier = Modifier.fillMaxWidth(),
                    trailingIcon = {
                        IconButton(
                            onClick = {
                                clipboardManager.getText()?.let { text ->
                                    productUrl = text.toString()
                                }
                            }
                        ) {
                            Icon(Icons.Default.ContentPaste, "붙여넣기")
                        }
                    }
                )
                
                Spacer(modifier = Modifier.height(12.dp))
                
                OutlinedTextField(
                    value = targetPrice,
                    onValueChange = { targetPrice = it },
                    label = { Text("목표가 (원)") },
                    placeholder = { Text("200000") },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (productUrl.isNotEmpty() && targetPrice.isNotEmpty()) {
                        onAdd(productUrl, targetPrice.toIntOrNull() ?: 0)
                    }
                },
                enabled = productUrl.isNotEmpty() && targetPrice.isNotEmpty()
            ) {
                Text("추가")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("취소")
            }
        }
    )
}

/**
 * 📊 가격 트렌드 인디케이터 (미니 그래프)
 */
@Composable
private fun PriceTrendIndicator(
    priceHistory: List<Int>,
    targetPrice: Int
) {
    if (priceHistory.isEmpty()) return
    
    val maxPrice = priceHistory.maxOrNull() ?: 0
    val minPrice = priceHistory.minOrNull() ?: 0
    val range = maxPrice - minPrice
    
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(24.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            "📊",
            style = MaterialTheme.typography.labelSmall
        )
        
        Spacer(modifier = Modifier.width(8.dp))
        
        // 간단한 라인 차트 효과
        Row(
            modifier = Modifier.weight(1f),
            horizontalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            priceHistory.forEachIndexed { index, price ->
                val height = if (range > 0) {
                    ((price - minPrice).toFloat() / range * 16).dp + 4.dp
                } else {
                    8.dp
                }
                
                Surface(
                    modifier = Modifier
                        .width(8.dp)
                        .height(height),
                    color = if (price <= targetPrice) {
                        Color(0xFF00C853)
                    } else {
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.7f)
                    },
                    shape = RoundedCornerShape(2.dp)
                ) {}
            }
        }
        
        Text(
            "30일",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
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
 * 📦 위시리스트 아이템 데이터 클래스
 */
data class WatchlistItem(
    val id: Int,
    val title: String,
    val currentPrice: Int,
    val targetPrice: Int,
    val originalPrice: Int,
    val lastChecked: String,
    val priceHistory: List<Int>,
    val imageUrl: String,
    val productUrl: String,
    val isOnSale: Boolean = false, // 목표가 달성
    val dealFound: Boolean = false // 커뮤니티 딜 발견
)
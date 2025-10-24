package com.ddaeany0919.insightdeal

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BookmarkScreen(
    onDealClick: (DealItem) -> Unit,
    onBackClick: () -> Unit
) {
    val context = LocalContext.current
    val bookmarkManager = remember { BookmarkManager.getInstance(context) }
    
    val bookmarks by bookmarkManager.bookmarks.collectAsState()
    val tags by bookmarkManager.tags.collectAsState()
    val stats by bookmarkManager.bookmarkStats.collectAsState()
    
    var selectedTags by remember { mutableStateOf<Set<String>>(emptySet()) }
    var sortBy by remember { mutableStateOf(BookmarkSortBy.DATE_DESC) }
    var showStats by remember { mutableStateOf(false) }
    
    // 필터링된 북마크 목록
    val filteredBookmarks = remember(bookmarks, selectedTags, sortBy) {
        bookmarkManager.searchBookmarks(
            tags = selectedTags,
            sortBy = sortBy
        )
    }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "🔖 즐겨찾기",
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "뒤로가기")
                    }
                },
                actions = {
                    IconButton(
                        onClick = { showStats = !showStats }
                    ) {
                        Icon(
                            Icons.Default.Analytics,
                            contentDescription = "통계",
                            tint = if (showStats) MaterialTheme.colorScheme.primary 
                                  else MaterialTheme.colorScheme.onSurface
                        )
                    }
                    
                    // 정렬 메뉴
                    var showSortMenu by remember { mutableStateOf(false) }
                    
                    IconButton(onClick = { showSortMenu = true }) {
                        Icon(Icons.Default.Sort, contentDescription = "정렬")
                    }
                    
                    DropdownMenu(
                        expanded = showSortMenu,
                        onDismissRequest = { showSortMenu = false }
                    ) {
                        SortOption.values().forEach { option ->
                            DropdownMenuItem(
                                text = { Text(option.displayName) },
                                onClick = {
                                    sortBy = option.sortBy
                                    showSortMenu = false
                                },
                                leadingIcon = {
                                    if (sortBy == option.sortBy) {
                                        Icon(
                                            Icons.Default.Check,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                }
                            )
                        }
                    }
                }
            )
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(16.dp)
        ) {
            // 📊 북마크 통계 (토글 가능)
            if (showStats) {
                item {
                    BookmarkStatsCard(stats = stats)
                }
            }
            
            // 🏷️ 태그 필터
            item {
                TagFilterSection(
                    tags = tags,
                    selectedTags = selectedTags,
                    onTagToggle = { tag ->
                        selectedTags = if (selectedTags.contains(tag)) {
                            selectedTags - tag
                        } else {
                            selectedTags + tag
                        }
                    },
                    onClearTags = { selectedTags = emptySet() }
                )
            }
            
            // 📋 북마크 목록
            if (filteredBookmarks.isEmpty()) {
                item {
                    BookmarkEmptyState(
                        hasFilters = selectedTags.isNotEmpty(),
                        onClearFilters = { selectedTags = emptySet() }
                    )
                }
            } else {
                item {
                    Text(
                        text = "📌 저장된 핫딜 ${filteredBookmarks.size}개",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                }
                
                items(filteredBookmarks) { bookmark ->
                    BookmarkItemCard(
                        bookmark = bookmark,
                        onClick = {
                            // DealItem으로 변환해서 클릭 처리
                            val dealItem = DealItem(
                                id = bookmark.dealId,
                                title = bookmark.title,
                                price = bookmark.currentPrice,
                                imageUrl = bookmark.imageUrl,
                                url = bookmark.url,
                                siteName = bookmark.siteName
                            )
                            onDealClick(dealItem)
                        },
                        onRemove = {
                            bookmarkManager.removeBookmark(bookmark.id)
                        },
                        onTagsUpdate = { newTags ->
                            bookmarkManager.updateBookmarkTags(bookmark.id, newTags)
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun BookmarkStatsCard(stats: BookmarkStats) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        )
    ) {
        Column(
            modifier = Modifier.padding(20.dp)
        ) {
            Text(
                text = "📊 북마크 통계",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.padding(bottom = 16.dp)
            )
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                StatsItem(
                    icon = Icons.Default.Bookmark,
                    label = "총 북마크",
                    value = "${stats.activeBookmarks}개"
                )
                
                StatsItem(
                    icon = Icons.Default.AttachMoney,
                    label = "절약 금액",
                    value = "${NumberFormat.getInstance().format(stats.totalSavings)}원"
                )
                
                StatsItem(
                    icon = Icons.Default.TrendingUp,
                    label = "평균 가격",
                    value = "${NumberFormat.getInstance().format(stats.averagePrice)}원"
                )
            }
            
            if (stats.topTags.isNotEmpty()) {
                Spacer(modifier = Modifier.height(16.dp))
                
                Text(
                    text = "인기 태그: ${stats.topTags.joinToString(", ") { "${it.first}(${it.second})" }}",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                )
            }
        }
    }
}

@Composable
private fun StatsItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    value: String
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            icon,
            contentDescription = null,
            modifier = Modifier.size(24.dp),
            tint = MaterialTheme.colorScheme.primary
        )
        
        Text(
            text = value,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(top = 4.dp)
        )
        
        Text(
            text = label,
            fontSize = 11.sp,
            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
        )
    }
}

@Composable
private fun TagFilterSection(
    tags: List<BookmarkTag>,
    selectedTags: Set<String>,
    onTagToggle: (String) -> Unit,
    onClearTags: () -> Unit
) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "🏷️ 태그 필터",
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium
            )
            
            if (selectedTags.isNotEmpty()) {
                TextButton(onClick = onClearTags) {
                    Text("전체 해제")
                }
            }
        }
        
        Spacer(modifier = Modifier.height(8.dp))
        
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(tags.filter { it.usageCount > 0 }.sortedByDescending { it.usageCount }) { tag ->
                FilterChip(
                    onClick = { onTagToggle(tag.name) },
                    label = {
                        Text("${tag.name} (${tag.usageCount})")
                    },
                    selected = selectedTags.contains(tag.name),
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = Color(android.graphics.Color.parseColor(tag.color)),
                        selectedLabelColor = Color.White
                    )
                )
            }
        }
    }
}

@Composable
private fun BookmarkItemCard(
    bookmark: BookmarkItem,
    onClick: () -> Unit,
    onRemove: () -> Unit,
    onTagsUpdate: (Set<String>) -> Unit
) {
    var showDeleteDialog by remember { mutableStateOf(false) }
    
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top
            ) {
                // 상품 이미지
                AsyncImage(
                    model = bookmark.imageUrl,
                    contentDescription = bookmark.title,
                    modifier = Modifier
                        .size(80.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                )
                
                Spacer(modifier = Modifier.width(12.dp))
                
                // 상품 정보
                Column(
                    modifier = Modifier.weight(1f)
                ) {
                    // 사이트명 + 저장 날짜
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "[${bookmark.siteName}]",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Medium
                        )
                        
                        Text(
                            text = SimpleDateFormat("MM/dd", Locale.getDefault()).format(Date(bookmark.createdAt)),
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    
                    Spacer(modifier = Modifier.height(4.dp))
                    
                    // 상품명
                    Text(
                        text = bookmark.title,
                        fontSize = 14.sp,
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
                            text = "${NumberFormat.getInstance().format(bookmark.currentPrice)}원",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        
                        // 가격 변동 표시
                        if (bookmark.currentPrice != bookmark.originalPrice) {
                            Spacer(modifier = Modifier.width(8.dp))
                            
                            val priceDiff = bookmark.originalPrice - bookmark.currentPrice
                            val isDown = priceDiff > 0
                            
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = if (isDown) Color.Red else Color.Blue
                            ) {
                                Text(
                                    text = "${if (isDown) "-" else "+"}${NumberFormat.getInstance().format(kotlin.math.abs(priceDiff))}원",
                                    fontSize = 11.sp,
                                    color = Color.White,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }
                }
                
                // 삭제 버튼
                IconButton(
                    onClick = { showDeleteDialog = true },
                    modifier = Modifier.size(24.dp)
                ) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = "삭제",
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.error
                    )
                }
            }
            
            // 태그들
            if (bookmark.tags.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    items(bookmark.tags.toList()) { tag ->
                        AssistChip(
                            onClick = { },
                            label = {
                                Text(
                                    text = tag,
                                    fontSize = 11.sp
                                )
                            },
                            modifier = Modifier.height(24.dp)
                        )
                    }
                }
            }
        }
    }
    
    // 삭제 확인 다이얼로그
    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("북마크 삭제") },
            text = { Text("정말로 이 북마크를 삭제하시겠습니까?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        onRemove()
                        showDeleteDialog = false
                    }
                ) {
                    Text("삭제", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text("취소")
                }
            }
        )
    }
}

@Composable
private fun BookmarkEmptyState(
    hasFilters: Boolean,
    onClearFilters: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            if (hasFilters) Icons.Default.FilterList else Icons.Default.BookmarkBorder,
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        Text(
            text = if (hasFilters) "선택한 태그에 해당하는\n북마크가 없습니다" else "아직 저장된 핫딜이 없습니다",
            fontSize = 16.sp,
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.Center
        )
        
        Text(
            text = if (hasFilters) "다른 태그를 선택하거나 필터를 해제해보세요" else "마음에 드는 핫딜을 찾아서\n하트 버튼을 눌러보세요",
            fontSize = 14.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 8.dp)
        )
        
        if (hasFilters) {
            Spacer(modifier = Modifier.height(16.dp))
            
            Button(onClick = onClearFilters) {
                Text("모든 필터 해제")
            }
        }
    }
}

// 정렬 옵션 enum
private enum class SortOption(val displayName: String, val sortBy: BookmarkSortBy) {
    DATE_DESC("최신 순", BookmarkSortBy.DATE_DESC),
    DATE_ASC("오래된 순", BookmarkSortBy.DATE_ASC),
    PRICE_LOW("가격 낮은 순", BookmarkSortBy.PRICE_LOW),
    PRICE_HIGH("가격 높은 순", BookmarkSortBy.PRICE_HIGH),
    TITLE("이름 순", BookmarkSortBy.TITLE),
    SITE("사이트 순", BookmarkSortBy.SITE)
}
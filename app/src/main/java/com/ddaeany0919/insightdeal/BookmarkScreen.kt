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
import com.ddaeany0919.insightdeal.models.DealItem
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.*
import com.ddaeany0919.insightdeal.ui.A11yIconButton
import com.ddaeany0919.insightdeal.ui.rememberSavedLazyListState
import com.ddaeany0919.insightdeal.ui.rememberMinuteTicker
import com.ddaeany0919.insightdeal.ui.SavingTextStrong

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

    // 리스트 위치 복원 + 상대시간 틱
    val listState = rememberSavedLazyListState(key = "bookmark_list")
    val _ = rememberMinuteTicker()

    val filteredBookmarks = remember(bookmarks, selectedTags, sortBy) {
        bookmarkManager.searchBookmarks(
            tags = selectedTags,
            sortBy = sortBy
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(text = "🔖 즐겨찾기", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    A11yIconButton(onClick = onBackClick, contentDescription = "뒤로가기") {
                        Icon(Icons.Default.ArrowBack, contentDescription = null)
                    }
                },
                actions = {
                    A11yIconButton(onClick = { showStats = !showStats }, contentDescription = "통계") {
                        Icon(
                            Icons.Default.Analytics,
                            contentDescription = null,
                            tint = if (showStats) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                        )
                    }
                    var showSortMenu by remember { mutableStateOf(false) }
                    A11yIconButton(onClick = { showSortMenu = true }, contentDescription = "정렬") {
                        Icon(Icons.Default.Sort, contentDescription = null)
                    }
                    DropdownMenu(expanded = showSortMenu, onDismissRequest = { showSortMenu = false }) {
                        SortOption.values().forEach { option ->
                            DropdownMenuItem(
                                text = { Text(option.displayName) },
                                onClick = {
                                    sortBy = option.sortBy
                                    showSortMenu = false
                                },
                                leadingIcon = {
                                    if (sortBy == option.sortBy) {
                                        Icon(Icons.Default.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
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
            contentPadding = PaddingValues(16.dp),
            state = listState
        ) {
            if (showStats) { item { BookmarkStatsCard(stats = stats) } }
            item {
                TagFilterSection(
                    tags = tags,
                    selectedTags = selectedTags,
                    onTagToggle = { tag ->
                        selectedTags = if (selectedTags.contains(tag)) selectedTags - tag else selectedTags + tag
                    },
                    onClearTags = { selectedTags = emptySet() }
                )
            }
            if (filteredBookmarks.isEmpty()) {
                item { BookmarkEmptyState(hasFilters = selectedTags.isNotEmpty(), onClearFilters = { selectedTags = emptySet() }) }
            } else {
                item { Text(text = "📌 저장된 핫딜 ${filteredBookmarks.size}개", fontSize = 16.sp, fontWeight = FontWeight.Medium, modifier = Modifier.padding(bottom = 8.dp)) }
                items(filteredBookmarks) { bookmark ->
                    BookmarkItemCard(
                        bookmark = bookmark,
                        onClick = {
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
                        onRemove = { bookmarkManager.removeBookmark(bookmark.id) },
                        onTagsUpdate = { newTags -> bookmarkManager.updateBookmarkTags(bookmark.id, newTags) }
                    )
                }
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
        Column(modifier = Modifier.padding(16.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                AsyncImage(
                    model = bookmark.imageUrl,
                    contentDescription = bookmark.title,
                    modifier = Modifier
                        .size(80.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(text = "[${bookmark.siteName}]", fontSize = 12.sp, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Medium)
                        Text(text = SimpleDateFormat("MM/dd", Locale.getDefault()).format(Date(bookmark.createdAt)), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(text = bookmark.title, fontSize = 14.sp, fontWeight = FontWeight.Medium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(text = "${NumberFormat.getInstance().format(bookmark.currentPrice)}원", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                        if (bookmark.currentPrice != bookmark.originalPrice) {
                            Spacer(modifier = Modifier.width(8.dp))
                            val priceDiff = bookmark.originalPrice - bookmark.currentPrice
                            val isDown = priceDiff > 0
                            Surface(shape = RoundedCornerShape(12.dp), color = if (isDown) Color(0xFFB71C1C) else Color(0xFF0D47A1)) {
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
                A11yIconButton(onClick = { showDeleteDialog = true }, contentDescription = "삭제", modifier = Modifier.size(24.dp)) {
                    Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.error)
                }
            }
            if (bookmark.tags.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    items(bookmark.tags.toList()) { tag ->
                        AssistChip(onClick = { }, label = { Text(text = tag, fontSize = 11.sp) }, modifier = Modifier.height(24.dp))
                    }
                }
            }
        }
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("북마크 삭제") },
            text = { Text("정말로 이 북마크를 삭제하시겠습니까?") },
            confirmButton = {
                TextButton(onClick = { onRemove(); showDeleteDialog = false }) { Text("삭제", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { showDeleteDialog = false }) { Text("취소") } }
        )
    }
}

package com.example.insightdeal.ui.bookmark

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.platform.*
import androidx.compose.ui.text.*
import androidx.compose.ui.text.font.*
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.*
import com.example.insightdeal.data.BookmarkManager
import com.example.insightdeal.model.BookmarkItem


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BookmarkScreen(
    onDealClick: (Int) -> Unit,
    onBackClick: () -> Unit
) {
    val context = LocalContext.current
    val bookmarkManager = remember { BookmarkManager.getInstance(context) }
    val bookmarks by bookmarkManager.bookmarks.collectAsState()

    var selectedCategory by remember { mutableStateOf("전체") }
    var searchQuery by remember { mutableStateOf("") }
    var showDeleteDialog by remember { mutableStateOf(false) }

    // 필터링된 북마크
    val filteredBookmarks = remember(bookmarks, selectedCategory, searchQuery) {
        var filtered = if (selectedCategory == "전체") {
            bookmarks
        } else {
            bookmarks.filter { it.category == selectedCategory }
        }

        if (searchQuery.isNotBlank()) {
            filtered = filtered.filter { bookmark ->
                bookmark.title.contains(searchQuery, ignoreCase = true) ||
                        bookmark.shopName.contains(searchQuery, ignoreCase = true) ||
                        bookmark.community.contains(searchQuery, ignoreCase = true)
            }
        }

        filtered
    }

    // 카테고리 목록
    val categories = remember(bookmarks) {
        listOf("전체") + bookmarks.map { it.category ?: "기타" }.distinct().sorted()
    }

    Scaffold(
        topBar = {
            BookmarkTopBar(
                bookmarkCount = bookmarks.size,
                searchQuery = searchQuery,
                onSearchQueryChange = { searchQuery = it },
                onBackClick = onBackClick,
                onClearAll = { showDeleteDialog = true }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(MaterialTheme.colorScheme.background)
        ) {
            // 카테고리 필터
            if (categories.size > 1) {
                CategoryFilterChips(
                    categories = categories,
                    selectedCategory = selectedCategory,
                    onCategorySelect = { selectedCategory = it }
                )
            }

            // 북마크 리스트
            when {
                bookmarks.isEmpty() -> {
                    BookmarkEmptyState()
                }
                filteredBookmarks.isEmpty() -> {
                    SearchEmptyState(query = searchQuery)
                }
                else -> {
                    LazyColumn(
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(
                            items = filteredBookmarks,
                            key = { bookmark -> "bookmark_${bookmark.id}" }
                        ) { bookmark ->
                            BookmarkCard(
                                bookmark = bookmark,
                                onDealClick = { onDealClick(bookmark.id) },
                                onRemoveBookmark = {
                                    bookmarkManager.removeBookmark(bookmark.id)
                                }
                            )
                        }
                    }
                }
            }
        }
    }

    // 전체 삭제 확인 다이얼로그
    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("북마크 전체 삭제") },
            text = { Text("모든 북마크를 삭제하시겠습니까?\n이 작업은 되돌릴 수 없습니다.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        bookmarkManager.clearAllBookmarks()
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BookmarkTopBar(
    bookmarkCount: Int,
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    onBackClick: () -> Unit,
    onClearAll: () -> Unit
) {
    TopAppBar(
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Bookmark,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
                Text(
                    text = "북마크 ($bookmarkCount)",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        },
        navigationIcon = {
            IconButton(onClick = onBackClick) {
                Icon(Icons.Default.ArrowBack, contentDescription = "뒤로가기")
            }
        },
        actions = {
            // 검색창
            OutlinedTextField(
                value = searchQuery,
                onValueChange = onSearchQueryChange,
                modifier = Modifier.width(200.dp),
                placeholder = { Text("북마크 검색", fontSize = 14.sp) },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { onSearchQueryChange("") }) {
                            Icon(Icons.Default.Clear, contentDescription = "지우기")
                        }
                    } else {
                        Icon(Icons.Default.Search, contentDescription = null)
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(20.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
                )
            )

            // 전체 삭제 버튼
            if (bookmarkCount > 0) {
                IconButton(onClick = onClearAll) {
                    Icon(
                        Icons.Default.DeleteSweep,
                        contentDescription = "전체 삭제",
                        tint = MaterialTheme.colorScheme.error
                    )
                }
            }
        }
    )
}

@Composable
private fun CategoryFilterChips(
    categories: List<String>,
    selectedCategory: String,
    onCategorySelect: (String) -> Unit
) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(categories) { category ->
            FilterChip(
                selected = category == selectedCategory,
                onClick = { onCategorySelect(category) },
                label = { Text(category) },
                leadingIcon = if (category == selectedCategory) {
                    {
                        Icon(
                            Icons.Default.Check,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                } else null
            )
        }
    }
}

@Composable
private fun BookmarkEmptyState() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Icon(
                imageVector = Icons.Default.BookmarkBorder,
                contentDescription = null,
                modifier = Modifier.size(80.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = "저장된 북마크가 없어요",
                fontSize = 18.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = "관심 있는 딜을 북마크해보세요!",
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun SearchEmptyState(query: String) {
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
                text = "\"$query\"와 일치하는 북마크가 없어요",
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center
            )
            Text(
                text = "다른 검색어를 시도해보세요",
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

package com.example.insightdeal.ui.components

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.*
import androidx.compose.foundation.text.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.focus.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.platform.*
import androidx.compose.ui.text.font.*
import androidx.compose.ui.text.input.*
import androidx.compose.ui.text.style.*
import androidx.compose.ui.unit.*
import androidx.compose.ui.window.*
import com.example.insightdeal.data.SearchHistoryManager
import com.example.insightdeal.model.SearchHistoryItem
import com.example.insightdeal.ui.search.TrendingKeywordSuggestions
import com.example.insightdeal.ui.search.SearchHistoryDialog

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchDialog(
    onDismiss: () -> Unit,
    onSearch: (String) -> Unit
) {
    val context = LocalContext.current
    val searchHistoryManager = remember { SearchHistoryManager.getInstance(context) }
    val recentSearches by searchHistoryManager.searchHistory.collectAsState()

    var searchText by remember { mutableStateOf("") }
    var showSearchHistory by remember { mutableStateOf(false) }
    val focusRequester = remember { FocusRequester() }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.9f)
                .padding(16.dp),
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface
        ) {
            Column(
                modifier = Modifier.fillMaxSize()
            ) {
                // 헤더
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "딜 검색",
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "상품명, 쇼핑몰, 커뮤니티로 검색",
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // 검색 기록 보기 버튼
                        if (recentSearches.isNotEmpty()) {
                            IconButton(
                                onClick = { showSearchHistory = true },
                                modifier = Modifier
                                    .background(
                                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f),
                                        CircleShape
                                    )
                            ) {
                                Icon(
                                    Icons.Default.History,
                                    contentDescription = "검색 기록",
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        }

                        IconButton(onClick = onDismiss) {
                            Icon(
                                Icons.Default.Close,
                                contentDescription = "닫기",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                // 검색창
                OutlinedTextField(
                    value = searchText,
                    onValueChange = { searchText = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp)
                        .focusRequester(focusRequester),
                    placeholder = {
                        Text(
                            "검색어를 입력하세요 (예: 갤럭시, 쿠팡, 무료배송)",
                            fontSize = 14.sp
                        )
                    },
                    leadingIcon = {
                        Icon(Icons.Default.Search, contentDescription = null)
                    },
                    trailingIcon = if (searchText.isNotEmpty()) {
                        {
                            IconButton(onClick = { searchText = "" }) {
                                Icon(Icons.Default.Clear, contentDescription = "지우기")
                            }
                        }
                    } else null,
                    shape = RoundedCornerShape(16.dp),
                    singleLine = true,
                    keyboardActions = KeyboardActions(
                        onSearch = {
                            if (searchText.isNotBlank()) {
                                // 검색 기록에 추가
                                searchHistoryManager.addSearchHistory(searchText)
                                onSearch(searchText)
                            }
                        }
                    ),
                    keyboardOptions = KeyboardOptions.Default.copy(
                        imeAction = ImeAction.Search
                    )
                )

                Spacer(modifier = Modifier.height(24.dp))

                // 최근 검색어 섹션
                if (recentSearches.isNotEmpty()) {
                    RecentSearchSection(
                        recentSearches = recentSearches.take(5),
                        onSearchClick = { query ->
                            searchText = query
                            searchHistoryManager.addSearchHistory(query)
                            onSearch(query)
                        },
                        onDeleteSearch = { query ->
                            searchHistoryManager.removeSearchHistory(query)
                        }
                    )

                    Spacer(modifier = Modifier.height(16.dp))
                }

                // 인기 키워드 섹션
                TrendingKeywordSuggestions(
                    onKeywordClick = { keyword ->
                        searchText = keyword
                        searchHistoryManager.addSearchHistory(keyword)
                        onSearch(keyword)
                    }
                )

                Spacer(modifier = Modifier.weight(1f))

                // 검색 버튼
                Button(
                    onClick = {
                        if (searchText.isNotBlank()) {
                            searchHistoryManager.addSearchHistory(searchText)
                            onSearch(searchText)
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    shape = RoundedCornerShape(16.dp),
                    enabled = searchText.isNotBlank()
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.Search,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp)
                        )
                        Text(
                            text = "검색하기",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(vertical = 4.dp)
                        )
                    }
                }
            }
        }
    }

    // 검색 기록 상세 다이얼로그
    if (showSearchHistory) {
        SearchHistoryDialog(
            onDismiss = { showSearchHistory = false },
            onSearchHistoryClick = { query ->
                searchText = query
                showSearchHistory = false
                searchHistoryManager.addSearchHistory(query)
                onSearch(query)
            }
        )
    }

    // 검색창 자동 포커스
    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }
}

@Composable
private fun RecentSearchSection(
    recentSearches: List<SearchHistoryItem>,
    onSearchClick: (String) -> Unit,
    onDeleteSearch: (String) -> Unit
) {
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "최근 검색어",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        LazyColumn(
            modifier = Modifier.heightIn(max = 200.dp),
            contentPadding = PaddingValues(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            items(recentSearches) { historyItem ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onSearchClick(historyItem.query) }
                        .padding(vertical = 8.dp, horizontal = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            imageVector = Icons.Default.History,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(16.dp)
                        )

                        Text(
                            text = historyItem.query,
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    IconButton(
                        onClick = { onDeleteSearch(historyItem.query) },
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "삭제",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SearchHistoryEmptyState() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Icon(
                imageVector = Icons.Default.History,
                contentDescription = null,
                modifier = Modifier.size(64.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = "검색 기록이 없어요",
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = "검색을 시작해보세요!",
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

package com.example.insightdeal.ui.search

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
import androidx.compose.ui.text.font.*
import androidx.compose.ui.text.input.*
import androidx.compose.ui.text.style.*
import androidx.compose.ui.unit.*
import androidx.compose.ui.window.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdvancedSearchDialog(
    recentSearches: List<String>,
    onDismiss: () -> Unit,
    onSearch: (String) -> Unit,
    onRecentSearchDelete: (String) -> Unit
) {
    var searchText by remember { mutableStateOf("") }
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

                    IconButton(onClick = onDismiss) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = "닫기",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
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
                                onSearch(searchText)
                            }
                        }
                    ),
                    keyboardOptions = KeyboardOptions.Default.copy(
                        imeAction = ImeAction.Search
                    )
                )

                Spacer(modifier = Modifier.height(24.dp))

                // 최근 검색어 또는 인기 키워드
                if (recentSearches.isNotEmpty()) {
                    RecentSearchHistory(
                        recentSearches = recentSearches,
                        onSearchClick = { searchTerm ->
                            searchText = searchTerm
                            onSearch(searchTerm)
                        },
                        onDeleteClick = onRecentSearchDelete
                    )
                } else {
                    TrendingKeywordSuggestions(
                        onKeywordClick = { keyword ->
                            searchText = keyword
                            onSearch(keyword)
                        }
                    )
                }

                Spacer(modifier = Modifier.weight(1f))

                // 검색 버튼
                Button(
                    onClick = {
                        if (searchText.isNotBlank()) {
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

    // 검색창 자동 포커스
    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }
}

@Composable
fun RecentSearchHistory(
    recentSearches: List<String>,
    onSearchClick: (String) -> Unit,
    onDeleteClick: (String) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "최근 검색어",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )

            TextButton(
                onClick = {
                    // TODO: 모든 최근 검색어 삭제 확인 다이얼로그
                }
            ) {
                Text(
                    "전체삭제",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.error
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(recentSearches) { searchTerm ->
                RecentSearchItem(
                    searchTerm = searchTerm,
                    onSearchClick = { onSearchClick(searchTerm) },
                    onDeleteClick = { onDeleteClick(searchTerm) }
                )
            }
        }
    }
}

@Composable
fun RecentSearchItem(
    searchTerm: String,
    onSearchClick: () -> Unit,
    onDeleteClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onSearchClick() },
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.History,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(16.dp)
                )
                Text(
                    text = searchTerm,
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            IconButton(
                onClick = onDeleteClick,
                modifier = Modifier.size(32.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "삭제",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

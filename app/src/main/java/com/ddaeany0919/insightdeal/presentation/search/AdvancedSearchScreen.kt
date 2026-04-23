package com.ddaeany0919.insightdeal.presentation.search

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import androidx.paging.compose.collectAsLazyPagingItems
import com.ddaeany0919.insightdeal.ui.home.DealCardComposable
import com.ddaeany0919.insightdeal.ui.home.HomeViewModel

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun AdvancedSearchScreen(navController: NavController, viewModel: HomeViewModel = viewModel()) {
    var searchQuery by remember { mutableStateOf("") }
    var isSearching by remember { mutableStateOf(false) }
    val focusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current
    
    // 재사용할 HomeViewModel 상태
    val dealsPagingItems = viewModel.dealsPagingData.collectAsLazyPagingItems()
    val popularKeywords by viewModel.popularKeywords.collectAsState()

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
        // 검색 화면 초기화 - 빈 결과 셋팅
        viewModel.searchDeals("")
    }

    fun performSearch(query: String) {
        if (query.isNotBlank()) {
            searchQuery = query
            isSearching = true
            keyboardController?.hide()
            viewModel.searchDeals(query)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    TextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(end = 8.dp)
                            .focusRequester(focusRequester),
                        placeholder = { Text("핫딜 상품, 브랜드를 검색해보세요", fontSize = 14.sp) },
                        singleLine = true,
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent,
                            disabledContainerColor = Color.Transparent,
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent
                        ),
                        trailingIcon = {
                            if (searchQuery.isNotEmpty()) {
                                IconButton(onClick = { 
                                    searchQuery = "" 
                                    isSearching = false
                                }) {
                                    Icon(Icons.Default.Clear, contentDescription = "지우기")
                                }
                            } else {
                                IconButton(onClick = { performSearch(searchQuery) }) {
                                    Icon(Icons.Default.Search, contentDescription = "검색")
                                }
                            }
                        },
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                            imeAction = androidx.compose.ui.text.input.ImeAction.Search
                        ),
                        keyboardActions = androidx.compose.foundation.text.KeyboardActions(
                            onSearch = { performSearch(searchQuery) }
                        )
                    )
                },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "뒤로")
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            if (!isSearching && searchQuery.isEmpty()) {
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "🔥 실시간 급상승 검색어", 
                    fontWeight = FontWeight.Bold, 
                    fontSize = 16.sp, 
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
                Spacer(modifier = Modifier.height(12.dp))
                
                FlowRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    popularKeywords.forEach { keyword ->
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            modifier = Modifier.clickable { performSearch(keyword) }
                        ) {
                            Text(
                                text = keyword,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                fontSize = 14.sp
                            )
                        }
                    }
                }
            } else {
                Spacer(modifier = Modifier.height(8.dp))
                if (dealsPagingItems.loadState.refresh is androidx.paging.LoadState.Loading) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                } else if (dealsPagingItems.itemCount == 0) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("'$searchQuery'에 해당하는 핫딜이 없습니다.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                } else {
                    Text(
                        text = "검색 결과",
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium
                    )
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 80.dp)
                    ) {
                        items(dealsPagingItems.itemCount) { index ->
                            val deal = dealsPagingItems[index]
                            if (deal != null) {
                                DealCardComposable(deal = deal)
                            }
                        }
                    }
                }
            }
        }
    }
}
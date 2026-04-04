package com.ddaeany0919.insightdeal.feature.home

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.ddaeany0919.insightdeal.data.Resource
import com.ddaeany0919.insightdeal.models.ApiDeal

/**
 * EnhancedHomeScreen???ㅼ떆媛??곷??쒓컙/?ㅽ겕濡?蹂듭썝 ?좏떥 諛??꾪꽣(寃?? 移댄뀒怨좊━ 移? 異붽?
 */
@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    onDealClick: (ApiDeal) -> Unit,
    onBookmarkClick: (ApiDeal) -> Unit,
    onTrackClick: (ApiDeal) -> Unit
) {
    val listState = rememberLazyListState()
    val dealsState by viewModel.popularDeals.collectAsState()
    val isRefreshing by viewModel.isRefreshing.collectAsState()

    var searchQuery by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf("?꾩껜") }
    
    val categories = listOf("?꾩껜", "媛???붿???, "?앺뭹/?앺븘??, "?⑥뀡/酉고떚", "湲고?")

    EnhancedHomeScreenCore(
        dealsState = dealsState,
        isRefreshing = isRefreshing,
        onRefresh = { viewModel.refreshFeed() },
        listState = listState,
        searchQuery = searchQuery,
        onSearchQueryChange = { searchQuery = it },
        selectedCategory = selectedCategory,
        onCategorySelect = { selectedCategory = it },
        categories = categories,
        onDealClick = onDealClick,
        onBookmarkClick = onBookmarkClick,
        onTrackClick = onTrackClick
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EnhancedHomeScreenCore(
    dealsState: Resource<List<ApiDeal>>,
    isRefreshing: Boolean,
    onRefresh: () -> Unit,
    listState: androidx.compose.foundation.lazy.LazyListState,
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    selectedCategory: String,
    onCategorySelect: (String) -> Unit,
    categories: List<String>,
    onDealClick: (ApiDeal) -> Unit,
    onBookmarkClick: (ApiDeal) -> Unit,
    onTrackClick: (ApiDeal) -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Insight Deal", fontWeight = FontWeight.Bold) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.primary
                )
            )
        }
    ) { paddingValues ->
        Column(modifier = Modifier.fillMaxSize().padding(paddingValues)) {
            
            // [Task 1] 寃??諛??곕룞
            OutlinedTextField(
                value = searchQuery,
                onValueChange = onSearchQueryChange,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                placeholder = { Text("?먰븯??紐⑤뜽紐?1珥?留뚯뿉 寃??..") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = "寃??) },
                singleLine = true,
                shape = MaterialTheme.shapes.medium
            )
            
            // [Task 1] 移댄뀒怨좊━ 移??꾪꽣留?            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(bottom = 8.dp)
            ) {
                items(categories) { category ->
                    FilterChip(
                        selected = selectedCategory == category,
                        onClick = { onCategorySelect(category) },
                        label = { Text(category) },
                        shape = MaterialTheme.shapes.small
                    )
                }
            }
            
            Box(modifier = Modifier.fillMaxSize().weight(1f)) {
                if (isRefreshing) {
                    LinearProgressIndicator(
                        modifier = Modifier
                            .fillMaxWidth()
                            .align(Alignment.TopCenter)
                            .zIndex(1f)
                    )
                }
                
                when (dealsState) {
                    is Resource.Loading -> {
                        if (!isRefreshing) {
                            CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                        }
                    }
                    is Resource.Error -> {
                        Column(
                            modifier = Modifier.align(Alignment.Center),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = dealsState.message ?: "?????녿뒗 ?ㅻ쪟媛 諛쒖깮?덉뒿?덈떎.",
                                color = MaterialTheme.colorScheme.error
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Button(onClick = onRefresh) {
                                Text("?ㅼ떆 ?쒕룄")
                            }
                        }
                    }
                    is Resource.Success -> {
                        val deals = dealsState.data ?: emptyList()
                        
                        // ?ъ슜??濡쒖뺄 ?꾪꽣留?泥섎━ (MVP)
                        val filteredDeals = deals.filter { deal ->
                            val matchSearch = searchQuery.isBlank() || deal.title.contains(searchQuery, ignoreCase = true)
                            val matchCategory = selectedCategory == "?꾩껜" // ?ν썑 API category 留ㅼ묶 ?꾩슂
                            matchSearch && matchCategory
                        }

                        if (filteredDeals.isEmpty()) {
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Text("寃??議곌굔??留욌뒗 ?ル뵜???놁뒿?덈떎.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        } else {
                            LazyColumn(
                                state = listState,
                                contentPadding = PaddingValues(bottom = 16.dp),
                                modifier = Modifier.fillMaxSize()
                            ) {
                                items(items = filteredDeals, key = { it.id }) { deal ->
                                    DealCard(
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
            }
        }
    }
}

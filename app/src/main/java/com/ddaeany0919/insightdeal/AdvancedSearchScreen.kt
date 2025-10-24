package com.ddaeany0919.insightdeal

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdvancedSearchScreen(
    onDealClick: (DealItem) -> Unit,
    onBackClick: () -> Unit,
    viewModel: AdvancedSearchViewModel = viewModel()
) {
    val searchQuery by viewModel.searchQuery.collectAsState()
    val searchResults by viewModel.searchResults.collectAsState()
    val searchSuggestions by viewModel.searchSuggestions.collectAsState()
    val activeFilters by viewModel.activeFilters.collectAsState()
    val popularKeywords by viewModel.popularKeywords.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    
    var showFilters by remember { mutableStateOf(false) }
    val focusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current
    
    LaunchedEffect(Unit) {
        viewModel.loadPopularKeywords()
        focusRequester.requestFocus()
    }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "스마트 검색",
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
                        onClick = { showFilters = !showFilters }
                    ) {
                        Badge(
                            modifier = if (activeFilters.isNotEmpty()) Modifier else Modifier.size(0.dp)
                        ) {
                            Text("${activeFilters.size}")
                        }
                        Icon(
                            Icons.Default.FilterList,
                            contentDescription = "필터",
                            tint = if (showFilters) MaterialTheme.colorScheme.primary 
                                  else MaterialTheme.colorScheme.onSurface
                        )
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
            // 🔍 검색 입력 영역
            SearchInputSection(
                searchQuery = searchQuery,
                onQueryChange = viewModel::onSearchQueryChanged,
                suggestions = searchSuggestions,
                onSuggestionClick = viewModel::selectSuggestion,
                onSearch = {
                    viewModel.performSearch()
                    keyboardController?.hide()
                },
                focusRequester = focusRequester
            )
            
            // 🎯 필터 영역 (접을 수 있음)
            AnimatedVisibility(
                visible = showFilters,
                enter = slideInVertically() + expandVertically(),
                exit = slideOutVertically() + shrinkVertically()
            ) {
                FilterSection(
                    activeFilters = activeFilters,
                    onFilterToggle = viewModel::toggleFilter,
                    onClearFilters = viewModel::clearAllFilters
                )
            }
            
            // 🔥 인기 검색어 (검색어 없을 때만 표시)
            if (searchQuery.isEmpty() && searchResults.isEmpty()) {
                PopularKeywordsSection(
                    keywords = popularKeywords,
                    onKeywordClick = viewModel::selectSuggestion
                )
            }
            
            // 📱 검색 결과 영역
            when {
                isLoading -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            CircularProgressIndicator(
                                color = MaterialTheme.colorScheme.primary
                            )
                            
                            Spacer(modifier = Modifier.height(16.dp))
                            
                            Text(
                                text = "🔍 검색 중...",
                                fontSize = 16.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
                
                searchResults.isEmpty() && searchQuery.isNotEmpty() -> {
                    SearchEmptyState(searchQuery)
                }
                
                else -> {
                    SearchResultsList(
                        results = searchResults,
                        onDealClick = onDealClick,
                        query = searchQuery
                    )
                }
            }
        }
    }
}

@Composable
private fun SearchInputSection(
    searchQuery: String,
    onQueryChange: (String) -> Unit,
    suggestions: List<String>,
    onSuggestionClick: (String) -> Unit,
    onSearch: () -> Unit,
    focusRequester: FocusRequester
) {
    Column(
        modifier = Modifier.padding(16.dp)
    ) {
        OutlinedTextField(
            value = searchQuery,
            onValueChange = onQueryChange,
            modifier = Modifier
                .fillMaxWidth()
                .focusRequester(focusRequester),
            placeholder = {
                Text(
                    text = "🔍 상품명, 브랜드, 카테고리 검색...",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            leadingIcon = {
                Icon(
                    Icons.Default.Search,
                    contentDescription = "검색",
                    tint = MaterialTheme.colorScheme.primary
                )
            },
            trailingIcon = {
                if (searchQuery.isNotEmpty()) {
                    IconButton(onClick = { onQueryChange("") }) {
                        Icon(
                            Icons.Default.Clear,
                            contentDescription = "지우기"
                        )
                    }
                }
            },
            keyboardOptions = KeyboardOptions(
                imeAction = ImeAction.Search
            ),
            keyboardActions = KeyboardActions(
                onSearch = { onSearch() }
            ),
            singleLine = true,
            shape = RoundedCornerShape(12.dp)
        )
        
        // 💡 검색 자동완성 (드롭다운)
        AnimatedVisibility(
            visible = suggestions.isNotEmpty() && searchQuery.isNotEmpty(),
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically()
        ) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
            ) {
                LazyColumn(
                    modifier = Modifier.heightIn(max = 200.dp)
                ) {
                    items(suggestions.take(5)) { suggestion ->
                        SuggestionItem(
                            suggestion = suggestion,
                            query = searchQuery,
                            onClick = { onSuggestionClick(suggestion) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SuggestionItem(
    suggestion: String,
    query: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            Icons.Default.Search,
            contentDescription = null,
            modifier = Modifier.size(18.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
        
        Spacer(modifier = Modifier.width(12.dp))
        
        // 검색어 하이라이팅
        Text(
            text = suggestion,
            fontSize = 14.sp,
            modifier = Modifier.weight(1f)
        )
        
        Icon(
            Icons.Default.NorthWest,
            contentDescription = null,
            modifier = Modifier.size(16.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun FilterSection(
    activeFilters: Map<String, Set<String>>,
    onFilterToggle: (String, String) -> Unit,
    onClearFilters: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "🎯 상세 필터",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
                
                if (activeFilters.values.any { it.isNotEmpty() }) {
                    TextButton(onClick = onClearFilters) {
                        Text("전체 해제")
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(12.dp))
            
            // 가격대 필터
            FilterGroup(
                title = "💰 가격대",
                options = listOf("5만원 이하", "5-15만원", "15-50만원", "50-100만원", "100만원 이상"),
                selectedOptions = activeFilters["price"] ?: emptySet(),
                onToggle = { option -> onFilterToggle("price", option) }
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // 할인율 필터  
            FilterGroup(
                title = "🔥 할인율",
                options = listOf("30% 이상", "50% 이상", "70% 이상"),
                selectedOptions = activeFilters["discount"] ?: emptySet(),
                onToggle = { option -> onFilterToggle("discount", option) }
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // 카테고리 필터
            FilterGroup(
                title = "📱 카테고리",
                options = listOf("IT", "패션", "생활", "식품", "해외직구", "스포츠"),
                selectedOptions = activeFilters["category"] ?: emptySet(),
                onToggle = { option -> onFilterToggle("category", option) }
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // 사이트 필터 (우선순위 반영)
            FilterGroup(
                title = "🌐 커뮤니티",
                options = listOf("뽐뿌", "에펨코리아", "빠삭", "루리웹", "클리앙", "퀘이사존"),
                selectedOptions = activeFilters["site"] ?: emptySet(),
                onToggle = { option -> onFilterToggle("site", option) }
            )
        }
    }
}

@Composable
private fun FilterGroup(
    title: String,
    options: List<String>,
    selectedOptions: Set<String>,
    onToggle: (String) -> Unit
) {
    Column {
        Text(
            text = title,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(bottom = 8.dp)
        )
        
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(options) { option ->
                FilterChip(
                    onClick = { onToggle(option) },
                    label = { Text(option) },
                    selected = option in selectedOptions,
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.primary,
                        selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                    )
                )
            }
        }
    }
}

@Composable
private fun PopularKeywordsSection(
    keywords: List<String>,
    onKeywordClick: (String) -> Unit
) {
    Column(
        modifier = Modifier.padding(16.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(bottom = 12.dp)
        ) {
            Icon(
                Icons.Default.TrendingUp,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            
            Spacer(modifier = Modifier.width(8.dp))
            
            Text(
                text = "🔥 실시간 인기 검색어",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold
            )
        }
        
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(keywords.take(10)) { keyword ->
                AssistChip(
                    onClick = { onKeywordClick(keyword) },
                    label = { Text(keyword) },
                    leadingIcon = {
                        Icon(
                            Icons.Default.Search,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                )
            }
        }
    }
}

@Composable
private fun SearchResultsList(
    results: List<DealItem>,
    onDealClick: (DealItem) -> Unit,
    query: String
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(16.dp)
    ) {
        item {
            // 검색 결과 통계
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "🔍 검색 결과 ${results.size}개",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium
                )
                
                // 정렬 옵션
                var showSortMenu by remember { mutableStateOf(false) }
                
                TextButton(
                    onClick = { showSortMenu = true }
                ) {
                    Text("정렬")
                    Icon(
                        Icons.Default.ArrowDropDown,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                }
                
                DropdownMenu(
                    expanded = showSortMenu,
                    onDismissRequest = { showSortMenu = false }
                ) {
                    DropdownMenuItem(
                        text = { Text("최신순") },
                        onClick = { 
                            // viewModel.setSortOrder(SortOrder.LATEST)
                            showSortMenu = false 
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("인기순") },
                        onClick = { 
                            // viewModel.setSortOrder(SortOrder.POPULAR)
                            showSortMenu = false 
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("할인율순") },
                        onClick = { 
                            // viewModel.setSortOrder(SortOrder.DISCOUNT)
                            showSortMenu = false 
                        }
                    )
                }
            }
        }
        
        items(results) { deal ->
            SearchResultItem(
                deal = deal,
                onClick = { onDealClick(deal) },
                query = query
            )
        }
    }
}

@Composable
private fun SearchResultItem(
    deal: DealItem,
    onClick: () -> Unit,
    query: String
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 상품 이미지
            AsyncImage(
                model = deal.imageUrl,
                contentDescription = deal.title,
                modifier = Modifier
                    .size(60.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
            )
            
            Spacer(modifier = Modifier.width(12.dp))
            
            // 상품 정보
            Column(
                modifier = Modifier.weight(1f)
            ) {
                // 사이트명 + 꿀딜 지수
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "[${deal.siteName}]",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Medium
                    )
                    
                    InlineScoreBadge(score = 85) // 실제로는 deal.qualityScore
                }
                
                Spacer(modifier = Modifier.height(4.dp))
                
                // 상품명 (키워드 하이라이팅)
                Text(
                    text = deal.title,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 2
                )
                
                Spacer(modifier = Modifier.height(4.dp))
                
                // 가격 정보
                deal.price?.let { price ->
                    Text(
                        text = "${NumberFormat.getInstance().format(price)}원",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                
                // 할인율 (있는 경우)
                deal.discountRate?.let { discount ->
                    Text(
                        text = "${discount}% 할인",
                        fontSize = 12.sp,
                        color = Color.Red,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    }
}

@Composable
private fun SearchEmptyState(query: String) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            Icons.Default.SearchOff,
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        Text(
            text = "검색 결과가 없습니다",
            fontSize = 18.sp,
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.Center
        )
        
        Text(
            text = "\"$query\"에 대한 핫딜을 찾을 수 없어요",
            fontSize = 14.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 8.dp)
        )
        
        Spacer(modifier = Modifier.height(24.dp))
        
        Text(
            text = "💡 검색 팁:\n• 더 간단한 키워드로 검색해보세요\n• 브랜드명이나 카테고리로 검색해보세요\n• 필터를 조정해보세요",
            fontSize = 14.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            lineHeight = 20.sp
        )
    }
}
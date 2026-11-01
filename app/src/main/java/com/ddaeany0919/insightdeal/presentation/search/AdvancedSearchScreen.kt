package com.ddaeany0919.insightdeal.presentation.search

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.TrendingUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.paging.compose.itemKey
import com.ddaeany0919.insightdeal.models.DealItem
import com.ddaeany0919.insightdeal.network.PopularKeywordRankingItem
import com.ddaeany0919.insightdeal.presentation.PriceChart
import com.ddaeany0919.insightdeal.presentation.formatPrice
import com.ddaeany0919.insightdeal.presentation.home.DealCardComposable
import com.ddaeany0919.insightdeal.presentation.home.HomeViewModel
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class, FlowPreview::class)
@Composable
fun AdvancedSearchScreen(
    navController: NavController,
    initialQuery: String = "",
    viewModel: HomeViewModel = viewModel()
) {
    var searchQuery by androidx.compose.runtime.saveable.rememberSaveable(stateSaver = androidx.compose.ui.text.input.TextFieldValue.Saver) { 
        mutableStateOf(androidx.compose.ui.text.input.TextFieldValue(initialQuery)) 
    }
    var isSearching by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(initialQuery.isNotBlank()) }
    val focusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current
    val context = LocalContext.current
    val searchAutoComplete = remember(context) { SearchAutoComplete.getInstance(context) }
    val searchHistory by searchAutoComplete.searchHistory.collectAsState()
    val isDark = MaterialTheme.colorScheme.background.red < 0.2f
    
    // 재사용할 HomeViewModel 상태
    val dealsPagingItems = viewModel.dealsPagingData.collectAsLazyPagingItems()
    val popularKeywords by viewModel.popularKeywords.collectAsState()
    val popularRankings by viewModel.popularRankings.collectAsState()
    val popularUpdatedAt by viewModel.popularUpdatedAt.collectAsState()

    fun isMeaningfulQuery(raw: String): Boolean {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return false
        val tokens = trimmed.split(Regex("\\s+")).filter { it.isNotBlank() && !it.startsWith("-") && !it.startsWith(":") }
        if (tokens.isEmpty()) {
            return trimmed.startsWith(":") && trimmed.length >= 4
        }
        return tokens.all { tok ->
            if (tok.length >= 2) true
            else {
                val ch = tok[0]
                // 완성형 한글 1글자(예: 쌀, 꿀, 배, 김)만 허용하고 영문/숫자/자모 1글자('f', 'ㄱ' 등)는 차단
                ch in '가'..'힣'
            }
        }
    }

    val isQueryTooShort = remember(searchQuery.text) {
        val trimmed = searchQuery.text.trim()
        trimmed.isNotEmpty() && !isMeaningfulQuery(trimmed)
    }

    fun performSearch(query: String) {
        val trimmed = query.trim()
        if (isMeaningfulQuery(trimmed)) {
            searchQuery = androidx.compose.ui.text.input.TextFieldValue(trimmed)
            isSearching = true
            keyboardController?.hide()
            searchAutoComplete.addToSearchHistory(trimmed)
            viewModel.searchDeals(trimmed)
        } else if (trimmed.isEmpty()) {
            isSearching = false
            viewModel.searchDeals("")
        }
    }

    LaunchedEffect(initialQuery) {
        if (initialQuery.isNotBlank() && isMeaningfulQuery(initialQuery)) {
            val trimmed = initialQuery.trim()
            searchQuery = androidx.compose.ui.text.input.TextFieldValue(trimmed)
            isSearching = true
            searchAutoComplete.addToSearchHistory(trimmed)
            viewModel.searchDeals(trimmed)
        } else if (searchQuery.text.isEmpty()) {
            viewModel.searchDeals("")
        }
    }

    // ✨ 실시간 검색 (Debounce 적용) - snapshotFlow와 TextFieldValue 사용으로 한글 조합 중 끊김 현상 방지
    LaunchedEffect(Unit) {
        androidx.compose.runtime.snapshotFlow { searchQuery.text }
            .debounce(300L) // 300ms로 단축하여 더 빠르고 부드러운 반응
            .collectLatest { query ->
                val trimmed = query.trim()
                if (isMeaningfulQuery(trimmed)) {
                    isSearching = true
                    viewModel.searchDeals(trimmed)
                } else {
                    isSearching = false
                    viewModel.searchDeals("")
                }
            }
    }

    Scaffold(
        modifier = Modifier.imePadding(),
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
                            if (searchQuery.text.isNotEmpty()) {
                                IconButton(onClick = { 
                                    searchQuery = androidx.compose.ui.text.input.TextFieldValue("") 
                                    isSearching = false
                                }) {
                                    Icon(Icons.Default.Clear, contentDescription = "지우기")
                                }
                            } else {
                                IconButton(onClick = { performSearch(searchQuery.text) }) {
                                    Icon(Icons.Default.Search, contentDescription = "검색")
                                }
                            }
                        },
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                            imeAction = androidx.compose.ui.text.input.ImeAction.Search
                        ),
                        keyboardActions = androidx.compose.foundation.text.KeyboardActions(
                            onSearch = { performSearch(searchQuery.text) }
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
            if (isQueryTooShort) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "검색어를 2글자 이상 입력해 주세요",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "정확한 상품명이나 모델명을 입력하면 핫딜과 시세를 찾아드립니다.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else if (!isSearching && searchQuery.text.isEmpty()) {
                val rankingItems = remember(popularRankings, popularKeywords) {
                    if (popularRankings.isNotEmpty()) {
                        popularRankings.take(10)
                    } else {
                        popularKeywords.take(10).mapIndexed { idx, kw ->
                            PopularKeywordRankingItem(
                                rank = idx + 1,
                                keyword = kw,
                                dealCount = 0,
                                recentCount = 0,
                                status = if (idx == 0) "HOT" else if (idx < 3) "UP" else "SAME"
                            )
                        }
                    }
                }

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(bottom = 32.dp)
                ) {
                    Spacer(modifier = Modifier.height(12.dp))
                    // 검색 문법 가이드 슬림 바
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surface,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.65f)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = if (isDark) Color(0x33FF5A26) else Color(0xFFFFF1EC)
                            ) {
                                Text(
                                    text = "TIP",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = Color(0xFFFF5A26),
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                            Text(
                                text = "'-제외어'로 케이스 제외 · ':가격'으로 상한가 검색 (예: 맥북 -파우치 :1200000)",
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    // 최근 검색어 섹션 (개별 삭제 X 및 전체 삭제 지원)
                    if (searchHistory.isNotEmpty()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 18.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.History,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(16.dp)
                                )
                                Text(
                                    text = "최근 검색어",
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = 15.sp,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                            Text(
                                text = "전체 삭제",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f),
                                modifier = Modifier
                                    .clickable { searchAutoComplete.clearSearchHistory() }
                                    .padding(vertical = 2.dp, horizontal = 4.dp)
                            )
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        FlowRow(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            searchHistory.take(10).forEach { historyKw ->
                                Surface(
                                    shape = RoundedCornerShape(999.dp),
                                    color = MaterialTheme.colorScheme.surface,
                                    border = BorderStroke(
                                        1.dp,
                                        MaterialTheme.colorScheme.outline.copy(alpha = 0.65f)
                                    )
                                ) {
                                    Row(
                                        modifier = Modifier.padding(start = 12.dp, end = 8.dp, top = 6.dp, bottom = 6.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Text(
                                            text = historyKw,
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = MaterialTheme.colorScheme.onSurface,
                                            modifier = Modifier.clickable { performSearch(historyKw) }
                                        )
                                        Box(
                                            modifier = Modifier
                                                .size(18.dp)
                                                .background(
                                                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f),
                                                    shape = CircleShape
                                                )
                                                .clickable { searchAutoComplete.removeFromSearchHistory(historyKw) },
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Close,
                                                contentDescription = "$historyKw 삭제",
                                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                                modifier = Modifier.size(11.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(22.dp))
                    }

                    // 실시간 급상승 검색어 Top 10 보드 헤더
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 18.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.TrendingUp,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                text = "실시간 인기 검색어",
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 16.sp,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                        if (popularUpdatedAt.isNotBlank()) {
                            Text(
                                text = popularUpdatedAt,
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // 토스/네이버 스타일 2열(1~5위 / 6~10위) 실시간 랭킹 보드 카드
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.65f))
                    ) {
                        val leftColumn = rankingItems.take(5)
                        val rightColumn = rankingItems.drop(5).take(5)

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.Top
                        ) {
                            // 1~5위 컬럼
                            Column(modifier = Modifier.weight(1f)) {
                                leftColumn.forEachIndexed { i, item ->
                                    PopularRankingRowItem(
                                        item = item,
                                        isDark = isDark,
                                        onClick = { performSearch(item.keyword) }
                                    )
                                    if (i < leftColumn.lastIndex) {
                                        HorizontalDivider(
                                            modifier = Modifier.padding(horizontal = 14.dp),
                                            thickness = 0.6.dp,
                                            color = MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)
                                        )
                                    }
                                }
                            }

                            // 중앙 세로 구분선
                            if (rightColumn.isNotEmpty()) {
                                Box(
                                    modifier = Modifier
                                        .width(0.8.dp)
                                        .height(220.dp)
                                        .padding(vertical = 8.dp)
                                        .background(MaterialTheme.colorScheme.outline.copy(alpha = 0.45f))
                                )

                                // 6~10위 컬럼
                                Column(modifier = Modifier.weight(1f)) {
                                    rightColumn.forEachIndexed { i, item ->
                                        PopularRankingRowItem(
                                            item = item,
                                            isDark = isDark,
                                            onClick = { performSearch(item.keyword) }
                                        )
                                        if (i < rightColumn.lastIndex) {
                                            HorizontalDivider(
                                                modifier = Modifier.padding(horizontal = 14.dp),
                                                thickness = 0.6.dp,
                                                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    // 추천 검색 키워드 태그 칩
                    Text(
                        text = "빠른 키워드 탐색",
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 18.dp)
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    FlowRow(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        popularKeywords.forEachIndexed { idx, keyword ->
                            Surface(
                                shape = RoundedCornerShape(999.dp),
                                color = MaterialTheme.colorScheme.surface,
                                border = BorderStroke(
                                    1.dp,
                                    if (idx < 3) MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)
                                    else MaterialTheme.colorScheme.outline.copy(alpha = 0.65f)
                                ),
                                modifier = Modifier.clickable { performSearch(keyword) }
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                                ) {
                                    Text(
                                        text = "#",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = if (idx < 3) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                                    )
                                    Text(
                                        text = keyword,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }
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
                        Text("'${searchQuery.text}'에 해당하는 핫딜이 없습니다.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                } else {
                    val snapshotDeals = dealsPagingItems.itemSnapshotList.items
                    // [jetpack-compose-performance] LazyColumn key 및 contentType 명시로 리컴포지션 방어 및 슬롯 재사용
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 80.dp)
                    ) {
                        if (searchQuery.text.isNotBlank() && snapshotDeals.isNotEmpty()) {
                            item(key = "keyword_price_summary", contentType = "price_summary_header") {
                                KeywordPriceTrendSummaryCard(
                                    keyword = searchQuery.text.trim(),
                                    deals = snapshotDeals,
                                    onRegisterKeywordAlarm = { navController.navigate("keyword_alarm") }
                                )
                            }
                        }

                        items(
                            count = dealsPagingItems.itemCount,
                            key = dealsPagingItems.itemKey { it.id },
                            contentType = { "search_deal_item" }
                        ) { index ->
                            val deal = dealsPagingItems[index]
                            if (deal != null) {
                                DealCardComposable(
                                    deal = deal,
                                    onDetailClick = {
                                        if (isMeaningfulQuery(searchQuery.text)) {
                                            searchAutoComplete.addToSearchHistory(searchQuery.text.trim())
                                        }
                                        navController.navigate("deal_detail/${deal.id}")
                                    },
                                    onOpenUrl = { url ->
                                        if (isMeaningfulQuery(searchQuery.text)) {
                                            searchAutoComplete.addToSearchHistory(searchQuery.text.trim())
                                        }
                                        val encodedUrl = java.net.URLEncoder.encode(url, "UTF-8")
                                        navController.navigate("webview/$encodedUrl")
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PopularRankingRowItem(
    item: PopularKeywordRankingItem,
    isDark: Boolean,
    onClick: () -> Unit
) {
    val isTop3 = item.rank <= 3
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.weight(1f)
        ) {
            Text(
                text = "${item.rank}",
                fontSize = 13.5.sp,
                fontWeight = FontWeight.ExtraBold,
                color = if (isTop3) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.width(22.dp)
            )
            Text(
                text = item.keyword,
                fontSize = 13.5.sp,
                fontWeight = if (isTop3) FontWeight.Bold else FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(modifier = Modifier.width(6.dp))

        when (item.status) {
            "HOT" -> {
                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = if (isDark) Color(0x33FF5A26) else Color(0xFFFFF1EC)
                ) {
                    Text(
                        text = "HOT",
                        fontSize = 9.5.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = Color(0xFFFF5A26),
                        modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.5.dp)
                    )
                }
            }
            "UP" -> {
                Text(
                    text = "▲",
                    fontSize = 9.5.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFFFF5A26)
                )
            }
            "NEW" -> {
                Text(
                    text = "NEW",
                    fontSize = 9.5.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            else -> {
                Text(
                    text = "-",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                )
            }
        }
    }
}

/**
 * 핫구사(Hotgusa) `/price-trend/{KEYWORD}` 스타일 키워드 실시간 시세 브리핑 & 추이 그래프 카드
 * - 검색된 딜 목록에서 중앙값(Median) 기준 이상치(액세서리/완본체)를 자동 필터링하여
 *   역대 최저가 / 평균 핫딜가 / 최고가 요약과 시계열 그래프를 상단에 즉시 렌더링합니다.
 */
@Composable
fun KeywordPriceTrendSummaryCard(
    keyword: String,
    deals: List<DealItem>,
    onRegisterKeywordAlarm: () -> Unit,
    modifier: Modifier = Modifier
) {
    val cleanKeyword = remember(keyword) {
        keyword.split(Regex("\\s+"))
            .filter { it.isNotBlank() && !it.startsWith("-") && !it.startsWith(":") }
            .joinToString(" ")
            .trim()
    }

    // 단일 알파벳/자모이거나 2글자 미만인 경우, 또는 쇼핑몰 이름만 검색한 경우 시세 차트 비노출
    val mallOnlyKeywords = remember {
        setOf("지마켓", "g마켓", "옥션", "쿠팡", "11번가", "알리", "알리익스프레스", "네이버", "롯데온", "쓱", "ssg", "티몬", "위메프", "무신사")
    }
    if (cleanKeyword.length < 2 || cleanKeyword.lowercase() in mallOnlyKeywords) return

    val filteredDeals = remember(deals, cleanKeyword) {
        val positiveTokens = cleanKeyword.lowercase().split(Regex("\\s+")).filter { it.length >= 2 }
        if (positiveTokens.isEmpty()) return@remember emptyList()

        // 실제로 제목(title)에 검색 키워드 토큰이 모두 포함된 단일 상품 딜만 시세 집계 대상으로 인정 (이벤트/적립/모음전 제외)
        val titleMatched = deals.filter { d ->
            val tLow = d.title.lowercase()
            val isExcludedCategory = d.category in listOf("이벤트", "적립")
            val isBundleOrGuide = tLow.contains("골라담기") || tLow.contains("모음") || tLow.contains("외 ") || tLow.contains("선착순 쿠폰") || tLow.contains("할인코드")
            !isExcludedCategory && !isBundleOrGuide && d.price > 1000L && d.currency.equals("KRW", ignoreCase = true) && positiveTokens.all { tok -> tLow.contains(tok) }
        }
        if (titleMatched.size < 3) return@remember emptyList()

        val sortedPrices = titleMatched.map { it.price }.sorted()
        val median = sortedPrices[sortedPrices.size / 2]
        // 중앙값 대비 55% 이상 ~ 165% 이하 범위의 동일 체급 상품만 필터링
        val clean = titleMatched.filter { it.price >= (median * 0.55).toLong() && it.price <= (median * 1.65).toLong() }
        if (clean.size < 3) return@remember emptyList()
        val minP = clean.minOf { it.price }
        val maxP = clean.maxOf { it.price }
        // 최저가와 최고가 편차가 2.6배를 초과하면 단일 상품이 아닌 이종 상품 혼재로 판단하여 차트 숨김
        if (minP <= 0L || (maxP.toDouble() / minP.toDouble()) > 2.6) return@remember emptyList()
        clean.take(20).reversed()
    }

    if (filteredDeals.size < 3) return

    val minPrice = remember(filteredDeals) { filteredDeals.minOf { it.price } }
    val maxPrice = remember(filteredDeals) { filteredDeals.maxOf { it.price } }
    val avgPrice = remember(filteredDeals) { filteredDeals.map { it.price }.average().toLong() }
    val chartPrices = remember(filteredDeals) { filteredDeals.map { it.price.toFloat() } }
    val chartDates = remember(filteredDeals) {
        filteredDeals.map { d ->
            val raw = d.createdAt ?: ""
            if (raw.length >= 10) raw.substring(5, 10).replace("-", ".") else "최근"
        }
    }

    var isChartExpanded by remember { mutableStateOf(true) }

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(7.dp)
                                .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(4.dp))
                        )
                        Text(
                            text = "'$keyword' 가격 추이",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "최근 등록된 ${filteredDeals.size}건 기준",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Surface(
                    onClick = onRegisterKeywordAlarm,
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.primary
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.NotificationsActive,
                            contentDescription = "키워드 알림 등록",
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "키워드 알림",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // 3대 핵심 가격 지표 바
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.8f),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 10.dp, horizontal = 8.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.weight(1f)) {
                        Text("최저 핫딜가", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.height(3.dp))
                        Text(
                            text = formatPrice(minPrice, "KRW"),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.ExtraBold,
                            color = Color(0xFF10B981)
                        )
                    }
                    Box(
                        modifier = Modifier
                            .width(1.dp)
                            .height(26.dp)
                            .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
                    )
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.weight(1f)) {
                        Text("평균 핫딜가", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.height(3.dp))
                        Text(
                            text = formatPrice(avgPrice, "KRW"),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    Box(
                        modifier = Modifier
                            .width(1.dp)
                            .height(26.dp)
                            .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
                    )
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.weight(1f)) {
                        Text("최고 핫딜가", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.height(3.dp))
                        Text(
                            text = formatPrice(maxPrice, "KRW"),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFFF59E0B)
                        )
                    }
                }
            }

            if (chartPrices.size >= 2) {
                Spacer(modifier = Modifier.height(10.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { isChartExpanded = !isChartExpanded },
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (isChartExpanded) "시세 변동 그래프 접기 ▲" else "시세 변동 그래프 펼치기 ▼",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "터치하여 가격 비교",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )
                }

                if (isChartExpanded) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(175.dp)
                    ) {
                        PriceChart(
                            prices = chartPrices,
                            dates = chartDates,
                            modifier = Modifier.fillMaxSize(),
                            currency = "KRW"
                        )
                    }
                }
            }
        }
    }
}

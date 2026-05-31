package com.ddaeany0919.insightdeal.presentation.community

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.BrokenImage
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import coil.request.ImageRequest
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import com.ddaeany0919.insightdeal.data.HotDealDto
import coil.compose.AsyncImage
import com.ddaeany0919.insightdeal.presentation.rememberRelativeTime



import androidx.navigation.NavController

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CommunityScreen(
    navController: NavController? = null,
    viewModel: CommunityViewModel = androidx.lifecycle.viewmodel.compose.viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    var selectedTabIndex by remember { mutableIntStateOf(0) }
    val tabs = listOf("전체보기", "🔥 핫딜만", "💰 앱테크만")

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("커뮤니티 핫딜", fontWeight = FontWeight.Bold) },
                actions = {
                    IconButton(onClick = { viewModel.loadHotDeals() }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onBackground
                )
            )
        }
    ) { paddingValues ->
        Column(modifier = Modifier.fillMaxSize().padding(paddingValues)) {
            // 커뮤니티 탭
            ScrollableTabRow(
                selectedTabIndex = selectedTabIndex,
                edgePadding = 16.dp,
                containerColor = MaterialTheme.colorScheme.background,
                contentColor = MaterialTheme.colorScheme.primary,
                indicator = { tabPositions ->
                    TabRowDefaults.SecondaryIndicator(
                        Modifier.tabIndicatorOffset(tabPositions[selectedTabIndex]),
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            ) {
                tabs.forEachIndexed { index, title ->
                    Tab(
                        selected = selectedTabIndex == index,
                        onClick = { selectedTabIndex = index },
                        text = { Text(text = title) }
                    )
                }
            }

            Box(modifier = Modifier.fillMaxSize()) {
                android.util.Log.d("CommunityScreen", "Current UI State: $uiState")
                when (val state = uiState) {
                    is CommunityUiState.Loading -> {
                        CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                    }
                    is CommunityUiState.Error -> {
                        Column(
                            modifier = Modifier.align(Alignment.Center),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(text = state.message, color = MaterialTheme.colorScheme.error)
                            Spacer(modifier = Modifier.height(8.dp))
                            Button(onClick = { viewModel.loadHotDeals() }) {
                                Text("다시 시도")
                            }
                        }
                    }
                    is CommunityUiState.Success -> {
                        // 필터링 로직 - 성능 최적화를 위해 remember 사용
                        val filteredDeals = remember(selectedTabIndex, state.deals) {
                            when (selectedTabIndex) {
                                0 -> state.deals // 전체보기
                                1 -> state.deals.filter { it.honeyScore >= 80 } // 핫딜만 (꿀점수 80 이상)
                                2 -> state.deals.filter { it.category == "적립" || it.category == "이벤트" } // 앱테크만
                                else -> state.deals
                            }
                        }

                        if (filteredDeals.isEmpty()) {
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Text(
                                    text = "등록된 핫딜이 없습니다.",
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        } else {
                            LazyColumn(
                                contentPadding = PaddingValues(16.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                items(
                                    items = filteredDeals,
                                    key = { it.id }
                                ) { deal ->
                                    HotDealCard(
                                        deal = deal,
                                        onClick = {
                                            navController?.navigate("deal_detail/${deal.id}")
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
}

@Composable
fun HotDealCard(deal: HotDealDto, onClick: () -> Unit = {}) {
    Card(
        onClick = onClick,
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .padding(12.dp)
                .height(IntrinsicSize.Min)
        ) {
            // 1. 이미지 (왼쪽)
            Box(
                modifier = Modifier
                    .size(100.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
            ) {
                if (!deal.imageUrl.isNullOrEmpty()) {
                    AsyncImage(
                        model = ImageRequest.Builder(LocalContext.current)
                            .data(deal.imageUrl)
                            .addHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36")
                            .addHeader("Referer", "https://www.fmkorea.com/")
                            .crossfade(true)
                            .build(),
                        contentDescription = null,
                        contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                        error = rememberVectorPainter(Icons.Default.BrokenImage),
                        placeholder = rememberVectorPainter(Icons.Default.Image)
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.Image,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.align(Alignment.Center)
                    )
                }

                // 종료된 딜 오버레이
                if (deal.isClosed) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.6f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "종료됨",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.labelLarge
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            // 2. 내용 (오른쪽)
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                // 상단: 카테고리 + 쇼핑몰
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Badge(text = deal.category ?: "기타", color = MaterialTheme.colorScheme.surfaceVariant, contentColor = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = deal.mallName ?: "정보 없음",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.weight(1f))
                    val distinctCommunity = deal.communityName?.split(",")?.map { it.trim() }?.filter { it.isNotEmpty() }?.distinct()?.joinToString(", ") ?: "기타"
                    Badge(text = distinctCommunity, color = MaterialTheme.colorScheme.surfaceVariant, contentColor = MaterialTheme.colorScheme.onSurfaceVariant)
                }

                Spacer(modifier = Modifier.height(4.dp))

                // 제목
                Row(verticalAlignment = Alignment.Top) {
                    if (deal.honeyScore >= 80) {
                        Text(
                            text = "🔥",
                            modifier = Modifier.padding(end = 4.dp),
                            style = MaterialTheme.typography.titleMedium
                        )
                    }
                    Text(
                        text = deal.title,
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            textDecoration = if (deal.isClosed) androidx.compose.ui.text.style.TextDecoration.LineThrough else null,
                            color = if (deal.isClosed) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f) else MaterialTheme.colorScheme.onSurface
                        ),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                // 가격 및 배송/시간 정보
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        val priceText = if (deal.price == 0) {
                            if (deal.category == "이벤트" || deal.title.contains("무료") || deal.title.contains("쿠폰")) "무료 (쿠폰/이벤트)"
                            else "금액 확인 필요"
                        } else {
                            String.format(java.util.Locale.getDefault(), "%,d원", deal.price)
                        }
                        
                        Text(
                            text = priceText,
                            style = MaterialTheme.typography.titleLarge.copy(
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.error
                            )
                        )
                    }
                    
                    Spacer(modifier = Modifier.height(4.dp))

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        val digitalCategories = listOf("적립", "이벤트", "모바일/기프티콘", "상품권", "패키지/이용권")
                        val isDigitalTitle = deal.title.contains("요금제") || deal.title.contains("데이터")
                        val hideShipping = deal.category in digitalCategories || isDigitalTitle
                        
                        if (!hideShipping) {
                            val trimmed = deal.shippingFee?.trim() ?: "정보 없음"
                            val displayShipping = when {
                                trimmed == "정보 없음" || trimmed.isEmpty() -> "확인 필요"
                                trimmed == "0" || trimmed == "0원" || trimmed == "무배" || trimmed == "무료" || trimmed == "무료배송" -> "무료"
                                trimmed.matches(Regex("^0(원)?\\s*(/|\\+).*")) -> trimmed.replace(Regex("^0(원)?\\s*"), "무료 ")
                                trimmed == "유료" || trimmed == "유료배송" -> "유료"
                                else -> trimmed.replace("무료배송", "무료").replace("유료배송", "유료")
                            }
                            Surface(shape = RoundedCornerShape(4.dp), color = MaterialTheme.colorScheme.surfaceVariant) {
                                Text(text = " 배송비: $displayShipping ", fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier=Modifier.padding(vertical=2.dp))
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                        }
                        Text(
                            text = rememberRelativeTime(deal.createdAt ?: deal.timeAgo),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    
                    if (deal.likeCount > 0 || deal.dislikeCount > 0 || deal.commentCount > 0) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            if (deal.likeCount > 0) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text("🔥 질러 ", style = MaterialTheme.typography.labelSmall, color = Color(0xFFEF4444), fontWeight = FontWeight.Bold)
                                    Text("${deal.likeCount}", style = MaterialTheme.typography.labelSmall, color = Color(0xFF64748B))
                                }
                            }
                            if (deal.dislikeCount > 0) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text("💸 지갑 지켜 ", style = MaterialTheme.typography.labelSmall, color = Color(0xFF3B82F6), fontWeight = FontWeight.Bold)
                                    Text("${deal.dislikeCount}", style = MaterialTheme.typography.labelSmall, color = Color(0xFF64748B))
                                }
                            }
                            if (deal.commentCount > 0) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text("💬 참견 ", style = MaterialTheme.typography.labelSmall, color = Color.DarkGray, fontWeight = FontWeight.Bold)
                                    Text("${deal.commentCount}", style = MaterialTheme.typography.labelSmall, color = Color(0xFF64748B))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun Badge(text: String, color: Color, contentColor: Color) {
    Surface(
        color = color,
        shape = RoundedCornerShape(4.dp),
        modifier = Modifier.padding(end = 4.dp)
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
            style = MaterialTheme.typography.labelSmall,
            color = contentColor
        )
    }
}

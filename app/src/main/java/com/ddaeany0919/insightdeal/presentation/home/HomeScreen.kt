package com.ddaeany0919.insightdeal.presentation.home

import com.ddaeany0919.insightdeal.presentation.formatPrice
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.*
import androidx.compose.ui.text.font.*
import androidx.compose.ui.text.style.*
import androidx.compose.ui.unit.*
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import androidx.paging.LoadState
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.paging.compose.itemKey
import coil.compose.AsyncImage
import com.ddaeany0919.insightdeal.models.DealItem
 
import com.ddaeany0919.insightdeal.presentation.components.shimmerEffect
import com.ddaeany0919.insightdeal.presentation.rememberRelativeTime
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.input.nestedscroll.nestedScroll
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import com.ddaeany0919.insightdeal.network.ApiService

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(navController: NavController, viewModel: HomeViewModel = viewModel()) {
    val filterState by viewModel.filterState.collectAsState()
    val selectedCategory = filterState.category
    
    val context = LocalContext.current
    val lifecycleOwner = androidx.compose.ui.platform.LocalLifecycleOwner.current

    DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                val prefs = context.getSharedPreferences("app", android.content.Context.MODE_PRIVATE)
                val disabledPlatforms = prefs.getStringSet("disabled_platforms", emptySet()) ?: emptySet()
                val allPlatforms = listOf("뽐뿌", "퀘이사존", "펨코", "루리웹", "클리앙", "알리뽐뿌", "빠삭국내", "빠삭해외")
                val activePlatforms = allPlatforms.filter { it !in disabledPlatforms }.joinToString(",")
                viewModel.selectPlatform(activePlatforms)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }
    
    // ✨ Paging3 Lazy 아이템 콜렉팅 및 상태 구독
    val dealsPagingItems = viewModel.dealsPagingData.collectAsLazyPagingItems()
    
    // [Epic 3] 키워드 설정 다이얼로그 상태
    var showKeywordDialog by remember { mutableStateOf(false) }
    if (showKeywordDialog) {
        KeywordSettingsBottomSheet(onDismiss = { showKeywordDialog = false })
    }

    // [Epic 4] 쿠팡 파트너스 / 쿠팡 계정 로그인 (UI 제공)
    var showCoupangDialog by remember { mutableStateOf(false) }
    if (showCoupangDialog) {
        CoupangSettingsDialog(onDismiss = { showCoupangDialog = false })
    }

    // ✨ Pull-to-refresh 상태 관리
    val pullRefreshState = androidx.compose.material3.pulltorefresh.rememberPullToRefreshState()
    if (pullRefreshState.isRefreshing) {
        LaunchedEffect(true) {
            dealsPagingItems.refresh()
            viewModel.fetchTopHotDeals()
        }
    }
    LaunchedEffect(dealsPagingItems.loadState.refresh) {
        if (dealsPagingItems.loadState.refresh !is LoadState.Loading) {
            pullRefreshState.endRefresh()
        }
    }

    Box(modifier = Modifier.fillMaxSize().nestedScroll(pullRefreshState.nestedScrollConnection)) {
        Column(modifier = Modifier.fillMaxSize()) {
            TopAppBar(
            title = {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable { viewModel.selectCategory("전체") }) {
                    Text(
                        text = "🔥 InsightDeal",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Surface(
                        modifier = Modifier.size(34.dp, 18.dp),
                        shape = RoundedCornerShape(4.dp),
                        color = Color(0xFFFF3B30)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                text = "LIVE", 
                                fontSize = 10.sp, 
                                color = Color.White, 
                                fontWeight = FontWeight.Bold,
                                style = TextStyle(
                                    platformStyle = PlatformTextStyle(includeFontPadding = false),
                                    lineHeightStyle = LineHeightStyle(
                                        alignment = LineHeightStyle.Alignment.Center,
                                        trim = LineHeightStyle.Trim.Both
                                    )
                                )
                            )
                        }
                    }
                }
            },
            actions = {
                IconButton(onClick = { navController.navigate("advanced_search") }) { Icon(Icons.Default.Search, "검색") }
                IconButton(onClick = { showCoupangDialog = true }) { Icon(Icons.Default.ShoppingCart, "쿠팡 연동") }
                IconButton(onClick = { showKeywordDialog = true }) { Icon(Icons.Default.NotificationsActive, "키워드 알림") }
            }
        )



        LazyColumn(
            modifier = Modifier.fillMaxWidth().weight(1f),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // ✨ 상태 관리를 통한 스켈레톤 로딩 노출
            when (dealsPagingItems.loadState.refresh) {
                is LoadState.Loading -> {
                    items(3) { HomeDealCardSkeleton() }
                }
                is LoadState.Error -> {
                    item {
                        Box(Modifier.fillParentMaxSize(), contentAlignment = Alignment.Center) {
                            Text("데이터를 불러오는데 실패했습니다.", color = MaterialTheme.colorScheme.error)
                        }
                    }
                }
                else -> {
                    if (dealsPagingItems.itemCount == 0) {
                        item {
                            Box(Modifier.fillParentMaxSize(), contentAlignment = Alignment.Center) {
                                Text("해당 카테고리에 핫딜 데이터가 없습니다.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    } else {
                        // [Phase 11 Epic 2] 안드로이드 상단 '투데이 픽' 캐러셀 UI 도입
                        if (selectedCategory == "전체") {
                            item {
                                val topHotDeals by viewModel.topHotDeals.collectAsState()
                                val topPicks = topHotDeals
                                    .filter { deal ->
                                        // 백엔드에서 honeyScore가 100 이상이면 포텐/인기글(is_super_hotdeal)로 판단합니다.
                                        val isSuperHot = deal.honeyScore >= 100
                                        !deal.isClosed && isSuperHot
                                    }
                                    .sortedByDescending { it.createdAt ?: "" }
                                
                                android.util.Log.d("HomeScreen", "topHotDeals size: ${topHotDeals.size}, topPicks size: ${topPicks.size}")

                                if (topPicks.isNotEmpty()) {
                                Column(modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)) {
                                    Text("오늘의 미친 핫딜", fontWeight = FontWeight.ExtraBold, fontSize = 18.sp, modifier = Modifier.padding(bottom = 12.dp, start = 4.dp))
                                    LazyRow(
                                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                                        contentPadding = PaddingValues(horizontal = 4.dp)
                                    ) {
                                        items(topPicks.size) { index ->
                                            val pick = topPicks[index]
                                            val localCtx = LocalContext.current
                                            Card(
                                                modifier = Modifier
                                                    .width(200.dp)
                                                    .height(210.dp)
                                                    .clickable {
                                                        val targetUrl = pick.ecommerceUrl?.takeIf { it.isNotBlank() } ?: pick.postUrl ?: "https://insightdeal.com"
                                                        var finalUrl = if (targetUrl.startsWith("http")) targetUrl else "https://$targetUrl"
                                                        if (finalUrl.contains("bbasak.com") && !finalUrl.contains("device=pc")) {
                                                            finalUrl += if (finalUrl.contains("?")) "&device=pc" else "?device=pc"
                                                        }
                                                        val encodedUrl = java.net.URLEncoder.encode(finalUrl, "UTF-8")
                                                        navController.navigate("webview/$encodedUrl")
                                                    },
                                                shape = RoundedCornerShape(16.dp),
                                                elevation = CardDefaults.cardElevation(2.dp),
                                                colors = CardDefaults.cardColors(containerColor = Color.White)
                                            ) {
                                                Column {
                                                    val fallbackPickImg = "https://placehold.co/400x300/E2E8F0/A0AEC0?text=Deal"
                                                    val rawPickUrl = pick.imageUrl.takeIf { !it.isNullOrBlank() } ?: fallbackPickImg
                                                    val proxiedPickUrl = if (rawPickUrl.contains("bbasak.com") || rawPickUrl.contains("ppomppu.co.kr") || rawPickUrl.contains("fmkorea.com")) {
                                                        "http://192.168.0.36:8000/api/proxy-image?url=${java.net.URLEncoder.encode(rawPickUrl, "UTF-8")}"
                                                    } else rawPickUrl

                                                    val imageRequest = remember(proxiedPickUrl, pick.postUrl) {
                                                        coil.request.ImageRequest.Builder(context)
                                                            .data(proxiedPickUrl)
                                                            .setHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) Chrome/120.0.0.0 Safari/537.36")
                                                            .setHeader("Referer", pick.postUrl ?: "https://insightdeal.com/")
                                                            .crossfade(true).build()
                                                    }

                                                    AsyncImage(
                                                        model = imageRequest,
                                                        contentDescription = "Carousel Image",
                                                        modifier = Modifier
                                                            .height(120.dp)
                                                            .fillMaxWidth()
                                                            .background(Color.White)
                                                            .clip(RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)),
                                                        contentScale = ContentScale.Crop
                                                    )
                                                    Column(Modifier.padding(12.dp)) {
                                                        Text(pick.title, maxLines = 2, overflow = TextOverflow.Ellipsis, fontSize = 13.sp, fontWeight = FontWeight.Bold, lineHeight = 18.sp)
                                                        Spacer(Modifier.weight(1f))
                                                        val priceText = if (pick.price > 0) {
                                                            when (pick.currency.uppercase()) {
                                                                "USD" -> "$${String.format(Locale.US, "%.2f", pick.price.toDouble() / 100)}"
                                                                "EUR" -> "€${String.format(Locale.US, "%.2f", pick.price.toDouble() / 100)}"
                                                                else -> "${formatPrice(pick.price, pick.currency)}"
                                                            }
                                                        } else "확인필요"
                                                        Text(priceText, color = Color(0xFFFF3B30), fontSize = 16.sp, fontWeight = FontWeight.ExtraBold)
                                                    }
                                                }
                                            }
                                        }
                                    }
                                    Spacer(Modifier.height(24.dp))
                                    androidx.compose.material3.HorizontalDivider(thickness = 1.dp, color = Color.LightGray, modifier = Modifier.padding(horizontal = 4.dp))
                                    Spacer(Modifier.height(16.dp))
                                    Text("실시간 핫딜", fontWeight = FontWeight.ExtraBold, fontSize = 18.sp, modifier = Modifier.padding(bottom = 4.dp, start = 4.dp), color = MaterialTheme.colorScheme.onSurface)
                                }
                            }
                        }
                    }

                    // ✨ itemKey 제공으로 Recomposition 최소화 보장
                    items(
                            count = dealsPagingItems.itemCount
                        ) { index ->
                            val deal = dealsPagingItems[index]
                            if (deal != null) {
                                DealCardComposable(
                                    deal = deal,
                                    onDetailClick = { navController.navigate("deal_detail/${deal.id}") },
                                    onOpenUrl = { url ->
                                        val encodedUrl = java.net.URLEncoder.encode(url, "UTF-8")
                                        navController.navigate("webview/$encodedUrl")
                                    }
                                )
                            }
                        }
                        
                        // 하단 추가 로딩 시의 스켈레톤 표시
                        if (dealsPagingItems.loadState.append is LoadState.Loading) {
                            item { HomeDealCardSkeleton() }
                        }
                    }
                }
            }
        }
        }

        // ✨ PullToRefresh Indicator
        androidx.compose.material3.pulltorefresh.PullToRefreshContainer(
            modifier = Modifier.align(Alignment.TopCenter),
            state = pullRefreshState,
        )
    }
}

// ✨ 세로형 딜 카드를 위한 스켈레톤 (Recomposition 방지용, 기존 UI 크기 완벽 복제)
@Composable
fun HomeDealCardSkeleton() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(2.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White)
    ) {
        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(80.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .shimmerEffect()
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Box(Modifier.width(40.dp).height(16.dp).clip(RoundedCornerShape(4.dp)).shimmerEffect())
                Spacer(Modifier.height(8.dp))
                Box(Modifier.fillMaxWidth().height(16.dp).clip(RoundedCornerShape(4.dp)).shimmerEffect())
                Spacer(Modifier.height(4.dp))
                Box(Modifier.fillMaxWidth(0.5f).height(16.dp).clip(RoundedCornerShape(4.dp)).shimmerEffect())
                Spacer(Modifier.height(8.dp))
                Box(Modifier.width(80.dp).height(20.dp).clip(RoundedCornerShape(4.dp)).shimmerEffect())
            }
        }
    }
}

// Removed getTimeAgo (use rememberRelativeTime from StringUtils.kt instead)

@Composable
fun DealCardComposable(deal: DealItem, onDetailClick: () -> Unit = {}, onOpenUrl: (String) -> Unit = {}) {
    var isExpanded by remember { mutableStateOf(false) }
    val uriHandler = LocalUriHandler.current

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { isExpanded = !isExpanded }
            .alpha(if (deal.isClosed) 0.65f else 1f),
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(2.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White)
    ) {
        val isSuperHot = deal.aiSummary?.contains("🔥") == true

        Box(modifier = Modifier.fillMaxWidth()) {
            Column {
                Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.Top) {
                // 서브 Thumbnail
                val context = LocalContext.current
                val rawUrl = deal.imageUrl
                
                if (rawUrl.isNullOrBlank()) {
                    // 이미지가 없는 경우 (또는 분리된 상품 중 이미지가 없는 경우)
                    Box(
                        modifier = Modifier
                            .size(80.dp)
                            .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(8.dp))
                            .clip(RoundedCornerShape(8.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        androidx.compose.material3.Icon(
                            imageVector = androidx.compose.material.icons.Icons.Default.ShoppingCart,
                            contentDescription = "No Image",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                            modifier = Modifier.size(32.dp)
                        )
                    }
                } else {
                    Box(modifier = Modifier.size(80.dp)) {
                        val imageRequest = remember(rawUrl, deal.postUrl) {
                            val proxiedUrl = if (rawUrl.contains("bbasak.com") || rawUrl.contains("ppomppu.co.kr") || rawUrl.contains("fmkorea.com")) {
                                "http://192.168.0.36:8000/api/proxy-image?url=${java.net.URLEncoder.encode(rawUrl, "UTF-8")}"
                            } else rawUrl

                            coil.request.ImageRequest.Builder(context)
                                .data(proxiedUrl)
                                .setHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) Chrome/120.0.0.0 Safari/537.36")
                                .setHeader("Referer", deal.postUrl ?: "https://insightdeal.com/")
                                .crossfade(true)
                                .build()
                        }

                        AsyncImage(
                            model = imageRequest,
                            contentDescription = deal.title,
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Color.White, RoundedCornerShape(8.dp))
                                .clip(RoundedCornerShape(8.dp))
                                .then(if (deal.isClosed) Modifier.blur(2.dp) else Modifier),
                            colorFilter = if (deal.isClosed) {
                                remember { androidx.compose.ui.graphics.ColorFilter.colorMatrix(androidx.compose.ui.graphics.ColorMatrix().apply { setToSaturation(0f) }) }
                            } else null,
                            contentScale = ContentScale.Crop,
                            error = androidx.compose.ui.graphics.vector.rememberVectorPainter(image = androidx.compose.material.icons.Icons.Default.ShoppingCart) // 산/갤러리 이미지 대신 쇼핑카트 렌더링
                        )
                        
                        // 종료된 상품 오버레이
                        if (deal.isClosed) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(Color.Black.copy(alpha = 0.5f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "종료",
                                    color = Color.White,
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = 12.sp
                                )
                            }
                        }
                    }
                }
                
                Spacer(modifier = Modifier.width(12.dp))
                
                // 정보
                Column(modifier = Modifier.weight(1f)) {
                    // 출처 뱃지 및 쿠팡 FOMO 뱃지
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        val sourcesToRender = if (!deal.sources.isNullOrEmpty()) deal.sources else listOf(com.ddaeany0919.insightdeal.models.DealSource(deal.siteName, deal.postUrl ?: ""))
                        
                        sourcesToRender.forEach { source ->
                            val siteNameLower = source.siteName.lowercase(java.util.Locale.getDefault())
                            val (containerColor, contentColor) = when {
                                siteNameLower.contains("뽐뿌") -> Color(0xFF1565C0) to Color.White
                                siteNameLower.contains("퀘이사존") -> Color(0xFFE65100) to Color.White
                                siteNameLower.contains("루리웹") -> Color(0xFF0D47A1) to Color.White
                                siteNameLower.contains("펨코") || siteNameLower.contains("에펨코리아") -> Color(0xFF0288D1) to Color.White
                                siteNameLower.contains("빠삭") -> Color(0xFFC2185B) to Color.White
                                siteNameLower.contains("클리앙") -> Color(0xFF37474F) to Color.White
                                else -> MaterialTheme.colorScheme.primaryContainer to MaterialTheme.colorScheme.onPrimaryContainer
                            }

                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = containerColor,
                                modifier = Modifier.clickable {
                                    try {
                                        val targetUrl = source.postUrl
                                        val safeUrl = targetUrl.replace(":443", "")
                                        if (safeUrl.isNotBlank()) {
                                            uriHandler.openUri(safeUrl)
                                        } else {
                                            android.widget.Toast.makeText(context, "연결할 대상 링크가 존재하지 않습니다.", android.widget.Toast.LENGTH_SHORT).show()
                                        }
                                    } catch (e: Exception) {
                                        android.widget.Toast.makeText(context, "브라우저 앱을 열 수 없습니다.", android.widget.Toast.LENGTH_SHORT).show()
                                    }
                                }
                            ) {
                                Text(
                                    text = source.siteName,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                    fontSize = 10.sp,
                                    color = contentColor
                                )
                            }
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(6.dp))
                    
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Top) {
                        Text(
                            text = deal.title,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                            textDecoration = if (deal.isClosed) androidx.compose.ui.text.style.TextDecoration.LineThrough else null
                        )
                        val timeAgo = rememberRelativeTime(deal.createdAt)
                        if (timeAgo.isNotEmpty()) {
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant,
                                modifier = Modifier.padding(start = 6.dp)
                            ) {
                                Text(
                                    text = timeAgo,
                                    fontSize = 10.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(6.dp))
                    
                    @OptIn(ExperimentalLayoutApi::class)
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        val priceText = if (deal.price > 0) {
                            when (deal.currency.uppercase()) {
                                "USD" -> "$${String.format(Locale.US, "%.2f", deal.price.toDouble() / 100)}"
                                "EUR" -> "€${String.format(Locale.US, "%.2f", deal.price.toDouble() / 100)}"
                                else -> if (deal.category == "적립") "${formatPrice(deal.price, deal.currency)} 적립" else "${formatPrice(deal.price, deal.currency)}"
                            }
                        } else if (deal.category == "적립" || deal.title.contains("적립")) {
                            "포인트 적립"
                        } else if (deal.category == "이벤트" || deal.title.contains("무료") || deal.title.contains("쿠폰") || deal.title.contains("0원") || deal.title.contains("공짜")) {
                            "무료 (쿠폰/공짜)"
                        } else {
                            "정보 확인필요"
                        }
                        
                        Text(
                            text = priceText,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = if (deal.price > 0) Color(0xFFFF3B30) else MaterialTheme.colorScheme.error,
                            modifier = Modifier.align(Alignment.CenterVertically)
                        )
                        
                        val digitalCategories = listOf("적립", "이벤트", "모바일/기프티콘", "상품권", "패키지/이용권")
                        val isDigitalTitle = deal.title.contains("요금제") || deal.title.contains("데이터")
                        val hideShipping = deal.category in digitalCategories || isDigitalTitle
                        deal.shippingFee?.let {
                            if (it != "정보 없음" && !hideShipping) {
                                val trimmed = it.trim()
                                val displayShipping = when {
                                    trimmed == "0" || trimmed == "0원" || trimmed == "무배" || trimmed == "무료" || trimmed == "무료배송" -> "무료배송"
                                    trimmed.matches(Regex("^0(원)?\\s*(/|\\+).*")) -> trimmed.replace(Regex("^0(원)?\\s*"), "무료배송 ")
                                    trimmed == "유료" || trimmed == "유료배송" -> "유료배송"
                                    else -> trimmed
                                }
                                Surface(shape = RoundedCornerShape(4.dp), color = MaterialTheme.colorScheme.surfaceVariant, modifier = Modifier.align(Alignment.CenterVertically)) {
                                    Text(text = " 택배비 : $displayShipping ", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier=Modifier.padding(vertical=2.dp))
                                }
                            }
                        }
                        
                        // 핫딜 뱃지는 카드 우측 최상단으로 이동됨
                        
                        if (deal.isClosed) {
                            Surface(shape = RoundedCornerShape(4.dp), color = MaterialTheme.colorScheme.errorContainer, modifier = Modifier.align(Alignment.CenterVertically)) {
                                Text("종료됨", fontSize = 10.sp, color = MaterialTheme.colorScheme.onErrorContainer, modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp), fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
            
            // ✨ 아코디언 토글 상세 영역 (다이렉트 구매처 아웃링크 및 큰 이미지)
            AnimatedVisibility(visible = isExpanded) {
                Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    Spacer(modifier = Modifier.height(12.dp))
                    
                    // ✨ 큰 사이즈 원본 이미지 뷰어 (OOM 방지 및 크로스페이드)
                    val context = LocalContext.current
                    Box(modifier = Modifier.fillMaxWidth().heightIn(min = 150.dp, max = 300.dp), contentAlignment = Alignment.Center) {
                        val rawImageUrl = deal.imageUrl.takeIf { !it.isNullOrBlank() } ?: "https://placehold.co/400x300/E2E8F0/A0AEC0?text=Deal"
                        val proxiedExpandedUrl = if (rawImageUrl.contains("bbasak.com") || rawImageUrl.contains("ppomppu.co.kr") || rawImageUrl.contains("fmkorea.com")) {
                            "http://192.168.0.36:8000/api/proxy-image?url=${java.net.URLEncoder.encode(rawImageUrl, "UTF-8")}"
                        } else rawImageUrl

                        AsyncImage(
                            model = coil.request.ImageRequest.Builder(context)
                                .data(proxiedExpandedUrl)
                                .setHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) Chrome/120.0.0.0 Safari/537.36")
                                .setHeader("Referer", deal.postUrl ?: "https://insightdeal.com/")
                                .crossfade(true)
                                .crossfade(400)
                                .memoryCachePolicy(coil.request.CachePolicy.ENABLED)
                                .diskCachePolicy(coil.request.CachePolicy.ENABLED)
                                .build(),
                            contentDescription = "Full Product Image",
                            modifier = Modifier
                                .fillMaxWidth() // 너비는 채우되 비율은 유지
                                .clip(RoundedCornerShape(8.dp))
                                .then(if (deal.isClosed) Modifier.blur(6.dp) else Modifier),
                            colorFilter = if (deal.isClosed) androidx.compose.ui.graphics.ColorFilter.colorMatrix(androidx.compose.ui.graphics.ColorMatrix().apply { setToSaturation(0f) }) else null,
                            contentScale = ContentScale.Inside, // ✨ 작은 썸네일을 억지로 늘리지 않음 (흐릿함 방지)
                            error = coil.compose.rememberAsyncImagePainter(android.R.drawable.ic_menu_report_image)
                        )
                        
                        if (deal.isClosed) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(Color.Black.copy(alpha = 0.5f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "판매가 종료된 핫딜입니다",
                                    color = Color.White,
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = 16.sp,
                                    modifier = Modifier
                                        .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(8.dp))
                                        .padding(horizontal = 16.dp, vertical = 8.dp)
                                )
                            }
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(12.dp))
                    
                    val percentileText = when {
                        deal.honeyScore >= 95 -> "상위 1%"
                        deal.honeyScore >= 90 -> "상위 5%"
                        deal.honeyScore >= 80 -> "상위 10%"
                        deal.honeyScore >= 70 -> "상위 20%"
                        else -> "추천"
                    }
                    val badgeText = if (percentileText == "추천") "🍯 꿀딜 추천도 (${deal.honeyScore}점)" else "🍯 카테고리 내 $percentileText 추천도 (${deal.honeyScore}점)"
                    
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = Color(0xFFFFF8E1),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = badgeText,
                            modifier = Modifier.padding(8.dp),
                            fontSize = 12.sp,
                            color = Color(0xFFD84315),
                            fontWeight = FontWeight.Bold,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                    }
                    
                    Spacer(modifier = Modifier.height(8.dp))

                    if (!deal.aiSummary.isNullOrBlank()) {
                        Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Star, contentDescription = "AI", tint = Color(0xFF4CAF50), modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text(deal.aiSummary, fontSize = 13.sp, color = Color(0xFF2E7D32), fontWeight = FontWeight.Medium, lineHeight = 18.sp)
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                    }
                    // 📈 가격 상세 분석 버튼 추가
                    Button(
                        onClick = onDetailClick,
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("📈 역대 가격 추이 & AI 분석 보기", fontWeight = FontWeight.Bold)
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    Button(
                        onClick = {
                                try {
                                    val isPointEvent = deal.category == "적립" || deal.title.contains("네이버페이") || deal.title.contains("네이버 페이")
                                    var targetUrl = if (isPointEvent) {
                                        deal.postUrl // 네이버페이나 적립 포인트는 원본 게시글을 거쳐야 적립이 됨
                                    } else {
                                        deal.ecommerceUrl?.takeIf { it.isNotBlank() }?.replace(":443", "") ?: deal.postUrl
                                    }
                                    if (!targetUrl.isNullOrBlank()) {
                                        val prefs = context.getSharedPreferences("insightdeal_prefs", android.content.Context.MODE_PRIVATE)
                                        val afCode = prefs.getString("coupang_af_code", "") ?: ""
                                        
                                        if (afCode.isNotBlank() && (targetUrl.contains("coupang.com") || targetUrl.contains("coupa.ng"))) {
                                            android.util.Log.d("Hijack", "💰 Coupang Hijacked! 100% 수익화 모드: $afCode")
                                            targetUrl = if (targetUrl.contains("?")) "$targetUrl&subId=$afCode" else "$targetUrl?subId=$afCode"
                                            android.widget.Toast.makeText(context, "[쿠팡 파트너스 수익화 On] AF코드 작동중", android.widget.Toast.LENGTH_SHORT).show()
                                        }

                                        // G마켓 등 엄격한 WAF(봇 차단) 환경에서 '잘못된 호출페이지' 에러를 방지하기 위해 
                                        // 커뮤니티 원문(postUrl)으로 우회 연결합니다. 원문에서 직접 클릭 시 100% 정상 작동합니다.
                                        var finalUrl = if (targetUrl.startsWith("http")) targetUrl else "https://$targetUrl"
                                        if (finalUrl.contains("bbasak.com") && !finalUrl.contains("device=pc")) {
                                            finalUrl += if (finalUrl.contains("?")) "&device=pc" else "?device=pc"
                                        }
                                        onOpenUrl(finalUrl)
                                    } else {
                                        android.widget.Toast.makeText(context, "원본 링크가 제공되지 않았습니다.", android.widget.Toast.LENGTH_SHORT).show()
                                    }
                                } catch (e: Exception) {
                                    android.widget.Toast.makeText(context, "브라우저 앱을 열 수 없습니다.", android.widget.Toast.LENGTH_SHORT).show()
                                }
                            },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF5722)),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("상세 구매처로 바로 이동 🚀", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
        
        // 핫딜 뱃지를 우측 최상단에 고정
        if (isSuperHot) {
            Surface(
                shape = RoundedCornerShape(bottomStart = 12.dp, topEnd = 12.dp),
                color = Color(0xFFFFEBEB),
                modifier = Modifier.align(Alignment.TopEnd)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text("🔥", fontSize = 12.sp)
                    Spacer(modifier = Modifier.width(2.dp))
                    Text("핫딜", fontSize = 11.sp, color = Color(0xFFD32F2F), fontWeight = FontWeight.ExtraBold)
                }
            }
        }
    }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KeywordSettingsBottomSheet(onDismiss: () -> Unit) {
    var keywordText by remember { mutableStateOf("") }
    var keywords by remember { mutableStateOf(listOf<String>()) }
    var isPushEnabled by remember { mutableStateOf(true) }
    
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("insightdeal_prefs", android.content.Context.MODE_PRIVATE) }
    var nightPushConsent by remember { mutableStateOf(prefs.getBoolean("night_push_consent", false)) }
    
    val coroutineScope = rememberCoroutineScope()
    val deviceUuid = remember { com.ddaeany0919.insightdeal.generateDeviceUserId(context) }
    val apiService = com.ddaeany0919.insightdeal.network.NetworkModule.retrofit.create(com.ddaeany0919.insightdeal.network.ApiService::class.java)
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    LaunchedEffect(Unit) {
        try {
            val response = apiService.getPushKeywords(deviceUuid)
            if (response.isSuccessful) {
                keywords = response.body()?.keywords ?: emptyList()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        dragHandle = { BottomSheetDefaults.DragHandle() }
    ) {
        Column(modifier = Modifier.padding(24.dp).padding(bottom = 32.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                Text("🔔 맞춤 키워드 알림", fontWeight = FontWeight.ExtraBold, fontSize = 22.sp)
                Switch(checked = isPushEnabled, onCheckedChange = { isPushEnabled = it })
            }
            Text("원하는 키워드를 등록하면 특가가 떴을 때 즉시 푸시 알림을 쏴드립니다!", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top=8.dp, bottom=16.dp))
            
            // 야간 알림 수신 동의 UI
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = if (nightPushConsent) MaterialTheme.colorScheme.primaryContainer.copy(alpha=0.3f) else MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp).clickable { 
                    val newValue = !nightPushConsent
                    nightPushConsent = newValue
                    prefs.edit().putBoolean("night_push_consent", newValue).apply()
                    
                    val token = prefs.getString("fcm_token", "") ?: ""
                    coroutineScope.launch {
                        try {
                            val req = com.ddaeany0919.insightdeal.network.RegisterDeviceReq(
                                device_uuid = deviceUuid,
                                fcm_token = token,
                                night_push_consent = newValue
                            )
                            apiService.registerFCMToken(req)
                        } catch (e: Exception) { e.printStackTrace() }
                    }
                }
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(16.dp)) {
                    Checkbox(
                        checked = nightPushConsent,
                        onCheckedChange = { 
                            nightPushConsent = it 
                            prefs.edit().putBoolean("night_push_consent", it).apply()
                            
                            val token = prefs.getString("fcm_token", "") ?: ""
                            coroutineScope.launch {
                                try {
                                    val req = com.ddaeany0919.insightdeal.network.RegisterDeviceReq(
                                        device_uuid = deviceUuid,
                                        fcm_token = token,
                                        night_push_consent = it
                                    )
                                    apiService.registerFCMToken(req)
                                } catch (e: Exception) { e.printStackTrace() }
                            }
                        }
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text("야간(21시~08시) 핫딜 푸시 수신 동의", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        Text("정보통신망법 제50조에 따른 필수 동의 항목입니다.", fontSize = 11.sp, color = MaterialTheme.colorScheme.error)
                    }
                }
            }

            OutlinedTextField(
                value = keywordText,
                onValueChange = { keywordText = it },
                placeholder = { Text("예: 맥북") },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                trailingIcon = {
                    IconButton(onClick = {
                        if (keywordText.isNotBlank() && !keywords.contains(keywordText)) {
                            val newKw = keywordText
                            coroutineScope.launch {
                                try {
                                    val req = com.ddaeany0919.insightdeal.network.AddKeywordRequest(deviceUuid, newKw)
                                    val res = apiService.addPushKeyword(req)
                                    if (res.isSuccessful) {
                                        keywords = keywords + newKw
                                        keywordText = ""
                                    }
                                } catch (e: Exception) { e.printStackTrace() }
                            }
                        }
                    }) {
                        Icon(Icons.Default.Add, contentDescription = "추가", tint = MaterialTheme.colorScheme.primary)
                    }
                },
                singleLine = true
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            @OptIn(ExperimentalLayoutApi::class)
            FlowRow(
                modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                keywords.forEach { kw ->
                    InputChip(
                        selected = false,
                        onClick = { },
                        label = { Text(kw, fontWeight = FontWeight.Bold) },
                        trailingIcon = {
                            Icon(
                                Icons.Default.Close,
                                contentDescription = "삭제",
                                modifier = Modifier.size(16.dp).clickable {
                                    coroutineScope.launch {
                                        try {
                                            val req = com.ddaeany0919.insightdeal.network.AddKeywordRequest(deviceUuid, kw)
                                            val res = apiService.deletePushKeyword(req)
                                            if (res.isSuccessful) {
                                                keywords = keywords.filter { it != kw }
                                            }
                                        } catch (e: Exception) { e.printStackTrace() }
                                    }
                                }
                            )
                        },
                        colors = InputChipDefaults.inputChipColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                            labelColor = MaterialTheme.colorScheme.onPrimaryContainer
                        ),
                        border = InputChipDefaults.inputChipBorder(
                            borderColor = Color.Transparent, enabled = true, selected = false
                        ),
                        shape = RoundedCornerShape(16.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
            Button(
                onClick = { 
                    coroutineScope.launch {
                        sheetState.hide()
                        onDismiss()
                    }
                }, 
                modifier = Modifier.fillMaxWidth().height(50.dp),
                shape = RoundedCornerShape(16.dp)
            ) {
                Text("저장 및 닫기", fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
fun CoupangSettingsDialog(onDismiss: () -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val prefs = remember { context.getSharedPreferences("insightdeal_prefs", android.content.Context.MODE_PRIVATE) }
    
    var coupangAccessKey by remember { mutableStateOf(prefs.getString("coupang_access_key", "") ?: "") }
    var coupangSecretKey by remember { mutableStateOf(prefs.getString("coupang_secret_key", "") ?: "") }
    var coupangAfCode by remember { mutableStateOf(prefs.getString("coupang_af_code", "") ?: "") }

    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(modifier = Modifier.padding(24.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Text("🛒 쿠팡 파트너스 연동", fontWeight = FontWeight.ExtraBold, fontSize = 20.sp)
                }
                Text("API Key를 입력하시면 앱 내 핫딜 터치 시 대표님의 AF코드를 타도록 자동 변환됩니다.", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top=8.dp, bottom=16.dp))
                
                OutlinedTextField(
                    value = coupangAccessKey,
                    onValueChange = { coupangAccessKey = it },
                    label = { Text("Access Key") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    singleLine = true
                )
                
                Spacer(modifier = Modifier.height(12.dp))
                
                OutlinedTextField(
                    value = coupangSecretKey,
                    onValueChange = { coupangSecretKey = it },
                    label = { Text("Secret Key") },
                    visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(12.dp))
                
                OutlinedTextField(
                    value = coupangAfCode,
                    onValueChange = { coupangAfCode = it },
                    label = { Text("AF 코드 (예: AF1234567)") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(24.dp))
                
                Text(
                    text = "🚨 주의: 공정위 지침에 따라 상세 페이지 내 [쿠팡 파트너스 활동] 문구가 자동 노출됩니다.",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(bottom = 16.dp)
                )

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedButton(
                        onClick = onDismiss, 
                        modifier = Modifier.weight(1f).height(50.dp),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Text("취소", fontWeight = FontWeight.Bold)
                    }
                    Button(
                        onClick = {
                            prefs.edit()
                                .putString("coupang_access_key", coupangAccessKey)
                                .putString("coupang_secret_key", coupangSecretKey)
                                .putString("coupang_af_code", coupangAfCode)
                                .apply()
                            android.widget.Toast.makeText(context, "API Key 및 AF코드가 저장되었습니다.", android.widget.Toast.LENGTH_SHORT).show()
                            onDismiss()
                        }, 
                        modifier = Modifier.weight(1f).height(50.dp),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Text("저장", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}
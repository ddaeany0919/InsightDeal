package com.ddaeany0919.insightdeal.presentation.home

import com.ddaeany0919.insightdeal.presentation.formatPrice
import androidx.compose.foundation.*
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.ExperimentalFoundationApi
import com.ddaeany0919.insightdeal.presentation.KeywordManagerViewModel
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
import com.ddaeany0919.insightdeal.presentation.bounceClick
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.input.nestedscroll.nestedScroll
import kotlinx.coroutines.launch
import java.util.Locale
import com.ddaeany0919.insightdeal.core.network.NetworkMonitor
import com.ddaeany0919.insightdeal.presentation.wishlist.WishlistViewModel
import com.ddaeany0919.insightdeal.presentation.wishlist.WishlistUiState

import androidx.compose.animation.core.*
import androidx.compose.animation.animateColorAsState
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.composed

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun HomeScreen(
    navController: NavController, 
    viewModel: HomeViewModel = viewModel(),
    wishlistViewModel: WishlistViewModel? = null,
    keywordViewModel: KeywordManagerViewModel = viewModel()
) {
    val filterState by viewModel.filterState.collectAsState()
    val selectedCategory = filterState.category
    
    val wishlistState by wishlistViewModel?.uiState?.collectAsState(initial = WishlistUiState.Loading) ?: mutableStateOf(WishlistUiState.Loading)
    val wishlistedUrls = remember(wishlistState) {
        if (wishlistState is WishlistUiState.Success) {
            (wishlistState as WishlistUiState.Success).items.map { it.productUrl }.toSet()
        } else emptySet()
    }
    
    val context = LocalContext.current
    val networkMonitor = remember { NetworkMonitor(context) }
    val isOnline by networkMonitor.isOnline.collectAsState(initial = true)
    val isDark = isSystemInDarkTheme()
    
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
        KeywordSettingsBottomSheet(
            onDismiss = { showKeywordDialog = false },
            keywordViewModel = keywordViewModel
        )
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

    // 람다 재생성 방지 및 컴포지션 메모리 방어
    val onNavigateToDetail = remember(navController) {
        { dealId: Int -> navController.navigate("deal_detail/$dealId") }
    }
    var showCommissionSheet by remember { mutableStateOf(false) }
    var pendingUrlToOpen by remember { mutableStateOf("") }
    var dontShowToday by remember { mutableStateOf(false) }
    var commissionMessage by remember { mutableStateOf("") }

    val onOpenUrlInWebView = remember(navController, context) {
        { url: String ->
            val prefs = com.ddaeany0919.insightdeal.core.security.EncryptedPrefsManager.getEncryptedPrefs(context)
            val savedTime = prefs.getLong("last_dismiss_commission_time", 0L)
            val currentTime = System.currentTimeMillis()
            val shouldShowSheet = (currentTime - savedTime) > 24 * 60 * 60 * 1000

            val isCoupang = url.contains("coupang.com") || url.contains("link.coupang.com")
            val isAliexpress = url.contains("aliexpress.com") || url.contains("s.click.aliexpress.com")
            val isAffiliate = isCoupang || isAliexpress

            if (isAffiliate && shouldShowSheet) {
                pendingUrlToOpen = url
                dontShowToday = false
                commissionMessage = if (isCoupang) {
                    "이 링크는 쿠팡 파트너스 활동의 일환으로, 구매 시 이에 따른 일정액의 수수료를 지급받아 InsightDeal 서비스를 지속적으로 운영하는 데 큰 도움이 됩니다.\n\n추가적인 구매 금액 부담은 전혀 없으니 안심하셔도 좋습니다! 🧡"
                } else {
                    "이 링크는 알리익스프레스 어소시에이트 활동의 일환으로, 구매 시 이에 따른 일정액의 수수료를 지급받아 InsightDeal 서비스를 지속적으로 운영하는 데 큰 도움이 됩니다.\n\n추가적인 구매 금액 부담은 전혀 없으니 안심하셔도 좋습니다! 🧡"
                }
                showCommissionSheet = true
            } else {
                val encodedUrl = java.net.URLEncoder.encode(url, "UTF-8")
                navController.navigate("webview/$encodedUrl")
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize().nestedScroll(pullRefreshState.nestedScrollConnection)) {
        Column(modifier = Modifier.fillMaxSize()) {
            TopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically, 
                        modifier = Modifier.bounceClick { viewModel.selectCategory("전체") }
                    ) {
                        Text(
                            text = "🔥 InsightDeal",
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Surface(
                            modifier = Modifier.size(34.dp, 18.dp),
                            shape = RoundedCornerShape(4.dp),
                            color = if (isDark) Color(0xFFE53935) else Color(0xFFFF3B30)
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
                    IconButton(onClick = { showCoupangDialog = true }) { Icon(Icons.Default.ShoppingCart, "쿠팡 연동") }
                    IconButton(onClick = { showKeywordDialog = true }) { Icon(Icons.Default.NotificationsActive, "키워드 알림") }
                }
            )

            AnimatedVisibility(
                visible = !isOnline,
                enter = androidx.compose.animation.expandVertically(
                    expandFrom = Alignment.Top,
                    animationSpec = androidx.compose.animation.core.tween(300)
                ) + androidx.compose.animation.fadeIn(),
                exit = androidx.compose.animation.shrinkVertically(
                    shrinkTowards = Alignment.Top,
                    animationSpec = androidx.compose.animation.core.tween(300)
                ) + androidx.compose.animation.fadeOut()
            ) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = if (isDark) Color(0xFFD84315) else Color(0xFFFF9500),
                    shadowElevation = 2.dp
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.CloudOff,
                            contentDescription = "Offline Warning",
                            tint = Color.White,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "⚠️ 현재 오프라인 상태입니다. 로컬 캐시된 핫딜을 표시하고 있습니다.",
                            color = Color.White,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }

            // 🔔 Toss/29CM 스타일의 흐르는 프리미엄 관심 키워드 칩셋 LazyRow
            val keywordsState by keywordViewModel.keywords.collectAsState()
            val haptic = androidx.compose.ui.platform.LocalHapticFeedback.current
            val currentKeywordFilter = filterState.keyword

            if (keywordsState.isNotEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    contentAlignment = Alignment.CenterStart
                ) {
                    LazyRow(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(start = 16.dp, end = 48.dp, top = 8.dp, bottom = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        items(
                            items = keywordsState,
                            key = { it.id }
                        ) { keywordEntity ->
                            val isSelected = currentKeywordFilter == keywordEntity.keyword
                            val isActive = keywordEntity.isActive

                            // 쫀득한 클릭 반응 애니메이션을 위한 Scale
                            val scale by animateFloatAsState(
                                targetValue = if (isSelected) 1.05f else 1.0f,
                                animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
                                label = "chipScale"
                            )

                            // 켜짐/꺼짐 색상 전환 애니메이션
                            val animBgColor by animateColorAsState(
                                targetValue = when {
                                    isSelected -> MaterialTheme.colorScheme.primaryContainer
                                    isActive -> MaterialTheme.colorScheme.surfaceVariant
                                    else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                                },
                                label = "chipBgColor"
                            )

                            val animTextColor by animateColorAsState(
                                targetValue = when {
                                    isSelected -> MaterialTheme.colorScheme.onPrimaryContainer
                                    isActive -> MaterialTheme.colorScheme.onSurface
                                    else -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                                },
                                label = "chipTextColor"
                            )

                            val animBorderColor by animateColorAsState(
                                targetValue = when {
                                    isSelected -> MaterialTheme.colorScheme.primary
                                    isActive -> MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)
                                    else -> Color.Transparent
                                },
                                label = "chipBorder"
                            )

                            Surface(
                                modifier = Modifier
                                    .graphicsLayer {
                                        scaleX = scale
                                        scaleY = scale
                                    }
                                    .clip(RoundedCornerShape(20.dp))
                                    .combinedClickable(
                                        onClick = {
                                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                            if (isSelected) {
                                                viewModel.searchDeals("") // 필터 해제
                                            } else {
                                                viewModel.searchDeals(keywordEntity.keyword) // 필터 적용
                                            }
                                        },
                                        onLongClick = {
                                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                            keywordViewModel.toggleKeyword(keywordEntity)
                                        }
                                    ),
                                color = animBgColor,
                                border = BorderStroke(1.dp, animBorderColor),
                                shape = RoundedCornerShape(20.dp)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = if (isActive) "🔔 ${keywordEntity.keyword}" else "🔕 ${keywordEntity.keyword}",
                                        color = animTextColor,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }

                    // 프리미엄 페이드 그라데이션 에지 마스크 (우측)
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .width(48.dp)
                            .align(Alignment.CenterEnd)
                            .background(
                                brush = Brush.horizontalGradient(
                                    colors = listOf(
                                        Color.Transparent,
                                        MaterialTheme.colorScheme.background
                                    )
                                )
                            )
                    )
                }
            }

            LazyColumn(
                modifier = Modifier.fillMaxWidth().weight(1f),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                    // [Phase 11 Epic 2] 안드로이드 상단 '투데이 픽' 캐러셀 UI 도입
                    if (selectedCategory == "전체") {
                        item(key = "today_picks_section", contentType = "today_picks_section") {
                            val topHotDeals by viewModel.topHotDeals.collectAsState()
                            
                            val topPicks = remember(topHotDeals) {
                                topHotDeals
                                    .filter { deal ->
                                        val hasHotTag = deal.aiSummary?.contains("🔥 [커뮤니티 인기]") == true
                                                || deal.aiSummary?.contains("🔥 [커뮤니티 인증 핫딜]") == true
                                        val isSuperHot = hasHotTag && deal.honeyScore >= 100
                                        !deal.isClosed && isSuperHot
                                    }
                                    .filter { deal ->
                                        deal.category != "적립" && !deal.title.contains("적립") && !deal.title.contains("불가") && !deal.title.contains("정보")
                                    }
                                    .sortedByDescending { it.createdAt ?: "" }
                            }
                            
                            LaunchedEffect(topHotDeals.size, topPicks.size) {
                                android.util.Log.d("HomeScreen", "topHotDeals size: ${topHotDeals.size}, topPicks size: ${topPicks.size}")
                            }

                            if (topPicks.isNotEmpty()) {
                                Column(modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)) {
                                    Text(
                                        text = "오늘의 핫딜", 
                                        fontWeight = FontWeight.ExtraBold, 
                                        fontSize = 18.sp, 
                                        modifier = Modifier.padding(bottom = 12.dp, start = 4.dp),
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    LazyRow(
                                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                                        contentPadding = PaddingValues(horizontal = 4.dp)
                                    ) {
                                        items(
                                            count = topPicks.size,
                                            key = { index -> topPicks[index].id },
                                            contentType = { "today_pick_item" }
                                        ) { index ->
                                            val pick = topPicks[index]
                                            
                                            // 스크롤 시 문자열 가공 오버헤드 방지를 위한 캐싱
                                            val proxiedPickUrl = remember(pick.id, pick.imageUrl) {
                                                val fallbackPickImg = "https://placehold.co/400x300/E2E8F0/A0AEC0?text=Deal"
                                                val rawPickUrl = pick.imageUrl.takeIf { !it.isNullOrBlank() } ?: fallbackPickImg
                                                if (rawPickUrl.contains("bbasak.com") || rawPickUrl.contains("ppomppu.co.kr") || rawPickUrl.contains("fmkorea.com")) {
                                                    "http://192.168.0.36:8000/api/proxy-image?url=${java.net.URLEncoder.encode(rawPickUrl, "UTF-8")}"
                                                } else rawPickUrl
                                            }

                                            val imageRequest = remember(proxiedPickUrl, pick.postUrl) {
                                                coil.request.ImageRequest.Builder(context)
                                                    .data(proxiedPickUrl)
                                                    .setHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) Chrome/120.0.0.0 Safari/537.36")
                                                    .setHeader("Referer", pick.postUrl ?: "https://insightdeal.com/")
                                                    .crossfade(true).build()
                                            }

                                            val priceText = remember(pick.id, pick.price, pick.currency, pick.category, pick.title) {
                                                if (pick.price > 0) {
                                                    when (pick.currency.uppercase(Locale.getDefault())) {
                                                        "USD" -> "$${String.format(Locale.US, "%.2f", pick.price.toDouble() / 100)}"
                                                        "EUR" -> "€${String.format(Locale.US, "%.2f", pick.price.toDouble() / 100)}"
                                                        else -> formatPrice(pick.price, pick.currency)
                                                    }
                                                } else if (pick.category == "적립" || pick.title.contains("적립")) {
                                                    "포인트 적립"
                                                } else if (pick.category == "이벤트" || pick.category == "SW/게임" || pick.category == "게임/SW" ||
                                                    listOf("무료", "공짜", "배포", "나눔", "0원", "일시무료", "쿠폰").any { pick.title.contains(it) }) {
                                                    "무료 (쿠폰/공짜)"
                                                } else {
                                                    "정보 확인필요"
                                                }
                                            }

                                            val isBookmarked = wishlistedUrls.contains(pick.postUrl)
                                            
                                            val displayShipping = remember(pick.id, pick.shippingFee, pick.category, pick.title) {
                                                val digitalCategories = listOf("적립", "이벤트", "모바일/기프티콘", "상품권", "패키지/이용권")
                                                val isDigitalTitle = pick.title.contains("요금제") || pick.title.contains("데이터")
                                                val hideShipping = pick.category in digitalCategories || isDigitalTitle
                                                
                                                if (hideShipping) null
                                                else {
                                                    val trimmed = pick.shippingFee?.trim() ?: "정보 없음"
                                                    when {
                                                        trimmed == "정보 없음" || trimmed.isEmpty() -> "확인 필요"
                                                        trimmed.contains("와우") -> "와우무료"
                                                        trimmed.contains("스마일") || trimmed.contains("스클") -> "스클무료"
                                                        trimmed.contains("우주패스") -> "우주패스"
                                                        trimmed == "0" || trimmed == "0원" || trimmed == "무배" || trimmed == "무료" || trimmed == "무료배송" -> "무료"
                                                        trimmed.startsWith("0원/") || trimmed.startsWith("0원+") || trimmed.startsWith("0/") || trimmed.startsWith("0+") -> trimmed.replaceFirst(Regex("^0(원)?\\s*"), "무료 ")
                                                        trimmed == "유료" || trimmed == "유료배송" -> "유료"
                                                        trimmed.all { it.isDigit() || it == ',' } -> "${trimmed}원"
                                                        else -> trimmed.replace("무료배송", "무료").replace("유료배송", "유료")
                                                    }
                                                }
                                            }

                                            Card(
                                                modifier = Modifier
                                                    .width(180.dp)
                                                    .height(195.dp)
                                                    .bounceClick {
                                                        val targetUrl = pick.ecommerceUrl?.takeIf { it.isNotBlank() } ?: pick.postUrl ?: "https://insightdeal.com"
                                                        var finalUrl = if (targetUrl.startsWith("http")) targetUrl else "https://$targetUrl"
                                                        if (finalUrl.contains("bbasak.com") && !finalUrl.contains("device=pc")) {
                                                            finalUrl += if (finalUrl.contains("?")) "&device=pc" else "?device=pc"
                                                        }
                                                        onOpenUrlInWebView(finalUrl)
                                                    },
                                                shape = RoundedCornerShape(16.dp),
                                                elevation = CardDefaults.cardElevation(if (isDark) 0.dp else 2.dp),
                                                colors = CardDefaults.cardColors(
                                                    containerColor = if (isDark) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.8f) else Color.White,
                                                    contentColor = MaterialTheme.colorScheme.onSurface
                                                )
                                            ) {
                                                Column {
                                                    AsyncImage(
                                                        model = imageRequest,
                                                        contentDescription = "Carousel Image",
                                                        modifier = Modifier
                                                            .height(100.dp)
                                                            .fillMaxWidth()
                                                            .background(if (isDark) MaterialTheme.colorScheme.surfaceVariant else Color.White)
                                                            .clip(RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)),
                                                        contentScale = ContentScale.Crop
                                                    )
                                                    Column(Modifier.padding(8.dp)) {
                                                        Text(
                                                            text = pick.title, 
                                                            maxLines = 2, 
                                                            overflow = TextOverflow.Ellipsis, 
                                                            fontSize = 13.sp, 
                                                            fontWeight = FontWeight.Bold, 
                                                            lineHeight = 18.sp,
                                                            color = MaterialTheme.colorScheme.onSurface
                                                        )
                                                        Spacer(Modifier.weight(1f))
                                                        
                                                        Row(
                                                            verticalAlignment = Alignment.CenterVertically, 
                                                            horizontalArrangement = Arrangement.SpaceBetween, 
                                                            modifier = Modifier.fillMaxWidth()
                                                        ) {
                                                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                                                val priceFontSize = if (priceText.length >= 9) 13.sp else 16.sp
                                                                Text(
                                                                    text = priceText, 
                                                                    color = if (isDark) Color(0xFFFF453A) else Color(0xFFFF3B30), 
                                                                    fontSize = priceFontSize, 
                                                                    fontWeight = FontWeight.ExtraBold, 
                                                                    maxLines = 1, 
                                                                    overflow = TextOverflow.Ellipsis, 
                                                                    modifier = Modifier.weight(1f, fill = false)
                                                                )
                                                                if (displayShipping != null) {
                                                                    Spacer(Modifier.width(2.dp))
                                                                    Surface(
                                                                        shape = RoundedCornerShape(4.dp), 
                                                                        color = if (isDark) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceVariant
                                                                    ) {
                                                                        Text(
                                                                            text = "배송비: $displayShipping", 
                                                                            fontSize = 8.sp, 
                                                                            color = if (isDark) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurfaceVariant, 
                                                                            modifier = Modifier.padding(horizontal = 3.dp, vertical = 2.dp), 
                                                                            maxLines = 1, 
                                                                            overflow = TextOverflow.Ellipsis
                                                                        )
                                                                    }
                                                                }
                                                            }
                                                            androidx.compose.material3.IconButton(
                                                                onClick = { 
                                                                    wishlistViewModel?.let { vm ->
                                                                        val targetUrl = pick.ecommerceUrl?.takeIf { it.isNotBlank() } ?: pick.postUrl ?: ""
                                                                        if (wishlistedUrls.contains(targetUrl)) {
                                                                            val item = (wishlistState as? WishlistUiState.Success)?.items?.find { it.productUrl == targetUrl }
                                                                            if (item != null) vm.deleteItem(item)
                                                                        } else {
                                                                            vm.addItem(pick.title, targetUrl, pick.price.toInt())
                                                                        }
                                                                    }
                                                                },
                                                                modifier = Modifier.size(22.dp)
                                                            ) {
                                                                androidx.compose.material3.Icon(
                                                                    imageVector = if (isBookmarked) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                                                                    contentDescription = "Bookmark",
                                                                    tint = if (isBookmarked) Color.Red else Color.Gray,
                                                                    modifier = Modifier.size(18.dp)
                                                                )
                                                            }
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            } else {
                                Column(modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp, top = 8.dp)) {
                                    Text(
                                        text = "오늘의 핫딜", 
                                        fontWeight = FontWeight.ExtraBold, 
                                        fontSize = 18.sp, 
                                        modifier = Modifier.padding(bottom = 12.dp, start = 4.dp),
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Card(
                                        modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                                        shape = RoundedCornerShape(12.dp),
                                        elevation = CardDefaults.cardElevation(0.dp),
                                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                                    ) {
                                        Box(modifier = Modifier.fillMaxWidth().padding(vertical = 36.dp), contentAlignment = Alignment.Center) {
                                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                                Text("😢", fontSize = 42.sp, modifier = Modifier.padding(bottom = 12.dp))
                                                Text("현재 진행 중인 핫딜이 없습니다.", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                                Text("조금 뒤에 다시 확인해 보세요!", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f), modifier = Modifier.padding(top = 4.dp))
                                            }
                                        }
                                    }
                                }
                            }
                            Spacer(Modifier.height(8.dp))
                            androidx.compose.material3.HorizontalDivider(
                                thickness = 1.dp, 
                                color = if (isDark) MaterialTheme.colorScheme.outlineVariant else Color.LightGray, 
                                modifier = Modifier.padding(horizontal = 4.dp)
                            )
                            Spacer(Modifier.height(8.dp))
                            Text("실시간 핫딜", fontWeight = FontWeight.ExtraBold, fontSize = 18.sp, modifier = Modifier.padding(start = 4.dp), color = MaterialTheme.colorScheme.onSurface)
                        }
                    }
            // ✨ 상태 관리를 통한 스켈레톤 로딩 노출
            when (dealsPagingItems.loadState.refresh) {
                is LoadState.Loading -> {
                    items(
                        count = 3,
                        key = { index -> "skeleton_$index" },
                        contentType = { "skeleton_loader" }
                    ) { HomeDealCardSkeleton() }
                }
                is LoadState.Error -> {
                    item(key = "loading_error", contentType = "error_section") {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(220.dp)
                                .padding(horizontal = 4.dp, vertical = 8.dp),
                            shape = RoundedCornerShape(16.dp),
                            border = androidx.compose.foundation.BorderStroke(
                                1.dp,
                                MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.4f)
                            ),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.05f)
                            )
                        ) {
                            Column(
                                modifier = Modifier.fillMaxSize().padding(16.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.ErrorOutline,
                                    contentDescription = null,
                                    modifier = Modifier.size(44.dp),
                                    tint = MaterialTheme.colorScheme.error
                                )
                                Spacer(modifier = Modifier.height(10.dp))
                                Text(
                                    text = "실시간 핫딜 로딩 실패",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp,
                                    color = MaterialTheme.colorScheme.onErrorContainer
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "일시적인 네트워크 또는 서버 지연입니다.",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.6f)
                                )
                                Spacer(modifier = Modifier.height(14.dp))
                                Button(
                                    onClick = { dealsPagingItems.refresh() },
                                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                                    shape = RoundedCornerShape(8.dp),
                                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                                    modifier = Modifier.height(34.dp)
                                ) {
                                    Text("재시도", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
                else -> {
                    if (dealsPagingItems.itemCount == 0) {
                        item(key = "empty_deals", contentType = "empty_section") {
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(220.dp)
                                    .padding(horizontal = 4.dp, vertical = 8.dp),
                                shape = RoundedCornerShape(16.dp),
                                border = androidx.compose.foundation.BorderStroke(
                                    1.dp,
                                    MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                                ),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.1f)
                                )
                            ) {
                                Column(
                                    modifier = Modifier.fillMaxSize().padding(16.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.Center
                                ) {
                                    Text(
                                        text = "📦",
                                        fontSize = 42.sp
                                    )
                                    Spacer(modifier = Modifier.height(10.dp))
                                    Text(
                                        text = "아직 등록된 핫딜이 없습니다",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = "새로운 특가 정보가 수집되는 대로 바로 보여드릴게요!",
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                                    )
                                }
                            }
                        }
                    }

                    // ✨ itemKey 제공으로 Recomposition 최소화 보장 및 contentType 지정
                    items(
                        count = dealsPagingItems.itemCount,
                        key = dealsPagingItems.itemKey { it.id },
                        contentType = { "deal_item" }
                    ) { index ->
                        val deal = dealsPagingItems[index]
                        if (deal != null) {
                            DealCardComposable(
                                deal = deal,
                                onDetailClick = remember(deal.id) { { onNavigateToDetail(deal.id) } },
                                onOpenUrl = onOpenUrlInWebView,
                                isWishlisted = wishlistedUrls.contains(deal.ecommerceUrl?.takeIf { it.isNotBlank() } ?: deal.postUrl ?: ""),
                                onToggleWishlist = remember(deal.id, wishlistState) {
                                    {
                                        wishlistViewModel?.let { vm ->
                                            val targetUrl = deal.ecommerceUrl?.takeIf { it.isNotBlank() } ?: deal.postUrl ?: ""
                                            if (wishlistedUrls.contains(targetUrl)) {
                                                val item = (wishlistState as? WishlistUiState.Success)?.items?.find { it.productUrl == targetUrl }
                                                if (item != null) vm.deleteItem(item)
                                            } else {
                                                vm.addItem(deal.title, targetUrl, deal.price.toInt())
                                            }
                                        }
                                    }
                                }
                            )
                        }
                    }
                    
                    // 하단 추가 로딩 시의 스켈레톤 표시
                    if (dealsPagingItems.loadState.append is LoadState.Loading) {
                        item(key = "append_skeleton", contentType = "skeleton_loader") { 
                            HomeDealCardSkeleton() 
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

        // 💡 [법적 필수] 제휴 마케팅 수수료 우아한 고지 BottomSheet UI
        if (showCommissionSheet) {
            ModalBottomSheet(
                onDismissRequest = { showCommissionSheet = false },
                sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
                containerColor = if (isDark) MaterialTheme.colorScheme.surface else Color.White,
                dragHandle = { BottomSheetDefaults.DragHandle() }
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(start = 24.dp, end = 24.dp, bottom = 32.dp, top = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "구매 링크로 이동하기 전 안내해 드려요 💡",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.onSurface,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        color = if (isDark) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f) else Color(0xFFF2F4F7)
                    ) {
                        Text(
                            text = commissionMessage,
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            lineHeight = 20.sp,
                            modifier = Modifier.padding(16.dp)
                        )
                    }
                    
                    Spacer(modifier = Modifier.height(24.dp))
                    
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(
                                interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                                indication = null
                            ) { dontShowToday = !dontShowToday },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = dontShowToday,
                            onCheckedChange = { dontShowToday = it },
                            colors = CheckboxDefaults.colors(
                                checkedColor = if (isDark) Color(0xFFFF5A36) else Color(0xFFFF3B30)
                            )
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "오늘 하루 동안 이 안내 다시 보지 않기",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    
                    Spacer(modifier = Modifier.height(24.dp))
                    
                    Button(
                        onClick = {
                            showCommissionSheet = false
                            if (dontShowToday) {
                                val prefs = com.ddaeany0919.insightdeal.core.security.EncryptedPrefsManager.getEncryptedPrefs(context)
                                prefs.edit().putLong("last_dismiss_commission_time", System.currentTimeMillis()).apply()
                            }
                            if (pendingUrlToOpen.isNotEmpty()) {
                                val encodedUrl = java.net.URLEncoder.encode(pendingUrlToOpen, "UTF-8")
                                navController.navigate("webview/$encodedUrl")
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isDark) Color(0xFFFF5A36) else Color(0xFFFF3B30)
                        )
                    ) {
                        Text(
                            text = "확인하고 쇼핑몰로 이동하기",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                }
            }
        }
    }
}

// ✨ 세로형 딜 카드를 위한 스켈레톤 (Recomposition 방지용, 다크모드 완벽 대응)
@Composable
fun HomeDealCardSkeleton() {
    val isDark = isSystemInDarkTheme()
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(if (isDark) 0.dp else 2.dp),
        colors = CardDefaults.cardColors(containerColor = if (isDark) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f) else Color.White)
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

@Composable
fun DealCardComposable(
    deal: DealItem, 
    onDetailClick: () -> Unit = {}, 
    onOpenUrl: (String) -> Unit = {},
    isWishlisted: Boolean = false,
    onToggleWishlist: () -> Unit = {}
) {
    var isExpanded by remember { mutableStateOf(false) }
    val uriHandler = LocalUriHandler.current
    val context = LocalContext.current
    val isDark = isSystemInDarkTheme()

    // 1. 뱃지 판단 캐싱
    val isSuperHot = remember(deal.id, deal.aiSummary, deal.honeyScore) {
        (deal.aiSummary?.contains("🔥 [커뮤니티 인기]") == true
                || deal.aiSummary?.contains("🔥 [커뮤니티 인증 핫딜]") == true)
                && deal.honeyScore >= 100
    }

    // 2. 이미지 URL 프록시 캐싱
    val proxiedUrl = remember(deal.id, deal.imageUrl) {
        val rawUrl = deal.imageUrl
        if (rawUrl.isNullOrBlank()) null
        else if (rawUrl.contains("bbasak.com") || rawUrl.contains("ppomppu.co.kr") || rawUrl.contains("fmkorea.com")) {
            "http://192.168.0.36:8000/api/proxy-image?url=${java.net.URLEncoder.encode(rawUrl, "UTF-8")}"
        } else rawUrl
    }

    // 3. 이미지 Request 캐싱
    val imageRequest = remember(proxiedUrl, deal.postUrl) {
        if (proxiedUrl == null) null
        else {
            coil.request.ImageRequest.Builder(context)
                .data(proxiedUrl)
                .setHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) Chrome/120.0.0.0 Safari/537.36")
                .setHeader("Referer", deal.postUrl ?: "https://insightdeal.com/")
                .crossfade(true)
                .build()
        }
    }

    // 4. 출처 뱃지 목록 캐싱
    val sourcesToRender = remember(deal.id, deal.sources, deal.siteName, deal.postUrl) {
        val rawSources = if (!deal.sources.isNullOrEmpty()) deal.sources else listOf(com.ddaeany0919.insightdeal.models.DealSource(deal.siteName, deal.postUrl ?: ""))
        rawSources.distinctBy { it.siteName }
    }

    // 5. 배송비 연산 캐싱
    val displayShipping = remember(deal.id, deal.shippingFee, deal.category, deal.title) {
        val digitalCategories = listOf("적립", "이벤트", "모바일/기프티콘", "상품권", "패키지/이용권")
        val isDigitalTitle = deal.title.contains("요금제") || deal.title.contains("데이터")
        val hideShipping = deal.category in digitalCategories || isDigitalTitle

        if (hideShipping) null
        else {
            val trimmed = deal.shippingFee?.trim() ?: "정보 없음"
            when {
                trimmed == "정보 없음" || trimmed.isEmpty() -> "확인 필요"
                trimmed.contains("와우") -> "와우무료"
                trimmed.contains("스마일") || trimmed.contains("스클") -> "스클무료"
                trimmed.contains("우주패스") -> "우주패스"
                trimmed == "0" || trimmed == "0원" || trimmed == "무배" || trimmed == "무료" || trimmed == "무료배송" -> "무료"
                trimmed.startsWith("0원/") || trimmed.startsWith("0원+") || trimmed.startsWith("0/") || trimmed.startsWith("0+") -> trimmed.replaceFirst(Regex("^0(원)?\\s*"), "무료 ")
                trimmed == "유료" || trimmed == "유료배송" -> "유료"
                trimmed.all { it.isDigit() || it == ',' } -> "${trimmed}원"
                else -> trimmed.replace("무료배송", "무료").replace("유료배송", "유료")
            }
        }
    }

    // 6. 가격 정보 포맷팅 캐싱
    val priceText = remember(deal.id, deal.price, deal.currency, deal.category, deal.title) {
        if (deal.price > 0) {
            when (deal.currency.uppercase(Locale.getDefault())) {
                "USD" -> "$${String.format(Locale.US, "%.2f", deal.price.toDouble() / 100)}"
                "EUR" -> "€${String.format(Locale.US, "%.2f", deal.price.toDouble() / 100)}"
                else -> if (deal.category == "적립") "${formatPrice(deal.price, deal.currency)} 적립" else "${formatPrice(deal.price, deal.currency)}"
            }
        } else if (deal.category == "적립" || deal.title.contains("적립")) {
            "포인트 적립"
        } else if (deal.category == "이벤트" || deal.category == "SW/게임" || deal.category == "게임/SW" ||
            listOf("무료", "공짜", "배포", "나눔", "0원", "일시무료", "쿠폰").any { deal.title.contains(it) }) {
            "무료 (쿠폰/공짜)"
        } else {
            "정보 확인필요"
        }
    }

    // 7. 아코디언 관련 메리트 정보 캐시
    val AIPriceAdvantageText = remember(deal.id, deal.honeyScore) {
        val percentileText = when {
            deal.honeyScore >= 95 -> "최상위 1% 급"
            deal.honeyScore >= 90 -> "상위 5%"
            deal.honeyScore >= 80 -> "상위 10%"
            deal.honeyScore >= 70 -> "상위 20%"
            else -> "평이"
        }
        if (percentileText == "평이") "📊 AI 가격 메리트 (${deal.honeyScore}점)" else "💡 AI 가격 메리트: $percentileText 저렴! (${deal.honeyScore}점)"
    }

    val cardBgColor = if (isDark) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.8f) else Color.White
    val cardContentColor = MaterialTheme.colorScheme.onSurface

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .bounceClick(scaleDown = 0.98f) { isExpanded = !isExpanded }
            .alpha(if (deal.isClosed) 0.65f else 1f),
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(if (isDark) 0.dp else 2.dp),
        colors = CardDefaults.cardColors(
            containerColor = cardBgColor,
            contentColor = cardContentColor
        )
    ) {
        Box(modifier = Modifier.fillMaxWidth()) {
            Column {
                Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.Top) {
                    if (imageRequest == null) {
                        // 이미지가 없는 경우 (또는 분리된 상품 중 이미지가 없는 경우)
                        Box(
                            modifier = Modifier
                                .size(80.dp)
                                .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(8.dp))
                                .clip(RoundedCornerShape(8.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.ShoppingCart,
                                contentDescription = "No Image",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                                modifier = Modifier.size(32.dp)
                            )
                        }
                    } else {
                        Box(modifier = Modifier.size(80.dp)) {
                            AsyncImage(
                                model = imageRequest,
                                contentDescription = deal.title,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(if (isDark) MaterialTheme.colorScheme.surfaceVariant else Color.White, RoundedCornerShape(8.dp))
                                    .clip(RoundedCornerShape(8.dp)),
                                colorFilter = if (deal.isClosed) {
                                    remember { androidx.compose.ui.graphics.ColorFilter.colorMatrix(androidx.compose.ui.graphics.ColorMatrix().apply { setToSaturation(0f) }) }
                                } else null,
                                contentScale = ContentScale.Crop,
                                error = androidx.compose.ui.graphics.vector.rememberVectorPainter(image = Icons.Default.ShoppingCart)
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
                            sourcesToRender.forEach { source ->
                                val displaySiteName = remember(source.siteName) {
                                    if (source.siteName.contains(" - ")) {
                                        source.siteName.substringBefore(" - ").trim()
                                    } else {
                                        source.siteName
                                    }
                                }
                                val (containerColor, contentColor) = remember(displaySiteName, isDark) {
                                    val siteNameLower = displaySiteName.lowercase(Locale.getDefault())
                                    when {
                                        siteNameLower.contains("뽐뿌") -> Color(0xFF1565C0) to Color.White
                                        siteNameLower.contains("퀘이사존") -> Color(0xFFE65100) to Color.White
                                        siteNameLower.contains("루리웹") -> Color(0xFF0D47A1) to Color.White
                                        siteNameLower.contains("펨코") || siteNameLower.contains("에펨코리아") -> Color(0xFF0288D1) to Color.White
                                        siteNameLower.contains("빠삭") -> Color(0xFFC2185B) to Color.White
                                        siteNameLower.contains("클리앙") -> Color(0xFF37474F) to Color.White
                                        else -> if (isDark) Color(0xFF424242) to Color.White else Color(0xFFE0E0E0) to Color.Black
                                    }
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
                                        text = displaySiteName,
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
                                modifier = Modifier.weight(1f).padding(end = 8.dp),
                                textDecoration = if (deal.isClosed) TextDecoration.LineThrough else null,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            val timeAgo = rememberRelativeTime(deal.createdAt)
                            if (timeAgo.isNotEmpty()) {
                                Surface(
                                    shape = RoundedCornerShape(4.dp),
                                    color = if (isDark) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                                    modifier = Modifier.padding(start = 6.dp)
                                ) {
                                    Text(
                                        text = timeAgo,
                                        fontSize = 10.sp,
                                        color = if (isDark) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                                    )
                                }
                            }
                        }
                        
                        Spacer(modifier = Modifier.height(6.dp))
                        
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            @OptIn(ExperimentalLayoutApi::class)
                            FlowRow(
                                modifier = Modifier.weight(1f),
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                val priceFontSize = if (priceText.length >= 9) 13.sp else 16.sp
                                Text(
                                    text = priceText,
                                    fontSize = priceFontSize,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = if (deal.price > 0) {
                                        if (isDark) Color(0xFFFF453A) else Color(0xFFFF3B30)
                                    } else MaterialTheme.colorScheme.error,
                                    modifier = Modifier.align(Alignment.CenterVertically).weight(1f, fill = false),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                
                                if (displayShipping != null) {
                                    Surface(
                                        shape = RoundedCornerShape(4.dp), 
                                        color = if (isDark) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceVariant, 
                                        modifier = Modifier.align(Alignment.CenterVertically)
                                    ) {
                                        Text(
                                            text = " 배송비: $displayShipping ", 
                                            fontSize = 9.sp, 
                                            color = if (isDark) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurfaceVariant, 
                                            modifier = Modifier.padding(vertical = 2.dp)
                                        )
                                    }
                                }
                                
                                if (deal.isClosed) {
                                    Surface(
                                        shape = RoundedCornerShape(4.dp), 
                                        color = MaterialTheme.colorScheme.errorContainer, 
                                        modifier = Modifier.align(Alignment.CenterVertically)
                                    ) {
                                        Text(
                                            text = "종료됨", 
                                            fontSize = 10.sp, 
                                            color = MaterialTheme.colorScheme.onErrorContainer, 
                                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp), 
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }
                            IconButton(
                                onClick = onToggleWishlist,
                                modifier = Modifier.size(24.dp).padding(start = 4.dp)
                            ) {
                                Icon(
                                    imageVector = if (isWishlisted) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                                    contentDescription = "관심목록 추가/제거",
                                    tint = if (isWishlisted) Color(0xFFE91E63) else Color.Gray
                                )
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
                    Box(modifier = Modifier.fillMaxWidth().heightIn(min = 150.dp, max = 300.dp), contentAlignment = Alignment.Center) {
                        val proxiedExpandedUrl = remember(deal.id, deal.imageUrl) {
                            val rawImageUrl = deal.imageUrl.takeIf { !it.isNullOrBlank() } ?: "https://placehold.co/400x300/E2E8F0/A0AEC0?text=Deal"
                            if (rawImageUrl.contains("bbasak.com") || rawImageUrl.contains("ppomppu.co.kr") || rawImageUrl.contains("fmkorea.com")) {
                                "http://192.168.0.36:8000/api/proxy-image?url=${java.net.URLEncoder.encode(rawImageUrl, "UTF-8")}"
                            } else rawImageUrl
                        }

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
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .then(if (deal.isClosed) Modifier.blur(6.dp) else Modifier),
                            colorFilter = if (deal.isClosed) androidx.compose.ui.graphics.ColorFilter.colorMatrix(androidx.compose.ui.graphics.ColorMatrix().apply { setToSaturation(0f) }) else null,
                            contentScale = ContentScale.Inside,
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
                    
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = if (isDark) Color(0xFF3E2723) else Color(0xFFFFF8E1),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = AIPriceAdvantageText,
                            modifier = Modifier.padding(8.dp),
                            fontSize = 12.sp,
                            color = if (isDark) Color(0xFFFF8A65) else Color(0xFFD84315),
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center
                        )
                    }
                    
                    Spacer(modifier = Modifier.height(8.dp))

                    if (!deal.aiSummary.isNullOrBlank()) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp), 
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.Star, contentDescription = "AI", tint = if (isDark) Color(0xFF81C784) else Color(0xFF4CAF50), modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text(
                                text = deal.aiSummary, 
                                fontSize = 13.sp, 
                                color = if (isDark) Color(0xFF81C784) else Color(0xFF2E7D32), 
                                fontWeight = FontWeight.Medium, 
                                lineHeight = 18.sp
                            )
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                    }
                    // 📈 가격 상세 분석 버튼 추가
                    Button(
                        onClick = onDetailClick,
                        modifier = Modifier.fillMaxWidth().height(48.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isDark) MaterialTheme.colorScheme.secondary else Color(0xFF1F2937),
                            contentColor = if (isDark) MaterialTheme.colorScheme.onSecondary else Color.White
                        ),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("🔥 AI 가격 분석 & 상세정보 보기", fontWeight = FontWeight.ExtraBold)
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
                        modifier = Modifier.fillMaxWidth().height(48.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isDark) MaterialTheme.colorScheme.primary else Color(0xFFFF6D00),
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        ),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("구매하기 🚀", fontWeight = FontWeight.ExtraBold)
                    }
                }
            }
        }
        
        // 핫딜 뱃지를 우측 최상단에 고정
        if (isSuperHot) {
            Surface(
                shape = RoundedCornerShape(bottomStart = 12.dp, topEnd = 12.dp),
                color = if (isDark) Color(0xFF3700B3).copy(alpha = 0.2f) else Color(0xFFFFEBEB),
                modifier = Modifier.align(Alignment.TopEnd)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text("🔥", fontSize = 12.sp)
                    Spacer(modifier = Modifier.width(2.dp))
                    Text(
                        text = "핫딜", 
                        fontSize = 11.sp, 
                        color = if (isDark) Color(0xFFCF6679) else Color(0xFFD32F2F), 
                        fontWeight = FontWeight.ExtraBold
                    )
                }
            }
        }
    }
}
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun KeywordSettingsBottomSheet(
    onDismiss: () -> Unit,
    keywordViewModel: KeywordManagerViewModel
) {
    var keywordText by remember { mutableStateOf("") }
    
    val keywordsState by keywordViewModel.keywords.collectAsState()
    val dndEnabled by keywordViewModel.dndEnabled.collectAsState()
    val dndStartTime by keywordViewModel.dndStartTime.collectAsState()
    val dndEndTime by keywordViewModel.dndEndTime.collectAsState()
    
    val coroutineScope = rememberCoroutineScope()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        dragHandle = { BottomSheetDefaults.DragHandle() }
    ) {
        Column(modifier = Modifier.padding(24.dp).padding(bottom = 32.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                Text("🔔 맞춤 키워드 알림", fontWeight = FontWeight.ExtraBold, fontSize = 22.sp, color = MaterialTheme.colorScheme.onSurface)
                Switch(
                    checked = !dndEnabled, 
                    onCheckedChange = { isEnabled ->
                        keywordViewModel.updateDndSettings(
                            enabled = !isEnabled, 
                            startTime = dndStartTime, 
                            endTime = dndEndTime
                        )
                    }
                )
            }
            Text("원하는 키워드를 등록하면 특가가 떴을 때 즉시 푸시 알림을 쏴드립니다!", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top=8.dp, bottom=16.dp))
            
            // 야간 알림 수신 동의 및 DND 상태 토글 UI
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = if (dndEnabled) MaterialTheme.colorScheme.primaryContainer.copy(alpha=0.3f) else MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp).clickable { 
                    keywordViewModel.updateDndSettings(
                        enabled = !dndEnabled, 
                        startTime = dndStartTime, 
                        endTime = dndEndTime
                    )
                }
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(16.dp)) {
                    Checkbox(
                        checked = dndEnabled,
                        onCheckedChange = { 
                            keywordViewModel.updateDndSettings(
                                enabled = it, 
                                startTime = dndStartTime, 
                                endTime = dndEndTime
                            )
                        }
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text("야간(21시~08시) DND 모드 활성화", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurface)
                        Text("야간 시간대에 수신을 일시적으로 제한합니다.", fontSize = 11.sp, color = MaterialTheme.colorScheme.error)
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
                        if (keywordText.isNotBlank() && !keywordsState.any { it.keyword == keywordText }) {
                            keywordViewModel.addKeyword(keywordText)
                            keywordText = ""
                        }
                    }) {
                        Icon(Icons.Default.Add, contentDescription = "추가", tint = MaterialTheme.colorScheme.primary)
                    }
                },
                singleLine = true
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            FlowRow(
                modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                keywordsState.forEach { kwEntity ->
                    InputChip(
                        selected = false,
                        onClick = { },
                        label = { Text(kwEntity.keyword, fontWeight = FontWeight.Bold) },
                        trailingIcon = {
                            Icon(
                                Icons.Default.Close,
                                contentDescription = "삭제",
                                modifier = Modifier.size(16.dp).clickable {
                                    keywordViewModel.deleteKeyword(kwEntity.keyword)
                                }
                            )
                        },
                        colors = InputChipDefaults.inputChipColors(
                            containerColor = if (kwEntity.isActive) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                            labelColor = if (kwEntity.isActive) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
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
                    Text("🛒 쿠팡 파트너스 연동", fontWeight = FontWeight.ExtraBold, fontSize = 20.sp, color = MaterialTheme.colorScheme.onSurface)
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
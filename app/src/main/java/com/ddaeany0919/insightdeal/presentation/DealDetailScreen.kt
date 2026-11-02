package com.ddaeany0919.insightdeal.presentation

import com.ddaeany0919.insightdeal.presentation.formatPrice
import androidx.compose.animation.core.*
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material3.*
import androidx.compose.runtime.*
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.composed
import coil.compose.AsyncImage
import com.ddaeany0919.insightdeal.models.DealItem
import com.ddaeany0919.insightdeal.models.PriceHistoryPoint
import com.ddaeany0919.insightdeal.models.MallPrice
import java.util.Locale

import com.ddaeany0919.insightdeal.presentation.wishlist.WishlistViewModel
import com.ddaeany0919.insightdeal.presentation.wishlist.WishlistUiState
import com.ddaeany0919.insightdeal.network.ApiDealVotes

// [jetpack-compose-performance] 정규식, 날짜 포매터 및 정적 컬렉션 파일 레벨 호이스팅
private val DIGITAL_CATEGORIES = setOf("적립", "이벤트", "모바일/기프티콘", "상품권", "패키지/이용권")
private val SHIPPING_ZERO_SLASH_REGEX = Regex("^0(원)?\\s*(/|\\+).*")
private val SHIPPING_ZERO_PREFIX_REGEX = Regex("^0(원)?\\s*")
private val SHIPPING_NUMERIC_REGEX = Regex("^[0-9,]+$")
private val PRESET_DISCOUNT_PERCENTS = listOf(5, 10, 15)
private val PERIOD_OPTIONS = listOf(
    "7d" to "7일",
    "1m" to "1개월",
    "3m" to "3개월",
    "6m" to "6개월"
)
private val DETAIL_DATE_FORMATTER: java.time.format.DateTimeFormatter =
    java.time.format.DateTimeFormatter.ofPattern("yyyy.MM.dd HH:mm", Locale.getDefault())

/**
 * 사이트 칩의 메타 정보를 담는 고성능 정적 데이터 클래스
 */
@Immutable
private data class SiteChipInfo(
    val name: String,
    val bgColor: Color,
    val textColor: Color
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DealDetailRoute(
    dealId: Int,
    viewModel: DealDetailViewModel,
    wishlistViewModel: WishlistViewModel,
    onBack: () -> Unit,
    onOpenUrl: (String) -> Unit,
    onNavigateToCommunity: () -> Unit,
    onNavigateToWatchlist: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val wishlistState by wishlistViewModel.uiState.collectAsState()

    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    LaunchedEffect(dealId) {
        viewModel.loadDealData(dealId)
    }

    if (uiState.isLoading) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
        }
        return
    }

    if (uiState.error != null) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                text = uiState.error ?: "알 수 없는 에러가 발생했습니다.",
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.titleMedium
            )
        }
        return
    }

    val deal = uiState.deal
    if (deal == null) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("데이터를 찾을 수 없습니다.", style = MaterialTheme.typography.titleMedium)
        }
        return
    }

    val localContext = LocalContext.current
    LaunchedEffect(deal.id) {
        com.ddaeany0919.insightdeal.presentation.mypage.history.RecentDealManager.addRecentDeal(localContext, deal)
    }

    val currentPriceInt = deal.price.toInt()
    val targetUrl = deal.ecommerceUrl?.takeIf { it.isNotBlank() } ?: deal.postUrl ?: ""
    val existingWishlistItem = (wishlistState as? WishlistUiState.Success)?.items?.find { it.productUrl == targetUrl }
    val isBookmarked = existingWishlistItem != null

    // 다중 옵션(sources) 각각의 실제 가격(source.price), 구매처 링크(source.ecommerceUrl), 통화(source.currency) 반영
    val pricesList = remember(deal.sources, deal.siteName, deal.price, deal.currency, targetUrl) {
        deal.sources?.map { source ->
            val optionPrice = source.price?.toInt()?.takeIf { it > 0 } ?: currentPriceInt
            val optionUrl = source.ecommerceUrl?.takeIf { it.isNotBlank() } ?: source.postUrl
            val optionCurrency = source.currency?.takeIf { it.isNotBlank() } ?: deal.currency
            MallPrice(
                platform = source.siteName,
                price = optionPrice,
                url = optionUrl,
                currency = optionCurrency
            )
        }?.takeIf { it.isNotEmpty() } ?: listOf(
            MallPrice(platform = deal.siteName, price = currentPriceInt, url = targetUrl, currency = deal.currency)
        )
    }

    val selectedPeriod by viewModel.selectedPeriod.collectAsState()

    DealDetailScreen(
        deal = deal,
        priceHistory = uiState.priceHistory,
        mallPrices = pricesList,
        isBookmarked = isBookmarked,
        initialTargetPrice = existingWishlistItem?.targetPrice,
        votes = uiState.votes,
        selectedPeriod = selectedPeriod,
        onPeriodSelected = { viewModel.setPeriod(it) },
        onBack = onBack,
        onBookmarkToggle = {
            if (isBookmarked) {
                if (existingWishlistItem != null) {
                    wishlistViewModel.deleteItem(existingWishlistItem)
                }
            } else {
                wishlistViewModel.addItem(
                    keyword = deal.title,
                    productUrl = targetUrl,
                    targetPrice = deal.price.toInt(),
                    currentLowestPrice = deal.price.toInt(),
                    currentLowestPlatform = deal.siteName
                )
                scope.launch {
                    val result = snackbarHostState.showSnackbar(
                        message = "관심 상품에 등록되었습니다",
                        actionLabel = "찜 목록 보기",
                        duration = SnackbarDuration.Short
                    )
                    if (result == SnackbarResult.ActionPerformed) {
                        onNavigateToWatchlist()
                    }
                }
            }
        },
        onTargetPriceAlertRegister = { targetPrice ->
            if (existingWishlistItem != null) {
                wishlistViewModel.deleteItem(existingWishlistItem)
            }
            wishlistViewModel.addItem(
                keyword = deal.title,
                productUrl = targetUrl,
                targetPrice = targetPrice,
                currentLowestPrice = deal.price.toInt(),
                currentLowestPlatform = deal.siteName
            )
            scope.launch {
                val formattedTarget = formatPrice(targetPrice.toLong(), deal.currency)
                val result = snackbarHostState.showSnackbar(
                    message = "목표가($formattedTarget) 도달 시 푸시 알림을 보내드릴게요",
                    actionLabel = "찜 목록 보기",
                    duration = SnackbarDuration.Short
                )
                if (result == SnackbarResult.ActionPerformed) {
                    onNavigateToWatchlist()
                }
            }
        },
        onShare = {
            val formattedSharePrice = formatPrice(deal.price, deal.currency)
            val shareText = buildString {
                append("[인사이트딜 핫딜 공유]\n")
                append("${deal.title}\n")
                append("현재 핫딜가: $formattedSharePrice\n")
                if (targetUrl.isNotBlank()) append(targetUrl)
            }
            val sendIntent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(android.content.Intent.EXTRA_SUBJECT, deal.title)
                putExtra(android.content.Intent.EXTRA_TEXT, shareText)
            }
            localContext.startActivity(android.content.Intent.createChooser(sendIntent, "핫딜 공유하기"))
        },
        onOpenOrigin = onOpenUrl,
        onNavigateToCommunity = onNavigateToCommunity,
        snackbarHostState = snackbarHostState
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DealDetailScreen(
    deal: DealItem,
    priceHistory: List<PriceHistoryPoint>,
    mallPrices: List<MallPrice>,
    isBookmarked: Boolean,
    initialTargetPrice: Int? = null,
    votes: ApiDealVotes?,
    selectedPeriod: String,
    onPeriodSelected: (String) -> Unit,
    onBack: () -> Unit,
    onBookmarkToggle: () -> Unit,
    onTargetPriceAlertRegister: (Int) -> Unit = {},
    onShare: () -> Unit,
    onOpenOrigin: (String) -> Unit,
    onNavigateToCommunity: () -> Unit,
    snackbarHostState: SnackbarHostState
) {
    // 🛡️ 제휴 수수료 고지 BottomSheet 상태 관리
    var showAffiliateSheet by remember { mutableStateOf(false) }
    var pendingUrl by remember { mutableStateOf<String?>(null) }
    val affiliateSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val affiliateScope = rememberCoroutineScope()

    // 🛡️ 제휴 수수료 고지 ModalBottomSheet (전자상거래법 준수)
    if (showAffiliateSheet && pendingUrl != null) {
        ModalBottomSheet(
            onDismissRequest = {
                showAffiliateSheet = false
                pendingUrl = null
            },
            sheetState = affiliateSheetState,
            containerColor = MaterialTheme.colorScheme.surface,
            tonalElevation = 4.dp,
            shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // 아이콘 + 타이틀
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                    Text(
                        text = "외부 쇼핑몰 이동 안내",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                // 고지 본문
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "본 링크를 통해 상품을 구매하시면, 인사이트딜이 판매자로부터 소정의 제휴 수수료를 받을 수 있습니다.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            lineHeight = 22.sp
                        )
                        Text(
                            text = "이는 사용자의 구매 가격에 영향을 미치지 않으며, 서비스 운영 및 콘텐츠 품질 향상을 위해 사용됩니다.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            lineHeight = 20.sp
                        )
                    }
                }

                // CTA 버튼
                Button(
                    onClick = {
                        val url = pendingUrl
                        affiliateScope.launch {
                            affiliateSheetState.hide()
                        }.invokeOnCompletion {
                            showAffiliateSheet = false
                            pendingUrl = null
                            if (url != null) onOpenOrigin(url)
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary
                    )
                ) {
                    Icon(
                        imageVector = Icons.Default.ShoppingCart,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "쇼핑몰로 이동",
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleMedium,
                        color = Color.White
                    )
                }

                // 닫기 링크
                TextButton(
                    onClick = {
                        showAffiliateSheet = false
                        pendingUrl = null
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "취소",
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Spacer(Modifier.height(8.dp))
            }
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            val targetUrl = remember(deal, mallPrices) {
                deal.ecommerceUrl?.takeIf { it.isNotBlank() }
                    ?: mallPrices.firstOrNull()?.url
                    ?: deal.sources?.firstOrNull()?.postUrl
            }
            val originalPostUrl = remember(deal) {
                deal.postUrl?.takeIf { it.isNotBlank() }
                    ?: deal.sources?.firstOrNull()?.postUrl?.takeIf { it.isNotBlank() }
            }
            if (targetUrl != null || originalPostUrl != null) {
                androidx.compose.material3.Surface(
                    color = MaterialTheme.colorScheme.surface,
                    tonalElevation = 6.dp,
                    shadowElevation = 16.dp,
                    modifier = Modifier.navigationBarsPadding()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (originalPostUrl != null && originalPostUrl != targetUrl) {
                            OutlinedButton(
                                onClick = { onOpenOrigin(originalPostUrl) },
                                modifier = Modifier
                                    .weight(0.95f)
                                    .height(52.dp),
                                shape = RoundedCornerShape(14.dp),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                            ) {
                                Text(
                                    text = "원본 글 보기",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    letterSpacing = (-0.3).sp
                                )
                            }
                        }

                        if (targetUrl != null) {
                            Box(
                                modifier = Modifier
                                    .weight(1.35f)
                                    .height(52.dp)
                                    .background(
                                        brush = Brush.horizontalGradient(
                                            colors = listOf(
                                                Color(0xFFFF6B35),
                                                Color(0xFFFF3B30)
                                            )
                                        ),
                                        shape = RoundedCornerShape(14.dp)
                                    )
                                    .bounceClick {
                                        pendingUrl = targetUrl
                                        showAffiliateSheet = true
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.ShoppingCart,
                                        contentDescription = null,
                                        tint = Color.White,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(Modifier.width(7.dp))
                                    Text(
                                        text = "구매하러 가기",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 15.sp,
                                        color = Color.White,
                                        letterSpacing = (-0.3).sp
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = deal.title,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleMedium
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "뒤로가기",
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                },
                actions = {
                    IconButton(onClick = onShare) {
                        Icon(
                            imageVector = Icons.Default.Share,
                            contentDescription = "공유",
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    IconButton(onClick = onBookmarkToggle) {
                        Icon(
                            imageVector = if (isBookmarked) Icons.Default.Bookmark else Icons.Default.BookmarkBorder,
                            contentDescription = "북마크",
                            tint = if (isBookmarked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(padding),
            contentPadding = PaddingValues(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 88.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 고유한 key 및 contentType을 선언하여 스크롤 시 Compose 아이템의 재활용 성능을 극대화
            item(key = "deal_header", contentType = "header") {
                DealHeader(deal, onOpenOrigin)
            }
            item(key = "price_chart", contentType = "chart") {
                PriceHistoryInteractiveCard(
                    history = priceHistory,
                    currency = deal.currency,
                    selectedPeriod = selectedPeriod,
                    onPeriodSelected = onPeriodSelected
                )
            }
            item(key = "buyer_guide", contentType = "guide") {
                if (priceHistory.size > 1 || deal.honeyScore > 0) {
                    AIBuyerGuide(deal = deal, priceHistory = priceHistory, currentPrice = deal.price.toInt())
                }
            }
            item(key = "alert_register", contentType = "alert_btn") {
                val minPrice = remember(mallPrices, deal.price) {
                    mallPrices.minOfOrNull { it.price } ?: deal.price.toInt()
                }
                PriceAlertRegistrationButton(
                    currentPrice = minPrice,
                    currency = deal.currency,
                    titleHint = deal.title,
                    priceHistory = priceHistory,
                    initialTargetPrice = initialTargetPrice,
                    onAlertClick = onTargetPriceAlertRegister
                )
            }
            item(key = "community_gomin", contentType = "gomin_banner") {
                CommunityGominBanner(
                    votes = votes,
                    onClick = onNavigateToCommunity
                )
            }
            if (mallPrices.size > 1 && mallPrices.none { it.platform.contains(" - ") }) {
                item(key = "mall_prices", contentType = "price_table") {
                    MallPriceTable(mallPrices, onOpenOrigin)
                }
            }
            item(key = "affiliate_notice", contentType = "footer_notice") {
                Text(
                    text = "일부 쇼핑몰 이동 링크는 제휴 마케팅의 일환으로 일정액의 수수료를 제공받을 수 있습니다.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp, bottom = 12.dp)
                )
            }
        }
    }
}

@Composable
private fun CommunityGominBanner(
    votes: ApiDealVotes?,
    onClick: () -> Unit
) {
    val totalVotes = remember(votes) { (votes?.buy_count ?: 0) + (votes?.pass_count ?: 0) }
    val hasVotes = totalVotes > 0
    val buyRatio = remember(votes, totalVotes) {
        if (hasVotes) {
            ((votes!!.buy_count.toFloat() / totalVotes) * 100).toInt()
        } else {
            0
        }
    }

    val statusText = remember(votes, hasVotes, buyRatio, totalVotes) {
        if (hasVotes) {
            "지름 지수 $buyRatio% (${totalVotes}명 참여 · 살까 ${votes?.buy_count ?: 0} / 말까 ${votes?.pass_count ?: 0})"
        } else {
            "실구매 유저들의 투표를 확인하고 현명하게 결정하세요"
        }
    }

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = Modifier
            .fillMaxWidth()
            .bounceClick { onClick() }
    ) {
        Box(
            modifier = Modifier
                .background(
                    brush = Brush.horizontalGradient(
                        colors = listOf(Color(0xFFFF8E53), Color(0xFFFE6B8B))
                    )
                )
                .padding(16.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = Color.White.copy(alpha = 0.25f),
                    modifier = Modifier.size(40.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.Star,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "이 특가 핫딜, 살까 말까 고민되시나요?",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        letterSpacing = (-0.3).sp
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = statusText,
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White.copy(alpha = 0.9f),
                        letterSpacing = (-0.2).sp
                    )
                }
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(24.dp)
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PriceAlertRegistrationButton(
    currentPrice: Int,
    currency: String? = "KRW",
    titleHint: String? = null,
    priceHistory: List<PriceHistoryPoint> = emptyList(),
    initialTargetPrice: Int? = null,
    onAlertClick: (Int) -> Unit
) {
    if (currentPrice == 0) return
    val historyMinPrice = remember(priceHistory) {
        priceHistory.minOfOrNull { it.price }?.takeIf { it > 0 }
    }
    val historyAvgPrice = remember(priceHistory) {
        if (priceHistory.size >= 2) priceHistory.map { it.price }.average().toInt().takeIf { it > 0 } else null
    }
    val defaultTargetPrice = remember(currentPrice, historyMinPrice, initialTargetPrice) {
        when {
            initialTargetPrice != null && initialTargetPrice > 0 -> initialTargetPrice
            historyMinPrice != null && historyMinPrice < currentPrice -> historyMinPrice
            else -> (currentPrice * 0.90).toInt()
        }
    }
    var showDialog by remember { mutableStateOf(false) }
    var inputPrice by remember(defaultTargetPrice) { mutableStateOf(defaultTargetPrice.toString()) }
    var isConfigured by remember(initialTargetPrice) { mutableStateOf(initialTargetPrice != null && initialTargetPrice > 0) }
    var configuredPrice by remember(defaultTargetPrice) { mutableStateOf(defaultTargetPrice) }

    val formattedCurrentPrice = remember(currentPrice, currency, titleHint) {
        formatPrice(currentPrice.toLong(), currency, titleHint)
    }

    OutlinedButton(
        onClick = { showDialog = true },
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp)
            .bounceClick { showDialog = true },
        colors = ButtonDefaults.outlinedButtonColors(
            contentColor = if (isConfigured) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
        ),
        border = BorderStroke(
            width = 1.5.dp,
            color = if (isConfigured) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
        ),
        shape = RoundedCornerShape(14.dp)
    ) {
        Icon(
            imageVector = if (isConfigured) Icons.Default.CheckCircle else Icons.Default.Notifications,
            contentDescription = null,
            modifier = Modifier.size(20.dp),
            tint = if (isConfigured) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = if (isConfigured) "목표가 알림 설정 완료 (${formatPrice(configuredPrice.toLong(), currency, titleHint)})" else "목표가 도달 시 알림 설정",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            letterSpacing = (-0.3).sp
        )
    }

    if (showDialog) {
        val calculatedPrice = inputPrice.toIntOrNull() ?: 0
        val savedMoney = remember(currentPrice, calculatedPrice) {
            if (currentPrice > calculatedPrice && calculatedPrice > 0) {
                currentPrice - calculatedPrice
            } else {
                0
            }
        }
        val discountPercent = remember(currentPrice, calculatedPrice) {
            if (currentPrice > calculatedPrice && calculatedPrice > 0) {
                ((currentPrice - calculatedPrice).toFloat() / currentPrice * 100).toInt()
            } else {
                0
            }
        }

        AlertDialog(
            onDismissRequest = { showDialog = false },
            shape = RoundedCornerShape(24.dp),
            containerColor = MaterialTheme.colorScheme.surface,
            title = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Notifications,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(22.dp)
                    )
                    Text(
                        text = "알림 받을 목표가 설정",
                        fontWeight = FontWeight.ExtraBold,
                        style = MaterialTheme.typography.titleLarge,
                        letterSpacing = (-0.5).sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            },
            text = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = "설정한 가격 이하로 내려가면 바로 알려드려요.\n현재가 $formattedCurrentPrice",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        lineHeight = 20.sp,
                        letterSpacing = (-0.2).sp
                    )
                    Spacer(Modifier.height(14.dp))

                    // 시세 그래프 연동 원클릭 프리셋 (역대 최저가 / 평균 핫딜가 - 벡터 인디케이터 칩)
                    if (historyMinPrice != null || historyAvgPrice != null) {
                        Text(
                            text = "추천 목표가",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(Modifier.height(6.dp))
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            if (historyMinPrice != null) {
                                FilterChip(
                                    selected = (calculatedPrice == historyMinPrice),
                                    onClick = { inputPrice = historyMinPrice.toString() },
                                    leadingIcon = {
                                        Box(
                                            modifier = Modifier
                                                .size(7.dp)
                                                .background(Color(0xFF10B981), RoundedCornerShape(4.dp))
                                        )
                                    },
                                    label = {
                                        Text(
                                            text = "역대 최저 ${formatPrice(historyMinPrice.toLong(), currency, titleHint)}",
                                            fontSize = 11.5.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    },
                                    shape = RoundedCornerShape(8.dp),
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = Color(0xFF10B981).copy(alpha = 0.16f),
                                        selectedLabelColor = Color(0xFF10B981)
                                    )
                                )
                            }
                            if (historyAvgPrice != null && historyAvgPrice != historyMinPrice) {
                                FilterChip(
                                    selected = (calculatedPrice == historyAvgPrice),
                                    onClick = { inputPrice = historyAvgPrice.toString() },
                                    leadingIcon = {
                                        Box(
                                            modifier = Modifier
                                                .size(7.dp)
                                                .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(4.dp))
                                        )
                                    },
                                    label = {
                                        Text(
                                            text = "평균가 ${formatPrice(historyAvgPrice.toLong(), currency, titleHint)}",
                                            fontSize = 11.5.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    },
                                    shape = RoundedCornerShape(8.dp),
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                        selectedLabelColor = MaterialTheme.colorScheme.primary
                                    )
                                )
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                    }

                    // 퀵 제안 할인 프리셋 칩 (-5%, -10%, -15%)
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        PRESET_DISCOUNT_PERCENTS.forEach { percent ->
                            val presetPrice = (currentPrice * (100 - percent) / 100)
                            FilterChip(
                                selected = (calculatedPrice == presetPrice),
                                onClick = { inputPrice = presetPrice.toString() },
                                label = { Text("-$percent%") },
                                shape = RoundedCornerShape(8.dp),
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                    selectedLabelColor = MaterialTheme.colorScheme.primary
                                )
                            )
                        }
                    }
                    Spacer(Modifier.height(12.dp))

                    OutlinedTextField(
                        value = inputPrice,
                        onValueChange = { newValue ->
                            if (newValue.all { it.isDigit() }) inputPrice = newValue
                        },
                        label = { Text("목표 가격") },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = MaterialTheme.colorScheme.outline
                        ),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth(),
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                            keyboardType = androidx.compose.ui.text.input.KeyboardType.Number
                        )
                    )

                    if (savedMoney > 0) {
                        Spacer(Modifier.height(12.dp))
                        Card(
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.CheckCircle,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(16.dp)
                                )
                                Text(
                                    text = "현재가보다 ${formatPrice(savedMoney.toLong(), currency, titleHint)} ($discountPercent%) 낮을 때 알림을 보내드려요",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary,
                                    letterSpacing = (-0.3).sp
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val target = inputPrice.toIntOrNull() ?: 0
                        if (target > 0) {
                            configuredPrice = target
                            onAlertClick(target)
                            isConfigured = true
                        }
                        showDialog = false
                    },
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                ) {
                    Text("설정 완료", fontWeight = FontWeight.Bold, color = Color.White)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showDialog = false }
                ) {
                    Text("취소", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        )
    }
}

@Composable
private fun PriceHistoryInteractiveCard(
    history: List<PriceHistoryPoint>,
    currency: String = "KRW",
    selectedPeriod: String = "7d",
    onPeriodSelected: (String) -> Unit = {}
) {
    if (history.size <= 1) {
        val singlePoint = history.firstOrNull()
        Card(
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f)),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 18.dp, vertical = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "가격 변동 이력 없음",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                            letterSpacing = (-0.2).sp
                        )
                        androidx.compose.material3.Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant
                        ) {
                            Text(
                                text = if (singlePoint != null) "초기 1건" else "기록 없음",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "동일 상품의 이전 핫딜 기록이 없어 비교 차트를 표시하지 않습니다.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall,
                        lineHeight = 18.sp,
                        letterSpacing = (-0.1).sp
                    )
                }
                if (singlePoint != null && singlePoint.price > 0) {
                    Spacer(Modifier.width(12.dp))
                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            text = "최초 등록가",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = formatPrice(singlePoint.price.toLong(), currency),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        }
        return
    }

    Card(
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            // Header Row (핫딜 가격 변동 + 기간 선택 칩들)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "핫딜 가격 변동",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    letterSpacing = (-0.3).sp
                )

                // 기간 선택 칩들 (7일, 30일, 90일)
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    PERIOD_OPTIONS.forEach { (periodKey, label) ->
                        val isSelected = periodKey == selectedPeriod
                        val containerColor = if (isSelected) MaterialTheme.colorScheme.primary else androidx.compose.ui.graphics.Color.Transparent
                        val contentColor = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                        val borderStroke = if (isSelected) null else BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)

                        androidx.compose.material3.Surface(
                            onClick = { onPeriodSelected(periodKey) },
                            shape = RoundedCornerShape(8.dp),
                            color = containerColor,
                            contentColor = contentColor,
                            border = borderStroke,
                            modifier = Modifier.height(28.dp)
                        ) {
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier.padding(horizontal = 10.dp)
                            ) {
                                Text(
                                    text = label,
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                                )
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            run {
                // [jetpack-compose-performance] 리컴포지션 시 매번 리스트가 재할당되지 않도록 remember로 캐싱
                val chartPrices = remember(history) { history.map { it.price.toFloat() } }
                val chartDates = remember(history) { history.map { it.date } }
                val minPrice = remember(history) { history.minOfOrNull { it.price } ?: 0 }
                val maxPrice = remember(history) { history.maxOfOrNull { it.price } ?: 0 }
                val avgPrice = remember(history) {
                    if (history.isNotEmpty()) history.map { it.price }.average().toInt() else 0
                }
                val latestPrice = remember(history) { history.lastOrNull()?.price ?: 0 }
                val vsAvgPercent = remember(latestPrice, avgPrice) {
                    if (avgPrice > 0) (((latestPrice - avgPrice).toFloat() / avgPrice) * 100).toInt() else 0
                }

                // 핫구사(Hotgusa) 스타일 핵심 3대 가격 요약 지표 바 (역대 최저가 / 평균 핫딜가 / 최고가)
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.75f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 12.dp, horizontal = 8.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.weight(1f)) {
                            Text(
                                text = "역대 최저가",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = formatPrice(minPrice.toLong(), currency),
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.ExtraBold,
                                color = Color(0xFF10B981)
                            )
                        }
                        Box(
                            modifier = Modifier
                                .width(1.dp)
                                .height(28.dp)
                                .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
                        )
                        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.weight(1f)) {
                            Text(
                                text = "평균 핫딜가",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = formatPrice(avgPrice.toLong(), currency),
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                        Box(
                            modifier = Modifier
                                .width(1.dp)
                                .height(28.dp)
                                .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
                        )
                        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.weight(1f)) {
                            Text(
                                text = "기간 최고가",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = formatPrice(maxPrice.toLong(), currency),
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFFF59E0B)
                            )
                        }
                    }
                }

                if (maxPrice > minPrice) {
                    Spacer(modifier = Modifier.height(10.dp))
                    val posFraction = ((latestPrice - minPrice).toFloat() / (maxPrice - minPrice).toFloat()).coerceIn(0f, 1f)
                    val posPercent = (posFraction * 100f).toInt()
                    val pinColor = when {
                        posPercent <= 25 -> Color(0xFF10B981)
                        posPercent <= 60 -> MaterialTheme.colorScheme.primary
                        else -> Color(0xFFF59E0B)
                    }
                    Surface(
                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.55f),
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "최저가 구간",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF10B981)
                                )
                                Text(
                                    text = if (posPercent == 0) "현재 역대 최저가 (0%)" else "현재 가격 위치 하위 ${posPercent}%",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = pinColor
                                )
                                Text(
                                    text = "최고가 구간",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFFF59E0B)
                                )
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            BoxWithConstraints(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(14.dp),
                                contentAlignment = Alignment.CenterStart
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(6.dp)
                                        .background(
                                            brush = Brush.horizontalGradient(
                                                colors = listOf(
                                                    Color(0xFF10B981),
                                                    MaterialTheme.colorScheme.primary,
                                                    Color(0xFFF59E0B)
                                                )
                                            ),
                                            shape = RoundedCornerShape(999.dp)
                                        )
                                )
                                val offsetDp = (maxWidth - 12.dp) * posFraction
                                Surface(
                                    modifier = Modifier
                                        .offset(x = offsetDp)
                                        .size(12.dp),
                                    shape = RoundedCornerShape(999.dp),
                                    color = pinColor,
                                    border = BorderStroke(2.dp, MaterialTheme.colorScheme.surface)
                                ) {}
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // 현재가 위치 한줄 인사이트 배지 (이모지 대신 시맨틱 컬러 도트 인디케이터 적용)
                val insightBadgeText = remember(latestPrice, minPrice, vsAvgPercent, history.size) {
                    when {
                        latestPrice <= minPrice && maxPrice > minPrice ->
                            if (vsAvgPercent < 0) "최근 ${history.size}건 중 가장 낮은 가격이에요 · 평균보다 ${-vsAvgPercent}% 저렴"
                            else "최근 ${history.size}건 중 가장 낮은 가격이에요"
                        vsAvgPercent < 0 ->
                            "평균가보다 ${-vsAvgPercent}% 저렴해요 (${history.size}건 기준)"
                        vsAvgPercent > 0 ->
                            "평균가보다 ${vsAvgPercent}% 높아요 (${history.size}건 기준)"
                        else ->
                            "최근 ${history.size}건 가격이 일정하게 유지되고 있어요"
                    }
                }
                val isFavorablePrice = latestPrice <= minPrice || vsAvgPercent < 0
                val badgeAccentColor = if (isFavorablePrice) Color(0xFF10B981) else MaterialTheme.colorScheme.primary
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(7.dp)
                            .background(badgeAccentColor, RoundedCornerShape(4.dp))
                    )
                    Text(
                        text = insightBadgeText,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = badgeAccentColor,
                        letterSpacing = (-0.2).sp
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                Box(modifier = Modifier.fillMaxWidth().height(210.dp)) {
                    PriceChart(
                        prices = chartPrices,
                        dates = chartDates,
                        modifier = Modifier.fillMaxSize(),
                        currency = currency
                    )
                }
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "차트를 좌우로 움직여 날짜별 가격을 확인해 보세요",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    letterSpacing = (-0.1).sp
                )
            }
        }
    }
}

@Composable
private fun AIBuyerGuide(deal: DealItem, priceHistory: List<PriceHistoryPoint>, currentPrice: Int) {
    if (currentPrice == 0) return

    val currency = remember(deal.currency) { deal.currency }
    val minPrice = remember(priceHistory, currentPrice) { priceHistory.minOfOrNull { it.price } ?: currentPrice }
    val maxPrice = remember(priceHistory, currentPrice) { priceHistory.maxOfOrNull { it.price } ?: currentPrice }
    val isRecordLow = remember(currentPrice, minPrice, priceHistory) { currentPrice <= minPrice && priceHistory.size > 1 }

    val isGoodDeal = deal.honeyScore >= 80

    // 다크모드/라이트모드에 완벽 반응하는 프리미엄 테마 컬러 동적 맵핑
    val isDark = MaterialTheme.colorScheme.background.red < 0.2f

    val titleColor = remember(isRecordLow, isGoodDeal, isDark) {
        if (isRecordLow || isGoodDeal) {
            if (isDark) Color(0xFF4ADE80) else Color(0xFF166534)
        } else {
            if (isDark) Color(0xFF94A3B8) else Color(0xFF334155)
        }
    }
    val containerColor = remember(isRecordLow, isGoodDeal, isDark) {
        if (isRecordLow || isGoodDeal) {
            if (isDark) Color(0xFF064E3B).copy(alpha = 0.3f) else Color(0xFFF0FDF4)
        } else {
            if (isDark) Color(0xFF1E293B).copy(alpha = 0.3f) else Color(0xFFF8FAFC)
        }
    }
    val borderColor = remember(isRecordLow, isGoodDeal, isDark) {
        if (isRecordLow || isGoodDeal) {
            if (isDark) Color(0xFF059669) else Color(0xFF4ADE80)
        } else {
            if (isDark) Color(0xFF334155) else Color(0xFFE2E8F0)
        }
    }

    // 통화별 가격 차이 포매팅 헬퍼 함수
    val formatDiff = remember(currency) {
        { diffVal: Int ->
            val safeCurr = currency.uppercase(Locale.getDefault())
            when (safeCurr) {
                "USD" -> String.format(Locale.US, "$%.2f", diffVal / 100.0)
                "EUR" -> String.format(Locale.US, "€%.2f", diffVal / 100.0)
                else -> String.format(Locale.getDefault(), "%,d원", diffVal)
            }
        }
    }

    Card(
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = containerColor),
        border = BorderStroke(1.dp, borderColor),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(20.dp).fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = if (isRecordLow || isGoodDeal) {
                        if (isDark) Color(0xFF065F46) else Color(0xFFDCFCE7)
                    } else {
                        if (isDark) Color(0xFF334155) else Color(0xFFE2E8F0)
                    },
                    modifier = Modifier.size(38.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.AutoAwesome,
                            contentDescription = "AI",
                            tint = titleColor,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
                Spacer(Modifier.width(12.dp))
                Column {
                    Text(
                        text = "시세 요약",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.ExtraBold,
                        color = titleColor,
                        letterSpacing = (-0.3).sp
                    )
                    Text(
                        text = if (isRecordLow || isGoodDeal) "지금 구매하기 좋은 가격이에요" else "목표가 알림 설정을 추천해요",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (isRecordLow || isGoodDeal) Color(0xFF22C55E) else Color(0xFF64748B),
                        fontWeight = FontWeight.Bold,
                        letterSpacing = (-0.2).sp
                    )
                }
            }
            Spacer(Modifier.height(16.dp))
            HorizontalDivider(color = borderColor.copy(alpha = 0.5f))
            Spacer(Modifier.height(16.dp))

            if (priceHistory.size <= 1) {
                Text(
                    text = "커뮤니티 추천도 ${deal.honeyScore}점",
                    style = MaterialTheme.typography.titleMedium,
                    color = if (isGoodDeal) {
                        if (isDark) Color(0xFF34D399) else Color(0xFF059669)
                    } else {
                        if (isDark) Color(0xFFE2E8F0) else Color(0xFF334155)
                    },
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = (-0.3).sp
                )
                Spacer(Modifier.height(8.dp))
                val message = remember(isGoodDeal) {
                    if (isGoodDeal) {
                        "비슷한 상품의 최근 핫딜가보다 저렴한 편이에요. 품절되기 전에 조건을 확인해 보세요."
                    } else {
                        "최근 올라온 비슷한 상품과 가격대가 비슷해요. 급하지 않다면 목표가 알림을 켜두고 기다려 보세요."
                    }
                }
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (isDark) Color(0xFFCBD5E1) else Color(0xFF334155),
                    lineHeight = 22.sp,
                    letterSpacing = (-0.2).sp
                )
            } else if (isRecordLow) {
                Text(
                    text = "최근 핫딜 중 가장 낮은 가격",
                    style = MaterialTheme.typography.titleMedium,
                    color = if (isDark) Color(0xFFF87171) else Color(0xFFDC2626),
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = (-0.3).sp
                )
                Spacer(Modifier.height(8.dp))
                val diffFromMax = maxPrice - currentPrice
                val formattedDiff = remember(diffFromMax) { formatDiff(diffFromMax) }
                Text(
                    text = "이전 최고가보다 약 ${formattedDiff} 저렴한 역대 최저가예요. 구매를 고민 중이었다면 좋은 타이밍이에요.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (isDark) Color(0xFFCBD5E1) else Color(0xFF334155),
                    lineHeight = 22.sp,
                    letterSpacing = (-0.2).sp
                )
            } else {
                val prevPrice = remember(priceHistory) {
                    if (priceHistory.size >= 2) priceHistory[priceHistory.size - 2].price else currentPrice
                }
                val diffFromPrev = currentPrice - prevPrice
                val diffFromMin = currentPrice - minPrice

                val formattedDiffPrev = remember(diffFromPrev) { formatDiff(Math.abs(diffFromPrev)) }
                val formattedDiffMin = remember(diffFromMin) { formatDiff(diffFromMin) }

                val dynamicMessage = remember(diffFromPrev, diffFromMin, formattedDiffPrev, formattedDiffMin) {
                    when {
                        diffFromPrev > 0 -> {
                            "직전 핫딜보다 ${formattedDiffPrev} 올랐고, 역대 최저가보다는 ${formattedDiffMin} 높아요. 급하지 않다면 목표가 알림을 설정해 보세요."
                        }
                        diffFromPrev < 0 -> {
                            "직전 핫딜보다 ${formattedDiffPrev} 내려왔어요. 역대 최저가와 ${formattedDiffMin} 차이여서 조건이 괜찮은 편이에요."
                        }
                        else -> {
                            "직전 핫딜과 같은 가격을 유지하고 있어요 (역대 최저가 대비 +${formattedDiffMin}). 목표가 알림을 켜두면 더 내려갔을 때 바로 알려드려요."
                        }
                    }
                }

                Text(
                    text = "이전 핫딜가와 비교해 봤어요",
                    style = MaterialTheme.typography.titleMedium,
                    color = if (isDark) Color(0xFF60A5FA) else Color(0xFF2563EB),
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = (-0.3).sp
                )
                Spacer(Modifier.height(8.dp))
                Text(
                     text = dynamicMessage,
                     style = MaterialTheme.typography.bodyMedium,
                     color = if (isDark) Color(0xFFCBD5E1) else Color(0xFF334155),
                     lineHeight = 22.sp,
                     letterSpacing = (-0.2).sp
                )
            }
        }
    }
}

@Composable
private fun MallPriceTable(mallPrices: List<MallPrice>, onOpenOrigin: (String) -> Unit) {
    val isMultiOption = remember(mallPrices) {
        mallPrices.size > 1 && mallPrices.any { it.platform.contains(" - ") }
    }
    Card(
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(20.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text(
                text = if (isMultiOption) "상품 옵션별 가격 및 바로가기" else "웹 판매처 최저가 비교",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                letterSpacing = (-0.3).sp
            )
            Spacer(Modifier.height(14.dp))

            mallPrices.forEachIndexed { index, row ->
                val priceText = remember(row.price, row.currency) {
                    when (row.currency?.uppercase()) {
                        "USD" -> "$${String.format(Locale.US, "%.2f", row.price.toDouble() / 100)}"
                        "EUR" -> "€${String.format(Locale.US, "%.2f", row.price.toDouble() / 100)}"
                        else -> "${formatPrice(row.price.toLong(), row.currency ?: "KRW")}"
                    }
                }
                val displayPlatform = remember(row.platform) {
                    if (row.platform.contains(" - ")) {
                        row.platform.substringAfter(" - ").trim()
                    } else {
                        row.platform
                    }
                }
                val subCaption = remember(row.platform) {
                    if (row.platform.contains(" - ")) {
                        "${row.platform.substringBefore(" - ").trim()} · 옵션 구매 링크"
                    } else {
                        "실시간 매칭가"
                    }
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 10.dp)
                        .bounceClick { onOpenOrigin(row.url) },
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                        Text(
                            text = displayPlatform,
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = subCaption,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                        )
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = priceText,
                            fontWeight = FontWeight.ExtraBold,
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.primary,
                            letterSpacing = (-0.2).sp
                        )

                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }

                if (index < mallPrices.size - 1) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                }
            }
        }
    }
}

@Composable
fun DealHeader(deal: DealItem, onOpenOrigin: (String) -> Unit) {
    val context = LocalContext.current
    Column(modifier = Modifier.fillMaxWidth()) {
        if (!deal.imageUrl.isNullOrEmpty()) {
            val proxiedHeaderUrl = remember(deal.id, deal.imageUrl) {
                val rawUrl = deal.imageUrl
                if (rawUrl.isNullOrBlank()) null
                else if (rawUrl.startsWith("http")) {
                    try {
                        val baseUrl = com.ddaeany0919.insightdeal.data.network.NetworkConfig.getActiveServerUrl().removeSuffix("/")
                        "$baseUrl/api/proxy-image?url=${java.net.URLEncoder.encode(rawUrl, "UTF-8")}&v=5"
                    } catch (e: Exception) {
                        rawUrl
                    }
                } else rawUrl
            }
            var imageLoadFailed by remember(proxiedHeaderUrl) { mutableStateOf(false) }
            val headerImageRequest = remember(proxiedHeaderUrl, deal.postUrl) {
                coil.request.ImageRequest.Builder(context)
                    .data(proxiedHeaderUrl)
                    .setHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) Chrome/120.0.0.0 Safari/537.36")
                    .setHeader("Referer", deal.postUrl ?: "https://insightdeal.com/")
                    .crossfade(true)
                    .precision(coil.size.Precision.EXACT)
                    .build()
            }
            if (!imageLoadFailed && !proxiedHeaderUrl.isNullOrBlank()) {
                Card(
                    shape = RoundedCornerShape(20.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            val targetUrl = deal.ecommerceUrl?.takeIf { it.isNotBlank() } ?: deal.postUrl ?: ""
                            if (targetUrl.isNotBlank()) {
                                onOpenOrigin(targetUrl)
                            }
                        }
                ) {
                    AsyncImage(
                        model = headerImageRequest,
                        contentDescription = deal.title,
                        onError = { imageLoadFailed = true },
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 120.dp, max = 280.dp)
                            .background(Color.White),
                        contentScale = ContentScale.Fit,
                        alpha = 1.0f
                    )
                }
            }
        }

        Column(modifier = Modifier.padding(vertical = 16.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                // 고성능 렌더링 최적화: 사이트 이름 매칭 칩에 필요한 색상 매핑 연산을 remember 블록 내부에서 1회만 계산하도록 최적화
                val surfaceVariant = MaterialTheme.colorScheme.surfaceVariant
                val onSurfaceVariant = MaterialTheme.colorScheme.onSurfaceVariant

                val siteChips = remember(deal.siteNames, deal.siteName, surfaceVariant, onSurfaceVariant) {
                    val rawList = if (deal.siteNames.isNotEmpty()) deal.siteNames else deal.siteName.split(",").map { it.trim() }
                    val distinctRawList = rawList.map { sName ->
                        if (sName.contains(" - ")) {
                            sName.substringBefore(" - ").trim()
                        } else {
                            sName.trim()
                        }
                    }.distinct()

                    distinctRawList.map { sName ->
                        val siteNameLower = sName.lowercase(Locale.getDefault())
                        val (bgColor, textColor) = when {
                            siteNameLower.contains("뽐뿌") -> Color(0xFF1565C0) to Color.White
                            siteNameLower.contains("퀘이사존") -> Color(0xFFE65100) to Color.White
                            siteNameLower.contains("루리웹") -> Color(0xFF0D47A1) to Color.White
                            siteNameLower.contains("펨코") || siteNameLower.contains("에펨코리아") -> Color(0xFF0288D1) to Color.White
                            siteNameLower.contains("빠삭") -> Color(0xFFC2185B) to Color.White
                            siteNameLower.contains("클리앙") -> Color(0xFF37474F) to Color.White
                            else -> surfaceVariant to onSurfaceVariant
                        }
                        SiteChipInfo(name = sName, bgColor = bgColor, textColor = textColor)
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    siteChips.forEach { chip ->
                        Text(
                            text = chip.name,
                            style = MaterialTheme.typography.labelMedium,
                            color = chip.textColor,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier
                                .background(chip.bgColor, RoundedCornerShape(6.dp))
                                .padding(horizontal = 10.dp, vertical = 5.dp)
                        )
                    }
                }

                // 배송비 관련 로직도 remember {} 블록 내부로 전면 격리하여 1회만 실행하도록 최적화
                val displayShippingInfo = remember(deal.shippingFee, deal.category, deal.title) {
                    val isDigitalTitle = deal.title.contains("요금제") || deal.title.contains("데이터")
                    val hideShipping = deal.category in DIGITAL_CATEGORIES || isDigitalTitle

                    if (hideShipping) {
                        null
                    } else {
                        val trimmed = deal.shippingFee?.trim() ?: "정보 없음"
                        when {
                            trimmed == "정보 없음" || trimmed.isEmpty() || trimmed == "배송비 확인 필요" -> "확인 필요"
                            trimmed == "0" || trimmed == "0원" || trimmed == "무배" || trimmed == "무료" || trimmed == "무료배송" -> "무료"
                            trimmed.matches(SHIPPING_ZERO_SLASH_REGEX) -> trimmed.replace(SHIPPING_ZERO_PREFIX_REGEX, "무료 ")
                            trimmed == "유료" || trimmed == "유료배송" -> "유료"
                            trimmed.matches(SHIPPING_NUMERIC_REGEX) -> "${trimmed}원"
                            else -> trimmed.replace("무료배송", "무료").replace("유료배송", "유료")
                        }
                    }
                }

                if (displayShippingInfo != null) {
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        shape = RoundedCornerShape(6.dp),
                        modifier = Modifier.padding(start = 8.dp)
                    ) {
                        Text(
                            text = "배송비: $displayShippingInfo",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            Text(
                text = deal.title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.ExtraBold,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
                lineHeight = 28.sp,
                letterSpacing = (-0.5).sp,
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(modifier = Modifier.height(16.dp))

            Row(verticalAlignment = Alignment.Bottom) {
                if (deal.discountRate != null && deal.discountRate > 0) {
                    Text(
                        text = "${deal.discountRate}%",
                        style = MaterialTheme.typography.headlineLarge,
                        color = MaterialTheme.colorScheme.error,
                        fontWeight = FontWeight.Black,
                        letterSpacing = (-0.5).sp
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                }

                val priceText = remember(deal.price, deal.currency, deal.title) { formatPrice(deal.price, deal.currency, deal.title) }
                Text(
                    text = priceText,
                    style = if (priceText == "본문 참조") MaterialTheme.typography.titleLarge else MaterialTheme.typography.headlineLarge,
                    fontWeight = FontWeight.Black,
                    color = if (priceText == "본문 참조") MaterialTheme.colorScheme.onSurfaceVariant else if (MaterialTheme.colorScheme.background.red < 0.2f) Color(0xFFFF7A45) else Color(0xFFFF5A26),
                    letterSpacing = (-0.5).sp
                )

                if (priceText == "본문 참조") {
                    Spacer(modifier = Modifier.width(6.dp))
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        modifier = Modifier.size(20.dp).padding(bottom = 2.dp)
                    )
                }
            }

            // ⌚ 등록 일시 & 실시간 조회수 메타데이터 바 (Premium HSL Gray Typography & Dot Divider)
            Spacer(modifier = Modifier.height(10.dp))
            val absoluteTimeText = remember(deal.createdAt) {
                if (deal.createdAt.isNullOrEmpty()) return@remember ""
                try {
                    val parsedTime = try {
                        java.time.OffsetDateTime.parse(deal.createdAt)
                    } catch (e: Exception) {
                        val local = java.time.LocalDateTime.parse(deal.createdAt)
                        local.atZone(java.time.ZoneId.systemDefault()).toOffsetDateTime()
                    }
                    parsedTime.format(DETAIL_DATE_FORMATTER)
                } catch (e: Exception) {
                    ""
                }
            }
            val relativeTime = rememberRelativeTime(deal.createdAt)
            val timeDisplayText = remember(relativeTime, absoluteTimeText) {
                when {
                    relativeTime.isNotEmpty() && absoluteTimeText.isNotEmpty() -> "$relativeTime ($absoluteTimeText)"
                    relativeTime.isNotEmpty() -> relativeTime
                    absoluteTimeText.isNotEmpty() -> absoluteTimeText
                    else -> "등록일 정보 없음"
                }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "등록 $timeDisplayText",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    fontWeight = FontWeight.Medium
                )

                Text(
                    text = "•",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                )

                Text(
                    text = "조회 ${deal.viewCount}회",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    fontWeight = FontWeight.Medium
                )
            }

            val multiOptions = remember(deal.id, deal.sources) {
                deal.sources?.filter { it.siteName.contains(" - ") && (it.price ?: 0L) > 0L } ?: emptyList()
            }

            if (multiOptions.size > 1) {
                Spacer(modifier = Modifier.height(16.dp))
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)),
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                    elevation = CardDefaults.cardElevation(0.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "구성 상품 선택 및 바로가기 (${multiOptions.size}종)",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        multiOptions.forEach { opt ->
                            val optName = opt.siteName.substringAfter(" - ").trim()
                            val optPriceText = formatPrice(opt.price ?: deal.price, opt.currency ?: deal.currency, optName)
                            val optTargetUrl = opt.ecommerceUrl?.takeIf { it.isNotBlank() } ?: opt.postUrl
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = MaterialTheme.colorScheme.surface,
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        if (optTargetUrl.isNotBlank()) {
                                            val finalOptUrl = if (optTargetUrl.startsWith("http")) optTargetUrl else "https://$optTargetUrl"
                                            onOpenOrigin(finalOptUrl)
                                        }
                                    }
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = optName,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f).padding(end = 10.dp)
                                    )
                                    Text(
                                        text = "$optPriceText ➔",
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        }
                    }
                }
            }

            val cleanAiSummary = remember(deal.aiSummary) {
                deal.aiSummary
                    ?.replace(Regex("[🔥✨🤖💡⚡🎉📦]+"), "")
                    ?.replace(Regex("\\s{2,}"), " ")
                    ?.trim()
                    .orEmpty()
            }
            if (cleanAiSummary.isNotEmpty() && cleanAiSummary != "[커뮤니티 인증 핫딜]" && !(multiOptions.size > 1 && cleanAiSummary.contains("[구성 상품"))) {
                Spacer(modifier = Modifier.height(16.dp))
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)),
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                    elevation = CardDefaults.cardElevation(0.dp)
                ) {
                    Row(modifier = Modifier.padding(14.dp), verticalAlignment = Alignment.Top) {
                        Text(
                            text = cleanAiSummary,
                            style = MaterialTheme.typography.bodyMedium,
                            lineHeight = 21.sp,
                            color = MaterialTheme.colorScheme.onSurface,
                            letterSpacing = (-0.2).sp
                        )
                    }
                }
            }

            val cleanedBodyText = remember(deal.contentHtml, deal.title) {
                val raw = deal.contentHtml.orEmpty()
                if (raw.isBlank()) ""
                else {
                    val step1 = raw
                        .replace(Regex("<br\\s*/?>", RegexOption.IGNORE_CASE), "\n")
                        .replace(Regex("</(p|div|li)>", RegexOption.IGNORE_CASE), "\n")
                        .replace(Regex("\\(\\s*링크\\s*:\\s*https?://[^)]+\\)", RegexOption.IGNORE_CASE), "")
                        .replace(Regex("\\[첨부된 이미지 링크들\\]\\s*:?[\\s\\S]*$", RegexOption.IGNORE_CASE), "")
                        .replace(Regex("Your browser does not support the video tag\\.?", RegexOption.IGNORE_CASE), "")
                        .replace(Regex("<[^>]+>"), " ")
                        .replace("&nbsp;", " ")
                        .replace("&amp;", "&")
                        .replace("&lt;", "<")
                        .replace("&gt;", ">")
                        .replace("&quot;", "\"")
                        .replace("&#039;", "'")
                        .replace("&#39;", "'")
                        .replace(Regex("https?://\\S+"), "")
                    val lines = step1.lines().map { it.replace(Regex("\\s+"), " ").trim() }
                    val validLines = mutableListOf<String>()
                    for (ln in lines) {
                        if (ln.isEmpty()) continue
                        if (ln.matches(Regex("^[\\-=*_~.,:;()\\[\\]/\\\\]+$"))) continue
                        if (ln.matches(Regex("^(?:링크|제품\\s*바로가기|상품\\s*바로가기|구매\\s*링크)\\s*>{2,}.*$", RegexOption.IGNORE_CASE))) continue
                        if (ln.matches(Regex("^>{3,}.*$"))) continue
                        if (ln == "보기>" || ln == "보기 >" || ln == "바로가기>" || ln == "바로가기 >") continue
                        val cleanLn = ln.replace(Regex(",\\s*,+"), ", ").replace(Regex(",\\s*$"), "").trim()
                        if (cleanLn.isEmpty()) continue
                        val isCodeOnly = cleanLn.matches(Regex("^[A-Z0-9]{4,12}(?:\\s*,\\s*[A-Z0-9]{4,12})*$", RegexOption.IGNORE_CASE)) &&
                            cleanLn.contains(Regex("[A-Za-z]")) && cleanLn.contains(Regex("[0-9]"))
                        if (isCodeOnly && validLines.isNotEmpty()) {
                            val prev = validLines.last()
                            if (prev.contains(Regex("(?:코드|쿠폰|할인)")) || prev.contains(Regex("[A-Z0-9]{4,12}$", RegexOption.IGNORE_CASE))) {
                                validLines[validLines.lastIndex] = if (prev.endsWith(":") || prev.endsWith(",")) "$prev $cleanLn" else "$prev ($cleanLn)"
                                continue
                            }
                        }
                        validLines.add(cleanLn)
                    }
                    val formattedLines = mutableListOf<String>()
                    for (i in validLines.indices) {
                        val ln = validLines[i]
                        formattedLines.add(ln)
                        if (ln.matches(Regex("^(?:최종가|최종\\s*체감가|최대\\s*혜택가|최대\\s*할인가)\\b.*$")) && i < validLines.lastIndex) {
                            formattedLines.add("────────────────────────")
                        }
                    }
                    val joined = formattedLines.joinToString("\n").trim()
                    if (joined.length < 4 || joined.replace(" ", "") == deal.title.replace(" ", "")) "" else joined
                }
            }

            val promoCodes = remember(cleanedBodyText) {
                if (cleanedBodyText.isBlank()) emptyList()
                else {
                    val blacklist = setOf(
                        "HTTP", "HTTPS", "HTML", "UTF8", "ALLDOCUBE", "BIGME", "VIOFO", "VIOFEO",
                        "JONR", "UWANT", "SHOKZ", "IPLAY", "MINI", "PRO", "MAX", "PLUS", "ULTRA",
                        "WIFI", "LTE", "NVME", "DDR4", "DDR5", "HDMI", "USBC", "TYPEC", "PD100W"
                    )
                    val found = linkedSetOf<String>()
                    val codeRegex = Regex("\\b[A-Z]{2,8}[0-9]{2,6}[A-Z0-9]{0,4}\\b")
                    for (line in cleanedBodyText.lines()) {
                        val hasContext = line.contains(Regex("(?:코드|쿠폰|프로모션|할인|소진시|적용)")) || line.contains(",")
                        for (m in codeRegex.findAll(line)) {
                            val up = m.value.uppercase()
                            if (up !in blacklist && (up.length >= 6 || (up.length >= 5 && hasContext))) {
                                found.add(up)
                            }
                        }
                    }
                    found.take(8).toList()
                }
            }

            if (cleanedBodyText.isNotEmpty()) {
                Spacer(modifier = Modifier.height(20.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "상품 안내 요약",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    if (promoCodes.isNotEmpty()) {
                        Text(
                            text = "할인코드 터치 시 복사",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
                if (promoCodes.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    androidx.compose.foundation.lazy.LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        items(promoCodes) { code ->
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)),
                                modifier = Modifier.clickable {
                                    val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as? android.content.ClipboardManager
                                    clipboard?.setPrimaryClip(android.content.ClipData.newPlainText("promo_code", code))
                                    android.widget.Toast.makeText(context, "할인코드 '$code' 복사 완료", android.widget.Toast.LENGTH_SHORT).show()
                                }
                            ) {
                                Text(
                                    text = "📋 $code",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                )
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                Card(
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f)),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
                    elevation = CardDefaults.cardElevation(0.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = cleanedBodyText,
                        style = MaterialTheme.typography.bodyMedium.copy(
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            lineHeight = 22.sp,
                            letterSpacing = (-0.1).sp
                        ),
                        modifier = Modifier.padding(16.dp)
                    )
                }
            }
        }
    }
}

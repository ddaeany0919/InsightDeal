package com.ddaeany0919.insightdeal.presentation

import com.ddaeany0919.insightdeal.presentation.formatPrice
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import com.ddaeany0919.insightdeal.presentation.home.BrandPlaceholder
import com.ddaeany0919.insightdeal.presentation.theme.LocalInsightDealColors
import com.ddaeany0919.insightdeal.presentation.theme.LocalIsDarkTheme
import com.ddaeany0919.insightdeal.presentation.theme.PriceTextStyle
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.ShowChart
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Poll
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.ThumbDown
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.ddaeany0919.insightdeal.models.DealItem
import com.ddaeany0919.insightdeal.models.DealSource
import com.ddaeany0919.insightdeal.models.MallPrice
import com.ddaeany0919.insightdeal.models.PriceHistoryPoint
import com.ddaeany0919.insightdeal.network.ApiDealComment
import com.ddaeany0919.insightdeal.network.ApiDealVotes
import com.ddaeany0919.insightdeal.presentation.wishlist.WishlistUiState
import com.ddaeany0919.insightdeal.presentation.wishlist.WishlistViewModel
import com.ddaeany0919.insightdeal.utils.CustomTabLauncher
import java.util.Locale

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
private val DETAIL_CLOSED_COLOR_FILTER by lazy {
    ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(0f) })
}

// [현안 4] 옵션명 다중 중첩 접두사("알리검색 - ", "네이버검색 - ", "옵션 - " 등) 정제용 정규식
private val OPTION_PREFIX_REGEX = Regex(
    "^(?:옵션|알리검색|네이버검색|다나와검색|쿠팡검색|알리뽐뿌|뽐뿌해외|뽐뿌|클리앙|루리웹|퀘이사존|에펨코리아|빠삭|알리|네이버)\\s*[-:·]\\s*",
    RegexOption.IGNORE_CASE
)
private val OPTION_LEADING_NUM_REGEX = Regex("^(?:\\d{1,2}\\s*[.)](?!\\d)\\s+|[\\u2460-\\u2473]\\s*)")
private val OPTION_LEADING_DISCOUNT_REGEX = Regex("^\\d{1,2}(?:\\.\\d+)?%\\s*[/·\\-|]\\s*")
private val OPTION_UNMATCHED_PREFIX_REGEX = Regex("^[^\\[\\]()]{1,14}[\\])]\\s*")
private val OPTION_TRAILING_UNCLOSED_REGEX = Regex("\\s*[(\\[][^)\\]]*$")

fun cleanOptionTitle(rawName: String): String {
    if (rawName.isBlank()) return ""
    val decoded = decodeHtmlEntities(rawName)
    var name = decoded.replace('\u00A0', ' ').let {
        if (it.contains(" - ")) it.substringAfter(" - ").trim() else it.trim()
    }
    while (OPTION_PREFIX_REGEX.containsMatchIn(name)) {
        name = name.replaceFirst(OPTION_PREFIX_REGEX, "").trim()
    }
    name = name.replaceFirst(OPTION_LEADING_NUM_REGEX, "").trim()
    name = name.replaceFirst(OPTION_LEADING_DISCOUNT_REGEX, "").trim()
    name = name.replaceFirst(OPTION_UNMATCHED_PREFIX_REGEX, "").trim()
    name = name.replaceFirst(OPTION_TRAILING_UNCLOSED_REGEX, "").trim()
    return name.ifBlank { decoded.trim() }
}

internal fun formatEditableTargetInput(minorPrice: Int, isSubunitCurrency: Boolean): String {
    if (minorPrice <= 0) return ""
    return if (isSubunitCurrency) {
        if (minorPrice % 100 == 0) {
            (minorPrice / 100).toString()
        } else {
            String.format(Locale.US, "%.2f", minorPrice / 100.0).trimEnd('0').trimEnd('.')
        }
    } else {
        minorPrice.toString()
    }
}

internal fun parseEditableTargetInput(input: String, isSubunitCurrency: Boolean): Int {
    val trimmed = input.trim()
    if (trimmed.isEmpty()) return 0
    return if (isSubunitCurrency) {
        val major = trimmed.toDoubleOrNull() ?: return 0
        kotlin.math.round(major * 100.0).toInt().coerceAtLeast(0)
    } else {
        trimmed.toIntOrNull()?.coerceAtLeast(0) ?: 0
    }
}

// [현안 4] 핫딜 본문/제목 내 할인 프로모션 코드 추출 헬퍼
private val PROMO_BLACKLIST = setOf(
    "HTTP", "HTTPS", "HTML", "UTF8", "ALLDOCUBE", "BIGME", "VIOFO", "VIOFEO",
    "JONR", "UWANT", "SHOKZ", "IPLAY", "MINI", "PRO", "MAX", "PLUS", "ULTRA",
    "WIFI", "LTE", "NVME", "DDR4", "DDR5", "HDMI", "USBC", "TYPEC", "PD100W",
    "PAW3395", "5000IU", "10000MAH", "20000MAH", "PD20W", "IN1200T", "CB3000",
    "11STREET", "27U411B", "27G411B", "LS32FG810", "S32FG500", "S32FG50", "32U701SB"
)
private val PROMO_CODE_REGEX = Regex("\\b[A-Z0-9]{5,14}\\b")
private val PROMO_UNIT_REGEX = Regex("^[0-9]+(?:GB|TB|MB|MAH|W|HZ|INCH|PACK|P|G|ML|KG|IU)$", RegexOption.IGNORE_CASE)
private val PROMO_MODEL_REGEX = Regex("^(?:[0-9]{2}[A-Z]{1,3}[0-9]{3,4}[A-Z]{0,2}|LS[0-9]{2}[A-Z]{2}[0-9]{3}|RQ[0-9]{2}[A-Z0-9]{5,})$", RegexOption.IGNORE_CASE)
private val PROMO_STRICT_REGEX = Regex("^[A-Z]{3,8}[0-9]{2,6}$")

fun extractPromoCodesFromText(text: String, title: String): List<String> {
    if (text.isBlank()) return emptyList()
    val titleTokens = title.uppercase()
        .replace(Regex("[^A-Z0-9]"), " ")
        .split(Regex("\\s+"))
        .filter { it.length >= 4 }
        .toSet()
    val found = linkedSetOf<String>()
    for (line in text.lines()) {
        val hasExplicitCodeLabel = line.contains(Regex("(?:공구\\s*코드|할인\\s*코드|프로모션\\s*코드|쿠폰\\s*코드|소진시\\s*쿠폰|코드\\s*할인|코드\\s*:|코드\\s*\\$|🎟|Ⓐ|Ⓑ)"))
        val isCodeListLine = line.trim().matches(Regex("^[A-Z0-9,\\s()/\\$-]{5,80}$", RegexOption.IGNORE_CASE)) && line.contains(",")
        for (m in PROMO_CODE_REGEX.findAll(line)) {
            val up = m.value.uppercase()
            if (up in PROMO_BLACKLIST) continue
            if (up in titleTokens || titleTokens.any { it.contains(up) || up.contains(it) }) continue
            if (!up.contains(Regex("[A-Z]")) || !up.contains(Regex("[0-9]"))) continue
            if (up.matches(PROMO_UNIT_REGEX) || up.matches(PROMO_MODEL_REGEX)) continue
            if (((hasExplicitCodeLabel || isCodeListLine) && up.length >= 5) || (up.length >= 6 && up.matches(PROMO_STRICT_REGEX))) {
                found.add(up)
            }
        }
    }
    return found.take(10).toList()
}

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
private fun DealDetailStatusScaffold(
    title: String,
    onBack: () -> Unit,
    content: @Composable BoxScope.() -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = title,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleMedium
                    )
                },
                navigationIcon = {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "뒤로가기",
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(padding),
            contentAlignment = Alignment.Center,
            content = content
        )
    }
}

@Suppress("UNUSED_PARAMETER")
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
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val wishlistState by wishlistViewModel.uiState.collectAsStateWithLifecycle()

    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    LaunchedEffect(dealId) {
        viewModel.loadDealData(dealId)
    }

    LaunchedEffect(uiState.reportClosedFeedbackSeq) {
        val msg = uiState.reportClosedFeedback
        if (uiState.reportClosedFeedbackSeq > 0 && !msg.isNullOrBlank()) {
            snackbarHostState.showSnackbar(
                message = msg,
                duration = SnackbarDuration.Short
            )
        }
    }

    if (uiState.isLoading) {
        DealDetailStatusScaffold(title = "핫딜 상세", onBack = onBack) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                Text(
                    text = "핫딜 정보를 불러오는 중이에요",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        return
    }

    if (uiState.error != null) {
        DealDetailStatusScaffold(title = "핫딜 상세", onBack = onBack) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.ErrorOutline,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(44.dp)
                )
                Text(
                    text = uiState.error ?: DealDetailViewModel.STANDARD_DETAIL_ERROR_MESSAGE,
                    color = MaterialTheme.colorScheme.onSurface,
                    style = MaterialTheme.typography.titleMedium,
                    textAlign = TextAlign.Center
                )
                Button(
                    onClick = { viewModel.loadDealData(dealId) },
                    modifier = Modifier.heightIn(min = 48.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    )
                ) {
                    Text(
                        text = "다시 시도",
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                }
            }
        }
        return
    }

    val deal = uiState.deal
    if (deal == null) {
        DealDetailStatusScaffold(title = "핫딜 상세", onBack = onBack) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Info,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(44.dp)
                )
                Text(
                    text = DealDetailViewModel.STANDARD_DETAIL_ERROR_MESSAGE,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center
                )
                Button(
                    onClick = { viewModel.loadDealData(dealId) },
                    modifier = Modifier.heightIn(min = 48.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    )
                ) {
                    Text(
                        text = "다시 시도",
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                }
            }
        }
        return
    }

    val localContext = LocalContext.current
    LaunchedEffect(deal.id) {
        com.ddaeany0919.insightdeal.presentation.mypage.history.RecentDealManager.addRecentDeal(localContext, deal)
    }

    val multiOptions = remember(deal.id, deal.sources, deal.optionsData) {
        DealDetailViewModel.extractValidMultiOptions(deal)
    }
    val selectedOption = remember(multiOptions, uiState.selectedOptionIndex) {
        if (multiOptions.isNotEmpty() && uiState.selectedOptionIndex in multiOptions.indices) {
            multiOptions[uiState.selectedOptionIndex]
        } else {
            null
        }
    }
    // 0원 유효 옵션 선택 시 대표가로 덮어쓰이지 않도록 >= 0L 기준 보존
    val effectivePrice = remember(selectedOption, deal.price) {
        selectedOption?.price?.takeIf { it >= 0L } ?: deal.price
    }
    val effectiveCurrency = remember(selectedOption, deal.currency) {
        selectedOption?.currency?.takeIf { it.isNotBlank() } ?: deal.currency
    }
    val rootTargetUrl = deal.ecommerceUrl?.takeIf { it.isNotBlank() } ?: deal.postUrl ?: ""
    val effectiveTargetUrl = remember(selectedOption, rootTargetUrl) {
        selectedOption?.ecommerceUrl?.takeIf { it.isNotBlank() }
            ?: selectedOption?.postUrl?.takeIf { it.isNotBlank() }
            ?: rootTargetUrl
    }
    val selectedOptionLabel = remember(selectedOption) {
        selectedOption?.let { cleanOptionTitle(it.siteName) }?.takeIf { it.isNotBlank() }
    }
    val effectiveTitle = remember(deal.title, selectedOptionLabel) {
        if (selectedOptionLabel != null && !deal.title.contains(selectedOptionLabel)) {
            "${deal.title} ($selectedOptionLabel)"
        } else {
            deal.title
        }
    }

    val currentPriceInt = deal.price.toInt()
    val existingWishlistItem = (wishlistState as? WishlistUiState.Success)?.items?.find {
        it.productUrl == effectiveTargetUrl || (effectiveTargetUrl.isBlank() && it.productUrl == rootTargetUrl)
    }
    val isBookmarked = existingWishlistItem != null

    // 다중 옵션(sources) 각각의 실제 가격(0원 포함), 구매처 링크, 통화 반영
    val pricesList = remember(deal.sources, deal.siteName, deal.price, deal.currency, rootTargetUrl) {
        deal.sources?.map { source ->
            val optionPrice = source.price?.takeIf { it >= 0L }?.toInt() ?: currentPriceInt
            val optionUrl = source.ecommerceUrl?.takeIf { it.isNotBlank() } ?: source.postUrl
            val optionCurrency = source.currency?.takeIf { it.isNotBlank() } ?: deal.currency
            MallPrice(
                platform = source.siteName,
                price = optionPrice,
                url = optionUrl,
                currency = optionCurrency
            )
        }?.takeIf { it.isNotEmpty() } ?: listOf(
            MallPrice(platform = deal.siteName, price = currentPriceInt, url = rootTargetUrl, currency = deal.currency)
        )
    }

    val selectedPeriod by viewModel.selectedPeriod.collectAsStateWithLifecycle()

    DealDetailScreen(
        deal = deal,
        priceHistory = uiState.priceHistory,
        mallPrices = pricesList,
        isBookmarked = isBookmarked,
        initialTargetPrice = existingWishlistItem?.targetPrice,
        votes = uiState.votes,
        buyCount = uiState.buyCount,
        passCount = uiState.passCount,
        userVote = uiState.userVote,
        commentCount = uiState.commentCount,
        historyCount = uiState.historyCount,
        isSearchFallback = uiState.isSearchFallback,
        isFree = uiState.isFree,
        isClosed = uiState.isClosed || deal.isClosed,
        reportCount = maxOf(uiState.reportCount, deal.reportCount),
        isReportingClosed = uiState.isReportingClosed,
        hasReportedClosed = uiState.hasReportedClosed,
        onReportClosed = { viewModel.reportClosed() },
        comments = uiState.comments,
        commentsLoaded = uiState.commentsLoaded,
        isCommentsLoading = uiState.isCommentsLoading,
        commentsError = uiState.commentsError,
        currentDeviceUuid = uiState.currentDeviceUuid,
        isVoting = uiState.isVoting,
        voteError = uiState.voteError,
        isSubmittingComment = uiState.isSubmittingComment,
        deletingCommentId = uiState.deletingCommentId,
        commentActionError = uiState.commentActionError,
        commentSubmitSuccessSeq = uiState.commentSubmitSuccessSeq,
        selectedPeriod = selectedPeriod,
        onPeriodSelected = { viewModel.setPeriod(it) },
        selectedOptionIndex = uiState.selectedOptionIndex,
        onSelectOption = { viewModel.selectOption(it) },
        onBack = onBack,
        onBookmarkToggle = {
            if (isBookmarked) {
                if (existingWishlistItem != null) {
                    wishlistViewModel.deleteItem(existingWishlistItem)
                }
            } else {
                val activePriceInt = effectivePrice.coerceAtLeast(0L).toInt()
                wishlistViewModel.addItem(
                    keyword = effectiveTitle,
                    productUrl = effectiveTargetUrl,
                    targetPrice = activePriceInt,
                    currentLowestPrice = activePriceInt,
                    currentLowestPlatform = deal.siteName
                )
                scope.launch {
                    val result = snackbarHostState.showSnackbar(
                        message = "관심 상품에 담았어요",
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
            val activePriceInt = effectivePrice.coerceAtLeast(0L).toInt()
            wishlistViewModel.addItem(
                keyword = effectiveTitle,
                productUrl = effectiveTargetUrl,
                targetPrice = targetPrice,
                currentLowestPrice = activePriceInt,
                currentLowestPlatform = deal.siteName
            )
            scope.launch {
                val formattedTarget = formatPrice(targetPrice.toLong(), effectiveCurrency, effectiveTitle)
                val result = snackbarHostState.showSnackbar(
                    message = "목표가($formattedTarget) 이하로 내려가면 알려드릴게요",
                    actionLabel = "찜 목록 보기",
                    duration = SnackbarDuration.Short
                )
                if (result == SnackbarResult.ActionPerformed) {
                    onNavigateToWatchlist()
                }
            }
        },
        onShare = {
            val formattedSharePrice = formatDetailPriceText(
                price = effectivePrice,
                currency = effectiveCurrency,
                titleHint = effectiveTitle,
                isFree = uiState.isFree || deal.isFree
            )
            val shareText = buildString {
                append("[얼만데 핫딜 공유]\n")
                append("$effectiveTitle\n")
                append("현재 핫딜가: $formattedSharePrice\n")
                if (effectiveTargetUrl.isNotBlank()) append(effectiveTargetUrl)
            }
            val sendIntent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(android.content.Intent.EXTRA_SUBJECT, effectiveTitle)
                putExtra(android.content.Intent.EXTRA_TEXT, shareText)
            }
            val chooserIntent = android.content.Intent.createChooser(sendIntent, "핫딜 공유하기").apply {
                if (localContext !is android.app.Activity) {
                    addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            }
            try {
                localContext.startActivity(chooserIntent)
            } catch (_: Exception) {
                android.widget.Toast.makeText(localContext, "공유할 수 있는 앱을 찾지 못했습니다.", android.widget.Toast.LENGTH_SHORT).show()
            }
        },
        onOpenOrigin = onOpenUrl,
        onOpenPurchaseUrl = { purchaseUrl, purchasePrice, purchaseCurrency ->
            viewModel.trackOutboundClick(
                url = purchaseUrl,
                price = purchasePrice,
                currency = purchaseCurrency
            )
            CustomTabLauncher.launchShoppingUrl(localContext, purchaseUrl)
        },
        onVote = { voteType -> viewModel.vote(voteType) },
        onRetryVotes = { viewModel.refreshVotes() },
        onExpandComments = { viewModel.loadCommentsIfNeeded() },
        onRetryComments = { viewModel.loadCommentsIfNeeded(forceRefresh = true) },
        onAddComment = { content -> viewModel.addComment(content) },
        onDeleteComment = { commentId -> viewModel.deleteComment(commentId) },
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
    buyCount: Int = deal.buyCount,
    passCount: Int = deal.passCount,
    userVote: String? = deal.userVote,
    commentCount: Int = deal.commentCount,
    historyCount: Int = deal.historyCount,
    isSearchFallback: Boolean = deal.isSearchFallback,
    isFree: Boolean = deal.isFree,
    isClosed: Boolean = deal.isClosed,
    reportCount: Int = deal.reportCount,
    isReportingClosed: Boolean = false,
    hasReportedClosed: Boolean = false,
    onReportClosed: () -> Unit = {},
    comments: List<ApiDealComment> = emptyList(),
    commentsLoaded: Boolean = false,
    isCommentsLoading: Boolean = false,
    commentsError: String? = null,
    currentDeviceUuid: String = "",
    isVoting: Boolean = false,
    voteError: String? = null,
    isSubmittingComment: Boolean = false,
    deletingCommentId: Int? = null,
    commentActionError: String? = null,
    commentSubmitSuccessSeq: Int = 0,
    selectedPeriod: String,
    onPeriodSelected: (String) -> Unit,
    selectedOptionIndex: Int = 0,
    onSelectOption: (Int) -> Unit,
    onBack: () -> Unit,
    onBookmarkToggle: () -> Unit,
    onTargetPriceAlertRegister: (Int) -> Unit,
    onShare: () -> Unit,
    onOpenOrigin: (String) -> Unit,
    onOpenPurchaseUrl: (String, Long?, String?) -> Unit,
    onVote: (String) -> Unit,
    onRetryVotes: () -> Unit,
    onExpandComments: () -> Unit,
    onRetryComments: () -> Unit,
    onAddComment: (String) -> Unit,
    onDeleteComment: (Int) -> Unit,
    snackbarHostState: SnackbarHostState
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()

    val multiOptions = remember(deal.id, deal.sources, deal.optionsData) {
        DealDetailViewModel.extractValidMultiOptions(deal)
    }
    val selectedOption = remember(multiOptions, selectedOptionIndex) {
        if (multiOptions.isNotEmpty() && selectedOptionIndex in multiOptions.indices) {
            multiOptions[selectedOptionIndex]
        } else null
    }
    val effectivePrice = selectedOption?.price?.takeIf { it >= 0L } ?: deal.price
    val effectiveCurrency = selectedOption?.currency?.takeIf { it.isNotBlank() } ?: deal.currency
    val promoCodes = remember(deal.contentHtml, deal.title) {
        extractPromoCodesFromText(deal.contentHtml ?: deal.title, deal.title)
    }

    val effectiveIsFree = isFree || deal.isFree
    val effectiveHistoryCount = maxOf(historyCount, priceHistory.size)
    val effectiveBuyCount = votes?.buy_count ?: buyCount
    val effectivePassCount = votes?.pass_count ?: passCount
    val effectiveUserVote = votes?.user_vote ?: userVote
    val effectiveCommentCount = if (commentsLoaded) comments.size else maxOf(commentCount, comments.size)
    val canShowTargetAlert = effectivePrice > 0L && !effectiveIsFree
    val isUsingSearchFallback = (isSearchFallback || deal.isSearchFallback) &&
        selectedOption?.ecommerceUrl.isNullOrBlank()

    // [안 B] 2급 아코디언 초기 상태는 모두 접힘(expanded = false)
    var chartExpanded by rememberSaveable(deal.id) { mutableStateOf(false) }
    var voteExpanded by rememberSaveable(deal.id) { mutableStateOf(false) }
    var commentsExpanded by rememberSaveable(deal.id) { mutableStateOf(false) }
    var targetExpanded by rememberSaveable(deal.id) { mutableStateOf(false) }

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
                        text = if (isUsingSearchFallback) "쇼핑몰 검색 결과 이동 안내" else "외부 쇼핑몰 이동 안내",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = if (isUsingSearchFallback) {
                                "직접 링크가 제공되지 않아 해당 상품의 쇼핑몰 검색 결과 화면으로 연결됩니다."
                            } else {
                                "쿠팡 파트너스 및 제휴 마케팅 활동의 일환으로, 이에 따른 일정액의 수수료를 제공받습니다."
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            lineHeight = 22.sp
                        )
                        Text(
                            text = "구매 가격에는 어떠한 추가 비용도 발생하지 않으며, 서비스 운영을 위해 사용됩니다.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            lineHeight = 20.sp
                        )
                    }
                }

                Button(
                    onClick = {
                        val url = pendingUrl
                        val codeToCopy = selectedOption?.code?.takeIf { it.isNotBlank() } ?: promoCodes.firstOrNull()
                        if (!codeToCopy.isNullOrBlank()) {
                            val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as? android.content.ClipboardManager
                            clipboard?.setPrimaryClip(android.content.ClipData.newPlainText("promo_code", codeToCopy))
                            android.widget.Toast.makeText(context, "할인코드를 복사했어요 ($codeToCopy)", android.widget.Toast.LENGTH_SHORT).show()
                            affiliateScope.launch {
                                snackbarHostState.showSnackbar("할인코드를 복사했어요 ($codeToCopy)")
                            }
                        }
                        affiliateScope.launch {
                            affiliateSheetState.hide()
                        }.invokeOnCompletion {
                            showAffiliateSheet = false
                            pendingUrl = null
                            if (url != null) {
                                onOpenPurchaseUrl(url, effectivePrice, effectiveCurrency)
                            }
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 52.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary
                    )
                ) {
                    Text(
                        text = if (isUsingSearchFallback) "쇼핑몰 검색 결과 보기" else "구매처로 이동",
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                }

                TextButton(
                    onClick = {
                        showAffiliateSheet = false
                        pendingUrl = null
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                ) {
                    Text(
                        text = "닫기",
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
            val targetUrl = remember(selectedOption, deal, mallPrices) {
                selectedOption?.ecommerceUrl?.takeIf { it.isNotBlank() }
                    ?: selectedOption?.postUrl?.takeIf { it.isNotBlank() }
                    ?: deal.ecommerceUrl?.takeIf { it.isNotBlank() }
                    ?: mallPrices.firstOrNull()?.url
                    ?: deal.sources?.firstOrNull()?.postUrl
            }
            val originalPostUrl = remember(deal) {
                deal.postUrl?.takeIf { it.isNotBlank() }
                    ?: deal.sources?.firstOrNull()?.postUrl?.takeIf { it.isNotBlank() }
            }
            if (targetUrl != null || originalPostUrl != null) {
                Surface(
                    color = MaterialTheme.colorScheme.surface,
                    tonalElevation = 4.dp,
                    shadowElevation = 12.dp,
                    modifier = Modifier.navigationBarsPadding()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // 뼈대 ②: targetUrl과 동일하더라도 원본 커뮤니티 글 버튼 항상 유지
                        if (originalPostUrl != null) {
                            OutlinedButton(
                                onClick = { onOpenOrigin(originalPostUrl) },
                                modifier = Modifier
                                    .weight(0.9f)
                                    .heightIn(min = 52.dp),
                                shape = RoundedCornerShape(12.dp),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                            ) {
                                Text(
                                    text = "원본 글 보기",
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 14.sp,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    letterSpacing = (-0.2).sp,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }

                        // 뼈대 ③: 구매처 다이렉트 링크 아웃 버튼 (검색 폴백 시 표준 CTA 적용)
                        if (targetUrl != null) {
                            val bottomPriceText = remember(effectivePrice, effectiveCurrency, selectedOption, effectiveIsFree) {
                                formatDetailPriceText(
                                    price = effectivePrice,
                                    currency = effectiveCurrency,
                                    titleHint = selectedOption?.let { cleanOptionTitle(it.siteName) } ?: deal.title,
                                    isFree = effectiveIsFree
                                )
                            }
                            val ctaLabel = when {
                                isUsingSearchFallback -> "쇼핑몰 검색 결과 보기"
                                bottomPriceText == "본문 참조" || bottomPriceText == "가격 확인" -> "구매처로 이동"
                                else -> "$bottomPriceText · 구매처 이동"
                            }
                            Button(
                                onClick = {
                                    pendingUrl = targetUrl
                                    showAffiliateSheet = true
                                },
                                modifier = Modifier
                                    .weight(1.4f)
                                    .heightIn(min = 52.dp),
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.primary
                                )
                            ) {
                                Text(
                                    text = ctaLabel,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp,
                                    color = MaterialTheme.colorScheme.onPrimary,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                    letterSpacing = (-0.2).sp
                                )
                            }
                        }
                    }
                }
            }
        },
        topBar = {
            TopAppBar(
                title = {
                    val cleanHeaderTitle = remember(deal.title, deal.shopName, deal.ecommerceUrl) {
                        resolveShopBadgeAndCleanTitle(deal.title, deal.shopName, deal.ecommerceUrl).cleanTitle
                    }
                    Text(
                        text = cleanHeaderTitle,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleMedium
                    )
                },
                navigationIcon = {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "뒤로가기",
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                },
                actions = {
                    IconButton(
                        onClick = onShare,
                        modifier = Modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Share,
                            contentDescription = "공유",
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    IconButton(
                        onClick = onBookmarkToggle,
                        modifier = Modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp)
                    ) {
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
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(padding),
            contentPadding = PaddingValues(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // 1급 뼈대: 헤더 + 가격 + 검색 폴백 배지 + 1-RTT Summary Pill + 다중 옵션 + 본문 + 할인코드 + AI 요약
            item(key = "deal_header", contentType = "header") {
                DealHeader(
                    deal = deal,
                    selectedOption = selectedOption,
                    selectedOptionIndex = selectedOptionIndex,
                    multiOptions = multiOptions,
                    isSearchFallbackActive = isUsingSearchFallback,
                    isFreeDeal = effectiveIsFree,
                    isClosed = isClosed,
                    onSelectOption = onSelectOption,
                    onOpenPurchaseUrl = onOpenPurchaseUrl,
                    summaryPillsContent = {
                        DealSummaryPillsRow(
                            historyCount = effectiveHistoryCount,
                            buyCount = effectiveBuyCount,
                            passCount = effectivePassCount,
                            commentCount = effectiveCommentCount,
                            showTargetAlertPill = canShowTargetAlert,
                            isTargetAlertConfigured = initialTargetPrice != null && initialTargetPrice > 0,
                            isClosed = isClosed,
                            reportCount = reportCount,
                            isReportingClosed = isReportingClosed,
                            hasReportedClosed = hasReportedClosed,
                            onReportClosed = onReportClosed,
                            onHistoryPillClick = {
                                chartExpanded = true
                                scope.launch { listState.animateScrollToItem(1) }
                            },
                            onVotePillClick = {
                                voteExpanded = true
                                scope.launch { listState.animateScrollToItem(2) }
                            },
                            onCommentsPillClick = {
                                commentsExpanded = true
                                onExpandComments()
                                scope.launch { listState.animateScrollToItem(3) }
                            },
                            onTargetPillClick = {
                                targetExpanded = true
                                scope.launch { listState.animateScrollToItem(4) }
                            }
                        )
                    }
                )
            }

            // 2급 [안 B] 아코디언 ①: 가격 추이 · 시세 요약 (초기 접힘)
            item(key = "accordion_chart", contentType = "accordion_chart") {
                DetailAccordionSection(
                    title = "가격 추이 · 시세 요약",
                    icon = Icons.AutoMirrored.Filled.ShowChart,
                    summaryBadge = if (effectiveHistoryCount >= 2) "${effectiveHistoryCount}건" else "이력 부족",
                    expanded = chartExpanded,
                    onToggle = { chartExpanded = !chartExpanded }
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        PriceHistoryInteractiveCard(
                            history = priceHistory,
                            currency = effectiveCurrency,
                            selectedPeriod = selectedPeriod,
                            onPeriodSelected = onPeriodSelected
                        )
                        if (priceHistory.size > 1 || deal.honeyScore > 0) {
                            AIBuyerGuide(
                                deal = deal,
                                priceHistory = priceHistory,
                                currentPrice = effectivePrice.toInt(),
                                currencyOverride = effectiveCurrency
                            )
                        }
                    }
                }
            }

            // 2급 [안 B] 아코디언 ②: 살까 · 말까 투표 (초기 접힘)
            item(key = "accordion_vote", contentType = "accordion_vote") {
                DetailAccordionSection(
                    title = "살까 · 말까 투표",
                    icon = Icons.Default.Poll,
                    summaryBadge = "살까 $effectiveBuyCount · 말까 $effectivePassCount",
                    expanded = voteExpanded,
                    onToggle = { voteExpanded = !voteExpanded }
                ) {
                    DealVoteAccordionContent(
                        buyCount = effectiveBuyCount,
                        passCount = effectivePassCount,
                        userVote = effectiveUserVote,
                        isVoting = isVoting,
                        voteError = voteError,
                        onVote = onVote,
                        onRetryVotes = onRetryVotes
                    )
                }
            }

            // 2급 [안 B] 아코디언 ③: 한줄평 (초기 접힘, 펼침 시 Lazy-fetch)
            item(key = "accordion_comments", contentType = "accordion_comments") {
                DetailAccordionSection(
                    title = "한줄평",
                    icon = Icons.Default.Forum,
                    summaryBadge = "${effectiveCommentCount}개",
                    expanded = commentsExpanded,
                    onToggle = {
                        val next = !commentsExpanded
                        commentsExpanded = next
                        if (next) {
                            onExpandComments()
                        }
                    }
                ) {
                    DealCommentsAccordionContent(
                        comments = comments,
                        currentDeviceUuid = currentDeviceUuid,
                        isLoading = isCommentsLoading,
                        loadError = commentsError,
                        actionError = commentActionError,
                        isSubmitting = isSubmittingComment,
                        deletingCommentId = deletingCommentId,
                        commentSubmitSuccessSeq = commentSubmitSuccessSeq,
                        onAddComment = onAddComment,
                        onDeleteComment = onDeleteComment,
                        onRetry = onRetryComments
                    )
                }
            }

            // 2급 [안 B] 아코디언 ④: 목표가 알림 (0원·무료 딜 제외, 초기 접힘)
            if (canShowTargetAlert) {
                item(key = "accordion_target", contentType = "accordion_target") {
                    val activeAlertPrice = remember(selectedOption, effectivePrice, mallPrices, deal.price) {
                        if (selectedOption != null && effectivePrice >= 0L) {
                            effectivePrice.toInt()
                        } else {
                            mallPrices.filter { it.price > 0 }.minOfOrNull { it.price } ?: effectivePrice.toInt()
                        }
                    }
                    val activeAlertTitleHint = remember(selectedOption, deal.title) {
                        selectedOption?.let { cleanOptionTitle(it.siteName) }?.takeIf { it.isNotBlank() } ?: deal.title
                    }
                    val targetBadge = remember(initialTargetPrice, effectiveCurrency, activeAlertTitleHint) {
                        if (initialTargetPrice != null && initialTargetPrice > 0) {
                            formatPrice(initialTargetPrice.toLong(), effectiveCurrency, activeAlertTitleHint)
                        } else {
                            "미설정"
                        }
                    }
                    DetailAccordionSection(
                        title = "목표가 알림",
                        icon = Icons.Default.Notifications,
                        summaryBadge = targetBadge,
                        expanded = targetExpanded,
                        onToggle = { targetExpanded = !targetExpanded }
                    ) {
                        PriceAlertRegistrationButton(
                            currentPrice = activeAlertPrice,
                            currency = effectiveCurrency,
                            titleHint = activeAlertTitleHint,
                            priceHistory = priceHistory,
                            initialTargetPrice = initialTargetPrice,
                            onAlertClick = onTargetPriceAlertRegister
                        )
                    }
                }
            }

            item(key = "affiliate_notice", contentType = "footer_notice") {
                Text(
                    text = "쿠팡 파트너스 및 제휴 마케팅 활동의 일환으로, 이에 따른 일정액의 수수료를 제공받습니다.",
                    style = MaterialTheme.typography.labelSmall,
                    color = LocalInsightDealColors.current.textMuted,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp, bottom = 12.dp)
                )
            }
        }
    }
}

/**
 * 1-RTT Summary Pill 스트립 (진입 즉시 요약 카운트 노출, 탭 시 해당 아코디언 펼침+스크롤)
 */
@Composable
private fun DealSummaryPillsRow(
    historyCount: Int,
    buyCount: Int,
    passCount: Int,
    commentCount: Int,
    showTargetAlertPill: Boolean,
    isTargetAlertConfigured: Boolean,
    isClosed: Boolean = false,
    reportCount: Int = 0,
    isReportingClosed: Boolean = false,
    hasReportedClosed: Boolean = false,
    onReportClosed: () -> Unit = {},
    onHistoryPillClick: () -> Unit,
    onVotePillClick: () -> Unit,
    onCommentsPillClick: () -> Unit,
    onTargetPillClick: () -> Unit
) {
    val semantic = LocalInsightDealColors.current
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        item(key = "pill_history") {
            SummaryActionPill(
                icon = Icons.AutoMirrored.Filled.ShowChart,
                label = "가격 추이 ${historyCount}건",
                onClick = onHistoryPillClick
            )
        }
        item(key = "pill_vote") {
            SummaryActionPill(
                icon = Icons.Default.Poll,
                label = "살까 $buyCount · 말까 $passCount",
                onClick = onVotePillClick
            )
        }
        item(key = "pill_comments") {
            SummaryActionPill(
                icon = Icons.Default.Forum,
                label = "한줄평 ${commentCount}개",
                onClick = onCommentsPillClick
            )
        }
        if (showTargetAlertPill) {
            item(key = "pill_target") {
                SummaryActionPill(
                    icon = Icons.Default.Notifications,
                    label = if (isTargetAlertConfigured) "목표가 설정됨" else "목표가 알림",
                    onClick = onTargetPillClick
                )
            }
        }
        item(key = "pill_report_closed") {
            val reportLabel = when {
                isClosed -> "품절/종료됨"
                hasReportedClosed -> if (reportCount > 0) "품절 제보 완료 (${reportCount})" else "품절 제보 완료"
                isReportingClosed -> "제보 중..."
                else -> if (reportCount > 0) "품절 제보 (${reportCount})" else "품절 제보"
            }
            val canClickReport = !isClosed && !hasReportedClosed && !isReportingClosed
            Surface(
                onClick = onReportClosed,
                enabled = canClickReport,
                shape = RoundedCornerShape(999.dp),
                color = if (isClosed || hasReportedClosed) semantic.dangerContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                border = BorderStroke(
                    1.dp,
                    if (isClosed || hasReportedClosed) semantic.danger.copy(alpha = 0.4f) else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f)
                ),
                modifier = Modifier
                    .heightIn(min = 48.dp)
                    .semantics {
                        role = Role.Button
                        stateDescription = when {
                            isClosed -> "품절 또는 종료된 딜"
                            hasReportedClosed -> "품절 제보 완료됨"
                            else -> "제보 가능"
                        }
                    }
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Flag,
                        contentDescription = null,
                        tint = if (isClosed || hasReportedClosed) semantic.onDangerContainer else semantic.danger,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = reportLabel,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = if (isClosed || hasReportedClosed) semantic.onDangerContainer else MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }
    }
}

@Composable
private fun SummaryActionPill(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(999.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f)),
        modifier = Modifier.heightIn(min = 48.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(16.dp)
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

/**
 * [안 B] 공통 접이식 아코디언 섹션 (초기 접힘, 48dp+ 터치 타깃)
 */
@Composable
private fun DetailAccordionSection(
    title: String,
    icon: ImageVector,
    summaryBadge: String,
    expanded: Boolean,
    onToggle: () -> Unit,
    content: @Composable () -> Unit
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.65f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 56.dp)
                    .semantics(mergeDescendants = true) {
                        role = Role.Button
                        stateDescription = if (expanded) "펼쳐짐" else "접힘"
                    }
                    .clickable(onClick = onToggle)
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)
                    ) {
                        Text(
                            text = summaryBadge,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                    Icon(
                        imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = if (expanded) "접기" else "펼치기",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }

            AnimatedVisibility(
                visible = expanded,
                enter = expandVertically(),
                exit = shrinkVertically()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 16.dp, end = 16.dp, bottom = 16.dp)
                ) {
                    HorizontalDivider(
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                        modifier = Modifier.padding(bottom = 14.dp)
                    )
                    content()
                }
            }
        }
    }
}

/**
 * [안 B] 살까 · 말까 투표 아코디언 본문 (1-RTT 요약 연동 + 낙관적 업데이트 + 오류 재시도)
 */
@Composable
private fun DealVoteAccordionContent(
    buyCount: Int,
    passCount: Int,
    userVote: String?,
    isVoting: Boolean,
    voteError: String?,
    onVote: (String) -> Unit,
    onRetryVotes: () -> Unit
) {
    val totalVotes = buyCount + passCount
    val buyRatio = if (totalVotes > 0) (buyCount.toFloat() / totalVotes.toFloat()).coerceIn(0f, 1f) else 0.5f
    val buyPercent = if (totalVotes > 0) (buyRatio * 100f).toInt() else 0
    val normalizedVote = userVote?.lowercase(Locale.US)
    val isBuySelected = normalizedVote == "buy"
    val isPassSelected = normalizedVote == "save" || normalizedVote == "pass"

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (totalVotes > 0) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "지름 지수 $buyPercent% (${totalVotes}명 참여)",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "살까 $buyCount · 말까 $passCount",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(10.dp)
                        .clip(RoundedCornerShape(999.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .fillMaxWidth(fraction = buyRatio)
                            .background(MaterialTheme.colorScheme.primary)
                    )
                }
            }
        } else {
            Text(
                text = "아직 등록된 투표가 없어요. 첫 투표를 남겨보세요!",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (isBuySelected) {
                Button(
                    onClick = { onVote("buy") },
                    enabled = !isVoting,
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 48.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    )
                ) {
                    Icon(
                        imageVector = Icons.Default.ThumbUp,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = "살까 ($buyCount)",
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                }
            } else {
                OutlinedButton(
                    onClick = { onVote("buy") },
                    enabled = !isVoting,
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 48.dp),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                ) {
                    Icon(
                        imageVector = Icons.Default.ThumbUp,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = "살까 ($buyCount)",
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }

            if (isPassSelected) {
                Button(
                    onClick = { onVote("save") },
                    enabled = !isVoting,
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 48.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    )
                ) {
                    Icon(
                        imageVector = Icons.Default.ThumbDown,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = "말까 ($passCount)",
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                }
            } else {
                OutlinedButton(
                    onClick = { onVote("save") },
                    enabled = !isVoting,
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 48.dp),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                ) {
                    Icon(
                        imageVector = Icons.Default.ThumbDown,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = "말까 ($passCount)",
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }

        if (voteError != null) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = voteError,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.weight(1f)
                )
                TextButton(
                    onClick = onRetryVotes,
                    modifier = Modifier.heightIn(min = 48.dp)
                ) {
                    Text(
                        text = "다시 시도",
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    }
}

/**
 * [안 B] 한줄평 아코디언 본문 (Lazy-fetch + 작성 + 본인 댓글 삭제 CRUD 완전 연결)
 */
@Composable
private fun DealCommentsAccordionContent(
    comments: List<ApiDealComment>,
    currentDeviceUuid: String,
    isLoading: Boolean,
    loadError: String?,
    actionError: String?,
    isSubmitting: Boolean,
    deletingCommentId: Int?,
    commentSubmitSuccessSeq: Int = 0,
    onAddComment: (String) -> Unit,
    onDeleteComment: (Int) -> Unit,
    onRetry: () -> Unit
) {
    var commentInput by remember { mutableStateOf("") }
    LaunchedEffect(commentSubmitSuccessSeq) {
        if (commentSubmitSuccessSeq > 0) {
            commentInput = ""
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = commentInput,
                onValueChange = { commentInput = it },
                placeholder = {
                    Text(
                        text = "이 핫딜에 대한 한줄평을 남겨보세요",
                        style = MaterialTheme.typography.bodyMedium
                    )
                },
                singleLine = false,
                maxLines = 3,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.weight(1f)
            )
            Button(
                onClick = {
                    val trimmed = commentInput.trim()
                    if (trimmed.isNotEmpty()) {
                        onAddComment(trimmed)
                    }
                },
                enabled = commentInput.isNotBlank() && !isSubmitting,
                modifier = Modifier.heightIn(min = 48.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                )
            ) {
                if (isSubmitting) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                } else {
                    Text(
                        text = "등록",
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                }
            }
        }

        if (actionError != null) {
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = MaterialTheme.colorScheme.errorContainer
            ) {
                Text(
                    text = actionError,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                )
            }
        }

        when {
            isLoading -> {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(28.dp),
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
            loadError != null -> {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = loadError,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(
                        onClick = onRetry,
                        modifier = Modifier.heightIn(min = 48.dp)
                    ) {
                        Text(
                            text = "다시 시도",
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
            comments.isEmpty() -> {
                Text(
                    text = "아직 한줄평이 없어요. 첫 의견을 남겨보세요!",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
            }
            else -> {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    comments.forEach { comment ->
                        val isMyComment = currentDeviceUuid.isNotBlank() && comment.user_id == currentDeviceUuid
                        val isDeletingThis = deletingCommentId == comment.id
                        val relativeTime = rememberRelativeTime(comment.created_at)

                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Text(
                                            text = comment.nickname.ifBlank { "익명의찰떡이" },
                                            style = MaterialTheme.typography.labelMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        if (relativeTime.isNotEmpty()) {
                                            Text(
                                                text = "• $relativeTime",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }

                                    if (isMyComment) {
                                        TextButton(
                                            onClick = { onDeleteComment(comment.id) },
                                            enabled = !isDeletingThis,
                                            modifier = Modifier.heightIn(min = 48.dp)
                                        ) {
                                            if (isDeletingThis) {
                                                CircularProgressIndicator(
                                                    modifier = Modifier.size(14.dp),
                                                    strokeWidth = 2.dp
                                                )
                                            } else {
                                                Icon(
                                                    imageVector = Icons.Default.Delete,
                                                    contentDescription = "한줄평 삭제",
                                                    tint = MaterialTheme.colorScheme.error,
                                                    modifier = Modifier.size(16.dp)
                                                )
                                                Spacer(Modifier.width(4.dp))
                                                Text(
                                                    text = "삭제",
                                                    style = MaterialTheme.typography.labelMedium,
                                                    color = MaterialTheme.colorScheme.error,
                                                    fontWeight = FontWeight.SemiBold
                                                )
                                            }
                                        }
                                    }
                                }

                                Text(
                                    text = comment.content,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    lineHeight = 20.sp
                                )
                            }
                        }
                    }
                }
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
    val isSubunitCurrency = remember(currency) { isCentScaledCurrency(currency) }
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
    var inputPrice by remember(defaultTargetPrice, isSubunitCurrency) {
        mutableStateOf(formatEditableTargetInput(defaultTargetPrice, isSubunitCurrency))
    }
    var isConfigured by remember(initialTargetPrice) { mutableStateOf(initialTargetPrice != null && initialTargetPrice > 0) }
    var configuredPrice by remember(defaultTargetPrice) { mutableStateOf(defaultTargetPrice) }

    val formattedCurrentPrice = remember(currentPrice, currency, titleHint) {
        formatPrice(currentPrice.toLong(), currency, titleHint)
    }

    OutlinedButton(
        onClick = { showDialog = true },
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp),
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
        val calculatedPrice = remember(inputPrice, isSubunitCurrency) {
            parseEditableTargetInput(inputPrice, isSubunitCurrency)
        }
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
                                val semantic = LocalInsightDealColors.current
                                FilterChip(
                                    selected = (calculatedPrice == historyMinPrice),
                                    onClick = { inputPrice = formatEditableTargetInput(historyMinPrice, isSubunitCurrency) },
                                    leadingIcon = {
                                        Box(
                                            modifier = Modifier
                                                .size(7.dp)
                                                .background(semantic.success, RoundedCornerShape(4.dp))
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
                                        selectedContainerColor = semantic.successContainer,
                                        selectedLabelColor = semantic.onSuccessContainer
                                    )
                                )
                            }
                            if (historyAvgPrice != null && historyAvgPrice != historyMinPrice) {
                                FilterChip(
                                    selected = (calculatedPrice == historyAvgPrice),
                                    onClick = { inputPrice = formatEditableTargetInput(historyAvgPrice, isSubunitCurrency) },
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

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        PRESET_DISCOUNT_PERCENTS.forEach { percent ->
                            val presetPrice = (currentPrice * (100 - percent) / 100)
                            FilterChip(
                                selected = (calculatedPrice == presetPrice),
                                onClick = { inputPrice = formatEditableTargetInput(presetPrice, isSubunitCurrency) },
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

                    val currencyUnitLabel = remember(currency) {
                        when (currency?.trim()?.uppercase(Locale.US)) {
                            "USD" -> "USD ($)"
                            "EUR" -> "EUR (€)"
                            "CNY" -> "CNY (¥)"
                            "JPY" -> "JPY (¥)"
                            else -> "원"
                        }
                    }

                    OutlinedTextField(
                        value = inputPrice,
                        onValueChange = { newValue ->
                            val valid = if (isSubunitCurrency) {
                                newValue.count { it == '.' } <= 1 && newValue.all { it.isDigit() || it == '.' }
                            } else {
                                newValue.all { it.isDigit() }
                            }
                            if (valid) inputPrice = newValue
                        },
                        label = { Text("목표 가격 ($currencyUnitLabel)") },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = MaterialTheme.colorScheme.outline
                        ),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth(),
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                            keyboardType = if (isSubunitCurrency) {
                                androidx.compose.ui.text.input.KeyboardType.Decimal
                            } else {
                                androidx.compose.ui.text.input.KeyboardType.Number
                            }
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
                        val target = parseEditableTargetInput(inputPrice, isSubunitCurrency)
                        if (target > 0) {
                            configuredPrice = target
                            onAlertClick(target)
                            isConfigured = true
                        }
                        showDialog = false
                    },
                    modifier = Modifier.heightIn(min = 48.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                ) {
                    Text("설정 완료", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimary)
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
    onPeriodSelected: (String) -> Unit
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
                        text = "아직 비교할 가격 이력이 충분하지 않아요",
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
                    letterSpacing = (-0.3).sp,
                    modifier = Modifier.semantics { heading() }
                )

                // 기간 선택 칩들 (7일, 1개월, 3개월, 6개월)
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    PERIOD_OPTIONS.forEach { (periodKey, label) ->
                        val isSelected = periodKey == selectedPeriod
                        val containerColor = if (isSelected) MaterialTheme.colorScheme.primary else androidx.compose.ui.graphics.Color.Transparent
                        val contentColor = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                        val borderStroke = if (isSelected) null else BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)

                        Box(
                            modifier = Modifier
                                .heightIn(min = 48.dp)
                                .semantics(mergeDescendants = true) {
                                    role = Role.Tab
                                    selected = isSelected
                                }
                                .clickable { onPeriodSelected(periodKey) },
                            contentAlignment = Alignment.Center
                        ) {
                            androidx.compose.material3.Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = containerColor,
                                contentColor = contentColor,
                                border = borderStroke,
                                modifier = Modifier.heightIn(min = 32.dp)
                            ) {
                                Box(
                                    contentAlignment = Alignment.Center,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
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

                val chartSemantic = LocalInsightDealColors.current
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
                                color = chartSemantic.success
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
                                color = chartSemantic.warning
                            )
                        }
                    }
                }

                if (maxPrice > minPrice) {
                    Spacer(modifier = Modifier.height(10.dp))
                    val posFraction = ((latestPrice - minPrice).toFloat() / (maxPrice - minPrice).toFloat()).coerceIn(0f, 1f)
                    val posPercent = (posFraction * 100f).toInt()
                    val pinColor = when {
                        posPercent <= 25 -> chartSemantic.success
                        posPercent <= 60 -> MaterialTheme.colorScheme.primary
                        else -> chartSemantic.warning
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
                                    color = chartSemantic.success
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
                                    color = chartSemantic.warning
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
                                                    chartSemantic.success,
                                                    MaterialTheme.colorScheme.primary,
                                                    chartSemantic.warning
                                                )
                                            ),
                                            shape = RoundedCornerShape(999.dp)
                                        )
                                )
                                val offsetDp = (maxWidth - 12.dp) * posFraction
                                Box(
                                    modifier = Modifier
                                        .offset(x = offsetDp)
                                        .size(12.dp)
                                        .clip(RoundedCornerShape(999.dp))
                                        .background(pinColor)
                                        .then(
                                            Modifier.padding(0.dp)
                                        )
                                )
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
                val badgeAccentColor = if (isFavorablePrice) chartSemantic.success else MaterialTheme.colorScheme.primary
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
                    color = LocalInsightDealColors.current.textMuted,
                    letterSpacing = (-0.1).sp
                )
            }
        }
    }
}

@Composable
private fun AIBuyerGuide(
    deal: DealItem,
    priceHistory: List<PriceHistoryPoint>,
    currentPrice: Int,
    currencyOverride: String? = null
) {
    if (currentPrice <= 0) return

    val validHistory = remember(priceHistory) { priceHistory.filter { it.price > 0 } }
    val currency = remember(currencyOverride, deal.currency) { currencyOverride ?: deal.currency }
    val minPrice = remember(validHistory, currentPrice) { validHistory.minOfOrNull { it.price } ?: currentPrice }
    val maxPrice = remember(validHistory, currentPrice) { validHistory.maxOfOrNull { it.price } ?: currentPrice }
    val isRecordLow = remember(currentPrice, minPrice, validHistory) { currentPrice <= minPrice && validHistory.size > 1 }

    val isGoodDeal = deal.honeyScore >= 100

    // 다크모드/라이트모드에 완벽 반응하는 프리미엄 테마 컬러 동적 맵핑
    val isDark = LocalIsDarkTheme.current

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

    // 통화별 가격 차이 포매팅 헬퍼 함수 (USD/EUR/CNY 센트 단위 및 JPY/KRW 모두 formatPrice로 일원화)
    val formatDiff = remember(currency) {
        { diffVal: Int ->
            val absDiff = kotlin.math.abs(diffVal.toLong())
            if (absDiff == 0L) {
                when (currency.trim().uppercase(Locale.US)) {
                    "USD" -> "$0"
                    "EUR" -> "€0"
                    "CNY", "JPY" -> "¥0"
                    else -> "0원"
                }
            } else {
                formatPrice(absDiff, currency)
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
                    val guideSemantic = LocalInsightDealColors.current
                    Text(
                        text = "시세 요약",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.ExtraBold,
                        color = titleColor,
                        letterSpacing = (-0.3).sp,
                        modifier = Modifier.semantics { heading() }
                    )
                    Text(
                        text = if (isRecordLow || isGoodDeal) "지금 구매하기 좋은 가격이에요" else "목표가 알림 설정을 추천해요",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (isRecordLow || isGoodDeal) guideSemantic.success else guideSemantic.textMuted,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = (-0.2).sp
                    )
                }
            }
            Spacer(Modifier.height(16.dp))
            HorizontalDivider(color = borderColor.copy(alpha = 0.5f))
            Spacer(Modifier.height(16.dp))

            if (validHistory.size <= 1) {
                val guideSemantic = LocalInsightDealColors.current
                Text(
                    text = "커뮤니티 추천도 ${deal.honeyScore}점",
                    style = MaterialTheme.typography.titleMedium,
                    color = if (isGoodDeal) {
                        guideSemantic.success
                    } else {
                        MaterialTheme.colorScheme.onSurface
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
                val formattedDiff = remember(diffFromMax, formatDiff) { formatDiff(diffFromMax) }
                Text(
                    text = "이전 최고가보다 약 ${formattedDiff} 저렴한 역대 최저가예요. 구매를 고민 중이었다면 좋은 타이밍이에요.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (isDark) Color(0xFFCBD5E1) else Color(0xFF334155),
                    lineHeight = 22.sp,
                    letterSpacing = (-0.2).sp
                )
            } else {
                val prevPrice = remember(validHistory) {
                    if (validHistory.size >= 2) validHistory[validHistory.size - 2].price else currentPrice
                }
                val diffFromPrev = currentPrice - prevPrice
                val diffFromMin = currentPrice - minPrice

                val formattedDiffPrev = remember(diffFromPrev, formatDiff) { formatDiff(kotlin.math.abs(diffFromPrev)) }
                val formattedDiffMin = remember(diffFromMin, formatDiff) { formatDiff(diffFromMin) }

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

@OptIn(ExperimentalFoundationApi::class, ExperimentalLayoutApi::class)
@Composable
fun DealHeader(
    deal: DealItem,
    selectedOption: DealSource? = null,
    selectedOptionIndex: Int = 0,
    multiOptions: List<DealSource> = emptyList(),
    isSearchFallbackActive: Boolean = deal.isSearchFallback,
    isFreeDeal: Boolean = deal.isFree,
    isClosed: Boolean = deal.isClosed,
    onSelectOption: (Int) -> Unit,
    onOpenPurchaseUrl: (String, Long?, String?) -> Unit,
    summaryPillsContent: (@Composable () -> Unit)? = null
) {
    val context = LocalContext.current
    val semantic = LocalInsightDealColors.current
    val scope = rememberCoroutineScope()
    var copiedCode by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(copiedCode) {
        if (copiedCode != null) {
            delay(2000L)
            copiedCode = null
        }
    }
    val promoCodes = remember(deal.contentHtml, deal.title) {
        extractPromoCodesFromText(deal.contentHtml ?: deal.title, deal.title)
    }
    val effectiveImageUrl = selectedOption?.imageUrl?.takeIf { it.isNotBlank() } ?: deal.imageUrl
    val galleryImages = remember(deal.id, effectiveImageUrl, deal.bodyImages) {
        buildList {
            effectiveImageUrl?.trim()?.takeIf { it.isNotEmpty() }?.let(::add)
            addAll(deal.resolvedBodyImages)
        }
            .map { it.trim() }
            .filter { it.startsWith("http", ignoreCase = true) || it.startsWith("/") }
            .distinct()
    }
    var showLightbox by remember { mutableStateOf(false) }
    var lightboxInitialIndex by remember { mutableIntStateOf(0) }

    if (showLightbox && galleryImages.isNotEmpty()) {
        DealImageLightboxDialog(
            images = galleryImages,
            initialIndex = lightboxInitialIndex.coerceIn(0, galleryImages.lastIndex),
            siteName = deal.siteName,
            postUrl = deal.postUrl,
            onDismiss = { showLightbox = false }
        )
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        if (galleryImages.isEmpty()) {
            // P1-02: 이미지 미존재 시에도 카드 영역을 숨기지 않고 BrandPlaceholder 렌더링
            Card(
                shape = RoundedCornerShape(20.dp),
                border = BorderStroke(1.dp, semantic.cardBorder),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                modifier = Modifier.fillMaxWidth()
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 176.dp, max = 220.dp)
                ) {
                    BrandPlaceholder(
                        siteName = deal.siteName,
                        modifier = Modifier.fillMaxSize()
                    )
                    if (isClosed) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = semantic.dangerContainer,
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(12.dp)
                        ) {
                            Text(
                                text = "품절/종료",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.ExtraBold,
                                color = semantic.onDangerContainer,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                            )
                        }
                    }
                }
            }
        } else {
            val pagerState = rememberPagerState(initialPage = 0, pageCount = { galleryImages.size })
            Card(
                shape = RoundedCornerShape(20.dp),
                border = BorderStroke(1.dp, semantic.cardBorder),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                modifier = Modifier
                    .fillMaxWidth()
                    .semantics(mergeDescendants = true) {
                        role = Role.Button
                        contentDescription = "상품 이미지 크게 보기 (${pagerState.currentPage + 1}/${galleryImages.size})"
                    }
                    .clickable(onClickLabel = "상품 이미지 크게 보기") {
                        lightboxInitialIndex = pagerState.currentPage
                        showLightbox = true
                    }
            ) {
                Box(modifier = Modifier.fillMaxWidth()) {
                    HorizontalPager(
                        state = pagerState,
                        modifier = Modifier.fillMaxWidth()
                    ) { pageIdx ->
                        val rawUrl = galleryImages[pageIdx]
                        val primaryHeaderUrl = remember(deal.id, rawUrl) {
                            com.ddaeany0919.insightdeal.data.util.ImageUrlResolver.resolve(rawUrl, targetWidth = 800)
                        }
                        val fallbackHeaderUrl = remember(deal.id, rawUrl) {
                            com.ddaeany0919.insightdeal.data.util.ImageUrlResolver.getFallbackBackendProxyUrl(rawUrl)
                        }
                        var activeHeaderUrl by remember(primaryHeaderUrl) { mutableStateOf(primaryHeaderUrl) }
                        var imageLoadFailed by remember(primaryHeaderUrl) { mutableStateOf(false) }

                        if (imageLoadFailed || activeHeaderUrl.isNullOrBlank()) {
                            BrandPlaceholder(
                                siteName = deal.siteName,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 176.dp, max = 220.dp)
                            )
                        } else {
                            val headerImageRequest = remember(activeHeaderUrl, deal.postUrl) {
                                ImageRequest.Builder(context)
                                    .data(activeHeaderUrl)
                                    .setHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) Chrome/120.0.0.0 Safari/537.36")
                                    .setHeader("Referer", deal.postUrl ?: "https://insightdeal.com/")
                                    .crossfade(true)
                                    .precision(coil.size.Precision.EXACT)
                                    .build()
                            }
                            AsyncImage(
                                model = headerImageRequest,
                                contentDescription = null,
                                onError = {
                                    if (!activeHeaderUrl.orEmpty().contains("/api/proxy-image", ignoreCase = true) &&
                                        fallbackHeaderUrl != null &&
                                        activeHeaderUrl != fallbackHeaderUrl
                                    ) {
                                        activeHeaderUrl = fallbackHeaderUrl
                                    } else {
                                        imageLoadFailed = true
                                    }
                                },
                                colorFilter = if (isClosed) DETAIL_CLOSED_COLOR_FILTER else null,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 176.dp, max = 280.dp)
                                    .background(MaterialTheme.colorScheme.surfaceVariant),
                                contentScale = ContentScale.Fit,
                                alpha = 1.0f
                            )
                        }
                    }

                    if (isClosed) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = semantic.dangerContainer,
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(12.dp)
                        ) {
                            Text(
                                text = "품절/종료",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.ExtraBold,
                                color = semantic.onDangerContainer,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                            )
                        }
                    }

                    Surface(
                        shape = RoundedCornerShape(999.dp),
                        color = Color(0xCC111827),
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(12.dp)
                            .clearAndSetSemantics {}
                    ) {
                        Text(
                            text = "${pagerState.currentPage + 1} / ${galleryImages.size} · 확대",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                        )
                    }
                }
            }

            if (galleryImages.size > 1) {
                Spacer(modifier = Modifier.height(10.dp))
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(galleryImages.size) { idx ->
                        val isCurrent = pagerState.currentPage == idx
                        val thumbRaw = galleryImages[idx]
                        val thumbPrimary = remember(deal.id, thumbRaw) {
                            com.ddaeany0919.insightdeal.data.util.ImageUrlResolver.resolve(thumbRaw, targetWidth = 200)
                        }
                        val thumbFallback = remember(deal.id, thumbRaw) {
                            com.ddaeany0919.insightdeal.data.util.ImageUrlResolver.getFallbackBackendProxyUrl(thumbRaw)
                        }
                        var activeThumb by remember(thumbPrimary) { mutableStateOf(thumbPrimary) }
                        var thumbFailed by remember(thumbPrimary) { mutableStateOf(false) }

                        Box(
                            modifier = Modifier
                                .size(52.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .border(
                                    width = if (isCurrent) 2.dp else 1.dp,
                                    color = if (isCurrent) MaterialTheme.colorScheme.primary else semantic.cardBorder,
                                    shape = RoundedCornerShape(10.dp)
                                )
                                .semantics {
                                    role = Role.Tab
                                    selected = isCurrent
                                    contentDescription = "이미지 썸네일 ${idx + 1}"
                                }
                                .clickable {
                                    scope.launch { pagerState.animateScrollToPage(idx) }
                                }
                        ) {
                            if (thumbFailed || activeThumb.isNullOrBlank()) {
                                BrandPlaceholder(
                                    siteName = deal.siteName,
                                    modifier = Modifier.fillMaxSize()
                                )
                            } else {
                                AsyncImage(
                                    model = ImageRequest.Builder(context)
                                        .data(activeThumb)
                                        .setHeader("Referer", deal.postUrl ?: "https://insightdeal.com/")
                                        .crossfade(true)
                                        .build(),
                                    contentDescription = null,
                                    onError = {
                                        if (
                                            thumbFallback != null &&
                                            activeThumb != thumbFallback &&
                                            !activeThumb.orEmpty().contains("/api/proxy-image", ignoreCase = true)
                                        ) {
                                            activeThumb = thumbFallback
                                        } else {
                                            thumbFailed = true
                                        }
                                    },
                                    colorFilter = if (isClosed) DETAIL_CLOSED_COLOR_FILTER else null,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize()
                                )
                            }
                        }
                    }
                }
            }
        }

        Column(modifier = Modifier.padding(vertical = 16.dp)) {
            val resolvedShopTitle = remember(deal.title, deal.shopName, selectedOption?.ecommerceUrl, deal.ecommerceUrl) {
                resolveShopBadgeAndCleanTitle(
                    rawTitle = deal.title,
                    shopName = deal.shopName,
                    ecommerceUrl = selectedOption?.ecommerceUrl ?: deal.ecommerceUrl
                )
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                // Light/Dark/AMOLED 3모드 고대비 보장을 위해 테마 컨테이너/텍스트 토큰으로 통일
                val primaryContainer = MaterialTheme.colorScheme.primaryContainer
                val onPrimaryContainer = MaterialTheme.colorScheme.onPrimaryContainer
                val surfaceVariant = MaterialTheme.colorScheme.surfaceVariant
                val onSurfaceVariant = MaterialTheme.colorScheme.onSurfaceVariant

                val safeSiteNames = ((deal.siteNames as List<String>?) ?: emptyList())
                val siteChips = remember(
                    safeSiteNames,
                    deal.siteName,
                    primaryContainer,
                    onPrimaryContainer,
                    surfaceVariant,
                    onSurfaceVariant
                ) {
                    val rawList = if (safeSiteNames.isNotEmpty()) safeSiteNames else deal.siteName.split(",").map { it.trim() }
                    val distinctRawList = rawList.map { sName ->
                        if (sName.contains(" - ")) {
                            sName.substringBefore(" - ").trim()
                        } else {
                            sName.trim()
                        }
                    }.distinct()

                    distinctRawList.map { sName ->
                        val siteNameLower = sName.lowercase(Locale.getDefault())
                        val isKnownCommunity = siteNameLower.contains("뽐뿌") ||
                            siteNameLower.contains("퀘이사존") ||
                            siteNameLower.contains("루리웹") ||
                            siteNameLower.contains("펨코") ||
                            siteNameLower.contains("에펨코리아") ||
                            siteNameLower.contains("빠삭") ||
                            siteNameLower.contains("클리앙")
                        val (bgColor, textColor) = if (isKnownCommunity) {
                            primaryContainer to onPrimaryContainer
                        } else {
                            surfaceVariant to onSurfaceVariant
                        }
                        SiteChipInfo(name = sName, bgColor = bgColor, textColor = textColor)
                    }
                }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
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
                    if (!resolvedShopTitle.shopBadge.isNullOrBlank()) {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
                        ) {
                            Text(
                                text = resolvedShopTitle.shopBadge,
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp)
                            )
                        }
                    }
                    if (isClosed) {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = semantic.dangerContainer
                        ) {
                            Text(
                                text = "품절/종료",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.ExtraBold,
                                color = semantic.onDangerContainer,
                                modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp)
                            )
                        }
                    }
                }

                val displayShippingInfo = remember(deal.shippingFee, deal.category, resolvedShopTitle.cleanTitle) {
                    val isDigitalTitle = resolvedShopTitle.cleanTitle.contains("요금제") || resolvedShopTitle.cleanTitle.contains("데이터")
                    val hideShipping = deal.category in DIGITAL_CATEGORIES || isDigitalTitle

                    if (hideShipping) {
                        null
                    } else {
                        val trimmed = decodeHtmlEntities(deal.shippingFee).trim().ifEmpty { "정보 없음" }
                        when {
                            trimmed == "정보 없음" || trimmed == "배송비 확인 필요" -> "확인 필요"
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
                text = resolvedShopTitle.cleanTitle,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.ExtraBold,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
                lineHeight = 28.sp,
                letterSpacing = (-0.5).sp,
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(modifier = Modifier.height(16.dp))

            if (selectedOption != null) {
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                    modifier = Modifier.padding(bottom = 6.dp)
                ) {
                    Text(
                        text = "선택: #${selectedOptionIndex + 1} ${cleanOptionTitle(selectedOption.siteName)}",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }
            }

            val effectivePrice = selectedOption?.price?.takeIf { it >= 0L } ?: deal.price
            val effectiveCurrency = selectedOption?.currency?.takeIf { it.isNotBlank() } ?: deal.currency

            Row(verticalAlignment = Alignment.Bottom) {
                if (deal.discountRate != null && deal.discountRate > 0 && selectedOption == null) {
                    Text(
                        text = "${deal.discountRate}%",
                        style = MaterialTheme.typography.headlineLarge,
                        color = MaterialTheme.colorScheme.error,
                        fontWeight = FontWeight.Black,
                        letterSpacing = (-0.5).sp
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                }

                val priceText = remember(effectivePrice, effectiveCurrency, resolvedShopTitle.cleanTitle, selectedOption, isFreeDeal) {
                    formatDetailPriceText(
                        price = effectivePrice,
                        currency = effectiveCurrency,
                        titleHint = selectedOption?.let { cleanOptionTitle(it.siteName) } ?: resolvedShopTitle.cleanTitle,
                        isFree = isFreeDeal
                    )
                }
                val isUnconfirmedPrice = priceText == "가격 확인"
                val basePriceStyle = if (isUnconfirmedPrice) MaterialTheme.typography.titleLarge else MaterialTheme.typography.headlineLarge
                Text(
                    text = priceText,
                    style = basePriceStyle.copy(
                        fontFeatureSettings = PriceTextStyle.fontFeatureSettings,
                        fontWeight = FontWeight.Black,
                        letterSpacing = (-0.5).sp
                    ),
                    color = if (isUnconfirmedPrice) {
                        semantic.textMuted
                    } else {
                        semantic.priceAccent
                    }
                )

                if (isUnconfirmedPrice) {
                    Spacer(modifier = Modifier.width(6.dp))
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = null,
                        tint = semantic.textMuted,
                        modifier = Modifier.size(20.dp).padding(bottom = 2.dp)
                    )
                }
            }

            val selectedOptionTitle = selectedOption?.let { cleanOptionTitle(it.siteName) }.orEmpty()
            val unitFromOption = remember(
                selectedOptionTitle,
                effectivePrice,
                effectiveCurrency,
                deal.category,
                isClosed,
                isFreeDeal
            ) {
                if (!isClosed && !isFreeDeal && selectedOptionTitle.isNotBlank()) {
                    computeUnitPriceBadge(
                        title = selectedOptionTitle,
                        price = effectivePrice,
                        currency = effectiveCurrency,
                        category = deal.category
                    )
                } else {
                    null
                }
            }
            val unitFromDealTitle = remember(
                resolvedShopTitle.cleanTitle,
                effectivePrice,
                deal.price,
                effectiveCurrency,
                deal.category,
                selectedOption,
                selectedOptionTitle,
                unitFromOption,
                isClosed,
                isFreeDeal,
                deal.hasOptions
            ) {
                if (
                    !isClosed &&
                    !isFreeDeal &&
                    unitFromOption == null &&
                    !(deal.hasOptions && selectedOption == null) &&
                    (selectedOption == null ||
                        effectivePrice == deal.price ||
                        Regex("^(?:로켓배송|무료배송|기본|선택\\s*\\d+|옵션\\s*\\d+)$", RegexOption.IGNORE_CASE)
                            .matches(selectedOptionTitle.trim()))
                ) {
                    computeUnitPriceBadge(
                        title = resolvedShopTitle.cleanTitle,
                        price = effectivePrice,
                        currency = effectiveCurrency,
                        category = deal.category
                    )
                } else {
                    null
                }
            }
            val detailUnitPriceBadge = unitFromOption ?: unitFromDealTitle
            val rawDetailPaymentCondBadge = remember(
                deal.title,
                selectedOptionTitle,
                deal.shippingFee,
                isClosed,
                isFreeDeal
            ) {
                if (!isClosed && !isFreeDeal) {
                    val combined = if (selectedOptionTitle.isNotBlank()) "${deal.title} $selectedOptionTitle" else deal.title
                    extractPaymentConditionBadge(combined, deal.shippingFee)
                } else {
                    null
                }
            }
            val rawShippingForDedup = remember(deal.category, deal.shippingFee) {
                if (deal.category in DIGITAL_CATEGORIES) "" else decodeHtmlEntities(deal.shippingFee)
            }
            val detailPaymentCondBadge = if (
                rawDetailPaymentCondBadge != null &&
                !((rawDetailPaymentCondBadge == "와우회원 조건" && rawShippingForDedup.contains("와우")) ||
                    (rawDetailPaymentCondBadge == "네멤 조건" && rawShippingForDedup.contains("네멤")))
            ) {
                rawDetailPaymentCondBadge
            } else {
                null
            }
            if (detailUnitPriceBadge != null || detailPaymentCondBadge != null) {
                Spacer(modifier = Modifier.height(6.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.semantics(mergeDescendants = true) {
                        contentDescription = listOfNotNull(
                            detailUnitPriceBadge?.let { "${it.label}, ${it.detail}" },
                            detailPaymentCondBadge
                        ).joinToString(", ")
                    }
                ) {
                    if (detailUnitPriceBadge != null) {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = semantic.infoContainer,
                            border = BorderStroke(0.5.dp, semantic.info.copy(alpha = 0.35f)),
                            modifier = Modifier.align(Alignment.CenterVertically)
                        ) {
                            Text(
                                text = detailUnitPriceBadge.label,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = semantic.onInfoContainer,
                                style = PriceTextStyle,
                                maxLines = 1,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                        if (detailUnitPriceBadge.detail.isNotBlank()) {
                            Text(
                                text = detailUnitPriceBadge.detail,
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontFeatureSettings = PriceTextStyle.fontFeatureSettings,
                                    fontWeight = FontWeight.SemiBold
                                ),
                                color = semantic.textMuted,
                                modifier = Modifier.align(Alignment.CenterVertically)
                            )
                        }
                    }
                    if (detailPaymentCondBadge != null) {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = semantic.warningContainer,
                            border = BorderStroke(0.5.dp, semantic.onWarningContainer.copy(alpha = 0.35f)),
                            modifier = Modifier.align(Alignment.CenterVertically)
                        ) {
                            Text(
                                text = detailPaymentCondBadge,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = semantic.onWarningContainer,
                                maxLines = 1,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))
            val absoluteTimeText = remember(deal.createdAt) {
                parseBackendTimestampToKst(deal.createdAt)?.format(DETAIL_DATE_FORMATTER).orEmpty()
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
                    color = semantic.textMuted,
                    fontWeight = FontWeight.Medium
                )

                Text(
                    text = "•",
                    style = MaterialTheme.typography.bodySmall,
                    color = semantic.textMuted
                )

                Text(
                    text = "조회 ${deal.viewCount}회",
                    style = MaterialTheme.typography.bodySmall,
                    color = semantic.textMuted,
                    fontWeight = FontWeight.Medium
                )
            }

            if (isSearchFallbackActive) {
                Spacer(modifier = Modifier.height(12.dp))
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = semantic.warningContainer,
                    border = BorderStroke(1.dp, semantic.onWarningContainer.copy(alpha = 0.25f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = null,
                            tint = semantic.onWarningContainer,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = "직접 링크 미제공 · 쇼핑몰 검색 결과로 이동해요",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = semantic.onWarningContainer
                        )
                    }
                }
            }

            if (summaryPillsContent != null) {
                Spacer(modifier = Modifier.height(14.dp))
                summaryPillsContent()
            }

            val dealLevelSubUrls = remember(deal.id, deal.subUrls, deal.sources, deal.ecommerceUrl) {
                deal.resolvedSubUrls
            }
            if (dealLevelSubUrls.isNotEmpty() && multiOptions.size <= 1) {
                Spacer(modifier = Modifier.height(12.dp))
                androidx.compose.foundation.lazy.LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(dealLevelSubUrls.size) { subIdx ->
                        val subUrl = dealLevelSubUrls[subIdx]
                        OutlinedButton(
                            onClick = {
                                onOpenPurchaseUrl(subUrl, deal.price, deal.currency)
                            },
                            modifier = Modifier.heightIn(min = 48.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                            shape = RoundedCornerShape(8.dp),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                        ) {
                            Text(
                                text = "보조 구매처 ${subIdx + 1}",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }

            val cleanedBodyText = remember(deal.contentHtml, deal.title) {
                val raw = deal.contentHtml.orEmpty()
                if (raw.isBlank()) ""
                else {
                    val step1 = raw
                        .replace(Regex("<br\\s*/?>", RegexOption.IGNORE_CASE), "\n")
                        .replace(Regex("</(p|div|li|tr|h[1-6])>", RegexOption.IGNORE_CASE), "\n")
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
                        .replace("\uFFFD", "")
                        .replace(Regex("https?://\\S+"), "")
                        .replace(Regex("\\s+(?=[📱📶💰📦🎁⚠️✅🎟️🔥👉ⒶⒷ①②③④⑤])"), "\n")
                        .replace(Regex("\\s*🔗\\s*"), "\n")
                        .replace(Regex("\\s+(?=(?:[1-9]\\.\\s+[가-힣A-Za-z]))"), "\n\n")
                        .replace(Regex("([^\\n])\\s*(최종가\\s*:\\s*[0-9,]+원)"), "$1\n$2\n")
                    val lines = step1.lines()
                        .map { it.replace(Regex("\\s+"), " ").trim() }
                        .map { it.replace(Regex("^(?:보기|바로가기|제품\\s*바로가기|상품\\s*바로가기)\\s*>{1,}\\s*", RegexOption.IGNORE_CASE), "▪ ") }
                        .map { it.replace(Regex("\\s+최근\\s*본\\s*상품\\s*>\\s*$", RegexOption.IGNORE_CASE), "") }
                    val validLines = mutableListOf<String>()
                    for (ln in lines) {
                        if (ln.isEmpty() || ln == "▪") continue
                        if (ln.matches(Regex("^[\\-=*_~.,:;()\\[\\]/\\\\]+$"))) continue
                        if (ln.matches(Regex("^(?:링크|바로가기|보기)\\s*>{0,}$", RegexOption.IGNORE_CASE))) continue
                        if (ln.matches(Regex("^(?:링크|구매\\s*링크)\\s*>{2,}.*$", RegexOption.IGNORE_CASE))) continue
                        if (ln.matches(Regex("^>{2,}.*$"))) continue
                        if (ln.matches(Regex("^링크오류시$", RegexOption.IGNORE_CASE))) continue
                        if (ln.matches(Regex("^(?:갤럭시|아이폰)\\s*:\\s*.*(?:링크\\s*꾹|알리익스프레스\\s*앱).*$", RegexOption.IGNORE_CASE))) continue
                        if (ln.matches(Regex("^2\\)\\s*알리앱\\s*꾹\\s*눌러서.*$", RegexOption.IGNORE_CASE))) continue
                        if (ln.matches(Regex("^-\\s*가격은\\s*쿠폰\\s*적용\\s*전\\s*가격을\\s*적어주세요.*$", RegexOption.IGNORE_CASE))) continue
                        val cleanLn = ln
                            .replace(Regex(",\\s*,+"), ", ")
                            .replace(Regex("([A-Za-z0-9]{4,})\\s*,\\s*(?=[A-Za-z0-9]{4,})"), "$1, ")
                            .replace(Regex(",\\s*$"), "")
                            .trim()
                        if (cleanLn.isEmpty()) continue
                        val isCodeOnly = cleanLn.matches(Regex("^[A-Z0-9]{4,14}(?:\\s*,\\s*[A-Z0-9]{4,14})*$", RegexOption.IGNORE_CASE)) &&
                            cleanLn.contains(Regex("[A-Za-z]")) && cleanLn.contains(Regex("[0-9]"))
                        if (isCodeOnly && validLines.isNotEmpty()) {
                            val prev = validLines.last()
                            if (prev.contains(Regex("(?:코드|쿠폰|할인)")) || prev.contains(Regex("[A-Z0-9]{4,14}\\)?$", RegexOption.IGNORE_CASE))) {
                                validLines[validLines.lastIndex] = when {
                                    prev.endsWith(")") -> "${prev.dropLast(1)}, $cleanLn)"
                                    prev.endsWith(":") || prev.endsWith(",") -> "$prev $cleanLn"
                                    else -> "$prev ($cleanLn)"
                                }
                                continue
                            }
                        }
                        validLines.add(cleanLn)
                    }
                    val formattedLines = mutableListOf<String>()
                    for (i in validLines.indices) {
                        val ln = validLines[i]
                        formattedLines.add(ln)
                        val isFinal = ln.contains(Regex("^(?:최종가|최종\\s*체감가|최대\\s*혜택가|최대\\s*할인가|합계)(?=\\s|:|$)")) ||
                            ln.contains(Regex("최저가\\s*:\\s*\\$?[0-9,.]+"))
                        if (isFinal && i < validLines.lastIndex) {
                            val next1 = validLines.getOrNull(i + 1).orEmpty()
                            val next2 = validLines.getOrNull(i + 2).orEmpty()
                            val isNext1Payment = next1.contains(Regex("^(?:우리카드|국민카드|결제\\s*할인|카드\\s*할인|※\\s*관세|\\*[0-9]+인치)"))
                            val hasFollowUp = next1.contains(Regex("^(?:최종가|최대\\s*할인가)(?=\\s|:|$)")) ||
                                (isNext1Payment && next2.contains(Regex("^(?:최종가|최대\\s*할인가)(?=\\s|:|$)")))
                            if (!hasFollowUp) {
                                formattedLines.add("────────────────────────")
                            }
                        }
                    }
                    val joined = formattedLines.joinToString("\n").trim()
                    if (joined.length < 4 || joined.replace(" ", "") == deal.title.replace(" ", "")) "" else joined
                }
            }

            if (cleanedBodyText.isNotEmpty() || promoCodes.isNotEmpty()) {
                Spacer(modifier = Modifier.height(18.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "상품 상세 본문",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.semantics { heading() }
                    )
                    if (promoCodes.isNotEmpty()) {
                        Text(
                            text = "코드 터치 시 복사",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
                if (promoCodes.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        items(promoCodes) { code ->
                            val isCopied = copiedCode == code
                            val chipBg by animateColorAsState(
                                targetValue = if (isCopied) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f),
                                label = "promoChipBg"
                            )
                            val chipFg by animateColorAsState(
                                targetValue = if (isCopied) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.primary,
                                label = "promoChipFg"
                            )
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = chipBg,
                                border = BorderStroke(
                                    1.dp,
                                    if (isCopied) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)
                                ),
                                modifier = Modifier
                                    .heightIn(min = 48.dp)
                                    .clickable {
                                        val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as? android.content.ClipboardManager
                                        clipboard?.setPrimaryClip(android.content.ClipData.newPlainText("promo_code", code))
                                        copiedCode = code
                                        android.widget.Toast.makeText(context, "할인코드를 복사했어요 ($code)", android.widget.Toast.LENGTH_SHORT).show()
                                    }
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                ) {
                                    Icon(
                                        imageVector = if (isCopied) Icons.Default.Check else Icons.Default.ContentCopy,
                                        contentDescription = null,
                                        tint = chipFg,
                                        modifier = Modifier.size(13.dp)
                                    )
                                    Text(
                                        text = if (isCopied) "$code 복사됨" else code,
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = chipFg
                                    )
                                }
                            }
                        }
                    }
                }
                if (cleanedBodyText.isNotEmpty()) {
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
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "옵션 선택 (${multiOptions.size}개)",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "선택 시 가격·시세 반영",
                                style = MaterialTheme.typography.labelSmall,
                                color = LocalInsightDealColors.current.textMuted
                            )
                        }

                        multiOptions.forEachIndexed { index, opt ->
                            val isSelected = selectedOptionIndex == index
                            val optName = cleanOptionTitle(opt.siteName)
                            val optPriceVal = opt.price ?: 0L
                            val optCurrencyVal = opt.currency ?: deal.currency
                            val optPriceText = formatDetailPriceText(
                                price = optPriceVal,
                                currency = optCurrencyVal,
                                titleHint = optName,
                                isFree = isFreeDeal && optPriceVal == 0L
                            )
                            val optTargetUrl = opt.ecommerceUrl?.takeIf { it.isNotBlank() } ?: opt.postUrl
                            val optSubUrls = remember(opt.subUrls, optTargetUrl) {
                                opt.subUrls.orEmpty()
                                    .map { it.trim() }
                                    .filter { it.startsWith("http", ignoreCase = true) && it != optTargetUrl }
                                    .distinct()
                            }

                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.22f) else MaterialTheme.colorScheme.surface,
                                border = if (isSelected) BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary) else BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .semantics {
                                        selected = isSelected
                                    }
                                    .clickable {
                                        onSelectOption(index)
                                    }
                            ) {
                                Column(
                                    modifier = Modifier.padding(10.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        if (!opt.imageUrl.isNullOrBlank()) {
                                            val primaryOptImg = remember(opt.imageUrl) {
                                                com.ddaeany0919.insightdeal.data.util.ImageUrlResolver.resolve(opt.imageUrl, targetWidth = 120)
                                            }
                                            val fallbackOptImg = remember(opt.imageUrl) {
                                                com.ddaeany0919.insightdeal.data.util.ImageUrlResolver.getFallbackBackendProxyUrl(opt.imageUrl)
                                            }
                                            var activeOptImg by remember(primaryOptImg) { mutableStateOf(primaryOptImg) }

                                            AsyncImage(
                                                model = coil.request.ImageRequest.Builder(context)
                                                    .data(activeOptImg)
                                                    .crossfade(true)
                                                    .build(),
                                                onError = {
                                                    if (
                                                        fallbackOptImg != null &&
                                                        activeOptImg != fallbackOptImg &&
                                                        !activeOptImg.orEmpty().contains("/api/proxy-image", ignoreCase = true)
                                                    ) {
                                                        activeOptImg = fallbackOptImg
                                                    }
                                                },
                                                contentDescription = optName,
                                                modifier = Modifier
                                                    .size(48.dp)
                                                    .clip(RoundedCornerShape(8.dp))
                                                    .background(MaterialTheme.colorScheme.surfaceVariant),
                                                contentScale = ContentScale.Crop
                                            )
                                        } else {
                                            Box(
                                                modifier = Modifier
                                                    .size(48.dp)
                                                    .clip(RoundedCornerShape(8.dp))
                                                    .background(if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f) else MaterialTheme.colorScheme.surfaceVariant),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Text(
                                                    text = "${index + 1}",
                                                    style = MaterialTheme.typography.titleMedium,
                                                    fontWeight = FontWeight.Bold,
                                                    color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                        }

                                        Column(
                                            modifier = Modifier.weight(1f),
                                            verticalArrangement = Arrangement.spacedBy(2.dp)
                                        ) {
                                            Text(
                                                text = optName,
                                                style = MaterialTheme.typography.bodyMedium,
                                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                                color = MaterialTheme.colorScheme.onSurface,
                                                maxLines = 2,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                                            ) {
                                                Text(
                                                    text = optPriceText,
                                                    style = MaterialTheme.typography.bodyMedium,
                                                    fontWeight = FontWeight.Bold,
                                                    color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                                if (!opt.code.isNullOrBlank()) {
                                                    val isOptCodeCopied = copiedCode == opt.code
                                                    val chipBg by animateColorAsState(
                                                        targetValue = if (isOptCodeCopied) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.75f),
                                                        label = "optCodeBg"
                                                    )
                                                    val chipFg by animateColorAsState(
                                                        targetValue = if (isOptCodeCopied) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSecondaryContainer,
                                                        label = "optCodeFg"
                                                    )
                                                    Surface(
                                                        shape = RoundedCornerShape(6.dp),
                                                        color = chipBg,
                                                        modifier = Modifier
                                                            .heightIn(min = 48.dp)
                                                            .clickable {
                                                                val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as? android.content.ClipboardManager
                                                                clipboard?.setPrimaryClip(android.content.ClipData.newPlainText("promo_code", opt.code))
                                                                copiedCode = opt.code
                                                                android.widget.Toast.makeText(context, "할인코드를 복사했어요 (${opt.code})", android.widget.Toast.LENGTH_SHORT).show()
                                                            }
                                                    ) {
                                                        Row(
                                                            verticalAlignment = Alignment.CenterVertically,
                                                            horizontalArrangement = Arrangement.spacedBy(3.dp),
                                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                        ) {
                                                            Icon(
                                                                imageVector = if (isOptCodeCopied) Icons.Default.Check else Icons.Default.ContentCopy,
                                                                contentDescription = null,
                                                                tint = chipFg,
                                                                modifier = Modifier.size(11.dp)
                                                            )
                                                            Text(
                                                                text = if (isOptCodeCopied) "${opt.code} 복사됨" else opt.code,
                                                                style = MaterialTheme.typography.labelSmall,
                                                                fontWeight = FontWeight.SemiBold,
                                                                color = chipFg
                                                            )
                                                        }
                                                    }
                                                }
                                            }
                                        }

                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            if (isSelected) {
                                                Icon(
                                                    imageVector = Icons.Default.CheckCircle,
                                                    contentDescription = "선택됨",
                                                    tint = MaterialTheme.colorScheme.primary,
                                                    modifier = Modifier.size(18.dp)
                                                )
                                            }
                                            if (optTargetUrl.isNotBlank()) {
                                                OutlinedButton(
                                                    onClick = {
                                                        val codeToCopy = opt.code?.takeIf { it.isNotBlank() } ?: promoCodes.firstOrNull()
                                                        if (!codeToCopy.isNullOrBlank()) {
                                                            val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as? android.content.ClipboardManager
                                                            clipboard?.setPrimaryClip(android.content.ClipData.newPlainText("promo_code", codeToCopy))
                                                            copiedCode = codeToCopy
                                                            android.widget.Toast.makeText(context, "할인코드를 복사했어요 ($codeToCopy)", android.widget.Toast.LENGTH_SHORT).show()
                                                        }
                                                        val finalOptUrl = if (optTargetUrl.startsWith("http", ignoreCase = true)) optTargetUrl else "https://$optTargetUrl"
                                                        onOpenPurchaseUrl(finalOptUrl, optPriceVal, optCurrencyVal)
                                                    },
                                                    modifier = Modifier.heightIn(min = 48.dp),
                                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                                                    shape = RoundedCornerShape(8.dp),
                                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.6f))
                                                ) {
                                                    Text(
                                                        text = "구매",
                                                        style = MaterialTheme.typography.labelSmall,
                                                        fontWeight = FontWeight.Bold,
                                                        color = MaterialTheme.colorScheme.primary
                                                    )
                                                }
                                            }
                                        }
                                    }

                                    if (optSubUrls.isNotEmpty()) {
                                        LazyRow(
                                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            items(optSubUrls.size) { subIdx ->
                                                val subUrl = optSubUrls[subIdx]
                                                Surface(
                                                    shape = RoundedCornerShape(6.dp),
                                                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f),
                                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)),
                                                    modifier = Modifier
                                                        .heightIn(min = 48.dp)
                                                        .clickable {
                                                            onOpenPurchaseUrl(subUrl, optPriceVal, optCurrencyVal)
                                                        }
                                                ) {
                                                    Box(
                                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                                        contentAlignment = Alignment.Center
                                                    ) {
                                                        Text(
                                                            text = "보조 구매처 ${subIdx + 1}",
                                                            style = MaterialTheme.typography.labelSmall,
                                                            fontWeight = FontWeight.Medium,
                                                            color = MaterialTheme.colorScheme.onSurfaceVariant
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
                    elevation = CardDefaults.cardElevation(0.dp),
                    modifier = Modifier.fillMaxWidth()
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
        }
    }
}

/**
 * P1-03 전체화면 핀치줌·스와이프 라이트박스 이미지 뷰어 다이얼로그
 * - scale == 1f에서는 HorizontalPager 좌우 스와이프가 정상 작동하며, 확대 시(scale > 1.02f) 팬과 경계 클램핑이 활성화됩니다.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun DealImageLightboxDialog(
    images: List<String>,
    initialIndex: Int,
    siteName: String,
    postUrl: String?,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val pagerState = rememberPagerState(initialPage = initialIndex, pageCount = { images.size })
    var currentScale by remember { mutableFloatStateOf(1f) }
    var currentOffset by remember { mutableStateOf(Offset.Zero) }

    LaunchedEffect(pagerState.currentPage) {
        currentScale = 1f
        currentOffset = Offset.Zero
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
        ) {
            HorizontalPager(
                state = pagerState,
                userScrollEnabled = currentScale <= 1.02f,
                modifier = Modifier.fillMaxSize()
            ) { pageIdx ->
                val rawUrl = images[pageIdx]
                val primaryUrl = remember(rawUrl) {
                    com.ddaeany0919.insightdeal.data.util.ImageUrlResolver.resolve(rawUrl, targetWidth = 1200)
                }
                val fallbackUrl = remember(rawUrl) {
                    com.ddaeany0919.insightdeal.data.util.ImageUrlResolver.getFallbackBackendProxyUrl(rawUrl)
                }
                var activeUrl by remember(primaryUrl) { mutableStateOf(primaryUrl) }
                var loadFailed by remember(primaryUrl) { mutableStateOf(false) }
                var containerSize by remember { mutableStateOf(IntSize.Zero) }

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .onSizeChanged { containerSize = it }
                        .pointerInput(pageIdx) {
                            detectTapGestures(
                                onDoubleTap = {
                                    if (currentScale > 1.02f) {
                                        currentScale = 1f
                                        currentOffset = Offset.Zero
                                    } else {
                                        currentScale = 2.5f
                                        currentOffset = Offset.Zero
                                    }
                                }
                            )
                        }
                        .pointerInput(pageIdx) {
                            awaitEachGesture {
                                awaitFirstDown(requireUnconsumed = false)
                                do {
                                    val event = awaitPointerEvent()
                                    val pressedCount = event.changes.count { it.pressed }
                                    val zoomChange = event.calculateZoom()
                                    val panChange = event.calculatePan()
                                    if (pressedCount >= 2 || currentScale > 1.02f) {
                                        val nextScale = (currentScale * zoomChange).coerceIn(1f, 4f)
                                        val maxX = ((containerSize.width * (nextScale - 1f)) / 2f).coerceAtLeast(0f)
                                        val maxY = ((containerSize.height * (nextScale - 1f)) / 2f).coerceAtLeast(0f)
                                        val nextOffset = if (nextScale <= 1.02f) {
                                            Offset.Zero
                                        } else {
                                            Offset(
                                                x = (currentOffset.x + panChange.x).coerceIn(-maxX, maxX),
                                                y = (currentOffset.y + panChange.y).coerceIn(-maxY, maxY)
                                            )
                                        }
                                        currentScale = nextScale
                                        currentOffset = nextOffset
                                        event.changes.forEach { it.consume() }
                                    }
                                } while (event.changes.any { it.pressed })
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    if (loadFailed || activeUrl.isNullOrBlank()) {
                        BrandPlaceholder(
                            siteName = siteName,
                            modifier = Modifier
                                .fillMaxWidth(0.75f)
                                .heightIn(min = 200.dp, max = 260.dp)
                        )
                    } else {
                        AsyncImage(
                            model = ImageRequest.Builder(context)
                                .data(activeUrl)
                                .setHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) Chrome/120.0.0.0 Safari/537.36")
                                .setHeader("Referer", postUrl ?: "https://insightdeal.com/")
                                .crossfade(true)
                                .build(),
                            contentDescription = "상품 확대 이미지 ${pageIdx + 1}",
                            onError = {
                                if (
                                    fallbackUrl != null &&
                                    activeUrl != fallbackUrl &&
                                    !activeUrl.orEmpty().contains("/api/proxy-image", ignoreCase = true)
                                ) {
                                    activeUrl = fallbackUrl
                                } else {
                                    loadFailed = true
                                }
                            },
                            contentScale = ContentScale.Fit,
                            modifier = Modifier
                                .fillMaxSize()
                                .graphicsLayer {
                                    scaleX = currentScale
                                    scaleY = currentScale
                                    translationX = currentOffset.x
                                    translationY = currentOffset.y
                                }
                        )
                    }
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 12.dp)
                    .align(Alignment.TopCenter),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = RoundedCornerShape(999.dp),
                    color = Color(0xAA111827)
                ) {
                    Text(
                        text = "${pagerState.currentPage + 1} / ${images.size}",
                        color = Color.White,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                    )
                }
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier
                        .sizeIn(minWidth = 48.dp, minHeight = 48.dp)
                        .background(Color(0xAA111827), RoundedCornerShape(999.dp))
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "이미지 뷰어 닫기",
                        tint = Color.White
                    )
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 16.dp)
                    .align(Alignment.BottomCenter),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = RoundedCornerShape(999.dp),
                    color = Color(0xAA111827)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                    ) {
                        Text(
                            text = "두 손가락 확대 · 더블탭 1x/2.5x",
                            color = Color.White,
                            style = MaterialTheme.typography.labelSmall
                        )
                        if (currentScale > 1.02f) {
                            TextButton(
                                onClick = {
                                    currentScale = 1f
                                    currentOffset = Offset.Zero
                                },
                                modifier = Modifier.heightIn(min = 48.dp)
                            ) {
                                Text("원본 크기", color = Color(0xFF34D399), fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }
    }
}

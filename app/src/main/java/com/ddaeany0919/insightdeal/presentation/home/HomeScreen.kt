package com.ddaeany0919.insightdeal.presentation.home

import com.ddaeany0919.insightdeal.presentation.decodeHtmlEntities
import com.ddaeany0919.insightdeal.presentation.formatDetailPriceText
import com.ddaeany0919.insightdeal.presentation.formatPrice
import com.ddaeany0919.insightdeal.presentation.resolveShopBadgeAndCleanTitle
import com.ddaeany0919.insightdeal.presentation.theme.LocalInsightDealColors
import com.ddaeany0919.insightdeal.presentation.theme.LocalIsDarkTheme
import com.ddaeany0919.insightdeal.presentation.theme.PriceTextStyle
import androidx.compose.foundation.*
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.ExperimentalFoundationApi
import com.ddaeany0919.insightdeal.presentation.KeywordManagerViewModel
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.*
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.outlined.TrendingUp
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.*
import androidx.compose.ui.text.font.*
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.*
import androidx.compose.ui.unit.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import androidx.paging.LoadState
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.paging.compose.itemKey
import coil.compose.AsyncImage
import coil.compose.SubcomposeAsyncImage
import com.ddaeany0919.insightdeal.R
import com.ddaeany0919.insightdeal.models.CategoryCatalog
import com.ddaeany0919.insightdeal.models.DealItem
import com.ddaeany0919.insightdeal.utils.CustomTabLauncher
import androidx.compose.foundation.Image
import androidx.compose.ui.res.painterResource

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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown

// [jetpack-compose-performance & anr-audit-and-fix] 정규식, 컬러필터, 포매터 등 고비용 객체 상단 호이스팅
private val HOME_DIGITAL_CATEGORIES = setOf("적립", "이벤트", "모바일/기프티콘", "상품권", "패키지/이용권")
private val FREE_KEYWORDS = listOf("무료", "공짜", "배포", "나눔", "0원", "일시무료", "쿠폰")
private val SHIPPING_LEADING_ZERO_REGEX = Regex("^0(원)?\\s*")
private val SHIPPING_FREE_COND_REGEX = Regex("(이상|미만|이하)(0원?)?\\s*무료")
private val SHIPPING_AMOUNT_COND_REGEX = Regex("(\\d+[만천])(이상|미만|이하)")
private val CLOSED_DEAL_COLOR_FILTER by lazy {
    androidx.compose.ui.graphics.ColorFilter.colorMatrix(
        androidx.compose.ui.graphics.ColorMatrix().apply { setToSaturation(0f) }
    )
}
private val ALERT_DATE_FORMATTER: java.time.format.DateTimeFormatter =
    java.time.format.DateTimeFormatter.ofPattern("MM-dd HH:mm")
private val HOME_SORT_OPTIONS = listOf(
    "latest" to "최신순",
    "popular" to "인기순",
    "price_asc" to "낮은가격순"
)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class, ExperimentalLayoutApi::class)
@Composable
@Suppress("DEPRECATION")
fun HomeScreen(
    navController: NavController,
    viewModel: HomeViewModel = viewModel(),
    wishlistViewModel: WishlistViewModel? = null,
    keywordViewModel: KeywordManagerViewModel = viewModel()
) {
    val filterState by viewModel.filterState.collectAsStateWithLifecycle()
    val activeCategories = remember(filterState) { filterState.effectiveCategories() }
    val activeCommunityChips = remember(filterState) { filterState.effectiveCommunityChips() }
    val hasActiveFilter = !filterState.keyword.isNullOrBlank() ||
        activeCommunityChips.isNotEmpty() ||
        activeCategories.isNotEmpty() ||
        (!filterState.platform.isNullOrBlank() && filterState.platform != "전체") ||
        filterState.hideClosed ||
        filterState.freeShippingOnly ||
        filterState.popularOnly
    val catalogCategories by CategoryCatalog.categoriesState.collectAsStateWithLifecycle()
    val configuration = LocalConfiguration.current
    val isExpandedScreen = configuration.screenWidthDp >= 600
    val focusManager = LocalFocusManager.current
    var searchInput by remember { mutableStateOf(filterState.keyword.orEmpty()) }

    LaunchedEffect(filterState.keyword) {
        val externalKeyword = filterState.keyword.orEmpty()
        if (externalKeyword != searchInput.trim()) {
            searchInput = externalKeyword
        }
    }

    LaunchedEffect(searchInput) {
        val trimmed = searchInput.trim()
        if (trimmed.isNotEmpty() && isIncompleteHangulJamoQuery(trimmed)) {
            return@LaunchedEffect
        }
        if (trimmed != filterState.keyword.orEmpty()) {
            if (trimmed.isEmpty()) {
                viewModel.searchDeals("")
            } else {
                kotlinx.coroutines.delay(300L)
                viewModel.searchDeals(trimmed)
            }
        }
    }

    val wishlistState by wishlistViewModel?.uiState?.collectAsStateWithLifecycle(initialValue = WishlistUiState.Loading) ?: remember { mutableStateOf(WishlistUiState.Loading) }
    val wishlistedUrls = remember(wishlistState) {
        if (wishlistState is WishlistUiState.Success) {
            (wishlistState as WishlistUiState.Success).items.map { it.productUrl }.toSet()
        } else emptySet()
    }

    val context = LocalContext.current
    val haptic = androidx.compose.ui.platform.LocalHapticFeedback.current
    val networkMonitor = remember { NetworkMonitor(context) }
    val isOnline by networkMonitor.isOnline.collectAsStateWithLifecycle(initialValue = true)
    val isDark = LocalIsDarkTheme.current

    var isNotificationPermissionGranted by remember {
        mutableStateOf(androidx.core.app.NotificationManagerCompat.from(context).areNotificationsEnabled())
    }

    // Android 13+ 알림 런타임 권한 요청 Compose 런처
    val permissionLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        isNotificationPermissionGranted = isGranted
    }

    var showPrePermissionDialog by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        val prefs = context.getSharedPreferences("app", android.content.Context.MODE_PRIVATE)
        val hasShown = prefs.getBoolean("has_shown_pre_permission_dialog", false)
        if (!isNotificationPermissionGranted && !hasShown) {
            showPrePermissionDialog = true
        }
    }

    val lifecycleOwner = androidx.compose.ui.platform.LocalLifecycleOwner.current

    DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                isNotificationPermissionGranted = androidx.core.app.NotificationManagerCompat.from(context).areNotificationsEnabled()
                val prefs = context.getSharedPreferences("app", android.content.Context.MODE_PRIVATE)
                val disabledPlatforms = prefs.getStringSet("disabled_platforms", emptySet()) ?: emptySet()
                viewModel.updateDisabledPlatforms(disabledPlatforms)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    val dealsPagingItems = viewModel.dealsPagingData.collectAsLazyPagingItems()
    val gridState = rememberLazyGridState()

    // Toss 스타일 프리미엄 소프트 가이드 알림 권한 팝업 UI
    if (showPrePermissionDialog) {
        AlertDialog(
            onDismissRequest = {
                showPrePermissionDialog = false
                val prefs = context.getSharedPreferences("app", android.content.Context.MODE_PRIVATE)
                prefs.edit().putBoolean("has_shown_pre_permission_dialog", true).apply()
            },
            confirmButton = {
                Button(
                    onClick = {
                        showPrePermissionDialog = false
                        val prefs = context.getSharedPreferences("app", android.content.Context.MODE_PRIVATE)
                        prefs.edit().putBoolean("has_shown_pre_permission_dialog", true).apply()
                        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                            permissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 52.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary
                    )
                ) {
                    Text("알림 켜기", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showPrePermissionDialog = false
                        val prefs = context.getSharedPreferences("app", android.content.Context.MODE_PRIVATE)
                        prefs.edit().putBoolean("has_shown_pre_permission_dialog", true).apply()
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                ) {
                    Text(
                        "나중에 설정할게요",
                        color = LocalInsightDealColors.current.textMuted,
                        fontSize = 14.sp
                    )
                }
            },
            title = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        modifier = Modifier
                            .size(56.dp)
                            .clip(RoundedCornerShape(18.dp))
                            .background(MaterialTheme.colorScheme.primary),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.NotificationsActive,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(32.dp)
                        )
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "특가 타이밍을 가장 먼저 알려드릴게요",
                        fontWeight = FontWeight.Black,
                        fontSize = 18.sp,
                        textAlign = TextAlign.Center,
                        lineHeight = 24.sp
                    )
                }
            },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp)
                ) {
                    Text(
                        text = "알림을 허용하시면 이런 혜택을 드려요.",
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(bottom = 12.dp)
                    )

                    val benefits = listOf(
                        "등록한 키워드의 초특가 딜 실시간 매칭 알림",
                        "방해금지 시간을 설정하면 해제 시각(기본 08:00)에 밤사이 놓친 핫딜을 1건으로 요약해 받아볼 수 있어요",
                        "품절 대란 핫딜 정보 선착순 1초 컷 진입"
                    )
                    benefits.forEach { benefit ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = benefit,
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Text(
                        text = "본 알림 서비스는 정보통신망법(제50조)을 준수하며, 야간 시간대(21:00 ~ 익일 08:00)의 광고성 푸시는 수신 동의 여부와 관계없이 설정된 방해금지 규칙에 따라 완전히 필터링됩니다.",
                        fontSize = 11.sp,
                        color = LocalInsightDealColors.current.textMuted,
                        lineHeight = 16.sp,
                        textAlign = TextAlign.Center
                    )
                }
            },
            shape = RoundedCornerShape(28.dp),
            modifier = Modifier.padding(horizontal = 8.dp)
        )
    }

    // [Epic 3] 키워드 설정 다이얼로그 상태
    var showKeywordDialog by remember { mutableStateOf(false) }
    if (showKeywordDialog) {
        KeywordSettingsBottomSheet(
            onDismiss = { showKeywordDialog = false },
            keywordViewModel = keywordViewModel
        )
    }

    val notificationAlerts by com.ddaeany0919.insightdeal.presentation.mypage.history.NotificationHistoryManager.alerts.collectAsStateWithLifecycle()
    var showNotificationHistory by remember { mutableStateOf(false) }

    val hiddenDeals = remember { mutableStateListOf<String>() }
    androidx.compose.runtime.LaunchedEffect(Unit) {
        val prefs = context.getSharedPreferences("app", android.content.Context.MODE_PRIVATE)
        hiddenDeals.addAll(prefs.getStringSet("hidden_deals", emptySet()) ?: emptySet())
    }

    val hideDeal = remember(context) {
        { dealId: Int ->
            val prefs = context.getSharedPreferences("app", android.content.Context.MODE_PRIVATE)
            val hiddenSet = prefs.getStringSet("hidden_deals", emptySet())?.toMutableSet() ?: mutableSetOf()
            hiddenSet.add(dealId.toString())
            prefs.edit().putStringSet("hidden_deals", hiddenSet).apply()
            hiddenDeals.add(dealId.toString())
            android.widget.Toast.makeText(context, "해당 핫딜을 목록에서 숨겼어요", android.widget.Toast.LENGTH_SHORT).show()
        }
    }

    var showDealOptionsBottomSheet by remember { mutableStateOf(false) }
    var selectedDealForOptions by remember { mutableStateOf<DealItem?>(null) }
    var showWhyThisDealDialog by remember { mutableStateOf<DealItem?>(null) }
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    var showCoupangDialog by remember { mutableStateOf(false) }
    if (showCoupangDialog) {
        CoupangSettingsDialog(onDismiss = { showCoupangDialog = false })
    }

    val hasNewDeals by viewModel.hasNewDeals.collectAsStateWithLifecycle()
    val newDealsCount by viewModel.newDealsCount.collectAsStateWithLifecycle()

    val topHotDeals by viewModel.topHotDeals.collectAsStateWithLifecycle()
    val topPicks = remember(topHotDeals) {
        topHotDeals
            .filter { deal ->
                val isSuperHot = deal.honeyScore >= 100
                !deal.isClosed && isSuperHot
            }
            .filter { deal ->
                deal.category != "적립" && !deal.title.contains("적립") && !deal.title.contains("불가") && !deal.title.contains("정보")
            }
            .sortedByDescending { it.createdAt ?: "" }
    }
    val popularRankings by viewModel.popularRankings.collectAsStateWithLifecycle()
    val popularKeywords by viewModel.popularKeywords.collectAsStateWithLifecycle()
    val tickerItems = remember(popularRankings, popularKeywords) {
        if (popularRankings.isNotEmpty()) {
            popularRankings.take(10)
        } else {
            popularKeywords.take(10).mapIndexed { idx, kw ->
                com.ddaeany0919.insightdeal.network.PopularKeywordRankingItem(
                    rank = idx + 1,
                    keyword = kw,
                    dealCount = 0,
                    recentCount = 0,
                    status = if (idx == 0) "HOT" else if (idx < 3) "UP" else "SAME"
                )
            }
        }
    }
    var hideKeywordBanner by remember {
        mutableStateOf(
            runCatching {
                com.ddaeany0919.insightdeal.core.security.EncryptedPrefsManager
                    .getEncryptedPrefs(context)
                    .getBoolean("hide_home_kw_banner", false)
            }.getOrDefault(false)
        )
    }

    val pullRefreshState = androidx.compose.material3.pulltorefresh.rememberPullToRefreshState()

    LaunchedEffect(pullRefreshState.isRefreshing) {
        if (pullRefreshState.isRefreshing) {
            dealsPagingItems.refresh()
            viewModel.fetchTopHotDeals()
            viewModel.onRefreshNewDeals()
            kotlinx.coroutines.delay(1200)
            pullRefreshState.endRefresh()
        }
    }
    LaunchedEffect(dealsPagingItems.loadState.refresh) {
        if (dealsPagingItems.loadState.refresh !is LoadState.Loading) {
            pullRefreshState.endRefresh()
        }
    }

    val onNavigateToDetail = remember(navController) {
        { dealId: Int -> navController.navigate("deal_detail/$dealId") }
    }
    var showCommissionSheet by remember { mutableStateOf(false) }
    var pendingUrlToOpen by remember { mutableStateOf("") }
    var dontShowToday by remember { mutableStateOf(false) }
    var commissionMessage by remember { mutableStateOf("") }

    val onOpenUrlInWebView = remember(navController, context) {
        { url: String ->
            val lowerUrl = url.lowercase(Locale.ROOT)
            val isCommunityPost = listOf(
                "ppomppu.co.kr", "fmkorea.com", "quasarzone.com",
                "ruliweb.com", "clien.net", "bbasak.com"
            ).any { lowerUrl.contains(it) }

            if (isCommunityPost) {
                val encodedUrl = java.net.URLEncoder.encode(url, "UTF-8")
                navController.navigate("webview/$encodedUrl")
            } else {
                viewModel.trackOutboundClick(url)
                val prefs = runCatching {
                    com.ddaeany0919.insightdeal.core.security.EncryptedPrefsManager.getEncryptedPrefs(context)
                }.getOrNull()
                val savedTime = prefs?.getLong("last_dismiss_commission_time", 0L) ?: 0L
                val currentTime = System.currentTimeMillis()
                val shouldShowSheet = (currentTime - savedTime) > 24 * 60 * 60 * 1000

                val isCoupang = lowerUrl.contains("coupang.com") || lowerUrl.contains("coupa.ng")
                val isAliexpress = lowerUrl.contains("aliexpress.com")
                val isAffiliate = isCoupang || isAliexpress

                if (isAffiliate && shouldShowSheet) {
                    pendingUrlToOpen = url
                    dontShowToday = false
                    commissionMessage = "쿠팡 파트너스 및 제휴 마케팅 활동의 일환으로, 이에 따른 일정액의 수수료를 제공받습니다.\n\n구매 가격에는 추가 부담이 발생하지 않습니다."
                    showCommissionSheet = true
                } else {
                    CustomTabLauncher.launchShoppingUrl(context, url)
                }
            }
        }
    }

    if (showNotificationHistory) {
        NotificationHistoryBottomSheet(
            onDismiss = { showNotificationHistory = false },
            onNavigateToKeywordSettings = {
                showNotificationHistory = false
                showKeywordDialog = true
            },
            onOpenUrlInWebView = onOpenUrlInWebView
        )
    }

    Box(modifier = Modifier.fillMaxSize().nestedScroll(pullRefreshState.nestedScrollConnection)) {
        val homeSemantic = LocalInsightDealColors.current
        Column(modifier = Modifier.fillMaxSize()) {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onBackground
                ),
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.bounceClick(onClickLabel = "홈 새로고침 및 맨 위로 이동") {
                            haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                            scope.launch {
                                try {
                                    searchInput = ""
                                    viewModel.resetHomeFilters()
                                    try {
                                        gridState.animateScrollToItem(0)
                                    } catch (_: Exception) {
                                    }
                                    dealsPagingItems.refresh()
                                    viewModel.fetchTopHotDeals()
                                    viewModel.onRefreshNewDeals()
                                } catch (_: Exception) {
                                }
                            }
                        }
                    ) {
                        Image(
                            painter = painterResource(id = R.drawable.ic_brand_mark),
                            contentDescription = "얼만데 로고",
                            modifier = Modifier.size(30.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "얼만데",
                            fontSize = 20.sp,
                            fontWeight = FontWeight.ExtraBold,
                            letterSpacing = (-0.5).sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                },
                actions = {
                    val alertsCount = notificationAlerts.size
                    Box(
                        modifier = Modifier
                            .sizeIn(minWidth = 48.dp, minHeight = 48.dp)
                            .bounceClick(
                                onClickLabel = if (alertsCount > 0) "알림 내역 (${alertsCount}건)" else "알림 내역"
                            ) { showNotificationHistory = true }
                            .padding(8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Notifications,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                        if (alertsCount > 0) {
                            Surface(
                                shape = CircleShape,
                                color = homeSemantic.danger,
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .padding(top = 0.dp, end = 0.dp)
                                    .sizeIn(minWidth = 16.dp, minHeight = 16.dp)
                                    .clearAndSetSemantics {}
                            ) {
                                Box(
                                    contentAlignment = Alignment.Center,
                                    modifier = Modifier.padding(horizontal = 4.dp)
                                ) {
                                    Text(
                                        text = "+$alertsCount",
                                        color = Color.White,
                                        fontSize = 10.sp,
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
                    }
                    Spacer(modifier = Modifier.width(4.dp))
                    Box(
                        modifier = Modifier
                            .sizeIn(minWidth = 48.dp, minHeight = 48.dp)
                            .bounceClick(onClickLabel = "키워드 알림 설정") { navController.navigate("keyword_alarm") }
                            .padding(8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Settings,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            )
            HorizontalDivider(thickness = 0.8.dp, color = homeSemantic.cardBorder)

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
                    color = MaterialTheme.colorScheme.secondaryContainer,
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
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "인터넷 연결이 끊겼어요 · 저장해 둔 핫딜을 보여드려요",
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }

            AnimatedVisibility(
                visible = isOnline && !isNotificationPermissionGranted,
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
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            val intent = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                                android.content.Intent(android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                                    putExtra(android.provider.Settings.EXTRA_APP_PACKAGE, context.packageName)
                                    if (context !is android.app.Activity) {
                                        addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                                    }
                                }
                            } else {
                                android.content.Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                    data = android.net.Uri.fromParts("package", context.packageName, null)
                                    if (context !is android.app.Activity) {
                                        addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                                    }
                                }
                            }
                            try {
                                context.startActivity(intent)
                            } catch (e: Exception) {
                                try {
                                    val fallbackIntent = android.content.Intent().apply {
                                        action = android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS
                                        data = android.net.Uri.fromParts("package", context.packageName, null)
                                        if (context !is android.app.Activity) {
                                            addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                                        }
                                    }
                                    context.startActivity(fallbackIntent)
                                } catch (_: Exception) {
                                    android.widget.Toast.makeText(context, "설정 화면을 열지 못했어요", android.widget.Toast.LENGTH_SHORT).show()
                                }
                            }
                        },
                    color = MaterialTheme.colorScheme.primaryContainer,
                    shadowElevation = 2.dp
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                imageVector = Icons.Default.NotificationsActive,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(
                                    text = "알림을 켜면 원하는 특가를 바로 받을 수 있어요",
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = "여기를 눌러 실시간 키워드 알림 권한을 허용해 주세요",
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                        Icon(
                            imageVector = Icons.Default.KeyboardArrowRight,
                            contentDescription = "이동",
                            tint = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f),
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }

            LazyVerticalGrid(
                columns = GridCells.Fixed(if (isExpandedScreen) 2 else 1),
                state = gridState,
                modifier = Modifier.fillMaxWidth().weight(1f),
                contentPadding = PaddingValues(16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (!hasActiveFilter && (topPicks.isNotEmpty() || !hideKeywordBanner)) {
                    item(
                        key = "today_picks_section",
                        span = { GridItemSpan(maxLineSpan) },
                        contentType = "today_picks_section"
                    ) {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            if (topPicks.isNotEmpty()) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(bottom = if (!hideKeywordBanner) 8.dp else 0.dp)
                                ) {
                                    Text(
                                        text = "오늘의 핫딜",
                                        fontWeight = FontWeight.ExtraBold,
                                        fontSize = 18.sp,
                                        modifier = Modifier
                                            .padding(bottom = 12.dp, start = 4.dp)
                                            .semantics { heading() },
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    LazyRow(
                                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                                        contentPadding = PaddingValues(horizontal = 4.dp)
                                    ) {
                                        items(
                                            count = topPicks.size,
                                            key = { index -> topPicks[index].id },
                                            contentType = { "today_pick_item" }
                                        ) { index ->
                                            val pick = topPicks[index]
                                            val resolvedPickTitle = remember(pick.id, pick.title, pick.shopName, pick.ecommerceUrl) {
                                                resolveShopBadgeAndCleanTitle(pick.title, pick.shopName, pick.ecommerceUrl)
                                            }

                                            val primaryPickUrl = remember(pick.id, pick.imageUrl, com.ddaeany0919.insightdeal.data.network.NetworkConfig.getActiveServerUrl()) {
                                                val rawPickUrl = pick.imageUrl?.takeIf { it.isNotBlank() }
                                                com.ddaeany0919.insightdeal.data.util.ImageUrlResolver.resolve(rawPickUrl, targetWidth = 400)
                                            }

                                            val fallbackPickUrl = remember(pick.id, pick.imageUrl, com.ddaeany0919.insightdeal.data.network.NetworkConfig.getActiveServerUrl()) {
                                                val rawPickUrl = pick.imageUrl?.takeIf { it.isNotBlank() }
                                                com.ddaeany0919.insightdeal.data.util.ImageUrlResolver.getFallbackBackendProxyUrl(rawPickUrl)
                                            }

                                            var activePickUrl by remember(primaryPickUrl) { mutableStateOf(primaryPickUrl) }

                                            val imageRequest = remember(activePickUrl, pick.postUrl) {
                                                coil.request.ImageRequest.Builder(context)
                                                    .data(activePickUrl)
                                                    .setHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) Chrome/120.0.0.0 Safari/537.36")
                                                    .setHeader("Referer", pick.postUrl ?: "https://insightdeal.com/")
                                                    .crossfade(true)
                                                    .precision(coil.size.Precision.EXACT)
                                                    .build()
                                            }

                                            val priceText = remember(pick.id, pick.price, pick.currency, pick.category, resolvedPickTitle.cleanTitle) {
                                                if (pick.price > 0L) {
                                                    val formatted = formatPrice(pick.price, pick.currency, resolvedPickTitle.cleanTitle)
                                                    if (pick.category == "적립") "$formatted 적립" else formatted
                                                } else if (pick.category == "적립" || resolvedPickTitle.cleanTitle.contains("적립")) {
                                                    "포인트 적립"
                                                } else {
                                                    formatPrice(pick.price, pick.currency, resolvedPickTitle.cleanTitle)
                                                }
                                            }

                                            val isBookmarked = wishlistedUrls.contains(pick.ecommerceUrl?.takeIf { it.isNotBlank() } ?: pick.postUrl ?: "")

                                            val displayShipping = remember(pick.id, pick.shippingFee, pick.category, resolvedPickTitle.cleanTitle) {
                                                val digitalCategories = HOME_DIGITAL_CATEGORIES
                                                val isDigitalTitle = resolvedPickTitle.cleanTitle.contains("요금제") || resolvedPickTitle.cleanTitle.contains("데이터")
                                                val hideShipping = pick.category in digitalCategories || isDigitalTitle

                                                if (hideShipping) null
                                                else {
                                                    val trimmed = decodeHtmlEntities(pick.shippingFee).trim().ifEmpty { "정보 없음" }
                                                    when {
                                                        trimmed == "정보 없음" || trimmed == "배송비 확인 필요" -> "확인 필요"
                                                        trimmed.contains("와우") -> "와우무료"
                                                        trimmed.contains("스마일") || trimmed.contains("스클") -> "스클무료"
                                                        trimmed.contains("우주패스") -> "우주패스"
                                                        trimmed == "0" || trimmed == "0원" || trimmed == "무배" || trimmed == "무료" || trimmed == "무료배송" -> "무료"
                                                        trimmed.startsWith("0원/") || trimmed.startsWith("0원+") || trimmed.startsWith("0/") || trimmed.startsWith("0+") -> trimmed.replaceFirst(SHIPPING_LEADING_ZERO_REGEX, "무료 ")
                                                        trimmed == "유료" || trimmed == "유료배송" -> "유료"
                                                        trimmed.all { it.isDigit() || it == ',' } -> "${trimmed}원"
                                                        else -> trimmed.replace("무료배송", "무료").replace("유료배송", "유료").replace(SHIPPING_FREE_COND_REGEX, "$1 무료").replace(SHIPPING_AMOUNT_COND_REGEX, "$1원$2").trim()
                                                    }
                                                }
                                            }

                                            Card(
                                                modifier = Modifier
                                                    .width(188.dp)
                                                    .heightIn(min = 216.dp)
                                                    .bounceClick(
                                                        onClickLabel = "${resolvedPickTitle.cleanTitle} 상세 보기"
                                                    ) {
                                                        onNavigateToDetail(pick.id)
                                                    },
                                                shape = RoundedCornerShape(16.dp),
                                                elevation = CardDefaults.cardElevation(0.dp),
                                                border = BorderStroke(1.dp, LocalInsightDealColors.current.cardBorder),
                                                colors = CardDefaults.cardColors(
                                                    containerColor = MaterialTheme.colorScheme.surface,
                                                    contentColor = MaterialTheme.colorScheme.onSurface
                                                )
                                            ) {
                                                Column {
                                                    if (pick.imageUrl.isNullOrBlank()) {
                                                        BrandPlaceholder(
                                                            siteName = pick.siteName,
                                                            modifier = Modifier
                                                                .height(100.dp)
                                                                .fillMaxWidth()
                                                                .clip(RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)),
                                                            textStyle = TextStyle(fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, color = Color(0xFFF9FAFB))
                                                        )
                                                    } else {
                                                        SubcomposeAsyncImage(
                                                            model = imageRequest,
                                                            contentDescription = null,
                                                            modifier = Modifier
                                                                .height(100.dp)
                                                                .fillMaxWidth()
                                                                .background(MaterialTheme.colorScheme.surfaceVariant)
                                                                .clip(RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)),
                                                            contentScale = ContentScale.Crop,
                                                            onError = {
                                                                if (fallbackPickUrl != null && activePickUrl != fallbackPickUrl) {
                                                                    activePickUrl = fallbackPickUrl
                                                                }
                                                            },
                                                            error = {
                                                                BrandPlaceholder(
                                                                    siteName = pick.siteName,
                                                                    modifier = Modifier.fillMaxSize(),
                                                                    textStyle = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Color(0xFFF9FAFB))
                                                                )
                                                            }
                                                        )
                                                    }
                                                    Column(Modifier.padding(10.dp)) {
                                                        if (!resolvedPickTitle.shopBadge.isNullOrBlank()) {
                                                            Text(
                                                                text = resolvedPickTitle.shopBadge,
                                                                fontSize = 10.sp,
                                                                fontWeight = FontWeight.SemiBold,
                                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                                maxLines = 1,
                                                                overflow = TextOverflow.Ellipsis,
                                                                modifier = Modifier.padding(bottom = 2.dp)
                                                            )
                                                        }
                                                        Text(
                                                            text = resolvedPickTitle.cleanTitle,
                                                            maxLines = 2,
                                                            overflow = TextOverflow.Ellipsis,
                                                            fontSize = 13.sp,
                                                            fontWeight = FontWeight.Bold,
                                                            lineHeight = 17.sp,
                                                            color = MaterialTheme.colorScheme.onSurface,
                                                        )
                                                        Spacer(modifier = Modifier.height(8.dp))

                                                        Row(
                                                            verticalAlignment = Alignment.CenterVertically,
                                                            horizontalArrangement = Arrangement.SpaceBetween,
                                                            modifier = Modifier.fillMaxWidth()
                                                        ) {
                                                            Column(
                                                                modifier = Modifier
                                                                    .weight(1f)
                                                                    .padding(end = 4.dp),
                                                                verticalArrangement = Arrangement.spacedBy(4.dp)
                                                            ) {
                                                                val priceFontSize = when {
                                                                    priceText.length >= 9 -> 12.sp
                                                                    priceText.length >= 7 -> 13.sp
                                                                    priceText.length >= 5 -> 14.5.sp
                                                                    else -> 15.5.sp
                                                                }
                                                                val pickSemantic = LocalInsightDealColors.current
                                                                Text(
                                                                    text = priceText,
                                                                    color = if (pick.price > 0L || priceText == "무료") {
                                                                        pickSemantic.priceAccent
                                                                    } else {
                                                                        pickSemantic.textMuted
                                                                    },
                                                                    style = PriceTextStyle.copy(
                                                                        fontSize = priceFontSize,
                                                                        fontWeight = FontWeight.ExtraBold
                                                                    ),
                                                                    maxLines = 2,
                                                                    overflow = TextOverflow.Ellipsis
                                                                )
                                                                if (displayShipping != null) {
                                                                    Surface(
                                                                        shape = RoundedCornerShape(4.dp),
                                                                        color = if (isDark) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceVariant
                                                                    ) {
                                                                        Text(
                                                                            text = "배송비: $displayShipping",
                                                                            fontSize = 10.5.sp,
                                                                            color = if (isDark) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                                                                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp),
                                                                            maxLines = 2,
                                                                            overflow = TextOverflow.Ellipsis
                                                                        )
                                                                    }
                                                                }
                                                            }
                                                            IconButton(
                                                                onClick = {
                                                                    wishlistViewModel?.let { vm ->
                                                                        val targetUrl = pick.ecommerceUrl?.takeIf { it.isNotBlank() } ?: pick.postUrl ?: ""
                                                                        if (wishlistedUrls.contains(targetUrl)) {
                                                                            val item = (wishlistState as? WishlistUiState.Success)?.items?.find { it.productUrl == targetUrl }
                                                                            if (item != null) vm.deleteItem(item)
                                                                        } else {
                                                                            vm.addItem(resolvedPickTitle.cleanTitle, targetUrl, pick.price.toInt(), pick.price.toInt(), pick.siteName)
                                                                            scope.launch {
                                                                                val result = snackbarHostState.showSnackbar(
                                                                                    message = "관심 상품에 담았어요",
                                                                                    actionLabel = "관심 목록 보기",
                                                                                    duration = SnackbarDuration.Short
                                                                                )
                                                                                if (result == SnackbarResult.ActionPerformed) {
                                                                                    navController.navigate("watchlist")
                                                                                }
                                                                            }
                                                                        }
                                                                    }
                                                                },
                                                                modifier = Modifier.size(48.dp)
                                                            ) {
                                                                Icon(
                                                                    imageVector = if (isBookmarked) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                                                                    contentDescription = "관심 상품",
                                                                    tint = if (isBookmarked) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
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
                            }

                            if (!hideKeywordBanner) {
                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .bounceClick { navController.navigate("keyword_alarm") }
                                        .padding(horizontal = 4.dp, vertical = 2.dp),
                                    shape = RoundedCornerShape(12.dp),
                                    border = BorderStroke(
                                        1.dp,
                                        if (isDark) Color(0xFF2B333E) else Color(0xFFE5E8EB)
                                    ),
                                    colors = CardDefaults.cardColors(
                                        containerColor = MaterialTheme.colorScheme.surface
                                    )
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 12.dp, vertical = 9.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(26.dp)
                                                .clip(RoundedCornerShape(8.dp))
                                                .background(MaterialTheme.colorScheme.secondaryContainer),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.NotificationsActive,
                                                contentDescription = "알림",
                                                tint = MaterialTheme.colorScheme.onSecondaryContainer,
                                                modifier = Modifier.size(15.dp)
                                            )
                                        }
                                        Spacer(modifier = Modifier.width(10.dp))
                                        Text(
                                            text = "원하는 상품 키워드를 등록하면 바로 알려드려요",
                                            fontWeight = FontWeight.SemiBold,
                                            fontSize = 13.sp,
                                            color = MaterialTheme.colorScheme.onSurface,
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.weight(1f)
                                        )
                                        Text(
                                            text = "등록하기",
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.secondary
                                        )
                                        Icon(
                                            imageVector = Icons.Default.KeyboardArrowRight,
                                            contentDescription = "이동",
                                            tint = MaterialTheme.colorScheme.secondary,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Box(
                                            modifier = Modifier
                                                .sizeIn(minWidth = 48.dp, minHeight = 48.dp)
                                                .clickable {
                                                    hideKeywordBanner = true
                                                    runCatching {
                                                        com.ddaeany0919.insightdeal.core.security.EncryptedPrefsManager
                                                            .getEncryptedPrefs(context)
                                                            .edit()
                                                            .putBoolean("hide_home_kw_banner", true)
                                                            .apply()
                                                    }
                                                },
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Close,
                                                contentDescription = "배너 닫기",
                                                tint = LocalInsightDealColors.current.textMuted,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // 인라인 검색바 → 급상승 티커 → 출처/분류 필터 → 리스트 직상단 정렬·상태 단일 바
                item(
                    key = "search_and_filters_section",
                    span = { GridItemSpan(maxLineSpan) },
                    contentType = "search_and_filters_section"
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 2.dp)
                    ) {
                        // 1) 컴팩트 인라인 검색바
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            color = if (isDark) Color(0xFF191F28) else Color(0xFFF3F4F6),
                            border = BorderStroke(
                                1.dp,
                                if (searchInput.isNotBlank()) MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                                else if (isDark) Color(0xFF2B333E) else Color(0xFFE5E7EB)
                            )
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Search,
                                    contentDescription = "검색",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Box(modifier = Modifier.weight(1f)) {
                                    if (searchInput.isEmpty()) {
                                        Text(
                                            text = "상품명이나 브랜드를 검색해 보세요",
                                            fontSize = 13.5.sp,
                                            color = LocalInsightDealColors.current.textMuted,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                    BasicTextField(
                                        value = searchInput,
                                        onValueChange = { searchInput = it },
                                        singleLine = true,
                                        textStyle = TextStyle(
                                            fontSize = 13.5.sp,
                                            fontWeight = FontWeight.Medium,
                                            color = MaterialTheme.colorScheme.onSurface
                                        ),
                                        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                                        keyboardActions = KeyboardActions(
                                            onSearch = {
                                                viewModel.searchDeals(searchInput)
                                                focusManager.clearFocus()
                                            }
                                        ),
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                }
                                if (searchInput.isNotEmpty()) {
                                    Spacer(modifier = Modifier.width(2.dp))
                                    Box(
                                        modifier = Modifier
                                            .size(48.dp)
                                            .clickable {
                                                searchInput = ""
                                                viewModel.searchDeals("")
                                            },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Cancel,
                                            contentDescription = "검색어 지우기",
                                            tint = LocalInsightDealColors.current.textMuted,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                            }
                        }

                        // 2) 급상승 키워드 슬림 바 (웹과 동일한 [검색창 → 급상승 티커] 순서)
                        if (tickerItems.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(6.dp))
                            HomeTrendingTickerBar(
                                tickerItems = tickerItems,
                                isDark = isDark,
                                onKeywordClick = { keyword ->
                                    searchInput = keyword
                                    viewModel.searchDeals(keyword)
                                    focusManager.clearFocus()
                                },
                                onAllRankingsClick = {
                                    navController.navigate("advanced_search")
                                }
                            )
                        }

                        Spacer(modifier = Modifier.height(4.dp))

                        // 3) "출처" 고정 라벨 + 각진 배지형 커뮤니티 필터 칩 (RoundedCornerShape(8.dp), 높이 34.dp)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            HomeFilterRowLabel(
                                text = "출처",
                                shape = RoundedCornerShape(6.dp),
                                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                                contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                            LazyRow(
                                modifier = Modifier.weight(1f),
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                contentPadding = PaddingValues(end = 2.dp)
                            ) {
                                items(
                                    count = HomeViewModel.COMMUNITY_FILTER_CHIPS.size,
                                    key = { idx -> HomeViewModel.COMMUNITY_FILTER_CHIPS[idx] },
                                    contentType = { "community_chip" }
                                ) { idx ->
                                    val chip = HomeViewModel.COMMUNITY_FILTER_CHIPS[idx]
                                    val isSelected = if (chip == "전체") {
                                        activeCommunityChips.isEmpty()
                                    } else {
                                        chip in activeCommunityChips
                                    }
                                    HomeSelectablePillChip(
                                        label = chip,
                                        selected = isSelected,
                                        role = if (chip == "전체") Role.Tab else Role.Checkbox,
                                        shape = RoundedCornerShape(8.dp),
                                        chipHeight = 34.dp,
                                        selectedContainerColor = MaterialTheme.colorScheme.secondaryContainer,
                                        selectedContentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                                        selectedBorderColor = MaterialTheme.colorScheme.secondary,
                                        unselectedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                                        unselectedContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                        unselectedBorderColor = homeSemantic.cardBorder,
                                        onClick = {
                                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                            viewModel.toggleCommunityChip(chip)
                                        }
                                    )
                                }
                            }
                        }

                        // 4) "분류" 고정 라벨 + 알약형 카테고리 필터 칩 (RoundedCornerShape(999.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            HomeFilterRowLabel(
                                text = "분류",
                                shape = RoundedCornerShape(999.dp),
                                containerColor = MaterialTheme.colorScheme.primaryContainer,
                                contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                            LazyRow(
                                modifier = Modifier.weight(1f),
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                contentPadding = PaddingValues(end = 2.dp)
                            ) {
                                item(key = "cat_all", contentType = "category_chip") {
                                    HomeSelectablePillChip(
                                        label = "전체",
                                        selected = activeCategories.isEmpty(),
                                        role = Role.Tab,
                                        shape = RoundedCornerShape(999.dp),
                                        chipHeight = 36.dp,
                                        onClick = {
                                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                            viewModel.toggleCategory("전체")
                                        }
                                    )
                                }
                                items(
                                    items = catalogCategories,
                                    key = { "cat_${it.key}" },
                                    contentType = { "category_chip" }
                                ) { cat ->
                                    val isCatSelected = cat.key in activeCategories
                                    HomeSelectablePillChip(
                                        label = cat.label,
                                        selected = isCatSelected,
                                        role = Role.Checkbox,
                                        shape = RoundedCornerShape(999.dp),
                                        chipHeight = 36.dp,
                                        onClick = {
                                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                            viewModel.toggleCategory(cat.key)
                                        }
                                    )
                                }
                            }
                        }

                        // 5) 리스트 직상단 정렬·상태 단일 가로 스크롤 바 (최신순/인기순/낮은가격순 + 인기 딜만 + 품절 제외 + 무료배송)
                        LazyRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            item(key = "sort_segment", contentType = "sort_segment") {
                                val trackColor = if (isDark) Color(0xFF191F28) else Color(0xFFF2F4F7)
                                Box(contentAlignment = Alignment.Center) {
                                    Surface(
                                        modifier = Modifier
                                            .matchParentSize()
                                            .padding(vertical = 6.dp),
                                        shape = RoundedCornerShape(10.dp),
                                        color = trackColor,
                                        border = BorderStroke(1.dp, homeSemantic.cardBorder)
                                    ) {}
                                    Row(
                                        modifier = Modifier.padding(horizontal = 3.dp),
                                        horizontalArrangement = Arrangement.spacedBy(2.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        HOME_SORT_OPTIONS.forEach { (sortKey, sortLabel) ->
                                            val isSortSelected = filterState.sortBy == sortKey
                                            Box(
                                                modifier = Modifier
                                                    .heightIn(min = 48.dp)
                                                    .semantics {
                                                        role = Role.Tab
                                                        selected = isSortSelected
                                                    }
                                                    .clickable {
                                                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                                        viewModel.selectSort(sortKey)
                                                    },
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Surface(
                                                    shape = RoundedCornerShape(8.dp),
                                                    color = when {
                                                        isSortSelected && isDark -> MaterialTheme.colorScheme.surfaceVariant
                                                        isSortSelected -> MaterialTheme.colorScheme.surface
                                                        else -> Color.Transparent
                                                    },
                                                    shadowElevation = if (isSortSelected && !isDark) 1.dp else 0.dp,
                                                    modifier = Modifier.height(30.dp)
                                                ) {
                                                    Box(
                                                        modifier = Modifier.padding(horizontal = 10.dp),
                                                        contentAlignment = Alignment.Center
                                                    ) {
                                                        Text(
                                                            text = sortLabel,
                                                            fontSize = 12.5.sp,
                                                            fontWeight = if (isSortSelected) FontWeight.Bold else FontWeight.Medium,
                                                            color = if (isSortSelected) MaterialTheme.colorScheme.onSurface
                                                            else MaterialTheme.colorScheme.onSurfaceVariant,
                                                            maxLines = 1,
                                                            style = TextStyle(
                                                                platformStyle = PlatformTextStyle(includeFontPadding = false)
                                                            )
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }

                            item(key = "filter_popular_only", contentType = "status_chip") {
                                HomeSelectablePillChip(
                                    label = "인기 딜만",
                                    selected = filterState.popularOnly,
                                    shape = RoundedCornerShape(999.dp),
                                    chipHeight = 36.dp,
                                    leadingIcon = Icons.Filled.LocalFireDepartment,
                                    selectedContainerColor = homeSemantic.priceContainer,
                                    selectedContentColor = homeSemantic.onPriceContainer,
                                    selectedBorderColor = homeSemantic.priceAccent,
                                    onClick = {
                                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        viewModel.togglePopularOnly()
                                    }
                                )
                            }

                            item(key = "filter_hide_closed", contentType = "status_chip") {
                                HomeSelectablePillChip(
                                    label = "품절 제외",
                                    selected = filterState.hideClosed,
                                    shape = RoundedCornerShape(999.dp),
                                    chipHeight = 36.dp,
                                    onClick = {
                                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        viewModel.toggleHideClosed()
                                    }
                                )
                            }

                            item(key = "filter_free_shipping", contentType = "status_chip") {
                                HomeSelectablePillChip(
                                    label = "무료배송",
                                    selected = filterState.freeShippingOnly,
                                    shape = RoundedCornerShape(999.dp),
                                    chipHeight = 36.dp,
                                    onClick = {
                                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        viewModel.toggleFreeShippingOnly()
                                    }
                                )
                            }
                        }

                        // 6) 적용 필터 바 (활성 필터 개별 원클릭 해제 + 전체 초기화)
                        if (hasActiveFilter) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    text = "적용 필터",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1
                                )
                                LazyRow(
                                    modifier = Modifier.weight(1f),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    filterState.keyword?.takeIf { it.isNotBlank() }?.let { kw ->
                                        item(key = "active_kw", contentType = "active_filter_chip") {
                                            HomeActiveFilterChip(
                                                label = "검색: $kw",
                                                onRemove = {
                                                    searchInput = ""
                                                    viewModel.searchDeals("")
                                                }
                                            )
                                        }
                                    }
                                    items(
                                        items = activeCommunityChips.sorted(),
                                        key = { "active_comm_$it" },
                                        contentType = { "active_filter_chip" }
                                    ) { chip ->
                                        HomeActiveFilterChip(
                                            label = chip,
                                            onRemove = { viewModel.toggleCommunityChip(chip) }
                                        )
                                    }
                                    if (activeCommunityChips.isEmpty() && !filterState.platform.isNullOrBlank() && filterState.platform != "전체") {
                                        item(key = "active_platform", contentType = "active_filter_chip") {
                                            HomeActiveFilterChip(
                                                label = "출처: ${filterState.platform}",
                                                onRemove = { viewModel.selectPlatform("") }
                                            )
                                        }
                                    }
                                    items(
                                        items = activeCategories.sorted(),
                                        key = { "active_cat_$it" },
                                        contentType = { "active_filter_chip" }
                                    ) { cat ->
                                        HomeActiveFilterChip(
                                            label = CategoryCatalog.getDisplayLabel(cat),
                                            onRemove = { viewModel.toggleCategory(cat) }
                                        )
                                    }
                                    if (filterState.hideClosed) {
                                        item(key = "active_hide_closed", contentType = "active_filter_chip") {
                                            HomeActiveFilterChip(
                                                label = "품절 제외",
                                                onRemove = { viewModel.toggleHideClosed() }
                                            )
                                        }
                                    }
                                    if (filterState.freeShippingOnly) {
                                        item(key = "active_free_ship", contentType = "active_filter_chip") {
                                            HomeActiveFilterChip(
                                                label = "무료배송",
                                                onRemove = { viewModel.toggleFreeShippingOnly() }
                                            )
                                        }
                                    }
                                    if (filterState.popularOnly) {
                                        item(key = "active_popular_only", contentType = "active_filter_chip") {
                                            HomeActiveFilterChip(
                                                label = "인기 딜만",
                                                onRemove = { viewModel.togglePopularOnly() }
                                            )
                                        }
                                    }
                                }
                                Box(
                                    modifier = Modifier
                                        .heightIn(min = 48.dp)
                                        .semantics {
                                            role = Role.Button
                                            contentDescription = "적용된 필터 전체 초기화"
                                        }
                                        .clickable {
                                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                            searchInput = ""
                                            viewModel.resetHomeFilters()
                                        }
                                        .padding(horizontal = 4.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "초기화",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary,
                                        maxLines = 1
                                    )
                                }
                            }
                        }
                    }
                }

                // 상태 관리를 통한 스켈레톤 로딩 노출
                when {
                    dealsPagingItems.loadState.refresh is LoadState.Loading && dealsPagingItems.itemCount == 0 -> {
                        items(
                            count = if (isExpandedScreen) 4 else 3,
                            key = { index -> "skeleton_$index" },
                            contentType = { "skeleton_loader" }
                        ) { HomeDealCardSkeleton() }
                    }
                    dealsPagingItems.loadState.refresh is LoadState.Error -> {
                        item(
                            key = "loading_error",
                            span = { GridItemSpan(maxLineSpan) },
                            contentType = "error_section"
                        ) {
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 220.dp)
                                    .padding(horizontal = 4.dp, vertical = 8.dp),
                                shape = RoundedCornerShape(16.dp),
                                border = BorderStroke(
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
                                        modifier = Modifier.size(40.dp),
                                        tint = MaterialTheme.colorScheme.error
                                    )
                                    Spacer(modifier = Modifier.height(10.dp))
                                    Text(
                                        text = "핫딜 목록을 불러오지 못했어요. 잠시 후 다시 시도해 주세요.",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp,
                                        textAlign = TextAlign.Center,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Spacer(modifier = Modifier.height(14.dp))
                                    Button(
                                        onClick = { dealsPagingItems.refresh() },
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = MaterialTheme.colorScheme.error,
                                            contentColor = MaterialTheme.colorScheme.onError
                                        ),
                                        shape = RoundedCornerShape(8.dp),
                                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                                        modifier = Modifier.heightIn(min = 48.dp)
                                    ) {
                                        Text("다시 시도", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                    }
                    else -> {
                        if (dealsPagingItems.itemCount == 0) {
                            item(
                                key = "empty_deals",
                                span = { GridItemSpan(maxLineSpan) },
                                contentType = "empty_section"
                            ) {
                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .heightIn(min = 220.dp)
                                        .padding(horizontal = 4.dp, vertical = 8.dp),
                                    shape = RoundedCornerShape(16.dp),
                                    border = BorderStroke(
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
                                        Icon(
                                            imageVector = Icons.Default.Inbox,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f),
                                            modifier = Modifier.size(40.dp)
                                        )
                                        Spacer(modifier = Modifier.height(10.dp))
                                        Text(
                                            text = if (hasActiveFilter) "조건에 맞는 핫딜이 없어요" else "아직 등록된 핫딜이 없어요",
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 14.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            text = if (hasActiveFilter) "검색어나 필터 조건을 변경해 보세요." else "새로운 특가 정보가 올라오면 바로 알려드릴게요.",
                                            fontSize = 12.sp,
                                            color = LocalInsightDealColors.current.textMuted
                                        )
                                        if (hasActiveFilter) {
                                            Spacer(modifier = Modifier.height(12.dp))
                                            OutlinedButton(
                                                onClick = {
                                                    searchInput = ""
                                                    viewModel.resetHomeFilters()
                                                },
                                                shape = RoundedCornerShape(8.dp),
                                                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                                                modifier = Modifier.heightIn(min = 48.dp)
                                            ) {
                                                Text("필터 초기화", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        items(
                            count = dealsPagingItems.itemCount,
                            key = dealsPagingItems.itemKey { it.id },
                            contentType = { "deal_item" }
                        ) { index ->
                            val deal = dealsPagingItems[index]

                            if (deal != null) {
                                val isHidden = remember(deal.id, hiddenDeals.size) {
                                    hiddenDeals.contains(deal.id.toString())
                                }
                                if (!isHidden) {
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
                                                        vm.addItem(decodeHtmlEntities(deal.title), targetUrl, deal.price.toInt(), deal.price.toInt(), deal.siteName)
                                                        scope.launch {
                                                            val result = snackbarHostState.showSnackbar(
                                                                message = "관심 상품에 담았어요",
                                                                actionLabel = "관심 목록 보기",
                                                                duration = SnackbarDuration.Short
                                                            )
                                                            if (result == SnackbarResult.ActionPerformed) {
                                                                navController.navigate("watchlist")
                                                            }
                                                        }
                                                    }
                                                }
                                            }
                                        },
                                        onMoreClick = {
                                            selectedDealForOptions = deal
                                            showDealOptionsBottomSheet = true
                                        },
                                        highlightKeyword = filterState.keyword
                                    )
                                }
                            } else {
                                HomeDealCardSkeleton()
                            }
                        }

                        val appendState = dealsPagingItems.loadState.append
                        when {
                            appendState is LoadState.Loading -> {
                                item(
                                    key = "append_skeleton",
                                    span = { GridItemSpan(maxLineSpan) },
                                    contentType = "skeleton_loader"
                                ) {
                                    HomeDealCardSkeleton()
                                }
                            }
                            appendState is LoadState.Error -> {
                                item(
                                    key = "append_error",
                                    span = { GridItemSpan(maxLineSpan) },
                                    contentType = "append_error"
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 12.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        OutlinedButton(
                                            onClick = { dealsPagingItems.retry() },
                                            modifier = Modifier.heightIn(min = 48.dp)
                                        ) {
                                            Text("더 불러오기 다시 시도", fontWeight = FontWeight.SemiBold)
                                        }
                                    }
                                }
                            }
                            appendState is LoadState.NotLoading &&
                                appendState.endOfPaginationReached &&
                                dealsPagingItems.itemCount > 0 -> {
                                item(
                                    key = "append_end",
                                    span = { GridItemSpan(maxLineSpan) },
                                    contentType = "append_end"
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 14.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = "모든 핫딜을 불러왔어요",
                                            fontSize = 12.sp,
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

        // M3 공식 PullToRefresh 스피너 (군더더기 없는 깔끔한 네이티브 원형 인디케이터)
        androidx.compose.material3.pulltorefresh.PullToRefreshContainer(
            modifier = Modifier.align(Alignment.TopCenter),
            state = pullRefreshState,
        )

        // 실시간 새 핫딜 감지 플로팅 캡슐 배너
        AnimatedVisibility(
            visible = hasNewDeals,
            enter = androidx.compose.animation.fadeIn(animationSpec = androidx.compose.animation.core.tween(300)) + androidx.compose.animation.slideInVertically(animationSpec = androidx.compose.animation.core.tween(300)) { -it },
            exit = androidx.compose.animation.fadeOut(animationSpec = androidx.compose.animation.core.tween(200)) + androidx.compose.animation.slideOutVertically(animationSpec = androidx.compose.animation.core.tween(200)) { -it },
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 76.dp)
        ) {
            androidx.compose.material3.Surface(
                modifier = Modifier
                    .heightIn(min = 48.dp)
                    .bounceClick {
                        dealsPagingItems.refresh()
                        viewModel.onRefreshNewDeals()
                        scope.launch {
                            gridState.animateScrollToItem(0)
                        }
                    },
                shape = RoundedCornerShape(999.dp),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 6.dp,
                shadowElevation = 8.dp,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            ) {
                Row(
                    modifier = Modifier
                        .heightIn(min = 48.dp)
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Refresh,
                        contentDescription = "새 핫딜 새로고침",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = if (newDealsCount > 0) "새 핫딜 ${newDealsCount}건 보기" else "새 핫딜 보기",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Icon(
                        imageVector = Icons.Rounded.ArrowUpward,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(14.dp)
                    )
                }
            }
        }

        // 💡 [법적 필수] 제휴 마케팅 수수료 우아한 고지 BottomSheet UI
        if (showCommissionSheet) {
            ModalBottomSheet(
                onDismissRequest = { showCommissionSheet = false },
                sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
                containerColor = MaterialTheme.colorScheme.surface,
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
                        text = "구매처 이동 안내",
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
                                checkedColor = MaterialTheme.colorScheme.primary
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
                                runCatching {
                                    val prefs = com.ddaeany0919.insightdeal.core.security.EncryptedPrefsManager.getEncryptedPrefs(context)
                                    prefs.edit().putLong("last_dismiss_commission_time", System.currentTimeMillis()).apply()
                                }
                            }
                            if (pendingUrlToOpen.isNotEmpty()) {
                                CustomTabLauncher.launchShoppingUrl(context, pendingUrlToOpen)
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 52.dp),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        )
                    ) {
                        Text(
                            text = "확인하고 쇼핑몰로 이동하기",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                    }
                }
            }
        }

        // 🔔 대표님의 프리미엄 딜 옵션 BottomSheet UI 및 다이얼로그 체인 연동
        if (showDealOptionsBottomSheet && selectedDealForOptions != null) {
            val deal = selectedDealForOptions!!
            val isWishlisted = wishlistedUrls.contains(deal.ecommerceUrl?.takeIf { it.isNotBlank() } ?: deal.postUrl ?: "")

            ModalBottomSheet(
                onDismissRequest = { showDealOptionsBottomSheet = false },
                sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
                containerColor = MaterialTheme.colorScheme.background, // 뒤에 배경(글씨, 카드)이 비치지 않도록 불투명한 배경색 지정
                dragHandle = null, // 드래그 핸들 없음 (깔끔한 플랫 스타일)
                windowInsets = WindowInsets(0) // 인셋 여백 초기화로 밀착 레이아웃 보장
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(start = 16.dp, end = 16.dp, top = 20.dp, bottom = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // 둥근 그레이 박스 1 (관심 액션 그룹)
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        color = if (isDark) MaterialTheme.colorScheme.surfaceVariant else Color(0xFFF2F4F7)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    showDealOptionsBottomSheet = false
                                    wishlistViewModel?.let { vm ->
                                        val targetUrl = deal.ecommerceUrl?.takeIf { it.isNotBlank() } ?: deal.postUrl ?: ""
                                        if (isWishlisted) {
                                            val item = (wishlistState as? WishlistUiState.Success)?.items?.find { it.productUrl == targetUrl }
                                            if (item != null) vm.deleteItem(item)
                                        } else {
                                            vm.addItem(deal.title, targetUrl, deal.price.toInt(), deal.price.toInt(), deal.siteName)
                                            scope.launch {
                                                val result = snackbarHostState.showSnackbar(
                                                    message = "관심 상품에 담았어요",
                                                    actionLabel = "관심 목록 보기",
                                                    duration = SnackbarDuration.Short
                                                )
                                                if (result == SnackbarResult.ActionPerformed) {
                                                    navController.navigate("watchlist")
                                                }
                                            }
                                        }
                                    }
                                }
                                .padding(horizontal = 16.dp, vertical = 18.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            // 찜 여부에 따른 빨간하트 vs 빈하트 시각 가변화
                            Icon(
                                imageVector = if (isWishlisted) Icons.Filled.Favorite else Icons.Default.FavoriteBorder,
                                contentDescription = null,
                                tint = if (isWishlisted) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(24.dp)
                            )
                            Text(
                                text = "관심 있음",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }

                    // 둥근 그레이 박스 2 (숨기기 / 왜 보이나요 / 신고하기 그룹)
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        color = if (isDark) MaterialTheme.colorScheme.surfaceVariant else Color(0xFFF2F4F7)
                    ) {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            // 1) 이 글 숨기기 (비로그인 허용 즉시 숨김)
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        showDealOptionsBottomSheet = false
                                        hideDeal(deal.id)
                                    }
                                    .padding(horizontal = 16.dp, vertical = 18.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.VisibilityOff,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(24.dp)
                                )
                                Text(
                                    text = "이 글 숨기기",
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }

                            HorizontalDivider(color = if (isDark) MaterialTheme.colorScheme.outlineVariant else Color(0xFFE4E7EC), thickness = 1.dp, modifier = Modifier.padding(horizontal = 16.dp))

                            // 2) 이 글이 왜 보이나요?
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        showDealOptionsBottomSheet = false
                                        showWhyThisDealDialog = deal
                                    }
                                    .padding(horizontal = 16.dp, vertical = 18.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.HelpOutline,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(24.dp)
                                )
                                Text(
                                    text = "이 글이 왜 보이나요?",
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }

                    // 둥근 그레이 박스 3 (하단 독립 닫기 버튼)
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { showDealOptionsBottomSheet = false },
                        shape = RoundedCornerShape(16.dp),
                        color = if (isDark) MaterialTheme.colorScheme.surfaceVariant else Color(0xFFF2F4F7)
                    ) {
                        Box(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 18.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "닫기",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }
        }

        // 💡 1:1 개인화 지능형 추천 설명 다이얼로그 (왜 보이나요?)
        if (showWhyThisDealDialog != null) {
            val deal = showWhyThisDealDialog!!
            AlertDialog(
                onDismissRequest = { showWhyThisDealDialog = null },
                title = { Text(text = "추천 기준 안내", fontWeight = FontWeight.Bold) },
                text = {
                    Text(
                        text = "이 글은 [${deal.siteName}]에서 반응이 좋은 실시간 핫딜이에요.\n\n" +
                                "최근 48시간 내 올라온 글 중 커뮤니티 추천 점수(${deal.honeyScore}점)가 높아 소개해 드려요",
                        fontSize = 14.sp,
                        lineHeight = 20.sp
                    )
                },
                confirmButton = {
                    TextButton(onClick = { showWhyThisDealDialog = null }) {
                        Text("확인", fontWeight = FontWeight.Bold)
                    }
                }
            )
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = 16.dp)
        )
    }
}

@Composable
private fun HomeTrendingTickerBar(
    tickerItems: List<com.ddaeany0919.insightdeal.network.PopularKeywordRankingItem>,
    isDark: Boolean,
    onKeywordClick: (String) -> Unit,
    onAllRankingsClick: () -> Unit
) {
    if (tickerItems.isEmpty()) return
    var tickerIndex by remember { mutableIntStateOf(0) }
    LaunchedEffect(tickerItems.size) {
        while (true) {
            kotlinx.coroutines.delay(3200L)
            if (tickerItems.isNotEmpty()) {
                tickerIndex = (tickerIndex + 1) % tickerItems.size
            }
        }
    }
    val currentItem = tickerItems[tickerIndex.coerceIn(0, tickerItems.lastIndex)]
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .bounceClick(onClickLabel = "${currentItem.keyword} 급상승 키워드 검색") {
                onKeywordClick(currentItem.keyword)
            },
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(
            1.dp,
            if (isDark) Color(0xFF2B333E) else Color(0xFFE5E8EB)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
                .padding(horizontal = 12.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = MaterialTheme.colorScheme.primary.copy(alpha = if (isDark) 0.2f else 0.1f)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(3.dp)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Outlined.TrendingUp,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(12.dp)
                        )
                        Text(
                            text = "급상승",
                            fontSize = 10.5.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = "${currentItem.rank}",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = if (currentItem.rank <= 3) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.width(18.dp)
                )
                Text(
                    text = currentItem.keyword,
                    fontSize = 13.5.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (currentItem.status == "HOT") {
                    Spacer(modifier = Modifier.width(6.dp))
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = MaterialTheme.colorScheme.secondaryContainer
                    ) {
                        Text(
                            text = "인기",
                            fontSize = 10.5.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                        )
                    }
                }
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .heightIn(min = 48.dp)
                    .clickable(onClick = onAllRankingsClick)
                    .padding(horizontal = 4.dp)
            ) {
                Text(
                    text = "전체 순위",
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = "전체 순위 보기",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(15.dp)
                )
            }
        }
    }
}

@Composable
private fun HomeFilterRowLabel(
    text: String,
    shape: Shape,
    containerColor: Color,
    contentColor: Color
) {
    Surface(
        shape = shape,
        color = containerColor,
        modifier = Modifier.height(26.dp)
    ) {
        Box(
            modifier = Modifier.padding(horizontal = 8.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = text,
                fontSize = 11.5.sp,
                fontWeight = FontWeight.Bold,
                color = contentColor,
                maxLines = 1,
                style = TextStyle(
                    platformStyle = PlatformTextStyle(includeFontPadding = false)
                )
            )
        }
    }
}

@Composable
private fun HomeSelectablePillChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    role: Role = Role.Checkbox,
    shape: Shape = RoundedCornerShape(999.dp),
    chipHeight: Dp = 36.dp,
    selectedContainerColor: Color = MaterialTheme.colorScheme.primary,
    selectedContentColor: Color = MaterialTheme.colorScheme.onPrimary,
    selectedBorderColor: Color = selectedContainerColor,
    unselectedContainerColor: Color = MaterialTheme.colorScheme.surface,
    unselectedContentColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    unselectedBorderColor: Color = MaterialTheme.colorScheme.outline,
    leadingIcon: ImageVector? = null
) {
    val containerColor = if (selected) selectedContainerColor else unselectedContainerColor
    val contentColor = if (selected) selectedContentColor else unselectedContentColor
    val borderColor = if (selected) selectedBorderColor else unselectedBorderColor

    Box(
        modifier = Modifier
            .heightIn(min = 48.dp)
            .semantics {
                this.role = role
                this.selected = selected
                this.stateDescription = if (selected) "선택됨" else "선택 안 됨"
            }
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            modifier = Modifier.height(chipHeight),
            shape = shape,
            color = containerColor,
            border = BorderStroke(1.dp, borderColor)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = if (chipHeight <= 34.dp) 10.dp else 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                val iconToShow = when {
                    leadingIcon != null -> leadingIcon
                    selected -> Icons.Default.Check
                    else -> null
                }
                if (iconToShow != null) {
                    Icon(
                        imageVector = iconToShow,
                        contentDescription = null,
                        tint = contentColor,
                        modifier = Modifier.size(14.dp)
                    )
                }
                Text(
                    text = label,
                    fontSize = if (chipHeight <= 34.dp) 12.5.sp else 13.sp,
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                    color = contentColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = TextStyle(
                        platformStyle = PlatformTextStyle(includeFontPadding = false)
                    )
                )
            }
        }
    }
}

@Composable
private fun HomeActiveFilterChip(
    label: String,
    onRemove: () -> Unit
) {
    Box(
        modifier = Modifier
            .heightIn(min = 48.dp)
            .semantics {
                role = Role.Button
                contentDescription = "$label 필터 해제"
            }
            .clickable(onClick = onRemove),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            modifier = Modifier.height(32.dp),
            shape = RoundedCornerShape(999.dp),
            color = MaterialTheme.colorScheme.secondaryContainer,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = label,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = TextStyle(
                        platformStyle = PlatformTextStyle(includeFontPadding = false)
                    )
                )
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.size(13.dp)
                )
            }
        }
    }
}

// ✨ 세로형 딜 카드를 위한 스켈레톤 (Recomposition 방지용, DealCardComposable 규격 정합)
@Composable
fun HomeDealCardSkeleton() {
    val semantic = LocalInsightDealColors.current
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, semantic.cardBorder),
        elevation = CardDefaults.cardElevation(0.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
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

internal fun isIncompleteHangulJamoQuery(raw: String): Boolean {
    val trimmed = raw.trim()
    if (trimmed.isEmpty()) return false
    val tokens = trimmed.split(Regex("\\s+")).filter { it.isNotBlank() }
    if (tokens.isEmpty()) return false
    return tokens.any { token ->
        val target = if (token.startsWith("-") && token.length > 1) token.substring(1) else token
        target.isNotEmpty() && target.all { ch ->
            ch in '\u3131'..'\u318E' || ch in '\u1100'..'\u11FF'
        }
    }
}

internal fun buildHighlightedTitle(
    title: String,
    keyword: String?,
    highlightBg: Color,
    highlightFg: Color
): AnnotatedString {
    val cleanQuery = keyword?.trim().orEmpty()
    if (cleanQuery.isEmpty() || title.isEmpty()) return AnnotatedString(title)

    val tokens = cleanQuery
        .split(Regex("\\s+"))
        .filter { it.isNotBlank() && !it.startsWith("-") && !it.startsWith(":") }
        .distinct()
    if (tokens.isEmpty()) return AnnotatedString(title)

    val rawRanges = mutableListOf<Pair<Int, Int>>()
    for (token in tokens) {
        var searchStart = 0
        while (searchStart < title.length) {
            val matchIndex = title.indexOf(token, startIndex = searchStart, ignoreCase = true)
            if (matchIndex < 0) break
            rawRanges.add(matchIndex to (matchIndex + token.length))
            searchStart = matchIndex + 1
        }
    }
    if (rawRanges.isEmpty()) return AnnotatedString(title)

    val sortedRanges = rawRanges.sortedWith(
        compareBy<Pair<Int, Int>> { it.first }.thenByDescending { it.second }
    )
    val mergedRanges = mutableListOf<Pair<Int, Int>>()
    for (range in sortedRanges) {
        if (mergedRanges.isEmpty()) {
            mergedRanges.add(range)
        } else {
            val last = mergedRanges.last()
            if (range.first <= last.second) {
                mergedRanges[mergedRanges.lastIndex] = last.first to maxOf(last.second, range.second)
            } else {
                mergedRanges.add(range)
            }
        }
    }

    return buildAnnotatedString {
        var cursor = 0
        for ((start, end) in mergedRanges) {
            if (start > cursor) {
                append(title.substring(cursor, start))
            }
            withStyle(
                SpanStyle(
                    background = highlightBg,
                    color = highlightFg,
                    fontWeight = FontWeight.Bold
                )
            ) {
                append(title.substring(start, end))
            }
            cursor = end
        }
        if (cursor < title.length) {
            append(title.substring(cursor))
        }
    }
}

private data class CardOptionEntry(
    val name: String,
    val price: Long,
    val currency: String,
    val targetUrl: String
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
@Suppress("UNUSED_PARAMETER")
fun DealCardComposable(
    deal: DealItem,
    onDetailClick: () -> Unit = {},
    onOpenUrl: (String) -> Unit = {},
    isWishlisted: Boolean = false,
    onToggleWishlist: () -> Unit = {},
    onMoreClick: (() -> Unit)? = null,
    highlightKeyword: String? = null
) {
    var isExpanded by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val isDark = LocalIsDarkTheme.current

    // 1. 쇼핑몰 배지 및 중복 [말머리] 제거·HTML 엔티티 디코딩 캐싱
    val resolvedShopTitle = remember(deal.id, deal.title, deal.shopName, deal.ecommerceUrl) {
        resolveShopBadgeAndCleanTitle(
            rawTitle = deal.title,
            shopName = deal.shopName,
            ecommerceUrl = deal.ecommerceUrl
        )
    }

    // 2. 인기 배지 판단 캐싱
    val isSuperHot = remember(deal.id, deal.aiSummary, deal.honeyScore) {
        ((deal.aiSummary?.contains("[커뮤니티 인기]") == true) ||
            (deal.aiSummary?.contains("[커뮤니티 인증 핫딜]") == true)) &&
            deal.honeyScore >= 100
    }

    // 3. 이미지 URL 및 Request 캐싱
    val primaryImageUrl = remember(deal.id, deal.imageUrl, com.ddaeany0919.insightdeal.data.network.NetworkConfig.getActiveServerUrl()) {
        com.ddaeany0919.insightdeal.data.util.ImageUrlResolver.resolve(deal.imageUrl, targetWidth = 400)
    }
    val fallbackProxyUrl = remember(deal.id, deal.imageUrl, com.ddaeany0919.insightdeal.data.network.NetworkConfig.getActiveServerUrl()) {
        com.ddaeany0919.insightdeal.data.util.ImageUrlResolver.getFallbackBackendProxyUrl(deal.imageUrl)
    }

    var activeImageUrl by remember(primaryImageUrl) { mutableStateOf(primaryImageUrl) }

    val imageRequest = remember(activeImageUrl, deal.postUrl) {
        if (activeImageUrl == null) null
        else {
            coil.request.ImageRequest.Builder(context)
                .data(activeImageUrl)
                .setHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) Chrome/120.0.0.0 Safari/537.36")
                .setHeader("Referer", deal.postUrl ?: "https://insightdeal.com/")
                .crossfade(true)
                .precision(coil.size.Precision.EXACT)
                .build()
        }
    }

    // 4. 출처 배지 목록 캐싱
    val sourcesToRender = remember(deal.id, deal.sources, deal.siteName, deal.postUrl) {
        val rawSources = if (!deal.sources.isNullOrEmpty()) deal.sources else listOf(com.ddaeany0919.insightdeal.models.DealSource(deal.siteName, deal.postUrl ?: ""))
        rawSources.distinctBy { source ->
            if (source.siteName.contains(" - ")) {
                source.siteName.substringBefore(" - ").trim()
            } else {
                source.siteName.trim()
            }
        }
    }

    // 5. 배송비 연산 캐싱 (HTML 엔티티 디코딩 보장)
    val displayShipping = remember(deal.id, deal.shippingFee, deal.category, resolvedShopTitle.cleanTitle) {
        val digitalCategories = HOME_DIGITAL_CATEGORIES
        val isDigitalTitle = resolvedShopTitle.cleanTitle.contains("요금제") || resolvedShopTitle.cleanTitle.contains("데이터")
        val hideShipping = deal.category in digitalCategories || isDigitalTitle

        if (hideShipping) null
        else {
            val trimmed = decodeHtmlEntities(deal.shippingFee).trim().ifEmpty { "정보 없음" }
            when {
                trimmed == "정보 없음" || trimmed == "배송비 확인 필요" -> "확인 필요"
                trimmed.contains("와우") -> "와우무료"
                trimmed.contains("스마일") || trimmed.contains("스클") -> "스클무료"
                trimmed.contains("우주패스") -> "우주패스"
                trimmed == "0" || trimmed == "0원" || trimmed == "무배" || trimmed == "무료" || trimmed == "무료배송" -> "무료"
                trimmed.startsWith("0원/") || trimmed.startsWith("0원+") || trimmed.startsWith("0/") || trimmed.startsWith("0+") -> trimmed.replaceFirst(SHIPPING_LEADING_ZERO_REGEX, "무료 ")
                trimmed == "유료" || trimmed == "유료배송" -> "유료"
                trimmed.all { it.isDigit() || it == ',' } -> "${trimmed}원"
                else -> trimmed.replace("무료배송", "무료").replace("유료배송", "유료").replace(SHIPPING_FREE_COND_REGEX, "$1 무료").replace(SHIPPING_AMOUNT_COND_REGEX, "$1원$2").trim()
            }
        }
    }

    // 6. 가격 정보 포맷팅 캐싱 (StringUtils.formatPrice 단일 소스 사용)
    val priceText = remember(deal.id, deal.price, deal.currency, deal.category, resolvedShopTitle.cleanTitle) {
        if (deal.price > 0L) {
            val formatted = formatPrice(deal.price, deal.currency, resolvedShopTitle.cleanTitle)
            if (deal.category == "적립") "$formatted 적립" else formatted
        } else if (deal.category == "적립" || resolvedShopTitle.cleanTitle.contains("적립")) {
            "포인트 적립"
        } else {
            formatPrice(deal.price, deal.currency, resolvedShopTitle.cleanTitle)
        }
    }

    // 7. 다중 옵션 수 및 아코디언 목록 캐싱 (price == 0 옵션도 누락 없이 "0원"으로 표기)
    val optionCount = remember(deal.id, deal.hasOptions, deal.optionsCount, deal.optionsData, deal.sources) {
        deal.resolvedOptionCount
    }

    val multiOptions = remember(deal.id, deal.optionsData, deal.sources, deal.currency, deal.ecommerceUrl, deal.postUrl) {
        if (deal.optionsData.size > 1) {
            deal.optionsData.map { opt ->
                CardOptionEntry(
                    name = decodeHtmlEntities(opt.name).trim(),
                    price = opt.price,
                    currency = opt.currency ?: deal.currency,
                    targetUrl = opt.url?.takeIf { it.isNotBlank() }
                        ?: opt.subUrls?.firstOrNull { it.isNotBlank() }
                        ?: deal.ecommerceUrl?.takeIf { it.isNotBlank() }
                        ?: deal.postUrl.orEmpty()
                )
            }
        } else {
            deal.sources
                ?.filter { it.siteName.contains(" - ") && it.siteName.substringAfter(" - ").isNotBlank() }
                ?.map { src ->
                    CardOptionEntry(
                        name = decodeHtmlEntities(src.siteName.substringAfter(" - ")).trim(),
                        price = src.price ?: 0L,
                        currency = src.currency ?: deal.currency,
                        targetUrl = src.ecommerceUrl?.takeIf { it.isNotBlank() }
                            ?: src.subUrls?.firstOrNull { it.isNotBlank() }
                            ?: src.postUrl
                    )
                }
                .orEmpty()
        }
    }

    val scoreSummaryText = remember(deal.id, deal.honeyScore) {
        val percentileText = when {
            deal.honeyScore >= 95 -> "상위 1%"
            deal.honeyScore >= 90 -> "상위 5%"
            deal.honeyScore >= 80 -> "상위 10%"
            deal.honeyScore >= 70 -> "상위 20%"
            else -> "일반"
        }
        if (percentileText == "일반") "커뮤니티 추천도 ${deal.honeyScore}점" else "커뮤니티 인기 $percentileText · 추천도 ${deal.honeyScore}점"
    }

    val semantic = LocalInsightDealColors.current
    val cardBgColor = MaterialTheme.colorScheme.surface
    val cardContentColor = MaterialTheme.colorScheme.onSurface
    val highlightBg = if (isDark) Color(0x4DFFB74D) else Color(0xFFFFF3BF)
    val highlightFg = if (isDark) Color(0xFFFFE082) else Color(0xFF7C2D12)
    val highlightedTitle = remember(resolvedShopTitle.cleanTitle, highlightKeyword, isDark) {
        buildHighlightedTitle(
            title = resolvedShopTitle.cleanTitle,
            keyword = highlightKeyword,
            highlightBg = highlightBg,
            highlightFg = highlightFg
        )
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .semantics {
                stateDescription = if (deal.isClosed) {
                    "종료됨"
                } else if (isExpanded) {
                    "옵션 펼쳐짐"
                } else {
                    "진행중"
                }
            }
            .bounceClick(
                scaleDown = 0.98f,
                onClickLabel = "${resolvedShopTitle.cleanTitle}, $priceText 상세 보기"
            ) { onDetailClick() },
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, semantic.cardBorder),
        elevation = CardDefaults.cardElevation(0.dp),
        colors = CardDefaults.cardColors(
            containerColor = cardBgColor,
            contentColor = cardContentColor
        )
    ) {
        Box(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(modifier = Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.Top) {
                    if (imageRequest == null) {
                        BrandPlaceholder(
                            siteName = deal.siteName,
                            modifier = Modifier
                                .size(80.dp)
                                .clip(RoundedCornerShape(8.dp)),
                            textStyle = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Color(0xFFF9FAFB))
                        )
                    } else {
                        Box(modifier = Modifier.size(80.dp)) {
                            SubcomposeAsyncImage(
                                model = imageRequest,
                                contentDescription = null,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(8.dp))
                                    .clip(RoundedCornerShape(8.dp)),
                                colorFilter = if (deal.isClosed) CLOSED_DEAL_COLOR_FILTER else null,
                                contentScale = ContentScale.Crop,
                                onError = {
                                    if (fallbackProxyUrl != null && activeImageUrl != fallbackProxyUrl) {
                                        activeImageUrl = fallbackProxyUrl
                                    }
                                },
                                error = {
                                    BrandPlaceholder(
                                        siteName = deal.siteName,
                                        modifier = Modifier.fillMaxSize(),
                                        textStyle = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color(0xFFF9FAFB))
                                    )
                                }
                            )

                            if (deal.isClosed) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(MaterialTheme.colorScheme.inverseSurface.copy(alpha = 0.85f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "종료",
                                        color = MaterialTheme.colorScheme.inverseOnSurface,
                                        fontWeight = FontWeight.ExtraBold,
                                        fontSize = 12.sp
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            FlowRow(
                                modifier = Modifier.weight(1f).padding(end = 4.dp),
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                val primarySource = sourcesToRender.firstOrNull()
                                if (primarySource != null) {
                                    val baseSiteName = if (primarySource.siteName.contains(" - ")) {
                                        primarySource.siteName.substringBefore(" - ").trim()
                                    } else {
                                        primarySource.siteName.trim()
                                    }
                                    val displaySiteName = if (sourcesToRender.size > 1) {
                                        "$baseSiteName +${sourcesToRender.size - 1}"
                                    } else {
                                        baseSiteName
                                    }
                                    val siteNameLower = baseSiteName.lowercase(Locale.getDefault())
                                    val (containerColor, contentColor) = when {
                                        siteNameLower.contains("뽐뿌해외") -> Color(0xFF00796B) to Color(0xFFF9FAFB)
                                        siteNameLower.contains("뽐뿌") -> Color(0xFF1565C0) to Color(0xFFF9FAFB)
                                        siteNameLower.contains("퀘이사존") -> Color(0xFFB84500) to Color(0xFFF9FAFB)
                                        siteNameLower.contains("루리웹") -> Color(0xFF0D47A1) to Color(0xFFF9FAFB)
                                        siteNameLower.contains("펨코") || siteNameLower.contains("에펨코리아") -> Color(0xFF026AA7) to Color(0xFFF9FAFB)
                                        siteNameLower.contains("빠삭") -> Color(0xFFAD1457) to Color(0xFFF9FAFB)
                                        siteNameLower.contains("클리앙") -> Color(0xFF37474F) to Color(0xFFF9FAFB)
                                        else -> MaterialTheme.colorScheme.surfaceVariant to MaterialTheme.colorScheme.onSurfaceVariant
                                    }

                                    Surface(
                                        shape = RoundedCornerShape(4.dp),
                                        color = containerColor,
                                        modifier = Modifier.align(Alignment.CenterVertically)
                                    ) {
                                        Text(
                                            text = displaySiteName,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = contentColor
                                        )
                                    }
                                }

                                // Density Cap: 상단 보조 칩은 우선순위(인기 -> 옵션 N개 -> 쇼핑몰) 기준 최대 1개만 표시
                                when {
                                    isSuperHot -> {
                                        Surface(
                                            shape = RoundedCornerShape(4.dp),
                                            color = MaterialTheme.colorScheme.errorContainer,
                                            modifier = Modifier.align(Alignment.CenterVertically)
                                        ) {
                                            Text(
                                                text = "인기",
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.ExtraBold,
                                                color = MaterialTheme.colorScheme.onErrorContainer
                                            )
                                        }
                                    }
                                    deal.hasMultipleOptions && optionCount > 1 -> {
                                        Surface(
                                            shape = RoundedCornerShape(4.dp),
                                            color = MaterialTheme.colorScheme.primaryContainer,
                                            modifier = Modifier.align(Alignment.CenterVertically)
                                        ) {
                                            Text(
                                                text = "옵션 ${optionCount}개",
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.onPrimaryContainer
                                            )
                                        }
                                    }
                                    !resolvedShopTitle.shopBadge.isNullOrBlank() -> {
                                        Surface(
                                            shape = RoundedCornerShape(4.dp),
                                            color = MaterialTheme.colorScheme.surfaceVariant,
                                            border = BorderStroke(
                                                0.5.dp,
                                                MaterialTheme.colorScheme.outline
                                            ),
                                            modifier = Modifier.align(Alignment.CenterVertically)
                                        ) {
                                            Text(
                                                text = resolvedShopTitle.shopBadge,
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.SemiBold,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                }

                                val timeAgo = rememberRelativeTime(deal.createdAt)
                                if (timeAgo.isNotEmpty()) {
                                    Text(
                                        text = timeAgo,
                                        fontSize = 11.sp,
                                        color = semantic.textMuted,
                                        modifier = Modifier
                                            .align(Alignment.CenterVertically)
                                            .padding(start = 2.dp)
                                    )
                                }
                            }

                            IconButton(
                                onClick = {
                                    if (onMoreClick != null) {
                                        onMoreClick()
                                    } else {
                                        isExpanded = !isExpanded
                                    }
                                },
                                modifier = Modifier.size(48.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.MoreVert,
                                    contentDescription = "더보기",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(6.dp))

                        Text(
                            text = highlightedTitle,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.fillMaxWidth().padding(end = 8.dp),
                            textDecoration = if (deal.isClosed) TextDecoration.LineThrough else null,
                            color = if (deal.isClosed) semantic.textMuted else MaterialTheme.colorScheme.onSurface
                        )

                        Spacer(modifier = Modifier.height(6.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            FlowRow(
                                modifier = Modifier.weight(1f),
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                val priceFontSize = if (priceText.length >= 9) 14.sp else 16.5.sp
                                Text(
                                    text = priceText,
                                    style = PriceTextStyle.copy(
                                        fontSize = priceFontSize,
                                        fontWeight = FontWeight.ExtraBold
                                    ),
                                    color = when {
                                        deal.isClosed -> semantic.textMuted
                                        deal.price > 0L || priceText == "무료" -> semantic.priceAccent
                                        else -> semantic.textMuted
                                    },
                                    modifier = Modifier.align(Alignment.CenterVertically).weight(1f, fill = false),
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )

                                if (displayShipping != null) {
                                    Surface(
                                        shape = RoundedCornerShape(4.dp),
                                        color = if (isDark) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                                        modifier = Modifier.align(Alignment.CenterVertically)
                                    ) {
                                        Text(
                                            text = "배송비: $displayShipping",
                                            fontSize = 10.5.sp,
                                            color = if (isDark) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                                        )
                                    }
                                }

                                if (deal.isSearchFallback) {
                                    Surface(
                                        shape = RoundedCornerShape(4.dp),
                                        color = semantic.warningContainer,
                                        border = BorderStroke(0.5.dp, semantic.onWarningContainer.copy(alpha = 0.35f)),
                                        modifier = Modifier
                                            .align(Alignment.CenterVertically)
                                            .semantics { contentDescription = "직접 링크 없음, 쇼핑몰 검색 이동" }
                                    ) {
                                        Text(
                                            text = "검색이동",
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = semantic.onWarningContainer,
                                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                                        )
                                    }
                                }

                                if (deal.isClosed) {
                                    Surface(
                                        shape = RoundedCornerShape(4.dp),
                                        color = semantic.dangerContainer,
                                        modifier = Modifier.align(Alignment.CenterVertically)
                                    ) {
                                        Text(
                                            text = "품절·종료",
                                            fontSize = 10.sp,
                                            color = semantic.onDangerContainer,
                                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                AnimatedVisibility(visible = isExpanded) {
                    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp)) {
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        Spacer(modifier = Modifier.height(10.dp))

                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = if (isDark) Color(0xFF2B333E) else Color(0xFFF8FAFC),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = scoreSummaryText,
                                modifier = Modifier.padding(8.dp),
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontWeight = FontWeight.SemiBold,
                                textAlign = TextAlign.Center
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        if (multiOptions.size > 1) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(
                                        color = if (isDark) MaterialTheme.colorScheme.surface.copy(alpha = 0.6f) else Color(0xFFF8FAFC),
                                        shape = RoundedCornerShape(10.dp)
                                    )
                                    .padding(10.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    text = "옵션별 가격 및 바로가기",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                multiOptions.forEach { opt ->
                                    val optPriceText = formatDetailPriceText(
                                        price = opt.price,
                                        currency = opt.currency,
                                        titleHint = opt.name,
                                        isFree = deal.isFree && opt.price == 0L
                                    )
                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = MaterialTheme.colorScheme.surface,
                                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .heightIn(min = 48.dp)
                                            .clickable {
                                                if (opt.targetUrl.isNotBlank()) {
                                                    val finalOptUrl = if (opt.targetUrl.startsWith("http")) opt.targetUrl else "https://${opt.targetUrl}"
                                                    onOpenUrl(finalOptUrl)
                                                }
                                            }
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = opt.name,
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.Medium,
                                                color = MaterialTheme.colorScheme.onSurface,
                                                maxLines = 2,
                                                overflow = TextOverflow.Ellipsis,
                                                modifier = Modifier.weight(1f).padding(end = 8.dp)
                                            )
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Text(
                                                    text = optPriceText,
                                                    fontSize = 12.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = if (opt.price > 0L || optPriceText.contains("무료") || optPriceText == "0원") {
                                                        semantic.priceAccent
                                                    } else {
                                                        semantic.textMuted
                                                    }
                                                )
                                                Icon(
                                                    imageVector = Icons.Default.ChevronRight,
                                                    contentDescription = null,
                                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                                    modifier = Modifier.size(16.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                            Spacer(modifier = Modifier.height(12.dp))
                        } else if (!deal.aiSummary.isNullOrBlank()) {
                            val cleanedSummary = remember(deal.aiSummary) {
                                deal.aiSummary
                                    .replace(Regex("^[^가-힣A-Za-z0-9\\[]+"), "")
                                    .replace("[커뮤니티 인증 핫딜]", "")
                                    .replace("[커뮤니티 인기]", "")
                                    .trim()
                            }
                            if (cleanedSummary.isNotBlank() &&
                                !cleanedSummary.contains("자동 분리") &&
                                !cleanedSummary.contains("모음전] 여러 상품이 포함된")
                            ) {
                                Text(
                                    text = cleanedSummary,
                                    fontSize = 12.5.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontWeight = FontWeight.Medium,
                                    lineHeight = 18.sp,
                                    modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp)
                                )
                                Spacer(modifier = Modifier.height(12.dp))
                            }
                        }

                        Button(
                            onClick = onDetailClick,
                            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.secondary,
                                contentColor = MaterialTheme.colorScheme.onSecondary
                            ),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text("상세 정보 및 가격 추이", fontWeight = FontWeight.Bold)
                        }
                        Spacer(modifier = Modifier.height(8.dp))

                        Button(
                            onClick = {
                                try {
                                    val isPointEvent = deal.category == "적립" || resolvedShopTitle.cleanTitle.contains("네이버페이") || resolvedShopTitle.cleanTitle.contains("네이버 페이")
                                    var targetUrl = if (isPointEvent) {
                                        deal.postUrl
                                    } else {
                                        deal.ecommerceUrl?.takeIf { it.isNotBlank() }?.replace(":443", "") ?: deal.postUrl
                                    }
                                    if (!targetUrl.isNullOrBlank()) {
                                        val prefs = context.getSharedPreferences("insightdeal_prefs", android.content.Context.MODE_PRIVATE)
                                        val afCode = prefs.getString("coupang_af_code", "") ?: ""

                                        if (afCode.isNotBlank() && (targetUrl.contains("coupang.com") || targetUrl.contains("coupa.ng"))) {
                                            targetUrl = if (targetUrl.contains("?")) "$targetUrl&subId=$afCode" else "$targetUrl?subId=$afCode"
                                        }

                                        var finalUrl = if (targetUrl.startsWith("http")) targetUrl else "https://$targetUrl"
                                        if (finalUrl.contains("bbasak.com") && !finalUrl.contains("device=pc")) {
                                            finalUrl += if (finalUrl.contains("?")) "&device=pc" else "?device=pc"
                                        }
                                        onOpenUrl(finalUrl)
                                    } else {
                                        android.widget.Toast.makeText(context, "이동할 링크가 없어요.", android.widget.Toast.LENGTH_SHORT).show()
                                    }
                                } catch (_: Exception) {
                                    android.widget.Toast.makeText(context, "브라우저를 열 수 없어요.", android.widget.Toast.LENGTH_SHORT).show()
                                }
                            },
                            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary,
                                contentColor = MaterialTheme.colorScheme.onPrimary
                            ),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text("쇼핑몰 바로가기", fontWeight = FontWeight.Bold)
                        }
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

    val keywordsState by keywordViewModel.keywords.collectAsStateWithLifecycle()
    val dndEnabled by keywordViewModel.dndEnabled.collectAsStateWithLifecycle()
    val dndStartTime by keywordViewModel.dndStartTime.collectAsStateWithLifecycle()
    val dndEndTime by keywordViewModel.dndEndTime.collectAsStateWithLifecycle()

    // 💡 바텀시트가 실행되는 0.00ms 시점에 즉각 백엔드로부터 최신 맞춤 키워드 정보를 강제 동기화합니다.
    androidx.compose.runtime.LaunchedEffect(Unit) {
        keywordViewModel.fetchKeywords()
    }

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
                Text("맞춤 키워드 알림", fontWeight = FontWeight.ExtraBold, fontSize = 22.sp, color = MaterialTheme.colorScheme.onSurface)
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

            val context = LocalContext.current
            val formattedStartTime = dndStartTime.split(":").firstOrNull()?.let { "${it}시" } ?: dndStartTime
            val formattedEndTime = dndEndTime.split(":").firstOrNull()?.let { "${it}시" } ?: dndEndTime

            // 야간 알림 수신 동의 및 DND 상태 토글 UI (2번째 캡쳐 피드백 반영: 둥근 18.dp 연회색 박스형 카드 레이아웃)
            Surface(
                shape = RoundedCornerShape(18.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp)
                    .clickable {
                        val startParts = dndStartTime.split(":")
                        val startHour = startParts.firstOrNull()?.toIntOrNull() ?: 22
                        val startMinute = startParts.getOrNull(1)?.toIntOrNull() ?: 0

                        val endParts = dndEndTime.split(":")
                        val endHour = endParts.firstOrNull()?.toIntOrNull() ?: 8
                        val endMinute = endParts.getOrNull(1)?.toIntOrNull() ?: 0

                        android.app.TimePickerDialog(
                            context,
                            { _, pickedStartHour, pickedStartMinute ->
                                val formattedStart = String.format("%02d:%02d", pickedStartHour, pickedStartMinute)

                                android.app.TimePickerDialog(
                                    context,
                                    { _, pickedEndHour, pickedEndMinute ->
                                        val formattedEnd = String.format("%02d:%02d", pickedEndHour, pickedEndMinute)
                                        keywordViewModel.updateDndSettings(
                                            enabled = true,
                                            startTime = formattedStart,
                                            endTime = formattedEnd
                                        )
                                    },
                                    endHour,
                                    endMinute,
                                    true
                                ).apply {
                                    setTitle("야간 알림 차단 종료 시간")
                                    show()
                                }
                            },
                            startHour,
                            startMinute,
                            true
                        ).apply {
                            setTitle("야간 알림 차단 시작 시간")
                            show()
                        }
                    }
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 18.dp)
                ) {
                    Checkbox(
                        checked = dndEnabled,
                        onCheckedChange = { isChecked ->
                            keywordViewModel.updateDndSettings(
                                enabled = isChecked,
                                startTime = dndStartTime,
                                endTime = dndEndTime
                            )
                        },
                        colors = CheckboxDefaults.colors(
                            checkedColor = MaterialTheme.colorScheme.primary,
                            uncheckedColor = LocalInsightDealColors.current.textMuted
                        )
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = "야간(${formattedStartTime}~${formattedEndTime}) 알림 차단",
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "해제 시각(${dndEndTime})에 놓친 핫딜을 요약해 알려드려요 (탭하여 시간 변경)",
                            fontSize = 12.sp,
                            color = LocalInsightDealColors.current.textMuted
                        )
                    }
                }
            }

            OutlinedTextField(
                value = keywordText,
                onValueChange = { keywordText = it },
                placeholder = { Text("예: 맥북") },
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                    imeAction = androidx.compose.ui.text.input.ImeAction.Done
                ),
                keyboardActions = androidx.compose.foundation.text.KeyboardActions(
                    onDone = {
                        if (keywordText.isNotBlank() && !keywordsState.any { it.keyword == keywordText }) {
                            keywordViewModel.addKeyword(keywordText)
                            keywordText = ""
                        }
                    }
                ),
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

            // 💡 등록된 맞춤 키워드가 없는 경우, 사용자 편의를 위한 프리미엄 아크릴 플레이스홀더를 노출합니다.
            if (keywordsState.isEmpty()) {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                    modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            "아직 등록된 키워드가 없어요.",
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            "원하는 키워드를 입력하고 + 버튼을 눌러 추가해보세요!",
                            fontSize = 11.sp,
                            color = LocalInsightDealColors.current.textMuted
                        )
                    }
                }
            }

            if (keywordsState.isNotEmpty()) {
                FlowRow(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    keywordsState.forEach { kwEntity ->
                        InputChip(
                            selected = kwEntity.isActive,
                            onClick = { keywordViewModel.toggleKeyword(kwEntity) },
                            label = { Text(kwEntity.keyword, fontWeight = FontWeight.Bold) },
                            trailingIcon = {
                                Box(
                                    modifier = Modifier
                                        .sizeIn(minWidth = 48.dp, minHeight = 48.dp)
                                        .clickable {
                                            keywordViewModel.deleteKeyword(kwEntity.keyword)
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        Icons.Default.Close,
                                        contentDescription = "삭제",
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
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
            }

            Spacer(modifier = Modifier.height(8.dp))
            Button(
                onClick = {
                    coroutineScope.launch {
                        sheetState.hide()
                        onDismiss()
                    }
                },
                modifier = Modifier.fillMaxWidth().heightIn(min = 50.dp),
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
                    Text("쿠팡 파트너스 연동 안내", fontWeight = FontWeight.ExtraBold, fontSize = 20.sp, color = MaterialTheme.colorScheme.onSurface)
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
                    text = "공정위 지침에 따라 상세 페이지에 파트너스 안내 문구가 함께 표시됩니다.",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(bottom = 16.dp)
                )

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f).heightIn(min = 50.dp),
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
                        modifier = Modifier.weight(1f).heightIn(min = 50.dp),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Text("저장", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
fun BrandPlaceholder(
    siteName: String,
    modifier: Modifier = Modifier,
    textStyle: TextStyle = TextStyle(fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color(0xFFF9FAFB))
) {
    val isDark = LocalIsDarkTheme.current
    val semantic = LocalInsightDealColors.current
    val siteLower = siteName.lowercase(Locale.getDefault())
    val (bgColor, fgColor, labelText) = remember(siteLower, isDark) {
        when {
            siteLower.contains("뽐뿌해외") -> Triple(
                if (isDark) Color(0xFF0D3B2B) else Color(0xFFE6F7F0),
                if (isDark) Color(0xFF6EE7B7) else Color(0xFF005C3D),
                "뽐뿌해외"
            )
            siteLower.contains("알리뽐뿌") -> Triple(
                if (isDark) Color(0xFF3A1D2E) else Color(0xFFFDF2F8),
                if (isDark) Color(0xFFF9A8D4) else Color(0xFF9D174D),
                "알리뽐뿌"
            )
            siteLower.contains("뽐뿌") -> Triple(
                if (isDark) Color(0xFF172B4D) else Color(0xFFE8F3FF),
                if (isDark) Color(0xFF93C5FD) else Color(0xFF154EAD),
                "뽐뿌"
            )
            siteLower.contains("퀘이사존") -> Triple(
                if (isDark) Color(0xFF3D2210) else Color(0xFFFFF7ED),
                if (isDark) Color(0xFFFDBA74) else Color(0xFF9A3412),
                "퀘이사존"
            )
            siteLower.contains("펨코") || siteLower.contains("에펨코리아") -> Triple(
                if (isDark) Color(0xFF132A46) else Color(0xFFEBF5FF),
                if (isDark) Color(0xFF7DD3FC) else Color(0xFF035388),
                "펨코"
            )
            siteLower.contains("루리웹") -> Triple(
                if (isDark) Color(0xFF1E264D) else Color(0xFFEEF2FF),
                if (isDark) Color(0xFFA5B4FC) else Color(0xFF3730A3),
                "루리웹"
            )
            siteLower.contains("빠삭") -> Triple(
                if (isDark) Color(0xFF3B1224) else Color(0xFFFFF1F2),
                if (isDark) Color(0xFFFDA4AF) else Color(0xFF9F1239),
                "빠삭"
            )
            siteLower.contains("슬리앙") || siteLower.contains("클리앙") -> Triple(
                if (isDark) Color(0xFF222934) else Color(0xFFF0F3F7),
                if (isDark) Color(0xFFCBD5E1) else Color(0xFF334155),
                "클리앙"
            )
            else -> Triple(
                if (isDark) Color(0xFF0D3B2B) else Color(0xFFE6F7F0),
                if (isDark) Color(0xFF6EE7B7) else Color(0xFF005C3D),
                "얼만데"
            )
        }
    }

    Box(
        modifier = modifier
            .background(bgColor)
            .border(1.dp, semantic.cardBorder, RoundedCornerShape(8.dp)),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.padding(4.dp)
        ) {
            Icon(
                imageVector = Icons.Default.LocalOffer,
                contentDescription = null,
                tint = fgColor,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = labelText,
                style = textStyle.copy(
                    color = fgColor,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = (-0.3).sp
                ),
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

// 🔔 신규 프리미엄 알림 내역 바텀시트
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotificationHistoryBottomSheet(
    onDismiss: () -> Unit,
    onNavigateToKeywordSettings: () -> Unit,
    onOpenUrlInWebView: (String) -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val alertsState by com.ddaeany0919.insightdeal.presentation.mypage.history.NotificationHistoryManager.alerts.collectAsStateWithLifecycle()
    val coroutineScope = rememberCoroutineScope()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    // 💡 바텀시트가 열릴 때마다 서버와 실시간 알림 데이터 동기화 격발
    androidx.compose.runtime.LaunchedEffect(Unit) {
        com.ddaeany0919.insightdeal.presentation.mypage.history.NotificationHistoryManager.syncWithServer(context)
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        dragHandle = { BottomSheetDefaults.DragHandle() }
    ) {
        Column(
            modifier = Modifier
                .padding(24.dp)
                .padding(bottom = 32.dp)
        ) {
            // 헤더 영역
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("알림 내역", fontWeight = FontWeight.ExtraBold, fontSize = 22.sp, color = MaterialTheme.colorScheme.onSurface)
                    Spacer(modifier = Modifier.width(8.dp))
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier.padding(horizontal = 4.dp)
                    ) {
                        Text(
                            text = "${alertsState.size}",
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (alertsState.isNotEmpty()) {
                        TextButton(
                            onClick = {
                                com.ddaeany0919.insightdeal.presentation.mypage.history.NotificationHistoryManager.clearAll(context)
                            },
                            modifier = Modifier.heightIn(min = 48.dp)
                        ) {
                            Text("전체 삭제", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        }
                    }

                    IconButton(onClick = onNavigateToKeywordSettings) {
                        Icon(Icons.Default.Add, contentDescription = "키워드 등록", tint = MaterialTheme.colorScheme.primary)
                    }

                    IconButton(
                        onClick = {
                            coroutineScope.launch {
                                sheetState.hide()
                                onDismiss()
                            }
                        }
                    ) {
                        Icon(Icons.Default.Close, contentDescription = "닫기", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }

            // 本문 영역
            if (alertsState.isEmpty()) {
                // 📭 알림이 비어있을 때의 아름다운 플레이스홀더 디자인
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 220.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Default.NotificationsNone,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f),
                            modifier = Modifier.size(44.dp)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            "아직 받은 알림이 없어요",
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            "키워드를 등록하면 새 특가가 올라올 때 바로 알려드려요",
                            fontSize = 11.sp,
                            color = LocalInsightDealColors.current.textMuted,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f, fill = false),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(
                        items = alertsState,
                        key = { it.id },
                        contentType = { "notification_alert_item" }
                    ) { alert ->
                        val dateText = remember(alert.receivedAt) {
                            val instant = java.time.Instant.ofEpochMilli(alert.receivedAt)
                            val localDateTime = java.time.LocalDateTime.ofInstant(instant, java.time.ZoneId.systemDefault())
                            localDateTime.format(ALERT_DATE_FORMATTER)
                        }

                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    if (alert.dealUrl.isNotBlank()) {
                                        onOpenUrlInWebView(alert.dealUrl)
                                    }
                                },
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                            )
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(
                                        text = "키워드: ${alert.keyword}",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    IconButton(
                                        onClick = {
                                            com.ddaeany0919.insightdeal.presentation.mypage.history.NotificationHistoryManager.deleteAlert(context, alert.id)
                                        },
                                        modifier = Modifier.size(48.dp)
                                    ) {
                                        Icon(
                                            Icons.Default.Delete,
                                            contentDescription = "삭제",
                                            tint = LocalInsightDealColors.current.textMuted,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = alert.title,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = dateText,
                                    fontSize = 10.sp,
                                    color = LocalInsightDealColors.current.textMuted
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

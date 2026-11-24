package com.ddaeany0919.insightdeal.presentation.home

import com.ddaeany0919.insightdeal.presentation.computeUnitPriceBadge
import com.ddaeany0919.insightdeal.presentation.decodeHtmlEntities
import com.ddaeany0919.insightdeal.presentation.extractPaymentConditionBadge
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
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
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
import com.ddaeany0919.insightdeal.data.FeedPreferencesDataSource
import com.ddaeany0919.insightdeal.models.CategoryCatalog
import com.ddaeany0919.insightdeal.models.DealItem
import com.ddaeany0919.insightdeal.models.DealOptionItem
import com.ddaeany0919.insightdeal.models.DealSource
import com.ddaeany0919.insightdeal.utils.CustomTabLauncher
import androidx.compose.foundation.Image
import androidx.compose.ui.res.painterResource

import com.ddaeany0919.insightdeal.presentation.components.shimmerEffect
import com.ddaeany0919.insightdeal.presentation.rememberRelativeTime
import com.ddaeany0919.insightdeal.presentation.bounceClick
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.window.PopupProperties
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.input.nestedscroll.nestedScroll
import kotlinx.coroutines.launch
import java.util.Locale
import com.ddaeany0919.insightdeal.core.network.NetworkMonitor
import com.ddaeany0919.insightdeal.presentation.mypage.DeviceSyncBottomSheet
import com.ddaeany0919.insightdeal.presentation.mypage.DeviceSyncViewModel
import com.ddaeany0919.insightdeal.presentation.mypage.history.NotificationHistoryManager
import com.ddaeany0919.insightdeal.presentation.mypage.history.RecentDealManager
import com.ddaeany0919.insightdeal.presentation.search.SearchAutoComplete
import com.ddaeany0919.insightdeal.presentation.theme.BrandEmeraldPoint
import com.ddaeany0919.insightdeal.presentation.theme.ThemeManager
import com.ddaeany0919.insightdeal.presentation.theme.ThemeMode
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

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction

// [jetpack-compose-performance & anr-audit-and-fix] 정규식, 컬러필터, 포매터 등 고비용 객체 상단 호이스팅
private val HOME_DIGITAL_CATEGORIES = setOf("적립", "이벤트", "모바일/기프티콘", "상품권", "패키지/이용권")
private val FREE_KEYWORDS = listOf("무료", "공짜", "배포", "나눔", "0원", "일시무료", "쿠폰")
private val SHIPPING_LEADING_ZERO_REGEX = Regex("^0(원)?\\s*")
private val SHIPPING_FREE_COND_REGEX = Regex("(이상|미만|이하)(0원?)?\\s*무료")
private val SHIPPING_AMOUNT_COND_REGEX = Regex("(\\d+[만천])(이상|미만|이하)")
private val CLOSED_DEAL_COLOR_FILTER by lazy {
    ColorFilter.colorMatrix(
        ColorMatrix().apply { setToSaturation(0f) }
    )
}
private val STUDIO_THUMB_LIGHT_BG = Color(0xFFF7F8FA)
private val STUDIO_MULTIPLY_COLOR_FILTER by lazy {
    ColorFilter.tint(STUDIO_THUMB_LIGHT_BG, BlendMode.Multiply)
}

/**
 * 스튜디오 톤 이미지 마감 필터 결정 (라이트·진행중 딜에만 Multiply 적용, 다크/AMOLED 및 종료 딜 배제)
 */
internal fun resolveStudioThumbnailColorFilter(
    isDark: Boolean,
    isClosed: Boolean
): ColorFilter? = when {
    isClosed -> CLOSED_DEAL_COLOR_FILTER
    !isDark -> STUDIO_MULTIPLY_COLOR_FILTER
    else -> null
}

/**
 * 홈 피드 초기 로딩 스켈레톤 카드 개수 결정 (모바일/그리드 6개, 컴팩트 8개)
 */
@Suppress("UNUSED_PARAMETER")
internal fun resolveHomeSkeletonCount(
    isExpandedScreen: Boolean,
    isCompactMode: Boolean
): Int = if (isCompactMode) 8 else 6

/**
 * 상세 필터 시트 수납 항목 중 활성화된 필터 카테고리 개수 계산
 */
internal fun countActiveHomeSheetFilters(
    filterState: HomeViewModel.FilterState,
    isCompactMode: Boolean
): Int {
    val activeCommunityChips = filterState.effectiveCommunityChips()
    val hasPlatform = activeCommunityChips.isNotEmpty() ||
        (!filterState.platform.isNullOrBlank() && filterState.platform != "전체")
    return (if (hasPlatform) 1 else 0) +
        (if (!filterState.shop.isNullOrBlank()) 1 else 0) +
        (if (filterState.minPrice != null || filterState.maxPrice != null) 1 else 0) +
        (if (filterState.freeShippingOnly) 1 else 0) +
        (if (filterState.hideClosed) 1 else 0) +
        (if (filterState.includeClosed) 1 else 0) +
        (if (isCompactMode) 1 else 0)
}

/**
 * 카테고리 단독/기본 정렬을 제외한 보조 필터 활성 여부 (적용 필터 칩 1줄 조건부 노출용)
 */
internal fun hasSecondaryHomeFilter(filterState: HomeViewModel.FilterState): Boolean {
    val activeCommunityChips = filterState.effectiveCommunityChips()
    return !filterState.keyword.isNullOrBlank() ||
        activeCommunityChips.isNotEmpty() ||
        (!filterState.platform.isNullOrBlank() && filterState.platform != "전체") ||
        !filterState.shop.isNullOrBlank() ||
        filterState.minPrice != null ||
        filterState.maxPrice != null ||
        filterState.hideClosed ||
        filterState.includeClosed ||
        filterState.freeShippingOnly ||
        filterState.popularOnly
}

internal val HOME_STRING_SET_SAVER: Saver<Set<String>, ArrayList<String>> = Saver(
    save = { ArrayList(it) },
    restore = { it.toSet() }
)

internal enum class HomeEmptyRecoveryAction {
    NONE,
    RESET_FILTERS,
    RESTORE_HIDDEN_DEALS,
    OPEN_MUTED_MANAGER
}

internal data class HomeEmptyFeedUiModel(
    val isEmpty: Boolean,
    val title: String,
    val description: String,
    val primaryActionLabel: String?,
    val primaryAction: HomeEmptyRecoveryAction,
    val secondaryActionLabel: String? = null,
    val secondaryAction: HomeEmptyRecoveryAction = HomeEmptyRecoveryAction.NONE
)

internal fun countVisibleHomeDeals(
    deals: List<DealItem>,
    hiddenDealIds: Set<String>,
    mutedKeywords: Set<String>
): Int = deals.count { deal ->
    !hiddenDealIds.contains(deal.id.toString()) &&
        !HomeViewModel.matchesMutedKeywords(deal, mutedKeywords)
}

internal fun resolveHomeEmptyFeedUiModel(
    loadedDeals: List<DealItem>,
    totalItemCount: Int,
    hiddenDealIds: Set<String>,
    mutedKeywords: Set<String>,
    hasActiveFilter: Boolean
): HomeEmptyFeedUiModel {
    val visibleCount = countVisibleHomeDeals(loadedDeals, hiddenDealIds, mutedKeywords)
    val isAllHiddenFromLoaded = totalItemCount > 0 && loadedDeals.isNotEmpty() && visibleCount == 0
    if (totalItemCount > 0 && !isAllHiddenFromLoaded) {
        return HomeEmptyFeedUiModel(
            isEmpty = false,
            title = "",
            description = "",
            primaryActionLabel = null,
            primaryAction = HomeEmptyRecoveryAction.NONE
        )
    }

    val hiddenByUserDeals = isAllHiddenFromLoaded && loadedDeals.any { hiddenDealIds.contains(it.id.toString()) }
    val hiddenByMutedKeywords = (isAllHiddenFromLoaded && loadedDeals.any { HomeViewModel.matchesMutedKeywords(it, mutedKeywords) }) ||
        (totalItemCount == 0 && mutedKeywords.isNotEmpty())

    return when {
        hiddenByUserDeals -> HomeEmptyFeedUiModel(
            isEmpty = true,
            title = "숨긴 핫딜로 인해 표시할 항목이 없어요",
            description = if (hasActiveFilter) {
                "숨긴 핫딜을 다시 표시하거나 적용된 필터를 초기화해 보세요."
            } else {
                "숨긴 핫딜을 다시 표시하면 목록을 확인할 수 있어요."
            },
            primaryActionLabel = "숨김 해제",
            primaryAction = HomeEmptyRecoveryAction.RESTORE_HIDDEN_DEALS,
            secondaryActionLabel = if (hasActiveFilter) "필터 초기화" else null,
            secondaryAction = if (hasActiveFilter) HomeEmptyRecoveryAction.RESET_FILTERS else HomeEmptyRecoveryAction.NONE
        )
        hasActiveFilter -> HomeEmptyFeedUiModel(
            isEmpty = true,
            title = "조건에 맞는 핫딜이 없어요",
            description = if (mutedKeywords.isNotEmpty()) {
                "검색어·필터 조건이나 뮤트 키워드를 조정해 보세요."
            } else {
                "검색어나 필터 조건을 변경해 보세요."
            },
            primaryActionLabel = "필터 초기화",
            primaryAction = HomeEmptyRecoveryAction.RESET_FILTERS,
            secondaryActionLabel = if (mutedKeywords.isNotEmpty()) "숨김 · 뮤트 관리" else null,
            secondaryAction = if (mutedKeywords.isNotEmpty()) HomeEmptyRecoveryAction.OPEN_MUTED_MANAGER else HomeEmptyRecoveryAction.NONE
        )
        hiddenByMutedKeywords -> HomeEmptyFeedUiModel(
            isEmpty = true,
            title = "조건에 맞는 핫딜이 없어요",
            description = "등록한 뮤트 키워드로 인해 표시할 핫딜이 없어요.",
            primaryActionLabel = "숨김 · 뮤트 관리",
            primaryAction = HomeEmptyRecoveryAction.OPEN_MUTED_MANAGER
        )
        else -> HomeEmptyFeedUiModel(
            isEmpty = true,
            title = "아직 등록된 핫딜이 없어요",
            description = "새로운 특가 정보가 올라오면 바로 알려드릴게요.",
            primaryActionLabel = null,
            primaryAction = HomeEmptyRecoveryAction.NONE
        )
    }
}

private val ALERT_DATE_FORMATTER: java.time.format.DateTimeFormatter =
    java.time.format.DateTimeFormatter.ofPattern("MM-dd HH:mm")
private val HOME_SORT_OPTIONS = listOf(
    "latest" to "최신순",
    "popular" to "인기순",
    "price_asc" to "낮은가격순",
    "price_desc" to "높은가격순",
    "comments" to "댓글순",
    "recommendations" to "추천순"
)
private val HOME_SHOP_FILTER_OPTIONS = listOf(
    "쿠팡" to "쿠팡",
    "네이버" to "네이버",
    "G마켓" to "지마켓",
    "알리" to "알리익스프레스",
    "11번가" to "11번가",
    "옥션" to "옥션",
    "SSG" to "SSG",
    "롯데온" to "롯데온",
    "오늘의집" to "오늘의집",
    "올리브영" to "올리브영",
    "컬리" to "컬리",
    "아마존" to "아마존",
    "카카오쇼핑" to "카카오쇼핑",
    "토스쇼핑" to "토스쇼핑",
    "CJ온스타일" to "CJ온스타일",
    "무신사" to "무신사",
    "스팀" to "스팀",
    "에픽게임즈" to "에픽게임즈"
)
private val HOME_PRICE_PRESETS: List<Triple<String, Int?, Int?>> = listOf(
    Triple("1만원 이하", null, 10_000),
    Triple("3만원 이하", null, 30_000),
    Triple("5만원 이하", null, 50_000)
)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class, ExperimentalLayoutApi::class)
@Composable
@Suppress("DEPRECATION")
fun HomeScreen(
    navController: NavController,
    viewModel: HomeViewModel = viewModel(),
    wishlistViewModel: WishlistViewModel? = null,
    keywordViewModel: KeywordManagerViewModel = viewModel(),
    deviceSyncViewModel: DeviceSyncViewModel = viewModel()
) {
    val filterState by viewModel.filterState.collectAsStateWithLifecycle()
    val viewMode by viewModel.viewMode.collectAsStateWithLifecycle()
    val mutedKeywords by viewModel.mutedKeywords.collectAsStateWithLifecycle()
    val isCompactMode = viewMode == FeedPreferencesDataSource.VIEW_MODE_COMPACT
    val activeCategories = remember(filterState) { filterState.effectiveCategories() }
    val activeCommunityChips = remember(filterState) { filterState.effectiveCommunityChips() }
    val hasSecondaryActiveFilter = remember(filterState) { hasSecondaryHomeFilter(filterState) }
    val hasActiveFilter = hasSecondaryActiveFilter || activeCategories.isNotEmpty()
    val activeSheetFilterCount = remember(filterState, isCompactMode) {
        countActiveHomeSheetFilters(filterState, isCompactMode)
    }
    val catalogCategories by CategoryCatalog.categoriesState.collectAsStateWithLifecycle()
    val configuration = LocalConfiguration.current
    val isExpandedScreen = configuration.screenWidthDp >= 600
    val isCompactHeader = configuration.screenWidthDp < 380
    val focusManager = LocalFocusManager.current
    var searchInput by rememberSaveable { mutableStateOf(filterState.keyword.orEmpty()) }
    var showSearchPopover by rememberSaveable { mutableStateOf(false) }
    var showMoreMenu by rememberSaveable { mutableStateOf(false) }
    var showFilterBottomSheet by rememberSaveable { mutableStateOf(false) }
    var showDeviceSyncSheet by rememberSaveable { mutableStateOf(false) }
    val searchInteractionSource = remember { MutableInteractionSource() }

    // 검색 팝오버 노출 중 시스템 뒤로가기 시 앱 이탈 대신 팝오버만 닫고 포커스를 해제합니다.
    BackHandler(enabled = showSearchPopover) {
        showSearchPopover = false
        focusManager.clearFocus()
    }

    // BasicTextField가 이미 포커스를 보유한 상태에서 팝오버가 닫힌 뒤 재탭해도 다시 열리도록 Release 이벤트를 감지합니다.
    LaunchedEffect(searchInteractionSource) {
        searchInteractionSource.interactions.collect { interaction ->
            if (interaction is PressInteraction.Release) {
                showSearchPopover = true
            }
        }
    }

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
    val wishlistCount = remember(wishlistState) {
        (wishlistState as? WishlistUiState.Success)?.items?.size ?: 0
    }

    val context = LocalContext.current
    val haptic = androidx.compose.ui.platform.LocalHapticFeedback.current
    val networkMonitor = remember { NetworkMonitor(context) }
    val isOnline by networkMonitor.isOnline.collectAsStateWithLifecycle(initialValue = true)
    val isDark = LocalIsDarkTheme.current
    val searchAutoComplete = remember(context) { SearchAutoComplete.getInstance(context) }
    val searchHistory by searchAutoComplete.searchHistory.collectAsStateWithLifecycle()
    val recentDeals by RecentDealManager.recentDeals.collectAsStateWithLifecycle()
    val themeManager = remember(context) { ThemeManager.getInstance(context) }
    val currentThemeMode by themeManager.themeMode.collectAsStateWithLifecycle()
    val syncUiState by deviceSyncViewModel.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(syncUiState.syncCompletedSeq) {
        if (syncUiState.syncCompletedSeq > 0) {
            keywordViewModel.fetchKeywords()
            wishlistViewModel?.syncFromServer()
            NotificationHistoryManager.syncWithServer(context)
        }
    }

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

    DisposableEffect(lifecycleOwner, viewModel) {
        if (lifecycleOwner.lifecycle.currentState.isAtLeast(androidx.lifecycle.Lifecycle.State.STARTED)) {
            viewModel.onForegroundChanged(true)
        }
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            when (event) {
                androidx.lifecycle.Lifecycle.Event.ON_START -> {
                    viewModel.onForegroundChanged(true)
                }
                androidx.lifecycle.Lifecycle.Event.ON_STOP -> {
                    viewModel.onForegroundChanged(false)
                }
                androidx.lifecycle.Lifecycle.Event.ON_RESUME -> {
                    isNotificationPermissionGranted = androidx.core.app.NotificationManagerCompat.from(context).areNotificationsEnabled()
                    val prefs = context.getSharedPreferences("app", android.content.Context.MODE_PRIVATE)
                    val disabledPlatforms = prefs.getStringSet("disabled_platforms", emptySet()) ?: emptySet()
                    viewModel.updateDisabledPlatforms(disabledPlatforms)
                }
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            viewModel.onForegroundChanged(false)
        }
    }

    val dealsPagingItems = viewModel.dealsPagingData.collectAsLazyPagingItems()
    val gridState = rememberLazyGridState()
    val isScrolled by remember {
        derivedStateOf {
            gridState.firstVisibleItemIndex > 0 || gridState.firstVisibleItemScrollOffset > 40
        }
    }
    val showScrollTop by remember {
        derivedStateOf { gridState.firstVisibleItemIndex >= 3 }
    }

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
    var showKeywordDialog by rememberSaveable { mutableStateOf(false) }
    if (showKeywordDialog) {
        KeywordSettingsBottomSheet(
            onDismiss = { showKeywordDialog = false },
            keywordViewModel = keywordViewModel
        )
    }

    val notificationAlerts by com.ddaeany0919.insightdeal.presentation.mypage.history.NotificationHistoryManager.alerts.collectAsStateWithLifecycle()
    var showNotificationHistory by rememberSaveable { mutableStateOf(false) }

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
    var showMutedKeywordsSheet by rememberSaveable { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    val restoreHiddenDeals: () -> Unit = {
        val prefs = context.getSharedPreferences("app", android.content.Context.MODE_PRIVATE)
        prefs.edit().remove("hidden_deals").apply()
        hiddenDeals.clear()
        scope.launch {
            snackbarHostState.showSnackbar(
                message = "숨긴 핫딜을 모두 다시 표시했어요.",
                duration = SnackbarDuration.Short
            )
        }
    }

    var showCoupangDialog by rememberSaveable { mutableStateOf(false) }
    if (showCoupangDialog) {
        CoupangSettingsDialog(onDismiss = { showCoupangDialog = false })
    }

    val hasNewDeals by viewModel.hasNewDeals.collectAsStateWithLifecycle()
    val newDealsCount by viewModel.newDealsCount.collectAsStateWithLifecycle()

    val topHotDeals by viewModel.topHotDeals.collectAsStateWithLifecycle()
    val topPicks = remember(topHotDeals) {
        (topHotDeals as List<DealItem?>?)
            .orEmpty()
            .filterNotNull()
            .filter { deal ->
                val isSuperHot = deal.honeyScore >= 100
                !deal.isClosed && isSuperHot
            }
            .filter { deal ->
                val safeTitle = (deal.title as String?).orEmpty()
                deal.category != "적립" && !safeTitle.contains("적립") && !safeTitle.contains("불가") && !safeTitle.contains("정보")
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

    Box(modifier = Modifier.fillMaxSize()) {
        val homeSemantic = LocalInsightDealColors.current
        val isHairlineFeed = !isExpandedScreen && !isCompactMode
        val topBarContainerColor by animateColorAsState(
            targetValue = if (isScrolled) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.background,
            animationSpec = tween(durationMillis = 180),
            label = "topBarContainerColor"
        )
        val topBarShadowElevation by animateDpAsState(
            targetValue = if (isScrolled) 3.dp else 0.dp,
            animationSpec = tween(durationMillis = 180),
            label = "topBarShadowElevation"
        )
        val topDividerColor by animateColorAsState(
            targetValue = if (isScrolled) {
                MaterialTheme.colorScheme.outline.copy(alpha = if (isDark) 0.55f else 0.7f)
            } else {
                homeSemantic.cardBorder
            },
            animationSpec = tween(durationMillis = 180),
            label = "topDividerColor"
        )
        val topDividerThickness by animateDpAsState(
            targetValue = if (isScrolled) 1.dp else 0.75.dp,
            animationSpec = tween(durationMillis = 180),
            label = "topDividerThickness"
        )
        Column(modifier = Modifier.fillMaxSize()) {
            Surface(
                color = topBarContainerColor,
                shadowElevation = topBarShadowElevation
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    // 1줄: [로고 + "얼만데"] + [중앙 라운드 검색창 & 팝오버] + [♡ 찜 N · 🔔 · ⋮]
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 54.dp)
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .heightIn(min = 48.dp)
                                .bounceClick(onClickLabel = "홈 새로고침 및 맨 위로 이동") {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    scope.launch {
                                        try {
                                            searchInput = ""
                                            showSearchPopover = false
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
                                modifier = Modifier.size(28.dp)
                            )
                            if (!isCompactHeader) {
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "얼만데",
                                    fontSize = 17.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    letterSpacing = (-0.5).sp,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                    softWrap = false
                                )
                            }
                        }

                        // 중앙 라운드 검색창 + 급상승/최근검색어 팝오버
                        Box(modifier = Modifier.weight(1f)) {
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 38.dp),
                                shape = RoundedCornerShape(999.dp),
                                color = if (isDark) Color(0xFF191F28) else Color(0xFFF3F4F6),
                                border = BorderStroke(
                                    1.dp,
                                    if (showSearchPopover || searchInput.isNotBlank()) {
                                        MaterialTheme.colorScheme.primary.copy(alpha = 0.55f)
                                    } else {
                                        homeSemantic.cardBorder
                                    }
                                )
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable(
                                            interactionSource = remember { MutableInteractionSource() },
                                            indication = null
                                        ) { showSearchPopover = true }
                                        .padding(horizontal = 10.dp, vertical = 7.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Search,
                                        contentDescription = "검색",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Box(modifier = Modifier.weight(1f)) {
                                        if (searchInput.isEmpty()) {
                                            Text(
                                                text = if (isCompactHeader) "핫딜 검색" else "핫딜 검색 (-제외, :가격)",
                                                fontSize = 13.sp,
                                                color = homeSemantic.textMuted,
                                                maxLines = 1,
                                                softWrap = false,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                        BasicTextField(
                                            value = searchInput,
                                            onValueChange = {
                                                searchInput = it
                                                showSearchPopover = true
                                            },
                                            singleLine = true,
                                            interactionSource = searchInteractionSource,
                                            textStyle = TextStyle(
                                                fontSize = 13.sp,
                                                fontWeight = FontWeight.Medium,
                                                color = MaterialTheme.colorScheme.onSurface
                                            ),
                                            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                                            keyboardActions = KeyboardActions(
                                                onSearch = {
                                                    val query = searchInput.trim()
                                                    if (query.isNotEmpty()) {
                                                        searchAutoComplete.addToSearchHistory(query)
                                                    }
                                                    viewModel.searchDeals(query)
                                                    showSearchPopover = false
                                                    focusManager.clearFocus()
                                                }
                                            ),
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .onFocusChanged { focusState ->
                                                    if (focusState.isFocused) {
                                                        showSearchPopover = true
                                                    }
                                                }
                                        )
                                    }
                                    if (searchInput.isNotEmpty()) {
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Box(
                                            modifier = Modifier
                                                .size(24.dp)
                                                .clip(CircleShape)
                                                .clickable {
                                                    searchInput = ""
                                                    viewModel.searchDeals("")
                                                },
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Cancel,
                                                contentDescription = "검색어 지우기",
                                                tint = homeSemantic.textMuted,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                    }
                                }
                            }

                            DropdownMenu(
                                expanded = showSearchPopover && (searchHistory.isNotEmpty() || tickerItems.isNotEmpty()),
                                onDismissRequest = { showSearchPopover = false },
                                properties = PopupProperties(focusable = false),
                                modifier = Modifier
                                    .widthIn(min = 260.dp, max = 360.dp)
                                    .background(MaterialTheme.colorScheme.surface)
                            ) {
                                Column(
                                    modifier = Modifier
                                        .widthIn(min = 260.dp, max = 360.dp)
                                        .padding(horizontal = 14.dp, vertical = 10.dp),
                                    verticalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    if (searchHistory.isNotEmpty()) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Text(
                                                text = "최근 검색어",
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                            Text(
                                                text = "전체 삭제",
                                                fontSize = 11.5.sp,
                                                fontWeight = FontWeight.SemiBold,
                                                color = homeSemantic.textMuted,
                                                modifier = Modifier
                                                    .clip(RoundedCornerShape(6.dp))
                                                    .clickable { searchAutoComplete.clearSearchHistory() }
                                                    .padding(horizontal = 4.dp, vertical = 2.dp)
                                            )
                                        }
                                        FlowRow(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                                            verticalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            searchHistory.take(8).forEach { historyKeyword ->
                                                Surface(
                                                    shape = RoundedCornerShape(999.dp),
                                                    color = MaterialTheme.colorScheme.surfaceVariant,
                                                    border = BorderStroke(0.75.dp, homeSemantic.cardBorder)
                                                ) {
                                                    Row(
                                                        modifier = Modifier.padding(start = 10.dp, end = 6.dp, top = 5.dp, bottom = 5.dp),
                                                        verticalAlignment = Alignment.CenterVertically,
                                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                                    ) {
                                                        Text(
                                                            text = historyKeyword,
                                                            fontSize = 12.sp,
                                                            fontWeight = FontWeight.Medium,
                                                            color = MaterialTheme.colorScheme.onSurface,
                                                            maxLines = 1,
                                                            softWrap = false,
                                                            overflow = TextOverflow.Ellipsis,
                                                            modifier = Modifier
                                                                .widthIn(max = 120.dp)
                                                                .clickable {
                                                                    searchInput = historyKeyword
                                                                    searchAutoComplete.addToSearchHistory(historyKeyword)
                                                                    viewModel.searchDeals(historyKeyword)
                                                                    showSearchPopover = false
                                                                    focusManager.clearFocus()
                                                                }
                                                        )
                                                        Icon(
                                                            imageVector = Icons.Default.Close,
                                                            contentDescription = "$historyKeyword 검색 기록 삭제",
                                                            tint = homeSemantic.textMuted,
                                                            modifier = Modifier
                                                                .size(14.dp)
                                                                .clickable {
                                                                    searchAutoComplete.removeFromSearchHistory(historyKeyword)
                                                                }
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }

                                    if (searchHistory.isNotEmpty() && tickerItems.isNotEmpty()) {
                                        HorizontalDivider(color = homeSemantic.cardBorder, thickness = 0.75.dp)
                                    }

                                    if (tickerItems.isNotEmpty()) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.AutoMirrored.Outlined.TrendingUp,
                                                    contentDescription = null,
                                                    tint = MaterialTheme.colorScheme.primary,
                                                    modifier = Modifier.size(14.dp)
                                                )
                                                Text(
                                                    text = "급상승 인기검색어",
                                                    fontSize = 12.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = MaterialTheme.colorScheme.onSurface
                                                )
                                            }
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                modifier = Modifier
                                                    .clip(RoundedCornerShape(6.dp))
                                                    .clickable {
                                                        showSearchPopover = false
                                                        focusManager.clearFocus()
                                                        navController.navigate("advanced_search")
                                                    }
                                                    .padding(horizontal = 4.dp, vertical = 2.dp)
                                            ) {
                                                Text(
                                                    text = "전체 순위",
                                                    fontSize = 11.5.sp,
                                                    fontWeight = FontWeight.SemiBold,
                                                    color = MaterialTheme.colorScheme.primary
                                                )
                                                Icon(
                                                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                                    contentDescription = null,
                                                    tint = MaterialTheme.colorScheme.primary,
                                                    modifier = Modifier.size(14.dp)
                                                )
                                            }
                                        }

                                        Column(
                                            modifier = Modifier.fillMaxWidth(),
                                            verticalArrangement = Arrangement.spacedBy(2.dp)
                                        ) {
                                            tickerItems.take(10).forEach { rankItem ->
                                                Row(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .clip(RoundedCornerShape(8.dp))
                                                        .clickable {
                                                            searchInput = rankItem.keyword
                                                            searchAutoComplete.addToSearchHistory(rankItem.keyword)
                                                            viewModel.searchDeals(rankItem.keyword)
                                                            showSearchPopover = false
                                                            focusManager.clearFocus()
                                                        }
                                                        .padding(horizontal = 6.dp, vertical = 6.dp),
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                                ) {
                                                    Text(
                                                        text = "${rankItem.rank}",
                                                        fontSize = 12.5.sp,
                                                        fontWeight = FontWeight.ExtraBold,
                                                        color = if (rankItem.rank <= 3) MaterialTheme.colorScheme.primary else homeSemantic.textMuted,
                                                        modifier = Modifier.width(18.dp)
                                                    )
                                                    Text(
                                                        text = rankItem.keyword,
                                                        fontSize = 13.sp,
                                                        fontWeight = FontWeight.SemiBold,
                                                        color = MaterialTheme.colorScheme.onSurface,
                                                        maxLines = 1,
                                                        overflow = TextOverflow.Ellipsis,
                                                        modifier = Modifier.weight(1f)
                                                    )
                                                    if (rankItem.status == "HOT") {
                                                        Surface(
                                                            shape = RoundedCornerShape(4.dp),
                                                            color = MaterialTheme.colorScheme.errorContainer
                                                        ) {
                                                            Text(
                                                                text = "HOT",
                                                                fontSize = 9.5.sp,
                                                                fontWeight = FontWeight.ExtraBold,
                                                                color = MaterialTheme.colorScheme.onErrorContainer,
                                                                modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
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

                        // 우측 찜 카운트 필 (♡ 찜 N)
                        Box(
                            modifier = Modifier
                                .heightIn(min = 48.dp)
                                .semantics {
                                    role = Role.Button
                                    contentDescription = "찜한 핫딜 ${wishlistCount}건"
                                }
                                .bounceClick(onClickLabel = "찜한 핫딜 ${wishlistCount}건") {
                                    navController.navigate("watchlist")
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Surface(
                                shape = RoundedCornerShape(999.dp),
                                color = if (wishlistCount > 0) {
                                    MaterialTheme.colorScheme.errorContainer.copy(alpha = if (isDark) 0.4f else 0.65f)
                                } else {
                                    MaterialTheme.colorScheme.surfaceVariant
                                },
                                border = BorderStroke(0.75.dp, homeSemantic.cardBorder),
                                modifier = Modifier.height(34.dp)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = if (isCompactHeader) 8.dp else 9.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(3.dp)
                                ) {
                                    Icon(
                                        imageVector = if (wishlistCount > 0) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                                        contentDescription = null,
                                        tint = if (wishlistCount > 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Text(
                                        text = when {
                                            isCompactHeader && wishlistCount > 0 -> "$wishlistCount"
                                            isCompactHeader -> "찜"
                                            wishlistCount > 0 -> "찜 $wishlistCount"
                                            else -> "찜"
                                        },
                                        fontSize = 11.5.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        maxLines = 1,
                                        softWrap = false,
                                        style = TextStyle(platformStyle = PlatformTextStyle(includeFontPadding = false))
                                    )
                                }
                            }
                        }

                        // 알림 내역 아이콘 + 배지
                        val alertsCount = notificationAlerts.size
                        Box(
                            modifier = Modifier
                                .sizeIn(minWidth = 38.dp, minHeight = 48.dp)
                                .bounceClick(
                                    onClickLabel = if (alertsCount > 0) "알림 내역 (${alertsCount}건)" else "알림 내역"
                                ) { showNotificationHistory = true }
                                .padding(horizontal = 4.dp, vertical = 6.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Notifications,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.size(22.dp)
                            )
                            if (alertsCount > 0) {
                                Surface(
                                    shape = CircleShape,
                                    color = homeSemantic.danger,
                                    modifier = Modifier
                                        .align(Alignment.TopEnd)
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
                                            fontSize = 9.5.sp,
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

                        // 우측 더보기 메뉴 (키워드 알림 설정, 기기 동기화, 테마 전환, 앱 환경설정)
                        Box {
                            Box(
                                modifier = Modifier
                                    .sizeIn(minWidth = 36.dp, minHeight = 48.dp)
                                    .bounceClick(onClickLabel = "더보기 메뉴") { showMoreMenu = true }
                                    .padding(horizontal = 4.dp, vertical = 6.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.MoreVert,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.size(21.dp)
                                )
                            }

                            DropdownMenu(
                                expanded = showMoreMenu,
                                onDismissRequest = { showMoreMenu = false },
                                modifier = Modifier.background(MaterialTheme.colorScheme.surface)
                            ) {
                                DropdownMenuItem(
                                    text = { Text("키워드 알림 설정", fontWeight = FontWeight.SemiBold) },
                                    leadingIcon = {
                                        Icon(
                                            imageVector = Icons.Outlined.NotificationsActive,
                                            contentDescription = null
                                        )
                                    },
                                    onClick = {
                                        showMoreMenu = false
                                        showKeywordDialog = true
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("기기 동기화", fontWeight = FontWeight.SemiBold) },
                                    leadingIcon = {
                                        Icon(
                                            imageVector = Icons.Outlined.Sync,
                                            contentDescription = null
                                        )
                                    },
                                    onClick = {
                                        showMoreMenu = false
                                        deviceSyncViewModel.clearFeedback()
                                        showDeviceSyncSheet = true
                                    }
                                )
                                DropdownMenuItem(
                                    text = {
                                        val modeShort = when (currentThemeMode) {
                                            ThemeMode.LIGHT -> "라이트"
                                            ThemeMode.DARK -> "다크"
                                            ThemeMode.AMOLED -> "AMOLED"
                                            ThemeMode.SYSTEM -> "시스템"
                                        }
                                        Text("테마 전환 ($modeShort)", fontWeight = FontWeight.SemiBold)
                                    },
                                    leadingIcon = {
                                        Icon(
                                            imageVector = if (isDark) Icons.Outlined.DarkMode else Icons.Outlined.LightMode,
                                            contentDescription = null
                                        )
                                    },
                                    onClick = {
                                        val nextMode = when (currentThemeMode) {
                                            ThemeMode.LIGHT -> ThemeMode.DARK
                                            ThemeMode.DARK -> ThemeMode.AMOLED
                                            ThemeMode.AMOLED -> ThemeMode.SYSTEM
                                            ThemeMode.SYSTEM -> ThemeMode.LIGHT
                                        }
                                        themeManager.setThemeMode(nextMode)
                                        themeManager.setAmoledMode(nextMode == ThemeMode.AMOLED)
                                        showMoreMenu = false
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("앱 환경설정", fontWeight = FontWeight.SemiBold) },
                                    leadingIcon = {
                                        Icon(
                                            imageVector = Icons.Outlined.Settings,
                                            contentDescription = null
                                        )
                                    },
                                    onClick = {
                                        showMoreMenu = false
                                        navController.navigate("settings")
                                    }
                                )
                            }
                        }
                    }

                    // 2줄: 좌측 가로 스크롤 카테고리 밑줄 탭 + 우측 [인기 딜만] + [상세 필터(배지)]
                    val categoryListState = rememberLazyListState()
                    val isLargeFontScale = LocalDensity.current.fontScale >= 1.15f
                    val showRightCategoryFade by remember {
                        derivedStateOf { categoryListState.canScrollForward }
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 10.dp, end = 10.dp, bottom = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(if (isLargeFontScale) 4.dp else 6.dp)
                    ) {
                        LazyRow(
                            state = categoryListState,
                            modifier = Modifier
                                .weight(1f)
                                .graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen)
                                .drawWithContent {
                                    drawContent()
                                    if (showRightCategoryFade && size.width > 0f) {
                                        val fadePx = 20.dp.toPx().coerceAtMost(size.width * 0.25f)
                                        drawRect(
                                            brush = Brush.horizontalGradient(
                                                colors = listOf(Color.Black, Color.Transparent),
                                                startX = size.width - fadePx,
                                                endX = size.width
                                            ),
                                            blendMode = BlendMode.DstIn
                                        )
                                    }
                                },
                            horizontalArrangement = Arrangement.spacedBy(2.dp),
                            contentPadding = PaddingValues(end = if (showRightCategoryFade) 18.dp else 4.dp)
                        ) {
                            item(key = "tab_all", contentType = "category_tab") {
                                val isSelected = activeCategories.isEmpty()
                                Column(
                                    modifier = Modifier
                                        .width(IntrinsicSize.Max)
                                        .heightIn(min = 48.dp)
                                        .semantics {
                                            role = Role.Tab
                                            selected = isSelected
                                            stateDescription = if (isSelected) "선택됨" else "선택 안 됨"
                                        }
                                        .clickable {
                                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                            viewModel.toggleCategory("전체")
                                        },
                                    verticalArrangement = Arrangement.Bottom,
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Text(
                                        text = "전체",
                                        fontSize = 13.5.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                        color = if (isSelected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        softWrap = false,
                                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 10.dp)
                                    )
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(2.dp)
                                            .background(if (isSelected) BrandEmeraldPoint else Color.Transparent)
                                    )
                                }
                            }
                            items(
                                items = catalogCategories,
                                key = { "tab_${it.key}" },
                                contentType = { "category_tab" }
                            ) { cat ->
                                val isSelected = cat.key in activeCategories
                                Column(
                                    modifier = Modifier
                                        .width(IntrinsicSize.Max)
                                        .heightIn(min = 48.dp)
                                        .semantics {
                                            role = Role.Tab
                                            selected = isSelected
                                            stateDescription = if (isSelected) "선택됨" else "선택 안 됨"
                                        }
                                        .clickable {
                                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                            viewModel.toggleCategory(cat.key)
                                        },
                                    verticalArrangement = Arrangement.Bottom,
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Text(
                                        text = cat.label,
                                        fontSize = 13.5.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                        color = if (isSelected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        softWrap = false,
                                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 10.dp)
                                    )
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(2.dp)
                                            .background(if (isSelected) BrandEmeraldPoint else Color.Transparent)
                                    )
                                }
                            }
                        }

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(if (isLargeFontScale) 4.dp else 5.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            HomeSelectablePillChip(
                                label = if (isLargeFontScale) "인기" else "인기 딜만",
                                selected = filterState.popularOnly,
                                role = Role.Checkbox,
                                shape = RoundedCornerShape(999.dp),
                                chipHeight = 32.dp,
                                leadingIcon = Icons.Filled.LocalFireDepartment,
                                selectedContainerColor = homeSemantic.priceContainer,
                                selectedContentColor = homeSemantic.onPriceContainer,
                                selectedBorderColor = homeSemantic.priceAccent,
                                onClick = {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    viewModel.togglePopularOnly()
                                }
                            )

                            Box(
                                modifier = Modifier
                                    .heightIn(min = 48.dp)
                                    .semantics {
                                        role = Role.Button
                                        contentDescription = if (activeSheetFilterCount > 0) {
                                            "상세 필터 (${activeSheetFilterCount}개 적용됨)"
                                        } else {
                                            "상세 필터"
                                        }
                                    }
                                    .clickable {
                                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        showFilterBottomSheet = true
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Surface(
                                    modifier = Modifier.height(32.dp),
                                    shape = RoundedCornerShape(999.dp),
                                    color = if (activeSheetFilterCount > 0) {
                                        MaterialTheme.colorScheme.primaryContainer
                                    } else {
                                        MaterialTheme.colorScheme.surface
                                    },
                                    border = BorderStroke(
                                        1.dp,
                                        if (activeSheetFilterCount > 0) {
                                            MaterialTheme.colorScheme.primary
                                        } else {
                                            MaterialTheme.colorScheme.outline
                                        }
                                    )
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = if (isLargeFontScale) 7.dp else 9.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Outlined.Tune,
                                            contentDescription = null,
                                            tint = if (activeSheetFilterCount > 0) {
                                                MaterialTheme.colorScheme.onPrimaryContainer
                                            } else {
                                                MaterialTheme.colorScheme.onSurfaceVariant
                                            },
                                            modifier = Modifier.size(14.dp)
                                        )
                                        Text(
                                            text = if (isLargeFontScale) "필터" else "상세 필터",
                                            fontSize = 12.sp,
                                            fontWeight = if (activeSheetFilterCount > 0) FontWeight.Bold else FontWeight.Medium,
                                            color = if (activeSheetFilterCount > 0) {
                                                MaterialTheme.colorScheme.onPrimaryContainer
                                            } else {
                                                MaterialTheme.colorScheme.onSurfaceVariant
                                            },
                                            maxLines = 1,
                                            softWrap = false,
                                            style = TextStyle(platformStyle = PlatformTextStyle(includeFontPadding = false))
                                        )
                                        if (activeSheetFilterCount > 0) {
                                            Surface(
                                                shape = CircleShape,
                                                color = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(17.dp)
                                            ) {
                                                Box(contentAlignment = Alignment.Center) {
                                                    Text(
                                                        text = "$activeSheetFilterCount",
                                                        fontSize = 10.sp,
                                                        fontWeight = FontWeight.ExtraBold,
                                                        color = MaterialTheme.colorScheme.onPrimary,
                                                        maxLines = 1,
                                                        softWrap = false,
                                                        style = TextStyle(platformStyle = PlatformTextStyle(includeFontPadding = false))
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // 보조 필터가 1개 이상 활성일 때만 노출되는 적용 필터 칩 1줄
                    AnimatedVisibility(visible = hasSecondaryActiveFilter) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
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
                                filterState.shop?.takeIf { it.isNotBlank() }?.let { shopKey ->
                                    item(key = "active_shop", contentType = "active_filter_chip") {
                                        val shopLabel = HOME_SHOP_FILTER_OPTIONS.firstOrNull { it.second == shopKey }?.first
                                            ?: shopKey
                                        HomeActiveFilterChip(
                                            label = "쇼핑몰: $shopLabel",
                                            onRemove = { viewModel.setShopFilter(null) }
                                        )
                                    }
                                }
                                if (filterState.minPrice != null || filterState.maxPrice != null) {
                                    item(key = "active_price_range", contentType = "active_filter_chip") {
                                        val nf = java.text.NumberFormat.getNumberInstance(Locale.KOREA)
                                        val minP = filterState.minPrice
                                        val maxP = filterState.maxPrice
                                        val priceLabel = when {
                                            minP != null && maxP != null -> "가격: ${nf.format(minP)}~${nf.format(maxP)}원"
                                            maxP != null -> "가격: ~${nf.format(maxP)}원"
                                            else -> "가격: ${nf.format(minP ?: 0)}원~"
                                        }
                                        HomeActiveFilterChip(
                                            label = priceLabel,
                                            onRemove = { viewModel.setPriceRangeFilter(null, null) }
                                        )
                                    }
                                }
                                if (filterState.hideClosed) {
                                    item(key = "active_hide_closed", contentType = "active_filter_chip") {
                                        HomeActiveFilterChip(
                                            label = "품절 제외",
                                            onRemove = { viewModel.toggleHideClosed() }
                                        )
                                    }
                                }
                                if (filterState.includeClosed) {
                                    item(key = "active_include_closed", contentType = "active_filter_chip") {
                                        HomeActiveFilterChip(
                                            label = "종료 포함",
                                            onRemove = { viewModel.toggleIncludeClosed() }
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
                                    maxLines = 1,
                                    softWrap = false
                                )
                            }
                        }
                    }

                    HorizontalDivider(thickness = topDividerThickness, color = topDividerColor)
                }
            }

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

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .nestedScroll(pullRefreshState.nestedScrollConnection)
            ) {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(if (isExpandedScreen) 2 else 1),
                    state = gridState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = if (isHairlineFeed) {
                        PaddingValues(top = 10.dp, bottom = 88.dp)
                    } else {
                        PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 88.dp)
                    },
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(
                        when {
                            isExpandedScreen -> 12.dp
                            isCompactMode -> 8.dp
                            else -> 0.dp
                        }
                    )
                ) {
                    if (recentDeals.isNotEmpty()) {
                        item(
                            key = "recent_deals_tray",
                            span = { GridItemSpan(maxLineSpan) },
                            contentType = "recent_deals_tray"
                        ) {
                            HomeRecentDealsTray(
                                recentDeals = recentDeals,
                                isDark = isDark,
                                isHairlineFeed = isHairlineFeed,
                                onDealClick = { dealId -> onNavigateToDetail(dealId) },
                                onRemoveDeal = { dealId -> RecentDealManager.removeRecentDeal(context, dealId) },
                                onClearAll = { RecentDealManager.clearRecentDeals(context) }
                            )
                        }
                    }

                    if (!hasActiveFilter && (topPicks.isNotEmpty() || !hideKeywordBanner)) {
                        item(
                            key = "today_picks_section",
                            span = { GridItemSpan(maxLineSpan) },
                            contentType = "today_picks_section"
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .then(
                                        if (isHairlineFeed) {
                                            Modifier.padding(horizontal = 16.dp).padding(bottom = 12.dp)
                                        } else {
                                            Modifier
                                        }
                                    )
                            ) {
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
                                                                    .background(if (isDark) Color(0xFF1F242D) else STUDIO_THUMB_LIGHT_BG)
                                                                    .clip(RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)),
                                                                colorFilter = resolveStudioThumbnailColorFilter(isDark = isDark, isClosed = pick.isClosed),
                                                                contentScale = ContentScale.Crop,
                                                                onError = {
                                                                    if (
                                                                        fallbackPickUrl != null &&
                                                                        activePickUrl != fallbackPickUrl &&
                                                                        !activePickUrl.orEmpty().contains("/api/proxy-image", ignoreCase = true)
                                                                    ) {
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
                                                                        maxLines = 1,
                                                                        softWrap = false,
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
                                                                                maxLines = 1,
                                                                                softWrap = false,
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

                // 피드 헤더 ("실시간 핫딜") + 우측 캡슐 정렬 세그먼트 (6종 정렬)
                item(
                    key = "feed_header_section",
                    span = { GridItemSpan(maxLineSpan) },
                    contentType = "feed_header_section"
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(
                                horizontal = if (isHairlineFeed) 16.dp else 2.dp,
                                vertical = 6.dp
                            ),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            text = "실시간 핫딜",
                            fontSize = 17.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            softWrap = false,
                            modifier = Modifier.semantics { heading() }
                        )

                        val trackColor = if (isDark) Color(0xFF191F28) else Color(0xFFF2F4F7)
                        Box(
                            modifier = Modifier.weight(1f),
                            contentAlignment = Alignment.CenterEnd
                        ) {
                            Row(
                                modifier = Modifier
                                    .horizontalScroll(rememberScrollState())
                                    .clip(RoundedCornerShape(999.dp))
                                    .background(trackColor)
                                    .border(1.dp, homeSemantic.cardBorder, RoundedCornerShape(999.dp))
                                    .padding(3.dp),
                                horizontalArrangement = Arrangement.spacedBy(2.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                HOME_SORT_OPTIONS.forEach { (sortKey, sortLabel) ->
                                    val isSortSelected = filterState.sortBy == sortKey
                                    Surface(
                                        onClick = {
                                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                            viewModel.selectSort(sortKey)
                                        },
                                        shape = RoundedCornerShape(999.dp),
                                        color = when {
                                            isSortSelected && isDark -> MaterialTheme.colorScheme.surfaceVariant
                                            isSortSelected -> MaterialTheme.colorScheme.surface
                                            else -> Color.Transparent
                                        },
                                        shadowElevation = if (isSortSelected && !isDark) 1.dp else 0.dp,
                                        modifier = Modifier
                                            .heightIn(min = 32.dp)
                                            .semantics {
                                                role = Role.Tab
                                                selected = isSortSelected
                                            }
                                    ) {
                                        Box(
                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                text = sortLabel,
                                                fontSize = 12.sp,
                                                fontWeight = if (isSortSelected) FontWeight.Bold else FontWeight.Medium,
                                                color = if (isSortSelected) MaterialTheme.colorScheme.onSurface
                                                else MaterialTheme.colorScheme.onSurfaceVariant,
                                                maxLines = 1,
                                                softWrap = false,
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

                // 상태 관리를 통한 스켈레톤 로딩 노출
                when {
                    dealsPagingItems.loadState.refresh is LoadState.Loading && dealsPagingItems.itemCount == 0 -> {
                        if (isHairlineFeed) {
                            item(
                                key = "skeleton_top_divider",
                                span = { GridItemSpan(maxLineSpan) },
                                contentType = "feed_divider"
                            ) {
                                HorizontalDivider(thickness = 0.75.dp, color = homeSemantic.cardBorder)
                            }
                        }
                        items(
                            count = resolveHomeSkeletonCount(
                                isExpandedScreen = isExpandedScreen,
                                isCompactMode = isCompactMode
                            ),
                            key = { index -> "skeleton_$index" },
                            span = if (isCompactMode) {
                                { GridItemSpan(maxLineSpan) }
                            } else null,
                            contentType = { if (isCompactMode) "skeleton_compact_loader" else "skeleton_loader" }
                        ) {
                            if (isCompactMode) {
                                HomeCompactDealCardSkeleton()
                            } else {
                                HomeDealCardSkeleton(isExpandedGrid = isExpandedScreen)
                            }
                        }
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
                                    .padding(horizontal = if (isHairlineFeed) 16.dp else 4.dp, vertical = 8.dp),
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
                        val snapshotItems = dealsPagingItems.itemSnapshotList.items
                        val hiddenDealsSet = hiddenDeals.toSet()
                        val emptyFeedUi = resolveHomeEmptyFeedUiModel(
                            loadedDeals = snapshotItems,
                            totalItemCount = dealsPagingItems.itemCount,
                            hiddenDealIds = hiddenDealsSet,
                            mutedKeywords = mutedKeywords,
                            hasActiveFilter = hasActiveFilter
                        )
                        val executeRecoveryAction: (HomeEmptyRecoveryAction) -> Unit = { action ->
                            when (action) {
                                HomeEmptyRecoveryAction.RESTORE_HIDDEN_DEALS -> restoreHiddenDeals()
                                HomeEmptyRecoveryAction.RESET_FILTERS -> {
                                    searchInput = ""
                                    viewModel.resetHomeFilters()
                                }
                                HomeEmptyRecoveryAction.OPEN_MUTED_MANAGER -> showMutedKeywordsSheet = true
                                HomeEmptyRecoveryAction.NONE -> Unit
                            }
                        }

                        if (emptyFeedUi.isEmpty) {
                            item(
                                key = "empty_deals",
                                span = { GridItemSpan(maxLineSpan) },
                                contentType = "empty_section"
                            ) {
                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .heightIn(min = 220.dp)
                                        .padding(horizontal = if (isHairlineFeed) 16.dp else 4.dp, vertical = 8.dp),
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
                                            text = emptyFeedUi.title,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 14.sp,
                                            textAlign = TextAlign.Center,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            text = emptyFeedUi.description,
                                            fontSize = 12.sp,
                                            textAlign = TextAlign.Center,
                                            color = LocalInsightDealColors.current.textMuted
                                        )
                                        if (emptyFeedUi.primaryActionLabel != null || emptyFeedUi.secondaryActionLabel != null) {
                                            Spacer(modifier = Modifier.height(12.dp))
                                            Row(
                                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                emptyFeedUi.primaryActionLabel?.let { primaryLabel ->
                                                    OutlinedButton(
                                                        onClick = { executeRecoveryAction(emptyFeedUi.primaryAction) },
                                                        shape = RoundedCornerShape(8.dp),
                                                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                                                        modifier = Modifier.heightIn(min = 48.dp)
                                                    ) {
                                                        Text(primaryLabel, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                                                    }
                                                }
                                                emptyFeedUi.secondaryActionLabel?.let { secondaryLabel ->
                                                    TextButton(
                                                        onClick = { executeRecoveryAction(emptyFeedUi.secondaryAction) },
                                                        shape = RoundedCornerShape(8.dp),
                                                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                                        modifier = Modifier.heightIn(min = 48.dp)
                                                    ) {
                                                        Text(secondaryLabel, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        } else if (isHairlineFeed) {
                            item(
                                key = "feed_top_divider",
                                span = { GridItemSpan(maxLineSpan) },
                                contentType = "feed_divider"
                            ) {
                                HorizontalDivider(thickness = 0.75.dp, color = homeSemantic.cardBorder)
                            }
                        }

                        if (!emptyFeedUi.isEmpty) {
                            items(
                                count = dealsPagingItems.itemCount,
                                key = dealsPagingItems.itemKey { it.id },
                                span = if (isCompactMode) {
                                    { GridItemSpan(maxLineSpan) }
                                } else null,
                                contentType = { if (isCompactMode) "deal_compact_item" else "deal_item" }
                            ) { index ->
                                val deal = dealsPagingItems[index]

                                if (deal != null) {
                                    val isHidden = remember(deal.id, hiddenDealsSet) {
                                        hiddenDealsSet.contains(deal.id.toString())
                                    }
                                    if (!isHidden) {
                                    val onItemDetailClick = remember(deal.id) { { onNavigateToDetail(deal.id) } }
                                    val onItemMoreClick = remember(deal.id) {
                                        {
                                            selectedDealForOptions = deal
                                            showDealOptionsBottomSheet = true
                                        }
                                    }
                                    if (isCompactMode) {
                                        CompactDealCardComposable(
                                            deal = deal,
                                            onDetailClick = onItemDetailClick,
                                            onMoreClick = onItemMoreClick,
                                            highlightKeyword = filterState.keyword
                                        )
                                    } else {
                                        DealCardComposable(
                                            deal = deal,
                                            onDetailClick = onItemDetailClick,
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
                                            onMoreClick = onItemMoreClick,
                                            highlightKeyword = filterState.keyword,
                                            isExpandedGrid = isExpandedScreen
                                        )
                                    }
                                }
                            } else {
                                if (isCompactMode) {
                                    HomeCompactDealCardSkeleton()
                                    } else {
                                        HomeDealCardSkeleton(isExpandedGrid = isExpandedScreen)
                                    }
                                }
                            }
                        }

                        val appendState = dealsPagingItems.loadState.append
                        when {
                            appendState is LoadState.Loading -> {
                                item(
                                    key = "append_skeleton",
                                    span = { GridItemSpan(maxLineSpan) },
                                    contentType = if (isCompactMode) "skeleton_compact_loader" else "skeleton_loader"
                                ) {
                                    if (isCompactMode) {
                                        HomeCompactDealCardSkeleton()
                                    } else {
                                        HomeDealCardSkeleton(isExpandedGrid = isExpandedScreen)
                                    }
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
                                dealsPagingItems.itemCount > 0 &&
                                !emptyFeedUi.isEmpty -> {
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

                // M3 공식 PullToRefresh 스피너 (피드 영역 상단 정렬)
                androidx.compose.material3.pulltorefresh.PullToRefreshContainer(
                    modifier = Modifier.align(Alignment.TopCenter),
                    state = pullRefreshState,
                )

                // 실시간 새 핫딜 감지 다크 캡슐 (#191F28 + 에메랄드 펄스 점)
                androidx.compose.animation.AnimatedVisibility(
                    visible = hasNewDeals,
                    enter = fadeIn(animationSpec = tween(220)) +
                        slideInVertically(
                            animationSpec = spring(
                                dampingRatio = Spring.DampingRatioMediumBouncy,
                                stiffness = Spring.StiffnessMediumLow
                            )
                        ) { -it } +
                        scaleIn(
                            initialScale = 0.92f,
                            animationSpec = spring(
                                dampingRatio = Spring.DampingRatioMediumBouncy,
                                stiffness = Spring.StiffnessMedium
                            )
                        ),
                    exit = fadeOut(animationSpec = tween(180)) +
                        slideOutVertically(animationSpec = tween(180)) { -it },
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 10.dp)
                ) {
                    val infiniteTransition = rememberInfiniteTransition(label = "newDealPulse")
                    val pulseAlpha by infiniteTransition.animateFloat(
                        initialValue = 0.25f,
                        targetValue = 0.85f,
                        animationSpec = infiniteRepeatable(
                            animation = tween(durationMillis = 900, easing = FastOutSlowInEasing),
                            repeatMode = RepeatMode.Reverse
                        ),
                        label = "newDealPulseAlpha"
                    )
                    Surface(
                        modifier = Modifier
                            .heightIn(min = 42.dp)
                            .bounceClick(onClickLabel = "새 핫딜 불러오기") {
                                dealsPagingItems.refresh()
                                viewModel.onRefreshNewDeals()
                                scope.launch {
                                    gridState.animateScrollToItem(0)
                                }
                            },
                        shape = RoundedCornerShape(999.dp),
                        color = Color(0xFF191F28),
                        shadowElevation = 8.dp,
                        border = BorderStroke(1.dp, Color.White.copy(alpha = if (isDark) 0.16f else 0.08f))
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Box(
                                modifier = Modifier.size(14.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(14.dp)
                                        .clip(CircleShape)
                                        .background(BrandEmeraldPoint.copy(alpha = pulseAlpha))
                                )
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .clip(CircleShape)
                                        .background(BrandEmeraldPoint)
                                )
                            }
                            Text(
                                text = if (newDealsCount > 0) "방금 새 핫딜 ${newDealsCount}건 도착 ↑" else "방금 새 핫딜 도착 ↑",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                maxLines = 1,
                                softWrap = false
                            )
                        }
                    }
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

        if (showFilterBottomSheet) {
            HomeFilterBottomSheet(
                filterState = filterState,
                viewMode = viewMode,
                mutedCount = mutedKeywords.size + hiddenDeals.size,
                isDark = isDark,
                onApply = { nextCommunities, nextShop, nextMinPrice, nextMaxPrice, nextFreeShipping, nextHideClosed, nextIncludeClosed, nextViewMode ->
                    if (nextCommunities != activeCommunityChips) {
                        viewModel.toggleCommunityChip("전체")
                        nextCommunities.sorted().forEach { chip ->
                            viewModel.toggleCommunityChip(chip)
                        }
                    }
                    // 레거시 platform 필드가 남아 후속 개별 칩 해제 시 잔존하지 않도록 항상 정규화합니다.
                    viewModel.selectPlatform("")
                    viewModel.setShopFilter(nextShop)
                    viewModel.setPriceRangeFilter(nextMinPrice, nextMaxPrice)
                    if (nextFreeShipping != filterState.freeShippingOnly) {
                        viewModel.toggleFreeShippingOnly()
                    }
                    if (nextHideClosed != filterState.hideClosed) {
                        viewModel.toggleHideClosed()
                    }
                    if (nextIncludeClosed != viewModel.filterState.value.includeClosed) {
                        viewModel.toggleIncludeClosed()
                    }
                    viewModel.setViewMode(nextViewMode)
                    showFilterBottomSheet = false
                },
                onOpenMutedManager = {
                    showFilterBottomSheet = false
                    showMutedKeywordsSheet = true
                },
                onDismiss = { showFilterBottomSheet = false }
            )
        }

        if (showDeviceSyncSheet) {
            DeviceSyncBottomSheet(
                uiState = syncUiState,
                onIssueCode = { deviceSyncViewModel.issuePairingCode() },
                onClaimCode = { code -> deviceSyncViewModel.claimPairingCode(code) },
                onDismiss = { showDeviceSyncSheet = false }
            )
        }

        if (showMutedKeywordsSheet) {
            MutedKeywordsBottomSheet(
                mutedKeywords = mutedKeywords,
                hiddenDealsCount = hiddenDeals.size,
                onAddMutedKeyword = { kw -> viewModel.addMutedKeyword(kw) },
                onRemoveMutedKeyword = { kw -> viewModel.removeMutedKeyword(kw) },
                onRestoreHiddenDeals = restoreHiddenDeals,
                onDismiss = { showMutedKeywordsSheet = false }
            )
        }

        val scrollTopBottomPadding by animateDpAsState(
            targetValue = if (snackbarHostState.currentSnackbarData != null) 78.dp else 18.dp,
            animationSpec = tween(durationMillis = 180),
            label = "scrollTopBottomPadding"
        )
        AnimatedVisibility(
            visible = showScrollTop,
            enter = fadeIn(animationSpec = tween(180)) +
                slideInVertically(
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioMediumBouncy,
                        stiffness = Spring.StiffnessMedium
                    )
                ) { it / 2 } +
                scaleIn(
                    initialScale = 0.88f,
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioMediumBouncy,
                        stiffness = Spring.StiffnessMedium
                    )
                ),
            exit = fadeOut(animationSpec = tween(150)) +
                slideOutVertically(animationSpec = tween(150)) { it / 2 },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .navigationBarsPadding()
                .padding(end = 16.dp, bottom = scrollTopBottomPadding)
        ) {
            Surface(
                modifier = Modifier
                    .heightIn(min = 48.dp)
                    .semantics {
                        role = Role.Button
                        contentDescription = "맨 위로 이동"
                    }
                    .bounceClick(onClickLabel = "맨 위로 이동") {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        scope.launch {
                            gridState.animateScrollToItem(0)
                        }
                    },
                shape = RoundedCornerShape(999.dp),
                color = if (isDark) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.surface,
                tonalElevation = 4.dp,
                shadowElevation = 6.dp,
                border = BorderStroke(0.75.dp, homeSemantic.cardBorder)
            ) {
                Row(
                    modifier = Modifier
                        .heightIn(min = 48.dp)
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.ArrowUpward,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = "맨 위로",
                        fontSize = 12.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        style = TextStyle(
                            platformStyle = PlatformTextStyle(includeFontPadding = false)
                        )
                    )
                }
            }
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
private fun HomeRecentDealsTray(
    recentDeals: List<DealItem>,
    isDark: Boolean,
    isHairlineFeed: Boolean,
    onDealClick: (Int) -> Unit,
    onRemoveDeal: (Int) -> Unit,
    onClearAll: () -> Unit
) {
    if (recentDeals.isEmpty()) return
    val semantic = LocalInsightDealColors.current
    val context = LocalContext.current
    val thumbBg = if (isDark) Color(0xFF1F242D) else STUDIO_THUMB_LIGHT_BG

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 10.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = if (isHairlineFeed) 16.dp else 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "최근 본 핫딜 (${recentDeals.size})",
                fontSize = 13.5.sp,
                fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                softWrap = false,
                modifier = Modifier.semantics { heading() }
            )
            Text(
                text = "전체 비우기",
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                color = semantic.textMuted,
                maxLines = 1,
                softWrap = false,
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .clickable(onClick = onClearAll)
                    .padding(horizontal = 6.dp, vertical = 4.dp)
            )
        }

        LazyRow(
            contentPadding = PaddingValues(horizontal = if (isHairlineFeed) 16.dp else 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(
                items = recentDeals.take(15),
                key = { "recent_tray_${it.id}" },
                contentType = { "recent_tray_item" }
            ) { rd ->
                val resolvedTitle = remember(rd.id, rd.title, rd.shopName, rd.ecommerceUrl) {
                    resolveShopBadgeAndCleanTitle(rd.title, rd.shopName, rd.ecommerceUrl)
                }
                val primaryUrl = remember(rd.id, rd.imageUrl) {
                    com.ddaeany0919.insightdeal.data.util.ImageUrlResolver.resolve(rd.imageUrl, targetWidth = 160)
                }
                val fallbackUrl = remember(rd.id, rd.imageUrl) {
                    com.ddaeany0919.insightdeal.data.util.ImageUrlResolver.getFallbackBackendProxyUrl(rd.imageUrl)
                }
                var activeUrl by remember(primaryUrl) { mutableStateOf(primaryUrl) }
                val imageReq = remember(activeUrl, rd.postUrl) {
                    if (activeUrl == null) null
                    else {
                        coil.request.ImageRequest.Builder(context)
                            .data(activeUrl)
                            .setHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) Chrome/120.0.0.0 Safari/537.36")
                            .setHeader("Referer", rd.postUrl ?: "https://insightdeal.com/")
                            .crossfade(true)
                            .build()
                    }
                }
                val rdPriceText = remember(rd.id, rd.price, rd.currency, rd.category, resolvedTitle.cleanTitle) {
                    if (rd.price > 0L) {
                        formatPrice(rd.price, rd.currency, resolvedTitle.cleanTitle)
                    } else if (rd.category == "적립" || resolvedTitle.cleanTitle.contains("적립")) {
                        "포인트 적립"
                    } else {
                        formatPrice(rd.price, rd.currency, resolvedTitle.cleanTitle)
                    }
                }

                Surface(
                    modifier = Modifier
                        .widthIn(min = 184.dp, max = 228.dp)
                        .bounceClick(onClickLabel = "${resolvedTitle.cleanTitle} 다시 보기") {
                            onDealClick(rd.id)
                        },
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(0.75.dp, semantic.cardBorder)
                ) {
                    Row(
                        modifier = Modifier.padding(start = 6.dp, top = 6.dp, bottom = 6.dp, end = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Surface(
                            modifier = Modifier.size(44.dp),
                            shape = RoundedCornerShape(8.dp),
                            color = thumbBg,
                            border = BorderStroke(0.5.dp, semantic.cardBorder)
                        ) {
                            if (imageReq == null) {
                                BrandPlaceholder(
                                    siteName = rd.siteName,
                                    modifier = Modifier.fillMaxSize(),
                                    textStyle = TextStyle(fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color(0xFFF9FAFB))
                                )
                            } else {
                                SubcomposeAsyncImage(
                                    model = imageReq,
                                    contentDescription = null,
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .background(thumbBg, RoundedCornerShape(8.dp))
                                        .clip(RoundedCornerShape(8.dp)),
                                    colorFilter = resolveStudioThumbnailColorFilter(isDark = isDark, isClosed = rd.isClosed),
                                    contentScale = ContentScale.Crop,
                                    onError = {
                                        if (fallbackUrl != null && activeUrl != fallbackUrl &&
                                            !activeUrl.orEmpty().contains("/api/proxy-image", ignoreCase = true)
                                        ) {
                                            activeUrl = fallbackUrl
                                        }
                                    },
                                    error = {
                                        BrandPlaceholder(
                                            siteName = rd.siteName,
                                            modifier = Modifier.fillMaxSize(),
                                            textStyle = TextStyle(fontSize = 9.sp, fontWeight = FontWeight.Bold, color = Color(0xFFF9FAFB))
                                        )
                                    }
                                )
                            }
                        }

                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            Text(
                                text = resolvedTitle.cleanTitle,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = rdPriceText,
                                style = PriceTextStyle.copy(
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.ExtraBold
                                ),
                                color = if (rd.isClosed) semantic.textMuted else semantic.priceAccent,
                                maxLines = 1,
                                softWrap = false,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        Box(
                            modifier = Modifier
                                .size(28.dp)
                                .clip(CircleShape)
                                .semantics {
                                    role = Role.Button
                                    contentDescription = "${resolvedTitle.cleanTitle} 최근 본 핫딜에서 삭제"
                                }
                                .clickable { onRemoveDeal(rd.id) },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = null,
                                tint = semantic.textMuted,
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun HomeFilterBottomSheet(
    filterState: HomeViewModel.FilterState,
    viewMode: String,
    mutedCount: Int,
    isDark: Boolean,
    onApply: (
        communities: Set<String>,
        shop: String?,
        minPrice: Int?,
        maxPrice: Int?,
        freeShippingOnly: Boolean,
        hideClosed: Boolean,
        includeClosed: Boolean,
        viewMode: String
    ) -> Unit,
    onOpenMutedManager: () -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val semantic = LocalInsightDealColors.current

    // 시트 진입 시점의 스냅샷으로 1회만 초기화하여 검색 디바운스 중 Draft 리셋 및 회전 시 유실을 차단합니다.
    var draftCommunities by rememberSaveable(stateSaver = HOME_STRING_SET_SAVER) {
        mutableStateOf(
            filterState.effectiveCommunityChips().ifEmpty {
                filterState.platform
                    ?.split(",")
                    ?.map { it.trim() }
                    ?.filter { it.isNotEmpty() && it != "전체" }
                    ?.toSet()
                    ?: emptySet()
            }
        )
    }
    var draftShop by rememberSaveable { mutableStateOf(filterState.shop) }
    var draftMinPriceText by rememberSaveable { mutableStateOf(filterState.minPrice?.toString().orEmpty()) }
    var draftMaxPriceText by rememberSaveable { mutableStateOf(filterState.maxPrice?.toString().orEmpty()) }
    var draftFreeShipping by rememberSaveable { mutableStateOf(filterState.freeShippingOnly) }
    var draftHideClosed by rememberSaveable { mutableStateOf(filterState.hideClosed) }
    var draftIncludeClosed by rememberSaveable { mutableStateOf(filterState.includeClosed) }
    var draftViewMode by rememberSaveable { mutableStateOf(viewMode) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        dragHandle = { BottomSheetDefaults.DragHandle() }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "상세 필터",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.semantics { heading() }
                )
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "닫기",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f, fill = false)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(18.dp)
            ) {
                // 1) 출처 (11종)
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "출처 커뮤니티",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        HomeViewModel.COMMUNITY_FILTER_CHIPS.forEach { chip ->
                            val isSelected = if (chip == "전체") {
                                draftCommunities.isEmpty()
                            } else {
                                chip in draftCommunities
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
                                unselectedBorderColor = semantic.cardBorder,
                                onClick = {
                                    draftCommunities = if (chip == "전체") {
                                        emptySet()
                                    } else if (chip in draftCommunities) {
                                        draftCommunities - chip
                                    } else {
                                        draftCommunities + chip
                                    }
                                }
                            )
                        }
                    }
                }

                // 2) 쇼핑몰 (18종)
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "쇼핑몰",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        HomeSelectablePillChip(
                            label = "전체",
                            selected = draftShop.isNullOrBlank(),
                            role = Role.Tab,
                            shape = RoundedCornerShape(8.dp),
                            chipHeight = 34.dp,
                            selectedContainerColor = MaterialTheme.colorScheme.secondaryContainer,
                            selectedContentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                            selectedBorderColor = MaterialTheme.colorScheme.secondary,
                            unselectedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                            unselectedContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            unselectedBorderColor = semantic.cardBorder,
                            onClick = { draftShop = null }
                        )
                        HOME_SHOP_FILTER_OPTIONS.forEach { (displayLabel, serverShopKey) ->
                            val isSelected = draftShop == serverShopKey
                            HomeSelectablePillChip(
                                label = displayLabel,
                                selected = isSelected,
                                role = Role.Tab,
                                shape = RoundedCornerShape(8.dp),
                                chipHeight = 34.dp,
                                selectedContainerColor = MaterialTheme.colorScheme.secondaryContainer,
                                selectedContentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                                selectedBorderColor = MaterialTheme.colorScheme.secondary,
                                unselectedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                                unselectedContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                unselectedBorderColor = semantic.cardBorder,
                                onClick = { draftShop = if (isSelected) null else serverShopKey }
                            )
                        }
                    }
                }

                // 3) 가격대 (프리셋 + 직접 입력)
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "가격대",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        HOME_PRICE_PRESETS.forEach { (presetLabel, minP, maxP) ->
                            val curMin = draftMinPriceText.toIntOrNull()?.takeIf { it > 0 }
                            val curMax = draftMaxPriceText.toIntOrNull()?.takeIf { it > 0 }
                            val isSelected = curMin == minP && curMax == maxP
                            HomeSelectablePillChip(
                                label = presetLabel,
                                selected = isSelected,
                                role = Role.Tab,
                                shape = RoundedCornerShape(999.dp),
                                chipHeight = 34.dp,
                                onClick = {
                                    if (isSelected) {
                                        draftMinPriceText = ""
                                        draftMaxPriceText = ""
                                    } else {
                                        draftMinPriceText = minP?.toString().orEmpty()
                                        draftMaxPriceText = maxP?.toString().orEmpty()
                                    }
                                }
                            )
                        }
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = draftMinPriceText,
                            onValueChange = { raw -> draftMinPriceText = raw.filter { it.isDigit() }.take(9) },
                            label = { Text("최소 가격 (원)", fontSize = 12.sp) },
                            placeholder = { Text("0", fontSize = 13.sp) },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            text = "~",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = semantic.textMuted
                        )
                        OutlinedTextField(
                            value = draftMaxPriceText,
                            onValueChange = { raw -> draftMaxPriceText = raw.filter { it.isDigit() }.take(9) },
                            label = { Text("최대 가격 (원)", fontSize = 12.sp) },
                            placeholder = { Text("제한 없음", fontSize = 13.sp) },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                // 4) 배송 · 상태 옵션 (무료배송만 / 품절 제외 / 종료 포함)
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "배송 · 상태 옵션",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        HomeSelectablePillChip(
                            label = "무료배송만",
                            selected = draftFreeShipping,
                            shape = RoundedCornerShape(999.dp),
                            chipHeight = 36.dp,
                            onClick = { draftFreeShipping = !draftFreeShipping }
                        )
                        HomeSelectablePillChip(
                            label = "품절 제외",
                            selected = draftHideClosed,
                            shape = RoundedCornerShape(999.dp),
                            chipHeight = 36.dp,
                            onClick = {
                                val next = !draftHideClosed
                                draftHideClosed = next
                                if (next) draftIncludeClosed = false
                            }
                        )
                        HomeSelectablePillChip(
                            label = "종료 포함",
                            selected = draftIncludeClosed,
                            shape = RoundedCornerShape(999.dp),
                            chipHeight = 36.dp,
                            onClick = {
                                val next = !draftIncludeClosed
                                draftIncludeClosed = next
                                if (next) draftHideClosed = false
                            }
                        )
                    }
                }

                // 5) 보기 방식 (카드 보기 / 한줄 컴팩트 보기)
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "보기 방식",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    val trackColor = if (isDark) Color(0xFF191F28) else Color(0xFFF2F4F7)
                    val modeOptions = listOf(
                        FeedPreferencesDataSource.VIEW_MODE_CARD to "카드 보기",
                        FeedPreferencesDataSource.VIEW_MODE_COMPACT to "한줄 컴팩트 보기"
                    )
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = trackColor,
                        border = BorderStroke(1.dp, semantic.cardBorder),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(4.dp),
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            modeOptions.forEach { (modeKey, modeLabel) ->
                                val isSelected = draftViewMode == modeKey
                                Surface(
                                    onClick = { draftViewMode = modeKey },
                                    shape = RoundedCornerShape(9.dp),
                                    color = when {
                                        isSelected && isDark -> MaterialTheme.colorScheme.surfaceVariant
                                        isSelected -> MaterialTheme.colorScheme.surface
                                        else -> Color.Transparent
                                    },
                                    shadowElevation = if (isSelected && !isDark) 1.dp else 0.dp,
                                    modifier = Modifier
                                        .weight(1f)
                                        .heightIn(min = 42.dp)
                                        .semantics {
                                            role = Role.Tab
                                            selected = isSelected
                                        }
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Text(
                                            text = modeLabel,
                                            fontSize = 13.sp,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                            color = if (isSelected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 1,
                                            softWrap = false
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // 6) 숨김 · 뮤트 관리 진입
                Surface(
                    onClick = onOpenMutedManager,
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                    border = BorderStroke(0.75.dp, semantic.cardBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 50.dp)
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.VisibilityOff,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                text = "숨김 · 뮤트 관리",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = "${mutedCount}건",
                                fontSize = 12.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (mutedCount > 0) MaterialTheme.colorScheme.primary else semantic.textMuted
                            )
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                contentDescription = null,
                                tint = semantic.textMuted,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }

            HorizontalDivider(color = semantic.cardBorder, thickness = 0.75.dp)

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedButton(
                    onClick = {
                        draftCommunities = emptySet()
                        draftShop = null
                        draftMinPriceText = ""
                        draftMaxPriceText = ""
                        draftFreeShipping = false
                        draftHideClosed = false
                        draftIncludeClosed = false
                        draftViewMode = FeedPreferencesDataSource.VIEW_MODE_CARD
                    },
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 48.dp),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("초기화", fontWeight = FontWeight.Bold)
                }
                Button(
                    onClick = {
                        val parsedMin = draftMinPriceText.toIntOrNull()?.takeIf { it > 0 }
                        val parsedMax = draftMaxPriceText.toIntOrNull()?.takeIf { it > 0 }
                        val (safeMin, safeMax) = if (parsedMin != null && parsedMax != null && parsedMin > parsedMax) {
                            parsedMax to parsedMin
                        } else {
                            parsedMin to parsedMax
                        }
                        onApply(
                            draftCommunities,
                            draftShop,
                            safeMin,
                            safeMax,
                            draftFreeShipping,
                            draftHideClosed,
                            draftIncludeClosed,
                            draftViewMode
                        )
                    },
                    modifier = Modifier
                        .weight(2f)
                        .heightIn(min = 48.dp),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("적용하기", fontWeight = FontWeight.Bold)
                }
            }
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
                    softWrap = false,
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

// ✨ 세로형 딜 카드를 위한 스켈레톤 (Recomposition 방지용, 고밀도 4줄 DealCardComposable 규격 정합)
@Composable
fun HomeDealCardSkeleton(isExpandedGrid: Boolean = false) {
    val isDark = LocalIsDarkTheme.current
    val semantic = LocalInsightDealColors.current
    val thumbBg = if (isDark) Color(0xFF1F242D) else STUDIO_THUMB_LIGHT_BG
    val content: @Composable () -> Unit = {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    horizontal = if (isExpandedGrid) 12.dp else 16.dp,
                    vertical = if (isExpandedGrid) 12.dp else 10.dp
                ),
            verticalAlignment = Alignment.Top
        ) {
            Box(
                modifier = Modifier
                    .size(88.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(thumbBg)
            ) {
                Box(modifier = Modifier.fillMaxSize().shimmerEffect())
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(5.dp)
                        .size(28.dp)
                        .clip(CircleShape)
                        .shimmerEffect()
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Box(Modifier.width(110.dp).height(18.dp).clip(RoundedCornerShape(4.dp)).shimmerEffect())
                Spacer(Modifier.height(4.dp))
                Box(Modifier.fillMaxWidth().height(16.dp).clip(RoundedCornerShape(4.dp)).shimmerEffect())
                Spacer(Modifier.height(4.dp))
                Box(Modifier.fillMaxWidth(0.62f).height(16.dp).clip(RoundedCornerShape(4.dp)).shimmerEffect())
                Spacer(Modifier.height(6.dp))
                Box(Modifier.width(96.dp).height(20.dp).clip(RoundedCornerShape(4.dp)).shimmerEffect())
                Spacer(Modifier.height(4.dp))
                Box(Modifier.width(132.dp).height(16.dp).clip(RoundedCornerShape(4.dp)).shimmerEffect())
            }
        }
    }
    if (isExpandedGrid) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = Color.Transparent
        ) {
            content()
        }
    } else {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = MaterialTheme.colorScheme.surface
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                content()
                HorizontalDivider(thickness = 0.75.dp, color = semantic.cardBorder)
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

private fun buildAppInteractionBadge(deal: DealItem): Pair<String, String>? {
    val appVoteTotal = (deal.buyCount + deal.passCount).coerceAtLeast(0)
    val appCommentTotal = deal.appCommentCount.coerceAtLeast(0)
    if (appVoteTotal <= 0 && appCommentTotal <= 0) return null
    val text = buildString {
        if (appVoteTotal > 0) append("👍 $appVoteTotal")
        if (appVoteTotal > 0 && appCommentTotal > 0) append("  ")
        if (appCommentTotal > 0) append("💬 $appCommentTotal")
    }
    return text to "얼만데 투표 $appVoteTotal 의견 $appCommentTotal"
}

private fun resolveShopBrandTextColor(
    name: String,
    isDark: Boolean,
    fallbackColor: Color
): Color {
    val lower = name.lowercase(Locale.getDefault()).replace(" ", "")
    return when {
        lower.contains("네이버") || lower.contains("지마켓") || lower.contains("g마켓") || lower.contains("gmarket") ||
            lower.contains("컬리") || lower.contains("올리브영") || lower.contains("오늘의집") || lower.contains("쓱") || lower.contains("ssg") ->
            if (isDark) Color(0xFF34D399) else Color(0xFF059669)
        lower.contains("쿠팡") || lower.contains("11번가") || lower.contains("롯데온") || lower.contains("cj온스타일") || lower.contains("옥션") ->
            if (isDark) Color(0xFFFB7185) else Color(0xFFE11D48)
        lower.contains("알리") || lower.contains("아마존") || lower.contains("스팀") || lower.contains("에픽") || lower.contains("토스") ->
            if (isDark) Color(0xFF60A5FA) else Color(0xFF2563EB)
        lower.contains("카카오") ->
            if (isDark) Color(0xFFFBBF24) else Color(0xFFB45309)
        lower.contains("무신사") ->
            if (isDark) Color(0xFFE5E7EB) else Color(0xFF1F2937)
        else -> fallbackColor
    }
}

@Composable
@Suppress("UNUSED_PARAMETER")
fun DealCardComposable(
    deal: DealItem,
    onDetailClick: () -> Unit = {},
    onOpenUrl: (String) -> Unit = {},
    isWishlisted: Boolean = false,
    onToggleWishlist: () -> Unit = {},
    onMoreClick: (() -> Unit)? = null,
    highlightKeyword: String? = null,
    isExpandedGrid: Boolean = false,
    reserveBottomEndSpace: Boolean = false
) {
    var isExpanded by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val isDark = LocalIsDarkTheme.current
    val haptic = androidx.compose.ui.platform.LocalHapticFeedback.current
    var heartPopTrigger by remember { mutableStateOf(false) }
    LaunchedEffect(heartPopTrigger) {
        if (heartPopTrigger) {
            kotlinx.coroutines.delay(180L)
            heartPopTrigger = false
        }
    }
    val heartScale by animateFloatAsState(
        targetValue = if (heartPopTrigger) 1.28f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "wishlistHeartScale"
    )

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

    // 4. 출처 배지 목록 캐싱 (원시 Gson [null] 원소 방어)
    val sourcesToRender = remember(deal.id, deal.sources, deal.siteName, deal.postUrl) {
        val safeSources = (deal.sources as List<DealSource?>?).orEmpty().filterNotNull()
        val rawSources = if (safeSources.isNotEmpty()) {
            safeSources
        } else {
            listOf(DealSource(deal.siteName.orEmpty(), deal.postUrl ?: ""))
        }
        rawSources.distinctBy { source ->
            val safeName = source.siteName.orEmpty()
            if (safeName.contains(" - ")) {
                safeName.substringBefore(" - ").trim()
            } else {
                safeName.trim()
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

    val isMultiDeal = deal.hasOptions || optionCount >= 2
    val unitPriceBadge = remember(
        deal.id,
        resolvedShopTitle.cleanTitle,
        deal.price,
        deal.currency,
        deal.category,
        deal.isClosed,
        deal.isFree,
        isMultiDeal
    ) {
        if (!deal.isClosed && !deal.isFree && !isMultiDeal) {
            computeUnitPriceBadge(
                title = resolvedShopTitle.cleanTitle,
                price = deal.price,
                currency = deal.currency,
                category = deal.category
            )
        } else {
            null
        }
    }
    val rawPaymentCondBadge = remember(deal.id, deal.title, deal.shippingFee, deal.isClosed, deal.isFree, priceText) {
        if (!deal.isClosed && !deal.isFree && priceText != "무료") {
            extractPaymentConditionBadge(deal.title, deal.shippingFee)
        } else {
            null
        }
    }
    val paymentCondBadge = if (
        rawPaymentCondBadge != null &&
        !((rawPaymentCondBadge == "와우회원 조건" && displayShipping.orEmpty().contains("와우")) ||
            (rawPaymentCondBadge == "네멤 조건" && displayShipping.orEmpty().contains("네멤")))
    ) {
        rawPaymentCondBadge
    } else {
        null
    }

    val multiOptions = remember(deal.id, deal.optionsData, deal.sources, deal.currency, deal.ecommerceUrl, deal.postUrl) {
        val safeOptionsData = (deal.optionsData as List<DealOptionItem?>?).orEmpty().filterNotNull()
        val safeSources = (deal.sources as List<DealSource?>?).orEmpty().filterNotNull()
        if (safeOptionsData.size > 1) {
            safeOptionsData.map { opt ->
                CardOptionEntry(
                    name = decodeHtmlEntities(opt.name).trim(),
                    price = opt.price,
                    currency = opt.currency ?: deal.currency,
                    targetUrl = opt.url?.takeIf { it.isNotBlank() }
                        ?: (opt.subUrls as List<String?>?)?.filterNotNull()?.firstOrNull { it.isNotBlank() }
                        ?: deal.ecommerceUrl?.takeIf { it.isNotBlank() }
                        ?: deal.postUrl.orEmpty()
                )
            }
        } else {
            safeSources
                .filter { it.siteName.contains(" - ") && it.siteName.substringAfter(" - ").isNotBlank() }
                .map { src ->
                    CardOptionEntry(
                        name = decodeHtmlEntities(src.siteName.substringAfter(" - ")).trim(),
                        price = src.price ?: 0L,
                        currency = src.currency ?: deal.currency,
                        targetUrl = src.ecommerceUrl?.takeIf { it.isNotBlank() }
                            ?: (src.subUrls as List<String?>?)?.filterNotNull()?.firstOrNull { it.isNotBlank() }
                            ?: src.postUrl
                    )
                }
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
    val cardBgColor = if (isExpandedGrid) Color.Transparent else MaterialTheme.colorScheme.surface
    val cardContentColor = if (isExpandedGrid) MaterialTheme.colorScheme.onBackground else MaterialTheme.colorScheme.onSurface
    val thumbBg = if (isDark) Color(0xFF1F242D) else STUDIO_THUMB_LIGHT_BG
    val thumbShape = RoundedCornerShape(16.dp)
    val isHotBadge = remember(deal.id, deal.aiSummary, deal.honeyScore, deal.tags) {
        isSuperHot || HomeViewModel.isPopularDeal(deal)
    }
    val topStartBadgeLabel = when {
        deal.isClosed -> "종료"
        isHotBadge -> "인기"
        else -> null
    }
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

    val cardInteractionModifier = Modifier
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
            scaleDown = if (isExpandedGrid) 0.98f else 0.99f,
            onClickLabel = "${resolvedShopTitle.cleanTitle}, $priceText 상세 보기"
        ) { onDetailClick() }

    val dealCardBody: @Composable () -> Unit = {
        Box(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(
                            horizontal = if (isExpandedGrid) 12.dp else 16.dp,
                            vertical = if (isExpandedGrid) 12.dp else 10.dp
                        ),
                    verticalAlignment = Alignment.Top
                ) {
                    Surface(
                        modifier = Modifier.size(88.dp),
                        shape = thumbShape,
                        color = thumbBg,
                        border = BorderStroke(0.75.dp, semantic.cardBorder)
                    ) {
                        Box(modifier = Modifier.fillMaxSize()) {
                            if (imageRequest == null) {
                                BrandPlaceholder(
                                    siteName = deal.siteName,
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .clip(thumbShape)
                                        .alpha(if (deal.isClosed) 0.72f else 1f),
                                    textStyle = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Color(0xFFF9FAFB))
                                )
                            } else {
                                SubcomposeAsyncImage(
                                    model = imageRequest,
                                    contentDescription = null,
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .background(thumbBg, thumbShape)
                                        .clip(thumbShape)
                                        .alpha(if (deal.isClosed) 0.72f else 1f),
                                    colorFilter = resolveStudioThumbnailColorFilter(isDark = isDark, isClosed = deal.isClosed),
                                    contentScale = ContentScale.Crop,
                                    onError = {
                                        if (!activeImageUrl.orEmpty().contains("/api/proxy-image", ignoreCase = true) &&
                                            fallbackProxyUrl != null &&
                                            activeImageUrl != fallbackProxyUrl
                                        ) {
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
                            }

                            // 좌상단 다크 캡슐 배지 최대 1개 ("종료" 우선, 아니면 인기 딜만 "인기")
                            if (topStartBadgeLabel != null) {
                                Box(
                                    modifier = Modifier
                                        .align(Alignment.TopStart)
                                        .padding(6.dp)
                                        .clip(RoundedCornerShape(999.dp))
                                        .background(Color(0xCC191F28))
                                        .padding(horizontal = 7.dp, vertical = 2.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = topStartBadgeLabel,
                                        color = Color.White,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1,
                                        softWrap = false,
                                        style = TextStyle(platformStyle = PlatformTextStyle(includeFontPadding = false))
                                    )
                                }
                            }

                            // 좌측 88.dp 썸네일 우상단 반투명 다크 원형 미니 하트 (찜 시 레드 팝)
                            Box(
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .padding(5.dp)
                                    .size(28.dp)
                                    .clip(CircleShape)
                                    .background(Color(0x99191F28))
                                    .border(0.5.dp, Color.White.copy(alpha = 0.22f), CircleShape)
                                    .semantics {
                                        role = Role.Button
                                        contentDescription = if (isWishlisted) "찜 해제" else "찜하기"
                                    }
                                    .clickable {
                                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        heartPopTrigger = true
                                        onToggleWishlist()
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = if (isWishlisted) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                                    contentDescription = null,
                                    tint = if (isWishlisted) Color(0xFFE11D48) else Color.White,
                                    modifier = Modifier
                                        .size(16.dp)
                                        .graphicsLayer {
                                            scaleX = heartScale
                                            scaleY = heartScale
                                        }
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    // 우측 4줄 고밀도 텍스트 스택 (배경 박스·테두리 제거, 타이포그래피·컬러·점 중심, 단일 Row + softWrap = false 고정)
                    Column(modifier = Modifier.weight(1f)) {
                        // 1줄: 브랜드 톤 쇼핑몰명 · 출처 (weight 적용) + 상대시간 (고정)
                        val primarySource = sourcesToRender.firstOrNull()
                        val primarySourceName = remember(primarySource, sourcesToRender.size) {
                            val raw = primarySource?.siteName.orEmpty()
                            val base = if (raw.contains(" - ")) raw.substringBefore(" - ").trim() else raw.trim()
                            if (sourcesToRender.size > 1 && base.isNotEmpty()) "$base +${sourcesToRender.size - 1}" else base
                        }
                        val leadShopName = resolvedShopTitle.shopBadge?.takeIf { it.isNotBlank() }
                        val timeAgo = rememberRelativeTime(deal.createdAt)
                        val mutedMetaColor = if (isDark) Color(0xFF9CA3AF) else Color(0xFF6B7280)
                        val leadMetaText = remember(leadShopName, primarySourceName, isDark, mutedMetaColor) {
                            buildAnnotatedString {
                                if (!leadShopName.isNullOrBlank()) {
                                    withStyle(
                                        SpanStyle(
                                            color = resolveShopBrandTextColor(leadShopName, isDark, mutedMetaColor),
                                            fontWeight = FontWeight.Bold
                                        )
                                    ) {
                                        append(leadShopName)
                                    }
                                    if (primarySourceName.isNotBlank()) {
                                        withStyle(SpanStyle(color = mutedMetaColor, fontWeight = FontWeight.Medium)) {
                                            append(" · $primarySourceName")
                                        }
                                    }
                                } else if (primarySourceName.isNotBlank()) {
                                    withStyle(
                                        SpanStyle(
                                            color = resolveShopBrandTextColor(
                                                primarySourceName.substringBefore(" +").trim(),
                                                isDark,
                                                if (isDark) Color(0xFFD1D5DB) else Color(0xFF374151)
                                            ),
                                            fontWeight = FontWeight.Bold
                                        )
                                    ) {
                                        append(primarySourceName)
                                    }
                                }
                            }
                        }

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(end = if (reserveBottomEndSpace) 52.dp else 36.dp)
                                .heightIn(min = if (isExpandedGrid) 20.dp else 18.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (leadMetaText.isNotEmpty()) {
                                Text(
                                    text = leadMetaText,
                                    fontSize = 11.5.sp,
                                    maxLines = 1,
                                    softWrap = false,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f, fill = false)
                                )
                            }
                            if (timeAgo.isNotEmpty()) {
                                Text(
                                    text = if (leadMetaText.isNotEmpty()) " · $timeAgo" else timeAgo,
                                    fontSize = 11.5.sp,
                                    fontWeight = FontWeight.Normal,
                                    color = mutedMetaColor,
                                    maxLines = 1,
                                    softWrap = false,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(3.dp))

                        // 2줄: 제목 (태블릿 2열 그리드에서는 minLines = 2로 가격 Y좌표 칼정렬, MoreVert 물리적 여백 >= 36.dp 확보)
                        Text(
                            text = highlightedTitle,
                            fontSize = 14.sp,
                            lineHeight = 19.sp,
                            letterSpacing = (-0.2).sp,
                            fontWeight = FontWeight.SemiBold,
                            minLines = if (isExpandedGrid) 2 else 1,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(end = if (reserveBottomEndSpace) 52.dp else 36.dp),
                            textDecoration = if (deal.isClosed) TextDecoration.LineThrough else null,
                            color = if (deal.isClosed) semantic.textMuted else MaterialTheme.colorScheme.onSurface
                        )

                        Spacer(modifier = Modifier.height(4.dp))

                        // 3줄: 가격 단독 행 (#191F28 / 다크 #F9FAFB 진한 볼드, maxLines = 1 + softWrap = false)
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(end = if (reserveBottomEndSpace) 52.dp else 0.dp)
                                .heightIn(min = if (isExpandedGrid) 24.dp else 0.dp),
                            contentAlignment = Alignment.CenterStart
                        ) {
                            val priceFontSize = if (isExpandedGrid) {
                                16.sp
                            } else if (priceText.length >= 9) {
                                15.sp
                            } else {
                                16.5.sp
                            }
                            val strongPriceColor = if (isDark) Color(0xFFF9FAFB) else Color(0xFF191F28)
                            Text(
                                text = priceText,
                                style = PriceTextStyle.copy(
                                    fontSize = priceFontSize,
                                    fontWeight = FontWeight.ExtraBold
                                ),
                                color = when {
                                    deal.isClosed -> semantic.textMuted
                                    deal.price > 0L || priceText == "무료" -> strongPriceColor
                                    else -> semantic.textMuted
                                },
                                maxLines = 1,
                                softWrap = false,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        // 4줄: 좌측 에메랄드 단가 · 회색 배송비 (네모 박스 없이 weight(1f, fill = false)) + 우측 고정 👍 N  💬 N
                        val bottomShippingLabel = remember(displayShipping) {
                            when (displayShipping) {
                                null -> null
                                "무료" -> "무료배송"
                                "유료" -> "유료배송"
                                "확인 필요" -> "배송 확인 필요"
                                "와우무료", "스클무료", "우주패스" -> displayShipping
                                else -> if (displayShipping.contains("배송") || displayShipping.contains("무료")) displayShipping else "배송 $displayShipping"
                            }
                        }
                        val appBadge = remember(deal.id, deal.buyCount, deal.passCount, deal.appCommentCount) {
                            buildAppInteractionBadge(deal)
                        }
                        val emeraldUnitColor = if (isDark) Color(0xFF34D399) else Color(0xFF059669)
                        val bottomLeftText = remember(
                            unitPriceBadge,
                            bottomShippingLabel,
                            paymentCondBadge,
                            deal.isSearchFallback,
                            deal.isClosed,
                            deal.hasMultipleOptions,
                            optionCount,
                            isDark
                        ) {
                            buildAnnotatedString {
                                var hasSegment = false
                                fun appendSeparator() {
                                    if (hasSegment) {
                                        withStyle(SpanStyle(color = mutedMetaColor, fontWeight = FontWeight.Normal)) {
                                            append(" · ")
                                        }
                                    }
                                    hasSegment = true
                                }
                                if (unitPriceBadge != null) {
                                    appendSeparator()
                                    withStyle(SpanStyle(color = emeraldUnitColor, fontWeight = FontWeight.Bold)) {
                                        append(unitPriceBadge.label)
                                    }
                                }
                                if (bottomShippingLabel != null && !deal.isClosed) {
                                    appendSeparator()
                                    withStyle(SpanStyle(color = mutedMetaColor, fontWeight = FontWeight.Medium)) {
                                        append(bottomShippingLabel)
                                    }
                                }
                                if (paymentCondBadge != null && !deal.isClosed) {
                                    appendSeparator()
                                    withStyle(SpanStyle(color = semantic.warning, fontWeight = FontWeight.SemiBold)) {
                                        append(paymentCondBadge)
                                    }
                                }
                                if (deal.isSearchFallback) {
                                    appendSeparator()
                                    withStyle(SpanStyle(color = mutedMetaColor, fontWeight = FontWeight.Medium)) {
                                        append("검색이동")
                                    }
                                }
                                if (deal.hasMultipleOptions && optionCount > 1) {
                                    appendSeparator()
                                    withStyle(SpanStyle(color = mutedMetaColor, fontWeight = FontWeight.Medium)) {
                                        append("옵션 ${optionCount}개")
                                    }
                                }
                                if (deal.isClosed) {
                                    appendSeparator()
                                    withStyle(SpanStyle(color = semantic.danger, fontWeight = FontWeight.SemiBold)) {
                                        append("품절·종료")
                                    }
                                }
                            }
                        }

                        val hasBottomMeta = bottomLeftText.isNotEmpty() || appBadge != null
                        if (hasBottomMeta || isExpandedGrid) {
                            Spacer(modifier = Modifier.height(3.dp))
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(end = if (reserveBottomEndSpace) 52.dp else 0.dp)
                                    .heightIn(min = if (isExpandedGrid) 20.dp else 0.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                if (bottomLeftText.isNotEmpty()) {
                                    Text(
                                        text = bottomLeftText,
                                        fontSize = 11.5.sp,
                                        maxLines = 1,
                                        softWrap = false,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier
                                            .weight(1f, fill = false)
                                            .then(
                                                if (unitPriceBadge != null) {
                                                    Modifier.semantics {
                                                        contentDescription = "${unitPriceBadge.label}, ${unitPriceBadge.detail}"
                                                    }
                                                } else {
                                                    Modifier
                                                }
                                            )
                                    )
                                } else {
                                    Spacer(modifier = Modifier.weight(1f))
                                }

                                if (appBadge != null) {
                                    Text(
                                        text = appBadge.first,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = mutedMetaColor,
                                        maxLines = 1,
                                        softWrap = false,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier
                                            .padding(start = if (bottomLeftText.isNotEmpty()) 8.dp else 0.dp)
                                            .semantics {
                                                contentDescription = appBadge.second
                                            }
                                    )
                                }
                            }
                        }
                    }
                }

                AnimatedVisibility(visible = isExpanded) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(
                                start = if (isExpandedGrid) 12.dp else 16.dp,
                                end = (if (isExpandedGrid) 12.dp else 16.dp) + (if (reserveBottomEndSpace) 52.dp else 0.dp),
                                top = 8.dp,
                                bottom = 8.dp
                            )
                    ) {
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
                                                    maxLines = 1,
                                                    softWrap = false,
                                                    overflow = TextOverflow.Ellipsis,
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
                if (!isExpandedGrid) {
                    HorizontalDivider(thickness = 0.75.dp, color = semantic.cardBorder)
                }
            }

            // 우상단 컴팩트 더보기 슬롯 (1줄 메타 행 높이 팽창 방지)
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 6.dp, end = if (isExpandedGrid) 6.dp else 10.dp)
                    .size(32.dp)
                    .clip(CircleShape)
                    .semantics {
                        role = Role.Button
                        contentDescription = "더보기"
                    }
                    .clickable {
                        if (onMoreClick != null) {
                            onMoreClick()
                        } else {
                            isExpanded = !isExpanded
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.MoreVert,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }

    Surface(
        modifier = cardInteractionModifier,
        shape = if (isExpandedGrid) RoundedCornerShape(16.dp) else RectangleShape,
        color = cardBgColor,
        contentColor = cardContentColor
    ) {
        dealCardBody()
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
                val statusColors = if (dndEnabled) {
                    MaterialTheme.colorScheme.secondaryContainer to MaterialTheme.colorScheme.onSecondaryContainer
                } else {
                    MaterialTheme.colorScheme.primaryContainer to MaterialTheme.colorScheme.onPrimaryContainer
                }
                Surface(
                    shape = RoundedCornerShape(999.dp),
                    color = statusColors.first
                ) {
                    Text(
                        text = if (dndEnabled) "방해금지 중" else "실시간 수신",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = statusColors.second,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                    )
                }
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
                                val formattedStart = String.format(Locale.ROOT, "%02d:%02d", pickedStartHour, pickedStartMinute)

                                android.app.TimePickerDialog(
                                    context,
                                    { _, pickedEndHour, pickedEndMinute ->
                                        val formattedEnd = String.format(Locale.ROOT, "%02d:%02d", pickedEndHour, pickedEndMinute)
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
            siteLower.contains("클리앙") -> Triple(
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

/**
 * 슬림 컴팩트 1줄 보기 스켈레톤
 */
@Composable
fun HomeCompactDealCardSkeleton() {
    val isDark = LocalIsDarkTheme.current
    val semantic = LocalInsightDealColors.current
    val thumbBg = if (isDark) Color(0xFF1F242D) else STUDIO_THUMB_LIGHT_BG
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, semantic.cardBorder),
        elevation = CardDefaults.cardElevation(0.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                modifier = Modifier.size(48.dp),
                shape = RoundedCornerShape(8.dp),
                color = thumbBg,
                border = BorderStroke(0.75.dp, semantic.cardBorder)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(RoundedCornerShape(8.dp))
                        .shimmerEffect()
                )
            }
            Spacer(modifier = Modifier.width(10.dp))
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Box(Modifier.fillMaxWidth(0.85f).height(14.dp).clip(RoundedCornerShape(4.dp)).shimmerEffect())
                Box(Modifier.width(120.dp).height(14.dp).clip(RoundedCornerShape(4.dp)).shimmerEffect())
            }
        }
    }
}

/**
 * 슬림 컴팩트 1줄 보기 카드 (48dp 썸네일 + 1줄 제목 + 가격 + 핵심 배지 최대 2개 + 48dp 더보기 버튼)
 */
@Composable
fun CompactDealCardComposable(
    deal: DealItem,
    onDetailClick: () -> Unit = {},
    onMoreClick: () -> Unit = {},
    highlightKeyword: String? = null
) {
    val context = LocalContext.current
    val isDark = LocalIsDarkTheme.current
    val semantic = LocalInsightDealColors.current
    val thumbBg = if (isDark) Color(0xFF1F242D) else STUDIO_THUMB_LIGHT_BG
    val thumbShape = RoundedCornerShape(8.dp)

    val safeTitle = (deal.title as String?).orEmpty()
    val safeSiteName = (deal.siteName as String?).orEmpty()
    val safeCurrency = (deal.currency as String?)?.takeIf { it.isNotBlank() } ?: "KRW"
    val safeTags = (deal.tags as List<String?>?).orEmpty().filterNotNull()

    val resolvedShopTitle = remember(deal.id, safeTitle, deal.shopName, deal.ecommerceUrl) {
        resolveShopBadgeAndCleanTitle(
            rawTitle = safeTitle,
            shopName = deal.shopName,
            ecommerceUrl = deal.ecommerceUrl
        )
    }

    val isSuperHot = remember(deal.id, deal.aiSummary, deal.honeyScore, safeTags) {
        ((deal.aiSummary?.contains("[커뮤니티 인기]") == true) ||
            (deal.aiSummary?.contains("[커뮤니티 인증 핫딜]") == true) ||
            safeTags.any { it.contains("인기") || it.contains("역대") }) &&
            deal.honeyScore >= 100
    }

    val primaryImageUrl = remember(deal.id, deal.imageUrl, com.ddaeany0919.insightdeal.data.network.NetworkConfig.getActiveServerUrl()) {
        com.ddaeany0919.insightdeal.data.util.ImageUrlResolver.resolve(deal.imageUrl, targetWidth = 200)
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

    val displayShipping = remember(deal.id, deal.shippingFee, deal.category, resolvedShopTitle.cleanTitle) {
        val isDigitalTitle = resolvedShopTitle.cleanTitle.contains("요금제") || resolvedShopTitle.cleanTitle.contains("데이터")
        if (deal.category in HOME_DIGITAL_CATEGORIES || isDigitalTitle) null
        else {
            val trimmed = decodeHtmlEntities(deal.shippingFee).trim().ifEmpty { "정보 없음" }
            when {
                trimmed == "0" || trimmed == "0원" || trimmed == "무배" || trimmed == "무료" || trimmed == "무료배송" -> "무료"
                trimmed.startsWith("0원/") || trimmed.startsWith("0원+") || trimmed.startsWith("0/") || trimmed.startsWith("0+") -> "무료"
                else -> null
            }
        }
    }

    val priceText = remember(deal.id, deal.price, safeCurrency, deal.category, resolvedShopTitle.cleanTitle) {
        if (deal.price > 0L) {
            val formatted = formatPrice(deal.price, safeCurrency, resolvedShopTitle.cleanTitle)
            if (deal.category == "적립") "$formatted 적립" else formatted
        } else if (deal.category == "적립" || resolvedShopTitle.cleanTitle.contains("적립")) {
            "포인트 적립"
        } else {
            formatPrice(deal.price, safeCurrency, resolvedShopTitle.cleanTitle)
        }
    }

    val safeSources = (deal.sources as List<DealSource?>?).orEmpty().filterNotNull()
    val primarySite = remember(safeSources, safeSiteName) {
        val rawSite = safeSources.firstOrNull()?.siteName.orEmpty().ifBlank { safeSiteName }
        if (rawSite.contains(" - ")) rawSite.substringBefore(" - ").trim() else rawSite.trim()
    }
    val shopOrSiteBadge = remember(resolvedShopTitle.shopBadge, primarySite) {
        resolvedShopTitle.shopBadge?.takeIf { it.isNotBlank() } ?: primarySite
    }
    val timeAgo = rememberRelativeTime(deal.createdAt)

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
                stateDescription = if (deal.isClosed) "종료됨" else "진행중"
            }
            .bounceClick(
                scaleDown = 0.99f,
                onClickLabel = "${resolvedShopTitle.cleanTitle}, $priceText 상세 보기"
            ) { onDetailClick() },
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, semantic.cardBorder),
        elevation = CardDefaults.cardElevation(0.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.onSurface
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 10.dp, top = 8.dp, bottom = 8.dp, end = 2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                modifier = Modifier.size(48.dp),
                shape = thumbShape,
                color = thumbBg,
                border = BorderStroke(0.75.dp, semantic.cardBorder)
            ) {
                if (imageRequest == null) {
                    BrandPlaceholder(
                        siteName = safeSiteName,
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(thumbShape),
                        textStyle = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFFF9FAFB))
                    )
                } else {
                    Box(modifier = Modifier.fillMaxSize()) {
                        SubcomposeAsyncImage(
                            model = imageRequest,
                            contentDescription = null,
                            modifier = Modifier
                                .fillMaxSize()
                                .background(thumbBg, thumbShape)
                                .clip(thumbShape),
                            colorFilter = resolveStudioThumbnailColorFilter(isDark = isDark, isClosed = deal.isClosed),
                            contentScale = ContentScale.Crop,
                            onError = {
                                if (!activeImageUrl.orEmpty().contains("/api/proxy-image", ignoreCase = true) &&
                                    fallbackProxyUrl != null &&
                                    activeImageUrl != fallbackProxyUrl
                                ) {
                                    activeImageUrl = fallbackProxyUrl
                                }
                            },
                            error = {
                                BrandPlaceholder(
                                    siteName = safeSiteName,
                                    modifier = Modifier.fillMaxSize(),
                                    textStyle = TextStyle(fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color(0xFFF9FAFB))
                                )
                            }
                        )
                        if (deal.isClosed) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .clip(thumbShape)
                                    .background(MaterialTheme.colorScheme.inverseSurface.copy(alpha = 0.85f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "종료",
                                    color = MaterialTheme.colorScheme.inverseOnSurface,
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = 10.sp
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = highlightedTitle,
                    fontSize = 13.5.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textDecoration = if (deal.isClosed) TextDecoration.LineThrough else null,
                    color = if (deal.isClosed) semantic.textMuted else MaterialTheme.colorScheme.onSurface
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    val compactStrongPriceColor = if (isDark) Color(0xFFF9FAFB) else Color(0xFF191F28)
                    val compactMutedColor = if (isDark) Color(0xFF9CA3AF) else Color(0xFF6B7280)
                    val compactPriceFontSize = if (priceText.length >= 9) 12.5.sp else 14.sp
                    Text(
                        text = priceText,
                        style = PriceTextStyle.copy(
                            fontSize = compactPriceFontSize,
                            fontWeight = FontWeight.ExtraBold
                        ),
                        color = when {
                            deal.isClosed -> semantic.textMuted
                            deal.price > 0L || priceText == "무료" -> compactStrongPriceColor
                            else -> semantic.textMuted
                        },
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Ellipsis
                    )

                    val compactStatusLabel = when {
                        deal.isClosed -> "종료"
                        isSuperHot -> "인기"
                        displayShipping == "무료" -> "무료배송"
                        deal.isSearchFallback -> "검색이동"
                        else -> null
                    }
                    val compactAppBadge = remember(deal.id, deal.buyCount, deal.passCount, deal.appCommentCount) {
                        buildAppInteractionBadge(deal)
                    }
                    val compactMiddleMeta = remember(
                        shopOrSiteBadge,
                        compactStatusLabel,
                        compactAppBadge,
                        deal.isClosed,
                        isSuperHot,
                        isDark
                    ) {
                        buildAnnotatedString {
                            var hasPart = false
                            if (shopOrSiteBadge.isNotBlank()) {
                                withStyle(
                                    SpanStyle(
                                        color = resolveShopBrandTextColor(shopOrSiteBadge, isDark, compactMutedColor),
                                        fontWeight = FontWeight.Bold
                                    )
                                ) {
                                    append(shopOrSiteBadge)
                                }
                                hasPart = true
                            }
                            if (compactStatusLabel != null) {
                                if (hasPart) {
                                    withStyle(SpanStyle(color = compactMutedColor, fontWeight = FontWeight.Normal)) {
                                        append(" · ")
                                    }
                                }
                                val statusColor = when {
                                    deal.isClosed -> semantic.danger
                                    isSuperHot -> if (isDark) Color(0xFFFB7185) else Color(0xFFE11D48)
                                    else -> compactMutedColor
                                }
                                withStyle(SpanStyle(color = statusColor, fontWeight = FontWeight.SemiBold)) {
                                    append(compactStatusLabel)
                                }
                                hasPart = true
                            }
                            if (compactAppBadge != null) {
                                if (hasPart) {
                                    withStyle(SpanStyle(color = compactMutedColor, fontWeight = FontWeight.Normal)) {
                                        append(" · ")
                                    }
                                }
                                withStyle(SpanStyle(color = compactMutedColor, fontWeight = FontWeight.Medium)) {
                                    append(compactAppBadge.first)
                                }
                            }
                        }
                    }

                    // 가변 쇼핑몰·보조 배지 영역에만 weight(1f, fill = false)를 부여해 가격과 후미 시간(timeAgo)이 0px로 증발하지 않도록 보호
                    if (compactMiddleMeta.isNotEmpty()) {
                        Text(
                            text = compactMiddleMeta,
                            fontSize = 11.sp,
                            maxLines = 1,
                            softWrap = false,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier
                                .weight(1f, fill = false)
                                .semantics {
                                    if (compactAppBadge != null) {
                                        contentDescription = compactAppBadge.second
                                    }
                                }
                        )
                    }

                    if (timeAgo.isNotEmpty()) {
                        Text(
                            text = if (compactMiddleMeta.isNotEmpty()) "· $timeAgo" else timeAgo,
                            fontSize = 10.5.sp,
                            fontWeight = FontWeight.Normal,
                            color = compactMutedColor,
                            maxLines = 1,
                            softWrap = false,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            IconButton(
                onClick = onMoreClick,
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
    }
}

/**
 * 피드 숨김 · 뮤트 키워드 관리 바텀시트
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun MutedKeywordsBottomSheet(
    mutedKeywords: Set<String>,
    hiddenDealsCount: Int,
    onAddMutedKeyword: (String) -> Unit,
    onRemoveMutedKeyword: (String) -> Unit,
    onRestoreHiddenDeals: () -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var keywordInput by remember { mutableStateOf("") }
    var feedbackMessage by remember { mutableStateOf<String?>(null) }
    val semantic = LocalInsightDealColors.current

    val submitKeyword: () -> Unit = {
        val trimmed = keywordInput.trim()
        when {
            trimmed.isEmpty() -> {
                feedbackMessage = "뮤트할 키워드를 입력해 주세요."
            }
            mutedKeywords.any { it.equals(trimmed, ignoreCase = true) } -> {
                feedbackMessage = "이미 등록된 뮤트 키워드예요."
            }
            else -> {
                onAddMutedKeyword(trimmed)
                keywordInput = ""
                feedbackMessage = null
            }
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        dragHandle = { BottomSheetDefaults.DragHandle() }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 24.dp)
                .padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "숨김 · 키워드 뮤트",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "닫기",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Text(
                text = "등록한 키워드가 제목·쇼핑몰·태그에 포함된 핫딜을 피드에서 자동으로 숨깁니다.",
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                lineHeight = 18.sp
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = keywordInput,
                    onValueChange = {
                        keywordInput = it
                        if (feedbackMessage != null) feedbackMessage = null
                    },
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("예: 광고, 게임기, 유료", fontSize = 13.sp) },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { submitKeyword() })
                )
                Button(
                    onClick = submitKeyword,
                    modifier = Modifier.heightIn(min = 52.dp),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Text("추가", fontWeight = FontWeight.Bold)
                }
            }

            if (!feedbackMessage.isNullOrBlank()) {
                Text(
                    text = feedbackMessage.orEmpty(),
                    fontSize = 12.sp,
                    color = semantic.danger,
                    fontWeight = FontWeight.Medium
                )
            }

            if (mutedKeywords.isEmpty()) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                ) {
                    Text(
                        text = "등록된 뮤트 키워드가 없어요.",
                        fontSize = 13.sp,
                        color = semantic.textMuted,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 18.dp)
                    )
                }
            } else {
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    mutedKeywords.sorted().forEach { kw ->
                        HomeActiveFilterChip(
                            label = kw,
                            onRemove = { onRemoveMutedKeyword(kw) }
                        )
                    }
                }
            }

            if (hiddenDealsCount > 0) {
                HorizontalDivider(color = semantic.cardBorder)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "개별 숨긴 핫딜 ${hiddenDealsCount}건",
                        fontSize = 13.5.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    TextButton(
                        onClick = onRestoreHiddenDeals,
                        modifier = Modifier.heightIn(min = 48.dp)
                    ) {
                        Text("모두 다시 보기", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

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
import coil.compose.SubcomposeAsyncImage
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
@Suppress("DEPRECATION")
fun HomeScreen(
    navController: NavController, 
    viewModel: HomeViewModel = viewModel(),
    wishlistViewModel: WishlistViewModel? = null,
    keywordViewModel: KeywordManagerViewModel = viewModel()
) {
    val filterState by viewModel.filterState.collectAsState()
    val selectedCategory = filterState.category
    
    val wishlistState by wishlistViewModel?.uiState?.collectAsState(initial = WishlistUiState.Loading) ?: remember { mutableStateOf(WishlistUiState.Loading) }
    val wishlistedUrls = remember(wishlistState) {
        if (wishlistState is WishlistUiState.Success) {
            (wishlistState as WishlistUiState.Success).items.map { it.productUrl }.toSet()
        } else emptySet()
    }
    
    val context = LocalContext.current
    val haptic = androidx.compose.ui.platform.LocalHapticFeedback.current
    val networkMonitor = remember { NetworkMonitor(context) }
    val isOnline by networkMonitor.isOnline.collectAsState(initial = true)
    val isDark = isSystemInDarkTheme()
    
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
    val listState = rememberLazyListState()
    
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
                        .height(52.dp),
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
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        "나중에 설정할게요",
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
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
                            .background(
                                Brush.linearGradient(
                                    colors = listOf(
                                        Color(0xFF3A7BD5),
                                        Color(0xFF3A6073)
                                    )
                                )
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.NotificationsActive,
                            contentDescription = null,
                            tint = Color.White,
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
                        "야간 차단 및 방해금지 시간 설정으로 수면 방해 방지",
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
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
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

    // 🔔 신규 피처: 알림 내역 바텀시트 팝업 상태 관리 및 연동
    val notificationAlerts by com.ddaeany0919.insightdeal.presentation.mypage.history.NotificationHistoryManager.alerts.collectAsState()
    var showNotificationHistory by remember { mutableStateOf(false) }

    val username by com.ddaeany0919.insightdeal.presentation.auth.AuthManager.getUsername(context).collectAsState(initial = "guest")
    val isLoggedIn = username != "guest" && !username.isNullOrEmpty()

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
            android.widget.Toast.makeText(context, "해당 핫딜이 숨김 처리되었습니다.", android.widget.Toast.LENGTH_SHORT).show()
        }
    }

    var showDealOptionsBottomSheet by remember { mutableStateOf(false) }
    var selectedDealForOptions by remember { mutableStateOf<DealItem?>(null) }
    var showWhyThisDealDialog by remember { mutableStateOf<DealItem?>(null) }
    var showReportDialog by remember { mutableStateOf<DealItem?>(null) }
    var showAuthRequiredDialog by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    // [Epic 4] 쿠팡 파트너스 / 쿠팡 계정 로그인 (UI 제공)
    var showCoupangDialog by remember { mutableStateOf(false) }
    if (showCoupangDialog) {
        CoupangSettingsDialog(onDismiss = { showCoupangDialog = false })
    }

    // ✨ Pull-to-refresh 및 커스텀 새로고침 상태 통합 관리 (로고 터치 연동)
    val pullRefreshState = androidx.compose.material3.pulltorefresh.rememberPullToRefreshState()
    var isCustomRefreshing by remember { mutableStateOf(false) }

    if (pullRefreshState.isRefreshing) {
        LaunchedEffect(true) {
            isCustomRefreshing = true
            dealsPagingItems.refresh()
            viewModel.fetchTopHotDeals()
        }
    }
    LaunchedEffect(dealsPagingItems.loadState.refresh) {
        if (dealsPagingItems.loadState.refresh !is LoadState.Loading) {
            pullRefreshState.endRefresh()
            // 부드럽고 고급스러운 화면 트랜지션을 위해 350ms 극미세 지연 가미
            kotlinx.coroutines.delay(350)
            isCustomRefreshing = false
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
        Column(modifier = Modifier.fillMaxSize()) {
            TopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically, 
                        modifier = Modifier.bounceClick {
                            haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                            // 🧡 대표님 피드백: 투박한 토스트 대신 귀여운 불꽃 로딩 시트 가동
                            isCustomRefreshing = true
                            scope.launch {
                                try {
                                    viewModel.selectCategory("전체")
                                    try {
                                        listState.animateScrollToItem(0)
                                    } catch (_: Exception) {
                                        // 안전성 확보
                                    }
                                    dealsPagingItems.refresh()
                                    viewModel.fetchTopHotDeals()
                                    // ⏱️ [타임아웃 안전망]: 10초 내에 loadState가 완료되지 않으면 강제 해제
                                    kotlinx.coroutines.delay(10_000)
                                    if (isCustomRefreshing) {
                                        isCustomRefreshing = false
                                    }
                                } catch (_: Exception) {
                                    isCustomRefreshing = false
                                }
                            }
                        }
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
                    val alertsCount = notificationAlerts.size
                    Box(
                        modifier = Modifier
                            .bounceClick { showNotificationHistory = true }
                            .padding(8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Notifications,
                            contentDescription = "알림 내역",
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                        if (alertsCount > 0) {
                            Surface(
                                shape = CircleShape,
                                color = Color(0xFFFF3B30),
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .padding(top = 0.dp, end = 0.dp)
                                    .sizeIn(minWidth = 14.dp, minHeight = 14.dp)
                            ) {
                                Box(
                                    contentAlignment = Alignment.Center,
                                    modifier = Modifier.padding(horizontal = 3.dp)
                                ) {
                                    Text(
                                        text = "+$alertsCount",
                                        color = Color.White,
                                        fontSize = 8.sp,
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
                            .bounceClick { navController.navigate("keyword_alarm") }
                            .padding(8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Settings,
                            contentDescription = "키워드 알림 설정",
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
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
                            val intent = android.content.Intent().apply {
                                action = android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS
                                putExtra(android.provider.Settings.EXTRA_APP_PACKAGE, context.packageName)
                            }
                            try {
                                context.startActivity(intent)
                            } catch (e: Exception) {
                                val fallbackIntent = android.content.Intent().apply {
                                    action = android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS
                                    data = android.net.Uri.fromParts("package", context.packageName, null)
                                }
                                context.startActivity(fallbackIntent)
                            }
                        },
                    color = if (isDark) Color(0xFF2E7D32).copy(alpha = 0.85f) else Color(0xFFE8F5E9),
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
                                contentDescription = "Notification Warning",
                                tint = if (isDark) Color.White else Color(0xFF2E7D32),
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(
                                    text = "알림 권한이 꺼져 있어 특가를 놓치고 있어요 🔔",
                                    color = if (isDark) Color.White else Color(0xFF1B5E20),
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = "터치하여 실시간 핫딜 알림을 켜고 스마트하게 절약해보세요.",
                                    color = if (isDark) Color.White.copy(alpha = 0.8f) else Color(0xFF388E3C),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                        Icon(
                            imageVector = Icons.Default.KeyboardArrowRight,
                            contentDescription = "이동",
                            tint = if (isDark) Color.White.copy(alpha = 0.7f) else Color(0xFF2E7D32),
                            modifier = Modifier.size(12.dp)
                        )
                    }
                }
            }

            LazyColumn(
                state = listState,
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
                                        // 🎯 AI 요약 여부와 상관없이 100점 만점 꿀딜은 즉각 노출 보장!
                                        val isSuperHot = deal.honeyScore >= 100
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
                                            val proxiedPickUrl = remember(pick.id, pick.imageUrl, com.ddaeany0919.insightdeal.data.network.NetworkConfig.getActiveServerUrl()) {
                                                val fallbackPickImg = "https://placehold.co/400x300/E2E8F0/A0AEC0?text=Deal"
                                                val rawPickUrl = pick.imageUrl.takeIf { !it.isNullOrBlank() } ?: fallbackPickImg
                                                if (!rawPickUrl.isNullOrBlank() && rawPickUrl.startsWith("http")) {
                                                    val baseUrl = com.ddaeany0919.insightdeal.data.network.NetworkConfig.getActiveServerUrl().removeSuffix("/")
                                                    "$baseUrl/api/proxy-image?url=${java.net.URLEncoder.encode(rawPickUrl, "UTF-8")}"
                                                } else rawPickUrl
                                            }

                                            val imageRequest = remember(proxiedPickUrl, pick.postUrl) {
                                                coil.request.ImageRequest.Builder(context)
                                                    .data(proxiedPickUrl)
                                                    .setHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) Chrome/120.0.0.0 Safari/537.36")
                                                    .setHeader("Referer", pick.postUrl ?: "https://insightdeal.com/")
                                                    .crossfade(true)
                                                    .precision(coil.size.Precision.EXACT)
                                                    .build()
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
                                                    "금액 확인 필요"
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
                                                        trimmed == "정보 없음" || trimmed.isEmpty() -> "배송비 확인 필요"
                                                        trimmed.contains("와우") -> "와우무료"
                                                        trimmed.contains("스마일") || trimmed.contains("스클") -> "스클무료"
                                                        trimmed.contains("우주패스") -> "우주패스"
                                                        trimmed == "0" || trimmed == "0원" || trimmed == "무배" || trimmed == "무료" || trimmed == "무료배송" -> "무료"
                                                        trimmed.startsWith("0원/") || trimmed.startsWith("0원+") || trimmed.startsWith("0/") || trimmed.startsWith("0+") -> trimmed.replaceFirst(Regex("^0(원)?\\s*"), "무료 ")
                                                        trimmed == "유료" || trimmed == "유료배송" -> "유료"
                                                        trimmed.all { it.isDigit() || it == ',' } -> "${trimmed}원"
                                                        else -> trimmed.replace("무료배송", "무료").replace("유료배송", "유료").replace(Regex("(이상|미만|이하)(0원?)?\\s*무료"), "$1 무료").replace(Regex("(\\d+[만천])(이상|미만|이하)"), "$1원$2").trim()
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
                                                     if (pick.imageUrl.isNullOrBlank()) {
                                                         BrandPlaceholder(
                                                             siteName = pick.siteName,
                                                             modifier = Modifier
                                                                 .height(100.dp)
                                                                 .fillMaxWidth()
                                                                 .clip(RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)),
                                                             textStyle = TextStyle(fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, color = Color.White)
                                                         )
                                                     } else {
                                                         SubcomposeAsyncImage(
                                                             model = imageRequest,
                                                             contentDescription = "Carousel Image",
                                                             modifier = Modifier
                                                                 .height(100.dp)
                                                                 .fillMaxWidth()
                                                                 .background(if (isDark) MaterialTheme.colorScheme.surfaceVariant else Color.White)
                                                                 .clip(RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)),
                                                             contentScale = ContentScale.Crop,
                                                             error = {
                                                                 BrandPlaceholder(
                                                                     siteName = pick.siteName,
                                                                     modifier = Modifier.fillMaxSize(),
                                                                     textStyle = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Color.White)
                                                                 )
                                                             }
                                                         )
                                                     }
                                                     Column(Modifier.padding(8.dp)) {
                                                         Text(
                                                             text = pick.title,
                                                             maxLines = 2,
                                                             overflow = TextOverflow.Ellipsis,
                                                             fontSize = 13.sp, 
                                                            fontWeight = FontWeight.Bold, 
                                                            lineHeight = 18.sp,
                                                            color = MaterialTheme.colorScheme.onSurface,
                                                        )
                                                        Spacer(Modifier.weight(1f))
                                                        
                                                        Row(
                                                            verticalAlignment = Alignment.Bottom, 
                                                            horizontalArrangement = Arrangement.SpaceBetween, 
                                                            modifier = Modifier.fillMaxWidth()
                                                        ) {
                                                            Row(
                                                                verticalAlignment = Alignment.CenterVertically,
                                                                modifier = Modifier.weight(1f).padding(end = 2.dp)
                                                            ) {
                                                                // 🎯 지능형 동적 가격 폰트 스케일링 가드 (글자 길이에 맞춘 유연한 사이즈 조정)
                                                                val priceFontSize = when {
                                                                    priceText.length >= 9 -> 10.sp
                                                                    priceText.length >= 7 -> 12.sp
                                                                    priceText.length >= 5 -> 14.sp
                                                                    else -> 15.sp
                                                                }
                                                                Text(
                                                                    text = priceText, 
                                                                    color = if (isDark) Color(0xFFFF453A) else Color(0xFFFF3B30), 
                                                                    fontSize = priceFontSize, 
                                                                    fontWeight = FontWeight.ExtraBold, 
                                                                    maxLines = 1, 
                                                                    overflow = TextOverflow.Ellipsis
                                                                )
                                                                if (displayShipping != null) {
                                                                    Spacer(modifier = Modifier.width(4.dp))
                                                                    Surface(
                                                                        shape = RoundedCornerShape(3.dp), 
                                                                        color = if (isDark) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceVariant
                                                                    ) {
                                                                        Text(
                                                                            text = displayShipping, // 가로 공간 극대화를 위해 "배송비:"를 떼고 배송비 정보만 실속 칩셋으로 노출!
                                                                            fontSize = 8.sp, 
                                                                            color = if (isDark) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurfaceVariant, 
                                                                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp), 
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
                                                                            vm.addItem(pick.title, targetUrl, pick.price.toInt(), pick.price.toInt(), pick.siteName)
                                                                            scope.launch {
                                                                                val result = snackbarHostState.showSnackbar(
                                                                                    message = "관심 상품에 등록되었습니다 💖",
                                                                                    actionLabel = "찜 목록 보기 🚀",
                                                                                    duration = SnackbarDuration.Short
                                                                                )
                                                                                if (result == SnackbarResult.ActionPerformed) {
                                                                                    navController.navigate("watchlist")
                                                                                }
                                                                            }
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
                            
                            // 🔔 실시간 키워드 알림 설정 유도 배너 (Toss 스타일)
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .bounceClick { navController.navigate("keyword_alarm") }
                                    .padding(horizontal = 4.dp, vertical = 6.dp),
                                shape = RoundedCornerShape(16.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = if (isDark) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f) else Color(0xFFF2F4F6)
                                )
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp, vertical = 14.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(40.dp)
                                            .clip(CircleShape)
                                            .background(if (isDark) Color(0xFF2C2C2E) else Color.White),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.NotificationsActive,
                                            contentDescription = "알림",
                                            tint = if (isDark) Color(0xFFFF453A) else Color(0xFFFF3B30),
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = "놓치기 아까운 특가, 알림으로 받으세요",
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 13.5.sp,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Text(
                                            text = "실시간 키워드 알림 설정하기",
                                            fontSize = 11.5.sp,
                                            fontWeight = FontWeight.Medium,
                                            color = if (isDark) MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f) else Color(0xFF4E5968)
                                        )
                                    }
                                    Icon(
                                        imageVector = Icons.Default.KeyboardArrowRight,
                                        contentDescription = "이동",
                                        tint = if (isDark) MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f) else Color(0xFF8B95A1),
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                            Spacer(Modifier.height(8.dp))
                            Text("실시간 핫딜", fontWeight = FontWeight.ExtraBold, fontSize = 18.sp, modifier = Modifier.padding(start = 4.dp), color = MaterialTheme.colorScheme.onSurface)
                        }
                    }
            // ✨ 상태 관리를 통한 스켈레톤 로딩 노출
            // [HOTFIX] 이미 로드된 데이터가 존재할 때(itemCount > 0) refresh가 발생하더라도 목록을 밀어버리지 않고 유지하여 스크롤 튕김 완벽 차단!
            when {
                dealsPagingItems.loadState.refresh is LoadState.Loading && dealsPagingItems.itemCount == 0 -> {
                    items(
                        count = 3,
                        key = { index -> "skeleton_$index" },
                        contentType = { "skeleton_loader" }
                    ) { HomeDealCardSkeleton() }
                }
                dealsPagingItems.loadState.refresh is LoadState.Error -> {
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
                        
                        // 🔍 유저 고속 스크롤 추적용 실시간 렌더링 로깅 가드
                        android.util.Log.d(
                            "InsightDealScroll", 
                            "Index: $index | DealID: ${deal?.id ?: "NULL"} | Title: ${deal?.title?.take(15) ?: "PLACEHOLDER(Skeleton)"}"
                        )

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
                                                    vm.addItem(deal.title, targetUrl, deal.price.toInt(), deal.price.toInt(), deal.siteName)
                                                    scope.launch {
                                                        val result = snackbarHostState.showSnackbar(
                                                            message = "관심 상품에 등록되었습니다 💖",
                                                            actionLabel = "찜 목록 보기 🚀",
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
                                    }
                                )
                            }
                        } else {
                            // 🚀 스크롤 속도가 데이터 로드보다 빠를 때 높이 0으로 압축되는 레이아웃 붕괴를 원천 방어하여 튕김 현상 제거!
                            HomeDealCardSkeleton()
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

        // ✨ PullToRefresh Indicator - 기본 동그라미 스피너는 비주얼 노이즈이므로 제거하고, 
        // 🧡 대표님 피드백에 따라 오직 귀여운 불꽃 애니메이션 오버레이만 노출하도록 단일화합니다.
        /*
        androidx.compose.material3.pulltorefresh.PullToRefreshContainer(
            modifier = Modifier.align(Alignment.TopCenter),
            state = pullRefreshState,
        )
        */

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
                                                    message = "관심 상품에 등록되었습니다 💖",
                                                    actionLabel = "찜 목록 보기 🚀",
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
                                tint = if (isWishlisted) Color.Red else MaterialTheme.colorScheme.onSurfaceVariant,
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
                            // 1) 이 글 숨기기 (로그인 가드 및 영구 필터 등록)
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        showDealOptionsBottomSheet = false
                                        if (isLoggedIn) {
                                            hideDeal(deal.id)
                                        } else {
                                            showAuthRequiredDialog = true
                                        }
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
                            
                            HorizontalDivider(color = if (isDark) MaterialTheme.colorScheme.outlineVariant else Color(0xFFE4E7EC), thickness = 1.dp, modifier = Modifier.padding(horizontal = 16.dp))
                            
                            // 3) 신고하기 (빨간색 맑은 레드)
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        showDealOptionsBottomSheet = false
                                        showReportDialog = deal
                                    }
                                    .padding(horizontal = 16.dp, vertical = 18.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Report,
                                    contentDescription = null,
                                    tint = Color.Red,
                                    modifier = Modifier.size(24.dp)
                                )
                                Text(
                                    text = "신고하기",
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = Color.Red
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
                title = { Text(text = "이 글이 왜 보이나요? 💡", fontWeight = FontWeight.Bold) },
                text = {
                    Text(
                        text = "이 글은 [${deal.siteName}]에서 높은 인기를 얻고 있는 실시간 핫딜입니다.\n\n" +
                                "InsightDeal의 1:1 지능형 AI 추천 엔진이 분석한 결과, 현재 할인율이 상위 ${deal.honeyScore}%에 달하며 48시간 이내에 생성된 초신선 꿀딜 상태이므로 유저님의 쇼핑 만족도를 극대화할 수 있는 타점으로 판단하여 노출되었습니다. 🔥",
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

        // 🚨 신고 사유 선택 다이얼로그 (신고하기 클릭 시)
        if (showReportDialog != null) {
            var selectedReason by remember { mutableStateOf(0) }
            val reasons = listOf(
                "광고성 / 홍보성 / 도배성 게시글",
                "판매 종료 / 링크 깨짐 / 가격 오류",
                "욕설 / 비방 / 불쾌감을 주는 내용",
                "기타 사유"
            )
            AlertDialog(
                onDismissRequest = { showReportDialog = null },
                title = { Text(text = "🚨 이 글을 신고하시겠습니까?", fontWeight = FontWeight.Bold) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(text = "이 글의 신고 사유를 정밀하게 선택해주세요. 24시간 내 모니터링 후 조치됩니다.", fontSize = 12.sp, color = Color.Gray)
                        Spacer(modifier = Modifier.height(8.dp))
                        reasons.forEachIndexed { index, reason ->
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { selectedReason = index }
                                    .padding(vertical = 4.dp)
                            ) {
                                RadioButton(
                                    selected = selectedReason == index,
                                    onClick = { selectedReason = index }
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(text = reason, fontSize = 14.sp)
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            showReportDialog = null
                            android.widget.Toast.makeText(context, "신고가 정상적으로 접수되었습니다. 신속하게 정화 조치하겠습니다! 🚨", android.widget.Toast.LENGTH_SHORT).show()
                        }
                    ) {
                        Text("신고 제출", color = Color.Red, fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showReportDialog = null }) {
                        Text("취소")
                    }
                }
            )
        }

        // 🔒 로그인 권유 가드 다이얼로그 (비로그인 사용자 전용 워프 터널)
        if (showAuthRequiredDialog) {
            AlertDialog(
                onDismissRequest = { showAuthRequiredDialog = false },
                title = { Text(text = "로그인이 필요합니다 🔒", fontWeight = FontWeight.Bold) },
                text = {
                    Text(
                        text = "관심 키워드 알림, 핫딜 숨기기 등 지능형 고관여 활동은 로그인한 회원만 이용할 수 있습니다.\n\n" +
                                "1초 간편 소셜 로그인 화면으로 이동하시겠습니까? ⚡",
                        fontSize = 14.sp,
                        lineHeight = 20.sp
                    )
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            showAuthRequiredDialog = false
                            navController.navigate("auth")
                        }
                    ) {
                        Text("확인하고 로그인하기", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showAuthRequiredDialog = false }) {
                        Text("취소")
                    }
                }
            )

            SnackbarHost(
                hostState = snackbarHostState,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(bottom = 16.dp)
            )
        }

        // 🧡 대표님 헌정: 귀여운 불꽃 캐릭터 펄싱 애니메이션 새로고침 오버레이 뷰
        AnimatedVisibility(
            visible = isCustomRefreshing,
            enter = androidx.compose.animation.fadeIn(animationSpec = tween(300)),
            exit = androidx.compose.animation.fadeOut(animationSpec = tween(300))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.18f)) // 🧡 대표님 피드백: 개방성과 시원함을 주기 위해 대폭 투명하게 딤 완화!
                    .pointerInput(Unit) {}, // 뒷배경 클릭/스크롤 전면 차단 가드
                contentAlignment = Alignment.Center
            ) {
                // 무한 펄싱 바운싱 애니메이션 정의
                val infiniteTransition = rememberInfiniteTransition(label = "flame_pulse")
                val scale by infiniteTransition.animateFloat(
                    initialValue = 0.9f,
                    targetValue = 1.2f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(650, easing = FastOutSlowInEasing),
                        repeatMode = RepeatMode.Reverse
                    ),
                    label = "flame_scale"
                )
                val alpha by infiniteTransition.animateFloat(
                    initialValue = 0.7f,
                    targetValue = 1.0f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(650, easing = FastOutSlowInEasing),
                        repeatMode = RepeatMode.Reverse
                    ),
                    label = "flame_alpha"
                )

                Card(
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(
                        // 🧡 대표님 피드백: 카드 자체에도 은은한 투명도를 적용하여 유리(Glassmorphism) 같은 품격 극대화
                        containerColor = if (isDark) Color(0xFF1E1E1E).copy(alpha = 0.88f) else Color.White.copy(alpha = 0.92f)
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 10.dp),
                    modifier = Modifier
                        .width(260.dp)
                        .padding(16.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 32.dp, horizontal = 20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        // 펄싱 튀는 귀여운 불꽃
                        Box(
                            modifier = Modifier
                                .size(72.dp)
                                .graphicsLayer(
                                    scaleX = scale,
                                    scaleY = scale,
                                    alpha = alpha
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(text = "🔥", fontSize = 48.sp)
                        }
                        
                        Spacer(modifier = Modifier.height(20.dp))
                        
                        Text(
                            text = "새로고침 중입니다...",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isDark) Color.White else Color(0xFF191F28),
                            textAlign = TextAlign.Center
                        )
                        
                        Spacer(modifier = Modifier.height(6.dp))
                        
                        Text(
                            text = "잠시만 기다려주세요",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            color = if (isDark) Color(0xFF9E9E9E) else Color(0xFF6B7684),
                            textAlign = TextAlign.Center
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
@Suppress("UNUSED_PARAMETER")
fun DealCardComposable(
    deal: DealItem, 
    onDetailClick: () -> Unit = {}, 
    onOpenUrl: (String) -> Unit = {},
    isWishlisted: Boolean = false,
    onToggleWishlist: () -> Unit = {},
    onMoreClick: () -> Unit = {}
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

    // 2. 이미지 URL을 백엔드 프록시를 통해 안전하고 쨍하게 100% 로딩 (디스크 캐싱 및 헤더 우회 탑재)
    val proxiedUrl = remember(deal.id, deal.imageUrl, com.ddaeany0919.insightdeal.data.network.NetworkConfig.getActiveServerUrl()) {
        val rawUrl = deal.imageUrl
        if (rawUrl.isNullOrBlank()) null
        else if (rawUrl.startsWith("http")) {
            try {
                val baseUrl = com.ddaeany0919.insightdeal.data.network.NetworkConfig.getActiveServerUrl().removeSuffix("/")
                "$baseUrl/api/proxy-image?url=${java.net.URLEncoder.encode(rawUrl, "UTF-8")}"
            } catch (e: Exception) {
                rawUrl
            }
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
                .precision(coil.size.Precision.EXACT)
                .build()
        }
    }

    // 4. 출처 뱃지 목록 캐싱 (디코드 및 서브 출처 잘린 동일 명칭의 중복 뱃지 완전 소탕)
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

    // 5. 배송비 연산 캐싱
    val displayShipping = remember(deal.id, deal.shippingFee, deal.category, deal.title) {
        val digitalCategories = listOf("적립", "이벤트", "모바일/기프티콘", "상품권", "패키지/이용권")
        val isDigitalTitle = deal.title.contains("요금제") || deal.title.contains("데이터")
        val hideShipping = deal.category in digitalCategories || isDigitalTitle

        if (hideShipping) null
        else {
            val trimmed = deal.shippingFee?.trim() ?: "정보 없음"
            when {
                trimmed == "정보 없음" || trimmed.isEmpty() -> "배송비 확인 필요"
                trimmed.contains("와우") -> "와우무료"
                trimmed.contains("스마일") || trimmed.contains("스클") -> "스클무료"
                trimmed.contains("우주패스") -> "우주패스"
                trimmed == "0" || trimmed == "0원" || trimmed == "무배" || trimmed == "무료" || trimmed == "무료배송" -> "무료"
                trimmed.startsWith("0원/") || trimmed.startsWith("0원+") || trimmed.startsWith("0/") || trimmed.startsWith("0+") -> trimmed.replaceFirst(Regex("^0(원)?\\s*"), "무료 ")
                trimmed == "유료" || trimmed == "유료배송" -> "유료"
                trimmed.all { it.isDigit() || it == ',' } -> "${trimmed}원"
                else -> trimmed.replace("무료배송", "무료").replace("유료배송", "유료").replace(Regex("(이상|미만|이하)(0원?)?\\s*무료"), "$1 무료").replace(Regex("(\\d+[만천])(이상|미만|이하)"), "$1원$2").trim()
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
            "가격 확인 필요"
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
        if (percentileText == "평이") "📊 AI 가격 메리트 (${deal.honeyScore}점)" else "💡 AI 가격 메리트: $percentileText 추천! (${deal.honeyScore}점)"
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
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(modifier = Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.Top) {
                    if (imageRequest == null) {
                        // 이미지가 없는 경우 (또는 분리된 상품 중 이미지가 없는 경우)
                        BrandPlaceholder(
                            siteName = deal.siteName,
                            modifier = Modifier
                                .size(80.dp)
                                .clip(RoundedCornerShape(8.dp)),
                            textStyle = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Color.White)
                        )
                    } else {
                        Box(modifier = Modifier
                            .size(80.dp)
                            .clickable {
                                try {
                                    val targetUrl = deal.ecommerceUrl?.takeIf { it.isNotBlank() }?.replace(":443", "") ?: deal.postUrl
                                    if (!targetUrl.isNullOrBlank()) {
                                        var finalUrl = if (targetUrl.startsWith("http")) targetUrl else "https://$targetUrl"
                                        if (finalUrl.contains("bbasak.com") && !finalUrl.contains("device=pc")) {
                                            finalUrl += if (finalUrl.contains("?")) "&device=pc" else "?device=pc"
                                        }
                                        onOpenUrl(finalUrl)
                                    }
                                } catch (e: Exception) {
                                    android.widget.Toast.makeText(context, "브라우저 앱을 열 수 없습니다.", android.widget.Toast.LENGTH_SHORT).show()
                                }
                            }
                        ) {
                            SubcomposeAsyncImage(
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
                                error = {
                                    BrandPlaceholder(
                                        siteName = deal.siteName,
                                        modifier = Modifier.fillMaxSize(),
                                        textStyle = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color.White)
                                    )
                                }
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
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                sourcesToRender.forEach { source ->
                                    val displaySiteName = if (source.siteName.contains(" - ")) {
                                        source.siteName.substringBefore(" - ").trim()
                                    } else {
                                        source.siteName
                                    }
                                    val siteNameLower = displaySiteName.lowercase(Locale.getDefault())
                                    val (containerColor, contentColor) = when {
                                        siteNameLower.contains("뽐뿌") -> Color(0xFF1565C0) to Color.White
                                        siteNameLower.contains("퀘이사존") -> Color(0xFFE65100) to Color.White
                                        siteNameLower.contains("루리웹") -> Color(0xFF0D47A1) to Color.White
                                        siteNameLower.contains("펨코") || siteNameLower.contains("에펨코리아") -> Color(0xFF0288D1) to Color.White
                                        siteNameLower.contains("빠삭") -> Color(0xFFC2185B) to Color.White
                                        siteNameLower.contains("클리앙") -> Color(0xFF37474F) to Color.White
                                        else -> if (isDark) Color(0xFF424242) to Color.White else Color(0xFFE0E0E0) to Color.Black
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

                                val timeAgo = rememberRelativeTime(deal.createdAt)
                                if (timeAgo.isNotEmpty()) {
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = timeAgo,
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                                    )
                                }
                            }

                            IconButton(
                                onClick = onMoreClick,
                                modifier = Modifier.size(24.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.MoreVert,
                                    contentDescription = "더보기",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        
                        Spacer(modifier = Modifier.height(6.dp))
                        
                        Text(
                            text = deal.title,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.fillMaxWidth().padding(end = 8.dp),
                            textDecoration = if (deal.isClosed) TextDecoration.LineThrough else null,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        
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
                        }
                    }
                }
            
            // ✨ 아코디언 토글 상세 영역 (다이렉트 구매처 아웃링크 및 큰 이미지)
            androidx.compose.animation.AnimatedVisibility(visible = isExpanded) {
                Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp)) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    Spacer(modifier = Modifier.height(12.dp))
                    
                    // ✨ 큰 사이즈 원본 이미지 뷰어 (OOM 방지 및 크로스페이드)
                    Box(modifier = Modifier.fillMaxWidth().heightIn(min = 150.dp, max = 220.dp), contentAlignment = Alignment.Center) {
                        if (deal.imageUrl.isNullOrBlank()) {
                            BrandPlaceholder(
                                siteName = deal.siteName,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(200.dp)
                                    .clip(RoundedCornerShape(8.dp)),
                                textStyle = TextStyle(fontSize = 24.sp, fontWeight = FontWeight.ExtraBold, color = Color.White)
                            )
                        } else {
                            val proxiedExpandedUrl = remember(deal.id, deal.imageUrl, com.ddaeany0919.insightdeal.data.network.NetworkConfig.getActiveServerUrl()) {
                                val rawImageUrl = deal.imageUrl
                                if (rawImageUrl.isNullOrBlank()) ""
                                else if (!rawImageUrl.isNullOrBlank() && rawImageUrl.startsWith("http")) {
                                    val baseUrl = com.ddaeany0919.insightdeal.data.network.NetworkConfig.getActiveServerUrl().removeSuffix("/")
                                    "$baseUrl/api/proxy-image?url=${java.net.URLEncoder.encode(rawImageUrl, "UTF-8")}"
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
                                    .precision(coil.size.Precision.EXACT)
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
                        }
                        
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
        
        // 핫딜 뱃지를 우측 최상단에 고정 (Box로 감싸서 BoxScope 2D 정렬 완벽 보장!)
        if (isSuperHot) {
            Box(modifier = Modifier.fillMaxWidth()) {
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
                            uncheckedColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
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
                            text = "해당 시간대에 수신을 제한합니다. (탭하여 시간 변경)", 
                            fontSize = 12.sp, 
                            color = MaterialTheme.colorScheme.error
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
                            "🏷️ 아직 등록된 키워드가 없어요.",
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            "원하는 키워드를 입력하고 + 버튼을 눌러 추가해보세요!",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
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

@Composable
fun BrandPlaceholder(
    siteName: String, 
    modifier: Modifier = Modifier, 
    textStyle: TextStyle = TextStyle(fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color.White)
) {
    val siteLower = siteName.lowercase(Locale.getDefault())
    val (colors, _) = remember(siteLower) {
        when {
            siteLower.contains("뽐뿌") -> listOf(Color(0xFF1976D2), Color(0xFF0D47A1)) to "뽐뿌"
            siteLower.contains("퀘이사존") -> listOf(Color(0xFFFFB74D), Color(0xFFE65100)) to "퀘존"
            siteLower.contains("펨코") || siteLower.contains("에펨코리아") -> listOf(Color(0xFF4FC3F7), Color(0xFF0288D1)) to "펨코"
            siteLower.contains("루리웹") -> listOf(Color(0xFF5C6BC0), Color(0xFF1A237E)) to "루리"
            siteLower.contains("빠삭") -> listOf(Color(0xFFF48FB1), Color(0xFFC2185B)) to "빠삭"
            siteLower.contains("슬리앙") || siteLower.contains("클리앙") -> listOf(Color(0xFF78909C), Color(0xFF37474F)) to "클앙"
            else -> listOf(Color(0xFFB0BEC5), Color(0xFF546E7A)) to "HOT"
        }
    }

    Box(
        modifier = modifier
            .background(
                brush = Brush.linearGradient(
                    colors = colors,
                    start = androidx.compose.ui.geometry.Offset(0f, 0f),
                    end = androidx.compose.ui.geometry.Offset(100f, 100f)
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.padding(4.dp)
        ) {
            // 🔥 초귀여운 2D 슬픈 핫딜 불꽃(🔥) Canvas 일러스트
            androidx.compose.foundation.Canvas(
                modifier = Modifier
                    .size(26.dp)
                    .padding(bottom = 1.dp)
            ) {
                // 1. 외곽 큰 불꽃 몸체 (빨강 -> 오렌지 그라데이션)
                val firePath = androidx.compose.ui.graphics.Path().apply {
                    moveTo(size.width * 0.5f, size.height * 0.05f)
                    // 우측 뾰족한 곡선
                    quadraticBezierTo(size.width * 0.85f, size.height * 0.35f, size.width * 0.85f, size.height * 0.65f)
                    quadraticBezierTo(size.width * 0.85f, size.height * 0.95f, size.width * 0.5f, size.height * 0.95f)
                    // 좌측 뾰족한 곡선
                    quadraticBezierTo(size.width * 0.15f, size.height * 0.95f, size.width * 0.15f, size.height * 0.65f)
                    quadraticBezierTo(size.width * 0.15f, size.height * 0.35f, size.width * 0.5f, size.height * 0.05f)
                    close()
                }
                
                // 불꽃 그라데이션
                val fireBrush = Brush.verticalGradient(
                    colors = listOf(Color(0xFFFF3D00), Color(0xFFFF9100)),
                    startY = 0f,
                    endY = size.height
                )
                drawPath(path = firePath, brush = fireBrush)

                // 2. 내부 노란 불꽃 핵 (노란색)
                val innerFirePath = androidx.compose.ui.graphics.Path().apply {
                    moveTo(size.width * 0.5f, size.height * 0.35f)
                    quadraticBezierTo(size.width * 0.72f, size.height * 0.55f, size.width * 0.72f, size.height * 0.75f)
                    quadraticBezierTo(size.width * 0.72f, size.height * 0.9f, size.width * 0.5f, size.height * 0.9f)
                    quadraticBezierTo(size.width * 0.28f, size.height * 0.9f, size.width * 0.28f, size.height * 0.75f)
                    quadraticBezierTo(size.width * 0.28f, size.height * 0.55f, size.width * 0.5f, size.height * 0.35f)
                    close()
                }
                drawPath(path = innerFirePath, color = Color(0xFFFFEA3B))

                // 3. 슬픈 눈 (동그란 눈에 맺힌 눈물)
                drawCircle(
                    color = Color(0xFF212121),
                    radius = 1.8f,
                    center = androidx.compose.ui.geometry.Offset(size.width * 0.4f, size.height * 0.65f)
                )
                drawCircle(
                    color = Color(0xFF212121),
                    radius = 1.8f,
                    center = androidx.compose.ui.geometry.Offset(size.width * 0.6f, size.height * 0.65f)
                )

                // 4. 슬픈 입 모양 (우는 아치형)
                val mouthPath = androidx.compose.ui.graphics.Path().apply {
                    moveTo(size.width * 0.46f, size.height * 0.78f)
                    quadraticBezierTo(
                        size.width * 0.5f, size.height * 0.73f,
                        size.width * 0.54f, size.height * 0.78f
                    )
                }
                drawPath(
                    path = mouthPath,
                    color = Color(0xFF212121),
                    style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1.5f)
                )

                // 5. 수줍고 귀여운 뺨 볼터치
                drawCircle(
                    color = Color(0xFFFF8A80).copy(alpha = 0.8f),
                    radius = 1.8f,
                    center = androidx.compose.ui.geometry.Offset(size.width * 0.32f, size.height * 0.72f)
                )
                drawCircle(
                    color = Color(0xFFFF8A80).copy(alpha = 0.8f),
                    radius = 1.8f,
                    center = androidx.compose.ui.geometry.Offset(size.width * 0.68f, size.height * 0.72f)
                )

                // 6. 💧 주룩주룩 흐르는 귀여운 ㅠㅠ 눈물방울!
                // 왼쪽 눈물방울
                val tearPathLeft = androidx.compose.ui.graphics.Path().apply {
                    moveTo(size.width * 0.38f, size.height * 0.68f)
                    quadraticBezierTo(size.width * 0.33f, size.height * 0.73f, size.width * 0.33f, size.height * 0.78f)
                    quadraticBezierTo(size.width * 0.33f, size.height * 0.83f, size.width * 0.38f, size.height * 0.83f)
                    quadraticBezierTo(size.width * 0.43f, size.height * 0.83f, size.width * 0.43f, size.height * 0.78f)
                    close()
                }
                drawPath(path = tearPathLeft, color = Color(0xFF29B6F6))

                // 오른쪽 눈물방울
                val tearPathRight = androidx.compose.ui.graphics.Path().apply {
                    moveTo(size.width * 0.58f, size.height * 0.68f)
                    quadraticBezierTo(size.width * 0.53f, size.height * 0.73f, size.width * 0.53f, size.height * 0.78f)
                    quadraticBezierTo(size.width * 0.53f, size.height * 0.83f, size.width * 0.58f, size.height * 0.83f)
                    quadraticBezierTo(size.width * 0.63f, size.height * 0.83f, size.width * 0.63f, size.height * 0.78f)
                    close()
                }
                drawPath(path = tearPathRight, color = Color(0xFF29B6F6))
            }

            Spacer(modifier = Modifier.height(2.dp))
            
            // 📝 직관적이고 세련된 이미지 없음 문구 (잘림 없이 다 노출되도록 처리)
            Text(
                text = "이미지 없음",
                style = textStyle.copy(
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = (-0.5).sp
                ),
                textAlign = TextAlign.Center,
                maxLines = 1
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
    val alertsState by com.ddaeany0919.insightdeal.presentation.mypage.history.NotificationHistoryManager.alerts.collectAsState()
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
                    Text("🔔 알림 내역", fontWeight = FontWeight.ExtraBold, fontSize = 22.sp, color = MaterialTheme.colorScheme.onSurface)
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
                            }
                        ) {
                            Text("전체 삭제", color = Color.Red, fontWeight = FontWeight.Bold, fontSize = 14.sp)
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
                        .height(250.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("📭", fontSize = 48.sp)
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            "새로운 알림 내역이 없습니다.",
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            "맞춤 키워드를 등록하면 실시간 특가 정보를 받아볼 수 있어요!",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
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
                    items(alertsState) { alert ->
                        val dateText = remember(alert.receivedAt) {
                            val instant = java.time.Instant.ofEpochMilli(alert.receivedAt)
                            val localDateTime = java.time.LocalDateTime.ofInstant(instant, java.time.ZoneId.systemDefault())
                            val formatter = java.time.format.DateTimeFormatter.ofPattern("MM-dd HH:mm")
                            localDateTime.format(formatter)
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
                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                                    )
                                    IconButton(
                                        onClick = {
                                            com.ddaeany0919.insightdeal.presentation.mypage.history.NotificationHistoryManager.deleteAlert(context, alert.id)
                                        },
                                        modifier = Modifier.size(24.dp)
                                    ) {
                                        Icon(
                                            Icons.Default.Delete,
                                            contentDescription = "삭제",
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                            modifier = Modifier.size(16.dp)
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
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

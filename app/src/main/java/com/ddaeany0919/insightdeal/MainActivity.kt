package com.ddaeany0919.insightdeal

import android.os.Bundle
import android.util.Log
import android.Manifest
import android.os.Build
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.FragmentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.ddaeany0919.insightdeal.presentation.theme.ThemeManager
import com.ddaeany0919.insightdeal.presentation.theme.ThemeMode
import com.ddaeany0919.insightdeal.presentation.wishlist.*
import com.ddaeany0919.insightdeal.presentation.home.HomeScreen
import com.ddaeany0919.insightdeal.presentation.home.HomeViewModel
import com.ddaeany0919.insightdeal.presentation.theme.InsightDealTheme
import com.ddaeany0919.insightdeal.presentation.components.PinLockDialog
import com.ddaeany0919.insightdeal.presentation.mypage.history.NotificationHistoryManager
import com.ddaeany0919.insightdeal.core.security.EncryptedPrefsManager
import com.ddaeany0919.insightdeal.data.KeywordPreferencesDataSource
import com.ddaeany0919.insightdeal.data.LocalDataProvider
import androidx.compose.foundation.clickable
import com.google.firebase.messaging.FirebaseMessaging
import com.ddaeany0919.insightdeal.network.NetworkModule
import com.ddaeany0919.insightdeal.network.ApiService
import com.ddaeany0919.insightdeal.network.RegisterDeviceReq
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import retrofit2.HttpException

import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen

private const val TAG_UI = "MainActivity"

// [jetpack-compose-performance] 리컴포지션 시 불필요한 컬렉션 할당 방지를 위한 파일 레벨 상수 호이스팅
private val BOTTOM_NAV_ROUTES = setOf(
    "home", "advanced_search", "category", "category_detail", "community",
    "mypage", "watchlist", "my_posts", "my_comments", "recent_deals", "settings"
)

private val NAVIGATION_ITEMS = listOf(
    BottomNavItem("home", "홈", Icons.Outlined.Home, Icons.Filled.Home, "홈"),
    BottomNavItem("advanced_search", "검색", Icons.Outlined.Search, Icons.Filled.Search, "검색"),
    BottomNavItem("category", "카테고리", Icons.Outlined.GridView, Icons.Filled.GridView, "카테고리"),
    BottomNavItem("community", "커뮤니티", Icons.Outlined.Forum, Icons.Filled.Forum, "커뮤니티"),
    BottomNavItem("mypage", "내정보", Icons.Outlined.Person, Icons.Filled.Person, "내정보 (마이페이지)")
)

class MainActivity : FragmentActivity() {
    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            Log.d(TAG_UI, "Notification permission granted")
        } else {
            Log.w(TAG_UI, "Notification permission denied")
        }
    }

    private var currentIntent by mutableStateOf<android.content.Intent?>(null)

    override fun onNewIntent(intent: android.content.Intent?) {
        super.onNewIntent(intent)
        // [android-intent-security] singleTop 액티비티 재진입 시 Activity.getIntent() 동기화 강제
        if (intent != null) {
            setIntent(intent)
        }
        currentIntent = intent
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        // SplashScreen 적용
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // [edge-to-edge] 하단 NavigationBar 사용 시 시스템 강제 반투명 스크림(Scrim) 비활성화
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.isNavigationBarContrastEnforced = false
        }
        val deviceUserId = runCatching { LocalDataProvider.getDeviceUuid() }
            .onFailure { e ->
                Log.e(TAG_UI, "기기 식별자(EncryptedPrefs) 초기화 실패 - 안전 모드로 동작: ${e.message}", e)
            }
            .getOrDefault("")
        currentIntent = intent

        // Initialize Recent Deals & Notification History
        com.ddaeany0919.insightdeal.presentation.mypage.history.RecentDealManager.init(this)
        com.ddaeany0919.insightdeal.presentation.mypage.history.NotificationHistoryManager.init(this)

        // 앱 시작 시 FCM 토큰 가져와서 서버에 익명 가입(등록)
        FirebaseMessaging.getInstance().token.addOnCompleteListener { task ->
            if (!task.isSuccessful) {
                Log.w(TAG_UI, "Fetching FCM registration token failed", task.exception)
                return@addOnCompleteListener
            }

            val token = task.result
            Log.d(TAG_UI, "📱 FCM Token 가져오기 성공: ${token.take(20)}...")
            LocalDataProvider.saveFcmToken(token)

            val nightPushConsent = runCatching {
                EncryptedPrefsManager.getEncryptedPrefs(this).getBoolean("night_push_consent", false)
            }.getOrDefault(false)
            val keywordPrefs = runCatching { KeywordPreferencesDataSource() }.getOrNull()
            val dndEnabled = runCatching { keywordPrefs?.getDndEnabled() ?: false }.getOrDefault(false)
            val dndStart = runCatching { keywordPrefs?.getDndStartTime() ?: "21:00" }.getOrDefault("21:00")
            val dndEnd = runCatching { keywordPrefs?.getDndEndTime() ?: "08:00" }.getOrDefault("08:00")
            val dndJson = runCatching { keywordPrefs?.getWeekDndJson() }.getOrNull()

            CoroutineScope(Dispatchers.IO).launch {
                val resolvedDeviceUuid = deviceUserId.ifBlank {
                    runCatching { LocalDataProvider.getDeviceUuid() }.getOrDefault("")
                }
                if (resolvedDeviceUuid.isBlank()) {
                    Log.w(TAG_UI, "기기 식별자 초기화 실패 상태로 FCM 토큰 서버 등록을 건너뜁니다.")
                    return@launch
                }
                try {
                    val apiService = NetworkModule.createService<ApiService>()
                    val request = RegisterDeviceReq(
                        device_uuid = resolvedDeviceUuid,
                        fcm_token = token,
                        night_push_consent = nightPushConsent,
                        dnd_enabled = dndEnabled,
                        dnd_start_time = dndStart,
                        dnd_end_time = dndEnd,
                        dnd_settings_json = dndJson
                    )

                    val response = apiService.registerFCMToken(request)
                    if (response.isSuccessful) {
                        val issuedToken = response.body()?.deviceToken?.trim()?.takeIf { it.isNotBlank() }
                        if (issuedToken != null) {
                            LocalDataProvider.saveDeviceToken(issuedToken, resolvedDeviceUuid)
                        }
                        Log.d(TAG_UI, "✅ FCM Token 서버 등록 (익명 가입) 성공")
                    } else {
                        Log.e(TAG_UI, "❌ FCM Token 서버 등록 실패: ${response.code()}")
                    }
                } catch (e: Exception) {
                    Log.e(TAG_UI, "❌ FCM Token 서버 등록 에러: ${e.message}", e)
                }
            }
        }

        setContent {
            val tm = remember { ThemeManager.getInstance(this) }
            val mode by tm.themeMode.collectAsState()
            val amoledMode by tm.amoledMode.collectAsState()
            val systemDark = isSystemInDarkTheme()
            val useDark = tm.shouldUseDarkTheme(systemDark)
            
            // 보안 앱 잠금 상태 검사 (Keystore 영구 손상 시 비잠금 안전 모드로 동작)
            val context = this
            val prefs = remember {
                runCatching { EncryptedPrefsManager.getEncryptedPrefs(context) }
                    .onFailure { e ->
                        Log.e(TAG_UI, "보안 잠금 EncryptedPrefs 초기화 실패 - 비잠금 안전 모드로 동작: ${e.message}", e)
                    }
                    .getOrNull()
            }
            var correctPin by remember { mutableStateOf(prefs?.getString("app_lock_pin", "") ?: "") }
            var isLocked by remember { mutableStateOf(false) }

            LaunchedEffect(prefs) {
                if (prefs == null) {
                    android.widget.Toast.makeText(
                        context,
                        "보안 저장소 초기화에 실패하여 잠금 해제 안전 모드로 실행됩니다.",
                        android.widget.Toast.LENGTH_LONG
                    ).show()
                }
            }

            // 생명주기 및 백그라운드-포그라운드 전환 시 PIN 및 생체 잠금 정밀 검사
            val lifecycleOwner = androidx.compose.ui.platform.LocalLifecycleOwner.current
            DisposableEffect(lifecycleOwner, prefs) {
                val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
                    if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                        val activePrefs = prefs ?: runCatching {
                            EncryptedPrefsManager.getEncryptedPrefs(context)
                        }.getOrNull()
                        if (activePrefs == null) {
                            isLocked = false
                            return@LifecycleEventObserver
                        }
                        val pinLockEnabled = activePrefs.getBoolean("app_lock_enabled", false)
                        val bioLockEnabled = activePrefs.getBoolean("biometric_lock_enabled", false)
                        val pin = activePrefs.getString("app_lock_pin", "") ?: ""
                        correctPin = pin
                        
                        if (bioLockEnabled) {
                            val biometricManager = androidx.biometric.BiometricManager.from(this@MainActivity)
                            val authenticators = androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG
                            
                            if (biometricManager.canAuthenticate(authenticators) == androidx.biometric.BiometricManager.BIOMETRIC_SUCCESS) {
                                isLocked = true // 일단 잠금
                                val executor = androidx.core.content.ContextCompat.getMainExecutor(this@MainActivity)
                                val biometricPrompt = androidx.biometric.BiometricPrompt(this@MainActivity, executor,
                                    object : androidx.biometric.BiometricPrompt.AuthenticationCallback() {
                                        override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                                            super.onAuthenticationError(errorCode, errString)
                                            // 지문 인증 실패/취소 시 PIN 다이얼로그로 수동 폴백
                                            android.widget.Toast.makeText(this@MainActivity, "생체 인증에 실패했어요. PIN 번호를 입력해 주세요.", android.widget.Toast.LENGTH_SHORT).show()
                                        }
                                        override fun onAuthenticationSucceeded(result: androidx.biometric.BiometricPrompt.AuthenticationResult) {
                                            super.onAuthenticationSucceeded(result)
                                            isLocked = false // 잠금 해제!
                                        }
                                    })
                                
                                val promptInfo = androidx.biometric.BiometricPrompt.PromptInfo.Builder()
                                    .setTitle("앱 보안 잠금 해제")
                                    .setSubtitle("지문 센서에 터치하거나 취소하여 PIN 번호로 해제하세요.")
                                    .setNegativeButtonText("PIN 번호 입력")
                                    .build()
                                
                                biometricPrompt.authenticate(promptInfo)
                            } else {
                                // 하드웨어 미지원 등으로 생체 인증이 불가능할 경우 PIN 잠금으로 자동 폴백
                                if (pin.isNotEmpty()) {
                                    isLocked = true
                                }
                            }
                        } else if (pinLockEnabled && pin.isNotEmpty()) {
                            isLocked = true
                        }
                    }
                }
                lifecycleOwner.lifecycle.addObserver(observer)
                onDispose {
                    lifecycleOwner.lifecycle.removeObserver(observer)
                }
            }

            InsightDealTheme(
                darkTheme = useDark,
                themeMode = mode,
                amoledMode = amoledMode
            ) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    if (isLocked) {
                        PinLockDialog(
                            title = "보안 PIN 번호 입력",
                            subtitle = "개인 정보 보호를 위해 4자리 PIN을 입력하세요.",
                            correctPin = correctPin,
                            isSetupMode = false,
                            onDismiss = {},
                            onSuccess = {
                                isLocked = false
                            }
                        )
                    } else {
                        MainApp(deviceUserId, currentIntent)
                    }
                }
            }
        }
    }
}

@Composable
fun MainApp(deviceUserId: String, currentIntent: android.content.Intent?) {
    val navController = rememberNavController()
    val homeViewModel: HomeViewModel = androidx.lifecycle.viewmodel.compose.viewModel()
    
    // [android-intent-security] 딥링크 및 알림 인텐트 파라미터 검증 및 일회성 소비 처리
    LaunchedEffect(currentIntent) {
        currentIntent?.let { intent ->
            val navigateTo = intent.getStringExtra("navigate_to")?.trim()
            val rawDealId = intent.getStringExtra("deal_id")?.trim()
            val validDealId = rawDealId?.toIntOrNull()?.takeIf { it > 0 }
            when {
                navigateTo == "hotdeal" && validDealId != null -> {
                    Log.d(TAG_UI, "🚀 Deep Linking to deal_detail/$validDealId")
                    intent.removeExtra("navigate_to")
                    intent.removeExtra("deal_id")
                    navController.navigate("deal_detail/$validDealId") {
                        launchSingleTop = true
                    }
                }
                navigateTo == "watchlist" || navigateTo == "price_tracking" -> {
                    Log.d(TAG_UI, "Deep Linking to watchlist (source=$navigateTo)")
                    intent.removeExtra("navigate_to")
                    navController.navigate("watchlist") {
                        launchSingleTop = true
                    }
                }
            }
        }
    }
    
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route?.substringBefore("/")?.substringBefore("?")
    val showBottomNav = currentRoute in BOTTOM_NAV_ROUTES

    Scaffold(
        bottomBar = { 
            if (showBottomNav) {
                BottomNavigationBar(navController, homeViewModel)
            }
        }
    ) { innerPadding ->
        val context = androidx.compose.ui.platform.LocalContext.current.applicationContext
        val wishlistViewModel: WishlistViewModel = androidx.lifecycle.viewmodel.compose.viewModel(
            factory = object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return WishlistViewModel(
                        wishlistRepository = WishlistRepository(context = context),
                        userIdProvider = {
                            runCatching { LocalDataProvider.getDeviceUuid() }.getOrDefault("")
                        }
                    ) as T
                }
            }
        )
        NavHost(navController, startDestination = "home", Modifier.padding(innerPadding).consumeWindowInsets(innerPadding)) {
            composable("home") {
                HomeScreen(navController = navController, viewModel = homeViewModel, wishlistViewModel = wishlistViewModel)
            }
            composable("watchlist") {
                WishlistScreen(
                    viewModel = wishlistViewModel,
                    onBack = { navController.popBackStack() }
                )
            }
            composable("watchlist/detail/{itemId}") { backStackEntry ->
                val itemId = backStackEntry.arguments?.getString("itemId")?.toIntOrNull() ?: -1
                WishlistDetailScreen(itemId = itemId, onBack = { navController.popBackStack() }, viewModel = wishlistViewModel)
            }
            composable(
                route = "advanced_search?query={query}",
                arguments = listOf(
                    androidx.navigation.navArgument("query") {
                        type = androidx.navigation.NavType.StringType
                        defaultValue = ""
                    }
                )
            ) { backStackEntry ->
                val initialQuery = backStackEntry.arguments?.getString("query") ?: ""
                com.ddaeany0919.insightdeal.presentation.search.AdvancedSearchScreen(
                    navController = navController,
                    initialQuery = initialQuery
                )
            }
            composable("category") { com.ddaeany0919.insightdeal.presentation.category.CategoryScreen(navController = navController) }
            composable("category_detail/{categoryName}") { backStackEntry ->
                val encodedCategoryName = backStackEntry.arguments?.getString("categoryName") ?: "기타"
                val categoryName = java.net.URLDecoder.decode(encodedCategoryName, "UTF-8")
                val viewModel: com.ddaeany0919.insightdeal.presentation.home.HomeViewModel = androidx.lifecycle.viewmodel.compose.viewModel()
                com.ddaeany0919.insightdeal.presentation.category.CategoryDetailScreen(
                    categoryName = categoryName,
                    viewModel = viewModel,
                    navController = navController
                )
            }
            composable("community") { com.ddaeany0919.insightdeal.presentation.community.CommunityBoardScreen(navController = navController) }
            composable("community_detail/{postId}") { backStackEntry ->
                val postId = backStackEntry.arguments?.getString("postId")?.toIntOrNull() ?: -1
                com.ddaeany0919.insightdeal.presentation.community.CommunityPostDetailScreen(
                    postId = postId,
                    navController = navController
                )
            }
            composable("community_write") { com.ddaeany0919.insightdeal.presentation.community.WritePostScreen(navController = navController) }
            composable("community_edit/{postId}") { backStackEntry ->
                val postId = backStackEntry.arguments?.getString("postId")?.toIntOrNull()
                com.ddaeany0919.insightdeal.presentation.community.WritePostScreen(navController = navController, postId = postId)
            }
            composable("alerts") { com.ddaeany0919.insightdeal.presentation.alerts.AlertsScreen(deviceUserId = deviceUserId) }
            composable("mypage") { 
                com.ddaeany0919.insightdeal.presentation.mypage.MyPageScreen(
                    onNavigateToSettings = { navController.navigate("settings") },
                    onNavigateToWatchlist = { navController.navigate("watchlist") },
                    onNavigateToMyPosts = { navController.navigate("my_posts") },
                    onNavigateToMyComments = { navController.navigate("my_comments") },
                    onNavigateToRecentDeals = { navController.navigate("recent_deals") },
                    navController = navController
                ) 
            }
            composable("my_posts") {
                com.ddaeany0919.insightdeal.presentation.mypage.history.MyPostsScreen(navController = navController, onBack = { navController.popBackStack() })
            }
            composable("my_comments") {
                com.ddaeany0919.insightdeal.presentation.mypage.history.MyCommentsScreen(navController = navController, onBack = { navController.popBackStack() })
            }
            composable("recent_deals") {
                val activityContext = androidx.compose.ui.platform.LocalContext.current
                com.ddaeany0919.insightdeal.presentation.mypage.history.RecentDealsScreen(
                    onBack = { navController.popBackStack() },
                    onDealClick = { dealId -> navController.navigate("deal_detail/$dealId") },
                    onOpenUrl = { url ->
                        val finalUrl = if (url.startsWith("http")) url else "https://$url"
                        homeViewModel.trackOutboundClick(url = finalUrl)
                        com.ddaeany0919.insightdeal.utils.CustomTabLauncher.launchShoppingUrl(activityContext, finalUrl)
                    }
                )
            }
            composable("recommendations") {
                com.ddaeany0919.insightdeal.presentation.recommendation.RecommendationScreen(
                    onDealClick = { deal -> navController.navigate("deal_detail/${deal.id}") },
                    onBack = { navController.popBackStack() }
                )
            }
            composable("settings") {
                com.ddaeany0919.insightdeal.presentation.settings.SettingsScreen(
                    onBackClick = { navController.popBackStack() },
                    onNavigateToKeywordManager = { navController.navigate("keyword_alarm") }
                )
            }
            composable("keyword_alarm") {
                com.ddaeany0919.insightdeal.presentation.KeywordManagerScreen(
                    onNavigateBack = { navController.popBackStack() }
                )
            }
            composable("platform") { com.ddaeany0919.insightdeal.presentation.platform.PlatformScreen() }
            composable("deal_detail/{dealId}") { backStackEntry ->
                val activityContext = androidx.compose.ui.platform.LocalContext.current
                val dealId = backStackEntry.arguments?.getString("dealId")?.toIntOrNull() ?: -1
                val viewModel: com.ddaeany0919.insightdeal.presentation.DealDetailViewModel = androidx.lifecycle.viewmodel.compose.viewModel()
                com.ddaeany0919.insightdeal.presentation.DealDetailRoute(
                    dealId = dealId,
                    viewModel = viewModel,
                    wishlistViewModel = wishlistViewModel,
                    onBack = { navController.popBackStack() },
                    onOpenUrl = { url ->
                        com.ddaeany0919.insightdeal.utils.CustomTabLauncher.launchShoppingUrl(activityContext, url)
                    },
                    onNavigateToCommunity = {
                        navController.navigate("community")
                    },
                    onNavigateToWatchlist = {
                        navController.navigate("watchlist")
                    }
                )
            }
            composable(
                route = "webview/{url}",
                arguments = listOf(androidx.navigation.navArgument("url") { type = androidx.navigation.NavType.StringType })
            ) { backStackEntry ->
                val encodedUrl = backStackEntry.arguments?.getString("url") ?: ""
                val url = java.net.URLDecoder.decode(encodedUrl, "UTF-8")
                com.ddaeany0919.insightdeal.presentation.webview.DealWebViewScreen(
                    url = url,
                    onBack = { navController.popBackStack() }
                )
            }
        }
    }
}

@Composable
fun BottomNavigationBar(navController: androidx.navigation.NavController, homeViewModel: HomeViewModel? = null) {
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route?.substringBefore("/")?.substringBefore("?")

    Column(modifier = Modifier.fillMaxWidth()) {
        HorizontalDivider(
            thickness = 0.8.dp,
            color = MaterialTheme.colorScheme.outline.copy(alpha = 0.7f)
        )
        NavigationBar(
            containerColor = MaterialTheme.colorScheme.surface,
            tonalElevation = 0.dp
        ) {
            NAVIGATION_ITEMS.forEach { item ->
                val selected = when (item.route) {
                    "category" -> currentRoute == "category" || currentRoute == "category_detail"
                    "community" -> currentRoute == "community" || currentRoute == "community_write" || currentRoute == "community_edit" || currentRoute == "community_detail"
                    "mypage" -> currentRoute == "mypage" || currentRoute == "watchlist" || currentRoute == "my_posts" || currentRoute == "my_comments" || currentRoute == "recent_deals" || currentRoute == "settings"
                    else -> currentRoute == item.route
                }

                NavigationBarItem(
                    icon = {
                        Icon(
                            imageVector = if (selected) item.selectedIcon else item.unselectedIcon,
                            contentDescription = item.description,
                            modifier = Modifier.size(22.dp)
                        )
                    },
                    label = {
                        Text(
                            text = item.title,
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                            style = MaterialTheme.typography.labelSmall
                        )
                    },
                    selected = selected,
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = MaterialTheme.colorScheme.primary,
                        selectedTextColor = MaterialTheme.colorScheme.primary,
                        indicatorColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.10f),
                        unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f),
                        unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f)
                    ),
                    onClick = {
                        if (item.route == "home") {
                            homeViewModel?.selectCategory("전체")
                            navController.navigate("home") {
                                popUpTo(navController.graph.id) {
                                    inclusive = true
                                }
                                launchSingleTop = true
                            }
                        } else {
                            if (selected) {
                                navController.popBackStack(item.route, inclusive = false)
                            } else {
                                navController.navigate(item.route) {
                                    popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            }
                        }
                    }
                )
            }
        }
    }
}

@androidx.compose.runtime.Immutable
data class BottomNavItem(
    val route: String,
    val title: String,
    val unselectedIcon: androidx.compose.ui.graphics.vector.ImageVector,
    val selectedIcon: androidx.compose.ui.graphics.vector.ImageVector,
    val description: String
)

package com.ddaeany0919.insightdeal

import android.content.Context
import android.os.Bundle
import android.provider.Settings
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
import com.ddaeany0919.insightdeal.presentation.settings.ThemeSettingsScreenCollapsible
import com.ddaeany0919.insightdeal.presentation.home.HomeScreen
import com.ddaeany0919.insightdeal.presentation.home.HomeViewModel
import com.ddaeany0919.insightdeal.presentation.theme.InsightDealTheme
import com.ddaeany0919.insightdeal.presentation.components.PinLockDialog
import com.ddaeany0919.insightdeal.presentation.mypage.history.NotificationHistoryManager
import com.ddaeany0919.insightdeal.core.security.EncryptedPrefsManager
import java.util.UUID
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
import androidx.lifecycle.lifecycleScope
import com.ddaeany0919.insightdeal.presentation.auth.dataStore
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.firstOrNull

private const val TAG_UI = "MainActivity"

fun generateDeviceUserId(context: Context): String {
    return try {
        val androidId = Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)
        if (androidId.isNullOrBlank() || androidId == "9774d56d682e549c") "device_${UUID.randomUUID().toString().take(12)}" else "device_${androidId.take(12)}"
    } catch (e: Exception) {
        Log.w(TAG_UI, "Failed to get device ID, using random", e); "device_${UUID.randomUUID().toString().take(12)}"
    }
}

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
        currentIntent = intent
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        // SplashScreen 적용
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val deviceUserId = generateDeviceUserId(this)
        currentIntent = intent
        
        // ⚡ [자동로그인 가드] 최초 기동 시 자동로그인이 꺼져있다면 세션을 안전하게 guest로 청소
        lifecycleScope.launch {
            val prefs = this@MainActivity.dataStore.data.firstOrNull()
            if (prefs != null) {
                val autoLogin = prefs[booleanPreferencesKey("auto_login_enabled")] ?: true
                if (!autoLogin) {
                    this@MainActivity.dataStore.edit { editPrefs ->
                        editPrefs[stringPreferencesKey("username")] = "guest"
                        editPrefs[stringPreferencesKey("nickname")] = "guest"
                    }
                    Log.d("AuthGuard", "AutoLogin is disabled. Cleaned session to guest on Cold Start.")
                }
            }
        }
        
        // Android 13 이상에서 알림 권한 요청
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }

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

            val prefs = EncryptedPrefsManager.getEncryptedPrefs(this)
            val nightPushConsent = prefs.getBoolean("night_push_consent", false)
            
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val apiService = NetworkModule.createService<ApiService>()
                    val request = RegisterDeviceReq(
                        device_uuid = deviceUserId,
                        fcm_token = token,
                        night_push_consent = nightPushConsent
                    )
                    
                    val response = apiService.registerFCMToken(request)
                    if (response.isSuccessful) {
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
            
            // 보안 앱 잠금 상태 검사
            val context = this
            val prefs = remember { EncryptedPrefsManager.getEncryptedPrefs(context) }
            var correctPin by remember { mutableStateOf(prefs.getString("app_lock_pin", "") ?: "") }
            var isLocked by remember { mutableStateOf(false) }

            // 생명주기 및 백그라운드-포그라운드 전환 시 PIN 및 생체 잠금 정밀 검사
            val lifecycleOwner = androidx.compose.ui.platform.LocalLifecycleOwner.current
            DisposableEffect(lifecycleOwner) {
                val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
                    if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                        val pinLockEnabled = prefs.getBoolean("app_lock_enabled", false)
                        val bioLockEnabled = prefs.getBoolean("biometric_lock_enabled", false)
                        val pin = prefs.getString("app_lock_pin", "") ?: ""
                        correctPin = pin
                        
                        if (bioLockEnabled) {
                            val biometricManager = androidx.biometric.BiometricManager.from(context)
                            val authenticators = androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG
                            
                            if (biometricManager.canAuthenticate(authenticators) == androidx.biometric.BiometricManager.BIOMETRIC_SUCCESS) {
                                isLocked = true // 일단 잠금
                                val executor = androidx.core.content.ContextCompat.getMainExecutor(context)
                                val biometricPrompt = androidx.biometric.BiometricPrompt(context, executor,
                                    object : androidx.biometric.BiometricPrompt.AuthenticationCallback() {
                                        override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                                            super.onAuthenticationError(errorCode, errString)
                                            // 지문 인증 실패/취소 시 PIN 다이얼로그로 수동 폴백
                                            android.widget.Toast.makeText(context, "생체 인증 실패: PIN으로 잠금을 해제해 주세요. 🔒", android.widget.Toast.LENGTH_SHORT).show()
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
    
    LaunchedEffect(currentIntent) {
        currentIntent?.let { intent ->
            val navigateTo = intent.getStringExtra("navigate_to")
            val dealId = intent.getStringExtra("deal_id")
            if (navigateTo == "hotdeal" && dealId != null) {
                Log.d(TAG_UI, "🚀 Deep Linking to deal_detail/$dealId")
                navController.navigate("deal_detail/$dealId")
            }
        }
    }
    
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route?.substringBefore("/")
    val showBottomNav = currentRoute in listOf("home", "advanced_search", "category", "category_detail", "community", "mypage", "watchlist", "my_posts", "my_comments", "recent_deals", "settings", "theme_settings")

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
                        userIdProvider = { deviceUserId }
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
            composable("advanced_search") { com.ddaeany0919.insightdeal.presentation.search.AdvancedSearchScreen(navController) }
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
                    onNavigateToAuth = { navController.navigate("auth") },
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
                com.ddaeany0919.insightdeal.presentation.mypage.history.RecentDealsScreen(
                    onBack = { navController.popBackStack() },
                    onDealClick = { dealId -> navController.navigate("deal_detail/$dealId") }
                )
            }
            composable("settings") {
                com.ddaeany0919.insightdeal.presentation.settings.SettingsScreen(
                    onBackClick = { navController.popBackStack() },
                    onNavigateToKeywordManager = { navController.navigate("keyword_alarm") },
                    onNavigateToAuth = { navController.navigate("auth") }
                )
            }
            composable("auth") {
                com.ddaeany0919.insightdeal.presentation.auth.AuthScreen(
                    onBackClick = { navController.popBackStack() }
                )
            }
            composable("keyword_alarm") {
                com.ddaeany0919.insightdeal.presentation.KeywordManagerScreen(
                    onNavigateBack = { navController.popBackStack() }
                )
            }
            composable("platform") { com.ddaeany0919.insightdeal.presentation.platform.PlatformScreen() }
            composable("deal_detail/{dealId}") { backStackEntry ->
                val dealId = backStackEntry.arguments?.getString("dealId")?.toIntOrNull() ?: -1
                val viewModel: com.ddaeany0919.insightdeal.presentation.DealDetailViewModel = androidx.lifecycle.viewmodel.compose.viewModel()
                com.ddaeany0919.insightdeal.presentation.DealDetailRoute(
                    dealId = dealId,
                    viewModel = viewModel,
                    wishlistViewModel = wishlistViewModel,
                    onBack = { navController.popBackStack() },
                    onOpenUrl = { url ->
                        val encodedUrl = java.net.URLEncoder.encode(url, "UTF-8")
                        navController.navigate("webview/$encodedUrl")
                    },
                    onNavigateToCommunity = {
                        navController.navigate("community")
                    },
                    onNavigateToWatchlist = {
                        navController.navigate("watchlist")
                    }
                )
            }
            composable("product_detail/{productId}") { Box { Text("상품 상세 화면") } }
            composable("theme_settings") { com.ddaeany0919.insightdeal.presentation.settings.ThemeSettingsScreen(onBackClick = { navController.popBackStack() }) }
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
    val currentRoute = navBackStackEntry?.destination?.route?.substringBefore("/")
    
    val navigationItems = listOf(
        BottomNavItem("home", "홈", Icons.Default.Home, "홈"),
        BottomNavItem("advanced_search", "검색", Icons.Default.Search, "검색"),
        BottomNavItem("category", "카테고리", Icons.Default.Dashboard, "카테고리"),
        BottomNavItem("community", "커뮤니티", Icons.Default.Forum, "커뮤니티"),
        BottomNavItem("mypage", "내정보", Icons.Default.Person, "내정보 (마이페이지)")
    )
    NavigationBar {
        navigationItems.forEach { item ->
            val selected = when (item.route) {
                "category" -> currentRoute == "category" || currentRoute == "category_detail"
                "community" -> currentRoute == "community" || currentRoute == "community_write" || currentRoute == "community_edit" || currentRoute == "community_detail"
                "mypage" -> currentRoute == "mypage" || currentRoute == "watchlist" || currentRoute == "my_posts" || currentRoute == "my_comments" || currentRoute == "recent_deals" || currentRoute == "settings" || currentRoute == "theme_settings"
                else -> currentRoute == item.route
            }
            
            NavigationBarItem(
                icon = { 
                    Icon(
                        item.icon, 
                        contentDescription = item.description
                    ) 
                },
                label = { Text(item.title) },
                selected = selected,
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

data class BottomNavItem(val route: String, val title: String, val icon: androidx.compose.ui.graphics.vector.ImageVector, val description: String)

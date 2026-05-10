package com.ddaeany0919.insightdeal

import android.content.Context
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.Manifest
import android.os.Build
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.ComponentActivity
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

private const val TAG_UI = "MainActivity"

fun generateDeviceUserId(context: Context): String {
    return try {
        val androidId = Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)
        if (androidId.isNullOrBlank() || androidId == "9774d56d682e549c") "device_${UUID.randomUUID().toString().take(12)}" else "device_${androidId.take(12)}"
    } catch (e: Exception) {
        Log.w(TAG_UI, "Failed to get device ID, using random", e); "device_${UUID.randomUUID().toString().take(12)}"
    }
}

class MainActivity : ComponentActivity() {
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
        
        // Android 13 이상에서 알림 권한 요청
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }

        // 앱 시작 시 FCM 토큰 가져와서 서버에 익명 가입(등록)
        FirebaseMessaging.getInstance().token.addOnCompleteListener { task ->
            if (!task.isSuccessful) {
                Log.w(TAG_UI, "Fetching FCM registration token failed", task.exception)
                return@addOnCompleteListener
            }

            val token = task.result
            Log.d(TAG_UI, "📱 FCM Token 가져오기 성공: ${token.take(20)}...")

            val prefs = getSharedPreferences("insight_deal_prefs", Context.MODE_PRIVATE)
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
            
            InsightDealTheme(
                darkTheme = useDark,
                themeMode = mode,
                amoledMode = amoledMode
            ) {
                MainApp(deviceUserId, currentIntent)
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
    
    Scaffold(bottomBar = { BottomNavigationBar(navController, homeViewModel) }) { innerPadding ->
        NavHost(navController, startDestination = "home", Modifier.padding(innerPadding)) {
            composable("home") {
                HomeScreen(navController = navController, viewModel = homeViewModel)
            }
            composable("watchlist") {
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
                WishlistScreen(
                    viewModel = wishlistViewModel
                )
            }
            composable("watchlist/detail/{itemId}") { backStackEntry ->
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
                val itemId = backStackEntry.arguments?.getString("itemId")?.toIntOrNull() ?: -1
                WishlistDetailScreen(itemId = itemId, onBack = { navController.popBackStack() }, viewModel = wishlistViewModel)
            }
            composable("advanced_search") { com.ddaeany0919.insightdeal.presentation.search.AdvancedSearchScreen(navController) }
            composable("category") { com.ddaeany0919.insightdeal.presentation.category.CategoryScreen(navController = navController, homeViewModel = homeViewModel) }
            composable("community") { com.ddaeany0919.insightdeal.presentation.community.CommunityScreen() }
            composable("alerts") { com.ddaeany0919.insightdeal.presentation.alerts.AlertsScreen(deviceUserId = deviceUserId) }
            composable("mypage") { 
                com.ddaeany0919.insightdeal.presentation.mypage.MyPageScreen(
                    onNavigateToSettings = { navController.navigate("settings") },
                    onNavigateToWatchlist = { navController.navigate("watchlist") }
                ) 
            }
            composable("settings") { com.ddaeany0919.insightdeal.presentation.settings.SettingsScreen() }
            composable("platform") { com.ddaeany0919.insightdeal.presentation.platform.PlatformScreen() }
            composable("deal_detail/{dealId}") { backStackEntry ->
                val dealId = backStackEntry.arguments?.getString("dealId")?.toIntOrNull() ?: -1
                val viewModel: com.ddaeany0919.insightdeal.presentation.DealDetailViewModel = androidx.lifecycle.viewmodel.compose.viewModel()
                val localContext = androidx.compose.ui.platform.LocalContext.current
                com.ddaeany0919.insightdeal.presentation.DealDetailRoute(
                    dealId = dealId,
                    viewModel = viewModel,
                    onBack = { navController.popBackStack() },
                    onOpenUrl = { url ->
                        val encodedUrl = java.net.URLEncoder.encode(url, "UTF-8")
                        navController.navigate("webview/$encodedUrl")
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
    val currentDestination = navBackStackEntry?.destination
    val navigationItems = listOf(
        BottomNavItem("home", "홈", Icons.Default.Home, "홈"),
        BottomNavItem("advanced_search", "검색", Icons.Default.Search, "검색"),
        BottomNavItem("category", "카테고리", Icons.Default.Dashboard, "카테고리"),
        BottomNavItem("community", "커뮤니티", Icons.Default.Forum, "커뮤니티"),
        BottomNavItem("mypage", "내정보", Icons.Default.Person, "내정보 (마이페이지)")
    )
    NavigationBar {
        navigationItems.forEach { item ->
            val selected = currentDestination?.hierarchy?.any { dest -> dest.route == item.route } == true
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
                        // 홈 진입 시 무조건 백스택 날려서 검색화면 등 닫기
                        homeViewModel?.selectCategory("전체")
                        navController.navigate("home") {
                            popUpTo(navController.graph.id) {
                                inclusive = true
                            }
                            launchSingleTop = true
                        }
                    } else {
                        navController.navigate(item.route) {
                            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    }
                }
            )
        }
    }
}

data class BottomNavItem(val route: String, val title: String, val icon: androidx.compose.ui.graphics.vector.ImageVector, val description: String)

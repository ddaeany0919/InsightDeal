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
import com.ddaeany0919.insightdeal.ui.home.HomeScreen
import com.ddaeany0919.insightdeal.ui.home.HomeViewModel
import com.ddaeany0919.insightdeal.ui.theme.InsightDealTheme
import java.util.UUID
import androidx.compose.foundation.clickable

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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val deviceUserId = generateDeviceUserId(this)
        
        // Android 13 이상에서 알림 권한 요청
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
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
                MainApp(deviceUserId)
            }
        }
    }
}

@Composable
fun MainApp(deviceUserId: String) {
    val navController = rememberNavController()
    Scaffold(bottomBar = { BottomNavigationBar(navController) }) { innerPadding ->
        NavHost(navController, startDestination = "home", Modifier.padding(innerPadding)) {
            composable("home") {
                HomeScreen(navController = navController)
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
            composable("platform") { com.ddaeany0919.insightdeal.presentation.platform.PlatformScreen() }
            composable("settings") { com.ddaeany0919.insightdeal.presentation.settings.SettingsScreen() }
            composable("advanced_search") { com.ddaeany0919.insightdeal.presentation.search.AdvancedSearchScreen(navController) }
            composable("deal_detail/{dealId}") { Box { Text("핫딜 상세 화면") } }
            composable("product_detail/{productId}") { Box { Text("상품 상세 화면") } }
            composable("theme_settings") { com.ddaeany0919.insightdeal.presentation.settings.ThemeSettingsScreen(onBackClick = { navController.popBackStack() }) }
        }
    }
}

@Composable
fun BottomNavigationBar(navController: androidx.navigation.NavController) {
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination
    val navigationItems = listOf(
        BottomNavItem("home", "홈", Icons.Default.Home, "홈"),
        BottomNavItem("watchlist", "관심", Icons.Default.FavoriteBorder, "관심 위시리스트"),
        BottomNavItem("platform", "플랫폼", Icons.Default.Forum, "출처별 플랫폼 모아보기"),
        BottomNavItem("settings", "설정", Icons.Default.Settings, "설정")
    )
    NavigationBar {
        navigationItems.forEach { item ->
            val selected = currentDestination?.hierarchy?.any { dest -> dest.route == item.route } == true
            NavigationBarItem(
                icon = { 
                    Icon(
                        if (item.route == "watchlist" && selected) Icons.Default.Favorite 
                        else if (item.route == "platform" && selected) Icons.Default.Forum
                        else item.icon, 
                        contentDescription = item.description
                    ) 
                },
                label = { Text(item.title) },
                selected = selected,
                onClick = {
                    if (item.route == "home") {
                        // 홈 진입 시 무조건 백스택 날려서 검색화면 등 닫기
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

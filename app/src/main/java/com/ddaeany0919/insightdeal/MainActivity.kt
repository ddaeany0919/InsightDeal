package com.ddaeany0919.insightdeal

import android.content.Context
import android.os.Bundle
import android.provider.Settings
import android.util.Log
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
import com.ddaeany0919.insightdeal.data.theme.ThemeManager
import com.ddaeany0919.insightdeal.data.theme.ThemePreferences
import com.ddaeany0919.insightdeal.presentation.wishlist.*
import com.ddaeany0919.insightdeal.settings.ThemeSettingsScreenCollapsible
import com.ddaeany0919.insightdeal.ui.EnhancedHomeScreen_Applied
import com.ddaeany0919.insightdeal.ui.HomeViewModel
import com.ddaeany0919.insightdeal.ui.theme.InsightDealTheme
import java.util.UUID
import com.ddaeany0919.insightdeal.presentation.wishlist.WishlistDetailScreen
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
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val deviceUserId = generateDeviceUserId(this)
        setContent {
            val tm = remember { ThemeManager.getInstance(this) }
            val mode by tm.modeFlow.collectAsState(initial = ThemePreferences.Mode.SYSTEM)
            val dark = when (mode) {
                ThemePreferences.Mode.LIGHT -> false
                ThemePreferences.Mode.DARK, ThemePreferences.Mode.AMOLED -> true
                ThemePreferences.Mode.SYSTEM -> isSystemInDarkTheme()
            }
            val amoled = mode == ThemePreferences.Mode.AMOLED
            InsightDealTheme(darkTheme = dark, themeMode = if (amoled) ThemeMode.AMOLED else if (dark) ThemeMode.DARK else ThemeMode.LIGHT, amoledMode = amoled) {
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
                val vm: HomeViewModel = androidx.lifecycle.viewmodel.compose.viewModel()
                EnhancedHomeScreen_Applied(vm, onDealClick = {}, onBookmarkClick = {}, onTrackClick = {})
            }
            composable("watchlist") {
                val wishlistViewModel: WishlistViewModel = androidx.lifecycle.viewmodel.compose.viewModel(
                    factory = object : ViewModelProvider.Factory {
                        @Suppress("UNCHECKED_CAST")
                        override fun <T : ViewModel> create(modelClass: Class<T>): T {
                            return WishlistViewModel(
                                wishlistRepository = WishlistRepository(),
                                userIdProvider = { deviceUserId }
                            ) as T
                        }
                    }
                )
                WatchlistScreen(
                    viewModel = wishlistViewModel,
                    onItemClick = { wishlistItem ->
                        navController.navigate("watchlist/detail/${wishlistItem.id}")
                    }
                )
            }
            composable("watchlist/detail/{itemId}") { backStackEntry ->
                val wishlistViewModel: WishlistViewModel = androidx.lifecycle.viewmodel.compose.viewModel(
                    factory = object : ViewModelProvider.Factory {
                        @Suppress("UNCHECKED_CAST")
                        override fun <T : ViewModel> create(modelClass: Class<T>): T {
                            return WishlistViewModel(
                                wishlistRepository = WishlistRepository(),
                                userIdProvider = { deviceUserId }
                            ) as T
                        }
                    }
                )
                val itemId = backStackEntry.arguments?.getString("itemId")?.toIntOrNull() ?: -1
                WishlistDetailScreen(itemId = itemId, viewModel = wishlistViewModel)
            }
            composable("matches") { MatchesScreen() }
            composable("settings") { ThemeSettingsScreenCollapsible() }
            composable("deal_detail/{dealId}") { Box { Text("딜 상세 화면") } }
            composable("product_detail/{productId}") { Box { Text("상품 상세 화면") } }
            composable("theme_settings") { ThemeSettingsScreenCollapsible() }
        }
    }
}

@Composable
fun BottomNavigationBar(navController: androidx.navigation.NavController) {
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination
    val navigationItems = listOf(
        BottomNavItem("home", "발견", Icons.Default.Home, "커뮤니티 딜 피드"),
        BottomNavItem("watchlist", "추적", Icons.Default.BookmarkBorder, "내 위시리스트"),
        BottomNavItem("matches", "매칭", Icons.Default.Notifications, "AI가 찾은 딜"),
        BottomNavItem("settings", "설정", Icons.Default.Settings, "테마 설정")
    )
    NavigationBar {
        navigationItems.forEach { item ->
            val selected = currentDestination?.hierarchy?.any { dest -> dest.route == item.route } == true
            NavigationBarItem(
                icon = { Icon(if (item.route == "watchlist" && selected) Icons.Default.Bookmark else item.icon, contentDescription = item.description) },
                label = { Text(item.title) },
                selected = selected,
                onClick = {
                    navController.navigate(item.route) {
                        popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                        launchSingleTop = true
                        restoreState = true
                    }
                }
            )
        }
    }
}

data class BottomNavItem(val route: String, val title: String, val icon: androidx.compose.ui.graphics.vector.ImageVector, val description: String)

@Composable fun MatchesScreen() { /* 기존 코드 그대로 */ }

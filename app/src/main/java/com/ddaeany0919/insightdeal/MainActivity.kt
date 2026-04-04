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
import com.ddaeany0919.insightdeal.presentation.theme.ThemeManager
import com.ddaeany0919.insightdeal.presentation.theme.ThemeMode
import com.ddaeany0919.insightdeal.presentation.wishlist.*
import com.ddaeany0919.insightdeal.presentation.settings.ThemeSettingsScreenCollapsible
import com.ddaeany0919.insightdeal.feature.home.HomeScreen
import com.ddaeany0919.insightdeal.feature.home.HomeViewModel
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
                val vm: HomeViewModel = androidx.lifecycle.viewmodel.compose.viewModel()
                HomeScreen(vm, onDealClick = {}, onBookmarkClick = {}, onTrackClick = {})
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
                WishlistDetailScreen(itemId = itemId, onBack = { navController.popBackStack() }, viewModel = wishlistViewModel)
            }
            composable("community") { com.ddaeany0919.insightdeal.presentation.community.CommunityScreen() }
            composable("settings") { com.ddaeany0919.insightdeal.presentation.settings.SettingsScreen() }
            composable("deal_detail/{dealId}") { Box { Text("???ì„¸ ?”ë©´") } }
            composable("product_detail/{productId}") { Box { Text("?í’ˆ ?ì„¸ ?”ë©´") } }
            composable("theme_settings") { com.ddaeany0919.insightdeal.presentation.settings.ThemeSettingsScreen(onBackClick = { navController.popBackStack() }) }
        }
    }
}

@Composable
fun BottomNavigationBar(navController: androidx.navigation.NavController) {
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination
    val navigationItems = listOf(
        BottomNavItem("home", "??, Icons.Default.Home, "??),
        BottomNavItem("watchlist", "ê´€??, Icons.Default.FavoriteBorder, "???„ì‹œë¦¬ìŠ¤??),
        BottomNavItem("community", "ì»¤ë??ˆí‹°", Icons.Default.Forum, "ì»¤ë??ˆí‹° ?«ë”œ"),
        BottomNavItem("settings", "?¤ì •", Icons.Default.Settings, "?¤ì •")
    )
    NavigationBar {
        navigationItems.forEach { item ->
            val selected = currentDestination?.hierarchy?.any { dest -> dest.route == item.route } == true
            NavigationBarItem(
                icon = { 
                    Icon(
                        if (item.route == "watchlist" && selected) Icons.Default.Favorite 
                        else if (item.route == "community" && selected) Icons.Default.Forum
                        else item.icon, 
                        contentDescription = item.description
                    ) 
                },
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

@Composable fun MatchesScreen() { /* ê¸°ì¡´ ì½”ë“œ ê·¸ë?ë¡?*/ }

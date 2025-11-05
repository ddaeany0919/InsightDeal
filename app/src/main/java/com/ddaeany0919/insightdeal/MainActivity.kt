package com.ddaeany0919.insightdeal

import android.content.Context
import android.os.Bundle
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.ddaeany0919.insightdeal.data.theme.ThemeManager
import com.ddaeany0919.insightdeal.data.theme.ThemePreferences
import com.ddaeany0919.insightdeal.presentation.wishlist.*
import com.ddaeany0919.insightdeal.ui.EnhancedHomeScreen_Applied
import com.ddaeany0919.insightdeal.ui.HomeViewModel
import com.ddaeany0919.insightdeal.ui.theme.InsightDealTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val tm = remember { ThemeManager.getInstance(this) }
            val mode by tm.modeFlow.collectAsState(initial = ThemePreferences.Mode.SYSTEM)
            val dark = when (mode) {
                ThemePreferences.Mode.LIGHT -> false
                ThemePreferences.Mode.DARK -> true
                ThemePreferences.Mode.AMOLED -> true
                ThemePreferences.Mode.SYSTEM -> isSystemInDarkTheme()
            }
            val amoled = mode == ThemePreferences.Mode.AMOLED
            InsightDealTheme(
                darkTheme = dark,
                themeMode = if (amoled) ThemeMode.AMOLED else if (dark) ThemeMode.DARK else ThemeMode.LIGHT,
                amoledMode = amoled
            ) { MainApp() }
        }
    }
}

@Composable
fun MainApp() {
    val navController = rememberNavController()

    Scaffold(
        bottomBar = { BottomNavigationBar(navController = navController) }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = "home",
            modifier = Modifier.padding(innerPadding)
        ) {
            composable("home") {
                val vm: HomeViewModel = androidx.lifecycle.viewmodel.compose.viewModel()
                EnhancedHomeScreen_Applied(
                    viewModel = vm,
                    onDealClick = { /* TODO */ },
                    onBookmarkClick = { /* TODO */ },
                    onTrackClick = { /* TODO */ }
                )
            }
            composable("watchlist") {
                val context = LocalContext.current
                val wishlistViewModel: WishlistViewModel = viewModel(
                    factory = object : ViewModelProvider.Factory {
                        @Suppress("UNCHECKED_CAST")
                        override fun <T : ViewModel> create(modelClass: Class<T>): T {
                            val userIdProvider = {
                                // TODO: 실제 로그인 연동으로 교체. 임시로 SharedPreferences 사용.
                                val prefs = context.getSharedPreferences("app", Context.MODE_PRIVATE)
                                prefs.getString("user_id", null) ?: "guest"
                            }
                            return WishlistViewModel(
                                wishlistRepository = WishlistRepository(),
                                userIdProvider = userIdProvider
                            ) as T
                        }
                    }
                )
                WishlistScreenDetailed(viewModel = wishlistViewModel)
            }
            composable("matches") { MatchesScreen() }
            composable("settings") { com.ddaeany0919.insightdeal.settings.ThemeSettingsScreen() }
            composable("deal_detail/{dealId}") { Box { Text("딜 상세 화면") } }
            composable("product_detail/{productId}") { Box { Text("상품 상세 화면") } }
            composable("theme_settings") { com.ddaeany0919.insightdeal.settings.ThemeSettingsScreen() }
            composable("advanced_search") {
                AdvancedSearchScreen(
                    onDealClick = { dealItem -> navController.navigate("price_graph/${dealItem.id}") },
                    onBackClick = { navController.popBackStack() }
                )
            }
            composable("bookmarks") {
                BookmarkScreen(
                    onDealClick = { dealItem -> navController.navigate("price_graph/${dealItem.id}") },
                    onBackClick = { navController.popBackStack() }
                )
            }
            composable("recommendations") {
                RecommendationScreen(
                    onDealClick = { dealItem -> navController.navigate("price_graph/${dealItem.id}") }
                )
            }
            composable("price_graph/{dealId}") { backStackEntry ->
                val dealId = backStackEntry.arguments?.getString("dealId")?.toIntOrNull() ?: 0
                PriceGraphScreen(productId = dealId, onBackClick = { navController.popBackStack() })
            }
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
        BottomNavItem("settings", "설정", Icons.Default.Settings, "알림 및 테마")
    )

    NavigationBar {
        navigationItems.forEach { item ->
            NavigationBarItem(
                icon = {
                    Icon(
                        imageVector = if (item.route == "watchlist" && currentDestination?.hierarchy?.any { it.route == item.route } == true) {
                            Icons.Default.Bookmark
                        } else { item.icon },
                        contentDescription = item.description
                    )
                },
                label = { Text(item.title) },
                selected = currentDestination?.hierarchy?.any { it.route == item.route } == true,
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

data class BottomNavItem(
    val route: String,
    val title: String,
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
    val description: String
)

@Composable
fun MatchesScreen() { /* unchanged content */
    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = Icons.Default.Notifications,
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "🎯 AI 매칭 시스템",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "추적 중인 상품과 커뮤니티 딜을\n자동으로 매칭해서 알려드려요!",
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
        )
        Spacer(modifier = Modifier.height(24.dp))
        Text(
            text = "내일 구현 예정 (Day 4)",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary
        )
    }
}

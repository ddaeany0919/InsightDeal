package com.ddaeany0919.insightdeal

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.ddaeany0919.insightdeal.ui.EnhancedHomeScreen

/**
 * 🏠 InsightDeal 메인 액티비티
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            InsightDealTheme {
                MainApp()
            }
        }
    }
}

@Composable
fun MainApp() {
    val navController = rememberNavController()
    
    Scaffold(
        bottomBar = {
            BottomNavigationBar(navController = navController)
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = "home",
            modifier = Modifier.padding(innerPadding)
        ) {
            // 🏠 홈 - 커뮤니티 딜 피드 (Enhanced)
            composable("home") {
                EnhancedHomeScreen(
                    onDealClick = { /* TODO: 상세 진입 */ },
                    onBookmarkClick = { /* TODO: 북마크 토글 */ },
                    onTrackClick = { /* TODO: 추적 추가 */ }
                )
            }
            
            // 📋 추적 - 내 위시리스트
            composable("watchlist") {
                WatchlistScreen(navController = navController)
            }
            
            // 🎯 매칭 - AI가 찾은 딜들
            composable("matches") {
                MatchesScreen(navController = navController)
            }
            
            // ⚙️ 설정
            composable("settings") {
                ThemeSettingsScreen(navController = navController)
            }
            
            // 상세 화면들 (TODO 보완 예정)
            composable("deal_detail/{dealId}") { Box { Text("딜 상세 화면") } }
            composable("product_detail/{productId}") { Box { Text("상품 상세 화면") } }
            composable("theme_settings") { ThemeSettingsScreen(navController = navController) }
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
                PriceGraphScreen(
                    productId = dealId,
                    onBackClick = { navController.popBackStack() }
                )
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
                        imageVector = if (item.route == "watchlist" && 
                            currentDestination?.hierarchy?.any { it.route == item.route } == true) {
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
fun MatchesScreen(navController: androidx.navigation.NavController) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(imageVector = Icons.Default.Notifications, contentDescription = null, modifier = Modifier.size(64.dp), tint = MaterialTheme.colorScheme.primary)
        Spacer(modifier = Modifier.height(16.dp))
        Text(text = "🎯 AI 매칭 시스템", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(8.dp))
        Text(text = "추적 중인 상품과 커뮤니티 딜을\n자동으로 매칭해서 알려드려요!", style = MaterialTheme.typography.bodyLarge, textAlign = androidx.compose.ui.text.style.TextAlign.Center, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f))
        Spacer(modifier = Modifier.height(24.dp))
        Text(text = "내일 구현 예정 (Day 4)", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
    }
}

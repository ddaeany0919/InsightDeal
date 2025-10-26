package com.ddaeany0919.insightdeal

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            // ✅ 새로운 InsightDealTheme 사용 (컬러 스킴 지원)
            InsightDealTheme {
                InsightDealApp()
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InsightDealApp() {
    val navController = rememberNavController()

    NavHost(
        navController = navController,
        startDestination = "home"
    ) {
        // ✅ 홈화면
        composable("home") {
            HomeScreen(navController = navController)
        }

        // ✅ 테마 설정
        composable("theme_settings") {
            ThemeSettingsScreen(
                onBackClick = { navController.popBackStack() }
            )
        }

        // ✅ 고급 검색
        composable("advanced_search") {
            AdvancedSearchScreen(
                onDealClick = { dealItem ->
                    navController.navigate("price_graph/${dealItem.id}")
                },
                onBackClick = { navController.popBackStack() }
            )
        }

        // ✅ 북마크
        composable("bookmarks") {
            BookmarkScreen(
                onDealClick = { dealItem ->
                    navController.navigate("price_graph/${dealItem.id}")
                },
                onBackClick = { navController.popBackStack() }
            )
        }

        // ✅ 추천
        composable("recommendations") {
            RecommendationScreen(
                onDealClick = { dealItem ->
                    navController.navigate("price_graph/${dealItem.id}")
                }
            )
        }

        // ✅ 가격 그래프
        composable("price_graph/{dealId}") { backStackEntry ->
            val dealId = backStackEntry.arguments?.getString("dealId")?.toIntOrNull() ?: 0
            PriceGraphScreen(
                productId = dealId,
                onBackClick = { navController.popBackStack() }
            )
        }
    }
}
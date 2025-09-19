package com.example.insightdeal

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.insightdeal.ui.DealDetailScreen
import com.example.insightdeal.ui.MainScreen
import com.example.insightdeal.ui.theme.InsightDealTheme
import com.example.insightdeal.viewmodel.DealViewModel

class MainActivity : ComponentActivity() {
    // ViewModel 선언, activity scope에서 생명주기 연동
    private val dealViewModel: DealViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d("MainActivity", "onCreate called - App start")
        setContent {
            InsightDealTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    // 앱 UI root 호출
                    InsightDealApp(viewModel = dealViewModel)
                }
            }
        }
    }
}

/**
 * 앱 네비게이션 설정 및 화면 호출함수
 * @param viewModel 딜 관련 상태 및 비즈니스 로직 담당 ViewModel
 */
@Composable
fun InsightDealApp(viewModel: DealViewModel) {
    val navController = rememberNavController()

    NavHost(navController = navController, startDestination = "main") {
        // 메인 화면: 딜 목록, 카테고리 및 필터 사용
        composable("main") {
            val uiState by viewModel.uiState.collectAsStateWithLifecycle()

            MainScreen(
                uiState = uiState,
                onDealClick = { dealId ->
                    Log.d("InsightDealApp", "Navigating to detail for dealId: $dealId")
                    navController.navigate("detail/$dealId")
                },
                onRefresh = {
                    Log.d("InsightDealApp", "User requested refresh on main screen")
                    viewModel.refresh()
                },
                onLoadMore = {
                    Log.d("InsightDealApp", "User requested to load more deals")
                    viewModel.loadNextPage()
                },
                onCategorySelect = { category ->
                    Log.d("InsightDealApp", "Category selected: $category")
                    viewModel.selectCategory(category)
                },
                onCommunityToggle = { community ->
                    Log.d("InsightDealApp", "Community toggled: $community")
                    viewModel.toggleCommunity(community)
                }
            )
        }

        // 상세 화면: 딜 ID를 전달 받아 해당 딜 상세정보 표시
        composable(
            route = "detail/{dealId}",
            arguments = listOf(navArgument("dealId") {
                type = NavType.IntType
            })
        ) { backStackEntry ->

            // 전달받은 dealId, 기본 0
            val dealId = backStackEntry.arguments?.getInt("dealId") ?: 0

            // dealId가 변경되는 경우에만 API 호출하도록 최적화
            LaunchedEffect(dealId) {
                Log.d("InsightDealApp", "Fetching deal details for dealId: $dealId")
                viewModel.fetchDealDetail(dealId)
            }

            // Compose state 수집
            val detailState by viewModel.detailState.collectAsStateWithLifecycle()
            val priceHistoryState by viewModel.priceHistoryState.collectAsStateWithLifecycle()

            DealDetailScreen(
                detailState = detailState,
                priceHistoryState = priceHistoryState,
                onBackClick = {
                    Log.d("InsightDealApp", "Back triggered in detail screen")
                    navController.popBackStack()
                },
                onDealClick = { newDealId ->
                    Log.d(
                        "InsightDealApp",
                        "Related deal clicked - navigating to detail for newDealId: $newDealId (popping up to current detail/$dealId)"
                    )
                    // 현재 상세 화면 스택에서 제거 후 새 상세 화면으로 이동 (뒤로가기 시 메인)
                    navController.navigate("detail/$newDealId") {
                        popUpTo("detail/$dealId") { inclusive = true }
                    }
                }
            )
        }
    }
}

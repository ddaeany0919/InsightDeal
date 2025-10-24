package com.yourpackage.insightdeal

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.google.firebase.messaging.FirebaseMessaging
import com.yourpackage.insightdeal.ui.theme.InsightDealTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    
    companion object {
        private const val TAG = "MainActivity"
        private const val NOTIFICATION_PERMISSION_REQUEST = 1001
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // FCM 초기화
        initFCM()
        
        setContent {
            InsightDealTheme {
                MainApp()
            }
        }
        
        // 푸시 알림에서 오는 인텐트 처리
        handlePushIntent()
    }
    
    private fun initFCM() {
        """🔥 Firebase Cloud Messaging 초기화"""
        try {
            // 알림 권한 요청 (Android 13+)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                if (ContextCompat.checkSelfPermission(
                        this,
                        Manifest.permission.POST_NOTIFICATIONS
                    ) != PackageManager.PERMISSION_GRANTED
                ) {
                    ActivityCompat.requestPermissions(
                        this,
                        arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                        NOTIFICATION_PERMISSION_REQUEST
                    )
                }
            }
            
            // FCM 토큰 가져오기
            FirebaseMessaging.getInstance().token.addOnCompleteListener { task ->
                if (!task.isSuccessful) {
                    Log.w(TAG, "Fetching FCM registration token failed", task.exception)
                    return@addOnCompleteListener
                }
                
                val token = task.result
                Log.d(TAG, "🔑 FCM Token: ${token.take(20)}...")
                
                // 서버에 토큰 전송
                sendTokenToServer(token)
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ FCM initialization failed: ${e.message}")
        }
    }
    
    private fun sendTokenToServer(token: String) {
        """서버에 FCM 토큰 전송"""
        lifecycleScope.launch {
            try {
                val apiService = ApiService.create()
                val response = apiService.registerFCMToken(
                    mapOf(
                        "token" to token,
                        "user_id" to "anonymous",
                        "device_info" to "${Build.MANUFACTURER} ${Build.MODEL}",
                        "app_version" to BuildConfig.VERSION_NAME
                    )
                )
                Log.d(TAG, "✅ FCM Token sent to server successfully")
            } catch (e: Exception) {
                Log.e(TAG, "❌ Failed to send FCM token: ${e.message}")
            }
        }
    }
    
    private fun handlePushIntent() {
        """푸시 알림 인텐트 처리"""
        val action = intent.getStringExtra("notification_action")
        when (action) {
            "DEAL_DETAIL" -> {
                val dealId = intent.getIntExtra("deal_id", 0)
                if (dealId > 0) {
                    Log.d(TAG, "📲 Push notification - Navigate to deal: $dealId")
                    // TODO: 딜 상세 화면으로 이동
                }
            }
            "PRODUCT_DETAIL" -> {
                val productId = intent.getIntExtra("product_id", 0)
                if (productId > 0) {
                    Log.d(TAG, "📲 Push notification - Navigate to product: $productId")
                    // TODO: 상품 그래프 화면으로 이동
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainApp() {
    val navController = rememberNavController()
    
    Scaffold(
        bottomBar = {
            BottomNavigationBar(navController = navController)
        },
        containerColor = Color.White
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = "deals",
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // 🔥 커뮤니티 핫딜 화면
            composable("deals") {
                DealsScreen(
                    onNavigateToDetail = { dealId ->
                        navController.navigate("deal_detail/$dealId")
                    }
                )
            }
            
            // 📊 가격 추적 화면
            composable("tracking") {
                CoupangTrackingScreen(
                    onNavigateToChart = { productId ->
                        navController.navigate("price_chart/$productId")
                    }
                )
            }
            
            // 🔍 검색 화면
            composable("search") {
                SearchScreen()
            }
            
            // ⚙️ 설정 화면
            composable("settings") {
                SettingsScreen()
            }
            
            // 📎 딜 상세 화면
            composable("deal_detail/{dealId}") { backStackEntry ->
                val dealId = backStackEntry.arguments?.getString("dealId")?.toIntOrNull() ?: 0
                DealDetailScreen(
                    dealId = dealId,
                    onNavigateBack = {
                        navController.popBackStack()
                    }
                )
            }
            
            // 📈 가격 그래프 화면
            composable("price_chart/{productId}") { backStackEntry ->
                val productId = backStackEntry.arguments?.getString("productId")?.toIntOrNull() ?: 0
                PriceChartScreen(
                    productId = productId,
                    onNavigateBack = {
                        navController.popBackStack()
                    }
                )
            }
        }
    }
}

@Composable
fun BottomNavigationBar(navController: NavHostController) {
    val items = listOf(
        BottomNavItem("deals", "핫딜", Icons.Default.LocalOffer),
        BottomNavItem("tracking", "가격추적", Icons.Default.ShowChart),
        BottomNavItem("search", "검색", Icons.Default.Search),
        BottomNavItem("settings", "설정", Icons.Default.Settings)
    )
    
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route
    
    NavigationBar(
        containerColor = Color.White,
        contentColor = PrimaryOrange
    ) {
        items.forEach { item ->
            NavigationBarItem(
                icon = { 
                    Icon(
                        item.icon, 
                        contentDescription = item.label,
                        tint = if (currentRoute == item.route) PrimaryOrange else Color.Gray
                    ) 
                },
                label = { 
                    Text(
                        item.label,
                        color = if (currentRoute == item.route) PrimaryOrange else Color.Gray
                    ) 
                },
                selected = currentRoute == item.route,
                onClick = {
                    navController.navigate(item.route) {
                        // 동일한 대상이 여러 번 선택되지 않도록
                        popUpTo(navController.graph.startDestinationId) {
                            saveState = true
                        }
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
    val label: String,
    val icon: androidx.compose.ui.graphics.vector.ImageVector
)

// 임시 화면들 (추후 구현)
@Composable
fun DealsScreen(
    onNavigateToDetail: (Int) -> Unit = {}
) {
    // 기존 MainScreen 기능 유지
    // TODO: 전체 커뮤니티 핫딜 목록 표시
}

@Composable
fun SearchScreen() {
    // TODO: 핫딜 검색 화면
}

@Composable
fun SettingsScreen() {
    // TODO: 설정 화면 (알림 설정, 계정 관리 등)
}
package com.ddaeany0919.insightdeal

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.ddaeany0919.insightdeal.theme.*

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val context = LocalContext.current
            val currentTheme by remember { mutableStateOf(loadThemeMode(context)) }
            
            InsightDealTheme(themeMode = currentTheme) {
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
        startDestination = "main"
    ) {
        composable("main") {
            MainScreen(
                onNavigateToSettings = {
                    navController.navigate("theme_settings")
                },
                onNavigateToSearch = {
                    navController.navigate("advanced_search")
                }
            )
        }
        
        composable("theme_settings") {
            ThemeSettingsScreen(
                onBackClick = {
                    navController.popBackStack()
                }
            )
        }
        
        composable("advanced_search") {
            AdvancedSearchScreen(
                onBackClick = {
                    navController.popBackStack()
                }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    onNavigateToSettings: () -> Unit,
    onNavigateToSearch: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("🔥 InsightDeal") },
                actions = {
                    TextButton(onClick = onNavigateToSettings) {
                        Text("🎨 테마")
                    }
                    TextButton(onClick = onNavigateToSearch) {
                        Text("🔍 검색")
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            verticalArrangement = Arrangement.Center
        ) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Column(
                    modifier = Modifier.padding(24.dp)
                ) {
                    Text(
                        text = "🎉 InsightDeal 앱",
                        style = MaterialTheme.typography.headlineMedium
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "다크모드 & 테마 시스템이 성공적으로 통합되었습니다!",
                        style = MaterialTheme.typography.bodyLarge
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "• AUTO 모드: 저녁 7시~오전 7시 자동 다크모드\n" +
                              "• AMOLED 블랙 테마 지원\n" +
                              "• 4가지 컬러 테마 (오렌지/블루/그린/퍼플)\n" +
                              "• 백엔드 서버 연동 준비 완료",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdvancedSearchScreen(
    onBackClick: () -> Unit
) {
    val viewModel: AdvancedSearchViewModel = viewModel()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val searchResults by viewModel.searchResults.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("🔍 고급 검색") },
                navigationIcon = {
                    TextButton(onClick = onBackClick) {
                        Text("← 뒤로")
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp)
        ) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { viewModel.onSearchQueryChanged(it) },
                label = { Text("핫딜 검색") },
                placeholder = { Text("갤럭시, 아이폰, 노트북...") },
                modifier = Modifier.fillMaxWidth()
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            Button(
                onClick = { viewModel.performSearch() },
                modifier = Modifier.fillMaxWidth(),
                enabled = searchQuery.isNotEmpty() && !isLoading
            ) {
                if (isLoading) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp))
                } else {
                    Text("🔍 검색하기")
                }
            }
            
            if (searchResults.isNotEmpty()) {
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "검색 결과 (${searchResults.size}개)",
                    style = MaterialTheme.typography.titleMedium
                )
                // 실제 검색 결과 리스트는 여기에 표시
            }
        }
    }
}
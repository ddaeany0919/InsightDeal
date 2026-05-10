package com.ddaeany0919.insightdeal.presentation.category

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

import androidx.navigation.NavController
import com.ddaeany0919.insightdeal.presentation.home.HomeViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CategoryScreen(navController: NavController? = null, homeViewModel: HomeViewModel? = null) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("카테고리", fontWeight = FontWeight.Bold) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onBackground
                )
            )
        }
    ) { paddingValues ->
        val categories = listOf(
            "음식" to "🍔",
            "SW/게임" to "🎮",
            "PC제품" to "💻",
            "가전제품" to "📺",
            "생활용품" to "🧻",
            "의류" to "👕",
            "화장품" to "💄",
            "모바일/기프티콘" to "📱",
            "상품권" to "💳",
            "패키지/이용권" to "🎟",
            "여행.해외핫딜" to "✈️",
            "적립" to "💰",
            "이벤트" to "🎉",
            "기타" to "📦"
        )

        LazyVerticalGrid(
            columns = GridCells.Fixed(3),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            items(categories) { (name, emoji) ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(1.2f),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                    shape = RoundedCornerShape(12.dp),
                    onClick = {
                        val encodedName = java.net.URLEncoder.encode(name, "UTF-8")
                        navController?.navigate("category_detail/$encodedName")
                    }
                ) {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = emoji,
                            fontSize = 28.sp,
                            modifier = Modifier.padding(bottom = 6.dp)
                        )
                        Text(
                            text = name,
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        }
    }
}

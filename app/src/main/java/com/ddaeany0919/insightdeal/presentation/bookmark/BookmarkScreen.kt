package com.ddaeany0919.insightdeal.presentation.bookmark

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack

import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ddaeany0919.insightdeal.models.DealItem
import com.ddaeany0919.insightdeal.presentation.A11yIconButton
import com.ddaeany0919.insightdeal.presentation.rememberSavedLazyListState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
@Suppress("UNUSED_PARAMETER")
fun BookmarkScreen(
    onDealClick: (DealItem) -> Unit,
    onBackClick: () -> Unit
) {
    val listState = rememberSavedLazyListState(key = "bookmark_list")

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(text = "🔖 즐겨찾기", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    A11yIconButton(onClick = onBackClick, contentDescription = "뒤로가기") {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                }
            )
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            state = listState,
            contentPadding = PaddingValues(16.dp)
        ) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    elevation = CardDefaults.cardElevation(4.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "📌 북마크 기능 준비 중",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        Text(
                            text = "홈에서 하트 버튼을 눌러\n마음에 드는 딜을 저장하세요!",
                            fontSize = 14.sp
                        )
                    }
                }
            }
        }
    }
}

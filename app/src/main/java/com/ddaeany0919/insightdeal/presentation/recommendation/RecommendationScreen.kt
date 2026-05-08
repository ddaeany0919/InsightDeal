package com.ddaeany0919.insightdeal.presentation.recommendation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ddaeany0919.insightdeal.models.DealItem
import com.ddaeany0919.insightdeal.presentation.A11yIconButton
import com.ddaeany0919.insightdeal.presentation.rememberSavedLazyListState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
@Suppress("UNUSED_PARAMETER")
fun RecommendationScreen(
    onDealClick: (DealItem) -> Unit,
    viewModel: RecommendationViewModel = viewModel()
) {
    val listState = rememberSavedLazyListState(key = "recommend_list")

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(text = "추천", fontWeight = FontWeight.Bold, fontSize = 20.sp) },
                navigationIcon = {
                    A11yIconButton(onClick = { /* TODO: back */ }, contentDescription = "뒤로가기") {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                },
                actions = {
                    A11yIconButton(onClick = { /* TODO: refresh */ }, contentDescription = "새로고침") {
                        Icon(Icons.Default.Refresh, contentDescription = null)
                    }
                }
            )
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(MaterialTheme.colorScheme.background),
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
                            text = "🤖 AI 추천 기능 준비 중",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        Text(
                            text = "사용자의 관심사를 분석하여\n맞춤형 딜을 추천해드릴 예정입니다!",
                            fontSize = 14.sp
                        )
                    }
                }
            }
        }
    }
}

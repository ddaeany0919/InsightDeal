package com.ddaeany0919.insightdeal.presentation.mypage.history

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ddaeany0919.insightdeal.presentation.home.DealCardComposable

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecentDealsScreen(
    onBack: () -> Unit,
    onDealClick: (Int) -> Unit
) {
    val context = LocalContext.current
    LaunchedEffect(Unit) {
        RecentDealManager.init(context)
    }
    
    val recentDeals by RecentDealManager.recentDeals.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("최근 본 핫딜", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "뒤로가기")
                    }
                }
            )
        }
    ) { paddingValues ->
        if (recentDeals.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize().padding(paddingValues),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "최근 본 핫딜이 없습니다.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(paddingValues),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                items(recentDeals) { deal ->
                    DealCardComposable(
                        deal = deal,
                        onDetailClick = { onDealClick(deal.id) }
                    )
                }
            }
        }
    }
}

package com.ddaeany0919.insightdeal.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ddaeany0919.insightdeal.models.PriceHistoryPoint
import com.ddaeany0919.insightdeal.models.MallPrice

/**
 * 상세화면 신뢰 포인트 - UI 골격 (차트/가격표/액션)
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
@Suppress("UNUSED_PARAMETER")
fun DealDetailScreen(
    dealId: Int,
    title: String,
    originUrl: String,
    priceHistory: List<PriceHistoryPoint>,
    mallPrices: List<MallPrice>,
    isBookmarked: Boolean,
    onBack: () -> Unit,
    onBookmarkToggle: () -> Unit,
    onShare: () -> Unit,
    onOpenOrigin: (String) -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(text = title, maxLines = 1) },
                navigationIcon = {
                    A11yIconButton(onClick = onBack, contentDescription = "뒤로가기") {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                },
                actions = {
                    A11yIconButton(onClick = onShare, contentDescription = "공유") {
                        Icon(Icons.Default.Share, contentDescription = null)
                    }
                    A11yIconButton(
                        onClick = onBookmarkToggle,
                        contentDescription = if (isBookmarked) "북마크 제거" else "북마크 추가"
                    ) {
                        Icon(
                            imageVector = if (isBookmarked) Icons.Default.Bookmark else Icons.Default.BookmarkBorder,
                            contentDescription = null
                        )
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item { PriceHistoryCard(priceHistory) }
            item { MallPriceTable(mallPrices, onOpenOrigin) }
        }
    }
}

@Composable
@Suppress("UNUSED_PARAMETER")
private fun PriceHistoryCard(history: List<PriceHistoryPoint>) {
    Card(
        elevation = CardDefaults.cardElevation(3.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("90일 가격 추이", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(12.dp))
            // 차트 자리(임시 placeholder). 실제 구현은 Compose chart 또는 Canvas로 교체.
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(180.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                Text("차트 준비 중")
            }
        }
    }
}

@Composable
private fun MallPriceTable(mallPrices: List<MallPrice>, onOpenOrigin: (String) -> Unit) {
    Card(elevation = CardDefaults.cardElevation(3.dp)) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("4몰 가격 비교", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(12.dp))
            mallPrices.forEach { row ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(row.platform, style = MaterialTheme.typography.bodyMedium)
                    Text(String.format("%,d원", row.price), fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    TextButton(onClick = { onOpenOrigin(row.url) }) { Text("바로가기") }
                }
                HorizontalDivider()
            }
        }
    }
}

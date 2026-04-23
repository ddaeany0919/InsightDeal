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
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ddaeany0919.insightdeal.models.PriceHistoryPoint
import com.ddaeany0919.insightdeal.models.MallPrice

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DealDetailScreen(
    title: String,
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
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "뒤로가기")
                    }
                },
                actions = {
                    IconButton(onClick = onShare) {
                        Icon(Icons.Default.Share, contentDescription = "공유")
                    }
                    IconButton(onClick = onBookmarkToggle) {
                        Icon(
                            imageVector = if (isBookmarked) Icons.Default.Bookmark else Icons.Default.BookmarkBorder,
                            contentDescription = "북마크"
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
            item { 
                PriceHistoryInteractiveCard(priceHistory) 
            }
            item {
                AIBuyerGuide(priceHistory = priceHistory, currentPrice = mallPrices.minOfOrNull { it.price } ?: 0)
            }
            item {
                PriceAlertRegistrationButton(
                    currentPrice = mallPrices.minOfOrNull { it.price } ?: 0,
                    onAlertClick = { /* MVP: 백엔드 푸시 알람 */ }
                )
            }
            item { 
                MallPriceTable(mallPrices, onOpenOrigin) 
            }
        }
    }
}

@Composable
private fun PriceAlertRegistrationButton(currentPrice: Int, onAlertClick: () -> Unit) {
    if (currentPrice == 0) return
    val targetPrice = (currentPrice * 0.95).toInt()
    Button(
        onClick = onAlertClick,
        modifier = Modifier.fillMaxWidth().height(56.dp),
        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.tertiary),
        shape = RoundedCornerShape(12.dp)
    ) {
        Icon(Icons.Default.NotificationsActive, contentDescription = null, modifier = Modifier.size(24.dp))
        Spacer(Modifier.width(12.dp))
        Text(
            text = "${String.format("%,d", targetPrice)}원 이하로 떨어지면 알림",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun PriceHistoryInteractiveCard(history: List<PriceHistoryPoint>) {
    Card(
        elevation = CardDefaults.cardElevation(3.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("핫딜 가격 변동", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(16.dp))
            if (history.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxWidth().height(180.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text("충분한 가격 변동 기록이 수집되지 않았습니다", color = Color.Gray)
                }
            } else {
                Box(modifier = Modifier.fillMaxWidth().height(200.dp)) {
                    // 차트 플레이스홀더 (vico 등)
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text("그래프 영역을 터치하여 상세 금액을 확인하세요", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
            }
        }
    }
}

@Composable
private fun AIBuyerGuide(priceHistory: List<PriceHistoryPoint>, currentPrice: Int) {
    if (priceHistory.isEmpty() || currentPrice == 0) return

    val minPrice = priceHistory.minOfOrNull { it.price } ?: currentPrice
    val isRecordLow = currentPrice <= minPrice

    Card(
        elevation = CardDefaults.cardElevation(0.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isRecordLow) Color(0xFFFBE9E7) else MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f)
        )
    ) {
        Column(modifier = Modifier.padding(16.dp).fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.Insights, 
                    contentDescription = null, 
                    tint = if (isRecordLow) Color(0xFFD84315) else MaterialTheme.colorScheme.primary
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    "AI 구매 가이드", 
                    style = MaterialTheme.typography.titleMedium, 
                    fontWeight = FontWeight.Bold,
                    color = if (isRecordLow) Color(0xFFD84315) else MaterialTheme.colorScheme.onSurface
                )
            }
            Spacer(Modifier.height(8.dp))
            
            if (isRecordLow) {
                Text(
                    "역대 최저가 방어선 갱신! 망설이면 품절입니다. 당장 구매하세요!",
                    style = MaterialTheme.typography.bodyLarge,
                    color = Color(0xFFE64A19),
                    fontWeight = FontWeight.Bold
                )
            } else {
                Text(
                     "현재 가격은 전체 변동의 일정 구간에 위치합니다. 조금 더 관망하거나 알림 설정을 하는 것을 추천합니다.",
                     style = MaterialTheme.typography.bodyMedium,
                     color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun MallPriceTable(mallPrices: List<MallPrice>, onOpenOrigin: (String) -> Unit) {
    Card(elevation = CardDefaults.cardElevation(3.dp)) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("웹 판매처 최저가 비교", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
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
                    Text("${String.format("%,d", row.price)}원", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    TextButton(onClick = { onOpenOrigin(row.url) }) { Text("바로가기") }
                }
                HorizontalDivider()
            }
        }
    }
}

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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ddaeany0919.insightdeal.models.PriceHistoryPoint
import com.ddaeany0919.insightdeal.models.MallPrice

/**
 * ?곸꽭?붾㈃ ?좊ː ?ъ씤??- 李⑦듃 ?곕룞, AI 援щℓ 媛?대뱶 諛??꾩떆由ъ뒪???곕룞 ?ы븿
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
                    A11yIconButton(onClick = onBack, contentDescription = "?ㅻ줈媛湲?) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                },
                actions = {
                    A11yIconButton(onClick = onShare, contentDescription = "怨듭쑀") {
                        Icon(Icons.Default.Share, contentDescription = null)
                    }
                    A11yIconButton(
                        onClick = onBookmarkToggle,
                        contentDescription = if (isBookmarked) "遺곷쭏???쒓굅" else "遺곷쭏??異붽?"
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
            item { 
                PriceHistoryInteractiveCard(priceHistory) 
            }
            item {
                AIBuyerGuide(priceHistory = priceHistory, currentPrice = mallPrices.minOfOrNull { it.price } ?: 0)
            }
            
            // [Task 2] ?뚮┝ ?ㅼ젙 踰꾪듉 ?곕룞
            item {
                PriceAlertRegistrationButton(
                    currentPrice = mallPrices.minOfOrNull { it.price } ?: 0,
                    onAlertClick = { /* MVP: 諛깆뿏???ㅼ?以꾨윭 FCM ?곕룞 ?몃━嫄??몄텧 ?μ냼 */ }
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
    val targetPrice = (currentPrice * 0.95).toInt() // 5% ?좎씤??媛寃?    
    Button(
        onClick = onAlertClick,
        modifier = Modifier.fillMaxWidth().height(56.dp),
        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.tertiary),
        shape = RoundedCornerShape(12.dp)
    ) {
        Icon(Icons.Default.NotificationsActive, contentDescription = null, modifier = Modifier.size(24.dp))
        Spacer(Modifier.width(12.dp))
        Text(
            text = "${String.format("%,d", targetPrice)}???댄븯濡??⑥뼱吏硫??뚮┝ 諛쏄린",
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
            Text("?뱢 媛寃?蹂???덉뒪?좊━", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(16.dp))
            
            if (history.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxWidth().height(180.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text("異⑸텇??媛寃?蹂??湲곕줉???섏쭛?섏? ?딆븯?듬땲??", color = Color.Gray)
                }
            } else {
                val prices = history.map { it.price.toFloat() }
                val dates = history.map { it.date }
                
                PriceChart(
                    prices = prices,
                    dates = dates,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp)
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    "??洹몃옒???곸뿭???곗튂/?쒕옒洹명븯???곸꽭 湲덉븸???뺤씤?섏꽭??",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.Gray
                )
            }
        }
    }
}

@Composable
private fun AIBuyerGuide(priceHistory: List<PriceHistoryPoint>, currentPrice: Int) {
    if (priceHistory.isEmpty() || currentPrice == 0) return

    val minPrice = priceHistory.minOfOrNull { it.price } ?: currentPrice
    val maxPrice = priceHistory.maxOfOrNull { it.price } ?: currentPrice
    
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
                    "AI 援щℓ 媛?대뱶", 
                    style = MaterialTheme.typography.titleMedium, 
                    fontWeight = FontWeight.Bold,
                    color = if (isRecordLow) Color(0xFFD84315) else MaterialTheme.colorScheme.onSurface
                )
            }
            Spacer(Modifier.height(8.dp))
            
            if (isRecordLow) {
                Text(
                    "?뵦 ??? 理쒖?媛 諛⑹뼱??媛깆떊! 留앹꽕?대㈃ ?덉젅?낅땲?? ?뱀옣 援щℓ?섏꽭??",
                    style = MaterialTheme.typography.bodyLarge,
                    color = Color(0xFFE64A19),
                    fontWeight = FontWeight.Bold
                )
            } else {
                val range = maxPrice - minPrice
                val ratio = if (range == 0) 0 else ((currentPrice - minPrice).toFloat() / range * 100).toInt()
                
                Text(
                     "?꾩옱 媛寃⑹? ?꾩껜 蹂????쓽 ?섏쐞 ${ratio}% 援ш컙???꾩튂?⑸땲??\n議곌툑 ??愿留앺븯嫄곕굹 ?뚮┝ ?ㅼ젙???섎뒗 寃껋쓣 異붿쿇?⑸땲??",
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
            Text("?썟 ?먮ℓ泥?理쒖?媛 鍮꾧탳", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
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
                    Text(String.format("%,d??, row.price), fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    TextButton(onClick = { onOpenOrigin(row.url) }) { Text("諛붾줈媛湲?) }
                }
                HorizontalDivider()
            }
        }
    }
}

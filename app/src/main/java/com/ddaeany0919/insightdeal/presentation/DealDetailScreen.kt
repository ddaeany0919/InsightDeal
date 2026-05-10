package com.ddaeany0919.insightdeal.presentation

import com.ddaeany0919.insightdeal.presentation.formatPrice
import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.layout.ContentScale
import coil.compose.AsyncImage
import com.ddaeany0919.insightdeal.models.DealItem
import com.ddaeany0919.insightdeal.models.PriceHistoryPoint
import com.ddaeany0919.insightdeal.models.MallPrice
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DealDetailRoute(
    dealId: Int,
    viewModel: DealDetailViewModel,
    onBack: () -> Unit,
    onOpenUrl: (String) -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(dealId) {
        viewModel.loadDealData(dealId)
    }

    if (uiState.isLoading) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }

    if (uiState.error != null) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(uiState.error ?: "알 수 없는 에러가 발생했습니다.")
        }
        return
    }

    val deal = uiState.deal
    if (deal == null) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("데이터를 찾을 수 없습니다.")
        }
        return
    }

    val currentPriceInt = deal.price

    DealDetailScreen(
        deal = deal,
        priceHistory = uiState.priceHistory,
        mallPrices = listOf(MallPrice(platform = deal.siteName, price = currentPriceInt, url = deal.ecommerceUrl ?: deal.postUrl ?: "", currency = "KRW")),
        isBookmarked = false,
        onBack = onBack,
        onBookmarkToggle = { /* Todo */ },
        onShare = { /* Todo */ },
        onOpenOrigin = onOpenUrl
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DealDetailScreen(
    deal: DealItem,
    priceHistory: List<PriceHistoryPoint>,
    mallPrices: List<MallPrice>,
    isBookmarked: Boolean,
    onBack: () -> Unit,
    onBookmarkToggle: () -> Unit,
    onShare: () -> Unit,
    onOpenOrigin: (String) -> Unit
) {
    Scaffold(
        bottomBar = {
            val targetUrl = mallPrices.firstOrNull()?.url ?: deal.sources?.firstOrNull()?.postUrl
            if (targetUrl != null) {
                androidx.compose.material3.Surface(
                    color = MaterialTheme.colorScheme.surface,
                    shadowElevation = 16.dp
                ) {
                    androidx.compose.material3.Button(
                        onClick = { onOpenOrigin(targetUrl) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
                            .height(56.dp),
                        shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.ShoppingCart, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("최저가로 구매하기", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                    }
                }
            }
        },
        topBar = {
            TopAppBar(
                title = { Text(text = deal.title, maxLines = 1) },
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
            contentPadding = PaddingValues(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 88.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                DealHeader(deal)
            }
            item { 
                PriceHistoryInteractiveCard(priceHistory) 
            }
            item {
                AIBuyerGuide(priceHistory = priceHistory, currentPrice = mallPrices.minOfOrNull { it.price } ?: 0)
            }
            item {
                PriceAlertRegistrationButton(
                    currentPrice = mallPrices.minOfOrNull { it.price } ?: 0,
                    onAlertClick = { targetPrice -> /* MVP: 백엔드 푸시 알람 등록 */ }
                )
            }
            item { 
                MallPriceTable(mallPrices, onOpenOrigin) 
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PriceAlertRegistrationButton(currentPrice: Int, onAlertClick: (Int) -> Unit) {
    if (currentPrice == 0) return
    var showDialog by remember { mutableStateOf(false) }
    var inputPrice by remember { mutableStateOf((currentPrice * 0.95).toInt().toString()) }

    Button(
        onClick = { showDialog = true },
        modifier = Modifier.fillMaxWidth().height(48.dp),
        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.tertiary),
        shape = RoundedCornerShape(12.dp)
    ) {
        Icon(Icons.Default.NotificationsActive, contentDescription = null, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(8.dp))
        Text(
            text = "목표가 도달 시 알림 설정",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold
        )
    }

    if (showDialog) {
        AlertDialog(
            onDismissRequest = { showDialog = false },
            title = { Text("알림 받을 목표가 설정", fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    Text("원하시는 목표 가격을 입력해주세요.\n현재 최저가: ${String.format("%,d", currentPrice)}원", style = MaterialTheme.typography.bodyMedium, color = Color.Gray)
                    Spacer(Modifier.height(16.dp))
                    OutlinedTextField(
                        value = inputPrice,
                        onValueChange = { newValue ->
                            if (newValue.all { it.isDigit() }) inputPrice = newValue
                        },
                        label = { Text("목표 가격 (원)") },
                        singleLine = true,
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number)
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val target = inputPrice.toIntOrNull() ?: 0
                    if (target > 0) onAlertClick(target)
                    showDialog = false
                }) {
                    Text("설정")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDialog = false }) {
                    Text("취소")
                }
            }
        )
    }
}

@Composable
private fun PriceHistoryInteractiveCard(history: List<PriceHistoryPoint>) {
    // UI 최소화를 위해 데이터가 2개 이하인 경우 차트 대신 간략한 텍스트만 표시
    if (history.size <= 2) {
        Card(
            elevation = CardDefaults.cardElevation(0.dp),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text("핫딜 가격 변동", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                Text("아직 충분한 가격 데이터가 모이지 않았습니다.", color = Color.Gray, style = MaterialTheme.typography.bodyMedium)
            }
        }
    } else {
        Card(
            elevation = CardDefaults.cardElevation(0.dp),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text("핫딜 가격 변동", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(16.dp))
                Box(modifier = Modifier.fillMaxWidth().height(200.dp)) {
                    PriceChart(
                        prices = history.map { it.price.toFloat() },
                        dates = history.map { it.date },
                        modifier = Modifier.fillMaxSize()
                    )
                }
                Spacer(modifier = Modifier.height(12.dp))
                Text("그래프 영역을 터치하여 상세 금액을 확인하세요", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
            }
        }
    }
}

@Composable
private fun AIBuyerGuide(priceHistory: List<PriceHistoryPoint>, currentPrice: Int) {
    if (priceHistory.size <= 2 || currentPrice == 0) return

    val minPrice = priceHistory.minOfOrNull { it.price } ?: currentPrice
    val maxPrice = priceHistory.maxOfOrNull { it.price } ?: currentPrice
    val isRecordLow = currentPrice <= minPrice
    val diffFromMax = maxPrice - currentPrice

    val containerColor = if (isRecordLow) Color(0xFFF0FDF4) else Color(0xFFF8FAFC)
    val borderColor = if (isRecordLow) Color(0xFF4ADE80) else Color(0xFFE2E8F0)
    val titleColor = if (isRecordLow) Color(0xFF166534) else Color(0xFF334155)

    Card(
        elevation = CardDefaults.cardElevation(2.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = containerColor),
        border = BorderStroke(1.dp, borderColor),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
    ) {
        Column(modifier = Modifier.padding(20.dp).fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = if (isRecordLow) Color(0xFFDCFCE7) else Color(0xFFE2E8F0),
                    modifier = Modifier.size(36.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            Icons.Default.Insights, 
                            contentDescription = "AI", 
                            tint = titleColor,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
                Spacer(Modifier.width(12.dp))
                Column {
                    Text(
                        "AI 구매 타점 분석", 
                        style = MaterialTheme.typography.titleMedium, 
                        fontWeight = FontWeight.ExtraBold,
                        color = titleColor
                    )
                    Text(
                        if (isRecordLow) "강력 매수 권장" else "관망 또는 목표가 대기",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (isRecordLow) Color(0xFF22C55E) else Color(0xFF64748B),
                        fontWeight = FontWeight.Bold
                    )
                }
            }
            Spacer(Modifier.height(16.dp))
            HorizontalDivider(color = borderColor.copy(alpha = 0.5f))
            Spacer(Modifier.height(16.dp))
            
            if (isRecordLow) {
                Text(
                    "🔥 역대 최저가 갱신!",
                    style = MaterialTheme.typography.titleMedium,
                    color = Color(0xFFDC2626),
                    fontWeight = FontWeight.ExtraBold
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    "현재 가격은 수집된 역사상 가장 낮은 가격입니다. 고점 대비 ${String.format("%,d", diffFromMax)}원 저렴하며, 품절되기 전에 구매하는 것을 강력히 추천합니다.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color(0xFF334155),
                    lineHeight = 22.sp
                )
            } else {
                val percentage = if (maxPrice > 0) ((currentPrice.toFloat() - minPrice.toFloat()) / (maxPrice.toFloat() - minPrice.toFloat()) * 100).toInt() else 50
                Text(
                     "현재 가격은 변동 구간의 상위 ${percentage}%에 위치합니다. 급하지 않다면 '목표가 알림'을 설정하여 더 저렴해질 때 구매하세요.",
                     style = MaterialTheme.typography.bodyMedium,
                     color = Color(0xFF475569),
                     lineHeight = 22.sp
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
                    val priceText = when (row.currency?.uppercase()) {
                        "USD" -> "$${String.format(Locale.US, "%.2f", row.price.toDouble() / 100)}"
                        "EUR" -> "€${String.format(Locale.US, "%.2f", row.price.toDouble() / 100)}"
                        else -> "${formatPrice(row.price, row.currency ?: "KRW")}"
                    }
                    Text(priceText, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    TextButton(onClick = { onOpenOrigin(row.url) }) { Text("바로가기") }
                }
                HorizontalDivider()
            }
        }
    }
}

@Composable
fun DealHeader(deal: DealItem) {
    Column(modifier = Modifier.fillMaxWidth()) {
        if (!deal.imageUrl.isNullOrEmpty()) {
            AsyncImage(
                model = deal.imageUrl,
                contentDescription = null,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(250.dp)
                    .background(Color.LightGray.copy(alpha = 0.2f)),
                contentScale = ContentScale.Crop
            )
        }
        
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                val siteNameLower = deal.siteName.lowercase(java.util.Locale.getDefault())
                val (bgColor, textColor) = when {
                    siteNameLower.contains("뽐뿌") -> androidx.compose.ui.graphics.Color(0xFF1565C0) to androidx.compose.ui.graphics.Color.White
                    siteNameLower.contains("퀘이사존") -> androidx.compose.ui.graphics.Color(0xFFE65100) to androidx.compose.ui.graphics.Color.White
                    siteNameLower.contains("루리웹") -> androidx.compose.ui.graphics.Color(0xFF0D47A1) to androidx.compose.ui.graphics.Color.White
                    siteNameLower.contains("펨코") || siteNameLower.contains("에펨코리아") -> androidx.compose.ui.graphics.Color(0xFF0288D1) to androidx.compose.ui.graphics.Color.White
                    siteNameLower.contains("빠삭") -> androidx.compose.ui.graphics.Color(0xFFC2185B) to androidx.compose.ui.graphics.Color.White
                    siteNameLower.contains("클리앙") -> androidx.compose.ui.graphics.Color(0xFF37474F) to androidx.compose.ui.graphics.Color.White
                    else -> MaterialTheme.colorScheme.surfaceVariant to MaterialTheme.colorScheme.onSurfaceVariant
                }

                Text(
                    text = deal.siteName,
                    style = MaterialTheme.typography.labelMedium,
                    color = textColor,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .background(bgColor, RoundedCornerShape(4.dp))
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                )
                
                if (deal.shippingFee != null) {
                    Text(
                        text = deal.shippingFee,
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.Gray
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Text(
                text = deal.title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis
            )
            
            Spacer(modifier = Modifier.height(12.dp))
            
            Row(verticalAlignment = Alignment.Bottom) {
                if (deal.discountRate != null && deal.discountRate > 0) {
                    Text(
                        text = "${deal.discountRate}%",
                        style = MaterialTheme.typography.headlineMedium,
                        color = Color.Red,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                }
                
                Text(
                    text = "${formatPrice(deal.price, deal.currency ?: "KRW")}",
                    style = MaterialTheme.typography.headlineLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            
            if (deal.aiSummary != null && deal.aiSummary.isNotEmpty()) {
                Spacer(modifier = Modifier.height(16.dp))
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha=0.3f)),
                    shape = RoundedCornerShape(16.dp),
                    elevation = CardDefaults.cardElevation(0.dp)
                ) {
                    Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.Top) {
                        Icon(
                            Icons.Default.Info, 
                            contentDescription = "AI 요약", 
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(top = 2.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = deal.aiSummary, 
                            style = MaterialTheme.typography.bodyMedium,
                            lineHeight = 22.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }

            if (deal.contentHtml != null && deal.contentHtml.isNotEmpty() && deal.contentHtml.length > 10) {
                Spacer(modifier = Modifier.height(24.dp))
                Text(
                    text = "본문 내용",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(8.dp))
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha=0.5f)),
                    elevation = CardDefaults.cardElevation(0.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = deal.contentHtml,
                        style = MaterialTheme.typography.bodyMedium.copy(
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            lineHeight = 22.sp
                        ),
                        modifier = Modifier.padding(16.dp)
                    )
                }
            }
        }
    }
}

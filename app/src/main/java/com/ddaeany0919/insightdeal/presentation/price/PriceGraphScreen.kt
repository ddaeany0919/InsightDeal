package com.ddaeany0919.insightdeal.presentation.price

import android.view.ViewGroup
import android.widget.LinearLayout
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.automirrored.filled.TrendingDown
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import java.text.NumberFormat

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PriceGraphScreen(
    productId: String,
    onBackClick: () -> Unit,
    viewModel: PriceChartViewModel = viewModel()
) {
    val priceHistory by viewModel.priceHistory.collectAsState()
    val product by viewModel.product.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val errorMessage by viewModel.errorMessage.collectAsState()
    
    var selectedPeriod by remember { mutableStateOf("3개월") }
    val periods = listOf("7일", "1개월", "3개월", "6개월", "1년", "전체")

    LaunchedEffect(productId) {
        viewModel.loadProductData(productId.toIntOrNull() ?: 0)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("가격 변동 그래프", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "뒤로가기")
                    }
                },
                actions = {
                    IconButton(onClick = { /* 공유 기능 */ }) {
                        Icon(Icons.Default.Share, contentDescription = "공유")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { paddingValues ->
        if (isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(
                    color = MaterialTheme.colorScheme.primary
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // 📊 상품 정보 카드
                item {
                    val p = product
                    if (p != null) {
                        ProductInfoCard(p)
                    }
                }

                // 📈 기간 선택 버튼
                item {
                    PeriodSelector(
                        periods = periods,
                        selectedPeriod = selectedPeriod,
                        onPeriodSelected = { selectedPeriod = it }
                    )
                }

                // 📊 가격 그래프
                item {
                    if (priceHistory.isNotEmpty()) {
                        PriceChart(
                            priceHistory = viewModel.getFilteredHistory(
                                when (selectedPeriod) {
                                    "7일" -> 7
                                    "30일" -> 30
                                    else -> -1
                                }
                            ),
                            targetPrice = product?.targetPrice ?: 0
                        )
                    }
                }

                // 📈 가격 통계
                item {
                    PriceStatisticsCard(
                        statistics = viewModel.getPriceStatistics()
                    )
                }

                // 🎯 구매 타이밍 조언
                item {
                    BuyingAdviceCard(
                        advice = viewModel.getBuyingTimingAdvice()
                    )
                }

                // 📋 가격 히스토리 리스트
                items(priceHistory.take(10)) { historyItem ->
                    PriceHistoryItem(historyItem)
                }
            }
        }
    }

    // 에러 메시지 처리
    errorMessage?.let { message ->
        LaunchedEffect(message) {
            viewModel.clearError()
        }
    }
}

@Composable
private fun ProductInfoCard(product: PriceChartViewModel.ProductData) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        shape = RoundedCornerShape(16.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 상품 이미지
            AsyncImage(
                model = product.imageUrl,
                contentDescription = product.title,
                modifier = Modifier
                    .size(80.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
            )

            Spacer(modifier = Modifier.width(12.dp))

            // 상품 정보
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = product.brand,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Text(
                    text = product.title,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 2,
                    modifier = Modifier.padding(top = 4.dp)
                )

                Spacer(modifier = Modifier.height(8.dp))

                // 현재 가격 정보
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "${NumberFormat.getNumberInstance().format(product.currentPrice)}원",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )

                    // ✅ originalPrice 사용하여 할인율 표시
                    if (product.originalPrice > product.currentPrice) {
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = MaterialTheme.colorScheme.error
                        ) {
                            Text(
                                text = "${((product.originalPrice - product.currentPrice) * 100 / product.originalPrice).toInt()}%",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onError,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                }

                // ✅ 원가 표시
                if (product.originalPrice > product.currentPrice) {
                    Text(
                        text = "${NumberFormat.getNumberInstance().format(product.originalPrice)}원",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall.copy(textDecoration = androidx.compose.ui.text.style.TextDecoration.LineThrough)
                    )
                }
            }
        }
    }
}

@Composable
private fun PeriodSelector(
    periods: List<String>,
    selectedPeriod: String,
    onPeriodSelected: (String) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        periods.forEach { period ->
            FilterChip(
                selected = period == selectedPeriod,
                onClick = { onPeriodSelected(period) },
                label = { Text(period) },
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
@Suppress("UNUSED_PARAMETER")
private fun PriceChart(
    priceHistory: List<PriceChartViewModel.PriceHistoryData>,
    targetPrice: Int
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(300.dp)
            .padding(horizontal = 16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White)
    ) {
        AndroidView(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            factory = { context ->
                LineChart(context).apply {
                    description.isEnabled = false
                    setTouchEnabled(true)
                    isDragEnabled = true
                    setScaleEnabled(true)
                    setPinchZoom(true)
                    setDrawGridBackground(false)
                    
                    xAxis.position = XAxis.XAxisPosition.BOTTOM
                    xAxis.setDrawGridLines(false)
                    axisRight.isEnabled = false
                    legend.isEnabled = false
                }
            },
            update = { chart ->
                val entries = priceHistory.mapIndexed { index, item ->
                    Entry(index.toFloat(), item.price.toFloat())
                }

                val dataSet = LineDataSet(entries, "Price").apply {
                    color = android.graphics.Color.parseColor("#6200EE")
                    setCircleColor(android.graphics.Color.parseColor("#6200EE"))
                    lineWidth = 2f
                    circleRadius = 4f
                    setDrawValues(false)
                    mode = LineDataSet.Mode.CUBIC_BEZIER
                    setDrawFilled(true)
                    fillColor = android.graphics.Color.parseColor("#BB86FC")
                    fillAlpha = 50
                }

                chart.data = LineData(dataSet)
                chart.invalidate()
            }
        )
    }
}

@Composable
private fun PriceStatisticsCard(statistics: PriceChartViewModel.PriceStatistics) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = "가격 통계",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 12.dp)
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                StatisticItem("최저가", statistics.lowest, Color(0xFF00C853))
                StatisticItem("평균가", statistics.averagePrice, Color(0xFF2962FF))
                StatisticItem("최고가", statistics.highest, Color(0xFFD50000))
            }
        }
    }
}

@Composable
private fun StatisticItem(label: String, price: Int, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = label,
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = "${NumberFormat.getNumberInstance().format(price)}원",
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            color = color
        )
    }
}

@Composable
private fun BuyingAdviceCard(advice: PriceChartViewModel.BuyingAdvice) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        colors = CardDefaults.cardColors(
            containerColor = when (advice.timing) {
                "지금 구매" -> Color(0xFFE8F5E9)
                "조금 더 기다리기" -> Color(0xFFE3F2FD)
                else -> Color(0xFFFFF3E0)
            }
        )
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = when (advice.timing) {
                    "지금 구매" -> Icons.AutoMirrored.Filled.TrendingDown
                    "조금 더 기다리기" -> Icons.Default.Share // Placeholder
                    else -> Icons.AutoMirrored.Filled.TrendingUp
                },
                contentDescription = null,
                tint = when (advice.timing) {
                    "지금 구매" -> Color(0xFF00C853)
                    "조금 더 기다리기" -> Color(0xFF2962FF)
                    else -> Color(0xFFFF6D00)
                },
                modifier = Modifier.size(32.dp)
            )

            Spacer(modifier = Modifier.width(12.dp))

            Column {
                Text(
                    text = advice.timing,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
                Text(
                    text = advice.reason,
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun PriceHistoryItem(historyItem: PriceChartViewModel.PriceHistoryData) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = historyItem.date,
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "${NumberFormat.getNumberInstance().format(historyItem.price)}원",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium
                )

                Spacer(modifier = Modifier.width(8.dp))

                if (historyItem.priceChange != 0) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .background(
                                color = if (historyItem.priceChange > 0) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.tertiaryContainer,
                                shape = RoundedCornerShape(4.dp)
                            )
                            .padding(horizontal = 4.dp, vertical = 2.dp)
                    ) {
                        Icon(
                            imageVector = if (historyItem.priceChange > 0) Icons.AutoMirrored.Filled.TrendingUp else Icons.AutoMirrored.Filled.TrendingDown,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = if (historyItem.priceChange > 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.tertiary
                        )

                        Text(
                            text = "${kotlin.math.abs(historyItem.priceChange)}원",
                            fontSize = 12.sp,
                            color = if (historyItem.priceChange > 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.tertiary,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}
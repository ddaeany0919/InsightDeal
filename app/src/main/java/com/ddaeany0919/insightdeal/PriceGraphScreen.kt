package com.ddaeany0919.insightdeal

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.TrendingDown
import androidx.compose.material.icons.filled.TrendingUp
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
    productId: Int,
    onBackClick: () -> Unit,
    viewModel: PriceChartViewModel = viewModel()
) {
    val product by viewModel.product.collectAsState()
    val priceHistory by viewModel.priceHistory.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val errorMessage by viewModel.errorMessage.collectAsState()
    var selectedPeriod by remember { mutableStateOf("전체") }
    val periods = listOf("7일", "30일", "전체")

    LaunchedEffect(productId) {
        viewModel.loadProductData(productId)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "가격 추이",
                        fontWeight = FontWeight.Bold,
                        fontSize = 20.sp
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "뒤로가기")
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
                    product?.let { ProductInfoCard(it) }
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
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun PeriodSelector(
    periods: List<String>,  // ✅ 제네릭 타입 추가
    selectedPeriod: String,
    onPeriodSelected: (String) -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = "📈 기간 선택",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 12.dp)
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                periods.forEach { period ->
                    FilterChip(
                        onClick = { onPeriodSelected(period) },
                        label = { Text(period) },
                        selected = selectedPeriod == period,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

@Composable
private fun PriceChart(
    priceHistory: List<PriceChartViewModel.PriceHistoryData>,  // ✅ 제네릭 타입 추가
    targetPrice: Int
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .height(300.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = "📊 가격 변동 그래프",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 12.dp)
            )

            AndroidView(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(240.dp),
                factory = { context ->
                    LineChart(context).apply {
                        description.isEnabled = false
                        setTouchEnabled(true)
                        isDragEnabled = true
                        setScaleEnabled(true)
                        setPinchZoom(true)

                        xAxis.apply {
                            position = XAxis.XAxisPosition.BOTTOM
                            setDrawGridLines(false)
                        }

                        axisLeft.apply {
                            setDrawGridLines(true)
                        }

                        axisRight.isEnabled = false
                        legend.isEnabled = false
                    }
                },
                update = { chart ->
                    val entries = priceHistory.mapIndexed { index, item ->
                        Entry(index.toFloat(), item.price.toFloat())
                    }

                    val dataSet = LineDataSet(entries, "가격").apply {
                        color = android.graphics.Color.rgb(255, 152, 0) // 오렌지
                        setCircleColor(android.graphics.Color.rgb(255, 152, 0))
                        lineWidth = 2f
                        circleRadius = 4f
                        setDrawFilled(true)
                        fillColor = android.graphics.Color.argb(50, 255, 152, 0)
                        mode = LineDataSet.Mode.CUBIC_BEZIER
                        setDrawValues(false)
                    }

                    chart.data = LineData(dataSet)
                    chart.animateX(800)
                    chart.invalidate()
                }
            )
        }
    }
}

@Composable
private fun PriceStatisticsCard(
    statistics: PriceChartViewModel.PriceStatistics
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = "📈 가격 통계",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 12.dp)
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // ✅ maxPrice 사용
                StatisticItem(
                    label = "최고가",
                    value = "${NumberFormat.getNumberInstance().format(statistics.maxPrice)}원",
                    icon = Icons.Default.TrendingUp,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.weight(1f)
                )

                // ✅ minPrice 사용
                StatisticItem(
                    label = "최저가",
                    value = "${NumberFormat.getNumberInstance().format(statistics.minPrice)}원",
                    icon = Icons.Default.TrendingDown,
                    color = MaterialTheme.colorScheme.tertiary,
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // ✅ averagePrice 사용
                StatisticItem(
                    label = "평균가",
                    value = "${NumberFormat.getNumberInstance().format(statistics.averagePrice)}원",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f)
                )

                // 변동폭
                StatisticItem(
                    label = "변동폭",
                    value = "${NumberFormat.getNumberInstance().format(statistics.maxPrice - statistics.minPrice)}원",
                    color = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun StatisticItem(
    label: String,
    value: String,
    color: Color,
    modifier: Modifier = Modifier,
    icon: androidx.compose.ui.graphics.vector.ImageVector? = null
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = color,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.height(4.dp))
        }

        Text(
            text = label,
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Text(
            text = value,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            color = color
        )
    }
}

@Composable
private fun BuyingAdviceCard(
    advice: PriceChartViewModel.BuyingAdvice  // ✅ BuyingAdvice 객체 사용
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        colors = CardDefaults.cardColors(
            containerColor = when (advice.timing) {
                "지금 구매" -> MaterialTheme.colorScheme.primaryContainer
                "조금 더 기다리기" -> MaterialTheme.colorScheme.secondaryContainer
                else -> MaterialTheme.colorScheme.surfaceVariant
            }
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "🎯",
                    fontSize = 18.sp
                )

                Spacer(modifier = Modifier.width(8.dp))

                Text(
                    text = "구매 타이밍 조언",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // ✅ timing 사용
            Text(
                text = advice.timing,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )

            // ✅ reason 사용
            Text(
                text = advice.reason,
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp)
            )

            // ✅ savings 사용
            if (advice.savings > 0) {
                Text(
                    text = "💰 예상 절약: ${NumberFormat.getNumberInstance().format(advice.savings)}원",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.tertiary,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
        }
    }
}

@Composable
private fun PriceHistoryItem(
    historyItem: PriceChartViewModel.PriceHistoryData
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                // ✅ siteName 사용
                Text(
                    text = historyItem.siteName,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Medium
                )

                Text(
                    text = historyItem.date,
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Text(
                text = "${NumberFormat.getNumberInstance().format(historyItem.price)}원",
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )

            // ✅ priceChange 사용땜 가격 변동 표시
            if (historyItem.priceChange != 0) {
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = if (historyItem.priceChange > 0) Icons.Default.TrendingUp else Icons.Default.TrendingDown,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = if (historyItem.priceChange > 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.tertiary
                    )

                    Text(
                        text = "${if (historyItem.priceChange > 0) "+" else ""}${NumberFormat.getNumberInstance().format(historyItem.priceChange)}원",
                        fontSize = 12.sp,
                        color = if (historyItem.priceChange > 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.tertiary
                    )
                }
            }
        }
    }
}
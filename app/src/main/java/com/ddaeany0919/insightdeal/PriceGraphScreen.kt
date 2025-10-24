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
import com.github.mikephil.charting.formatter.IndexAxisValueFormatter
import java.text.NumberFormat
import java.util.*

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
            // 스낵바나 토스트로 에러 표시
        }
    }
}

@Composable
private fun ProductInfoCard(product: ProductData) {
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

                // 현재 가격
                Text(
                    text = "${NumberFormat.getInstance().format(product.currentPrice)}원",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )

                // 가격 변동
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(top = 4.dp)
                ) {
                    val isIncrease = product.priceChangePercent > 0
                    Icon(
                        imageVector = if (isIncrease) Icons.Default.TrendingUp else Icons.Default.TrendingDown,
                        contentDescription = null,
                        size = 16.dp,
                        tint = if (isIncrease) Color.Red else Color.Blue
                    )
                    
                    Text(
                        text = "${if (isIncrease) "+" else ""}${product.priceChangePercent}%",
                        fontSize = 12.sp,
                        color = if (isIncrease) Color.Red else Color.Blue,
                        modifier = Modifier.padding(start = 4.dp)
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
            val isSelected = period == selectedPeriod
            
            FilterChip(
                onClick = { onPeriodSelected(period) },
                label = { Text(period) },
                selected = isSelected,
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.primary,
                    selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                )
            )
        }
    }
}

@Composable
private fun PriceChart(
    priceHistory: List<PriceHistoryData>,
    targetPrice: Int
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(300.dp)
            .padding(horizontal = 16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = "가격 추이 그래프",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 12.dp)
            )

            AndroidView(
                factory = { context ->
                    LineChart(context).apply {
                        description.isEnabled = false
                        setTouchEnabled(true)
                        isDragEnabled = true
                        setScaleEnabled(true)
                        setPinchZoom(true)

                        // X축 설정
                        xAxis.apply {
                            position = XAxis.XAxisPosition.BOTTOM
                            setDrawGridLines(false)
                            granularity = 1f
                        }

                        // 왼쪽 Y축 설정
                        axisLeft.apply {
                            setDrawGridLines(true)
                            isEnabled = true
                        }

                        // 오른쪽 Y축 비활성화
                        axisRight.isEnabled = false

                        // 범례 비활성화
                        legend.isEnabled = false
                    }
                },
                update = { chart ->
                    if (priceHistory.isNotEmpty()) {
                        val entries = priceHistory.mapIndexed { index, item ->
                            Entry(index.toFloat(), item.price.toFloat())
                        }

                        val dataSet = LineDataSet(entries, "가격").apply {
                            color = android.graphics.Color.parseColor("#FF6B35")
                            lineWidth = 2f
                            setDrawCircles(true)
                            setCircleColor(android.graphics.Color.parseColor("#FF6B35"))
                            circleRadius = 4f
                            setDrawValues(false)
                            mode = LineDataSet.Mode.CUBIC_BEZIER
                            setDrawFilled(true)
                            fillColor = android.graphics.Color.parseColor("#33FF6B35")
                        }

                        chart.data = LineData(dataSet)
                        chart.invalidate()
                    }
                },
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = 8.dp)
            )
        }
    }
}

@Composable
private fun PriceStatisticsCard(statistics: PriceStatistics) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
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
                StatisticItem("현재", "${NumberFormat.getInstance().format(statistics.current)}원")
                StatisticItem("최저", "${NumberFormat.getInstance().format(statistics.lowest)}원")
                StatisticItem("최고", "${NumberFormat.getInstance().format(statistics.highest)}원")
                StatisticItem("평균", "${NumberFormat.getInstance().format(statistics.average)}원")
            }
        }
    }
}

@Composable
private fun StatisticItem(label: String, value: String) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = label,
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(top = 4.dp)
        )
    }
}

@Composable
private fun BuyingAdviceCard(advice: String) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = "🎯 구매 타이밍 조언",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            
            Text(
                text = advice,
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                lineHeight = 20.sp
            )
        }
    }
}

@Composable
private fun PriceHistoryItem(historyItem: PriceHistoryData) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = historyItem.date,
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            
            Text(
                text = "${NumberFormat.getInstance().format(historyItem.price)}원",
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium
            )
        }
    }
}
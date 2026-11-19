package com.ddaeany0919.insightdeal.presentation.price

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.TrendingDown
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.ddaeany0919.insightdeal.data.util.ImageUrlResolver
import com.ddaeany0919.insightdeal.presentation.A11yIconButton
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

    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }

    // 에러 메시지 노출 후 소거 (재시도 액션 지원)
    LaunchedEffect(errorMessage) {
        val message = errorMessage ?: return@LaunchedEffect
        val result = snackbarHostState.showSnackbar(
            message = message,
            actionLabel = "다시 시도",
            duration = SnackbarDuration.Long
        )
        if (result == SnackbarResult.ActionPerformed) {
            viewModel.loadProductData(productId.toIntOrNull() ?: 0)
        }
        viewModel.clearError()
    }

    Scaffold(
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("가격 변동 그래프", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    A11yIconButton(onClick = onBackClick, contentDescription = "뒤로가기") {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                },
                actions = {
                    A11yIconButton(
                        onClick = {
                            val title = product?.title ?: "얼만데 가격 변동 그래프"
                            val priceText = product?.let {
                                "${NumberFormat.getNumberInstance().format(it.currentPrice)}원"
                            }.orEmpty()
                            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_TEXT, "$title $priceText".trim())
                            }
                            runCatching {
                                context.startActivity(Intent.createChooser(shareIntent, "공유하기"))
                            }
                        },
                        contentDescription = "공유"
                    ) {
                        Icon(Icons.Default.Share, contentDescription = null)
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

                // 📊 가격 그래프 (<2건일 때 표준 안내 카피 노출)
                item {
                    val filteredHistory = viewModel.getFilteredHistory(
                        when (selectedPeriod) {
                            "7일" -> 7
                            "1개월" -> 30
                            "3개월" -> 90
                            "6개월" -> 180
                            "1년" -> 365
                            else -> -1
                        }
                    )
                    if (filteredHistory.size >= 2) {
                        PriceChart(
                            priceHistory = filteredHistory,
                            targetPrice = product?.targetPrice ?: 0
                        )
                    } else {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp),
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant
                            )
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(20.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Info,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = PriceChartViewModel.INSUFFICIENT_HISTORY_MESSAGE,
                                    fontSize = 14.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
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
            // 상품 이미지 (ImageUrlResolver + 백엔드 프록시 폴백 적용)
            val resolvedPrimaryUrl = remember(product.imageUrl) {
                ImageUrlResolver.resolve(product.imageUrl, targetWidth = 200)
            }
            val fallbackProxyUrl = remember(product.imageUrl) {
                ImageUrlResolver.getFallbackBackendProxyUrl(product.imageUrl)
            }
            var activeUrl by remember(resolvedPrimaryUrl) { mutableStateOf(resolvedPrimaryUrl) }

            AsyncImage(
                model = activeUrl,
                contentDescription = product.title,
                onError = {
                    if (fallbackProxyUrl != null && activeUrl != fallbackProxyUrl) {
                        activeUrl = fallbackProxyUrl
                    }
                },
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
    val lineColor = MaterialTheme.colorScheme.primary.toArgb()
    val axisTextColor = MaterialTheme.colorScheme.onSurfaceVariant.toArgb()
    val fillAlphaColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.25f).toArgb()

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(300.dp)
            .padding(horizontal = 16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
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
                    xAxis.textColor = axisTextColor
                    axisLeft.textColor = axisTextColor
                    axisRight.isEnabled = false
                    legend.isEnabled = false
                }
            },
            update = { chart ->
                chart.xAxis.textColor = axisTextColor
                chart.axisLeft.textColor = axisTextColor

                val entries = priceHistory.mapIndexed { index, item ->
                    Entry(index.toFloat(), item.price.toFloat())
                }

                val dataSet = LineDataSet(entries, "Price").apply {
                    color = lineColor
                    setCircleColor(lineColor)
                    lineWidth = 2f
                    circleRadius = 4f
                    setDrawValues(false)
                    mode = LineDataSet.Mode.CUBIC_BEZIER
                    setDrawFilled(true)
                    fillColor = fillAlphaColor
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
    val (containerColor, contentColor) = when (advice.timing) {
        "지금 구매" -> MaterialTheme.colorScheme.primaryContainer to MaterialTheme.colorScheme.onPrimaryContainer
        "조금 더 기다리기" -> MaterialTheme.colorScheme.secondaryContainer to MaterialTheme.colorScheme.onSecondaryContainer
        else -> MaterialTheme.colorScheme.surfaceVariant to MaterialTheme.colorScheme.onSurfaceVariant
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        colors = CardDefaults.cardColors(
            containerColor = containerColor
        )
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = when (advice.timing) {
                    "지금 구매" -> Icons.AutoMirrored.Filled.TrendingDown
                    "조금 더 기다리기" -> Icons.Default.Info
                    else -> Icons.AutoMirrored.Filled.TrendingUp
                },
                contentDescription = null,
                tint = contentColor,
                modifier = Modifier.size(32.dp)
            )

            Spacer(modifier = Modifier.width(12.dp))

            Column {
                Text(
                    text = advice.timing,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    color = contentColor
                )
                Text(
                    text = advice.reason,
                    fontSize = 14.sp,
                    color = contentColor.copy(alpha = 0.85f)
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
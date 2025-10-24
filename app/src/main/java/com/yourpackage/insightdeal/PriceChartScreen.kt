package com.yourpackage.insightdeal

import android.graphics.Color as AndroidColor
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import com.github.mikephil.charting.formatter.ValueFormatter
import kotlinx.coroutines.launch
import java.text.DecimalFormat
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PriceChartScreen(
    productId: Int,
    viewModel: PriceChartViewModel = viewModel(),
    onNavigateBack: () -> Unit = {}
) {
    val product by viewModel.product.collectAsState()
    val priceHistory by viewModel.priceHistory.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    
    val pagerState = rememberPagerState(pageCount = { 3 }) // 7일, 30일, 전체
    val scope = rememberCoroutineScope()
    
    LaunchedEffect(productId) {
        viewModel.loadProductData(productId)
    }
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White)
    ) {
        // 🎨 폴센트 스타일 헤더
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = PrimaryOrange,
            shadowElevation = 4.dp
        ) {
            Row(
                modifier = Modifier
                    .padding(16.dp)
                    .fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onNavigateBack,
                    modifier = Modifier
                        .background(
                            Color.White.copy(alpha = 0.2f),
                            CircleShape
                        )
                        .size(40.dp)
                ) {
                    Icon(
                        Icons.Default.ArrowBack,
                        contentDescription = "뒤로가기",
                        tint = Color.White
                    )
                }
                
                Spacer(modifier = Modifier.width(16.dp))
                
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "가격 분석",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    if (product != null) {
                        Text(
                            text = product!!.title.take(30) + "...",
                            fontSize = 14.sp,
                            color = Color.White.copy(alpha = 0.8f),
                            maxLines = 1
                        )
                    }
                }
            }
        }
        
        if (isLoading) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = PrimaryOrange)
            }
        } else if (product != null) {
            // 📈 메인 컨텐츠
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
            ) {
                // 📸 상품 정보 카드
                ProductInfoCard(product = product!!)
                
                // 📉 가격 요약 카드
                PriceSummaryCard(
                    product = product!!,
                    priceHistory = priceHistory
                )
                
                // 📊 기간 선택 탭
                PeriodTabs(
                    pagerState = pagerState,
                    onTabClick = { index ->
                        scope.launch {
                            pagerState.animateScrollToPage(index)
                        }
                    }
                )
                
                // 📈 가격 그래프
                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier.height(320.dp)
                ) { page ->
                    val periodData = when (page) {
                        0 -> priceHistory.takeLast(7) // 7일
                        1 -> priceHistory.takeLast(30) // 30일
                        2 -> priceHistory // 전체
                        else -> priceHistory
                    }
                    
                    PriceChart(
                        priceData = periodData,
                        product = product!!
                    )
                }
                
                // 🎯 구매 타이밍 가이드
                BuyingTimingCard(
                    product = product!!,
                    priceHistory = priceHistory
                )
                
                // 🔔 알림 설정 카드
                AlertSettingsCard(
                    product = product!!,
                    onUpdateTargetPrice = { newPrice ->
                        viewModel.updateTargetPrice(newPrice)
                    }
                )
                
                Spacer(modifier = Modifier.height(100.dp))
            }
        }
    }
}

@Composable
fun ProductInfoCard(product: ProductData) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 상품 이미지
            AsyncImage(
                model = product.imageUrl,
                contentDescription = "상품 이미지",
                modifier = Modifier
                    .size(100.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(LightGray),
                contentScale = ContentScale.Crop
            )
            
            // 상품 정보
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = product.title,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = DarkGray,
                    maxLines = 2
                )
                
                if (product.brand.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = product.brand,
                        fontSize = 14.sp,
                        color = Color.Gray
                    )
                }
                
                Spacer(modifier = Modifier.height(12.dp))
                
                // 현재 가격
                Row(
                    verticalAlignment = Alignment.Baseline,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "${product.currentPrice:,}원",
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                        color = PrimaryOrange
                    )
                    
                    if (product.discountRate > 0) {
                        Text(
                            text = "${product.discountRate}% 할인",
                            fontSize = 14.sp,
                            color = Red,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun PriceSummaryCard(
    product: ProductData,
    priceHistory: List<PriceHistoryData>
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp)
        ) {
            Text(
                text = "💰 가격 분석",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = DarkGray
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // 4개 열 그리드
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                PriceSummaryItem(
                    label = "현재",
                    value = "${product.currentPrice:,}원",
                    color = PrimaryOrange,
                    modifier = Modifier.weight(1f)
                )
                
                PriceSummaryItem(
                    label = "최저",
                    value = "${product.lowestPrice:,}원",
                    color = Blue,
                    isHighlight = product.currentPrice == product.lowestPrice,
                    modifier = Modifier.weight(1f)
                )
                
                PriceSummaryItem(
                    label = "최고",
                    value = "${product.highestPrice:,}원",
                    color = Red,
                    modifier = Modifier.weight(1f)
                )
                
                val averagePrice = if (priceHistory.isNotEmpty()) {
                    priceHistory.sumOf { it.price } / priceHistory.size
                } else product.currentPrice
                
                PriceSummaryItem(
                    label = "평균",
                    value = "${averagePrice:,}원",
                    color = Color.Gray,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
fun PriceSummaryItem(
    label: String,
    value: String,
    color: Color,
    isHighlight: Boolean = false,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = label,
            fontSize = 13.sp,
            color = Color.Gray
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = value,
            fontSize = 16.sp,
            fontWeight = if (isHighlight) FontWeight.Bold else FontWeight.Medium,
            color = if (isHighlight) color else DarkGray,
            textAlign = TextAlign.Center
        )
        if (isHighlight) {
            Box(
                modifier = Modifier
                    .size(4.dp)
                    .background(color, CircleShape)
            )
        }
    }
}

@Composable
fun PeriodTabs(
    pagerState: androidx.compose.foundation.pager.PagerState,
    onTabClick: (Int) -> Unit
) {
    val tabTitles = listOf("7일", "30일", "전체")
    
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        horizontalArrangement = Arrangement.Center
    ) {
        tabTitles.forEachIndexed { index, title ->
            val isSelected = pagerState.currentPage == index
            
            Surface(
                modifier = Modifier
                    .clickable { onTabClick(index) }
                    .padding(horizontal = 4.dp),
                shape = RoundedCornerShape(20.dp),
                color = if (isSelected) PrimaryOrange else LightGray
            ) {
                Text(
                    text = title,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                    fontSize = 14.sp,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                    color = if (isSelected) Color.White else Color.Gray
                )
            }
        }
    }
}

@Composable
fun PriceChart(
    priceData: List<PriceHistoryData>,
    product: ProductData
) {
    if (priceData.isEmpty()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    Icons.Default.ShowChart,
                    contentDescription = "데이터 없음",
                    modifier = Modifier.size(48.dp),
                    tint = Color.Gray.copy(alpha = 0.5f)
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "가격 데이터를 수집 중입니다...",
                    color = Color.Gray,
                    fontSize = 16.sp
                )
            }
        }
        return
    }
    
    AndroidView(
        factory = { context ->
            LineChart(context).apply {
                setupChart(this, priceData, product)
            }
        },
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    )
}

private fun setupChart(
    chart: LineChart,
    priceData: List<PriceHistoryData>,
    product: ProductData
) {
    val entries = priceData.mapIndexed { index, data ->
        Entry(index.toFloat(), data.price.toFloat())
    }
    
    val dataSet = LineDataSet(entries, "가격").apply {
        color = AndroidColor.parseColor("#FF6B35") // PrimaryOrange
        setCircleColor(AndroidColor.parseColor("#FF6B35"))
        lineWidth = 3f
        circleRadius = 4f
        setDrawFilled(true)
        fillColor = AndroidColor.parseColor("#FFE5DB")
        fillAlpha = 100
        setDrawValues(false)
        mode = LineDataSet.Mode.CUBIC_BEZIER // 부드러운 곡선
    }
    
    chart.apply {
        data = LineData(dataSet)
        description.isEnabled = false
        legend.isEnabled = false
        
        // X축 설정
        xAxis.apply {
            position = com.github.mikephil.charting.components.XAxis.XAxisPosition.BOTTOM
            setDrawGridLines(false)
            granularity = 1f
            textColor = AndroidColor.GRAY
        }
        
        // Y축 설정
        axisLeft.apply {
            setDrawGridLines(true)
            gridColor = AndroidColor.LTGRAY
            textColor = AndroidColor.GRAY
            valueFormatter = object : ValueFormatter() {
                override fun getFormattedValue(value: Float): String {
                    return DecimalFormat("#,###").format(value.toInt()) + "원"
                }
            }
        }
        
        axisRight.isEnabled = false
        
        // 애니메이션
        animateX(1000)
        invalidate()
    }
}

@Composable
fun BuyingTimingCard(
    product: ProductData,
    priceHistory: List<PriceHistoryData>
) {
    val isGoodTiming = product.currentPrice <= product.targetPrice || 
                      product.currentPrice == product.lowestPrice
    
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isGoodTiming) Green.copy(alpha = 0.1f) else Color.White
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Icon(
                    if (isGoodTiming) Icons.Default.TrendingDown else Icons.Default.TrendingUp,
                    contentDescription = "타이밍",
                    tint = if (isGoodTiming) Green else PrimaryOrange,
                    modifier = Modifier.size(28.dp)
                )
                
                Column {
                    Text(
                        text = if (isGoodTiming) "🎉 지금이 구매 타이밍!" else "🤔 좀 더 기다려볼까요?",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isGoodTiming) Green else DarkGray
                    )
                    
                    Text(
                        text = if (isGoodTiming) "목표가격 또는 최저가에 도달했습니다" else "좋은 가격으로 떨어질 때까지 알림을 드릴게요",
                        fontSize = 14.sp,
                        color = Color.Gray
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlertSettingsCard(
    product: ProductData,
    onUpdateTargetPrice: (Int) -> Unit
) {
    var showDialog by remember { mutableStateOf(false) }
    var targetPriceText by remember { mutableStateOf(product.targetPrice.toString()) }
    
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "🔔 알림 설정",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = DarkGray
                    )
                    Text(
                        text = "목표 가격: ${product.targetPrice:,}원",
                        fontSize = 14.sp,
                        color = Color.Gray
                    )
                }
                
                Button(
                    onClick = { showDialog = true },
                    colors = ButtonDefaults.buttonColors(containerColor = PrimaryOrange),
                    shape = RoundedCornerShape(20.dp)
                ) {
                    Text("수정")
                }
            }
        }
    }
    
    // 목표가격 수정 다이얼로그
    if (showDialog) {
        AlertDialog(
            onDismissRequest = { showDialog = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        val newPrice = targetPriceText.toIntOrNull()
                        if (newPrice != null && newPrice > 0) {
                            onUpdateTargetPrice(newPrice)
                            showDialog = false
                        }
                    }
                ) {
                    Text("저장", color = PrimaryOrange)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDialog = false }) {
                    Text("취소")
                }
            },
            title = { Text("목표 가격 설정") },
            text = {
                OutlinedTextField(
                    value = targetPriceText,
                    onValueChange = { targetPriceText = it.filter { char -> char.isDigit() } },
                    label = { Text("목표 가격") },
                    trailingIcon = { Text("원") },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        )
    }
}

// 데이터 모델
data class PriceHistoryData(
    val price: Int,
    val date: String
)
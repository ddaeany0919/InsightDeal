package com.example.insightdeal.ui

import android.content.Intent
import android.net.Uri
import android.util.Log
import android.webkit.WebView
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.insightdeal.model.DealDetail
import com.example.insightdeal.model.DealItem
import com.example.insightdeal.model.PriceHistoryItem
import com.example.insightdeal.network.ApiClient
import com.example.insightdeal.viewmodel.DealDetailState
import com.example.insightdeal.viewmodel.PriceHistoryState
import com.patrykandpatrick.vico.compose.axis.horizontal.rememberBottomAxis
import com.patrykandpatrick.vico.compose.axis.vertical.rememberStartAxis
import com.patrykandpatrick.vico.compose.chart.Chart
import com.patrykandpatrick.vico.compose.chart.line.lineChart
import com.patrykandpatrick.vico.core.axis.AxisPosition
import com.patrykandpatrick.vico.core.axis.formatter.AxisValueFormatter
import com.patrykandpatrick.vico.core.entry.ChartEntryModelProducer
import com.patrykandpatrick.vico.core.entry.entryOf
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import android.util.Base64

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DealDetailScreen(
    detailState: DealDetailState,
    priceHistoryState: PriceHistoryState,
    onBackClick: () -> Unit,
    onDealClick: (Int) -> Unit
) {
    val context = LocalContext.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("상세 분석 리포트", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.White)
            )
        },
        containerColor = Color(0xFFF2F4F7)
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            when (detailState) {
                is DealDetailState.Loading -> {
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                }
                is DealDetailState.Success -> {
                    val deal = detailState.dealDetail
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(vertical = 16.dp)
                    ) {
                        InfoCard(deal = deal)
                        Spacer(modifier = Modifier.height(16.dp))

                        if (!deal.relatedDeals.isNullOrEmpty()) {
                            RelatedDealsCard(
                                deals = deal.relatedDeals ?: emptyList(),
                                onDealClick = onDealClick
                            )
                        } else {
                            Log.d("DealDetailScreen", "관련 딜이 존재하지 않습니다.")
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        PromotionInfoCard(htmlContent = deal.contentHtml)

                        Spacer(modifier = Modifier.height(16.dp))

                        PriceHistoryCard(priceHistoryState = priceHistoryState)

                        Spacer(modifier = Modifier.height(24.dp))

                        Button(
                            onClick = {
                                Log.d("DealDetailScreen", "🔥 버튼 클릭됨!")

                                val rawUrl = deal.ecommerceLink?.takeIf { it.isNotBlank() } ?: deal.postLink

                                rawUrl?.let { url ->
                                    Log.d("DealDetailScreen", "Selected URL: '$url'")

                                    try {
                                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                                        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

                                        // ✨ resolveActivity 체크 제거하고 강제 실행
                                        context.startActivity(intent)
                                        Log.d("DealDetailScreen", "Intent 실행 완료: $url")

                                    } catch (e: Exception) {
                                        Log.e("DealDetailScreen", "Intent 실행 실패: $url", e)

                                        // ✨ 실패 시 대체 방법: 시스템 선택기 표시
                                        try {
                                            val chooser = Intent.createChooser(
                                                Intent(Intent.ACTION_VIEW, Uri.parse(url)),
                                                "브라우저 선택"
                                            )
                                            context.startActivity(chooser)
                                        } catch (e2: Exception) {
                                            Log.e("DealDetailScreen", "Chooser도 실패: $url", e2)
                                        }
                                    }
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(52.dp)
                                .padding(horizontal = 16.dp),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("상품 보러가기", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
                is DealDetailState.Error -> {
                    Text(
                        text = "상세 정보를 불러오지 못했습니다: ${detailState.message}",
                        modifier = Modifier.align(Alignment.Center),
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        }
    }
}

@Composable
fun InfoCard(deal: DealDetail) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        val context = LocalContext.current

        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = deal.shopName,
                    fontSize = 12.sp,
                    color = Color.White,
                    modifier = Modifier
                        .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(6.dp))
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                )

                if (!deal.category.isNullOrBlank()) {
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = deal.category ?: "",
                        fontSize = 12.sp,
                        color = Color.White,
                        modifier = Modifier
                            .background(MaterialTheme.colorScheme.tertiary, RoundedCornerShape(6.dp))
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = deal.title,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                lineHeight = 30.sp
            )
            Spacer(modifier = Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom
            ) {
                Text(
                    text = deal.price,
                    fontSize = 28.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = "배송비: ${deal.shippingFee}",
                    fontSize = 14.sp,
                    color = Color.Gray
                )
            }

            Divider(modifier = Modifier.padding(vertical = 16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("출처: ${deal.community}", fontSize = 12.sp, color = Color.Gray)

                if (deal.postLink != null) {
                    TextButton(onClick = {
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(deal.postLink))
                        context.startActivity(intent)
                    }) {
                        Text("원본 게시물 보기", fontSize = 12.sp)
                    }
                }
            }
        }
    }
}

@Composable
fun PromotionInfoCard(htmlContent: String?) {
    if (htmlContent.isNullOrBlank()) {
        return
    }
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "프로모션 정보 ℹ️",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(8.dp))

            if (!htmlContent.isNullOrBlank()) {
                AndroidView(factory = { context ->
                    WebView(context).apply {
                        settings.javaScriptEnabled = false
                        setBackgroundColor(android.graphics.Color.TRANSPARENT)
                        val styledHtml =
                            "<html><head><style>body{color:#333; line-height:1.6;} a{color:#007BFF; text-decoration:none;}</style></head><body>$htmlContent</body></html>"
                        loadDataWithBaseURL(null, styledHtml, "text/html", "UTF-8", null)
                    }
                }, modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp))
            }
        }
    }
}

@Composable
fun RelatedDealsCard(deals: List<DealItem>, onDealClick: (Int) -> Unit) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = "이 글의 다른 상품 🎁",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 16.dp)
        )
        Spacer(modifier = Modifier.height(8.dp))
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(deals, key = { it.id }) { deal ->
                CompactDealCard(deal = deal, onClick = { onDealClick(deal.id) })
            }
        }
    }
}

@Composable
fun CompactDealCard(deal: DealItem, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .width(150.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column {
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(ApiClient.BASE_URL.removeSuffix("/") + deal.imageUrl)
                    .crossfade(true)
                    .build(),
                contentDescription = deal.title,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .clip(RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp)),
                contentScale = ContentScale.Crop
            )
            Column(modifier = Modifier.padding(8.dp)) {
                Text(
                    text = deal.title,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.height(34.dp)
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = deal.price,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 15.sp,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                )
            }
        }
    }
}

@Composable
fun PriceHistoryCard(priceHistoryState: PriceHistoryState) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "가격 변동 그래프 📈",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(8.dp))
            Box(modifier = Modifier.height(200.dp)) {
                when (priceHistoryState) {
                    is PriceHistoryState.Loading -> {
                        CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                    }
                    is PriceHistoryState.Success -> {
                        PriceChart(history = priceHistoryState.history)
                    }
                    is PriceHistoryState.Error -> {
                        Text(
                            text = priceHistoryState.message,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.align(Alignment.Center)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun PriceChart(history: List<PriceHistoryItem>) {
    val chartEntryModelProducer = remember { ChartEntryModelProducer() }

    fun parsePrice(priceString: String): Float {
        return priceString.replace(Regex("[^0-9.]"), "").toFloatOrNull() ?: 0f
    }

    val inputFormatter = DateTimeFormatter.ISO_OFFSET_DATE_TIME
    val outputFormatter = DateTimeFormatter.ofPattern("MM/dd")

    val entries = remember(history) {
        history.mapIndexedNotNull { index, item ->
            val date = try {
                LocalDate.parse(item.checkedAt, inputFormatter)
            } catch (e: Exception) {
                Log.w("PriceChart", "날짜 파싱 실패: ${item.checkedAt}")
                null
            }
            if (date != null) {
                entryOf(index.toFloat(), parsePrice(item.price)) to date
            } else null
        }
    }

    val (chartEntries, dates) = remember(entries) { entries.unzip() }

    chartEntryModelProducer.setEntries(chartEntries)

    val bottomAxisValueFormatter = AxisValueFormatter<AxisPosition.Horizontal.Bottom> { value, _ ->
        dates.getOrNull(value.toInt())?.format(outputFormatter) ?: ""
    }

    if (chartEntries.isNotEmpty()) {
        Chart(
            chart = lineChart(),
            chartModelProducer = chartEntryModelProducer,
            startAxis = rememberStartAxis(),
            bottomAxis = rememberBottomAxis(valueFormatter = bottomAxisValueFormatter),
        )
    } else {
        Box(modifier = Modifier.fillMaxSize()) {
            Text("차트를 표시할 데이터가 없습니다.", modifier = Modifier.align(Alignment.Center))
        }
    }
}

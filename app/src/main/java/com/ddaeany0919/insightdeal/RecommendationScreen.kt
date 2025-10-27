package com.ddaeany0919.insightdeal

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.ddaeany0919.insightdeal.models.DealItem
import java.text.NumberFormat
import com.ddaeany0919.insightdeal.ui.A11yIconButton
import com.ddaeany0919.insightdeal.ui.rememberSavedLazyListState
import com.ddaeany0919.insightdeal.ui.rememberMinuteTicker
import com.ddaeany0919.insightdeal.ui.SavingTextStrong
import com.ddaeany0919.insightdeal.ui.BadgeBlue
import com.ddaeany0919.insightdeal.ui.BadgeOrange
import com.ddaeany0919.insightdeal.ui.BadgePurple
import com.ddaeany0919.insightdeal.ui.BadgeGreen
import com.ddaeany0919.insightdeal.ui.OnBadge

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecommendationScreen(
    onDealClick: (DealItem) -> Unit,
    viewModel: RecommendationViewModel = viewModel()
) {
    val context = LocalContext.current
    val recommendations by viewModel.recommendations.collectAsState()
    val insights by viewModel.insights.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val errorMessage by viewModel.errorMessage.collectAsState()

    // 실시간 relative time 갱신 트리거 (필요 시 확장)
    val _ = rememberMinuteTicker()

    // 리스트 위치 복원
    val listState = rememberSavedLazyListState(key = "recommend_list")

    LaunchedEffect(Unit) {
        viewModel.loadPersonalizedRecommendations()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "추천",
                        fontWeight = FontWeight.Bold,
                        fontSize = 20.sp
                    )
                },
                navigationIcon = {
                    A11yIconButton(onClick = { /* TODO: back */ }, contentDescription = "뒤로가기") {
                        Icon(Icons.Default.ArrowBack, contentDescription = null)
                    }
                },
                actions = {
                    A11yIconButton(onClick = { viewModel.refresh() }, contentDescription = "새로고침") {
                        Icon(Icons.Default.Refresh, contentDescription = null)
                    }
                }
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(MaterialTheme.colorScheme.background)
        ) {
            when {
                isLoading -> {
                    CircularProgressIndicator(
                        modifier = Modifier.align(Alignment.Center),
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                else -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                        contentPadding = PaddingValues(bottom = 24.dp),
                        state = listState
                    ) {
                        item { PersonalizationStatusCard(insights) }

                        if (recommendations.isNotEmpty()) {
                            item {
                                RecommendationSection(
                                    title = "🤖 나를 위한 AI 추천",
                                    subtitle = "${insights?.totalInteractions ?: 0}번의 상호작용 기반",
                                    deals = recommendations.take(10),
                                    onDealClick = onDealClick,
                                    onFeedback = { dealId, isPositive ->
                                        viewModel.provideFeedback(dealId, isPositive)
                                    }
                                )
                            }
                        }

                        insights?.topCategories?.forEach { categoryInfo ->
                            item {
                                val categoryName = categoryInfo.split(" ")[0]
                                CategoryRecommendationSection(
                                    categoryName = categoryName,
                                    deals = viewModel.getCategoryRecommendations(categoryName),
                                    onDealClick = onDealClick
                                )
                            }
                        }

                        insights?.topBrands?.forEach { brandInfo ->
                            item {
                                val brandName = brandInfo.split(" ")[0]
                                BrandRecommendationSection(
                                    brandName = brandName,
                                    deals = viewModel.getBrandRecommendations(brandName),
                                    onDealClick = onDealClick
                                )
                            }
                        }

                        if (!errorMessage.isNullOrEmpty()) {
                            item { ErrorCard(message = errorMessage ?: "", onDismiss = { viewModel.clearError() }) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionHeader(title: String, subtitle: String? = null) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
    ) {
        Text(text = title, fontSize = 16.sp, fontWeight = FontWeight.Bold)
        if (!subtitle.isNullOrEmpty()) {
            Text(
                text = subtitle,
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 2.dp)
            )
        }
    }
}

@Composable
private fun DealCard(
    deal: DealItem,
    onClick: () -> Unit,
    onPositive: () -> Unit,
    onNegative: () -> Unit
) {
    Card(
        modifier = Modifier
            .width(220.dp)
            .heightIn(min = 160.dp)
            .clickable { onClick() },
        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            AsyncImage(
                model = deal.imageUrl ?: "",
                contentDescription = deal.title,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(110.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = deal.title,
                fontSize = 14.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                fontWeight = FontWeight.Medium
            )
            Spacer(modifier = Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                val priceText = deal.price?.let { NumberFormat.getInstance().format(it) + "원" } ?: "-"
                Text(
                    text = priceText,
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold
                )
                if ((deal.discountRate ?: 0) > 0) {
                    Spacer(Modifier.width(6.dp))
                    // 할인율 대비 강화 텍스트
                    SavingTextStrong("-${deal.discountRate}%")
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                A11yIconButton(onClick = onNegative, contentDescription = "별로예요") {
                    Icon(Icons.Default.ThumbDown, contentDescription = null)
                }
                Spacer(Modifier.width(8.dp))
                A11yIconButton(onClick = onPositive, contentDescription = "좋아요") {
                    Icon(Icons.Default.ThumbUp, contentDescription = null)
                }
            }
        }
    }
}

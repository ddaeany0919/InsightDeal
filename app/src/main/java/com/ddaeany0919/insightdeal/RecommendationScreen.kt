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
                    IconButton(onClick = { /* TODO: back */ }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "뒤로가기")
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.refresh() }) {
                        Icon(Icons.Default.Refresh, contentDescription = "새로고침")
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
                        contentPadding = PaddingValues(bottom = 24.dp)
                    ) {
                        // 🎯 개인화 상태 헤더
                        item {
                            PersonalizationStatusCard(insights)
                        }

                        // 🔥 맞춤 추천 섹션
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

                        // 📊 카테고리별 추천
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

                        // 🏷️ 브랜드별 추천
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

                        // 에러 메시지(있다면)
                        if (!errorMessage.isNullOrEmpty()) {
                            item {
                                ErrorCard(message = errorMessage ?: "", onDismiss = { viewModel.clearError() })
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PersonalizationStatusCard(insights: PersonalizationInsights?) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        )
    ) {
        Column(
            modifier = Modifier.padding(20.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(bottom = 12.dp)
            ) {
                Icon(
                    Icons.Default.Psychology,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "AI 개인화 상태",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }

            insights?.let { data ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    InfoChip(
                        icon = Icons.Default.Timeline,
                        label = "학습 데이터",
                        value = "${data.totalInteractions}개"
                    )
                    InfoChip(
                        icon = Icons.Default.Category,
                        label = "관심 분야",
                        value = "${data.topCategories.size}개"
                    )
                    InfoChip(
                        icon = Icons.Default.Speed,
                        label = "정확도",
                        value = "${data.profileCompleteness}%"
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = "관심 분야: ${data.topCategories.joinToString(", ")}",
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                )

                if (data.favoriteKeywords.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "관심 키워드: ${data.favoriteKeywords.joinToString(", ")}",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                    )
                }
            } ?: run {
                Text(
                    text = "개인화 데이터를 수집하는 중입니다...",
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                )
            }
        }
    }
}

@Composable
private fun InfoChip(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    value: String
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            icon,
            contentDescription = null,
            modifier = Modifier.size(20.dp),
            tint = MaterialTheme.colorScheme.primary
        )
        Text(
            text = value,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(top = 4.dp)
        )
        Text(
            text = label,
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
        )
    }
}

@Composable
private fun RecommendationSection(
    title: String,
    subtitle: String? = null,
    deals: List<DealItem>,
    onDealClick: (DealItem) -> Unit,
    onFeedback: (dealId: Int, isPositive: Boolean) -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth()
    ) {
        SectionHeader(title = title, subtitle = subtitle)

        LazyRow(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(horizontal = 16.dp)
        ) {
            items(deals) { deal ->
                DealCard(
                    deal = deal,
                    onClick = { onDealClick(deal) },
                    onPositive = { onFeedback(deal.id, true) },
                    onNegative = { onFeedback(deal.id, false) }
                )
            }
        }
    }
}

@Composable
private fun CategoryRecommendationSection(
    categoryName: String,
    deals: List<DealItem>,
    onDealClick: (DealItem) -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth()
    ) {
        SectionHeader(
            title = "📊 $categoryName 추천",
            subtitle = "관심 카테고리 기반 추천"
        )
        LazyRow(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(horizontal = 16.dp)
        ) {
            items(deals) { deal ->
                DealCard(
                    deal = deal,
                    onClick = { onDealClick(deal) },
                    onPositive = {},
                    onNegative = {}
                )
            }
        }
    }
}

@Composable
private fun BrandRecommendationSection(
    brandName: String,
    deals: List<DealItem>,
    onDealClick: (DealItem) -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth()
    ) {
        SectionHeader(
            title = "🏷️ $brandName 추천",
            subtitle = "선호 브랜드 기반 추천"
        )
        LazyRow(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(horizontal = 16.dp)
        ) {
            items(deals) { deal ->
                DealCard(
                    deal = deal,
                    onClick = { onDealClick(deal) },
                    onPositive = {},
                    onNegative = {}
                )
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
        Text(
            text = title,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold
        )
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
        Column(
            modifier = Modifier.padding(12.dp)
        ) {
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
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                val priceText = deal.price?.let { NumberFormat.getInstance().format(it) + "원" } ?: "-"
                Text(
                    text = priceText,
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold
                )
                if ((deal.discountRate ?: 0) > 0) {
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = "-${deal.discountRate}%",
                        fontSize = 12.sp,
                        color = Color.Red
                    )
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                horizontalArrangement = Arrangement.End,
                modifier = Modifier.fillMaxWidth()
            ) {
                IconButton(onClick = onNegative) {
                    Icon(Icons.Default.ThumbDown, contentDescription = "별로예요")
                }
                IconButton(onClick = onPositive) {
                    Icon(Icons.Default.ThumbUp, contentDescription = "좋아요")
                }
            }
        }
    }
}

@Composable
private fun ErrorCard(message: String, onDismiss: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = message,
                color = MaterialTheme.colorScheme.onErrorContainer
            )
            IconButton(onClick = onDismiss) {
                Icon(Icons.Default.Close, contentDescription = "닫기")
            }
        }
    }
}

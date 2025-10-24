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
    
    LaunchedEffect(Unit) {
        viewModel.loadPersonalizedRecommendations()
    }
    
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // 🎯 개인화 상태 헤더
        item {
            PersonalizationStatusCard(insights)
        }
        
        // 🔥 맞춤 추천 섹션
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
    subtitle: String,
    deals: List<DealItem>,
    onDealClick: (DealItem) -> Unit,
    onFeedback: (Int, Boolean) -> Unit
) {
    Column {
        // 섹션 헤더
        Column(
            modifier = Modifier.padding(horizontal = 16.dp)
        ) {
            Text(
                text = title,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold
            )
            
            Text(
                text = subtitle,
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp)
            )
        }
        
        Spacer(modifier = Modifier.height(12.dp))
        
        // 추천 아이템들 (가로 스크롤)
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(horizontal = 16.dp)
        ) {
            items(deals) { deal ->
                PersonalizedDealCard(
                    deal = deal,
                    onClick = { onDealClick(deal) },
                    onPositiveFeedback = { onFeedback(deal.id, true) },
                    onNegativeFeedback = { onFeedback(deal.id, false) }
                )
            }
        }
    }
}

@Composable
private fun PersonalizedDealCard(
    deal: DealItem,
    onClick: () -> Unit,
    onPositiveFeedback: () -> Unit,
    onNegativeFeedback: () -> Unit
) {
    Card(
        modifier = Modifier
            .width(280.dp)
            .clickable { onClick() },
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(
            modifier = Modifier.padding(12.dp)
        ) {
            // 이미지
            AsyncImage(
                model = deal.imageUrl,
                contentDescription = deal.title,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(120.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            // 사이트명 + AI 점수
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "[${deal.siteName}]",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Medium
                )
                
                // AI 추천 배지
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.secondary
                ) {
                    Text(
                        text = "AI 95%", // 실제로는 계산된 점수
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.onSecondary,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }
            
            // 제목
            Text(
                text = deal.title,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(vertical = 4.dp)
            )
            
            // 가격 정보
            deal.price?.let { price ->
                Text(
                    text = "${NumberFormat.getInstance().format(price)}원",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            
            // 피드백 버튼들
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                horizontalArrangement = Arrangement.End
            ) {
                IconButton(
                    onClick = onPositiveFeedback,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        Icons.Default.ThumbUp,
                        contentDescription = "좋음",
                        tint = Color.Green,
                        modifier = Modifier.size(18.dp)
                    )
                }
                
                IconButton(
                    onClick = onNegativeFeedback,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        Icons.Default.ThumbDown,
                        contentDescription = "싫음",
                        tint = Color.Red,
                        modifier = Modifier.size(18.dp)
                    )
                }
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
    if (deals.isEmpty()) return
    
    Column {
        Text(
            text = "📱 $categoryName 맞춤 추천",
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 16.dp)
        )
        
        Spacer(modifier = Modifier.height(8.dp))
        
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(horizontal = 16.dp)
        ) {
            items(deals.take(5)) { deal ->
                CompactDealCard(deal, onDealClick)
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
    if (deals.isEmpty()) return
    
    Column {
        Text(
            text = "🏷️ $brandName 신상품",
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 16.dp)
        )
        
        Spacer(modifier = Modifier.height(8.dp))
        
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(horizontal = 16.dp)
        ) {
            items(deals.take(5)) { deal ->
                CompactDealCard(deal, onDealClick)
            }
        }
    }
}

@Composable
private fun CompactDealCard(
    deal: DealItem,
    onDealClick: (DealItem) -> Unit
) {
    Card(
        modifier = Modifier
            .width(200.dp)
            .clickable { onDealClick(deal) },
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.padding(12.dp)
        ) {
            AsyncImage(
                model = deal.imageUrl,
                contentDescription = deal.title,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(80.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
            )
            
            Spacer(modifier = Modifier.height(6.dp))
            
            Text(
                text = "[${deal.siteName}]",
                fontSize = 10.sp,
                color = MaterialTheme.colorScheme.primary
            )
            
            Text(
                text = deal.title,
                fontSize = 12.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(vertical = 4.dp)
            )
            
            deal.price?.let { price ->
                Text(
                    text = "${NumberFormat.getInstance().format(price)}원",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}
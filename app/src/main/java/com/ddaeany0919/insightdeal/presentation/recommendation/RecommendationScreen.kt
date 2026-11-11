package com.ddaeany0919.insightdeal.presentation.recommendation

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Inbox
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ddaeany0919.insightdeal.domain.InteractionType
import com.ddaeany0919.insightdeal.models.DealItem
import com.ddaeany0919.insightdeal.presentation.A11yIconButton
import com.ddaeany0919.insightdeal.presentation.home.DealCardComposable
import com.ddaeany0919.insightdeal.presentation.home.HomeDealCardSkeleton
import com.ddaeany0919.insightdeal.utils.CustomTabLauncher

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecommendationScreen(
    onDealClick: (DealItem) -> Unit,
    onBack: () -> Unit = {},
    viewModel: RecommendationViewModel = viewModel()
) {
    val context = LocalContext.current
    val listState = rememberLazyListState()
    val recommendations by viewModel.recommendations.collectAsStateWithLifecycle()
    val insights by viewModel.insights.collectAsStateWithLifecycle()
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()
    val errorMessage by viewModel.errorMessage.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        if (recommendations.isEmpty() && !isLoading) {
            viewModel.refresh()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "맞춤 핫딜",
                        fontWeight = FontWeight.Bold,
                        fontSize = 19.sp
                    )
                },
                navigationIcon = {
                    A11yIconButton(
                        onClick = onBack,
                        contentDescription = "뒤로가기"
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                },
                actions = {
                    A11yIconButton(
                        onClick = { viewModel.refresh() },
                        contentDescription = "새로고침"
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = null)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(MaterialTheme.colorScheme.background),
            state = listState,
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            insights?.let { info ->
                if (info.topCategories.isNotEmpty()) {
                    item(key = "recommendation_summary_header") {
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                            border = BorderStroke(
                                1.dp,
                                MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                            )
                        ) {
                            Column(
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text(
                                    text = "관심 카테고리 기반 추천",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    text = "자주 살펴본 ${info.topCategories.take(3).joinToString(", ")} 분야의 인기 핫딜을 모았어요.",
                                    fontSize = 13.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }

            when {
                isLoading && recommendations.isEmpty() -> {
                    items(count = 4, key = { idx -> "rec_skeleton_$idx" }) {
                        HomeDealCardSkeleton()
                    }
                }
                errorMessage != null && recommendations.isEmpty() -> {
                    item(key = "recommendation_error") {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(220.dp),
                            shape = RoundedCornerShape(16.dp),
                            border = BorderStroke(
                                1.dp,
                                MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.4f)
                            ),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.06f)
                            )
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(16.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.ErrorOutline,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(38.dp)
                                )
                                Spacer(modifier = Modifier.height(10.dp))
                                Text(
                                    text = errorMessage ?: "맞춤 핫딜을 불러오지 못했어요",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Spacer(modifier = Modifier.height(12.dp))
                                OutlinedButton(
                                    onClick = { viewModel.refresh() },
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Text("다시 시도", fontWeight = FontWeight.SemiBold)
                                }
                            }
                        }
                    }
                }
                recommendations.isEmpty() -> {
                    item(key = "recommendation_empty") {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(220.dp),
                            shape = RoundedCornerShape(16.dp),
                            border = BorderStroke(
                                1.dp,
                                MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                            ),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.15f)
                            )
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(16.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Inbox,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f),
                                    modifier = Modifier.size(38.dp)
                                )
                                Spacer(modifier = Modifier.height(10.dp))
                                Text(
                                    text = "아직 추천할 핫딜이 없어요",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "관심 있는 상품을 둘러보면 취향에 맞는 특가를 추천해 드릴게요.",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                                )
                            }
                        }
                    }
                }
                else -> {
                    items(
                        items = recommendations,
                        key = { it.id },
                        contentType = { "recommended_deal_item" }
                    ) { deal ->
                        DealCardComposable(
                            deal = deal,
                            onDetailClick = {
                                viewModel.trackInteraction(deal.id, InteractionType.CLICK, deal)
                                onDealClick(deal)
                            },
                            onOpenUrl = { url ->
                                viewModel.trackInteraction(deal.id, InteractionType.CLICK, deal)
                                viewModel.trackOutboundClick(
                                    url = url,
                                    price = deal.price.takeIf { it > 0L }?.toInt(),
                                    currency = deal.currency
                                )
                                CustomTabLauncher.launchShoppingUrl(context, url)
                            }
                        )
                    }
                }
            }
        }
    }
}

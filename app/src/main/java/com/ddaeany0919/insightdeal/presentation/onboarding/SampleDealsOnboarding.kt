package com.ddaeany0919.insightdeal.presentation.onboarding

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

data class SampleDeal(
    val title: String,
    val price: String,
    val originalPrice: String,
    val discount: String,
    val platform: String
)

/**
 * 🎆 첫 사용자를 위한 온보딩 섹션
 * 빈 피드 대신 앱의 가치를 즉시 보여주는 샘플 데이터
 */
@Composable
fun SampleDealsOnboarding(
    onStartTracking: () -> Unit,
    modifier: Modifier = Modifier
) {
    val sampleDeals = listOf(
        SampleDeal(
            title = "갤럭시 버즈 프로 2",
            price = "189,000원", 
            originalPrice = "259,000원",
            discount = "27% 할인",
            platform = "G마켓"
        ),
        SampleDeal(
            title = "에어팟 프로 3세대", 
            price = "279,000원",
            originalPrice = "359,000원", 
            discount = "22% 할인",
            platform = "11번가"
        ),
        SampleDeal(
            title = "아이폰 15 Pro 128GB",
            price = "1,350,000원",
            originalPrice = "1,550,000원",
            discount = "13% 할인", 
            platform = "쿠팡"
        )
    )
    
    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding = PaddingValues(16.dp)
    ) {
        item {
            OnboardingHeader(onStartTracking = onStartTracking)
        }
        
        items(sampleDeals) { deal ->
            SampleDealCard(deal = deal)
        }
        
        item {
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "💡 실제 커뮤니티 딜이 수집되는 동안 미리보기입니다",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
            )
        }
    }
}

@Composable
private fun OnboardingHeader(
    onStartTracking: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                Icons.AutoMirrored.Filled.TrendingUp, 
                contentDescription = null,
                modifier = Modifier.size(48.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            
            Spacer(modifier = Modifier.height(12.dp))
            
            Text(
                text = "🎉 InsightDeal에 오신 걸 환영합니다!",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Text(
                text = "4개 쇼핑몰 가격을 실시간 비교하고\n90일 가격 변동을 확인하세요",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            Button(
                onClick = onStartTracking,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.Add, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("첫 상품 추적 시작하기")
            }
        }
    }
}

@Composable
private fun SampleDealCard(
    deal: SampleDeal
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { /* 샘플이므로 클릭 비활성화 */ }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Text(
                text = deal.title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Medium
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = deal.price,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    
                    Row {
                        Text(
                            text = deal.originalPrice,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = deal.discount,
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFFE91E63),
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
                
                Surface(
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    shape = MaterialTheme.shapes.small
                ) {
                    Text(
                        text = deal.platform,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(12.dp))
            
            // 샘플 4몰 비교 섹션
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                shape = MaterialTheme.shapes.small
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(
                        text = "💰 ${deal.platform}에서 70,000원 절약!",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color(0xFF00C853),
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = "다른 곳: 쿠팡(+50,000) · 옥션(+30,000)",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                    )
                }
            }
        }
    }
}
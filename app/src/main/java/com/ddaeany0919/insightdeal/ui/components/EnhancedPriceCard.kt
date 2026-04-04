package com.ddaeany0919.insightdeal.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.automirrored.filled.TrendingDown
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

data class PriceComparison(
    val productName: String,
    val lowestPrice: Int,
    val lowestPlatform: String,
    val originalPrice: Int,
    val discountRate: Int,
    val platforms: List<PlatformPrice>,
    val lastUpdated: String,
    val isAvailable: Boolean = true,
    val hasShippingFee: Boolean = false,
    val totalPriceWithShipping: Int? = null,
    val recordLowPrice: Int? = null // 역대 최저가 (옵션)
)

data class PlatformPrice(
    val platform: String,
    val price: Int,
    val isLowest: Boolean = false,
    val priceDiff: Int = 0
)

/**
 * 🎯 사용자 중심 가격 카드 (v2.0) - 통합 딜 뱃지 & 역대 최저가 대비 % 기능
 */
@Composable
fun EnhancedPriceCard(
    comparison: PriceComparison,
    onBuyNow: (String) -> Unit,
    onSetAlert: (String, Int) -> Unit,
    modifier: Modifier = Modifier
) {
    var showAlert by remember { mutableStateOf(false) }
    val savings = comparison.originalPrice - comparison.lowestPrice
    val savingsPercentage = if (comparison.originalPrice > 0) {
        ((savings.toFloat() / comparison.originalPrice) * 100).toInt()
    } else 0

    // 역대 최저가 대비 상승/하락 폭 계산
    val recordLowDiff = comparison.recordLowPrice?.let {
        if (comparison.lowestPrice <= it) {
            "역대 최저가 달성!"
        } else {
            val diff = ((comparison.lowestPrice - it) / it.toFloat() * 100).toInt()
            "역대 최저가 대비 +${diff}%"
        }
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable { onBuyNow(comparison.lowestPlatform) },
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            // [Task 1] 통합 딜 배지 (뽐뿌, 퀘이사존 통합 증거)
            Surface(
                color = MaterialTheme.colorScheme.secondaryContainer,
                shape = RoundedCornerShape(4.dp),
                modifier = Modifier.padding(bottom = 8.dp)
            ) {
                Text(
                    text = "✨ 뽐뿌, 퀘이사존 등 3곳에서 검증됨",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    fontWeight = FontWeight.Bold
                )
            }

            // 상품명
            Text(
                text = comparison.productName,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Medium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(12.dp))

            // 💰 절약 강조 섹션
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = Color(0xFF4CAF50).copy(alpha = 0.1f),
                shape = RoundedCornerShape(8.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "💰 ${comparison.lowestPlatform} ${String.format("%,d", savings)}원 할인",
                            style = MaterialTheme.typography.bodyLarge,
                            color = Color(0xFF2E7D32),
                            fontWeight = FontWeight.Bold
                        )

                        // [Task 1] 역대 최저가 텍스트
                        if (recordLowDiff != null) {
                            Text(
                                text = "📈 $recordLowDiff",
                                style = MaterialTheme.typography.bodySmall,
                                color = if (comparison.lowestPrice <= comparison.recordLowPrice!!) Color(0xFFE91E63) else Color(0xFF1565C0),
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(top = 2.dp)
                            )
                        } else {
                            Text(
                                text = "🔥 지금이 3개월 중 최저가",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color(0xFF1565C0),
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }

                    // 최저가 표시
                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            text = "${String.format("%,d", comparison.lowestPrice)}원",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )

                        Text(
                            text = "${String.format("%,d", comparison.originalPrice)}원",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // 신뢰성 레이블 등 정보 영역
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 시간
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.CheckCircle, null, Modifier.size(14.dp), Color(0xFF4CAF50))
                    Spacer(Modifier.width(4.dp))
                    Text("실시간 동기화 완료", style = MaterialTheme.typography.labelSmall)
                }

                Text("·", color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f))

                // 배송
                Text(
                    text = if (comparison.hasShippingFee && comparison.totalPriceWithShipping != null) 
                           "무료배송 제외" else "무료배송",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (!comparison.hasShippingFee) Color(0xFF4CAF50) else Color.Gray,
                    fontWeight = FontWeight.Medium
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // 비교 몰 영역
            if (comparison.platforms.size > 1) {
                Text(
                    text = "다른 곳: ${comparison.platforms.filter { !it.isLowest }.take(2).joinToString(" · ") { "${it.platform}(+${String.format("%,d", it.priceDiff)})" }}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
                Spacer(modifier = Modifier.height(12.dp))
            }

            // CTA 섹션
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = { onBuyNow(comparison.lowestPlatform) },
                    modifier = Modifier.weight(1.5f),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                ) {
                    Icon(Icons.Default.ShoppingCart, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("초특가로 구매하기", fontSize = 14.sp)
                }

                OutlinedButton(
                    onClick = {
                        onSetAlert(comparison.productName, (comparison.lowestPrice * 0.95).toInt())
                        showAlert = true
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Text("5% 하락 알림", fontSize = 12.sp)
                }
            }
        }
    }
}

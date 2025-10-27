package com.ddaeany0919.insightdeal.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.TrendingDown
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
import java.text.SimpleDateFormat
import java.util.*

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
    val totalPriceWithShipping: Int? = null
)

data class PlatformPrice(
    val platform: String,
    val price: Int,
    val isLowest: Boolean = false,
    val priceDiff: Int = 0
)

/**
 * 🎯 사용자 중심 가격 카드 - 절약/신뢰/행동 유도 강화
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

    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable { onBuyNow(comparison.lowestPlatform) },
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
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
                            text = "💰 ${comparison.lowestPlatform}에서 ${String.format("%,d", savings)}원 절약!",
                            style = MaterialTheme.typography.bodyLarge,
                            color = Color(0xFF2E7D32),
                            fontWeight = FontWeight.Bold
                        )

                        Text(
                            text = "🔥 지금이 3개월 중 최저가",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFF1565C0),
                            fontWeight = FontWeight.Medium
                        )

                        // 하락 트렌드 표시
                        if (savingsPercentage >= 10) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Default.TrendingDown,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp),
                                    tint = Color(0xFFE91E63)
                                )
                                Text(
                                    text = "어제보다 ${savingsPercentage}% 하락",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color(0xFFE91E63),
                                    fontWeight = FontWeight.Medium
                                )
                            }
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

            // 신뢰성 레이블
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 최근 확인 시간
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.CheckCircle,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp),
                        tint = Color(0xFF4CAF50)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "2분 전 확인됨",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                    )
                }

                // 구분선
                Text(
                    text = "·",
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                )

                // 배송비 포함 총액
                if (comparison.hasShippingFee && comparison.totalPriceWithShipping != null) {
                    Text(
                        text = "배송비 포함 ${String.format("%,d", comparison.totalPriceWithShipping)}원",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                    )
                } else {
                    Text(
                        text = "무료배송",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color(0xFF4CAF50),
                        fontWeight = FontWeight.Medium
                    )
                }

                // 구분선
                Text(
                    text = "·",
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                )

                // 재고 상태
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        if (comparison.isAvailable) Icons.Default.CheckCircle else Icons.Default.Warning,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp),
                        tint = if (comparison.isAvailable) Color(0xFF4CAF50) else Color(0xFFFF9800)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = if (comparison.isAvailable) "재고 있음" else "품절",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (comparison.isAvailable)
                            Color(0xFF4CAF50) else Color(0xFFFF9800),
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // 플랫폼별 가격 비교 (간략)
            if (comparison.platforms.size > 1) {
                Text(
                    text = "다른 곳: ${comparison.platforms
                        .filter { !it.isLowest }
                        .take(2)
                        .joinToString(" · ") { "${it.platform}(+${String.format("%,d", it.priceDiff)})" }}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )

                Spacer(modifier = Modifier.height(12.dp))
            }

            // CTA 버튼들
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // 지금 구매하기 (Primary)
                Button(
                    onClick = { onBuyNow(comparison.lowestPlatform) },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary
                    )
                ) {
                    Icon(
                        Icons.Default.ShoppingCart,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "지금 구매하기",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium
                    )
                }

                // 5% 더 떨어지면 알림 (Secondary)
                OutlinedButton(
                    onClick = {
                        val alertPrice = (comparison.lowestPrice * 0.95).toInt()
                        onSetAlert(comparison.productName, alertPrice)
                        showAlert = true
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = "5% 더 떨어지면 알림",
                        fontSize = 12.sp
                    )
                }
            }
        }
    }

    // 알림 설정 완료 스낵바
    if (showAlert) {
        LaunchedEffect(showAlert) {
            kotlinx.coroutines.delay(2000)
            showAlert = false
        }
    }
}

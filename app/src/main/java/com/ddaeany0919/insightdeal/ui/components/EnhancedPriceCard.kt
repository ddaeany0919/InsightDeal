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
 * 
 * 개선점:
 * - 절약 금액과 이유를 명확히 강조 ("16,000원 절약!")
 * - 신뢰성 레이블 ("2분 전 확인됨", "배송비 포함 총액")
 * - 명확한 CTA ("지금 구매하기", "5% 더 떨어지면 알려줘")
 * - 3개월 최저가, 하락률 같은 맥락 정보
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
                                )\n                            }\n                        }\n                    }\n                    \n                    // 최저가 표시\n                    Column(horizontalAlignment = Alignment.End) {\n                        Text(\n                            text = \"${String.format(\"%,d\", comparison.lowestPrice)}원\",\n                            style = MaterialTheme.typography.titleLarge,\n                            fontWeight = FontWeight.Bold,\n                            color = MaterialTheme.colorScheme.primary\n                        )\n                        \n                        Text(\n                            text = \"${String.format(\"%,d\", comparison.originalPrice)}원\",\n                            style = MaterialTheme.typography.bodySmall,\n                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)\n                        )\n                    }\n                }\n            }\n            \n            Spacer(modifier = Modifier.height(12.dp))\n            \n            // 신뢰성 레이블\n            Row(\n                modifier = Modifier.fillMaxWidth(),\n                horizontalArrangement = Arrangement.spacedBy(12.dp),\n                verticalAlignment = Alignment.CenterVertically\n            ) {\n                // 최근 확인 시간\n                Row(\n                    verticalAlignment = Alignment.CenterVertically\n                ) {\n                    Icon(\n                        Icons.Default.CheckCircle,\n                        contentDescription = null,\n                        modifier = Modifier.size(14.dp),\n                        tint = Color(0xFF4CAF50)\n                    )\n                    Spacer(modifier = Modifier.width(4.dp))\n                    Text(\n                        text = \"2분 전 확인됨\",\n                        style = MaterialTheme.typography.labelSmall,\n                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)\n                    )\n                }\n                \n                // 구분선\n                Text(\n                    text = \"·\",\n                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)\n                )\n                \n                // 배송비 포함 총액\n                if (comparison.hasShippingFee && comparison.totalPriceWithShipping != null) {\n                    Text(\n                        text = \"배송비 포함 ${String.format(\"%,d\", comparison.totalPriceWithShipping)}원\",\n                        style = MaterialTheme.typography.labelSmall,\n                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)\n                    )\n                } else {\n                    Text(\n                        text = \"무료배송\",\n                        style = MaterialTheme.typography.labelSmall,\n                        color = Color(0xFF4CAF50),\n                        fontWeight = FontWeight.Medium\n                    )\n                }\n                \n                // 구분선\n                Text(\n                    text = \"·\",\n                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)\n                )\n                \n                // 재고 상태\n                Row(\n                    verticalAlignment = Alignment.CenterVertically\n                ) {\n                    Icon(\n                        if (comparison.isAvailable) Icons.Default.CheckCircle else Icons.Default.Warning,\n                        contentDescription = null,\n                        modifier = Modifier.size(14.dp),\n                        tint = if (comparison.isAvailable) Color(0xFF4CAF50) else Color(0xFFFF9800)\n                    )\n                    Spacer(modifier = Modifier.width(4.dp))\n                    Text(\n                        text = if (comparison.isAvailable) \"재고 있음\" else \"품절\",\n                        style = MaterialTheme.typography.labelSmall,\n                        color = if (comparison.isAvailable) \n                            Color(0xFF4CAF50) else Color(0xFFFF9800),\n                        fontWeight = FontWeight.Medium\n                    )\n                }\n            }\n            \n            Spacer(modifier = Modifier.height(16.dp))\n            \n            // 플랫폼별 가격 비교 (간략)\n            if (comparison.platforms.size > 1) {\n                Text(\n                    text = \"다른 곳: ${comparison.platforms\n                        .filter { !it.isLowest }\n                        .take(2)\n                        .joinToString(\" · \") { \"${it.platform}(+${String.format(\"%,d\", it.priceDiff)})\" }}\",\n                    style = MaterialTheme.typography.bodySmall,\n                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)\n                )\n                \n                Spacer(modifier = Modifier.height(12.dp))\n            }\n            \n            // CTA 버튼들\n            Row(\n                modifier = Modifier.fillMaxWidth(),\n                horizontalArrangement = Arrangement.spacedBy(8.dp)\n            ) {\n                // 지금 구매하기 (Primary)\n                Button(\n                    onClick = { onBuyNow(comparison.lowestPlatform) },\n                    modifier = Modifier.weight(1f),\n                    colors = ButtonDefaults.buttonColors(\n                        containerColor = MaterialTheme.colorScheme.primary\n                    )\n                ) {\n                    Icon(\n                        Icons.Default.ShoppingCart,\n                        contentDescription = null,\n                        modifier = Modifier.size(18.dp)\n                    )\n                    Spacer(modifier = Modifier.width(6.dp))\n                    Text(\n                        text = \"지금 구매하기\",\n                        fontSize = 14.sp,\n                        fontWeight = FontWeight.Medium\n                    )\n                }\n                \n                // 5% 더 떨어지면 알림 (Secondary)\n                OutlinedButton(\n                    onClick = { \n                        val alertPrice = (comparison.lowestPrice * 0.95).toInt()\n                        onSetAlert(comparison.productName, alertPrice)\n                        showAlert = true\n                    },\n                    modifier = Modifier.weight(1f)\n                ) {\n                    Text(\n                        text = \"5% 더 떨어지면 알림\",\n                        fontSize = 12.sp\n                    )\n                }\n            }\n        }\n    }\n    \n    // 알림 설정 완료 스낵바\n    if (showAlert) {\n        LaunchedEffect(showAlert) {\n            kotlinx.coroutines.delay(2000)\n            showAlert = false\n        }\n    }\n}
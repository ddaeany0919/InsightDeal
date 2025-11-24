package com.ddaeany0919.insightdeal.presentation.wishlist

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.*
import androidx.compose.foundation.clickable
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.Alignment
import java.time.format.DateTimeFormatter

@Composable
fun WishlistCard(
    item: WishlistItem,
    onDelete: (() -> Unit)? = null,
    onCheckPrice: (() -> Unit)? = null,
    onClick: (() -> Unit)? = null,
    priceHistory: com.ddaeany0919.insightdeal.data.PriceHistory? = null,
    onExpand: (() -> Unit)? = null,
    isExpanded: Boolean = false
) {
    val targetReached = item.isTargetReached || (item.currentLowestPrice != null && item.targetPrice >= item.currentLowestPrice)
    val currentPrice = item.currentLowestPrice ?: 0
    val targetPrice = item.targetPrice
    val discountRate = if (currentPrice > 0 && targetPrice > 0) {
        ((targetPrice - currentPrice).toFloat() / targetPrice * 100).toInt()
    } else 0

    val timeFormatter = DateTimeFormatter.ofPattern("MM/dd HH:mm")

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp, horizontal = 4.dp)
            .clickable { onClick?.invoke() },
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(Modifier.padding(16.dp)) {
            // Header: Title & Badges
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        text = item.keyword,
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        maxLines = 2
                    )
                    Spacer(Modifier.height(4.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (targetReached) {
                            Surface(
                                color = MaterialTheme.colorScheme.tertiaryContainer,
                                shape = MaterialTheme.shapes.small
                            ) {
                                Text(
                                    text = "목표달성",
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onTertiaryContainer
                                )
                            }
                            Spacer(Modifier.width(6.dp))
                        }
                        Text(
                            text = item.currentLowestPlatform ?: "알수없음",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Icon(
                    imageVector = if (isExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                    contentDescription = "Expand",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.clickable {
                        android.util.Log.d("WishlistCard", "Icon Clicked: ${item.keyword}")
                        onExpand?.invoke()
                    }
                )
            }

            Spacer(Modifier.height(12.dp))

            // Price Section
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Bottom
            ) {
                if (item.currentLowestPrice != null) {
                    Text(
                        text = "${item.currentLowestPrice.toString().reversed().chunked(3).joinToString(",").reversed()}원",
                        style = MaterialTheme.typography.headlineMedium.copy(
                            color = MaterialTheme.colorScheme.secondary,
                            fontWeight = FontWeight.ExtraBold
                        )
                    )
                    Spacer(Modifier.width(8.dp))
                    if (discountRate > 0) {
                        Text(
                            text = "$discountRate% ▼",
                            style = MaterialTheme.typography.titleMedium.copy(
                                color = MaterialTheme.colorScheme.error,
                                fontWeight = FontWeight.Bold
                            ),
                            modifier = Modifier.padding(bottom = 2.dp)
                        )
                    }
                } else {
                    Text(
                        text = "가격 확인 필요",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(Modifier.weight(1f))
                Text(
                    text = "목표 ${item.targetPrice.toString().reversed().chunked(3).joinToString(",").reversed()}원",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 4.dp)
                )
            }

            // Footer: Actions & Timestamp
            Spacer(Modifier.height(12.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Spacer(Modifier.height(8.dp))
            
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                item.lastChecked?.let {
                    Text(
                        text = "${it.format(timeFormatter)} 업데이트",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    IconButton(
                        onClick = { onCheckPrice?.invoke() },
                        enabled = !item.isLoading,
                        modifier = Modifier.size(32.dp)
                    ) {
                        if (item.isLoading) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                        } else {
                            Icon(Icons.Default.Refresh, contentDescription = "Refresh", tint = MaterialTheme.colorScheme.primary)
                        }
                    }
                    IconButton(
                        onClick = { onDelete?.invoke() },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(Icons.Default.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }

            if (isExpanded) {
                Spacer(Modifier.height(16.dp))
                Text(
                    "가격 변동 추이 (30일)",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(Modifier.height(8.dp))
                if (priceHistory != null && priceHistory.dataPoints.isNotEmpty()) {
                    com.ddaeany0919.insightdeal.presentation.components.PriceHistoryGraph(
                        dataPoints = priceHistory.dataPoints,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(120.dp)
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(120.dp)
                            .padding(8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        if (priceHistory == null) {
                            CircularProgressIndicator(modifier = Modifier.size(24.dp))
                        } else {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(
                                    imageVector = Icons.Default.Info,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                                )
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    "가격 기록이 없습니다", 
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

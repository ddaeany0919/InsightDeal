package com.ddaeany0919.insightdeal.ui.components

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import java.time.format.DateTimeFormatter
import com.ddaeany0919.insightdeal.presentation.wishlist.WishlistItem
import com.ddaeany0919.insightdeal.presentation.wishlist.PriceCheckResponse

enum class WishlistCardStyle {
    SIMPLE, DETAILED
}

@Composable
fun StandardWishlistCard(
    item: WishlistItem,
    style: WishlistCardStyle = WishlistCardStyle.DETAILED,
    onDelete: (() -> Unit)? = null,
    onCheckPrice: (() -> Unit)? = null,
    onClick: (() -> Unit)? = null,
    onExpand: (() -> Unit)? = null,
    isExpanded: Boolean = false,
    checkResult: PriceCheckResponse? = null,
    priceHistory: com.ddaeany0919.insightdeal.data.PriceHistory? = null
) {
    val context = LocalContext.current
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
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(12.dp)
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
                        if (targetReached || checkResult?.isTargetReached == true) {
                            Surface(
                                color = MaterialTheme.colorScheme.tertiaryContainer,
                                shape = MaterialTheme.shapes.small
                            ) {
                                Text(
                                    text = "목표달성 🎉",
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onTertiaryContainer
                                )
                            }
                            Spacer(Modifier.width(6.dp))
                        }
                        Text(
                            text = checkResult?.platform ?: item.currentLowestPlatform ?: "알수없음",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                if (style == WishlistCardStyle.DETAILED) {
                    Icon(
                        imageVector = if (isExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                        contentDescription = "Expand",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.clickable { onExpand?.invoke() }
                    )
                }
            }

            Spacer(Modifier.height(12.dp))

            // Price Section
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Bottom
            ) {
                val displayPrice = checkResult?.currentPrice ?: item.currentLowestPrice
                if (displayPrice != null && displayPrice > 0) {
                    Text(
                        text = "${displayPrice.toString().reversed().chunked(3).joinToString(",").reversed()}원",
                        style = MaterialTheme.typography.headlineMedium.copy(
                            color = MaterialTheme.colorScheme.secondary,
                            fontWeight = FontWeight.ExtraBold
                        )
                    )
                    Spacer(Modifier.width(8.dp))
                    if (discountRate > 0 && style == WishlistCardStyle.DETAILED) {
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

            Spacer(Modifier.height(12.dp))
            val progress = if (currentPrice > 0) {
                (targetPrice.toFloat() / currentPrice.toFloat()).coerceIn(0f, 1f)
            } else 0f
            
            Column(modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("목표 달성률", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("${(progress * 100).toInt()}%", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.height(4.dp))
                LinearProgressIndicator(
                    progress = progress,
                    modifier = Modifier.fillMaxWidth().height(6.dp),
                    color = if (progress >= 1f) com.ddaeany0919.insightdeal.ui.theme.PriceBest else MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant
                )
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Spacer(Modifier.height(8.dp))

            // Add Buy Button for Simple Mode if result exists
            if (style == WishlistCardStyle.SIMPLE && !checkResult?.productUrl.isNullOrBlank()) {
                TextButton(
                    onClick = {
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(checkResult!!.productUrl))
                        ContextCompat.startActivity(context, intent, null)
                    },
                    modifier = Modifier.padding(vertical = 2.dp)
                ) {
                    Text(text = "구매 바로가기", color = MaterialTheme.colorScheme.primary)
                }
            }

            // Footer actions (Refresh & Delete)
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (item.lastChecked != null) {
                    Text(
                        text = "${item.lastChecked.format(timeFormatter)} 업데이트",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    Text("초기화됨", style = MaterialTheme.typography.labelSmall)
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (style == WishlistCardStyle.SIMPLE) {
                        OutlinedButton(onClick = { onCheckPrice?.invoke() }) {
                            Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("가격 체크")
                        }
                        OutlinedButton(
                            onClick = { onDelete?.invoke() },
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.error),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
                        ) {
                            Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("삭제")
                        }
                    } else {
                        IconButton(
                            onClick = { onCheckPrice?.invoke() },
                            enabled = !item.isLoading,
                            modifier = Modifier.size(32.dp)
                        ) {
                            if (item.isLoading) CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                            else Icon(Icons.Default.Refresh, contentDescription = "Refresh", tint = MaterialTheme.colorScheme.primary)
                        }
                        IconButton(
                            onClick = { onDelete?.invoke() },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(Icons.Default.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }

            // Expanded Graph View for Detailed Mode
            AnimatedVisibility(visible = isExpanded && style == WishlistCardStyle.DETAILED) {
                Column {
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
                            modifier = Modifier.fillMaxWidth().height(120.dp)
                        )
                    } else {
                        Box(
                            modifier = Modifier.fillMaxWidth().height(120.dp).padding(8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            if (priceHistory == null) CircularProgressIndicator(modifier = Modifier.size(24.dp))
                            else Text("가격 기록이 없습니다", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
    }
}

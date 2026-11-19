package com.ddaeany0919.insightdeal.presentation.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ddaeany0919.insightdeal.presentation.A11yIconButton
import com.ddaeany0919.insightdeal.presentation.formatPrice
import com.ddaeany0919.insightdeal.presentation.theme.LocalInsightDealColors
import com.ddaeany0919.insightdeal.presentation.theme.LocalIsDarkTheme
import com.ddaeany0919.insightdeal.presentation.wishlist.PriceCheckResponse
import com.ddaeany0919.insightdeal.presentation.wishlist.WishlistItem
import java.time.format.DateTimeFormatter

enum class WishlistCardStyle {
    SIMPLE, DETAILED
}

private val WISHLIST_TIME_FORMATTER = DateTimeFormatter.ofPattern("MM/dd HH:mm")

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
    val isDark = LocalIsDarkTheme.current

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp, horizontal = 16.dp)
            .clickable { onClick?.invoke() },
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        border = BorderStroke(1.dp, if (isDark) Color(0xFF2C2C2E) else Color(0xFFE5E7EB)),
        colors = CardDefaults.cardColors(
            containerColor = if (isDark) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f) else MaterialTheme.colorScheme.surfaceVariant
        ),
        shape = RoundedCornerShape(18.dp)
    ) {
        Column(Modifier.padding(20.dp)) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        text = item.keyword,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 2
                    )
                    Spacer(Modifier.height(8.dp))
                    val platformName = checkResult?.platform ?: item.currentLowestPlatform
                    if (!platformName.isNullOrBlank() && platformName != "알수없음") {
                        Spacer(Modifier.height(8.dp))
                        Surface(
                            color = if (isDark) Color(0xFF3A3A3C) else Color(0xFFE4E7EC),
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Text(
                                text = platformName,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
                if (style == WishlistCardStyle.DETAILED) {
                    A11yIconButton(
                        onClick = { onExpand?.invoke() },
                        contentDescription = "상세 펼치기"
                    ) {
                        Icon(
                            imageVector = if (isExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Bottom
            ) {
                val displayPrice = checkResult?.currentPrice ?: item.currentLowestPrice ?: item.targetPrice
                if (displayPrice > 0) {
                    Text(
                        text = formatPrice(displayPrice.toLong(), null, item.keyword),
                        fontSize = 22.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.primary
                    )
                } else {
                    Text(
                        text = "가격 추적 중",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(Modifier.height(16.dp))
            HorizontalDivider(color = if (isDark) Color(0xFF2C2C2E) else Color(0xFFE5E7EB), thickness = 1.dp)
            Spacer(Modifier.height(12.dp))

            if (style == WishlistCardStyle.SIMPLE && !checkResult?.productUrl.isNullOrBlank()) {
                Button(
                    onClick = {
                        try {
                            val url = checkResult?.productUrl ?: ""
                            if (url.startsWith("http://") || url.startsWith("https://")) {
                                com.ddaeany0919.insightdeal.utils.CustomTabLauncher.launchShoppingUrl(context, url)
                            }
                        } catch (_: Exception) {}
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    )
                ) {
                    Text(
                        text = "구매 바로가기",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                }
            }

            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (item.lastChecked != null) {
                    Text(
                        text = "${item.lastChecked.format(WISHLIST_TIME_FORMATTER)} 업데이트",
                        fontSize = 11.sp,
                        color = LocalInsightDealColors.current.textMuted
                    )
                } else {
                    Text("가격 확인 전", fontSize = 11.sp, color = LocalInsightDealColors.current.textMuted)
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (style == WishlistCardStyle.SIMPLE) {
                        OutlinedButton(
                            onClick = { onCheckPrice?.invoke() },
                            modifier = Modifier.heightIn(min = 48.dp),
                            shape = RoundedCornerShape(10.dp),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.primary)
                        ) {
                            Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("가격 체크", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                        OutlinedButton(
                            onClick = { onDelete?.invoke() },
                            modifier = Modifier.heightIn(min = 48.dp),
                            shape = RoundedCornerShape(10.dp),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.error),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
                        ) {
                            Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("삭제", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    } else {
                        IconButton(
                            onClick = { onCheckPrice?.invoke() },
                            enabled = !item.isLoading,
                            modifier = Modifier
                                .size(48.dp)
                                .background(Color.Transparent)
                                .border(
                                    BorderStroke(1.dp, if (isDark) Color(0xFF3A3A3C) else Color(0xFFE5E7EB)),
                                    RoundedCornerShape(10.dp)
                                )
                        ) {
                            if (item.isLoading) CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                            else Icon(Icons.Default.Refresh, contentDescription = "가격 확인", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                        }
                        IconButton(
                            onClick = { onDelete?.invoke() },
                            modifier = Modifier
                                .size(48.dp)
                                .background(Color.Transparent)
                                .border(
                                    BorderStroke(1.dp, if (isDark) Color(0xFF3A3A3C) else Color(0xFFE5E7EB)),
                                    RoundedCornerShape(10.dp)
                                )
                        ) {
                            Icon(Icons.Default.Delete, contentDescription = "삭제", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(20.dp))
                        }
                    }
                }
            }

            AnimatedVisibility(visible = isExpanded && style == WishlistCardStyle.DETAILED) {
                Column {
                    Spacer(Modifier.height(16.dp))
                    Text(
                        "가격 변동 추이 (30일)",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(Modifier.height(8.dp))
                    if (priceHistory != null && priceHistory.dataPoints.isNotEmpty()) {
                        PriceHistoryGraph(
                            dataPoints = priceHistory.dataPoints,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(120.dp)
                                .clip(RoundedCornerShape(12.dp))
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(120.dp)
                                .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(12.dp))
                                .padding(8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            if (priceHistory == null) CircularProgressIndicator(modifier = Modifier.size(24.dp))
                            else Text("가격 기록이 없습니다", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
    }
}

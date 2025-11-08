package com.ddaeany0919.insightdeal.presentation.wishlist

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
fun WishlistCardSimple(
    item: WishlistItem,
    checkResult: PriceCheckResponse?,
    isLoading: Boolean = false,
    onPriceCheck: () -> Unit,
    onBuy: ((String) -> Unit)? = null,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF212121)),
        modifier = modifier.padding(8.dp).fillMaxWidth(),
        elevation = CardDefaults.cardElevation(2.dp)
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(
                text = item.keyword,
                style = MaterialTheme.typography.bodyLarge,
                color = Color.White
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = "목표가: %,d원".format(item.targetPrice),
                color = Color(0xFFB0B0B0),
                style = MaterialTheme.typography.bodyMedium
            )
            Spacer(Modifier.height(8.dp))

            when {
                isLoading -> {
                    LinearProgressIndicator(Modifier.fillMaxWidth())
                }
                checkResult == null -> {}
                checkResult.currentPrice == null -> {
                    Text(
                        text = "최저가를 찾을 수 없습니다",
                        color = Color.Gray,
                        modifier = Modifier.padding(vertical = 6.dp),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
                else -> {
                    Text(
                        text = "최저가: %,d원".format(checkResult.currentPrice),
                        style = MaterialTheme.typography.bodyLarge,
                        color = if (checkResult.isTargetReached == true) Color(0xFF00C853) else Color.White,
                        fontWeight = if (checkResult.isTargetReached == true) FontWeight.Bold else null
                    )
                    Text(
                        text = "[${checkResult.platform ?: "-"]}",
                        color = Color(0xFF42a5f5),
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = checkResult.title ?: "",
                        color = Color(0xFFCCCCCC),
                        style = MaterialTheme.typography.bodyMedium
                    )
                    if (!checkResult.productUrl.isNullOrBlank() && onBuy != null) {
                        TextButton(
                            onClick = { onBuy(checkResult.productUrl!!) },
                            modifier = Modifier.padding(vertical = 2.dp)
                        ) {
                            Text(
                                text = "구매 바로가기",
                                color = Color(0xFF009688)
                            )
                        }
                    }
                    if (checkResult.isTargetReached == true) {
                        Text(
                            text = "최저가 도달! 🎉",
                            color = Color(0xFF009688),
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            Row {
                OutlinedButton(
                    onClick = onPriceCheck,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.Refresh, contentDescription = null, tint = Color(0xFFFF9800), modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(2.dp))
                    Text("가격 체크", color = Color(0xFFFF9800), style = MaterialTheme.typography.bodyMedium)
                }
                Spacer(Modifier.width(8.dp))
                OutlinedButton(
                    onClick = onDelete,
                    modifier = Modifier.weight(0.9f),
                    border = BorderStroke(1.dp, Color(0xFFEA5353)),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFEA5353))
                ) {
                    Icon(Icons.Default.Delete, contentDescription = null, tint = Color(0xFFEA5353), modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(2.dp))
                    Text("삭제", style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
}

fun openLinkInBrowser(context: Context, url: String) {
    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
    ContextCompat.startActivity(context, intent, null)
}
package com.ddaeany0919.insightdeal.presentation.wishlist

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.ChevronRight
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
    onClick: (() -> Unit)? = null
) {
    val targetReached = item.isTargetReached || (item.currentLowestPrice != null && item.targetPrice >= item.currentLowestPrice)

    val priceText = item.currentLowestPrice?.let { price ->
        "최저가: ${price.toString().reversed().chunked(3).joinToString(",").reversed()}원 (${item.currentLowestPlatform ?: "-"})"
    }

    val targetText = "목표가: ${item.targetPrice.toString().reversed().chunked(3).joinToString(",").reversed()}원"

    val timeFormatter = DateTimeFormatter.ofPattern("MM/dd HH:mm")

    Card(
        Modifier.fillMaxWidth().padding(vertical = 6.dp)
        // 카드 클릭은 제거해서 버튼만 터치 가능하도록 함
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(item.keyword, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold))
                if (targetReached) {
                    Icon(Icons.Default.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.tertiary)
                }
                Icon(
                    Icons.Default.ChevronRight,
                    contentDescription = "상세화면 이동",
                    tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                    modifier = Modifier.clickable { onClick?.invoke() }
                )
            }
            Spacer(Modifier.height(6.dp))
            Text(targetText, color = if (targetReached) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurface)
            priceText?.let { Text(it) }
            item.lastChecked?.let {
                Text(
                    "마지막 체크: ${it.format(timeFormatter)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                )
            }
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { onCheckPrice?.invoke() }, enabled = !item.isLoading) {
                    Icon(Icons.Default.Refresh, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text("가격 체크")
                    if (item.isLoading) {
                        Spacer(Modifier.width(4.dp))
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                    }
                }
                OutlinedButton(onClick = { onDelete?.invoke() }, colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)) {
                    Icon(Icons.Default.Delete, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text("삭제")
                }
            }
        }
    }
}

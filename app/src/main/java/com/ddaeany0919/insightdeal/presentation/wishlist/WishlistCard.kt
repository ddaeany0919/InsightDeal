package com.ddaeany0919.insightdeal.presentation.wishlist

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
fun WishlistCard(item: WishlistItem, onDelete: (() -> Unit)? = null, onCheckPrice: (() -> Unit)? = null) {
    val targetReached = item.isTargetReached || (item.currentLowestPrice != null && item.targetPrice >= item.currentLowestPrice)
    val priceText = item.currentLowestPrice?.let { price ->
        "최저가: ${price.toString().reversed().chunked(3).joinToString(",").reversed()} (${item.currentLowestPlatform ?: "-"})"
    }
    val targetText = "목표가: ${item.targetPrice.toString().reversed().chunked(3).joinToString(",").reversed()}"

    Card(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Column(Modifier.padding(14.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(item.keyword, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold))
                if (targetReached) Icon(Icons.Default.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.tertiary)
            }
            Spacer(Modifier.height(6.dp))
            Text(targetText, color = if (targetReached) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurface)
            priceText?.let { Text(it) }
            item.lastChecked?.let { Text("마지막 체크: $it", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)) }
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { onCheckPrice?.invoke() }) {
                    Icon(Icons.Default.Refresh, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text("가격 체크")
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

package com.ddaeany0919.insightdeal

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocalMall
import androidx.compose.material.icons.filled.TrendingDown
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.ddaeany0919.insightdeal.data.PriceComparison
import com.ddaeany0919.insightdeal.data.PriceComparisonService
import com.ddaeany0919.insightdeal.data.PriceComparisonState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 🧩 Deal 카드에 붙는 4몰 가격 비교 섹션
 * - 2초 내 결과가 오면 즉시 표시
 * - 느리면 스켈레톤/지연 안내 → 사용자 방해 없음
 */
@Composable
fun FourMallComparisonSection(
    productTitle: String,
    service: PriceComparisonService,
    modifier: Modifier = Modifier
) {
    var state by remember { mutableStateOf<PriceComparisonState>(PriceComparisonState.Idle) }

    LaunchedEffect(productTitle) {
        state = PriceComparisonState.Loading
        val result = withContext(Dispatchers.IO) {
            service.comparePrices(productTitle)
        }
        state = if (result != null) PriceComparisonState.Success(result) else PriceComparisonState.Error("비교 결과 없음", canRetry = false)
    }

    when (val s = state) {
        is PriceComparisonState.Idle -> Unit
        is PriceComparisonState.Loading -> ComparisonSkeleton(modifier)
        is PriceComparisonState.Timeout -> ComparisonTimeout(modifier)
        is PriceComparisonState.Error -> { /* 섹션 숨김 (UX 방해 금지) */ }
        is PriceComparisonState.Success -> ComparisonContent(s.comparison, modifier)
    }
}

@Composable
private fun ComparisonSkeleton(modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        CircularProgressIndicator(modifier = Modifier.height(16.dp), strokeWidth = 2.dp)
        Text("4몰 최저가 확인 중…", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f))
    }
}

@Composable
private fun ComparisonTimeout(modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Icon(Icons.Default.LocalMall, contentDescription = null, tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
        Text("일부 쇼핑몰 확인 중…", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f))
    }
}

@Composable
private fun ComparisonContent(data: PriceComparison, modifier: Modifier = Modifier) {
    if (data.lowestPlatform == null || data.lowestPrice == null) return

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        color = MaterialTheme.colorScheme.surfaceVariant
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Icon(Icons.Default.TrendingDown, contentDescription = null, tint = Color(0xFF00C853))
                Text(
                    text = "최저가: ${data.lowestPlatform.uppercase()} ${"%,d".format(data.lowestPrice)}원",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color(0xFF00C853)
                )
            }
            Spacer(Modifier.height(4.dp))
            val others = data.platforms.filterKeys { it != data.lowestPlatform }.toList().sortedBy { it.second.price }
            if (others.isNotEmpty()) {
                val summary = others.joinToString(" · ") { (k, v) ->
                    val diff = v.price - (data.lowestPrice ?: v.price)
                    "${k.uppercase()}(+${"%,d".format(diff)})"
                }
                Text(
                    text = "다른 곳: $summary",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                )
            }
        }
    }
}

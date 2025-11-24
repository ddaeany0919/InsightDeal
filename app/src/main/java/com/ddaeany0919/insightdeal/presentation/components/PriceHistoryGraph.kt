package com.ddaeany0919.insightdeal.presentation.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.ddaeany0919.insightdeal.data.PricePoint

@Composable
fun PriceHistoryGraph(
    dataPoints: List<PricePoint>,
    modifier: Modifier = Modifier,
    lineColor: Color = MaterialTheme.colorScheme.primary,
    fillColor: Color = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
) {
    if (dataPoints.isEmpty()) return

    val prices = remember(dataPoints) { dataPoints.map { it.price } }
    val minPrice = prices.minOrNull() ?: 0
    val maxPrice = prices.maxOrNull() ?: 0
    
    Canvas(modifier = modifier) {
        val width = size.width
        val height = size.height
        val priceRange = (maxPrice - minPrice).coerceAtLeast(1)
        
        val points = prices.mapIndexed { index, price ->
            val x = width * index / (prices.size - 1).coerceAtLeast(1)
            val y = height - (height * (price - minPrice) / priceRange)
            Offset(x, y)
        }

        val path = Path().apply {
            moveTo(points.first().x, points.first().y)
            points.drop(1).forEach { point ->
                lineTo(point.x, point.y)
            }
        }

        drawPath(
            path = path,
            color = lineColor,
            style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round)
        )
    }
}

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
@Suppress("UNUSED_PARAMETER")
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
    
    // Check start and end to determine overall trend: Dropping = Blue, Rising = Red
    val startPrice = prices.firstOrNull() ?: 0
    val endPrice = prices.lastOrNull() ?: 0
    val isDropping = endPrice < startPrice
    val dynamicLineColor = if (isDropping) Color(0xFF2196F3) else Color(0xFFF44336)
    val gradientColors = if (isDropping) listOf(Color(0x802196F3), Color(0x102196F3)) else listOf(Color(0x80F44336), Color(0x10F44336))
    
    Canvas(modifier = modifier) {
        val width = size.width
        val height = size.height
        // Add 10% vertical padding so points don't clip at top/bottom edges
        val paddingY = height * 0.1f
        val usableHeight = height - (paddingY * 2)
        val priceRange = (maxPrice - minPrice).coerceAtLeast(1)
        
        val points = prices.mapIndexed { index, price ->
            val x = width * index / (prices.size - 1).coerceAtLeast(1)
            val y = paddingY + usableHeight - (usableHeight * (price - minPrice) / priceRange)
            Offset(x, y)
        }

        val path = Path().apply {
            if (points.isNotEmpty()) {
                moveTo(points.first().x, points.first().y)
                for (i in 0 until points.size - 1) {
                    val start = points[i]
                    val end = points[i + 1]
                    val midX = (start.x + end.x) / 2
                    cubicTo(
                        x1 = midX, y1 = start.y,
                        x2 = midX, y2 = end.y,
                        x3 = end.x, y3 = end.y
                    )
                }
            }
        }

        // Fill below the path with Gradient
        val fillPath = Path().apply {
            addPath(path)
            if (points.isNotEmpty()) {
                lineTo(width, height)
                lineTo(0f, height)
                close()
            }
        }

        drawPath(
            path = fillPath,
            brush = androidx.compose.ui.graphics.Brush.verticalGradient(
                colors = gradientColors,
                startY = 0f,
                endY = height
            )
        )

        // Draw the stroke Path over it
        drawPath(
            path = path,
            color = dynamicLineColor,
            style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round)
        )
    }
}

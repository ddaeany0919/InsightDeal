package com.ddaeany0919.insightdeal.presentation

import android.graphics.Paint
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import kotlin.math.max

/**
 * 럭셔리 가격 변동 차트 (PriceChart)
 * - 유려한 곡선(Cubic)
 * - 하단 그라데이션
 * - 포인트 인터랙션 (Selection Tooltip)
 */
@Composable
fun PriceChart(
    prices: List<Float>,
    dates: List<String>,
    modifier: Modifier = Modifier
) {
    if (prices.isEmpty()) return

    var selectedIndex by remember { mutableStateOf<Int?>(null) }
    
    val maxPrice = prices.maxOrNull() ?: 0f
    val minPrice = prices.minOrNull() ?: 0f
    val range = if (maxPrice - minPrice == 0f) 10000f else maxPrice - minPrice
    val padding = range * 0.25f // 25% 상하 여백 (숫자 짤림 방지)
    
    val chartMax = maxPrice + padding
    val chartMin = max(0f, minPrice - padding)
    
    val primaryColor = MaterialTheme.colorScheme.primary
    val gradientBrush = remember {
        Brush.verticalGradient(
            colors = listOf(primaryColor.copy(alpha = 0.5f), Color.Transparent)
        )
    }

    // 진입 시 애니메이션 그리기
    val lineProgress = remember { Animatable(0f) }
    LaunchedEffect(prices) {
        lineProgress.snapTo(0f)
        lineProgress.animateTo(1f, animationSpec = tween(1200))
    }

    Box(modifier = modifier) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(prices) {
                    awaitPointerEventScope {
                        while (true) {
                            val event = awaitPointerEvent(androidx.compose.ui.input.pointer.PointerEventPass.Initial)
                            val pointer = event.changes.firstOrNull()
                            if (pointer != null && pointer.pressed) {
                                val itemWidth = size.width / max(1, prices.size - 1)
                                val index = (pointer.position.x / itemWidth).toInt().coerceIn(0, prices.lastIndex)
                                selectedIndex = index
                            } else {
                                selectedIndex = null
                            }
                        }
                    }
                }
        ) {
            val width = size.width
            val height = size.height
            val xStep = if (prices.size > 1) width / (prices.size - 1) else width
            val yRange = chartMax - chartMin

            val linePath = Path()
            val fillPath = Path()
            val points = mutableListOf<Offset>()

            prices.forEachIndexed { i, price ->
                val x = i * xStep
                val y = if (yRange == 0f) height / 2 else height - ((price - chartMin) / yRange) * height
                points.add(Offset(x, y))
                
                if (i == 0) {
                    linePath.moveTo(x, y)
                    fillPath.moveTo(x, height)
                    fillPath.lineTo(x, y)
                    if (prices.size == 1) {
                        linePath.lineTo(width, y)
                        fillPath.lineTo(width, y)
                    }
                } else {
                    val prev = points[i - 1]
                    if (prices.size < 5) {
                        // 데이터가 적을 때는 오버슈팅 방지를 위해 직선형(LineTo)으로 렌더링
                        linePath.lineTo(x, y)
                        fillPath.lineTo(x, y)
                    } else {
                        // 유려한 3차 베지어 곡선(CubicTo) 렌더링
                        val controlX = (prev.x + x) / 2
                        linePath.cubicTo(controlX, prev.y, controlX, y, x, y)
                        fillPath.cubicTo(controlX, prev.y, controlX, y, x, y)
                    }
                }
            }

            // 가로 진행도(progress)만큼 렌더링
            clipRect(right = width * lineProgress.value) {
                fillPath.lineTo(width, height)
                fillPath.close()

                // 채우기 (Gradient)
                drawPath(path = fillPath, brush = gradientBrush)

                // 선 그리기
                drawPath(
                    path = linePath,
                    color = primaryColor,
                    style = Stroke(width = 4.dp.toPx())
                )
            }

            // 툴팁 렌더링 (Selection)
            selectedIndex?.let { index ->
                val point = points[index]
                
                // 기준선 (수직가이드라인)
                drawLine(
                    color = primaryColor.copy(alpha = 0.4f),
                    start = Offset(point.x, 0f),
                    end = Offset(point.x, height),
                    strokeWidth = 2f,
                    pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(10f, 10f))
                )
                
                // 원형 마커
                drawCircle(color = primaryColor, radius = 6.dp.toPx(), center = point)
                drawCircle(color = Color.White, radius = 3.dp.toPx(), center = point)

                val priceText = String.format("%,d원", prices[index].toInt())
                val rawDate: String? = if (dates.isNotEmpty() && index < dates.size) dates[index] else ""
                val dateText = rawDate ?: ""
                
                val textPaint = Paint().apply {
                    color = primaryColor.toArgb()
                    textSize = 38f
                    isAntiAlias = true
                    textAlign = Paint.Align.CENTER
                    isFakeBoldText = true
                }
                val datePaint = Paint().apply {
                    color = android.graphics.Color.GRAY
                    textSize = 28f
                    isAntiAlias = true
                    textAlign = Paint.Align.CENTER
                }
                
                // 말풍선 위치 (화면 상단으로 안 넘어가게 보정)
                val tooltipY = max(80f, point.y - 50f)
                
                // 텍스트가 좌우로 잘리지 않게 x 좌표 보정
                val priceTextWidth = textPaint.measureText(priceText)
                val dateTextWidth = datePaint.measureText(dateText)
                val maxTextWidth = max(priceTextWidth, dateTextWidth)
                val textX = point.x.coerceIn(maxTextWidth / 2f + 10f, width - maxTextWidth / 2f - 10f)

                drawContext.canvas.nativeCanvas.drawText(priceText, textX, tooltipY, textPaint)
                drawContext.canvas.nativeCanvas.drawText(dateText, textX, tooltipY + 40f, datePaint)
            }
        }
    }
}

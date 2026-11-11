package com.ddaeany0919.insightdeal.presentation

import android.graphics.Paint
import android.graphics.RectF
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * 핫구사(Hotgusa) 스타일 고명확도 가격 변동 차트 (PriceChart)
 * - 평균가 수평 점선 가이드라인 (Average Reference Line)
 * - 최저가 / 최고가 포인트 자동 마커 및 라벨 표시 (터치 전에도 한눈에 파악)
 * - 터치/드래그 인터랙션 말풍선 툴팁 (평균 대비 등락 표시)
 * - [jetpack-compose-performance] Paint 및 Path 객체 remember 캐싱으로 60fps 드로잉 보장
 */
@Composable
fun PriceChart(
    prices: List<Float>,
    dates: List<String>,
    modifier: Modifier = Modifier,
    currency: String = "KRW"
) {
    val validPairs = remember(prices, dates) {
        prices.mapIndexedNotNull { idx, p ->
            if (p.isFinite() && p > 0f) {
                p to (dates.getOrNull(idx) ?: "")
            } else {
                null
            }
        }
    }
    val safePrices = remember(validPairs) { validPairs.map { it.first } }
    val safeDates = remember(validPairs) { validPairs.map { it.second } }

    if (safePrices.isEmpty()) return

    var selectedIndex by remember(safePrices) { mutableStateOf<Int?>(null) }

    val maxPrice = remember(safePrices) { safePrices.maxOrNull() ?: 0f }
    val minPrice = remember(safePrices) { safePrices.minOrNull() ?: 0f }
    val avgPrice = remember(safePrices) { if (safePrices.isNotEmpty()) safePrices.average().toFloat() else 0f }
    val minIndex = remember(safePrices, minPrice) { safePrices.indexOfLast { it == minPrice }.coerceAtLeast(0) }
    val maxIndex = remember(safePrices, maxPrice) { safePrices.indexOfFirst { it == maxPrice }.coerceAtLeast(0) }

    val range = remember(maxPrice, minPrice) {
        if (maxPrice - minPrice == 0f) max(1f, maxPrice * 0.12f) else maxPrice - minPrice
    }
    val padding = remember(range) { range * 0.30f } // 상하 30% 여백 확보 (최고/최저 콜아웃 짤림 방지)

    val chartMax = maxPrice + padding
    val chartMin = max(0f, minPrice - padding)

    val primaryColor = MaterialTheme.colorScheme.primary
    val surfaceColor = MaterialTheme.colorScheme.surface
    val onSurfaceColor = MaterialTheme.colorScheme.onSurfaceVariant
    val lowBadgeColor = Color(0xFF10B981) // 최저가 에메랄드 포인트
    val highBadgeColor = Color(0xFFF59E0B) // 최고가 앰버 포인트

    val gradientBrush = remember(primaryColor) {
        Brush.verticalGradient(
            colors = listOf(primaryColor.copy(alpha = 0.38f), Color.Transparent)
        )
    }

    val dashEffect = remember { PathEffect.dashPathEffect(floatArrayOf(12f, 10f), 0f) }
    val linePath = remember { Path() }
    val fillPath = remember { Path() }

    val density = LocalDensity.current
    val axisPaint = remember(onSurfaceColor, density) {
        Paint().apply {
            color = onSurfaceColor.toArgb()
            textSize = with(density) { 11.dp.toPx() }
            isAntiAlias = true
            textAlign = Paint.Align.CENTER
        }
    }
    val avgLabelPaint = remember(onSurfaceColor, density) {
        Paint().apply {
            color = onSurfaceColor.copy(alpha = 0.85f).toArgb()
            textSize = with(density) { 10.dp.toPx() }
            isAntiAlias = true
            textAlign = Paint.Align.RIGHT
            isFakeBoldText = true
        }
    }
    val badgePaint = remember(density) {
        Paint().apply {
            textSize = with(density) { 10.5.dp.toPx() }
            isAntiAlias = true
            textAlign = Paint.Align.CENTER
            isFakeBoldText = true
        }
    }
    val tooltipPricePaint = remember(primaryColor, density) {
        Paint().apply {
            color = primaryColor.toArgb()
            textSize = with(density) { 13.dp.toPx() }
            isAntiAlias = true
            textAlign = Paint.Align.CENTER
            isFakeBoldText = true
        }
    }
    val tooltipDatePaint = remember(onSurfaceColor, density) {
        Paint().apply {
            color = onSurfaceColor.toArgb()
            textSize = with(density) { 10.5.dp.toPx() }
            isAntiAlias = true
            textAlign = Paint.Align.CENTER
        }
    }
    val pillBgPaint = remember {
        Paint().apply {
            isAntiAlias = true
            style = Paint.Style.FILL
        }
    }
    val pillRect = remember { RectF() }

    // 진입 시 애니메이션 그리기
    val lineProgress = remember { Animatable(0f) }
    LaunchedEffect(safePrices) {
        lineProgress.snapTo(0f)
        lineProgress.animateTo(1f, animationSpec = tween(900))
    }

    Box(modifier = modifier) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(safePrices) {
                    awaitPointerEventScope {
                        while (true) {
                            val event = awaitPointerEvent(PointerEventPass.Initial)
                            val pointer = event.changes.firstOrNull()
                            if (pointer != null && pointer.pressed) {
                                val itemWidth = size.width / max(1, safePrices.size - 1)
                                val index = ((pointer.position.x + itemWidth / 2f) / itemWidth)
                                    .toInt()
                                    .coerceIn(0, safePrices.lastIndex)
                                selectedIndex = index
                            } else {
                                selectedIndex = null
                            }
                        }
                    }
                }
        ) {
            val width = size.width
            val bottomLabelSpace = 26.dp.toPx()
            val height = size.height - bottomLabelSpace
            val xStep = if (safePrices.size > 1) width / (safePrices.size - 1) else width
            val yRange = (chartMax - chartMin).coerceAtLeast(0.001f)

            linePath.reset()
            fillPath.reset()
            val points = ArrayList<Offset>(safePrices.size)

            safePrices.forEachIndexed { i, price ->
                val x = i * xStep
                val y = height - ((price - chartMin) / yRange) * height
                points.add(Offset(x, y))

                if (i == 0) {
                    linePath.moveTo(x, y)
                    fillPath.moveTo(x, height)
                    fillPath.lineTo(x, y)
                    if (safePrices.size == 1) {
                        linePath.lineTo(width, y)
                        fillPath.lineTo(width, y)
                    }
                } else {
                    val prev = points[i - 1]
                    if (safePrices.size < 5) {
                        linePath.lineTo(x, y)
                        fillPath.lineTo(x, y)
                    } else {
                        val controlX = (prev.x + x) / 2f
                        linePath.cubicTo(controlX, prev.y, controlX, y, x, y)
                        fillPath.cubicTo(controlX, prev.y, controlX, y, x, y)
                    }
                }
            }

            // 1. 수평 그리드 및 평균가 기준선 (Average Reference Line)
            val avgY = height - ((avgPrice - chartMin) / yRange) * height
            drawLine(
                color = onSurfaceColor.copy(alpha = 0.28f),
                start = Offset(0f, avgY),
                end = Offset(width, avgY),
                strokeWidth = 1.2.dp.toPx(),
                pathEffect = dashEffect
            )
            if (maxPrice > minPrice) {
                val avgText = "평균 ${formatPrice(avgPrice.toLong(), currency)}"
                drawContext.canvas.nativeCanvas.drawText(
                    avgText,
                    width - 4.dp.toPx(),
                    (avgY - 6.dp.toPx()).coerceIn(14.dp.toPx(), height - 6.dp.toPx()),
                    avgLabelPaint
                )
            }

            // 2. 가로 진행도(progress)만큼 메인 곡선 & 그라데이션 렌더링
            clipRect(right = width * lineProgress.value) {
                fillPath.lineTo(width, height)
                fillPath.close()

                drawPath(path = fillPath, brush = gradientBrush)

                drawPath(
                    path = linePath,
                    color = primaryColor,
                    style = Stroke(
                        width = 3.2.dp.toPx(),
                        cap = StrokeCap.Round,
                        join = StrokeJoin.Round
                    )
                )
            }

            // 3. 각 데이터 포인트 도트 표시 (포인트가 15개 이하일 때 명확히 표시)
            if (safePrices.size <= 15 && lineProgress.value > 0.85f) {
                points.forEachIndexed { idx, pt ->
                    val dotColor = when (idx) {
                        minIndex -> lowBadgeColor
                        maxIndex -> if (maxPrice > minPrice) highBadgeColor else primaryColor
                        else -> primaryColor
                    }
                    drawCircle(color = dotColor, radius = 4.dp.toPx(), center = pt)
                    drawCircle(color = surfaceColor, radius = 2.dp.toPx(), center = pt)
                }
            }

            // 4. X축 날짜 라벨 렌더링 (겹침 방지)
            val indicesToDraw = mutableListOf<Int>()
            if (safeDates.isNotEmpty()) {
                indicesToDraw.add(safeDates.lastIndex)
                var lastAddedLeftEdge = width - axisPaint.measureText(safeDates.last())

                for (i in safeDates.lastIndex - 1 downTo 0) {
                    val safeDate = safeDates[i]
                    val x = i * xStep
                    val textWidth = axisPaint.measureText(safeDate)
                    val safeX = x.coerceIn(textWidth / 2f, width - textWidth / 2f)
                    val rightEdge = safeX + textWidth / 2f

                    if (rightEdge + 24.dp.toPx() < lastAddedLeftEdge) {
                        indicesToDraw.add(i)
                        lastAddedLeftEdge = safeX - textWidth / 2f
                    }
                }
            }
            indicesToDraw.reverse()

            indicesToDraw.forEach { i ->
                val safeDate = safeDates[i]
                val x = i * xStep
                val textWidth = axisPaint.measureText(safeDate)
                val safeX = x.coerceIn(textWidth / 2f, width - textWidth / 2f)
                drawContext.canvas.nativeCanvas.drawText(
                    safeDate,
                    safeX,
                    size.height - 4.dp.toPx(),
                    axisPaint
                )
            }

            // 5. 터치 전 기본 상태: 최저가 / 최고가 콜아웃 배지 자동 표시
            if (selectedIndex == null && lineProgress.value > 0.9f && points.isNotEmpty()) {
                // 최저가 배지
                val minPt = points[minIndex]
                val minLabel = "최저 ${formatPrice(minPrice.toLong(), currency)}"
                val minTextW = badgePaint.measureText(minLabel)
                val minX = minPt.x.coerceIn(minTextW / 2f + 8.dp.toPx(), width - minTextW / 2f - 8.dp.toPx())
                val minY = min(height - 10.dp.toPx(), minPt.y + 20.dp.toPx())

                pillBgPaint.color = lowBadgeColor.copy(alpha = 0.16f).toArgb()
                pillRect.set(
                    minX - minTextW / 2f - 6.dp.toPx(),
                    minY - 12.dp.toPx(),
                    minX + minTextW / 2f + 6.dp.toPx(),
                    minY + 4.dp.toPx()
                )
                drawContext.canvas.nativeCanvas.drawRoundRect(pillRect, 6.dp.toPx(), 6.dp.toPx(), pillBgPaint)
                badgePaint.color = lowBadgeColor.toArgb()
                drawContext.canvas.nativeCanvas.drawText(minLabel, minX, minY, badgePaint)

                // 최고가 배지 (최저가와 다른 가격일 때만 표시)
                if (maxPrice > minPrice && maxIndex != minIndex) {
                    val maxPt = points[maxIndex]
                    val maxLabel = "최고 ${formatPrice(maxPrice.toLong(), currency)}"
                    val maxTextW = badgePaint.measureText(maxLabel)
                    val maxX = maxPt.x.coerceIn(maxTextW / 2f + 8.dp.toPx(), width - maxTextW / 2f - 8.dp.toPx())
                    val maxY = max(16.dp.toPx(), maxPt.y - 12.dp.toPx())

                    pillBgPaint.color = highBadgeColor.copy(alpha = 0.16f).toArgb()
                    pillRect.set(
                        maxX - maxTextW / 2f - 6.dp.toPx(),
                        maxY - 12.dp.toPx(),
                        maxX + maxTextW / 2f + 6.dp.toPx(),
                        maxY + 4.dp.toPx()
                    )
                    drawContext.canvas.nativeCanvas.drawRoundRect(pillRect, 6.dp.toPx(), 6.dp.toPx(), pillBgPaint)
                    badgePaint.color = highBadgeColor.toArgb()
                    drawContext.canvas.nativeCanvas.drawText(maxLabel, maxX, maxY, badgePaint)
                }
            }

            // 6. 터치 시 인터랙티브 툴팁 렌더링
            selectedIndex?.takeIf { it in points.indices && it in safePrices.indices }?.let { index ->
                val point = points[index]

                drawLine(
                    color = primaryColor.copy(alpha = 0.45f),
                    start = Offset(point.x, 0f),
                    end = Offset(point.x, height),
                    strokeWidth = 1.5.dp.toPx(),
                    pathEffect = dashEffect
                )

                drawCircle(color = primaryColor, radius = 6.5.dp.toPx(), center = point)
                drawCircle(color = Color.White, radius = 3.dp.toPx(), center = point)

                val selectedPrice = safePrices[index]
                val diffPct = if (avgPrice > 0f) (((selectedPrice - avgPrice) / avgPrice) * 100f).roundToInt() else 0
                val diffSuffix = when {
                    diffPct < 0 -> " (평균 ${diffPct}%)"
                    diffPct > 0 -> " (평균 +${diffPct}%)"
                    else -> ""
                }
                val priceText = formatPrice(selectedPrice.toLong(), currency) + diffSuffix
                val dateText = if (safeDates.isNotEmpty() && index < safeDates.size) safeDates[index] else ""

                val maxTextWidth = max(tooltipPricePaint.measureText(priceText), tooltipDatePaint.measureText(dateText))
                val boxHalfW = maxTextWidth / 2f + 10.dp.toPx()
                val textX = point.x.coerceIn(boxHalfW + 4.dp.toPx(), width - boxHalfW - 4.dp.toPx())
                val tooltipY = max(22.dp.toPx(), point.y - 28.dp.toPx())

                pillBgPaint.color = surfaceColor.copy(alpha = 0.94f).toArgb()
                pillRect.set(
                    textX - boxHalfW,
                    tooltipY - 16.dp.toPx(),
                    textX + boxHalfW,
                    tooltipY + (if (dateText.isNotEmpty()) 18.dp.toPx() else 6.dp.toPx())
                )
                drawContext.canvas.nativeCanvas.drawRoundRect(pillRect, 8.dp.toPx(), 8.dp.toPx(), pillBgPaint)

                drawContext.canvas.nativeCanvas.drawText(priceText, textX, tooltipY, tooltipPricePaint)
                if (dateText.isNotEmpty()) {
                    drawContext.canvas.nativeCanvas.drawText(dateText, textX, tooltipY + 13.dp.toPx(), tooltipDatePaint)
                }
            }
        }
    }
}

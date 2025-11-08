// 기존 기간별 선택 유지, 차트포인트에 최저가 쇼핑몰 이름 표시 예시
// 상세 UI 코드 등은 실제 앱 구조에 맞춰 작성 필요

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.dp

@Composable
fun PriceChart(
    prices: List<Float>,
    stores: List<String>,
    selectedPeriod: String
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        // 기간별 필터링 가격 데이터 (예)
        Text(text = "Selected Period: $selectedPeriod")
        // 차트 그리기
        Canvas(modifier = Modifier.height(200.dp).fillMaxWidth()) {
            drawChartWithStores(prices, stores)
        }
    }
}

fun DrawScope.drawChartWithStores(prices: List<Float>, stores: List<String>) {
    // 가격 이력에 대해 포인트 표시 및 최저가 쇼핑몰 이름 표시 예
    if (prices.isEmpty() || stores.isEmpty()) return

    val pointRadius = 5f
    prices.forEachIndexed { index, price ->
        val x = (index + 1) * (size.width / prices.size)
        val y = size.height - (price / (prices.maxOrNull() ?: 1f)) * size.height
        drawCircle(Color.Blue, pointRadius, center = androidx.compose.ui.geometry.Offset(x, y))
        drawContext.canvas.nativeCanvas.drawText(
            stores[index],
            x,
            y - 10,
            android.graphics.Paint().apply {
                color = android.graphics.Color.BLACK
                textSize = 30f
            }
        )
    }
}

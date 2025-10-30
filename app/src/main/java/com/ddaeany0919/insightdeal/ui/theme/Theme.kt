package com.ddaeany0919.insightdeal.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable

@Composable
fun InsightDealTheme(
    darkTheme: Boolean = false,
    dynamicColor: Boolean = false,
    themeMode: com.ddaeany0919.insightdeal.ThemeMode = com.ddaeany0919.insightdeal.ThemeMode.LIGHT,
    colorScheme: com.ddaeany0919.insightdeal.AppColorScheme = com.ddaeany0919.insightdeal.AppColorScheme.ORANGE_CLASSIC,
    amoledMode: Boolean = false,
    content: @Composable () -> Unit
) {
    // 라이트 모드 강제 적용: 흰 배경 유지
    val colors = getOrangeTheme().copy(
        background = androidx.compose.ui.graphics.Color.White,
        surface = androidx.compose.ui.graphics.Color.White,
        onBackground = androidx.compose.ui.graphics.Color(0xFF111111),
        onSurface = androidx.compose.ui.graphics.Color(0xFF111111)
    )
    MaterialTheme(colorScheme = colors, typography = Typography) {
        Surface(color = MaterialTheme.colorScheme.background) {
            content()
        }
    }
}

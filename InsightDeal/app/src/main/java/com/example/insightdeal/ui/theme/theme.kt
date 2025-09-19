package com.example.insightdeal.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

// 라이트 다크 모드에 따른 컬러 스킴 정의
private val LightColors = lightColorScheme(
    primary = md_theme_light_primary,
    onPrimary = md_theme_light_onPrimary,
    primaryContainer = md_theme_light_primaryContainer,
    onPrimaryContainer = md_theme_light_onPrimaryContainer,
    background = md_theme_light_background,
    surface = md_theme_light_surface,
    error = md_theme_light_error,
    onError = md_theme_light_onError
    // 필요시 더 많은 속성 추가 설정 가능
)

private val DarkColors = darkColorScheme(
    primary = md_theme_dark_primary,
    onPrimary = md_theme_dark_onPrimary,
    primaryContainer = md_theme_dark_primaryContainer,
    onPrimaryContainer = md_theme_dark_onPrimaryContainer,
    background = md_theme_dark_background,
    surface = md_theme_dark_surface,
    error = md_theme_dark_error,
    onError = md_theme_dark_onError
    // 필요시 더 많은 속성 추가 설정 가능
)

/**
 * 앱 테마 컴포저블
 * 시스템 다크모드에 따라 자동 색상 스킴 적용
 * @param useDarkTheme 다크 모드 강제 적용 여부. 기본은 시스템 모드
 */
@Composable
fun InsightDealTheme(
    useDarkTheme: Boolean = false,
    content: @Composable () -> Unit
) {
    val colors = if (useDarkTheme) DarkColors else LightColors

    MaterialTheme(
        colorScheme = colors,
        typography = Typography,
        content = content
    )
}

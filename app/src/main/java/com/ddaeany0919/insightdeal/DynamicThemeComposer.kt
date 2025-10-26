package com.ddaeany0919.insightdeal

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

/**
 * 🌌 동적 테마 컴포저
 * 
 * 실시간 테마 전환 애니메이션 및 동적 컴러 조정
 */
@Composable
fun DynamicThemeProvider(
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val themeManager = remember { ThemeManager.getInstance(context) }
    
    val themeMode by themeManager.themeMode.collectAsState()
    val colorScheme by themeManager.colorScheme.collectAsState()
    val amoledMode by themeManager.amoledMode.collectAsState()
    
    val systemInDarkTheme = isSystemInDarkTheme()
    val useDarkTheme = themeManager.shouldUseDarkTheme(systemInDarkTheme)
    
    // 🎆 동적 컴러 스킴 애니메이션
    // 🎆 동적 컴러 스킴 애니메이션
    val dynamicColorScheme = createAnimatedColorScheme(
        baseScheme = themeManager.getCurrentColorScheme(
            darkTheme = useDarkTheme,
            systemInDarkTheme = systemInDarkTheme
        )
    )
    
    MaterialTheme(
        colorScheme = dynamicColorScheme,
        typography = InsightDealTypography,
        content = content
    )
}

/**
 * 🌈 애니메이션 컴러 스킴 생성
 */
@Composable
private fun createAnimatedColorScheme(baseScheme: ColorScheme): ColorScheme {
    val animationDuration = 1000 // 1초 애니메이션
    
    val animatedPrimary by animateColorAsState(
        targetValue = baseScheme.primary,
        animationSpec = tween(durationMillis = animationDuration),
        label = "primaryColor"
    )
    
    val animatedSurface by animateColorAsState(
        targetValue = baseScheme.surface,
        animationSpec = tween(durationMillis = animationDuration),
        label = "surfaceColor"
    )
    
    val animatedBackground by animateColorAsState(
        targetValue = baseScheme.background,
        animationSpec = tween(durationMillis = animationDuration),
        label = "backgroundColor"
    )
    
    return baseScheme.copy(
        primary = animatedPrimary,
        surface = animatedSurface,
        background = animatedBackground
    )
}

/**
 * 🌃 시간대별 동적 테마 조절
 */
@Composable
fun AutoThemeEffect() {
    val context = LocalContext.current
    val themeManager = remember { ThemeManager.getInstance(context) }
    val themeMode by themeManager.themeMode.collectAsState()
    
    // 1분마다 시간 체크 (AUTO_TIME 모드일 때)
    LaunchedEffect(themeMode) {
        if (themeMode == ThemeMode.AUTO_TIME) {
            // 실제 구현에서는 Timer나 코루틴 사용
            // 현재는 상징적 구현
        }
    }
}

/**
 * 🎨 테마 전환 효과
 */
@Composable
fun ThemeTransitionEffect(
    isChanging: Boolean,
    content: @Composable () -> Unit
) {
    // 테마 전환 시 매끄러운 애니메이션 효과
    androidx.compose.animation.AnimatedVisibility(
        visible = !isChanging,
        enter = androidx.compose.animation.fadeIn(tween(500)),
        exit = androidx.compose.animation.fadeOut(tween(300))
    ) {
        content()
    }
}
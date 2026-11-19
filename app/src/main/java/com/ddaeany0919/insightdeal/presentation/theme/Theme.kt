package com.ddaeany0919.insightdeal.presentation.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

// 🎨 얼만데(InsightDeal) 한국형 플랫 미니멀 팔레트 (WCAG AA 4.5:1 준수)
val PrimaryDark = EmeraldPrimaryLight
val PrimaryLight = White
val AccentOrange = Color(0xFFC2410C)
val AccentRed = Color(0xFFC81E1E)
val AccentBlue = TossBlueLight
val AccentGreen = BrandEmeraldPoint

val BackgroundLight = CanvasLight
val SurfaceLight = CardLight
val BackgroundDark = CanvasDark
val SurfaceDark = CardDark

val TextPrimaryLight = InkPrimaryLight
val TextSecondaryLight = InkSecondaryLight
val TextPrimaryDark = InkPrimaryDark
val TextSecondaryDark = InkSecondaryDark

val PriceIncrease = AccentRed
val PriceDecrease = AccentBlue
val PriceTarget = EmeraldPrimaryLight
val PriceBest = AccentOrange

internal val LightColorScheme = lightColorScheme(
    primary = EmeraldPrimaryLight,
    onPrimary = White,
    primaryContainer = EmeraldContainerLight,
    onPrimaryContainer = OnEmeraldContainerLight,
    secondary = TossBlueLight,
    onSecondary = White,
    secondaryContainer = TossBlueContainerLight,
    onSecondaryContainer = OnTossBlueContainerLight,
    tertiary = Color(0xFFC2410C),
    onTertiary = White,
    tertiaryContainer = Color(0xFFFFF7ED),
    onTertiaryContainer = Color(0xFF9A3412),
    error = Color(0xFFC81E1E),
    onError = White,
    errorContainer = Color(0xFFFEE2E2),
    onErrorContainer = Color(0xFF991B1B),
    background = CanvasLight,
    onBackground = InkPrimaryLight,
    surface = CardLight,
    onSurface = InkPrimaryLight,
    surfaceVariant = SurfaceVariantLight,
    onSurfaceVariant = InkSecondaryLight,
    outline = BorderLight,
    outlineVariant = BorderLight
)

internal val DarkColorScheme = darkColorScheme(
    primary = EmeraldPrimaryDark,
    onPrimary = OnEmeraldDark,
    primaryContainer = EmeraldContainerDark,
    onPrimaryContainer = OnEmeraldContainerDark,
    secondary = TossBlueDark,
    onSecondary = OnTossBlueDark,
    secondaryContainer = TossBlueContainerDark,
    onSecondaryContainer = OnTossBlueContainerDark,
    tertiary = Color(0xFFFB923C),
    onTertiary = Color(0xFF2B1200),
    tertiaryContainer = Color(0xFF3D2210),
    onTertiaryContainer = Color(0xFFFDBA74),
    error = Color(0xFFF87171),
    onError = Color(0xFF3B0808),
    errorContainer = Color(0xFF450A0A),
    onErrorContainer = Color(0xFFFECACA),
    background = CanvasDark,
    onBackground = InkPrimaryDark,
    surface = CardDark,
    onSurface = InkPrimaryDark,
    surfaceVariant = SurfaceVariantDark,
    onSurfaceVariant = InkSecondaryDark,
    outline = BorderDark,
    outlineVariant = BorderDark
)

internal val AmoledColorScheme = darkColorScheme(
    primary = EmeraldPrimaryDark,
    onPrimary = OnEmeraldDark,
    primaryContainer = EmeraldContainerDark,
    onPrimaryContainer = OnEmeraldContainerDark,
    secondary = TossBlueDark,
    onSecondary = OnTossBlueDark,
    secondaryContainer = TossBlueContainerDark,
    onSecondaryContainer = OnTossBlueContainerDark,
    tertiary = Color(0xFFFB923C),
    onTertiary = Color(0xFF2B1200),
    tertiaryContainer = Color(0xFF3D2210),
    onTertiaryContainer = Color(0xFFFDBA74),
    error = Color(0xFFF87171),
    onError = Color(0xFF3B0808),
    errorContainer = Color(0xFF450A0A),
    onErrorContainer = Color(0xFFFECACA),
    background = Black,
    onBackground = White,
    surface = Black,
    onSurface = White,
    surfaceVariant = Color(0xFF16191F),
    onSurfaceVariant = InkSecondaryDark,
    outline = BorderAmoled,
    outlineVariant = BorderAmoled
)

val LocalIsDarkTheme = staticCompositionLocalOf { false }

val ColorScheme.isDarkTheme: Boolean
    get() = background.luminance() < 0.5f

@Composable
fun InsightDealTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    amoledMode: Boolean = false,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    remember { ThemeManager.getInstance(context) }

    val useDarkTheme = when (themeMode) {
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
        ThemeMode.AMOLED -> true
        ThemeMode.SYSTEM -> darkTheme
    }

    val isAmoled = themeMode == ThemeMode.AMOLED || (useDarkTheme && amoledMode)

    val finalColorScheme = when {
        isAmoled -> AmoledColorScheme
        useDarkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    // [edge-to-edge skill] 테마 전환 시 상태바/내비게이션바 시스템 아이콘 명암비 실시간 동기화
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window ?: return@SideEffect
            val insetsController = WindowCompat.getInsetsController(window, view)
            insetsController.isAppearanceLightStatusBars = !useDarkTheme
            insetsController.isAppearanceLightNavigationBars = !useDarkTheme
        }
    }

    val semanticColors = remember(useDarkTheme, isAmoled) {
        if (useDarkTheme) {
            if (isAmoled) DarkInsightDealColors.copy(cardBorder = BorderAmoled) else DarkInsightDealColors
        } else {
            LightInsightDealColors
        }
    }

    CompositionLocalProvider(
        LocalIsDarkTheme provides useDarkTheme,
        LocalInsightDealColors provides semanticColors
    ) {
        MaterialTheme(
            colorScheme = finalColorScheme,
            typography = Typography,
            content = content
        )
    }
}

@Composable
fun getPriceColor(isIncrease: Boolean): Color {
    val semantic = LocalInsightDealColors.current
    return if (isIncrease) semantic.danger else semantic.info
}

@Composable
fun getTargetAchievedColor(): Color = LocalInsightDealColors.current.success

@Composable
fun getBestPriceColor(): Color = LocalInsightDealColors.current.priceAccent

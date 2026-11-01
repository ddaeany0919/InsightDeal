package com.ddaeany0919.insightdeal.presentation.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import com.ddaeany0919.insightdeal.presentation.theme.AppColorScheme
import com.ddaeany0919.insightdeal.presentation.theme.ThemeMode
import com.ddaeany0919.insightdeal.presentation.theme.ThemeManager

// 🎨 InsightDeal Toss × Karrot SEED Design Hybrid Palette (DESIGN.md v2.0)
val White = Color(0xFFFFFFFF)
val Black = Color(0xFF000000)

val PrimaryDark = Color(0xFF3182F6) // Toss Primary Blue (#3182F6)
val PrimaryLight = Color(0xFFFFFFFF)
val AccentOrange = Color(0xFFFF6F0F) // Karrot SEED Design Warm Orange (#FF6F0F)
val AccentRed = Color(0xFFF04452) // Toss Urgent Red (#F04452)
val AccentBlue = Color(0xFF3182F6) // Toss Info/Brand Blue (#3182F6)
val AccentGreen = Color(0xFF00C471) // Toss Positive Green (#00C471)

val BackgroundLight = Color(0xFFF2F4F6) // Toss Cool Gray Canvas (#F2F4F6)
val SurfaceLight = Color(0xFFFFFFFF) // Pure White Card Surface (#FFFFFF)
val BackgroundDark = Color(0xFF101318) // Toss Dark Slate Canvas (#101318)
val SurfaceDark = Color(0xFF191F28) // Toss Charcoal Elevated Surface (#191F28)

val TextPrimaryLight = Color(0xFF191F28) // Toss Charcoal Primary Ink (#191F28)
val TextSecondaryLight = Color(0xFF4E5968) // Toss Slate Secondary Ink (#4E5968)
val TextPrimaryDark = Color(0xFFF9FAFB)
val TextSecondaryDark = Color(0xFFB0B8C1)

val PriceIncrease = AccentRed
val PriceDecrease = AccentBlue
val PriceTarget = AccentGreen
val PriceBest = AccentOrange

private val LightColorScheme = lightColorScheme(
    primary = PrimaryDark,
    onPrimary = White,
    primaryContainer = Color(0xFFE8F3FF),
    onPrimaryContainer = Color(0xFF1B64DA),
    secondary = AccentOrange,
    onSecondary = White,
    secondaryContainer = Color(0xFFFFF3E8),
    onSecondaryContainer = Color(0xFFE05900),
    tertiary = AccentBlue,
    onTertiary = White,
    error = AccentRed,
    onError = White,
    background = BackgroundLight,
    onBackground = TextPrimaryLight,
    surface = SurfaceLight,
    onSurface = TextPrimaryLight,
    surfaceVariant = Color(0xFFF9FAFB),
    onSurfaceVariant = TextSecondaryLight,
    outline = Color(0xFFE5E8EB)
)

private val DarkColorScheme = darkColorScheme(
    primary = Color(0xFF4593FC),
    onPrimary = White,
    primaryContainer = Color(0xFF1E3A5F),
    onPrimaryContainer = Color(0xFFD1E5FF),
    secondary = Color(0xFFFF8433),
    onSecondary = White,
    secondaryContainer = Color(0xFF3D2210),
    onSecondaryContainer = Color(0xFFFFD8BE),
    tertiary = Color(0xFF4593FC),
    onTertiary = White,
    error = AccentRed,
    onError = White,
    background = BackgroundDark,
    onBackground = TextPrimaryDark,
    surface = SurfaceDark,
    onSurface = TextPrimaryDark,
    surfaceVariant = Color(0xFF222934),
    onSurfaceVariant = TextSecondaryDark,
    outline = Color(0xFF2B333E)
)

private val AmoledColorScheme = darkColorScheme(
    primary = White,
    onPrimary = PrimaryDark,
    primaryContainer = Black,
    onPrimaryContainer = White,
    secondary = AccentOrange,
    onSecondary = White,
    secondaryContainer = Color(0xFF3E2723),
    onSecondaryContainer = Color(0xFFFFCCBC),
    tertiary = AccentBlue,
    onTertiary = White,
    error = AccentRed,
    onError = White,
    background = Black,
    onBackground = White,
    surface = Black,
    onSurface = White,
    surfaceVariant = Color(0xFF1A1A1A),
    onSurfaceVariant = TextSecondaryDark,
    outline = Color(0xFF333333)
)

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

    val finalColorScheme = when {
        useDarkTheme && amoledMode -> AmoledColorScheme
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

    MaterialTheme(
        colorScheme = finalColorScheme,
        typography = Typography,
        content = content
    )
}

@Composable
fun getPriceColor(isIncrease: Boolean): Color = if (isIncrease) PriceIncrease else PriceDecrease
@Composable
fun getTargetAchievedColor(): Color = PriceTarget
@Composable
fun getBestPriceColor(): Color = PriceBest

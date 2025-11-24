package com.ddaeany0919.insightdeal.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import com.ddaeany0919.insightdeal.AppColorScheme
import com.ddaeany0919.insightdeal.ThemeMode
import com.ddaeany0919.insightdeal.data.theme.ThemeManager

// 🎨 InsightDeal Premium Palette
val White = Color(0xFFFFFFFF)
val Black = Color(0xFF000000)

val PrimaryDark = Color(0xFF1A1C29) // Deep Navy/Black
val PrimaryLight = Color(0xFFFFFFFF)
val AccentOrange = Color(0xFFFF5722) // Vibrant Hot Deal Orange
val AccentRed = Color(0xFFD32F2F) // Sale Red
val AccentBlue = Color(0xFF2196F3) // Info Blue
val AccentGreen = Color(0xFF4CAF50) // Success Green

val BackgroundLight = Color(0xFFF5F7FA) // Very light blue-gray
val SurfaceLight = Color(0xFFFFFFFF)
val BackgroundDark = Color(0xFF121212)
val SurfaceDark = Color(0xFF1E1E1E)

val TextPrimaryLight = Color(0xFF212121)
val TextSecondaryLight = Color(0xFF757575)
val TextPrimaryDark = Color(0xFFEEEEEE)
val TextSecondaryDark = Color(0xFFB0B0B0)

val PriceIncrease = AccentRed
val PriceDecrease = AccentBlue
val PriceTarget = AccentGreen
val PriceBest = AccentOrange

private val LightColorScheme = lightColorScheme(
    primary = PrimaryDark,
    onPrimary = White,
    primaryContainer = White,
    onPrimaryContainer = PrimaryDark,
    secondary = AccentOrange,
    onSecondary = White,
    secondaryContainer = Color(0xFFFFCCBC),
    onSecondaryContainer = Color(0xFFBF360C),
    tertiary = AccentBlue,
    onTertiary = White,
    error = AccentRed,
    onError = White,
    background = BackgroundLight,
    onBackground = TextPrimaryLight,
    surface = SurfaceLight,
    onSurface = TextPrimaryLight,
    surfaceVariant = Color(0xFFEEEEEE),
    onSurfaceVariant = TextSecondaryLight,
    outline = Color(0xFFE0E0E0)
)

private val DarkColorScheme = darkColorScheme(
    primary = White,
    onPrimary = PrimaryDark,
    primaryContainer = SurfaceDark,
    onPrimaryContainer = White,
    secondary = AccentOrange,
    onSecondary = White,
    secondaryContainer = Color(0xFF3E2723),
    onSecondaryContainer = Color(0xFFFFCCBC),
    tertiary = AccentBlue,
    onTertiary = White,
    error = AccentRed,
    onError = White,
    background = BackgroundDark,
    onBackground = TextPrimaryDark,
    surface = SurfaceDark,
    onSurface = TextPrimaryDark,
    surfaceVariant = Color(0xFF2C2C2C),
    onSurfaceVariant = TextSecondaryDark,
    outline = Color(0xFF424242)
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
    dynamicColor: Boolean = false,
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    colorScheme: AppColorScheme = AppColorScheme.ORANGE_CLASSIC,
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
        ThemeMode.AUTO_TIME -> {
            val hour = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)
            hour >= 19 || hour <= 7
        }
        else -> darkTheme // Fallback for safety
    }

    val finalColorScheme = when {
        useDarkTheme && amoledMode -> AmoledColorScheme
        useDarkTheme -> DarkColorScheme
        else -> LightColorScheme
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

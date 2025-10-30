package com.ddaeany0919.insightdeal.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.runtime.*
import com.ddaeany0919.insightdeal.AppColorScheme
import com.ddaeany0919.insightdeal.ThemeMode
import com.ddaeany0919.insightdeal.data.theme.ThemeManager

// 🎨 InsightDeal 브랜드 컬러 팔레트
val PrimaryOrange = Color(0xFFFF6B35)
val PrimaryOrangeLight = Color(0xFFFFE5DB)
val PrimaryOrangeDark = Color(0xFFE55A2B)
val SecondaryBlue = Color(0xFF2196F3)
val SecondaryGreen = Color(0xFF4CAF50)
val SecondaryRed = Color(0xFFF44336)
val NeutralGray = Color(0xFF9E9E9E)
val LightGray = Color(0xFFF5F5F5)
val DarkGray = Color(0xFF333333)
val White = Color(0xFFFFFFFF)
val Black = Color(0xFF000000)

val PriceIncrease = SecondaryRed
val PriceDecrease = SecondaryBlue
val PriceTarget = SecondaryGreen
val PriceBest = Color(0xFFFF9800)

private val LightColorScheme = lightColorScheme(
    primary = PrimaryOrange,
    onPrimary = White,
    primaryContainer = PrimaryOrangeLight,
    onPrimaryContainer = DarkGray,
    secondary = SecondaryBlue,
    onSecondary = White,
    secondaryContainer = Color(0xFFE3F2FD),
    onSecondaryContainer = DarkGray,
    tertiary = SecondaryGreen,
    onTertiary = White,
    tertiaryContainer = Color(0xFFE8F5E8),
    onTertiaryContainer = DarkGray,
    error = SecondaryRed,
    onError = White,
    errorContainer = Color(0xFFFFEBEE),
    onErrorContainer = DarkGray,
    background = White,
    onBackground = DarkGray,
    surface = White,
    onSurface = DarkGray,
    surfaceVariant = LightGray,
    onSurfaceVariant = NeutralGray,
    outline = Color(0xFFE0E0E0),
    outlineVariant = Color(0xFFF0F0F0)
)

private val DarkColorScheme = darkColorScheme(
    primary = PrimaryOrange,
    onPrimary = White,
    primaryContainer = PrimaryOrangeDark,
    onPrimaryContainer = White,
    secondary = SecondaryBlue,
    onSecondary = White,
    secondaryContainer = Color(0xFF1565C0),
    onSecondaryContainer = White,
    tertiary = SecondaryGreen,
    onTertiary = White,
    tertiaryContainer = Color(0xFF2E7D32),
    onTertiaryContainer = White,
    error = SecondaryRed,
    onError = White,
    errorContainer = Color(0xFFB71C1C),
    onErrorContainer = White,
    background = Color(0xFF121212),
    onBackground = White,
    surface = Color(0xFF1E1E1E),
    onSurface = White,
    surfaceVariant = Color(0xFF2A2A2A),
    onSurfaceVariant = Color(0xFFBBBBBB),
    outline = Color(0xFF424242),
    outlineVariant = Color(0xFF333333)
)

private val AmoledColorScheme = darkColorScheme(
    primary = PrimaryOrange,
    onPrimary = White,
    primaryContainer = PrimaryOrangeDark,
    onPrimaryContainer = White,
    secondary = SecondaryBlue,
    onSecondary = White,
    secondaryContainer = Color(0xFF1565C0),
    onSecondaryContainer = White,
    tertiary = SecondaryGreen,
    onTertiary = White,
    tertiaryContainer = Color(0xFF2E7D32),
    onTertiaryContainer = White,
    error = SecondaryRed,
    onError = White,
    errorContainer = Color(0xFFB71C1C),
    onErrorContainer = White,
    background = Black,
    onBackground = White,
    surface = Black,
    onSurface = White,
    surfaceVariant = Color(0xFF1A1A1A),
    onSurfaceVariant = Color(0xFFBBBBBB),
    outline = Color(0xFF333333),
    outlineVariant = Color(0xFF222222)
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
    val themeManager = remember { ThemeManager.getInstance(context) }

    val useDarkTheme = when (themeMode) {
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
        ThemeMode.AMOLED -> true
        ThemeMode.SYSTEM -> darkTheme
        ThemeMode.AUTO_TIME -> {
            val hour = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)
            hour >= 19 || hour <= 7
        }
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

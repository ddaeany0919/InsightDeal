package com.ddaeany0919.insightdeal.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// 🎨 InsightDeal 브랜드 컬러 팔레트
val PrimaryOrange = Color(0xFFFF6B35)      // 메인 브랜드 컬러 (폴센트 대비 더 따뜻함)
val PrimaryOrangeLight = Color(0xFFFFE5DB) // 밝은 주황 (배경, 강조)
val PrimaryOrangeDark = Color(0xFFE55A2B)  // 진한 주황 (버튼 press)

val SecondaryBlue = Color(0xFF2196F3)      // 하락 가격 표시
val SecondaryGreen = Color(0xFF4CAF50)     // 목표 달성, 좋은 소식
val SecondaryRed = Color(0xFFF44336)       // 상승 가격, 경고

val NeutralGray = Color(0xFF9E9E9E)        // 일반 텍스트
val LightGray = Color(0xFFF5F5F5)          // 배경, 비활성
val DarkGray = Color(0xFF333333)           // 진한 텍스트
val White = Color(0xFFFFFFFF)
val Black = Color(0xFF000000)

// 💰 가격 관련 특별 컬러
val PriceIncrease = SecondaryRed           // 가격 상승 🔺
val PriceDecrease = SecondaryBlue          // 가격 하락 🔻
val PriceTarget = SecondaryGreen           // 목표가격 달성 🎯
val PriceBest = Color(0xFFFF9800)          // 역대 최저가 ⭐

// Light 테마
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

// Dark 테마 (추후 지원)
private val DarkColorScheme = darkColorScheme(
    primary = PrimaryOrange,
    onPrimary = White,
    primaryContainer = PrimaryOrangeDark,
    onPrimaryContainer = White,
    
    secondary = SecondaryBlue,
    onSecondary = White,
    secondaryContainer = Color(0xFF1565C0),
    onSecondaryContainer = White,
    
    background = Color(0xFF121212),
    onBackground = White,
    surface = Color(0xFF1E1E1E),
    onSurface = White
)

@Composable
fun InsightDealTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false, // Dynamic color는 비활성 (브랜드 일관성)
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        // 현재는 Light 테마만 지원
        // darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}

// 🎯 확장 컬러 속성들 (Compose에서 쉽게 사용)
@Composable
fun getPriceColor(isIncrease: Boolean): Color {
    return if (isIncrease) PriceIncrease else PriceDecrease
}

@Composable
fun getTargetAchievedColor(): Color = PriceTarget

@Composable
fun getBestPriceColor(): Color = PriceBest
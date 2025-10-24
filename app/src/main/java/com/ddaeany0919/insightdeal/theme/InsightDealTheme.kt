package com.ddaeany0919.insightdeal.theme

import android.content.Context
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import java.util.*

/**
 * 🌙 InsightDeal 고급 테마 시스템
 * 
 * AUTO 모드: 19시~07시 자동 다크 전환
 * 직접 선택: LIGHT, DARK, AMOLED, 컬러 테마 4종
 */
enum class ThemeMode {
    AUTO,      // 자동 (시간 기반)
    LIGHT,     // 라이트 모드
    DARK,      // 다크 모드  
    AMOLED,    // AMOLED 블랙
    ORANGE,    // 오렌지 테마
    BLUE,      // 블루 테마
    GREEN,     // 그린 테마
    PURPLE     // 퍼플 테마
}

/**
 * 🔧 테마 설정 저장/로드
 */
fun saveThemeMode(context: Context, themeMode: ThemeMode) {
    val prefs = context.getSharedPreferences("insightdeal_settings", Context.MODE_PRIVATE)
    prefs.edit().putString("theme_mode", themeMode.name).apply()
}

fun loadThemeMode(context: Context): ThemeMode {
    val prefs = context.getSharedPreferences("insightdeal_settings", Context.MODE_PRIVATE)
    val modeName = prefs.getString("theme_mode", ThemeMode.AUTO.name) ?: ThemeMode.AUTO.name
    return try {
        ThemeMode.valueOf(modeName)
    } catch (e: IllegalArgumentException) {
        ThemeMode.AUTO
    }
}

/**
 * ⏰ AUTO 모드 다크 활성화 조건 (19시~07시)
 */
@Composable
fun isAutoDarkActive(themeMode: ThemeMode): Boolean {
    if (themeMode != ThemeMode.AUTO) return false
    
    val hour = remember {
        Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
    }
    
    // 저녁 19시부터 다음날 오전 7시까지 다크모드
    return hour >= 19 || hour < 7
}

/**
 * 🌙 다크모드 활성화 여부 판단
 */
@Composable
fun isDarkModeActive(themeMode: ThemeMode): Boolean {
    return when {
        isAutoDarkActive(themeMode) -> true
        themeMode == ThemeMode.DARK || themeMode == ThemeMode.AMOLED -> true
        else -> false
    }
}

/**
 * 🎨 InsightDeal 메인 테마 컴포저블
 */
@Composable
fun InsightDealTheme(
    themeMode: ThemeMode = ThemeMode.AUTO,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val currentTheme by remember(themeMode) {
        mutableStateOf(themeMode)
    }
    
    val isDark = isDarkModeActive(currentTheme)
    
    val colorScheme = when (currentTheme) {
        ThemeMode.ORANGE -> if (isDark) OrangeDarkColors else OrangeLightColors
        ThemeMode.BLUE -> if (isDark) BlueDarkColors else BlueLightColors
        ThemeMode.GREEN -> if (isDark) GreenDarkColors else GreenLightColors
        ThemeMode.PURPLE -> if (isDark) PurpleDarkColors else PurpleLightColors
        ThemeMode.AMOLED -> AmoledColors
        ThemeMode.DARK -> DarkColors
        ThemeMode.LIGHT -> LightColors
        ThemeMode.AUTO -> if (isDark) DarkColors else LightColors
    }
    
    MaterialTheme(
        colorScheme = colorScheme,
        typography = InsightDealTypography,
        shapes = InsightDealShapes,
        content = content
    )
}

/**
 * 🎨 색상 팔레트 정의
 */

// 기본 라이트 테마
val LightColors = lightColorScheme(
    primary = Color(0xFFFF6B35),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFFFE5DB),
    onPrimaryContainer = Color(0xFF5D1A00),
    secondary = Color(0xFF77574C),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFFFDBCF),
    onSecondaryContainer = Color(0xFF2C160D),
    tertiary = Color(0xFF6B5E2F),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFF4E2A8),
    onTertiaryContainer = Color(0xFF221B00),
    error = Color(0xFFBA1A1A),
    errorContainer = Color(0xFFFFDAD6),
    onError = Color.White,
    onErrorContainer = Color(0xFF410002),
    background = Color(0xFFFFFBFF),
    onBackground = Color(0xFF201A18),
    surface = Color(0xFFFFFBFF),
    onSurface = Color(0xFF201A18),
    surfaceVariant = Color(0xFFF5DDD7),
    onSurfaceVariant = Color(0xFF53433F),
    outline = Color(0xFF85746E),
    inverseOnSurface = Color(0xFFFBEEE9),
    inverseSurface = Color(0xFF362F2C),
    inversePrimary = Color(0xFFFFB59D)
)

// 기본 다크 테마
val DarkColors = darkColorScheme(
    primary = Color(0xFFFFB59D),
    onPrimary = Color(0xFF5D1A00),
    primaryContainer = Color(0xFF7F2C00),
    onPrimaryContainer = Color(0xFFFFE5DB),
    secondary = Color(0xFFE7BDB0),
    onSecondary = Color(0xFF442B22),
    secondaryContainer = Color(0xFF5D4037),
    onSecondaryContainer = Color(0xFFFFDBCF),
    tertiary = Color(0xFFD7C68E),
    onTertiary = Color(0xFF3B2F05),
    tertiaryContainer = Color(0xFF52461A),
    onTertiaryContainer = Color(0xFFF4E2A8),
    error = Color(0xFFFFB4AB),
    errorContainer = Color(0xFF93000A),
    onError = Color(0xFF690005),
    onErrorContainer = Color(0xFFFFDAD6),
    background = Color(0xFF1A1110),
    onBackground = Color(0xFFF0DDD7),
    surface = Color(0xFF1A1110),
    onSurface = Color(0xFFF0DDD7),
    surfaceVariant = Color(0xFF53433F),
    onSurfaceVariant = Color(0xFFD8C2BB),
    outline = Color(0xFFA08D87),
    inverseOnSurface = Color(0xFF1A1110),
    inverseSurface = Color(0xFFF0DDD7),
    inversePrimary = Color(0xFFFF6B35)
)

// AMOLED 블랙 테마
val AmoledColors = darkColorScheme(
    primary = Color(0xFFFF8A50),
    onPrimary = Color.White,
    primaryContainer = Color(0xFF7F2C00),
    onPrimaryContainer = Color(0xFFFFE5DB),
    secondary = Color(0xFFFFAB91),
    onSecondary = Color.Black,
    secondaryContainer = Color(0xFF5D4037),
    onSecondaryContainer = Color(0xFFFFCCBC),
    background = Color.Black,      // 완전 검정
    onBackground = Color.White,
    surface = Color.Black,         // 완전 검정
    onSurface = Color.White,
    surfaceVariant = Color(0xFF1A1A1A),
    onSurfaceVariant = Color(0xFFE0E0E0),
    outline = Color(0xFF616161)
)

// 🧡 오렌지 테마
val OrangeLightColors = lightColorScheme(
    primary = Color(0xFFFF9800),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFFFE0B2),
    background = Color(0xFFFFF8E1),
    surface = Color(0xFFFFFBFF)
)

val OrangeDarkColors = darkColorScheme(
    primary = Color(0xFFFFB74D),
    onPrimary = Color.Black,
    primaryContainer = Color(0xFFE65100),
    background = Color(0xFF1A1110),
    surface = Color(0xFF201A18)
)

// 💙 블루 테마
val BlueLightColors = lightColorScheme(
    primary = Color(0xFF2196F3),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFBBDEFB),
    background = Color(0xFFE3F2FD),
    surface = Color(0xFFFFFBFF)
)

val BlueDarkColors = darkColorScheme(
    primary = Color(0xFF64B5F6),
    onPrimary = Color.Black,
    primaryContainer = Color(0xFF1976D2),
    background = Color(0xFF0D1B2A),
    surface = Color(0xFF1A1110)
)

// 💚 그린 테마
val GreenLightColors = lightColorScheme(
    primary = Color(0xFF4CAF50),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFC8E6C9),
    background = Color(0xFFE8F5E8),
    surface = Color(0xFFFFFBFF)
)

val GreenDarkColors = darkColorScheme(
    primary = Color(0xFF81C784),
    onPrimary = Color.Black,
    primaryContainer = Color(0xFF2E7D32),
    background = Color(0xFF0F1B0F),
    surface = Color(0xFF1A1110)
)

// 💜 퍼플 테마
val PurpleLightColors = lightColorScheme(
    primary = Color(0xFF9C27B0),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE1BEE7),
    background = Color(0xFFF3E5F5),
    surface = Color(0xFFFFFBFF)
)

val PurpleDarkColors = darkColorScheme(
    primary = Color(0xFFBA68C8),
    onPrimary = Color.Black,
    primaryContainer = Color(0xFF7B1FA2),
    background = Color(0xFF1A0F1B),
    surface = Color(0xFF1A1110)
)
package com.ddaeany0919.insightdeal

import android.content.Context
import android.content.SharedPreferences
import android.content.res.Configuration
import android.os.Build
import android.util.Log
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.*

/**
 * 🎨 지능형 테마 관리자
 * 
 * 다크모드, AMOLED 모드, 4가지 컬러 테마를 통합 관리
 */
class ThemeManager private constructor(private val context: Context) {
    
    companion object {
        private const val TAG = "ThemeManager"
        private const val PREFS_NAME = "theme_prefs"
        private const val KEY_THEME_MODE = "theme_mode"
        private const val KEY_COLOR_SCHEME = "color_scheme"
        private const val KEY_AMOLED_MODE = "amoled_mode"
        private const val KEY_AUTO_THEME_TIME = "auto_theme_time"
        
        @Volatile
        private var INSTANCE: ThemeManager? = null
        
        fun getInstance(context: Context): ThemeManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: ThemeManager(context.applicationContext).also { INSTANCE = it }
            }
        }
    }
    
    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    
    // 테마 설정 상태
    private val _themeMode = MutableStateFlow(loadThemeMode())
    val themeMode: StateFlow<ThemeMode> = _themeMode.asStateFlow()
    
    private val _colorScheme = MutableStateFlow(loadColorScheme())
    val colorScheme: StateFlow<AppColorScheme> = _colorScheme.asStateFlow()
    
    private val _amoledMode = MutableStateFlow(loadAmoledMode())
    val amoledMode: StateFlow<Boolean> = _amoledMode.asStateFlow()
    
    init {
        Log.d(TAG, "🎨 테마 관리자 초기화: ${_themeMode.value.name}, ${_colorScheme.value.name}, AMOLED: ${_amoledMode.value}")
    }
    
    /**
     * 🌙 테마 모드 변경
     */
    fun setThemeMode(mode: ThemeMode) {
        _themeMode.value = mode
        saveThemeMode(mode)
        Log.d(TAG, "🌍 테마 모드 변경: ${mode.name}")
    }
    
    /**
     * 🎨 컬러 스킴 변경
     */
    fun setColorScheme(scheme: AppColorScheme) {
        _colorScheme.value = scheme
        saveColorScheme(scheme)
        Log.d(TAG, "🎨 컬러 스킴 변경: ${scheme.name}")
    }
    
    /**
     * 🖤 AMOLED 모드 전환
     */
    fun setAmoledMode(enabled: Boolean) {
        _amoledMode.value = enabled
        saveAmoledMode(enabled)
        Log.d(TAG, "🖤 AMOLED 모드: ${if (enabled) "ON" else "OFF"}")
    }
    
    /**
     * ⏰ 시간 기반 자동 다크모드 확인
     */
    fun shouldUseDarkTheme(systemInDarkTheme: Boolean): Boolean {
        return when (_themeMode.value) {
            ThemeMode.LIGHT -> false
            ThemeMode.DARK -> true
            ThemeMode.AMOLED -> true // AMOLED는 항상 다크
            ThemeMode.SYSTEM -> systemInDarkTheme
            ThemeMode.AUTO_TIME -> isNightTime()
        }
    }
    
    /**
     * 🖤 AMOLED 테마인지 확인 (수정됨 - systemInDarkTheme 매개변수 추가)
     */
    fun shouldUseAmoledTheme(systemInDarkTheme: Boolean = false): Boolean {
        return _themeMode.value == ThemeMode.AMOLED || 
               (_amoledMode.value && shouldUseDarkTheme(systemInDarkTheme))
    }
    
    /**
     * 🌃 시스템 다크모드 확인 (로컬 사용)
     */
    private fun isSystemInDarkThemeLocal(): Boolean {
        return (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
    }
    
    private fun isNightTime(): Boolean {
        val currentHour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        return currentHour >= 19 || currentHour <= 7 // 오후 7시 ~ 오전 7시
    }
    
    /**
     * 🎨 현재 컬러 스킴 가져오기
     */
    fun getCurrentColorScheme(darkTheme: Boolean): ColorScheme {
        val baseScheme = _colorScheme.value
        val isDark = darkTheme
        val isAmoled = shouldUseAmoledTheme(darkTheme)
        
        return when {
            isAmoled -> createAmoledColorScheme(baseScheme)
            isDark -> createDarkColorScheme(baseScheme)
            else -> createLightColorScheme(baseScheme)
        }
    }
    
    /**
     * 🌅 라이트 컬러 스킴 생성 (리소스 기반)
     */
    private fun createLightColorScheme(scheme: AppColorScheme): ColorScheme {
        return when (scheme) {
            AppColorScheme.ORANGE_CLASSIC -> lightColorScheme(
                primary = Color(0xFFFF6B35), // light_orange_primary
                onPrimary = Color(0xFFFFFFFF), // light_orange_onPrimary
                primaryContainer = Color(0xFFFFE5DB), // light_orange_primaryContainer
                onPrimaryContainer = Color(0xFF5D1A00), // light_orange_onPrimaryContainer
                surface = Color(0xFFFFFBFF), // light_orange_surface
                onSurface = Color(0xFF201A17), // light_orange_onSurface
                background = Color(0xFFFFFBFF), // light_orange_background
                onBackground = Color(0xFF201A17) // light_orange_onBackground
            )
            AppColorScheme.BLUE_MODERN -> lightColorScheme(
                primary = Color(0xFF2196F3), // light_blue_primary
                onPrimary = Color(0xFFFFFFFF), // light_blue_onPrimary
                primaryContainer = Color(0xFFD1E4FF), // light_blue_primaryContainer
                onPrimaryContainer = Color(0xFF001D36), // light_blue_onPrimaryContainer
                surface = Color(0xFFF8FEFF), // light_blue_surface
                onSurface = Color(0xFF191C20), // light_blue_onSurface
                background = Color(0xFFF8FEFF), // light_blue_background
                onBackground = Color(0xFF191C20) // light_blue_onBackground
            )
            AppColorScheme.GREEN_NATURAL -> lightColorScheme(
                primary = Color(0xFF4CAF50), // light_green_primary
                onPrimary = Color(0xFFFFFFFF), // light_green_onPrimary
                primaryContainer = Color(0xFFA8F5A8), // light_green_primaryContainer
                onPrimaryContainer = Color(0xFF002204), // light_green_onPrimaryContainer
                surface = Color(0xFFF6FFF6), // light_green_surface
                onSurface = Color(0xFF181D18), // light_green_onSurface
                background = Color(0xFFF6FFF6), // light_green_background
                onBackground = Color(0xFF181D18) // light_green_onBackground
            )
            AppColorScheme.PURPLE_LUXURY -> lightColorScheme(
                primary = Color(0xFF9C27B0), // light_purple_primary
                onPrimary = Color(0xFFFFFFFF), // light_purple_onPrimary
                primaryContainer = Color(0xFFF3DAFF), // light_purple_primaryContainer
                onPrimaryContainer = Color(0xFF36003C), // light_purple_onPrimaryContainer
                surface = Color(0xFFFEF7FF), // light_purple_surface
                onSurface = Color(0xFF1D1A20), // light_purple_onSurface
                background = Color(0xFFFEF7FF), // light_purple_background
                onBackground = Color(0xFF1D1A20) // light_purple_onBackground
            )
        }
    }
    
    /**
     * 🌑 다크 컬러 스킴 생성 (리소스 기반)
     */
    private fun createDarkColorScheme(scheme: AppColorScheme): ColorScheme {
        return when (scheme) {
            AppColorScheme.ORANGE_CLASSIC -> darkColorScheme(
                primary = Color(0xFFFFB59D), // dark_orange_primary
                onPrimary = Color(0xFF5D1A00), // dark_orange_onPrimary
                primaryContainer = Color(0xFF7F2C00), // dark_orange_primaryContainer
                onPrimaryContainer = Color(0xFFFFE5DB), // dark_orange_onPrimaryContainer
                surface = Color(0xFF1A1110), // dark_orange_surface
                onSurface = Color(0xFFF0E0DC), // dark_orange_onSurface
                background = Color(0xFF1A1110), // dark_orange_background
                onBackground = Color(0xFFF0E0DC) // dark_orange_onBackground
            )
            AppColorScheme.BLUE_MODERN -> darkColorScheme(
                primary = Color(0xFF9CCAFF), // dark_blue_primary
                onPrimary = Color(0xFF003258), // dark_blue_onPrimary
                primaryContainer = Color(0xFF00497D), // dark_blue_primaryContainer
                onPrimaryContainer = Color(0xFFD1E4FF), // dark_blue_onPrimaryContainer
                surface = Color(0xFF10131A), // dark_blue_surface
                onSurface = Color(0xFFE1E2E8), // dark_blue_onSurface
                background = Color(0xFF10131A), // dark_blue_background
                onBackground = Color(0xFFE1E2E8) // dark_blue_onBackground
            )
            AppColorScheme.GREEN_NATURAL -> darkColorScheme(
                primary = Color(0xFF8BD88B), // dark_green_primary
                onPrimary = Color(0xFF00390A), // dark_green_onPrimary
                primaryContainer = Color(0xFF005314), // dark_green_primaryContainer
                onPrimaryContainer = Color(0xFFA8F5A8), // dark_green_onPrimaryContainer
                surface = Color(0xFF0F140F), // dark_green_surface
                onSurface = Color(0xFFE0E4E0), // dark_green_onSurface
                background = Color(0xFF0F140F), // dark_green_background
                onBackground = Color(0xFFE0E4E0) // dark_green_onBackground
            )
            AppColorScheme.PURPLE_LUXURY -> darkColorScheme(
                primary = Color(0xFFD6BBFF), // dark_purple_primary
                onPrimary = Color(0xFF4F1A5B), // dark_purple_onPrimary
                primaryContainer = Color(0xFF673174), // dark_purple_primaryContainer
                onPrimaryContainer = Color(0xFFF3DAFF), // dark_purple_onPrimaryContainer
                surface = Color(0xFF141218), // dark_purple_surface
                onSurface = Color(0xFFE5E1E6), // dark_purple_onSurface
                background = Color(0xFF141218), // dark_purple_background
                onBackground = Color(0xFFE5E1E6) // dark_purple_onBackground
            )
        }
    }
    
    /**
     * 🖤 AMOLED 완전 검은색 스킴 생성 (리소스 기반)
     */
    private fun createAmoledColorScheme(scheme: AppColorScheme): ColorScheme {
        return when (scheme) {
            AppColorScheme.ORANGE_CLASSIC -> darkColorScheme(
                primary = Color(0xFFFFB59D), // amoled_orange_primary
                onPrimary = Color(0xFF5D1A00), // amoled_orange_onPrimary
                primaryContainer = Color(0xFF7F2C00), // amoled_orange_primaryContainer
                onPrimaryContainer = Color(0xFFFFE5DB), // amoled_orange_onPrimaryContainer
                surface = Color(0xFF000000), // amoled_orange_surface
                onSurface = Color(0xFFF0E0DC), // amoled_orange_onSurface
                background = Color(0xFF000000), // amoled_orange_background
                onBackground = Color(0xFFF0E0DC) // amoled_orange_onBackground
            )
            AppColorScheme.BLUE_MODERN -> darkColorScheme(
                primary = Color(0xFF9CCAFF), // amoled_blue_primary
                onPrimary = Color(0xFF003258), // amoled_blue_onPrimary
                primaryContainer = Color(0xFF00497D), // amoled_blue_primaryContainer
                onPrimaryContainer = Color(0xFFD1E4FF), // amoled_blue_onPrimaryContainer
                surface = Color(0xFF000000), // amoled_blue_surface
                onSurface = Color(0xFFE1E2E8), // amoled_blue_onSurface
                background = Color(0xFF000000), // amoled_blue_background
                onBackground = Color(0xFFE1E2E8) // amoled_blue_onBackground
            )
            AppColorScheme.GREEN_NATURAL -> darkColorScheme(
                primary = Color(0xFF8BD88B), // amoled_green_primary
                onPrimary = Color(0xFF00390A), // amoled_green_onPrimary
                primaryContainer = Color(0xFF005314), // amoled_green_primaryContainer
                onPrimaryContainer = Color(0xFFA8F5A8), // amoled_green_onPrimaryContainer
                surface = Color(0xFF000000), // amoled_green_surface
                onSurface = Color(0xFFE0E4E0), // amoled_green_onSurface
                background = Color(0xFF000000), // amoled_green_background
                onBackground = Color(0xFFE0E4E0) // amoled_green_onBackground
            )
            AppColorScheme.PURPLE_LUXURY -> darkColorScheme(
                primary = Color(0xFFD6BBFF), // amoled_purple_primary
                onPrimary = Color(0xFF4F1A5B), // amoled_purple_onPrimary
                primaryContainer = Color(0xFF673174), // amoled_purple_primaryContainer
                onPrimaryContainer = Color(0xFFF3DAFF), // amoled_purple_onPrimaryContainer
                surface = Color(0xFF000000), // amoled_purple_surface
                onSurface = Color(0xFFE5E1E6), // amoled_purple_onSurface
                background = Color(0xFF000000), // amoled_purple_background
                onBackground = Color(0xFFE5E1E6) // amoled_purple_onBackground
            )
        }
    }
    
    // 저장/로드 메소드
    private fun saveThemeMode(mode: ThemeMode) {
        prefs.edit().putString(KEY_THEME_MODE, mode.name).apply()
    }
    
    private fun loadThemeMode(): ThemeMode {
        val modeName = prefs.getString(KEY_THEME_MODE, ThemeMode.SYSTEM.name) ?: ThemeMode.SYSTEM.name
        return try {
            ThemeMode.valueOf(modeName)
        } catch (e: Exception) {
            ThemeMode.SYSTEM
        }
    }
    
    private fun saveColorScheme(scheme: AppColorScheme) {
        prefs.edit().putString(KEY_COLOR_SCHEME, scheme.name).apply()
    }
    
    private fun loadColorScheme(): AppColorScheme {
        val schemeName = prefs.getString(KEY_COLOR_SCHEME, AppColorScheme.ORANGE_CLASSIC.name) ?: AppColorScheme.ORANGE_CLASSIC.name
        return try {
            AppColorScheme.valueOf(schemeName)
        } catch (e: Exception) {
            AppColorScheme.ORANGE_CLASSIC
        }
    }
    
    private fun saveAmoledMode(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_AMOLED_MODE, enabled).apply()
    }
    
    private fun loadAmoledMode(): Boolean {
        return prefs.getBoolean(KEY_AMOLED_MODE, false)
    }
}

/**
 * 🌙 테마 모드 옵션 (AMOLED 추가)
 */
enum class ThemeMode(val displayName: String, val description: String) {
    LIGHT("라이트 모드", "밝은 배경으로 낮에 최적화"),
    DARK("다크 모드", "어두운 배경으로 눈의 피로 감소"),
    AMOLED("AMOLED 블랙", "완전한 검은색으로 배터리 절약"),
    SYSTEM("시스템 따라가기", "시스템 설정에 맞춰 자동 전환"),
    AUTO_TIME("시간 자동 전환", "오후 7시~오전 7시 자동 다크모드")
}

/**
 * 🎨 컬러 스킴 옵션
 */
enum class AppColorScheme(
    val displayName: String,
    val primaryColor: Color,
    val description: String
) {
    ORANGE_CLASSIC(
        "오렌지 클래식",
        Color(0xFFFF6B35),
        "따뜻하고 에너지 넘치는 InsightDeal 기본 테마"
    ),
    BLUE_MODERN(
        "블루 모던",
        Color(0xFF2196F3),
        "깔끔하고 전문적인 비즈니스 테마"
    ),
    GREEN_NATURAL(
        "그린 내추럴",
        Color(0xFF4CAF50),
        "안정적이고 친환경적인 내추럴 테마"
    ),
    PURPLE_LUXURY(
        "퍼플 럭셔리",
        Color(0xFF9C27B0),
        "럭셔리하고 세련된 프리미엄 테마"
    )
}

/**
 * 🎨 전체 앱 테마 제공자
 */
@Composable
fun InsightDealTheme(
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val themeManager = remember { ThemeManager.getInstance(context) }
    
    val themeMode by themeManager.themeMode.collectAsState()
    val colorScheme by themeManager.colorScheme.collectAsState()
    val amoledMode by themeManager.amoledMode.collectAsState()
    
    val systemInDarkTheme = isSystemInDarkTheme()
    val useDarkTheme = themeManager.shouldUseDarkTheme(systemInDarkTheme)
    
    val appColorScheme = themeManager.getCurrentColorScheme(useDarkTheme)
    
    MaterialTheme(
        colorScheme = appColorScheme,
        typography = InsightDealTypography,
        content = content
    )
}

/**
 * 🕰️ 타이포그래피 시스템
 */
val InsightDealTypography = Typography(
    displayLarge = androidx.compose.ui.text.TextStyle(
        fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
        fontSize = 32.sp
    ),
    headlineMedium = androidx.compose.ui.text.TextStyle(
        fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
        fontSize = 20.sp
    ),
    titleMedium = androidx.compose.ui.text.TextStyle(
        fontWeight = androidx.compose.ui.text.font.FontWeight.Medium,
        fontSize = 16.sp
    ),
    bodyMedium = androidx.compose.ui.text.TextStyle(
        fontWeight = androidx.compose.ui.text.font.FontWeight.Normal,
        fontSize = 14.sp
    ),
    labelSmall = androidx.compose.ui.text.TextStyle(
        fontWeight = androidx.compose.ui.text.font.FontWeight.Medium,
        fontSize = 12.sp
    )
)
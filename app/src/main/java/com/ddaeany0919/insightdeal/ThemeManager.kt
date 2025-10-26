package com.ddaeany0919.insightdeal

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.compose.material3.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.*

/**
 * 🎨 지능형 테마 관리자
 */
class ThemeManager private constructor(private val context: Context) {

    companion object {
        private const val TAG = "ThemeManager"
        private const val PREFS_NAME = "theme_prefs"
        private const val KEY_THEME_MODE = "theme_mode"
        private const val KEY_COLOR_SCHEME = "color_scheme"
        private const val KEY_AMOLED_MODE = "amoled_mode"

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
        Log.d(TAG, "🎨 테마 관리자 초기화: theme=${_themeMode.value}, color=${_colorScheme.value}, amoled=${_amoledMode.value}")
    }

    // 설정 변경
    fun setThemeMode(mode: ThemeMode) {
        Log.d(TAG, "🌍 테마 모드 변경: ${_themeMode.value} → $mode")
        _themeMode.value = mode
        prefs.edit().putString(KEY_THEME_MODE, mode.name).apply()
    }

    fun setColorScheme(scheme: AppColorScheme) {
        Log.d(TAG, "🎨 컬러 스킴 변경: ${_colorScheme.value} → $scheme")
        _colorScheme.value = scheme
        prefs.edit().putString(KEY_COLOR_SCHEME, scheme.name).apply()
    }

    fun setAmoledMode(enabled: Boolean) {
        Log.d(TAG, "🖤 AMOLED 모드: ${_amoledMode.value} → $enabled")
        _amoledMode.value = enabled
        prefs.edit().putBoolean(KEY_AMOLED_MODE, enabled).apply()
    }

    // 판단 로직 (순수 함수)
    fun shouldUseDarkTheme(systemInDarkTheme: Boolean): Boolean {
        val mode = _themeMode.value
        val result = when (mode) {
            ThemeMode.LIGHT -> false
            ThemeMode.DARK -> true
            ThemeMode.AMOLED -> true
            ThemeMode.SYSTEM -> systemInDarkTheme
            ThemeMode.AUTO_TIME -> isNightTime()
        }
        Log.d(TAG, "shouldUseDarkTheme: mode=$mode, system=$systemInDarkTheme → $result")
        return result
    }

    fun shouldUseAmoledTheme(systemInDarkTheme: Boolean): Boolean {
        val mode = _themeMode.value
        val enabled = _amoledMode.value
        val dark = shouldUseDarkTheme(systemInDarkTheme)
        val result = mode == ThemeMode.AMOLED || (enabled && dark)
        Log.d(TAG, "shouldUseAmoledTheme: mode=$mode, enabled=$enabled, dark=$dark → $result")
        return result
    }

    private fun isNightTime(): Boolean {
        val currentHour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        return currentHour >= 19 || currentHour <= 7
    }

    // 공개 컬러 스킴 생성자
    fun getLightColorScheme(scheme: AppColorScheme): ColorScheme {
        Log.d(TAG, "getLightColorScheme: $scheme")
        return when (scheme) {
            AppColorScheme.ORANGE_CLASSIC -> lightColorScheme(
                primary = Color(0xFFFF6B35), onPrimary = Color(0xFFFFFFFF),
                primaryContainer = Color(0xFFFFE5DB), onPrimaryContainer = Color(0xFF5D1A00),
                surface = Color(0xFFFFFBFF), onSurface = Color(0xFF201A17),
                background = Color(0xFFFFFBFF), onBackground = Color(0xFF201A17)
            )
            AppColorScheme.BLUE_MODERN -> lightColorScheme(
                primary = Color(0xFF2196F3), onPrimary = Color(0xFFFFFFFF),
                primaryContainer = Color(0xFFD1E4FF), onPrimaryContainer = Color(0xFF001D36),
                surface = Color(0xFFF8FEFF), onSurface = Color(0xFF191C20),
                background = Color(0xFFF8FEFF), onBackground = Color(0xFF191C20)
            )
            AppColorScheme.GREEN_NATURAL -> lightColorScheme(
                primary = Color(0xFF4CAF50), onPrimary = Color(0xFFFFFFFF),
                primaryContainer = Color(0xFFA8F5A8), onPrimaryContainer = Color(0xFF002204),
                surface = Color(0xFFF6FFF6), onSurface = Color(0xFF181D18),
                background = Color(0xFFF6FFF6), onBackground = Color(0xFF181D18)
            )
            AppColorScheme.PURPLE_LUXURY -> lightColorScheme(
                primary = Color(0xFF9C27B0), onPrimary = Color(0xFFFFFFFF),
                primaryContainer = Color(0xFFF3DAFF), onPrimaryContainer = Color(0xFF36003C),
                surface = Color(0xFFFEF7FF), onSurface = Color(0xFF1D1A20),
                background = Color(0xFFFEF7FF), onBackground = Color(0xFF1D1A20)
            )
        }
    }

    fun getDarkColorScheme(scheme: AppColorScheme): ColorScheme {
        Log.d(TAG, "getDarkColorScheme: $scheme")
        return when (scheme) {
            AppColorScheme.ORANGE_CLASSIC -> darkColorScheme(
                primary = Color(0xFFFFB59D), onPrimary = Color(0xFF5D1A00),
                primaryContainer = Color(0xFF7F2C00), onPrimaryContainer = Color(0xFFFFE5DB),
                surface = Color(0xFF1A1110), onSurface = Color(0xFFF0E0DC),
                background = Color(0xFF1A1110), onBackground = Color(0xFFF0E0DC)
            )
            AppColorScheme.BLUE_MODERN -> darkColorScheme(
                primary = Color(0xFF9CCAFF), onPrimary = Color(0xFF003258),
                primaryContainer = Color(0xFF00497D), onPrimaryContainer = Color(0xFFD1E4FF),
                surface = Color(0xFF10131A), onSurface = Color(0xFFE1E2E8),
                background = Color(0xFF10131A), onBackground = Color(0xFFE1E2E8)
            )
            AppColorScheme.GREEN_NATURAL -> darkColorScheme(
                primary = Color(0xFF8BD88B), onPrimary = Color(0xFF00390A),
                primaryContainer = Color(0xFF005314), onPrimaryContainer = Color(0xFFA8F5A8),
                surface = Color(0xFF0F140F), onSurface = Color(0xFFE0E4E0),
                background = Color(0xFF0F140F), onBackground = Color(0xFFE0E4E0)
            )
            AppColorScheme.PURPLE_LUXURY -> darkColorScheme(
                primary = Color(0xFFD6BBFF), onPrimary = Color(0xFF4F1A5B),
                primaryContainer = Color(0xFF673174), onPrimaryContainer = Color(0xFFF3DAFF),
                surface = Color(0xFF141218), onSurface = Color(0xFFE5E1E6),
                background = Color(0xFF141218), onBackground = Color(0xFFE5E1E6)
            )
        }
    }

    fun getAmoledColorScheme(scheme: AppColorScheme): ColorScheme {
        Log.d(TAG, "getAmoledColorScheme: $scheme")
        return when (scheme) {
            AppColorScheme.ORANGE_CLASSIC -> darkColorScheme(
                primary = Color(0xFFFFB59D), onPrimary = Color(0xFF5D1A00),
                primaryContainer = Color(0xFF7F2C00), onPrimaryContainer = Color(0xFFFFE5DB),
                surface = Color(0xFF000000), onSurface = Color(0xFFF0E0DC),
                background = Color(0xFF000000), onBackground = Color(0xFFF0E0DC)
            )
            AppColorScheme.BLUE_MODERN -> darkColorScheme(
                primary = Color(0xFF9CCAFF), onPrimary = Color(0xFF003258),
                primaryContainer = Color(0xFF00497D), onPrimaryContainer = Color(0xFFD1E4FF),
                surface = Color(0xFF000000), onSurface = Color(0xFFE1E2E8),
                background = Color(0xFF000000), onBackground = Color(0xFFE1E2E8)
            )
            AppColorScheme.GREEN_NATURAL -> darkColorScheme(
                primary = Color(0xFF8BD88B), onPrimary = Color(0xFF00390A),
                primaryContainer = Color(0xFF005314), onPrimaryContainer = Color(0xFFA8F5A8),
                surface = Color(0xFF000000), onSurface = Color(0xFFE0E4E0),
                background = Color(0xFF000000), onBackground = Color(0xFFE0E4E0)
            )
            AppColorScheme.PURPLE_LUXURY -> darkColorScheme(
                primary = Color(0xFFD6BBFF), onPrimary = Color(0xFF4F1A5B),
                primaryContainer = Color(0xFF673174), onPrimaryContainer = Color(0xFFF3DAFF),
                surface = Color(0xFF000000), onSurface = Color(0xFFE5E1E6),
                background = Color(0xFF000000), onBackground = Color(0xFFE5E1E6)
            )
        }
    }

    // 저장/로드
    private fun loadThemeMode(): ThemeMode {
        val modeName = prefs.getString(KEY_THEME_MODE, ThemeMode.SYSTEM.name) ?: ThemeMode.SYSTEM.name
        return try {
            ThemeMode.valueOf(modeName)
        } catch (e: Exception) {
            ThemeMode.SYSTEM
        }
    }

    private fun loadColorScheme(): AppColorScheme {
        val schemeName = prefs.getString(KEY_COLOR_SCHEME, AppColorScheme.ORANGE_CLASSIC.name) ?: AppColorScheme.ORANGE_CLASSIC.name
        return try {
            AppColorScheme.valueOf(schemeName)
        } catch (e: Exception) {
            AppColorScheme.ORANGE_CLASSIC
        }
    }

    private fun loadAmoledMode(): Boolean {
        return prefs.getBoolean(KEY_AMOLED_MODE, false)
    }
}

/**
 * 🌙 테마 모드 옵션
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
 * 🕰️ 타이포그래피 시스템
 */
val InsightDealTypography = Typography(
    displayLarge = TextStyle(
        fontWeight = FontWeight.Bold,
        fontSize = 32.sp
    ),
    headlineMedium = TextStyle(
        fontWeight = FontWeight.Bold,
        fontSize = 20.sp
    ),
    titleMedium = TextStyle(
        fontWeight = FontWeight.Medium,
        fontSize = 16.sp
    ),
    bodyMedium = TextStyle(
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp
    ),
    labelSmall = TextStyle(
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp
    )
)
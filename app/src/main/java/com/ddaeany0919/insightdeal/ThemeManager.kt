package com.ddaeany0919.insightdeal

import android.content.Context
import android.content.SharedPreferences
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
 * 다크모드, AMOLED 모드, 4가지 컴러 테마를 통합 관리
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
        Log.d(TAG, "🎨 테마 관리자 초기화: ${_themeMode.value.name}, ${_colorScheme.value.name}")
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
     * 🎨 컴러 스킴 변경
     */
    fun setColorScheme(scheme: AppColorScheme) {
        _colorScheme.value = scheme
        saveColorScheme(scheme)
        Log.d(TAG, "🎨 컴러 스킴 변경: ${scheme.name}")
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
            ThemeMode.SYSTEM -> systemInDarkTheme
            ThemeMode.AUTO_TIME -> isNightTime()
        }
    }
    
    private fun isNightTime(): Boolean {
        val currentHour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        return currentHour >= 19 || currentHour <= 7 // 오후 7시 ~ 오전 7시
    }
    
    /**
     * 🎨 현재 컴러 스킴 가져오기
     */
    fun getCurrentColorScheme(darkTheme: Boolean): ColorScheme {
        val baseScheme = _colorScheme.value
        val isDark = darkTheme || _amoledMode.value
        
        return if (isDark) {
            if (_amoledMode.value) {
                createAmoledColorScheme(baseScheme)
            } else {
                createDarkColorScheme(baseScheme)
            }
        } else {
            createLightColorScheme(baseScheme)
        }
    }
    
    /**
     * 🌅 라이트 컴러 스킴 생성
     */
    private fun createLightColorScheme(scheme: AppColorScheme): ColorScheme {
        return when (scheme) {
            AppColorScheme.ORANGE_CLASSIC -> lightColorScheme(
                primary = Color(0xFFFF6B35),
                onPrimary = Color.White,
                primaryContainer = Color(0xFFFFE4DB),
                onPrimaryContainer = Color(0xFF331100),
                secondary = Color(0xFFFF8A50),
                surface = Color(0xFFFFFBFF),
                background = Color(0xFFFFFBFF)
            )
            AppColorScheme.BLUE_MODERN -> lightColorScheme(
                primary = Color(0xFF2196F3),
                onPrimary = Color.White,
                primaryContainer = Color(0xFFE3F2FD),
                onPrimaryContainer = Color(0xFF0D47A1),
                secondary = Color(0xFF42A5F5),
                surface = Color(0xFFFFFBFF),
                background = Color(0xFFFFFBFF)
            )
            AppColorScheme.GREEN_NATURAL -> lightColorScheme(
                primary = Color(0xFF4CAF50),
                onPrimary = Color.White,
                primaryContainer = Color(0xFFE8F5E8),
                onPrimaryContainer = Color(0xFF1B5E20),
                secondary = Color(0xFF66BB6A),
                surface = Color(0xFFFFFBFF),
                background = Color(0xFFFFFBFF)
            )
            AppColorScheme.PURPLE_LUXURY -> lightColorScheme(
                primary = Color(0xFF9C27B0),
                onPrimary = Color.White,
                primaryContainer = Color(0xFFF3E5F5),
                onPrimaryContainer = Color(0xFF4A148C),
                secondary = Color(0xFFBA68C8),
                surface = Color(0xFFFFFBFF),
                background = Color(0xFFFFFBFF)
            )
        }
    }
    
    /**
     * 🌑 다크 컴러 스킴 생성
     */
    private fun createDarkColorScheme(scheme: AppColorScheme): ColorScheme {
        return when (scheme) {
            AppColorScheme.ORANGE_CLASSIC -> darkColorScheme(
                primary = Color(0xFFFF8A50),
                onPrimary = Color(0xFF331100),
                primaryContainer = Color(0xFF663300),
                onPrimaryContainer = Color(0xFFFFE4DB),
                secondary = Color(0xFFFFAB91),
                surface = Color(0xFF1C1B1F),
                background = Color(0xFF1C1B1F)
            )
            AppColorScheme.BLUE_MODERN -> darkColorScheme(
                primary = Color(0xFF64B5F6),
                onPrimary = Color(0xFF0D47A1),
                primaryContainer = Color(0xFF1976D2),
                onPrimaryContainer = Color(0xFFE3F2FD),
                secondary = Color(0xFF90CAF9),
                surface = Color(0xFF1C1B1F),
                background = Color(0xFF1C1B1F)
            )
            AppColorScheme.GREEN_NATURAL -> darkColorScheme(
                primary = Color(0xFF81C784),
                onPrimary = Color(0xFF1B5E20),
                primaryContainer = Color(0xFF388E3C),
                onPrimaryContainer = Color(0xFFE8F5E8),
                secondary = Color(0xFFA5D6A7),
                surface = Color(0xFF1C1B1F),
                background = Color(0xFF1C1B1F)
            )
            AppColorScheme.PURPLE_LUXURY -> darkColorScheme(
                primary = Color(0xFFCE93D8),
                onPrimary = Color(0xFF4A148C),
                primaryContainer = Color(0xFF7B1FA2),
                onPrimaryContainer = Color(0xFFF3E5F5),
                secondary = Color(0xFFE1BEE7),
                surface = Color(0xFF1C1B1F),
                background = Color(0xFF1C1B1F)
            )
        }
    }
    
    /**
     * 🖤 AMOLED 완전 검은색 스킴 생성
     */
    private fun createAmoledColorScheme(scheme: AppColorScheme): ColorScheme {
        val darkScheme = createDarkColorScheme(scheme)
        
        return darkScheme.copy(
            surface = Color.Black,         // 완전 검은색
            background = Color.Black,      // 완전 검은색
            surfaceVariant = Color(0xFF1A1A1A),
            surfaceContainer = Color(0xFF0D0D0D)
        )
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
 * 🌙 테마 모드 옵션
 */
enum class ThemeMode(val displayName: String) {
    LIGHT("라이트 모드"),
    DARK("다크 모드"),
    SYSTEM("시스템 따라가기"),
    AUTO_TIME("시간 자동 전환")
}

/**
 * 🎨 컴러 스킴 옵션
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
package com.ddaeany0919.insightdeal

import android.content.Context
import android.content.SharedPreferences
import android.content.res.Configuration
import android.util.Log
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Calendar

/**
 * 🎨 지능형 테마 관리자 (컬러 스킴 전달 방식으로 수정 + 로그 강화)
 */
class ThemeManager private constructor(private val context: Context) {

    companion object {
        private const val TAG = "ThemeManager"
        private const val PREFS_NAME = "theme_prefs"
        private const val KEY_THEME_MODE = "theme_mode"
        private const val KEY_COLOR_SCHEME = "color_scheme"
        private const val KEY_AMOLED_MODE = "amoled_mode"

        @Volatile private var INSTANCE: ThemeManager? = null
        fun getInstance(context: Context): ThemeManager =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: ThemeManager(context.applicationContext).also { INSTANCE = it }
            }
    }

    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    // 상태
    private val _themeMode = MutableStateFlow(loadThemeMode())
    val themeMode: StateFlow<ThemeMode> = _themeMode.asStateFlow()

    private val _colorScheme = MutableStateFlow(loadColorScheme())
    val colorScheme: StateFlow<AppColorScheme> = _colorScheme.asStateFlow()

    private val _amoledMode = MutableStateFlow(loadAmoledMode())
    val amoledMode: StateFlow<Boolean> = _amoledMode.asStateFlow()

    init {
        Log.d(TAG, "init -> theme=${_themeMode.value}, color=${_colorScheme.value}, amoled=${_amoledMode.value}")
    }

    fun setThemeMode(mode: ThemeMode) {
        Log.d(TAG, "setThemeMode: $mode")
        _themeMode.value = mode
        prefs.edit().putString(KEY_THEME_MODE, mode.name).apply()
    }

    fun setColorScheme(scheme: AppColorScheme) {
        Log.d(TAG, "setColorScheme: $scheme")
        _colorScheme.value = scheme
        prefs.edit().putString(KEY_COLOR_SCHEME, scheme.name).apply()
    }

    fun setAmoledMode(enabled: Boolean) {
        Log.d(TAG, "setAmoledMode: $enabled")
        _amoledMode.value = enabled
        prefs.edit().putBoolean(KEY_AMOLED_MODE, enabled).apply()
    }

    fun shouldUseDarkTheme(systemInDarkTheme: Boolean): Boolean {
        val mode = _themeMode.value
        val isNight = isNightTime()
        val result = when (mode) {
            ThemeMode.LIGHT -> false
            ThemeMode.DARK -> true
            ThemeMode.AMOLED -> true
            ThemeMode.SYSTEM -> systemInDarkTheme
            ThemeMode.AUTO_TIME -> isNight
        }
        Log.d(TAG, "shouldUseDarkTheme: mode=$mode system=$systemInDarkTheme night=$isNight -> $result")
        return result
    }

    fun shouldUseAmoledTheme(systemInDarkTheme: Boolean): Boolean {
        val mode = _themeMode.value
        val enabled = _amoledMode.value
        val dark = shouldUseDarkTheme(systemInDarkTheme)
        val result = mode == ThemeMode.AMOLED || (enabled && dark)
        Log.d(TAG, "shouldUseAmoledTheme: mode=$mode enabled=$enabled dark=$dark -> $result")
        return result
    }

    private fun isNightTime(): Boolean {
        val h = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        return h >= 19 || h <= 7
    }

    // 공개 팔레트 getter (스킴 명시 전달)
    fun getLightColorScheme(s: AppColorScheme): ColorScheme = when (s) {
        AppColorScheme.ORANGE_CLASSIC -> lightColorScheme(
            primary = Color(0xFFFF6B35), onPrimary = Color.White,
            primaryContainer = Color(0xFFFFE5DB), onPrimaryContainer = Color(0xFF5D1A00),
            surface = Color(0xFFFFFBFF), onSurface = Color(0xFF201A17),
            background = Color(0xFFFFFBFF), onBackground = Color(0xFF201A17)
        )
        AppColorScheme.BLUE_MODERN -> lightColorScheme(
            primary = Color(0xFF2196F3), onPrimary = Color.White,
            primaryContainer = Color(0xFFD1E4FF), onPrimaryContainer = Color(0xFF001D36),
            surface = Color(0xFFF8FEFF), onSurface = Color(0xFF191C20),
            background = Color(0xFFF8FEFF), onBackground = Color(0xFF191C20)
        )
        AppColorScheme.GREEN_NATURAL -> lightColorScheme(
            primary = Color(0xFF4CAF50), onPrimary = Color.White,
            primaryContainer = Color(0xFFA8F5A8), onPrimaryContainer = Color(0xFF002204),
            surface = Color(0xFFF6FFF6), onSurface = Color(0xFF181D18),
            background = Color(0xFFF6FFF6), onBackground = Color(0xFF181D18)
        )
        AppColorScheme.PURPLE_LUXURY -> lightColorScheme(
            primary = Color(0xFF9C27B0), onPrimary = Color.White,
            primaryContainer = Color(0xFFF3DAFF), onPrimaryContainer = Color(0xFF36003C),
            surface = Color(0xFFFEF7FF), onSurface = Color(0xFF1D1A20),
            background = Color(0xFFFEF7FF), onBackground = Color(0xFF1D1A20)
        )
    }

    fun getDarkColorScheme(s: AppColorScheme): ColorScheme = when (s) {
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

    fun getAmoledColorScheme(s: AppColorScheme): ColorScheme = when (s) {
        AppColorScheme.ORANGE_CLASSIC -> darkColorScheme(
            primary = Color(0xFFFFB59D), onPrimary = Color(0xFF5D1A00),
            primaryContainer = Color(0xFF7F2C00), onPrimaryContainer = Color(0xFFFFE5DB),
            surface = Color.Black, onSurface = Color(0xFFF0E0DC),
            background = Color.Black, onBackground = Color(0xFFF0E0DC)
        )
        AppColorScheme.BLUE_MODERN -> darkColorScheme(
            primary = Color(0xFF9CCAFF), onPrimary = Color(0xFF003258),
            primaryContainer = Color(0xFF00497D), onPrimaryContainer = Color(0xFFD1E4FF),
            surface = Color.Black, onSurface = Color(0xFFE1E2E8),
            background = Color.Black, onBackground = Color(0xFFE1E2E8)
        )
        AppColorScheme.GREEN_NATURAL -> darkColorScheme(
            primary = Color(0xFF8BD88B), onPrimary = Color(0xFF00390A),
            primaryContainer = Color(0xFF005314), onPrimaryContainer = Color(0xFFA8F5A8),
            surface = Color.Black, onSurface = Color(0xFFE0E4E0),
            background = Color.Black, onBackground = Color(0xFFE0E4E0)
        )
        AppColorScheme.PURPLE_LUXURY -> darkColorScheme(
            primary = Color(0xFFD6BBFF), onPrimary = Color(0xFF4F1A5B),
            primaryContainer = Color(0xFF673174), onPrimaryContainer = Color(0xFFF3DAFF),
            surface = Color.Black, onSurface = Color(0xFFE5E1E6),
            background = Color.Black, onBackground = Color(0xFFE5E1E6)
        )
    }

    private fun loadThemeMode(): ThemeMode =
        prefs.getString(KEY_THEME_MODE, ThemeMode.SYSTEM.name)?.let {
            runCatching { ThemeMode.valueOf(it) }.getOrElse { ThemeMode.SYSTEM }
        } ?: ThemeMode.SYSTEM

    private fun loadColorScheme(): AppColorScheme =
        prefs.getString(KEY_COLOR_SCHEME, AppColorScheme.ORANGE_CLASSIC.name)?.let {
            runCatching { AppColorScheme.valueOf(it) }.getOrElse { AppColorScheme.ORANGE_CLASSIC }
        } ?: AppColorScheme.ORANGE_CLASSIC

    private fun loadAmoledMode(): Boolean = prefs.getBoolean(KEY_AMOLED_MODE, false)
}

enum class ThemeMode { LIGHT, DARK, AMOLED, SYSTEM, AUTO_TIME }

enum class AppColorScheme(val displayName: String) {
    ORANGE_CLASSIC("오렌지"), BLUE_MODERN("블루"), GREEN_NATURAL("그린"), PURPLE_LUXURY("퍼플")
}

package com.ddaeany0919.insightdeal.presentation.theme

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 🎨 테마 관리자 (Light / Dark / AMOLED / System)
 */
class ThemeManager private constructor(context: Context) {

    companion object {
        private const val TAG = "ThemeManager"
        private const val PREFS_NAME = "theme_prefs"
        private const val KEY_THEME_MODE = "theme_mode"
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

    private val _amoledMode = MutableStateFlow(loadAmoledMode())
    val amoledMode: StateFlow<Boolean> = _amoledMode.asStateFlow()

    init {
        Log.d(TAG, "🎨 테마 관리자 초기화: theme=${_themeMode.value}, amoled=${_amoledMode.value}")
    }

    // 설정 변경
    fun setThemeMode(mode: ThemeMode) {
        Log.d(TAG, "🌍 테마 모드 변경: ${_themeMode.value} → $mode")
        _themeMode.value = mode
        prefs.edit().putString(KEY_THEME_MODE, mode.name).apply()
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

    // 저장/로드
    private fun loadThemeMode(): ThemeMode {
        val modeName = prefs.getString(KEY_THEME_MODE, ThemeMode.SYSTEM.name) ?: ThemeMode.SYSTEM.name
        return try {
            ThemeMode.valueOf(modeName)
        } catch (_: Exception) {
            ThemeMode.SYSTEM
        }
    }

    private fun loadAmoledMode(): Boolean {
        return prefs.getBoolean(KEY_AMOLED_MODE, false)
    }
}

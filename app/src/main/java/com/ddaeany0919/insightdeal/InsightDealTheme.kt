package com.ddaeany0919.insightdeal

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import android.util.Log

/**
 * 🎨 InsightDeal 앱 최상위 테마 제공자
 *
 * 선택된 컬러 스킴을 명시적으로 적용하여 컴러 테마 변경을 즉시 반영
 */
@Composable
fun InsightDealTheme(
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val themeManager = remember { ThemeManager.getInstance(context) }

    // StateFlow 구독
    val themeMode by themeManager.themeMode.collectAsState()
    val colorPref by themeManager.colorScheme.collectAsState()
    val amoledMode by themeManager.amoledMode.collectAsState()

    // 시스템 다크모드 확인
    val systemDark = isSystemInDarkTheme()
    val useDark = themeManager.shouldUseDarkTheme(systemDark)
    val useAmoled = themeManager.shouldUseAmoledTheme(systemDark)

    // 선택된 컬러 스킴을 명시적으로 전달하여 팩에 적용
    val colorScheme = when {
        useAmoled -> themeManager.getAmoledColorScheme(colorPref)
        useDark -> themeManager.getDarkColorScheme(colorPref)
        else -> themeManager.getLightColorScheme(colorPref)
    }

    // 로그 출력
    Log.d("InsightDealTheme", "🎨 테마 적용: mode=$themeMode, color=$colorPref, amoled=$amoledMode, systemDark=$systemDark, useDark=$useDark, useAmoled=$useAmoled")

    MaterialTheme(
        colorScheme = colorScheme,
        typography = InsightDealTypography,
        content = content
    )
}
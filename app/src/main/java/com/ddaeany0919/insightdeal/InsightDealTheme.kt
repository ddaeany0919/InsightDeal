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
 * 앱 최상위 테마 적용 (선택된 컬러 스킴을 명시적으로 적용 + 로그)
 */
@Composable
fun InsightDealTheme(content: @Composable () -> Unit) {
    val context = LocalContext.current
    val tm = remember { ThemeManager.getInstance(context) }

    val themeMode by tm.themeMode.collectAsState()
    val colorPref by tm.colorScheme.collectAsState()
    val amoled by tm.amoledMode.collectAsState()

    val systemDark = isSystemInDarkTheme()
    val useDark = tm.shouldUseDarkTheme(systemDark)
    val useAmoled = tm.shouldUseAmoledTheme(systemDark)

    val cs = when {
        useAmoled -> tm.getAmoledColorScheme(colorPref)
        useDark   -> tm.getDarkColorScheme(colorPref)
        else      -> tm.getLightColorScheme(colorPref)
    }

    Log.d("ThemeApply", "mode=$themeMode, color=$colorPref, amoled=$amoled, systemDark=$systemDark, useDark=$useDark, useAmoled=$useAmoled")

    MaterialTheme(colorScheme = cs, typography = InsightDealTypography, content = content)
}

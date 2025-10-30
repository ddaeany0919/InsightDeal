package com.ddaeany0919.insightdeal

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.*
import com.ddaeany0919.insightdeal.data.theme.ThemeManager
import com.ddaeany0919.insightdeal.data.theme.ThemePreferences
import com.ddaeany0919.insightdeal.ui.theme.InsightDealTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val tm = remember { ThemeManager.getInstance(this) }
            val mode by tm.modeFlow.collectAsState(initial = ThemePreferences.Mode.SYSTEM)
            val dark = when (mode) {
                ThemePreferences.Mode.LIGHT -> false
                ThemePreferences.Mode.DARK -> true
                ThemePreferences.Mode.AMOLED -> true
                ThemePreferences.Mode.SYSTEM -> isSystemInDarkTheme()
            }
            val amoled = mode == ThemePreferences.Mode.AMOLED
            InsightDealTheme(
                darkTheme = dark,
                themeMode = if (amoled) ThemeMode.AMOLED else if (dark) ThemeMode.DARK else ThemeMode.LIGHT,
                amoledMode = amoled
            ) { MainApp() }
        }
    }
}

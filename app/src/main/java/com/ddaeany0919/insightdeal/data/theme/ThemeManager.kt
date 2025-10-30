package com.ddaeany0919.insightdeal.data.theme

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

// File: app/src/main/java/com/ddaeany0919/insightdeal/data/theme/ThemeManager.kt
// Requires dependency: implementation "androidx.datastore:datastore-preferences:1.0.0"

private val Context.themeDataStore: DataStore<Preferences> by preferencesDataStore(name = "theme_settings")

object ThemePreferences {
    val KEY_MODE = intPreferencesKey("theme_mode")
    enum class Mode(val value: Int) { SYSTEM(0), LIGHT(1), DARK(2), AMOLED(3) }
}

class ThemeManager private constructor(private val context: Context) {
    val modeFlow: Flow<ThemePreferences.Mode> = context.themeDataStore.data.map { prefs ->
        when (prefs[ThemePreferences.KEY_MODE] ?: 0) {
            1 -> ThemePreferences.Mode.LIGHT
            2 -> ThemePreferences.Mode.DARK
            3 -> ThemePreferences.Mode.AMOLED
            else -> ThemePreferences.Mode.SYSTEM
        }
    }

    suspend fun updateMode(mode: ThemePreferences.Mode) {
        context.themeDataStore.edit { p -> p[ThemePreferences.KEY_MODE] = mode.value }
    }

    companion object {
        @Volatile private var INSTANCE: ThemeManager? = null
        fun getInstance(context: Context): ThemeManager =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: ThemeManager(context.applicationContext).also { INSTANCE = it }
            }
    }
}

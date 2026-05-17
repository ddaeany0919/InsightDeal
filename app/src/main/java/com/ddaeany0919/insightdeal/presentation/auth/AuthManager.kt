package com.ddaeany0919.insightdeal.presentation.auth

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

val Context.dataStore by preferencesDataStore(name = "user_prefs")

object AuthManager {
    private val USERNAME_KEY = stringPreferencesKey("username")
    private val NICKNAME_KEY = stringPreferencesKey("nickname")

    fun getUsername(context: Context): Flow<String?> {
        return context.dataStore.data.map { it[USERNAME_KEY] ?: "admin" }
    }

    fun getNickname(context: Context): Flow<String?> {
        return context.dataStore.data.map { it[NICKNAME_KEY] ?: "admin" }
    }

    suspend fun saveUser(context: Context, username: String, nickname: String) {
        context.dataStore.edit { prefs ->
            prefs[USERNAME_KEY] = username
            prefs[NICKNAME_KEY] = nickname
        }
    }

    suspend fun logout(context: Context) {
        context.dataStore.edit { prefs ->
            prefs.remove(USERNAME_KEY)
            prefs.remove(NICKNAME_KEY)
        }
    }
}

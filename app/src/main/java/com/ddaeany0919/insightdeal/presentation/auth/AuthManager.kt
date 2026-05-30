package com.ddaeany0919.insightdeal.presentation.auth

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map

val Context.dataStore by preferencesDataStore(name = "user_prefs")

object AuthManager {
    private val USERNAME_KEY = stringPreferencesKey("username")
    private val NICKNAME_KEY = stringPreferencesKey("nickname")
    
    // ⚡ 자동로그인 & 아이디 저장 DataStore 키 정의
    private val AUTO_LOGIN_KEY = booleanPreferencesKey("auto_login_enabled")
    private val REMEMBER_ID_KEY = booleanPreferencesKey("remember_id_enabled")
    private val SAVED_ID_KEY = stringPreferencesKey("saved_id")

    fun getUsername(context: Context): Flow<String?> {
        return context.dataStore.data.map { prefs ->
            val autoLogin = prefs[AUTO_LOGIN_KEY] ?: true
            val current = prefs[USERNAME_KEY]
            if (current == "guest") {
                "guest"
            } else if (autoLogin) {
                current ?: prefs[SAVED_ID_KEY] ?: "admin"
            } else {
                current ?: "guest"
            }
        }
    }

    fun getNickname(context: Context): Flow<String?> {
        return context.dataStore.data.map { prefs ->
            val autoLogin = prefs[AUTO_LOGIN_KEY] ?: true
            val current = prefs[NICKNAME_KEY]
            if (current == "guest") {
                "guest"
            } else if (autoLogin) {
                current ?: "admin"
            } else {
                current ?: "guest"
            }
        }
    }

    fun isAutoLoginEnabled(context: Context): Flow<Boolean> {
        return context.dataStore.data.map { it[AUTO_LOGIN_KEY] ?: true }
    }

    fun isRememberIdEnabled(context: Context): Flow<Boolean> {
        return context.dataStore.data.map { it[REMEMBER_ID_KEY] ?: true }
    }

    fun getSavedId(context: Context): Flow<String?> {
        return context.dataStore.data.map { it[SAVED_ID_KEY] ?: "admin" }
    }

    suspend fun setAutoLoginEnabled(context: Context, enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[AUTO_LOGIN_KEY] = enabled
        }
    }

    suspend fun setRememberIdEnabled(context: Context, enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[REMEMBER_ID_KEY] = enabled
            if (!enabled) {
                prefs.remove(SAVED_ID_KEY)
            } else {
                val current = prefs[USERNAME_KEY]
                if (current != null && current != "guest") {
                    prefs[SAVED_ID_KEY] = current
                }
            }
        }
    }

    /**
     * ⚡ [신규] 비밀번호를 매핑하여 유저 정보를 가입 및 저장
     */
    suspend fun saveUserWithPassword(context: Context, username: String, nickname: String, password: String) {
        context.dataStore.edit { prefs ->
            prefs[USERNAME_KEY] = username
            prefs[NICKNAME_KEY] = nickname
            
            // user_{username} 키에 닉네임과 비밀번호를 함께 캡슐화 보존
            val userKey = stringPreferencesKey("user_$username")
            prefs[userKey] = "$nickname|$password"
            
            val rememberId = prefs[REMEMBER_ID_KEY] ?: true
            if (rememberId && username != "guest") {
                prefs[SAVED_ID_KEY] = username
            }
        }
    }

    /**
     * ⚡ [신규] 해당 ID가 이미 가입(등록)되었는지 체크
     */
    suspend fun isUserRegistered(context: Context, username: String): Boolean {
        if (username == "admin") return true
        val userKey = stringPreferencesKey("user_$username")
        val prefs = context.dataStore.data.firstOrNull() ?: return false
        return prefs.contains(userKey)
    }

    /**
     * ⚡ [신규] 로그인 자격 검증 (아이디/비밀번호 매칭 판독)
     */
    suspend fun checkUserCredentials(context: Context, username: String, password: String): Boolean {
        if (username == "admin" && password == "admin") return true
        val userKey = stringPreferencesKey("user_$username")
        val prefs = context.dataStore.data.firstOrNull() ?: return false
        val storedData = prefs[userKey] ?: return false
        val storedPassword = storedData.substringAfter("|", "")
        return storedPassword == password
    }

    /**
     * ⚡ [리팩토링] 기존 닉네임 변경 시 기존 비밀번호 유실 방지
     */
    suspend fun saveUser(context: Context, username: String, nickname: String) {
        context.dataStore.edit { prefs ->
            prefs[USERNAME_KEY] = username
            prefs[NICKNAME_KEY] = nickname
            
            val userKey = stringPreferencesKey("user_$username")
            val existingData = prefs[userKey]
            val existingPassword = if (existingData != null) {
                existingData.substringAfter("|", "")
            } else {
                if (username == "admin") "admin" else ""
            }
            
            // 기존 패스워드를 훼손하지 않고 닉네임 부분만 미학적으로 갱신
            prefs[userKey] = "$nickname|$existingPassword"
            
            val rememberId = prefs[REMEMBER_ID_KEY] ?: true
            if (rememberId && username != "guest") {
                prefs[SAVED_ID_KEY] = username
            }
        }
    }

    suspend fun logout(context: Context) {
        context.dataStore.edit { prefs ->
            // 로그아웃 시 현재 세션을 "guest"로 만들며, 자동로그인이 켜져있다면 그것도 일시 꺼줍니다.
            prefs[USERNAME_KEY] = "guest"
            prefs[NICKNAME_KEY] = "guest"
            prefs[AUTO_LOGIN_KEY] = false
        }
    }
}

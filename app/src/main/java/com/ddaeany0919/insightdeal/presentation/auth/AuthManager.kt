package com.ddaeany0919.insightdeal.presentation.auth

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.ddaeany0919.insightdeal.core.security.EncryptedPrefsManager
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.security.SecureRandom

val Context.dataStore by preferencesDataStore(name = "user_prefs")

object AuthManager {
    private val USERNAME_KEY = stringPreferencesKey("username")
    private val NICKNAME_KEY = stringPreferencesKey("nickname")
    private val AUTO_LOGIN_KEY = booleanPreferencesKey("auto_login_enabled")
    private val REMEMBER_ID_KEY = booleanPreferencesKey("remember_id_enabled")
    private val SAVED_ID_KEY = stringPreferencesKey("saved_id")

    private const val ENC_HASH_PREFIX = "auth_pwd_hash_"
    private const val ENC_SALT_PREFIX = "auth_pwd_salt_"
    private const val ENC_NICK_PREFIX = "auth_nickname_"

    fun hashPassword(username: String, password: String, saltHex: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val normalizedUser = username.trim().lowercase()
        val payload = "$saltHex:$normalizedUser:$password".toByteArray(StandardCharsets.UTF_8)
        val hashed = digest.digest(payload)
        return hashed.joinToString("") { "%02x".format(it) }
    }

    fun verifyPasswordHash(
        username: String,
        password: String,
        saltHex: String,
        expectedHashHex: String
    ): Boolean {
        if (saltHex.isBlank() || expectedHashHex.isBlank()) return false
        val computed = hashPassword(username, password, saltHex)
        return MessageDigest.isEqual(
            computed.toByteArray(StandardCharsets.UTF_8),
            expectedHashHex.toByteArray(StandardCharsets.UTF_8)
        )
    }

    private fun generateSaltHex(): String {
        val bytes = ByteArray(16)
        SecureRandom().nextBytes(bytes)
        return bytes.joinToString("") { "%02x".format(it) }
    }

    fun getUsername(context: Context): Flow<String?> {
        return context.dataStore.data.map { prefs ->
            val autoLogin = prefs[AUTO_LOGIN_KEY] ?: true
            val current = prefs[USERNAME_KEY]
            if (current.isNullOrBlank() || current == "guest") {
                "guest"
            } else if (autoLogin) {
                current
            } else {
                "guest"
            }
        }
    }

    fun getNickname(context: Context): Flow<String?> {
        return context.dataStore.data.map { prefs ->
            val autoLogin = prefs[AUTO_LOGIN_KEY] ?: true
            val current = prefs[NICKNAME_KEY]
            if (current.isNullOrBlank() || current == "guest") {
                "guest"
            } else if (autoLogin) {
                current
            } else {
                "guest"
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
        return context.dataStore.data.map { it[SAVED_ID_KEY] ?: "" }
    }

    fun getRegisteredNickname(context: Context, username: String): String? {
        val normalized = username.trim()
        if (normalized.isEmpty()) return null
        val encPrefs = EncryptedPrefsManager.getEncryptedPrefs(context)
        return encPrefs.getString("$ENC_NICK_PREFIX$normalized", null)
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
                if (!current.isNullOrBlank() && current != "guest") {
                    prefs[SAVED_ID_KEY] = current
                }
            }
        }
    }

    suspend fun saveUserWithPassword(
        context: Context,
        username: String,
        nickname: String,
        password: String
    ) {
        val cleanUser = username.trim()
        val cleanNick = nickname.trim()
        if (cleanUser.isEmpty() || cleanUser == "guest") return

        val saltHex = generateSaltHex()
        val passwordHash = hashPassword(cleanUser, password, saltHex)

        val encPrefs = EncryptedPrefsManager.getEncryptedPrefs(context)
        encPrefs.edit()
            .putString("$ENC_SALT_PREFIX$cleanUser", saltHex)
            .putString("$ENC_HASH_PREFIX$cleanUser", passwordHash)
            .putString("$ENC_NICK_PREFIX$cleanUser", cleanNick)
            .apply()

        context.dataStore.edit { prefs ->
            prefs[USERNAME_KEY] = cleanUser
            prefs[NICKNAME_KEY] = cleanNick
            prefs[AUTO_LOGIN_KEY] = true
            // 기존 평문 키가 남아있다면 즉시 제거
            prefs.remove(stringPreferencesKey("user_$cleanUser"))

            val rememberId = prefs[REMEMBER_ID_KEY] ?: true
            if (rememberId) {
                prefs[SAVED_ID_KEY] = cleanUser
            }
        }
    }

    suspend fun isUserRegistered(context: Context, username: String): Boolean {
        val cleanUser = username.trim()
        if (cleanUser.isEmpty() || cleanUser == "guest") return false

        val encPrefs = EncryptedPrefsManager.getEncryptedPrefs(context)
        if (encPrefs.contains("$ENC_HASH_PREFIX$cleanUser")) {
            return true
        }

        val legacyKey = stringPreferencesKey("user_$cleanUser")
        val prefs = context.dataStore.data.firstOrNull() ?: return false
        return prefs.contains(legacyKey)
    }

    suspend fun checkUserCredentials(
        context: Context,
        username: String,
        password: String
    ): Boolean {
        val cleanUser = username.trim()
        if (cleanUser.isEmpty() || password.isEmpty()) return false

        val encPrefs = EncryptedPrefsManager.getEncryptedPrefs(context)
        val storedSalt = encPrefs.getString("$ENC_SALT_PREFIX$cleanUser", null)
        val storedHash = encPrefs.getString("$ENC_HASH_PREFIX$cleanUser", null)

        if (!storedSalt.isNullOrBlank() && !storedHash.isNullOrBlank()) {
            return verifyPasswordHash(cleanUser, password, storedSalt, storedHash)
        }

        // 레거시 평문 저장 데이터가 있는 경우 1회 검증 후 암호화 해시 저장소로 마이그레이션하고 평문 삭제
        val legacyKey = stringPreferencesKey("user_$cleanUser")
        val prefs = context.dataStore.data.firstOrNull() ?: return false
        val legacyData = prefs[legacyKey] ?: return false
        val legacyNick = legacyData.substringBefore("|", cleanUser)
        val legacyPwd = legacyData.substringAfter("|", "")

        if (legacyPwd.isNotEmpty() && legacyPwd == password) {
            val newSalt = generateSaltHex()
            val newHash = hashPassword(cleanUser, password, newSalt)
            encPrefs.edit()
                .putString("$ENC_SALT_PREFIX$cleanUser", newSalt)
                .putString("$ENC_HASH_PREFIX$cleanUser", newHash)
                .putString("$ENC_NICK_PREFIX$cleanUser", legacyNick)
                .apply()
            context.dataStore.edit { mutablePrefs ->
                mutablePrefs.remove(legacyKey)
            }
            return true
        }
        return false
    }

    suspend fun saveUser(context: Context, username: String, nickname: String) {
        val cleanUser = username.trim()
        val cleanNick = nickname.trim()
        if (cleanUser.isEmpty()) return

        if (cleanUser != "guest") {
            val encPrefs = EncryptedPrefsManager.getEncryptedPrefs(context)
            encPrefs.edit()
                .putString("$ENC_NICK_PREFIX$cleanUser", cleanNick)
                .apply()
        }

        context.dataStore.edit { prefs ->
            prefs[USERNAME_KEY] = cleanUser
            prefs[NICKNAME_KEY] = cleanNick
            prefs[AUTO_LOGIN_KEY] = cleanUser != "guest"

            val rememberId = prefs[REMEMBER_ID_KEY] ?: true
            if (rememberId && cleanUser != "guest") {
                prefs[SAVED_ID_KEY] = cleanUser
            }
        }
    }

    suspend fun logout(context: Context) {
        context.dataStore.edit { prefs ->
            prefs[USERNAME_KEY] = "guest"
            prefs[NICKNAME_KEY] = "guest"
            prefs[AUTO_LOGIN_KEY] = false
        }
    }
}

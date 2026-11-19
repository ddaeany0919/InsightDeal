package com.ddaeany0919.insightdeal.core.security

import android.content.Context
import android.util.Log
import com.ddaeany0919.insightdeal.data.LocalDataProvider
import java.util.UUID

/**
 * 기기 고유 식별자(device_uuid) 및 서버 발급 X-Device-Token, FCM 토큰을 관리하는 SSOT 저장소 계약입니다.
 * JVM 단위 테스트에서는 인메모리 Fake 구현체로 교체하여 Android Context 없이 검증할 수 있습니다.
 */
interface DeviceIdentityStore {
    fun getOrCreateDeviceUuid(): String
    fun getDeviceToken(): String?
    fun saveDeviceToken(token: String, deviceUuid: String = getOrCreateDeviceUuid())
    fun clearDeviceToken()
    fun getCachedFcmToken(): String?
    fun saveFcmToken(fcmToken: String)
}

/**
 * EncryptedPrefsManager 기반의 스레드 안전(DCL + @Volatile + 분리 Lock) 단일 기기 식별자/토큰 저장소입니다.
 * - 최초 조회 시 기존 평문 prefs("insight_deal_prefs")의 "device_uuid"가 있으면 1회 마이그레이션합니다.
 * - 모든 쓰기 작업은 commit()을 사용해 프로세스 종료 직전에도 영속성을 보장합니다.
 */
class EncryptedDeviceIdentityStore(context: Context) : DeviceIdentityStore {

    private val appContext: Context = context.applicationContext ?: context

    @Volatile
    private var cachedUuid: String? = null

    @Volatile
    private var cachedToken: String? = null

    @Volatile
    private var cachedFcmToken: String? = null

    private val uuidLock = Any()
    private val tokenLock = Any()

    override fun getOrCreateDeviceUuid(): String {
        val current = cachedUuid
        if (!current.isNullOrBlank()) return current

        return synchronized(uuidLock) {
            val rechecked = cachedUuid
            if (!rechecked.isNullOrBlank()) return@synchronized rechecked

            val encryptedPrefs = EncryptedPrefsManager.getEncryptedPrefs(appContext)
            val savedEncrypted = encryptedPrefs.getString(KEY_DEVICE_UUID, null)?.trim()
            if (!savedEncrypted.isNullOrBlank()) {
                cachedUuid = savedEncrypted
                return@synchronized savedEncrypted
            }

            // 기존 업그레이드 사용자를 위해 레거시 평문 prefs의 device_uuid 1회 마이그레이션
            val legacyUuid = runCatching {
                appContext.getSharedPreferences(LEGACY_PREFS_NAME, Context.MODE_PRIVATE)
                    .getString(KEY_DEVICE_UUID, null)
                    ?.trim()
            }.getOrNull()

            val resolvedUuid = if (!legacyUuid.isNullOrBlank()) {
                legacyUuid
            } else {
                generateDeviceUuid()
            }

            val committed = encryptedPrefs.edit()
                .putString(KEY_DEVICE_UUID, resolvedUuid)
                .commit()
            if (!committed) {
                Log.w(TAG, "device_uuid commit() 반환값 false (메모리 캐시는 유지됨)")
            }
            cachedUuid = resolvedUuid
            resolvedUuid
        }
    }

    override fun getDeviceToken(): String? {
        val current = cachedToken
        if (!current.isNullOrBlank()) return current

        return synchronized(tokenLock) {
            val rechecked = cachedToken
            if (!rechecked.isNullOrBlank()) return@synchronized rechecked

            val stored = runCatching {
                EncryptedPrefsManager.getEncryptedPrefs(appContext)
                    .getString(KEY_DEVICE_TOKEN, null)
                    ?.trim()
                    ?.takeIf { it.isNotBlank() }
            }.getOrNull()

            cachedToken = stored
            stored
        }
    }

    override fun saveDeviceToken(token: String, deviceUuid: String) {
        val cleanToken = token.trim()
        if (cleanToken.isBlank()) return
        val cleanUuid = deviceUuid.trim()

        // 일관된 락 순서(uuidLock -> tokenLock)로 cachedUuid/cachedToken 및 암호화 저장소를 동기화
        synchronized(uuidLock) {
            synchronized(tokenLock) {
                runCatching {
                    val encryptedPrefs = EncryptedPrefsManager.getEncryptedPrefs(appContext)
                    val editor = encryptedPrefs.edit()
                        .putString(KEY_DEVICE_TOKEN, cleanToken)
                    if (cleanUuid.isNotBlank()) {
                        editor.putString(KEY_DEVICE_UUID, cleanUuid)
                    }
                    val committed = editor.commit()
                    if (!committed) {
                        runCatching { Log.w(TAG, "device_token commit() 반환값 false (메모리 캐시는 유지됨)") }
                    }
                }.onFailure { e ->
                    runCatching { Log.e(TAG, "device_token 암호화 저장소 저장 실패: ${e.message}", e) }
                }
                if (cleanUuid.isNotBlank()) {
                    cachedUuid = cleanUuid
                }
                cachedToken = cleanToken
            }
        }
    }

    override fun clearDeviceToken() {
        synchronized(tokenLock) {
            runCatching {
                EncryptedPrefsManager.getEncryptedPrefs(appContext)
                    .edit()
                    .remove(KEY_DEVICE_TOKEN)
                    .commit()
            }
            cachedToken = null
        }
    }

    override fun getCachedFcmToken(): String? {
        val current = cachedFcmToken
        if (!current.isNullOrBlank()) return current

        return synchronized(tokenLock) {
            val rechecked = cachedFcmToken
            if (!rechecked.isNullOrBlank()) return@synchronized rechecked

            val stored = runCatching {
                EncryptedPrefsManager.getEncryptedPrefs(appContext)
                    .getString(KEY_FCM_TOKEN, null)
                    ?.trim()
                    ?.takeIf { it.isNotBlank() }
            }.getOrNull()

            cachedFcmToken = stored
            stored
        }
    }

    override fun saveFcmToken(fcmToken: String) {
        val cleanToken = fcmToken.trim()
        if (cleanToken.isBlank()) return

        synchronized(tokenLock) {
            runCatching {
                EncryptedPrefsManager.getEncryptedPrefs(appContext)
                    .edit()
                    .putString(KEY_FCM_TOKEN, cleanToken)
                    .putLong(KEY_FCM_TOKEN_SAVED_AT, System.currentTimeMillis())
                    .commit()
            }
            cachedFcmToken = cleanToken
        }
    }

    companion object {
        private const val TAG = "DeviceIdentityStore"
        private const val LEGACY_PREFS_NAME = "insight_deal_prefs"
        internal const val KEY_DEVICE_UUID = "device_uuid"
        internal const val KEY_DEVICE_TOKEN = "device_token"
        internal const val KEY_FCM_TOKEN = "fcm_token"
        private const val KEY_FCM_TOKEN_SAVED_AT = "token_saved_at"

        fun generateDeviceUuid(): String {
            return "device_${UUID.randomUUID().toString().replace("-", "").take(12)}"
        }
    }
}

/**
 * 레거시 호출부 호환을 위한 SSOT 진입점 래퍼.
 * 내부적으로 LocalDataProvider의 단일 DeviceIdentityStore를 사용합니다.
 */
object DeviceIdProvider {
    fun getOrCreateDeviceUuid(): String = LocalDataProvider.getDeviceUuid()
    fun getDeviceToken(): String? = LocalDataProvider.getDeviceToken()
    fun saveDeviceToken(token: String, deviceUuid: String = getOrCreateDeviceUuid()) {
        LocalDataProvider.saveDeviceToken(token, deviceUuid)
    }
    fun clearDeviceToken() = LocalDataProvider.clearDeviceToken()
}

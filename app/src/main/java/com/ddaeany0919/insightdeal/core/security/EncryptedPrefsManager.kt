package com.ddaeany0919.insightdeal.core.security

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys

/**
 * 🔒 Keystore 기반 암호화 SharedPreferences 관리자 싱글톤
 * - 디바이스 락 상태에서 백그라운드(FCM 수신 시 등) Keystore 로드 지연 예외 대비 Fallback 처리 적용.
 * - JSON TypeConverter 성능 향상 및 객체 매번 생성 방지를 위한 메모리 캐싱 적용.
 */
object EncryptedPrefsManager {
    private const val TAG = "EncryptedPrefsManager"
    private const val PREFS_NAME = "insight_deal_prefs_encrypted"

    @Volatile
    private var cachedPrefs: SharedPreferences? = null

    /**
     * 안전하게 암호화된 SharedPreferences 인스턴스를 반환
     * - Keystore 초기화 예외 발생 시 일반 SharedPreferences로 안전하게 Fallback하여 앱이 튕기지 않도록 방어.
     */
    fun getEncryptedPrefs(context: Context): SharedPreferences {
        return cachedPrefs ?: synchronized(this) {
            cachedPrefs ?: try {
                Log.d(TAG, "Initializing EncryptedSharedPreferences...")
                val masterKeyAlias = MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC)
                val prefs = EncryptedSharedPreferences.create(
                    PREFS_NAME,
                    masterKeyAlias,
                    context.applicationContext,
                    EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                    EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
                )
                Log.d(TAG, "EncryptedSharedPreferences initialized successfully.")
                cachedPrefs = prefs
                prefs
            } catch (e: Exception) {
                // 백그라운드 스레드에서 Keystore 로드 실패 시(예: 잠금 상태 등) 크래시 방지를 위해 일반 SharedPreferences로 백업
                Log.e(TAG, "Failed to initialize EncryptedSharedPreferences. Falling back to normal prefs.", e)
                val fallbackPrefs = context.applicationContext.getSharedPreferences(
                    "insight_deal_prefs_fallback",
                    Context.MODE_PRIVATE
                )
                fallbackPrefs
            }
        }
    }
}

package com.ddaeany0919.insightdeal.core.security

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import java.io.File
import java.security.KeyStore

/**
 * Keystore 기반 암호화 SharedPreferences 관리자 싱글톤.
 * - 평문 폴백을 허용하지 않으며(Fail-Closed), 손상 시 손상된 prefs와 MasterKey를 1회 정리·재생성합니다.
 * - 정상 초기화 시 과거 레거시 평문 폴백 파일(insight_deal_prefs_fallback)을 삭제 정리합니다.
 */
object EncryptedPrefsManager {
    private const val TAG = "EncryptedPrefsManager"
    private const val PREFS_NAME = "insight_deal_prefs_encrypted"
    private const val LEGACY_FALLBACK_PREFS_NAME = "insight_deal_prefs_fallback"
    private const val ANDROID_KEYSTORE_PROVIDER = "AndroidKeyStore"

    @Volatile
    private var cachedPrefs: SharedPreferences? = null

    /**
     * 안전하게 암호화된 SharedPreferences 인스턴스를 반환합니다.
     * 초기화 예외 시 손상된 Keystore 엔트리 및 암호화 파일을 정리한 뒤 1회 재생성하며,
     * 재시도에도 실패하면 평문 폴백 없이 IllegalStateException을 전파합니다.
     */
    fun getEncryptedPrefs(context: Context): SharedPreferences {
        val appContext = context.applicationContext ?: context
        return cachedPrefs ?: synchronized(this) {
            cachedPrefs ?: run {
                try {
                    createAndCacheEncryptedPrefs(appContext)
                } catch (firstError: Exception) {
                    Log.e(
                        TAG,
                        "암호화 저장소 초기화 실패. 손상된 키/파일 정리 후 1회 재생성을 시도합니다.",
                        firstError
                    )
                    cachedPrefs = null
                    deleteCorruptedPrefsAndKey(appContext)
                    try {
                        createAndCacheEncryptedPrefs(appContext)
                    } catch (retryError: Exception) {
                        cachedPrefs = null
                        Log.e(
                            TAG,
                            "암호화 저장소 재생성 재시도 실패(Fail-Closed).",
                            retryError
                        )
                        throw IllegalStateException(
                            "암호화 저장소(EncryptedSharedPreferences)를 초기화할 수 없습니다.",
                            retryError
                        )
                    }
                }
            }
        }
    }

    private fun createAndCacheEncryptedPrefs(appContext: Context): SharedPreferences {
        val masterKey = MasterKey.Builder(appContext)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        val prefs = EncryptedSharedPreferences.create(
            appContext,
            PREFS_NAME,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
        // 레거시 평문 폴백 파일이 잔존할 경우 즉시 삭제
        runCatching { appContext.deleteSharedPreferences(LEGACY_FALLBACK_PREFS_NAME) }
        cachedPrefs = prefs
        return prefs
    }

    private fun deleteCorruptedPrefsAndKey(appContext: Context) {
        runCatching {
            appContext.deleteSharedPreferences(PREFS_NAME)
        }.onFailure { e ->
            Log.w(TAG, "deleteSharedPreferences 실패: ${e.message}")
        }

        // shared_prefs 디렉터리 내 잔여 파일(.xml / .bak) 추가 정리
        runCatching {
            val sharedPrefsDir = File(appContext.applicationInfo.dataDir, "shared_prefs")
            if (sharedPrefsDir.exists() && sharedPrefsDir.isDirectory) {
                listOf(
                    File(sharedPrefsDir, "$PREFS_NAME.xml"),
                    File(sharedPrefsDir, "$PREFS_NAME.xml.bak")
                ).forEach { file ->
                    if (file.exists()) {
                        file.delete()
                    }
                }
            }
        }.onFailure { e ->
            Log.w(TAG, "잔여 prefs 파일 삭제 실패: ${e.message}")
        }

        // 손상된 AndroidKeyStore MasterKey 엔트리 제거
        runCatching {
            val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE_PROVIDER)
            keyStore.load(null)
            if (keyStore.containsAlias(MasterKey.DEFAULT_MASTER_KEY_ALIAS)) {
                keyStore.deleteEntry(MasterKey.DEFAULT_MASTER_KEY_ALIAS)
                Log.w(TAG, "손상된 MasterKey alias 삭제 완료: ${MasterKey.DEFAULT_MASTER_KEY_ALIAS}")
            }
        }.onFailure { e ->
            Log.w(TAG, "MasterKey alias 삭제 실패: ${e.message}")
        }
    }
}

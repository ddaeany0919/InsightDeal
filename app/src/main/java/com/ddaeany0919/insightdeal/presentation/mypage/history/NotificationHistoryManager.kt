package com.ddaeany0919.insightdeal.presentation.mypage.history

import android.content.Context
import android.util.Log
import com.ddaeany0919.insightdeal.core.security.EncryptedPrefsManager
import com.ddaeany0919.insightdeal.data.LocalDataProvider
import com.ddaeany0919.insightdeal.network.AddNotificationRequest
import com.ddaeany0919.insightdeal.network.ApiService
import com.ddaeany0919.insightdeal.network.NetworkModule
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

data class NotificationAlert(
    val id: String,
    val title: String,
    val keyword: String,
    val receivedAt: Long,
    val dealUrl: String
)

object NotificationHistoryManager {
    private const val TAG = "NotificationHistoryManager"
    private const val KEY_ALERTS = "notification_alerts"

    private val _alerts = MutableStateFlow<List<NotificationAlert>>(emptyList())
    val alerts: StateFlow<List<NotificationAlert>> = _alerts

    private val exceptionHandler = CoroutineExceptionHandler { _, throwable ->
        safeLogE("알림 이력 비동기 처리 중 미처리 예외 방어: ${throwable.message}", throwable)
    }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO + exceptionHandler)
    internal val coroutineScopeForTest: CoroutineScope
        get() = scope

    private val apiService by lazy { NetworkModule.createService<ApiService>() }

    private fun safeLogD(message: String) {
        runCatching { Log.d(TAG, message) }
    }

    private fun safeLogE(message: String, error: Throwable? = null) {
        runCatching {
            if (error != null) Log.e(TAG, message, error) else Log.e(TAG, message)
        }
    }

    fun init(context: Context) {
        // 1. 우선 로컬 캐시를 로드하여 Offline-first로 화면 매핑
        loadLocalAlerts(context)

        // 2. 서버와 비동기 동기화 (단일 SSOT device_uuid 기준)
        syncWithServer(context)
    }

    private fun loadLocalAlerts(context: Context) {
        val jsonStr = runCatching {
            EncryptedPrefsManager.getEncryptedPrefs(context).getString(KEY_ALERTS, null)
        }.onFailure { e ->
            safeLogE("암호화 저장소에서 알림 캐시 조회 실패: ${e.message}")
        }.getOrNull()

        if (jsonStr != null) {
            try {
                val list = mutableListOf<NotificationAlert>()
                val array = JSONArray(jsonStr)
                for (i in 0 until array.length()) {
                    val obj = array.getJSONObject(i)
                    list.add(
                        NotificationAlert(
                            id = obj.getString("id"),
                            title = obj.getString("title"),
                            keyword = obj.getString("keyword"),
                            receivedAt = obj.getLong("receivedAt"),
                            dealUrl = obj.getString("dealUrl")
                        )
                    )
                }
                _alerts.value = list.sortedByDescending { it.receivedAt }
            } catch (e: Exception) {
                safeLogE("로컬 알림 캐시 로드 실패: ${e.message}")
            }
        }
    }

    /**
     * 백엔드 API 서버 (/api/users/notifications) 와 실시간 비동기 동기화
     */
    fun syncWithServer(context: Context) {
        scope.launch {
            try {
                val userId = LocalDataProvider.getDeviceUuid()
                safeLogD("🌐 서버와 알림 내역 동기화 시작 (Device: $userId)")

                val response = apiService.getUserNotifications(userId)
                if (response.isSuccessful && response.body() != null) {
                    val serverAlerts = response.body()!!
                    val mapped = serverAlerts.map { dto ->
                        NotificationAlert(
                            id = dto.id,
                            title = dto.title,
                            keyword = dto.keyword,
                            receivedAt = dto.receivedAt,
                            dealUrl = dto.dealUrl
                        )
                    }
                    _alerts.value = mapped
                    saveAlertsToPref(context, mapped)
                    safeLogD("✅ 서버 알림 동기화 완료: ${mapped.size} 건 수급")
                } else {
                    safeLogE("서버 알림 수급 실패: ${response.code()}")
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                safeLogE("서버 알림 동기화 예외 발생: ${e.localizedMessage}")
            }
        }
    }

    fun addAlert(context: Context, title: String, keyword: String, dealUrl: String) {
        scope.launch {
            try {
                val userId = LocalDataProvider.getDeviceUuid()
                val req = AddNotificationRequest(
                    user_id = userId,
                    title = title,
                    keyword = keyword,
                    deal_url = dealUrl
                )
                val response = apiService.addUserNotification(req)
                if (response.isSuccessful) {
                    safeLogD("✅ 서버 알림 추가 성공")
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                safeLogE("서버 알림 추가 실패: ${e.message}")
            }

            // 로컬 임시 반영 후 즉각 서버 동기화 (Optimistic UI 패턴)
            val currentList = _alerts.value.toMutableList()
            val newAlert = NotificationAlert(
                id = java.util.UUID.randomUUID().toString(),
                title = title,
                keyword = keyword,
                receivedAt = System.currentTimeMillis(),
                dealUrl = dealUrl
            )
            currentList.add(0, newAlert)
            _alerts.value = currentList
            saveAlertsToPref(context, currentList)

            syncWithServer(context)
        }
    }

    fun deleteAlert(context: Context, id: String) {
        scope.launch {
            try {
                val userId = LocalDataProvider.getDeviceUuid()
                val numId = id.toIntOrNull()
                if (numId != null) {
                    val response = apiService.deleteUserNotification(numId, userId)
                    if (response.isSuccessful) {
                        safeLogD("✅ 서버 알림 개별 삭제 성공 ($id)")
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                safeLogE("서버 알림 삭제 실패: ${e.message}")
            }

            // 로컬 즉시 반영
            val currentList = _alerts.value.toMutableList()
            currentList.removeAll { it.id == id }
            _alerts.value = currentList
            saveAlertsToPref(context, currentList)

            syncWithServer(context)
        }
    }

    fun clearAll(context: Context) {
        scope.launch {
            try {
                val userId = LocalDataProvider.getDeviceUuid()
                val response = apiService.clearUserNotifications(userId)
                if (response.isSuccessful) {
                    safeLogD("✅ 서버 알림 전체 청소 성공")
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                safeLogE("서버 알림 전체 삭제 실패: ${e.message}")
            }

            // 로컬 즉시 반영
            _alerts.value = emptyList()
            saveAlertsToPref(context, emptyList())

            syncWithServer(context)
        }
    }

    private fun saveAlertsToPref(context: Context, list: List<NotificationAlert>) {
        runCatching {
            val sharedPref = EncryptedPrefsManager.getEncryptedPrefs(context)
            val array = JSONArray()
            for (alert in list) {
                val obj = JSONObject().apply {
                    put("id", alert.id)
                    put("title", alert.title)
                    put("keyword", alert.keyword)
                    put("receivedAt", alert.receivedAt)
                    put("dealUrl", alert.dealUrl)
                }
                array.put(obj)
            }
            sharedPref.edit().putString(KEY_ALERTS, array.toString()).apply()
        }.onFailure { e ->
            safeLogE("로컬 캐시 동기화 예외: ${e.message}")
        }
    }
}

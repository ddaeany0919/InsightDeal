package com.ddaeany0919.insightdeal.presentation.mypage.history

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
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
    private const val PREF_NAME = "notification_history_prefs"
    private const val KEY_ALERTS = "notification_alerts"

    private val _alerts = MutableStateFlow<List<NotificationAlert>>(emptyList())
    val alerts: StateFlow<List<NotificationAlert>> = _alerts

    fun init(context: Context) {
        val sharedPref = com.ddaeany0919.insightdeal.core.security.EncryptedPrefsManager.getEncryptedPrefs(context)
        val jsonStr = sharedPref.getString(KEY_ALERTS, null)
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
                loadMockData(context)
            }
        } else {
            loadMockData(context)
        }
    }

    private fun loadMockData(context: Context) {
        val now = System.currentTimeMillis()
        val mockList = listOf(
            NotificationAlert(
                id = "mock_1",
                title = "🔥 [쿠팡] 아이패드 프로 11인치 M4 256GB 관세내 대박 할인 특가!",
                keyword = "아이패드",
                receivedAt = now - 1000 * 60 * 15, // 15분 전
                dealUrl = "https://www.coupang.com"
            ),
            NotificationAlert(
                id = "mock_2",
                title = "🎁 [뽐뿌] 다이슨 에어랩 멀티 스타일러 역대급 구성 사은품 증정 딜",
                keyword = "다이슨",
                receivedAt = now - 1000 * 60 * 60 * 2, // 2시간 전
                dealUrl = "https://www.ppomppu.co.kr"
            ),
            NotificationAlert(
                id = "mock_3",
                title = "💻 [펨코] 삼성전자 갤럭시북4 프로 고성능 노트북 최종 혜택가 119만원!",
                keyword = "노트북",
                receivedAt = now - 1000 * 60 * 60 * 24, // 1일 전
                dealUrl = "https://www.fmkorea.com"
            )
        )
        _alerts.value = mockList
        saveAlertsToPref(context, mockList)
    }

    fun addAlert(context: Context, title: String, keyword: String, dealUrl: String) {
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
    }

    fun deleteAlert(context: Context, id: String) {
        val currentList = _alerts.value.toMutableList()
        currentList.removeAll { it.id == id }
        _alerts.value = currentList
        saveAlertsToPref(context, currentList)
    }

    fun clearAll(context: Context) {
        _alerts.value = emptyList()
        saveAlertsToPref(context, emptyList())
    }

    private fun saveAlertsToPref(context: Context, list: List<NotificationAlert>) {
        val sharedPref = com.ddaeany0919.insightdeal.core.security.EncryptedPrefsManager.getEncryptedPrefs(context)
        try {
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
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}

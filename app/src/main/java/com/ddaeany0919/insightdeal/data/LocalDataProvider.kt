package com.ddaeany0919.insightdeal.data

import android.content.Context
import com.ddaeany0919.insightdeal.InsightDealApplication
import com.ddaeany0919.insightdeal.domain.PersonalizationEngine
import com.ddaeany0919.insightdeal.local.db.AppDatabase
import com.ddaeany0919.insightdeal.local.db.KeywordDao
import com.ddaeany0919.insightdeal.presentation.auth.AuthManager
import kotlinx.coroutines.flow.first
import java.util.UUID

/**
 * ViewModel이 Android Context 및 Application에 직접 의존하지 않도록
 * 로컬 Room DB, SharedPreferences 및 도메인 엔진 접근을 캡슐화하는 데이터 공급 계층입니다.
 */
object LocalDataProvider {

    val database: AppDatabase
        get() = InsightDealApplication.database

    val keywordDao: KeywordDao
        get() = InsightDealApplication.database.keywordDao()

    val personalizationEngine: PersonalizationEngine
        get() = PersonalizationEngine.getInstance(InsightDealApplication.instance)
}

class KeywordPreferencesDataSource {

    private val prefs
        get() = InsightDealApplication.instance.getSharedPreferences(
            PREFS_NAME,
            Context.MODE_PRIVATE
        )

    fun getOrCreateDeviceUuid(): String {
        val current = prefs.getString(KEY_DEVICE_UUID, null)
        if (!current.isNullOrBlank()) return current
        val generated = "device_${UUID.randomUUID().toString().replace("-", "").take(12)}"
        prefs.edit().putString(KEY_DEVICE_UUID, generated).apply()
        return generated
    }

    fun getDndEnabled(): Boolean = prefs.getBoolean(KEY_DND_ENABLED, false)

    fun getDndStartTime(): String = prefs.getString(KEY_DND_START_TIME, DEFAULT_START_TIME) ?: DEFAULT_START_TIME

    fun getDndEndTime(): String = prefs.getString(KEY_DND_END_TIME, DEFAULT_END_TIME) ?: DEFAULT_END_TIME

    fun getWeekDndJson(): String? = prefs.getString(KEY_DND_SETTINGS_JSON, null)

    fun saveDndRange(enabled: Boolean, startTime: String, endTime: String) {
        prefs.edit()
            .putBoolean(KEY_DND_ENABLED, enabled)
            .putString(KEY_DND_START_TIME, startTime)
            .putString(KEY_DND_END_TIME, endTime)
            .apply()
    }

    fun saveWeekDndJson(json: String) {
        prefs.edit().putString(KEY_DND_SETTINGS_JSON, json).apply()
    }

    fun saveFullDndSnapshot(enabled: Boolean, startTime: String, endTime: String, json: String) {
        prefs.edit()
            .putBoolean(KEY_DND_ENABLED, enabled)
            .putString(KEY_DND_START_TIME, startTime)
            .putString(KEY_DND_END_TIME, endTime)
            .putString(KEY_DND_SETTINGS_JSON, json)
            .apply()
    }

    suspend fun resolveUserOrDeviceKey(deviceUuid: String): String {
        val username = AuthManager.getUsername(InsightDealApplication.instance).first()
        return if (username == "guest" || username.isNullOrBlank()) deviceUuid else username
    }

    companion object {
        private const val PREFS_NAME = "insight_deal_prefs"
        private const val KEY_DEVICE_UUID = "device_uuid"
        private const val KEY_DND_ENABLED = "dnd_enabled"
        private const val KEY_DND_START_TIME = "dnd_start_time"
        private const val KEY_DND_END_TIME = "dnd_end_time"
        private const val KEY_DND_SETTINGS_JSON = "dnd_settings_json"
        private const val DEFAULT_START_TIME = "22:00"
        private const val DEFAULT_END_TIME = "08:00"
    }
}

object CommunityVoteStore {
    private const val PREFS_NAME = "should_i_buy_votes"

    private val prefs
        get() = InsightDealApplication.instance.getSharedPreferences(
            PREFS_NAME,
            Context.MODE_PRIVATE
        )

    fun getVote(postId: Int): String? = prefs.getString("vote_$postId", null)

    fun saveVote(postId: Int, vote: String) {
        prefs.edit().putString("vote_$postId", vote).apply()
    }

    fun clearVote(postId: Int) {
        prefs.edit().remove("vote_$postId").apply()
    }
}

package com.ddaeany0919.insightdeal.data

import android.content.Context
import android.content.SharedPreferences
import com.ddaeany0919.insightdeal.InsightDealApplication
import com.ddaeany0919.insightdeal.core.security.DeviceIdentityStore
import com.ddaeany0919.insightdeal.core.security.EncryptedDeviceIdentityStore
import com.ddaeany0919.insightdeal.domain.PersonalizationEngine
import com.ddaeany0919.insightdeal.local.db.AppDatabase
import com.ddaeany0919.insightdeal.local.db.KeywordDao
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

/**
 * ViewModel이 Android Context 및 Application에 직접 의존하지 않도록
 * 로컬 Room DB, 암호화 기기 식별자(SSOT), SharedPreferences 및 도메인 엔진 접근을 캡슐화하는 데이터 공급 계층입니다.
 */
object LocalDataProvider {

    @Volatile
    private var testIdentityStore: DeviceIdentityStore? = null

    private val defaultIdentityStore: DeviceIdentityStore by lazy {
        EncryptedDeviceIdentityStore(InsightDealApplication.instance)
    }

    internal val identityStore: DeviceIdentityStore
        get() = testIdentityStore ?: defaultIdentityStore

    /**
     * JVM 단위 테스트에서 Application 초기화 없이 DeviceIdentityStore를 주입하기 위한 테스트 훅입니다.
     */
    internal fun setDeviceIdentityStoreForTest(store: DeviceIdentityStore?) {
        testIdentityStore = store
    }

    fun getDeviceUuid(): String = identityStore.getOrCreateDeviceUuid()

    fun getDeviceToken(): String? = identityStore.getDeviceToken()

    fun saveDeviceToken(token: String, deviceUuid: String = getDeviceUuid()) {
        identityStore.saveDeviceToken(token, deviceUuid)
    }

    fun clearDeviceToken() {
        identityStore.clearDeviceToken()
    }

    fun getCachedFcmToken(): String? = identityStore.getCachedFcmToken()

    fun saveFcmToken(fcmToken: String) {
        identityStore.saveFcmToken(fcmToken)
    }

    val database: AppDatabase
        get() = InsightDealApplication.database

    val keywordDao: KeywordDao
        get() = InsightDealApplication.database.keywordDao()

    val personalizationEngine: PersonalizationEngine
        get() = PersonalizationEngine.getInstance(InsightDealApplication.instance)

    @Volatile
    private var testFeedPreferencesDataSource: FeedPreferencesDataSource? = null

    private val defaultFeedPreferencesDataSource: FeedPreferencesDataSource by lazy {
        FeedPreferencesDataSource()
    }

    val feedPreferencesDataSource: FeedPreferencesDataSource
        get() = testFeedPreferencesDataSource ?: defaultFeedPreferencesDataSource

    /**
     * JVM 단위 테스트에서 Application 초기화 없이 FeedPreferencesDataSource를 주입하기 위한 테스트 훅입니다.
     */
    internal fun setFeedPreferencesDataSourceForTest(dataSource: FeedPreferencesDataSource?) {
        testFeedPreferencesDataSource = dataSource
    }
}

/**
 * 홈 피드 보기 모드(카드 | 컴팩트) 및 숨김/뮤트 키워드 설정을 영속화하는 데이터 소스입니다.
 * Application 미초기화(JVM 단위 테스트) 환경에서는 안전하게 인메모리 폴백으로 동작하며,
 * SharedPreferences 사용 시 getStringSet 변이 버그를 피하기 위해 Gson JSON 문자열로 직렬화합니다.
 */
class FeedPreferencesDataSource @JvmOverloads constructor(
    private val prefsProvider: () -> SharedPreferences? = {
        runCatching {
            InsightDealApplication.instance.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        }.getOrNull()
    }
) {
    private val gson = Gson()
    private val lock = Any()
    private var inMemoryViewMode: String = VIEW_MODE_CARD
    private var inMemoryMutedKeywords: LinkedHashSet<String> = linkedSetOf()

    fun getViewMode(): String = synchronized(lock) {
        val prefs = prefsProvider()
        val raw = if (prefs != null) {
            prefs.getString(KEY_VIEW_MODE, VIEW_MODE_CARD)
        } else {
            inMemoryViewMode
        }
        normalizeViewMode(raw)
    }

    fun saveViewMode(mode: String) = synchronized(lock) {
        val normalized = normalizeViewMode(mode)
        inMemoryViewMode = normalized
        prefsProvider()?.edit()?.putString(KEY_VIEW_MODE, normalized)?.apply()
    }

    fun getMutedKeywords(): Set<String> = synchronized(lock) {
        val prefs = prefsProvider()
        if (prefs == null) {
            return LinkedHashSet(inMemoryMutedKeywords)
        }
        val rawJson = prefs.getString(KEY_MUTED_KEYWORDS_JSON, null)
        if (rawJson.isNullOrBlank()) {
            return emptySet()
        }
        val parsed = runCatching {
            val listType = object : TypeToken<List<String?>>() {}.type
            gson.fromJson<List<String?>>(rawJson, listType)
        }.getOrNull().orEmpty()
        normalizeMutedKeywords(parsed)
    }

    fun saveMutedKeywords(keywords: Set<String>) = synchronized(lock) {
        val normalized = normalizeMutedKeywords(keywords)
        inMemoryMutedKeywords = normalized
        val json = gson.toJson(normalized.toList())
        prefsProvider()?.edit()?.putString(KEY_MUTED_KEYWORDS_JSON, json)?.apply()
    }

    fun addMutedKeyword(keyword: String): Set<String> = synchronized(lock) {
        val trimmed = keyword.trim()
        val current = LinkedHashSet(getMutedKeywordsLocked())
        if (trimmed.isNotEmpty() && current.none { it.equals(trimmed, ignoreCase = true) }) {
            current.add(trimmed)
            persistMutedKeywordsLocked(current)
        }
        LinkedHashSet(current)
    }

    fun removeMutedKeyword(keyword: String): Set<String> = synchronized(lock) {
        val trimmed = keyword.trim()
        val current = LinkedHashSet(getMutedKeywordsLocked())
        if (trimmed.isNotEmpty()) {
            val removed = current.removeAll { it.equals(trimmed, ignoreCase = true) }
            if (removed) {
                persistMutedKeywordsLocked(current)
            }
        }
        LinkedHashSet(current)
    }

    private fun getMutedKeywordsLocked(): Set<String> {
        val prefs = prefsProvider()
        if (prefs == null) {
            return LinkedHashSet(inMemoryMutedKeywords)
        }
        val rawJson = prefs.getString(KEY_MUTED_KEYWORDS_JSON, null)
        if (rawJson.isNullOrBlank()) {
            return emptySet()
        }
        val parsed = runCatching {
            val listType = object : TypeToken<List<String?>>() {}.type
            gson.fromJson<List<String?>>(rawJson, listType)
        }.getOrNull().orEmpty()
        return normalizeMutedKeywords(parsed)
    }

    private fun persistMutedKeywordsLocked(keywords: Set<String>) {
        val normalized = normalizeMutedKeywords(keywords)
        inMemoryMutedKeywords = normalized
        val json = gson.toJson(normalized.toList())
        prefsProvider()?.edit()?.putString(KEY_MUTED_KEYWORDS_JSON, json)?.apply()
    }

    private fun normalizeViewMode(mode: String?): String {
        return if (mode?.trim()?.equals(VIEW_MODE_COMPACT, ignoreCase = true) == true) {
            VIEW_MODE_COMPACT
        } else {
            VIEW_MODE_CARD
        }
    }

    private fun normalizeMutedKeywords(keywords: Iterable<String?>): LinkedHashSet<String> {
        val result = LinkedHashSet<String>()
        for (raw in keywords) {
            val trimmed = raw?.trim() ?: continue
            if (trimmed.isNotEmpty() && result.none { it.equals(trimmed, ignoreCase = true) }) {
                result.add(trimmed)
            }
        }
        return result
    }

    companion object {
        private const val PREFS_NAME = "insight_deal_feed_prefs"
        private const val KEY_VIEW_MODE = "feed_view_mode"
        private const val KEY_MUTED_KEYWORDS_JSON = "muted_keywords_json"
        const val VIEW_MODE_CARD = "card"
        const val VIEW_MODE_COMPACT = "compact"
    }
}

class KeywordPreferencesDataSource {

    private val prefs
        get() = InsightDealApplication.instance.getSharedPreferences(
            PREFS_NAME,
            Context.MODE_PRIVATE
        )

    fun getOrCreateDeviceUuid(): String = LocalDataProvider.getDeviceUuid()

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

    /**
     * P0 게스트 단일 기기 모드에서는 서버 X-Device-Token 소유권 일치 보장을 위해
     * 항상 SSOT deviceUuid를 반환합니다.
     */
    suspend fun resolveUserOrDeviceKey(deviceUuid: String): String {
        return deviceUuid.ifBlank { LocalDataProvider.getDeviceUuid() }
    }

    companion object {
        private const val PREFS_NAME = "insight_deal_prefs"
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

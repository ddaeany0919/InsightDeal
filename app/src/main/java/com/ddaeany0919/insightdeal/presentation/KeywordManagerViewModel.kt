package com.ddaeany0919.insightdeal.presentation

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ddaeany0919.insightdeal.InsightDealApplication
import com.ddaeany0919.insightdeal.local.db.AppDatabase
import com.ddaeany0919.insightdeal.local.db.KeywordEntity
import com.ddaeany0919.insightdeal.network.AddKeywordRequest
import com.ddaeany0919.insightdeal.network.ApiService
import com.ddaeany0919.insightdeal.network.RegisterDeviceReq
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.UUID

class KeywordManagerViewModel : ViewModel() {
    private val apiService = com.ddaeany0919.insightdeal.network.NetworkModule.createService<ApiService>()
    private val database = AppDatabase.getDatabase(InsightDealApplication.instance)
    private val keywordDao = database.keywordDao()
    private val gson = Gson()

    // 로컬 SharedPreferences를 활용해 최초 1회 생성된 UUID를 영구 보존 (기기 영속성 보장)
    val deviceUuid: String by lazy {
        getOrCreateDeviceUuid()
    }

    private val _keywords = MutableStateFlow<List<KeywordEntity>>(emptyList())
    val keywords: StateFlow<List<KeywordEntity>> = _keywords.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    // 🕒 Toss DND 설정을 위한 프리미엄 시간 범위 StateFlow
    private val _dndEnabled = MutableStateFlow(false)
    val dndEnabled: StateFlow<Boolean> = _dndEnabled.asStateFlow()

    private val _dndStartTime = MutableStateFlow("22:00")
    val dndStartTime: StateFlow<String> = _dndStartTime.asStateFlow()

    private val _dndEndTime = MutableStateFlow("08:00")
    val dndEndTime: StateFlow<String> = _dndEndTime.asStateFlow()

    // 요일별 DND 상세 설정을 위한 데이터 구조 및 StateFlow
    data class DayDndSetting(
        val enabled: Boolean = false,
        val start: String = "22:00",
        val end: String = "08:00"
    )

    data class WeekDndSettings(
        val mon: DayDndSetting = DayDndSetting(),
        val tue: DayDndSetting = DayDndSetting(),
        val wed: DayDndSetting = DayDndSetting(),
        val thu: DayDndSetting = DayDndSetting(),
        val fri: DayDndSetting = DayDndSetting(),
        val sat: DayDndSetting = DayDndSetting(),
        val sun: DayDndSetting = DayDndSetting()
    )

    private val _weekDndSettings = MutableStateFlow(WeekDndSettings())
    val weekDndSettings: StateFlow<WeekDndSettings> = _weekDndSettings.asStateFlow()

    init {
        // 로컬 설정 로드
        loadLocalSettings()

        // 1. Room 로컬 DB의 키워드 실시간 흐름을 구독하여 offline-first로 화면에 매핑
        viewModelScope.launch {
            keywordDao.getAllKeywordsFlow().collect { localEntities ->
                _keywords.value = localEntities
            }
        }

        // 2. 서버 연동 및 최신 키워드/DND 비동기 Sync
        fetchKeywords()
        syncDndWithServer()
    }

    /**
     * SharedPreferences에서 고유 UUID 조회 및 미존재 시 자동 생성하여 영구 보존
     */
    private fun getOrCreateDeviceUuid(): String {
        val context = InsightDealApplication.instance
        val prefs = context.getSharedPreferences("insight_deal_prefs", Context.MODE_PRIVATE)
        var uuid = prefs.getString("device_uuid", null)
        if (uuid == null) {
            uuid = "device_${UUID.randomUUID().toString().replace("-", "").take(12)}"
            prefs.edit().putString("device_uuid", uuid).apply()
        }
        return uuid
    }

    /**
     * 로컬 SharedPreferences에서 DND 설정을 불러옵니다.
     */
    private fun loadLocalSettings() {
        val context = InsightDealApplication.instance
        val prefs = context.getSharedPreferences("insight_deal_prefs", Context.MODE_PRIVATE)
        _dndEnabled.value = prefs.getBoolean("dnd_enabled", false)
        _dndStartTime.value = prefs.getString("dnd_start_time", "22:00") ?: "22:00"
        _dndEndTime.value = prefs.getString("dnd_end_time", "08:00") ?: "08:00"

        val json = prefs.getString("dnd_settings_json", null)
        if (json != null) {
            try {
                _weekDndSettings.value = gson.fromJson(json, WeekDndSettings::class.java)
            } catch (e: Exception) {
                _weekDndSettings.value = WeekDndSettings()
            }
        } else {
            _weekDndSettings.value = WeekDndSettings()
        }
    }

    /**
     * DND 설정을 변경하고 로컬 영속화 및 백엔드 서버 동기화를 즉각 비동기로 수행합니다.
     */
    fun updateDndSettings(enabled: Boolean, startTime: String, endTime: String) {
        _dndEnabled.value = enabled
        _dndStartTime.value = startTime
        _dndEndTime.value = endTime

        // 로컬 SharedPreferences 동기 저장
        val context = InsightDealApplication.instance
        val prefs = context.getSharedPreferences("insight_deal_prefs", Context.MODE_PRIVATE)
        prefs.edit().apply {
            putBoolean("dnd_enabled", enabled)
            putString("dnd_start_time", startTime)
            putString("dnd_end_time", endTime)
        }.apply()

        // 서버 DB 즉각 비동기 동기화
        syncDndWithServer()
    }

    /**
     * 요일별 DND 설정을 변경하고 로컬 영속화 및 백엔드 서버 동기화를 수행합니다.
     */
    fun updateWeekDndSettings(settings: WeekDndSettings) {
        _weekDndSettings.value = settings

        val json = gson.toJson(settings)
        val context = InsightDealApplication.instance
        val prefs = context.getSharedPreferences("insight_deal_prefs", Context.MODE_PRIVATE)
        prefs.edit().putString("dnd_settings_json", json).apply()

        // 서버 DB 즉각 비동기 동기화
        syncDndWithServer()
    }

    /**
     * DND 설정을 일괄 템플릿으로 적용합니다. (더 좋은 방향 - 프리미엄 기능)
     */
    fun applyDndTemplate(templateType: String) {
        val current = _weekDndSettings.value
        val newSettings = when (templateType) {
            "weekdays" -> WeekDndSettings(
                mon = DayDndSetting(enabled = true, start = "22:00", end = "08:00"),
                tue = DayDndSetting(enabled = true, start = "22:00", end = "08:00"),
                wed = DayDndSetting(enabled = true, start = "22:00", end = "08:00"),
                thu = DayDndSetting(enabled = true, start = "22:00", end = "08:00"),
                fri = DayDndSetting(enabled = true, start = "22:00", end = "08:00"),
                sat = DayDndSetting(enabled = false, start = "23:00", end = "09:00"),
                sun = DayDndSetting(enabled = false, start = "23:00", end = "09:00")
            )
            "weekends" -> WeekDndSettings(
                mon = DayDndSetting(enabled = false, start = "22:00", end = "08:00"),
                tue = DayDndSetting(enabled = false, start = "22:00", end = "08:00"),
                wed = DayDndSetting(enabled = false, start = "22:00", end = "08:00"),
                thu = DayDndSetting(enabled = false, start = "22:00", end = "08:00"),
                fri = DayDndSetting(enabled = false, start = "22:00", end = "08:00"),
                sat = DayDndSetting(enabled = true, start = "23:00", end = "09:00"),
                sun = DayDndSetting(enabled = true, start = "23:00", end = "09:00")
            )
            "everyday" -> WeekDndSettings(
                mon = DayDndSetting(enabled = true, start = "22:00", end = "08:00"),
                tue = DayDndSetting(enabled = true, start = "22:00", end = "08:00"),
                wed = DayDndSetting(enabled = true, start = "22:00", end = "08:00"),
                thu = DayDndSetting(enabled = true, start = "22:00", end = "08:00"),
                fri = DayDndSetting(enabled = true, start = "22:00", end = "08:00"),
                sat = DayDndSetting(enabled = true, start = "22:00", end = "08:00"),
                sun = DayDndSetting(enabled = true, start = "22:00", end = "08:00")
            )
            "clear" -> WeekDndSettings(
                mon = DayDndSetting(enabled = false, start = "22:00", end = "08:00"),
                tue = DayDndSetting(enabled = false, start = "22:00", end = "08:00"),
                wed = DayDndSetting(enabled = false, start = "22:00", end = "08:00"),
                thu = DayDndSetting(enabled = false, start = "22:00", end = "08:00"),
                fri = DayDndSetting(enabled = false, start = "22:00", end = "08:00"),
                sat = DayDndSetting(enabled = false, start = "23:00", end = "09:00"),
                sun = DayDndSetting(enabled = false, start = "23:00", end = "09:00")
            )
            else -> current
        }
        updateWeekDndSettings(newSettings)
    }

    /**
     * 백엔드 API 서버의 device_tokens 엔드포인트에 DND 정보를 실시간 비동기 동기화 요청을 전송합니다.
     */
    private fun syncDndWithServer() {
        viewModelScope.launch {
            try {
                val json = gson.toJson(_weekDndSettings.value)
                val req = RegisterDeviceReq(
                    device_uuid = deviceUuid,
                    fcm_token = "",  // 키워드 알림용 UUID 등록 시점에 동기화
                    night_push_consent = false,
                    dnd_enabled = _dndEnabled.value,
                    dnd_start_time = _dndStartTime.value,
                    dnd_end_time = _dndEndTime.value,
                    dnd_settings_json = json
                )
                apiService.registerFCMToken(req)
            } catch (e: Exception) {
                _errorMessage.value = "서버 설정 동기화 실패: ${e.localizedMessage}"
            }
        }
    }

    /**
     * 서버에서 최신 키워드 리스트를 긁어와 로컬 DB와 원자적(Atomically)으로 동기화합니다.
     */
    fun fetchKeywords() {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val response = apiService.getPushKeywords(deviceUuid)
                if (response.isSuccessful) {
                    val responseBody = response.body()
                    val detailedList = responseBody?.detailed_keywords
                    val keywordsList = responseBody?.keywords ?: emptyList()
                    
                    // 로컬 DB를 비동기로 최신화하여 offline-first 무결성 보장
                    viewModelScope.launch(Dispatchers.IO) {
                        val currentLocal = keywordDao.getAllKeywords()
                        currentLocal.forEach { keywordDao.deleteKeyword(it) }
                        
                        if (!detailedList.isNullOrEmpty()) {
                            val entities = detailedList.map { dto ->
                                KeywordEntity(keyword = dto.keyword, isActive = dto.is_active)
                            }
                            keywordDao.insertKeywords(entities)
                        } else {
                            val entities = keywordsList.map { kw ->
                                KeywordEntity(keyword = kw, isActive = true)
                            }
                            keywordDao.insertKeywords(entities)
                        }
                    }

                    // 서버 측 DND 설정이 로컬보다 최신일 수 있으므로 동기화
                    responseBody?.let { body ->
                        body.dnd_enabled?.let { _dndEnabled.value = it }
                        body.dnd_start_time?.let { _dndStartTime.value = it }
                        body.dnd_end_time?.let { _dndEndTime.value = it }
                        body.dnd_settings_json?.let { json ->
                            try {
                                _weekDndSettings.value = gson.fromJson(json, WeekDndSettings::class.java)
                                // 로컬 pref도 동기화
                                val context = InsightDealApplication.instance
                                val prefs = context.getSharedPreferences("insight_deal_prefs", Context.MODE_PRIVATE)
                                prefs.edit().apply {
                                    putBoolean("dnd_enabled", body.dnd_enabled ?: false)
                                    putString("dnd_start_time", body.dnd_start_time ?: "22:00")
                                    putString("dnd_end_time", body.dnd_end_time ?: "08:00")
                                    putString("dnd_settings_json", json)
                                }.apply()
                            } catch (e: Exception) {
                                // 파싱 실패 시 무시
                            }
                        }
                    }
                } else {
                    _errorMessage.value = "키워드를 불러오지 못했습니다. (${response.code()})"
                }
            } catch (e: Exception) {
                _errorMessage.value = "네트워크 오류: ${e.localizedMessage}"
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun addKeyword(keyword: String) {
        if (keyword.isBlank() || _keywords.value.any { it.keyword == keyword }) return

        viewModelScope.launch {
            _isLoading.value = true
            try {
                val request = AddKeywordRequest(device_uuid = deviceUuid, keyword = keyword)
                val response = apiService.addPushKeyword(request)
                if (response.isSuccessful && response.body()?.success == true) {
                    // 로컬 DB에도 즉각 비동기로 캐시 이식
                    viewModelScope.launch(Dispatchers.IO) {
                        if (!keywordDao.isKeywordExists(keyword)) {
                            keywordDao.insertKeyword(KeywordEntity(keyword = keyword, isActive = true))
                        }
                    }
                    fetchKeywords()
                } else {
                    _errorMessage.value = response.body()?.message ?: "키워드 추가 실패"
                }
            } catch (e: Exception) {
                _errorMessage.value = "네트워크 오류: ${e.localizedMessage}"
            } finally {
                _isLoading.value = false
            }
        }
    }

    /**
     * 알림 상태를 토글합니다. (Optimistic UI 패턴)
     */
    fun toggleKeyword(entity: KeywordEntity) {
        viewModelScope.launch {
            val originalState = entity.isActive
            val newState = !originalState
            
            // Optimistic UI: Room 로컬 DB 즉시 비동기 업데이트 (UI 반응 지연 방지)
            viewModelScope.launch(Dispatchers.IO) {
                keywordDao.updateKeywordActive(entity.id, newState)
            }

            try {
                val request = AddKeywordRequest(device_uuid = deviceUuid, keyword = entity.keyword)
                val response = apiService.togglePushKeyword(request)
                if (!response.isSuccessful || response.body()?.success != true) {
                    // 서버 실패 시 원복
                    viewModelScope.launch(Dispatchers.IO) {
                        keywordDao.updateKeywordActive(entity.id, originalState)
                    }
                    _errorMessage.value = response.body()?.message ?: "서버 상태 동기화 실패"
                }
            } catch (e: Exception) {
                // 네트워크 에러 시 원복
                viewModelScope.launch(Dispatchers.IO) {
                    keywordDao.updateKeywordActive(entity.id, originalState)
                }
                _errorMessage.value = "네트워크 오류: ${e.localizedMessage}"
            }
        }
    }

    fun deleteKeyword(keyword: String) {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val request = AddKeywordRequest(device_uuid = deviceUuid, keyword = keyword)
                val response = apiService.deletePushKeyword(request)
                if (response.isSuccessful && response.body()?.success == true) {
                    // 로컬 DB에서도 비동기식으로 안전하게 추방
                    viewModelScope.launch(Dispatchers.IO) {
                        val currentLocal = keywordDao.getAllKeywords()
                        val target = currentLocal.find { it.keyword == keyword }
                        if (target != null) {
                            keywordDao.deleteKeyword(target)
                        }
                    }
                    fetchKeywords()
                } else {
                    _errorMessage.value = response.body()?.message ?: "키워드 삭제 실패"
                }
            } catch (e: Exception) {
                _errorMessage.value = "네트워크 오류: ${e.localizedMessage}"
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun clearError() {
        _errorMessage.value = null
    }
}

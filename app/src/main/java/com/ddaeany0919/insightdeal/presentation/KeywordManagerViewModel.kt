package com.ddaeany0919.insightdeal.presentation

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ddaeany0919.insightdeal.data.KeywordPreferencesDataSource
import com.ddaeany0919.insightdeal.data.LocalDataProvider
import com.ddaeany0919.insightdeal.local.db.KeywordDao
import com.ddaeany0919.insightdeal.local.db.KeywordEntity
import com.ddaeany0919.insightdeal.network.AddKeywordRequest
import com.ddaeany0919.insightdeal.network.ApiService
import com.ddaeany0919.insightdeal.network.NetworkModule
import com.ddaeany0919.insightdeal.network.UpdateDndReq
import com.google.gson.Gson
import com.google.gson.JsonParser
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Immutable
data class TestPushState(
    val isSending: Boolean = false,
    val isSuccess: Boolean? = null,
    val message: String? = null,
    val feedbackSeq: Int = 0
)

class KeywordManagerViewModel @JvmOverloads constructor(
    private val preferencesDataSource: KeywordPreferencesDataSource = KeywordPreferencesDataSource(),
    private val apiService: ApiService = NetworkModule.createService<ApiService>(),
    private val keywordDaoProvider: () -> KeywordDao = { LocalDataProvider.keywordDao },
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val coroutineScopeOverride: CoroutineScope? = null,
    autoInitialize: Boolean = (coroutineScopeOverride == null)
) : ViewModel() {

    private val vmScope: CoroutineScope
        get() = coroutineScopeOverride ?: viewModelScope

    private val keywordDao: KeywordDao by lazy { keywordDaoProvider() }
    private val gson = Gson()

    val deviceUuid: String by lazy {
        preferencesDataSource.getOrCreateDeviceUuid()
    }

    private val _keywords = MutableStateFlow<List<KeywordEntity>>(emptyList())
    val keywords: StateFlow<List<KeywordEntity>> = _keywords.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    private val _dndEnabled = MutableStateFlow(false)
    val dndEnabled: StateFlow<Boolean> = _dndEnabled.asStateFlow()

    private val _dndStartTime = MutableStateFlow("22:00")
    val dndStartTime: StateFlow<String> = _dndStartTime.asStateFlow()

    private val _dndEndTime = MutableStateFlow("08:00")
    val dndEndTime: StateFlow<String> = _dndEndTime.asStateFlow()

    data class DayDndSetting(
        @com.google.gson.annotations.SerializedName("enabled") val enabled: Boolean = false,
        @com.google.gson.annotations.SerializedName("start") val start: String = "22:00",
        @com.google.gson.annotations.SerializedName("end") val end: String = "08:00"
    )

    data class WeekDndSettings(
        @com.google.gson.annotations.SerializedName("mon") val mon: DayDndSetting = DayDndSetting(),
        @com.google.gson.annotations.SerializedName("tue") val tue: DayDndSetting = DayDndSetting(),
        @com.google.gson.annotations.SerializedName("wed") val wed: DayDndSetting = DayDndSetting(),
        @com.google.gson.annotations.SerializedName("thu") val thu: DayDndSetting = DayDndSetting(),
        @com.google.gson.annotations.SerializedName("fri") val fri: DayDndSetting = DayDndSetting(),
        @com.google.gson.annotations.SerializedName("sat") val sat: DayDndSetting = DayDndSetting(),
        @com.google.gson.annotations.SerializedName("sun") val sun: DayDndSetting = DayDndSetting()
    )

    private val _weekDndSettings = MutableStateFlow(WeekDndSettings())
    val weekDndSettings: StateFlow<WeekDndSettings> = _weekDndSettings.asStateFlow()

    private val _testPushState = MutableStateFlow(TestPushState())
    val testPushState: StateFlow<TestPushState> = _testPushState.asStateFlow()

    init {
        if (autoInitialize) {
            loadLocalSettings()

            vmScope.launch {
                keywordDao.getAllKeywordsFlow().collect { localEntities ->
                    _keywords.value = localEntities
                }
            }

            fetchKeywords()
        }
    }

    private fun loadLocalSettings() {
        _dndEnabled.value = preferencesDataSource.getDndEnabled()
        _dndStartTime.value = preferencesDataSource.getDndStartTime()
        _dndEndTime.value = preferencesDataSource.getDndEndTime()

        val json = preferencesDataSource.getWeekDndJson()
        if (!json.isNullOrBlank()) {
            try {
                _weekDndSettings.value = gson.fromJson(json, WeekDndSettings::class.java) ?: WeekDndSettings()
            } catch (_: Exception) {
                _weekDndSettings.value = WeekDndSettings()
            }
        } else {
            _weekDndSettings.value = WeekDndSettings()
        }
    }

    fun updateDndSettings(enabled: Boolean, startTime: String, endTime: String) {
        _dndEnabled.value = enabled
        _dndStartTime.value = startTime
        _dndEndTime.value = endTime

        preferencesDataSource.saveDndRange(
            enabled = enabled,
            startTime = startTime,
            endTime = endTime
        )
        syncDndWithServer()
    }

    fun updateWeekDndSettings(settings: WeekDndSettings) {
        _weekDndSettings.value = settings
        val json = gson.toJson(settings)
        preferencesDataSource.saveWeekDndJson(json)
        syncDndWithServer()
    }

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

    private fun syncDndWithServer() {
        vmScope.launch {
            try {
                val json = gson.toJson(_weekDndSettings.value)
                val req = UpdateDndReq(
                    device_uuid = deviceUuid,
                    dnd_enabled = _dndEnabled.value,
                    dnd_start_time = _dndStartTime.value,
                    dnd_end_time = _dndEndTime.value,
                    dnd_settings_json = json
                )
                val response = withContext(ioDispatcher) {
                    apiService.updateDndSettings(req)
                }
                if (!response.isSuccessful) {
                    _errorMessage.value = "알림 시간 설정 동기화에 실패했어요."
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _errorMessage.value = "알림 시간 설정 동기화에 실패했어요."
            }
        }
    }

    fun fetchKeywords() {
        vmScope.launch {
            _isLoading.value = true
            try {
                val userOrDevice = preferencesDataSource.resolveUserOrDeviceKey(deviceUuid)
                val response = withContext(ioDispatcher) {
                    apiService.getUserKeywords(userOrDevice)
                }
                if (response.isSuccessful) {
                    val responseBody = response.body()
                    val detailedList = responseBody?.detailed_keywords
                    val keywordsList = responseBody?.keywords ?: emptyList()

                    withContext(ioDispatcher) {
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

                    responseBody?.let { body ->
                        body.dnd_enabled?.let { _dndEnabled.value = it }
                        body.dnd_start_time?.let { _dndStartTime.value = it }
                        body.dnd_end_time?.let { _dndEndTime.value = it }
                        body.dnd_settings_json?.let { json ->
                            try {
                                val parsed = gson.fromJson(json, WeekDndSettings::class.java)
                                if (parsed != null) {
                                    _weekDndSettings.value = parsed
                                    preferencesDataSource.saveFullDndSnapshot(
                                        enabled = body.dnd_enabled ?: false,
                                        startTime = body.dnd_start_time ?: "22:00",
                                        endTime = body.dnd_end_time ?: "08:00",
                                        json = json
                                    )
                                }
                            } catch (_: Exception) {
                            }
                        }
                    }
                } else {
                    _errorMessage.value = "키워드 목록을 불러오지 못했어요. (${response.code()})"
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                _errorMessage.value = "네트워크 연결을 확인해 주세요."
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun addKeyword(keyword: String) {
        val trimmed = keyword.trim()
        if (trimmed.isBlank() || _keywords.value.any { it.keyword == trimmed }) return

        vmScope.launch {
            _isLoading.value = true
            try {
                val userOrDevice = preferencesDataSource.resolveUserOrDeviceKey(deviceUuid)
                val request = AddKeywordRequest(device_uuid = userOrDevice, keyword = trimmed)
                val response = withContext(ioDispatcher) {
                    apiService.addUserKeyword(request)
                }
                if (response.isSuccessful && response.body()?.success == true) {
                    withContext(ioDispatcher) {
                        if (!keywordDao.isKeywordExists(trimmed)) {
                            keywordDao.insertKeyword(KeywordEntity(keyword = trimmed, isActive = true))
                        }
                    }
                    fetchKeywords()
                } else {
                    _errorMessage.value = response.body()?.message ?: "키워드를 등록하지 못했어요."
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                _errorMessage.value = "네트워크 연결을 확인해 주세요."
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun toggleKeyword(entity: KeywordEntity) {
        vmScope.launch {
            val originalState = entity.isActive
            val newState = !originalState

            withContext(ioDispatcher) {
                keywordDao.updateKeywordActive(entity.id, newState)
            }

            try {
                val userOrDevice = preferencesDataSource.resolveUserOrDeviceKey(deviceUuid)
                val request = AddKeywordRequest(device_uuid = userOrDevice, keyword = entity.keyword)
                val response = withContext(ioDispatcher) {
                    apiService.toggleUserKeyword(request)
                }
                if (!response.isSuccessful || response.body()?.success != true) {
                    withContext(ioDispatcher) {
                        keywordDao.updateKeywordActive(entity.id, originalState)
                    }
                    _errorMessage.value = response.body()?.message ?: "알림 상태를 변경하지 못했어요."
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                withContext(ioDispatcher) {
                    keywordDao.updateKeywordActive(entity.id, originalState)
                }
                _errorMessage.value = "네트워크 연결을 확인해 주세요."
            }
        }
    }

    fun deleteKeyword(keyword: String) {
        vmScope.launch {
            _isLoading.value = true
            try {
                withContext(ioDispatcher) {
                    val currentLocal = keywordDao.getAllKeywords()
                    currentLocal.filter { it.keyword == keyword }.forEach { target ->
                        keywordDao.deleteKeyword(target)
                    }
                }

                val userOrDevice = preferencesDataSource.resolveUserOrDeviceKey(deviceUuid)
                val request = AddKeywordRequest(device_uuid = userOrDevice, keyword = keyword)
                val response = withContext(ioDispatcher) {
                    apiService.deleteUserKeyword(request)
                }
                if (userOrDevice != deviceUuid) {
                    try {
                        withContext(ioDispatcher) {
                            apiService.deleteUserKeyword(AddKeywordRequest(device_uuid = deviceUuid, keyword = keyword))
                        }
                    } catch (e: CancellationException) {
                        throw e
                    } catch (_: Exception) {
                    }
                }
                if (response.isSuccessful && response.body()?.success == true) {
                    fetchKeywords()
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
            } finally {
                _isLoading.value = false
            }
        }
    }

    /**
     * 테스트 푸시 알림 발송 (POST /api/push/test)
     * 모달 오류(_errorMessage)와 분리된 전용 testPushState로 로딩·성공·실패 피드백을 노출합니다.
     */
    fun sendTestPush() {
        val current = _testPushState.value
        if (current.isSending) return

        _testPushState.value = current.copy(isSending = true)
        vmScope.launch {
            try {
                val response = withContext(ioDispatcher) {
                    apiService.sendTestPush(mapOf("device_uuid" to deviceUuid))
                }
                val latest = _testPushState.value
                if (response.isSuccessful) {
                    val body = response.body()
                    val bodyMessage = body?.message?.trim()?.takeIf { it.isNotEmpty() }
                    if (body?.success == true) {
                        val successMsg = bodyMessage ?: "테스트 푸시 알림을 발송했어요!"
                        _testPushState.value = latest.copy(
                            isSending = false,
                            isSuccess = true,
                            message = successMsg,
                            feedbackSeq = latest.feedbackSeq + 1
                        )
                    } else {
                        val failMsg = bodyMessage ?: "테스트 알림 발송에 실패했어요."
                        _testPushState.value = latest.copy(
                            isSending = false,
                            isSuccess = false,
                            message = failMsg,
                            feedbackSeq = latest.feedbackSeq + 1
                        )
                    }
                } else {
                    val rawError = runCatching { response.errorBody()?.string() }.getOrNull()
                    val parsedDetail = extractApiErrorMessage(rawError)
                    val code = response.code()
                    val fallbackMsg = when (code) {
                        404 -> "등록된 알림 기기를 찾지 못했어요. 알림 설정을 먼저 저장해 주세요."
                        429 -> "테스트 알림은 1분에 최대 6회까지 보낼 수 있어요. 잠시 후 다시 시도해 주세요."
                        401, 403 -> "기기 인증에 실패했어요. 앱을 다시 실행한 후 시도해 주세요."
                        else -> "테스트 알림 발송에 실패했어요. ($code)"
                    }
                    // 401/403 인증 오류 및 5xx 서버 오류에서는 영문 detail 노출을 막고 사용자 친화적 한국어 안내를 우선한다.
                    val resolvedMessage = when {
                        code == 401 || code == 403 || code >= 500 -> fallbackMsg
                        else -> parsedDetail ?: fallbackMsg
                    }
                    _testPushState.value = latest.copy(
                        isSending = false,
                        isSuccess = false,
                        message = resolvedMessage,
                        feedbackSeq = latest.feedbackSeq + 1
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                val latest = _testPushState.value
                _testPushState.value = latest.copy(
                    isSending = false,
                    isSuccess = false,
                    message = "네트워크 연결을 확인해 주세요.",
                    feedbackSeq = latest.feedbackSeq + 1
                )
            }
        }
    }

    fun clearTestPushFeedback() {
        val current = _testPushState.value
        if (current.message != null || current.isSuccess != null) {
            _testPushState.value = current.copy(isSuccess = null, message = null)
        }
    }

    private fun extractApiErrorMessage(rawBody: String?): String? {
        val trimmed = rawBody?.trim().orEmpty()
        if (trimmed.isEmpty()) return null
        return runCatching {
            val obj = JsonParser.parseString(trimmed).asJsonObject
            val detail = obj.get("detail")?.takeIf { !it.isJsonNull && it.isJsonPrimitive }?.asString?.trim()
            val message = obj.get("message")?.takeIf { !it.isJsonNull && it.isJsonPrimitive }?.asString?.trim()
            detail?.takeIf { it.isNotEmpty() } ?: message?.takeIf { it.isNotEmpty() }
        }.getOrNull()
    }

    fun clearError() {
        _errorMessage.value = null
    }
}

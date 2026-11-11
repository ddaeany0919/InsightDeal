package com.ddaeany0919.insightdeal.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ddaeany0919.insightdeal.data.KeywordPreferencesDataSource
import com.ddaeany0919.insightdeal.data.LocalDataProvider
import com.ddaeany0919.insightdeal.local.db.KeywordEntity
import com.ddaeany0919.insightdeal.network.AddKeywordRequest
import com.ddaeany0919.insightdeal.network.ApiService
import com.ddaeany0919.insightdeal.network.NetworkModule
import com.ddaeany0919.insightdeal.network.RegisterDeviceReq
import com.google.gson.Gson
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class KeywordManagerViewModel(
    private val preferencesDataSource: KeywordPreferencesDataSource = KeywordPreferencesDataSource()
) : ViewModel() {

    private val apiService = NetworkModule.createService<ApiService>()
    private val keywordDao = LocalDataProvider.keywordDao
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

    init {
        loadLocalSettings()

        viewModelScope.launch {
            keywordDao.getAllKeywordsFlow().collect { localEntities ->
                _keywords.value = localEntities
            }
        }

        fetchKeywords()
        syncDndWithServer()
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
        viewModelScope.launch {
            try {
                val json = gson.toJson(_weekDndSettings.value)
                val req = RegisterDeviceReq(
                    device_uuid = deviceUuid,
                    fcm_token = "",
                    night_push_consent = false,
                    dnd_enabled = _dndEnabled.value,
                    dnd_start_time = _dndStartTime.value,
                    dnd_end_time = _dndEndTime.value,
                    dnd_settings_json = json
                )
                apiService.registerFCMToken(req)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _errorMessage.value = "알림 시간 설정 동기화에 실패했어요."
            }
        }
    }

    fun fetchKeywords() {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val userOrDevice = preferencesDataSource.resolveUserOrDeviceKey(deviceUuid)
                val response = apiService.getUserKeywords(userOrDevice)
                if (response.isSuccessful) {
                    val responseBody = response.body()
                    val detailedList = responseBody?.detailed_keywords
                    val keywordsList = responseBody?.keywords ?: emptyList()

                    withContext(Dispatchers.IO) {
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

        viewModelScope.launch {
            _isLoading.value = true
            try {
                val userOrDevice = preferencesDataSource.resolveUserOrDeviceKey(deviceUuid)
                val request = AddKeywordRequest(device_uuid = userOrDevice, keyword = trimmed)
                val response = apiService.addUserKeyword(request)
                if (response.isSuccessful && response.body()?.success == true) {
                    withContext(Dispatchers.IO) {
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
        viewModelScope.launch {
            val originalState = entity.isActive
            val newState = !originalState

            withContext(Dispatchers.IO) {
                keywordDao.updateKeywordActive(entity.id, newState)
            }

            try {
                val userOrDevice = preferencesDataSource.resolveUserOrDeviceKey(deviceUuid)
                val request = AddKeywordRequest(device_uuid = userOrDevice, keyword = entity.keyword)
                val response = apiService.toggleUserKeyword(request)
                if (!response.isSuccessful || response.body()?.success != true) {
                    withContext(Dispatchers.IO) {
                        keywordDao.updateKeywordActive(entity.id, originalState)
                    }
                    _errorMessage.value = response.body()?.message ?: "알림 상태를 변경하지 못했어요."
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                withContext(Dispatchers.IO) {
                    keywordDao.updateKeywordActive(entity.id, originalState)
                }
                _errorMessage.value = "네트워크 연결을 확인해 주세요."
            }
        }
    }

    fun deleteKeyword(keyword: String) {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                withContext(Dispatchers.IO) {
                    val currentLocal = keywordDao.getAllKeywords()
                    currentLocal.filter { it.keyword == keyword }.forEach { target ->
                        keywordDao.deleteKeyword(target)
                    }
                }

                val userOrDevice = preferencesDataSource.resolveUserOrDeviceKey(deviceUuid)
                val request = AddKeywordRequest(device_uuid = userOrDevice, keyword = keyword)
                val response = apiService.deleteUserKeyword(request)
                if (userOrDevice != deviceUuid) {
                    try {
                        apiService.deleteUserKeyword(AddKeywordRequest(device_uuid = deviceUuid, keyword = keyword))
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

    fun clearError() {
        _errorMessage.value = null
    }
}

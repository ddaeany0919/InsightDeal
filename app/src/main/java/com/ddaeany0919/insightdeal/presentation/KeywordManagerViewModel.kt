package com.ddaeany0919.insightdeal.presentation

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ddaeany0919.insightdeal.InsightDealApplication
import com.ddaeany0919.insightdeal.network.AddKeywordRequest
import com.ddaeany0919.insightdeal.network.ApiService
import com.ddaeany0919.insightdeal.network.RegisterDeviceReq
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.UUID

class KeywordManagerViewModel : ViewModel() {
    private val apiService = com.ddaeany0919.insightdeal.network.NetworkModule.createService<ApiService>()

    // 로컬 SharedPreferences를 활용해 최초 1회 생성된 UUID를 영구 보존 (기기 영속성 보장)
    val deviceUuid: String by lazy {
        getOrCreateDeviceUuid()
    }

    private val _keywords = MutableStateFlow<List<String>>(emptyList())
    val keywords: StateFlow<List<String>> = _keywords.asStateFlow()

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

    init {
        // 로컬 설정 로드 및 서버 연동
        loadLocalSettings()
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
     * 백엔드 API 서버의 device_tokens 엔드포인트에 DND 정보를 실시간 비동기 동기화 요청을 전송합니다.
     */
    private fun syncDndWithServer() {
        viewModelScope.launch {
            try {
                val req = RegisterDeviceReq(
                    device_uuid = deviceUuid,
                    fcm_token = "",  // 키워드 알림용 UUID 등록 시점에 동기화
                    night_push_consent = false,
                    dnd_enabled = _dndEnabled.value,
                    dnd_start_time = _dndStartTime.value,
                    dnd_end_time = _dndEndTime.value
                )
                apiService.registerFCMToken(req)
            } catch (e: Exception) {
                // 백그라운드 서버 동기화는 사용자 흐름을 방해하지 않도록 에러 무시 혹은 가볍게 로깅
                _errorMessage.value = "서버 설정 동기화 실패: ${e.localizedMessage}"
            }
        }
    }

    private fun fetchKeywords() {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val response = apiService.getPushKeywords(deviceUuid)
                if (response.isSuccessful) {
                    val list = response.body()?.keywords ?: emptyList()
                    _keywords.value = list
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
        if (keyword.isBlank() || _keywords.value.contains(keyword)) return

        viewModelScope.launch {
            _isLoading.value = true
            try {
                val request = AddKeywordRequest(device_uuid = deviceUuid, keyword = keyword)
                val response = apiService.addPushKeyword(request)
                if (response.isSuccessful && response.body()?.success == true) {
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

    fun deleteKeyword(keyword: String) {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val request = AddKeywordRequest(device_uuid = deviceUuid, keyword = keyword)
                val response = apiService.deletePushKeyword(request)
                if (response.isSuccessful && response.body()?.success == true) {
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

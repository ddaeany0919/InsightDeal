package com.ddaeany0919.insightdeal.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ddaeany0919.insightdeal.network.AddKeywordRequest
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.UUID
import com.ddaeany0919.insightdeal.network.ApiService

class KeywordManagerViewModel : ViewModel() {
    private val apiService = com.ddaeany0919.insightdeal.network.NetworkModule.createService<ApiService>()

    // 디바이스 식별자 (FCM 토큰이 없어도 식별 가능한 고유 UUID)
    // 실제 상용화 앱에서는 SharedPreferences 등에서 영구 저장/불러오기 해야 함
    private var deviceUuid = "device_${UUID.randomUUID().toString().take(8)}"

    private val _keywords = MutableStateFlow<List<String>>(emptyList())
    val keywords: StateFlow<List<String>> = _keywords.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    init {
        // ViewModel 생성 시 서버에서 기존 키워드 목록을 불러옴
        fetchKeywords()
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
                    // 낙관적 업데이트(Optimistic Update) 대신 서버 재조회
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

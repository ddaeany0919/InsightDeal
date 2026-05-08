package com.ddaeany0919.insightdeal.presentation.alerts

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ddaeany0919.insightdeal.network.AddKeywordRequest
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import com.ddaeany0919.insightdeal.network.ApiService

class AlertsViewModel(private val deviceUuid: String) : ViewModel() {
    private val _keywords = MutableStateFlow<List<String>>(emptyList())
    val keywords: StateFlow<List<String>> = _keywords.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    init {
        loadKeywords()
    }

    private fun loadKeywords() {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val response = com.ddaeany0919.insightdeal.network.NetworkModule.createService<ApiService>().getPushKeywords(deviceUuid)
                if (response.isSuccessful) {
                    _keywords.value = response.body()?.keywords ?: emptyList()
                } else {
                    Log.e("AlertsViewModel", "Failed to load keywords: ${response.code()}")
                }
            } catch (e: Exception) {
                Log.e("AlertsViewModel", "Error loading keywords", e)
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun addKeyword(keyword: String) {
        if (keyword.isBlank()) return
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val request = AddKeywordRequest(device_uuid = deviceUuid, keyword = keyword)
                val response = com.ddaeany0919.insightdeal.network.NetworkModule.createService<ApiService>().addPushKeyword(request)
                if (response.isSuccessful && response.body()?.success == true) {
                    // Update local list
                    val currentList = _keywords.value.toMutableList()
                    if (!currentList.contains(keyword)) {
                        currentList.add(keyword)
                        _keywords.value = currentList
                    }
                } else {
                    Log.e("AlertsViewModel", "Failed to add keyword: ${response.code()}")
                }
            } catch (e: Exception) {
                Log.e("AlertsViewModel", "Error adding keyword", e)
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
                val response = com.ddaeany0919.insightdeal.network.NetworkModule.createService<ApiService>().deletePushKeyword(request)
                if (response.isSuccessful && response.body()?.success == true) {
                    // Update local list
                    val currentList = _keywords.value.toMutableList()
                    currentList.remove(keyword)
                    _keywords.value = currentList
                } else {
                    Log.e("AlertsViewModel", "Failed to delete keyword: ${response.code()}")
                }
            } catch (e: Exception) {
                Log.e("AlertsViewModel", "Error deleting keyword", e)
            } finally {
                _isLoading.value = false
            }
        }
    }
}

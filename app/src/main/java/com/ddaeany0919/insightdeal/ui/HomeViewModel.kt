package com.ddaeany0919.insightdeal.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ddaeany0919.insightdeal.data.DealsRepository
import com.ddaeany0919.insightdeal.data.RepositoryProvider
import com.ddaeany0919.insightdeal.data.Resource
import com.ddaeany0919.insightdeal.models.ApiDeal
import com.ddaeany0919.insightdeal.models.ComparisonResponse
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * 🏠 홈 화면 ViewModel - 실데이터 연결 버전
 * 목표: 1초 내 첫 렌더, 캐시 즉시 표시, 2초 내 최신화
 */
class HomeViewModel(
    private val repository: DealsRepository = RepositoryProvider.getCurrentRepository()
) : ViewModel() {

    // UI 상태
    private val _popularDeals = MutableStateFlow<Resource<List<ApiDeal>>>(Resource.Loading())
    val popularDeals: StateFlow<Resource<List<ApiDeal>>> = _popularDeals

    init {
        loadInitialFeed()
    }

    private val _searchResult = MutableStateFlow<Resource<ComparisonResponse>?>(null)
    val searchResult: StateFlow<Resource<ComparisonResponse>?> = _searchResult

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing

    // 초기 로딩: 인기 검색어 기반 피드
    fun loadInitialFeed() {
        viewModelScope.launch {
            repository.getPopularDeals().catch { e ->
                _popularDeals.value = Resource.Error("피드를 불러오지 못했습니다", throwable = e)
            }.collectLatest { res ->
                _popularDeals.value = res
            }
        }
    }

    // Pull-to-Refresh: 강제 새로고침
    fun refreshFeed() {
        if (_isRefreshing.value) return
        _isRefreshing.value = true
        viewModelScope.launch {
            repository.getPopularDeals().collectLatest { res ->
                _popularDeals.value = res
            }
            _isRefreshing.value = false
        }
    }

    // 단일 검색 (검색바/자동완성 연동)
    fun search(query: String, forceRefresh: Boolean = false) {
        viewModelScope.launch {
            repository.searchDeal(query, forceRefresh).collectLatest { res ->
                _searchResult.value = res
            }
        }
    }
}

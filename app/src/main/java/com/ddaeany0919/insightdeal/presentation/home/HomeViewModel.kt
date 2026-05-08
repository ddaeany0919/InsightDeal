package com.ddaeany0919.insightdeal.presentation.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.cachedIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import com.ddaeany0919.insightdeal.models.DealItem
import com.ddaeany0919.insightdeal.network.NetworkModule
import com.ddaeany0919.insightdeal.network.ApiService
import com.ddaeany0919.insightdeal.presentation.home.HotDealsPagingSource

class HomeViewModel : ViewModel() {

    private val apiService = NetworkModule.createService<ApiService>()

    private val _popularKeywords = MutableStateFlow<List<String>>(emptyList())
    val popularKeywords: StateFlow<List<String>> = _popularKeywords.asStateFlow()

    init {
        viewModelScope.launch {
            try {
                val response = apiService.getPopularKeywords()
                if (response.isSuccessful) {
                    _popularKeywords.value = response.body()?.keywords ?: emptyList()
                }
            } catch (e: Exception) {
                // Ignore failure and fallback to empty keywords
            }
        }
    }

    data class FilterState(
        val category: String = "전체",
        val keyword: String? = null,
        val platform: String? = null
    )

    private val _filterState = MutableStateFlow(FilterState())
    val filterState: StateFlow<FilterState> = _filterState.asStateFlow()

    // ✨ Paging3 적용: 필터 상태(FilterState)가 변경될 때마다 Pager 폭포수 재생성 (flatMapLatest)
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val dealsPagingData: Flow<PagingData<DealItem>> = _filterState.flatMapLatest { state ->
        Pager(
            config = PagingConfig(
                pageSize = 20,
                enablePlaceholders = false,
                initialLoadSize = 20
            ),
            pagingSourceFactory = { 
                HotDealsPagingSource(
                    apiService = apiService, 
                    category = state.category,
                    keyword = state.keyword,
                    platform = state.platform
                ) 
            }
        ).flow.cachedIn(viewModelScope)
    }

    fun selectCategory(category: String) {
        _filterState.value = _filterState.value.copy(category = category)
    }

    fun searchDeals(keyword: String) {
        _filterState.value = _filterState.value.copy(keyword = if(keyword.isBlank()) null else keyword)
    }

    fun selectPlatform(platform: String) {
        _filterState.value = _filterState.value.copy(platform = platform)
    }
}

package com.ddaeany0919.insightdeal.feature.home

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
 * ?  ???”ë©´ ViewModel - ?¤ë°?´í„° ?°ê²° ë²„ì „
 * ëª©í‘œ: 1ì´???ì²??Œë”, ìºì‹œ ì¦‰ì‹œ ?œì‹œ, 2ì´???ìµœì‹ ??
 */
class HomeViewModel(
    private val repository: DealsRepository = RepositoryProvider.getCurrentRepository()
) : ViewModel() {

    // UI ?íƒœ
    private val _popularDeals = MutableStateFlow<Resource<List<ApiDeal>>>(Resource.Loading())
    val popularDeals: StateFlow<Resource<List<ApiDeal>>> = _popularDeals

    init {
        loadInitialFeed()
    }

    private val _searchResult = MutableStateFlow<Resource<ComparisonResponse>?>(null)
    val searchResult: StateFlow<Resource<ComparisonResponse>?> = _searchResult

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing

    // ì´ˆê¸° ë¡œë”©: ?¸ê¸° ê²€?‰ì–´ ê¸°ë°˜ ?¼ë“œ
    fun loadInitialFeed() {
        viewModelScope.launch {
            repository.getPopularDeals().catch { e ->
                _popularDeals.value = Resource.Error("?¼ë“œë¥?ë¶ˆëŸ¬?¤ì? ëª»í–ˆ?µë‹ˆ??, throwable = e)
            }.collectLatest { res ->
                _popularDeals.value = res
            }
        }
    }

    // Pull-to-Refresh: ê°•ì œ ?ˆë¡œê³ ì¹¨
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

    // ?¨ì¼ ê²€??(ê²€?‰ë°”/?ë™?„ì„± ?°ë™)
    fun search(query: String, forceRefresh: Boolean = false) {
        viewModelScope.launch {
            repository.searchDeal(query, forceRefresh).collectLatest { res ->
                _searchResult.value = res
            }
        }
    }
}

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
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import com.ddaeany0919.insightdeal.models.DealItem
import com.ddaeany0919.insightdeal.network.NetworkModule
import com.ddaeany0919.insightdeal.network.ApiService
import com.ddaeany0919.insightdeal.network.PopularKeywordRankingItem
import com.ddaeany0919.insightdeal.presentation.home.HotDealsPagingSource

import androidx.paging.ExperimentalPagingApi
import androidx.paging.filter
import androidx.paging.map
import kotlinx.coroutines.flow.map
import com.ddaeany0919.insightdeal.InsightDealApplication
import androidx.compose.foundation.lazy.LazyListState

class HomeViewModel : ViewModel() {

    // ✨ 홈 화면 스크롤 보존을 위한 LazyListState 호스팅
    val listState = LazyListState()

    private val apiService = NetworkModule.createService<ApiService>()

    private val _popularKeywords = MutableStateFlow<List<String>>(emptyList())
    val popularKeywords: StateFlow<List<String>> = _popularKeywords.asStateFlow()

    private val _popularRankings = MutableStateFlow<List<PopularKeywordRankingItem>>(emptyList())
    val popularRankings: StateFlow<List<PopularKeywordRankingItem>> = _popularRankings.asStateFlow()

    private val _popularUpdatedAt = MutableStateFlow("")
    val popularUpdatedAt: StateFlow<String> = _popularUpdatedAt.asStateFlow()
    
    private val _topHotDeals = MutableStateFlow<List<DealItem>>(emptyList())
    val topHotDeals: StateFlow<List<DealItem>> = _topHotDeals.asStateFlow()

    private val _hasNewDeals = MutableStateFlow(false)
    val hasNewDeals: StateFlow<Boolean> = _hasNewDeals.asStateFlow()

    private val _newDealsCount = MutableStateFlow(0)
    val newDealsCount: StateFlow<Int> = _newDealsCount.asStateFlow()

    private var latestKnownDealId: Int = 0
    private var dealWatcherJob: kotlinx.coroutines.Job? = null

    init {
        fetchPopularKeywords()
        fetchTopHotDeals()
        startRealtimeDealWatcher()
    }

    private fun startRealtimeDealWatcher() {
        dealWatcherJob?.cancel()
        dealWatcherJob = viewModelScope.launch {
            while (true) {
                kotlinx.coroutines.delay(50_000L) // 50초 주기 체크
                try {
                    val response = apiService.getCommunityHotDeals(limit = 10, offset = 0)
                    if (response.isSuccessful) {
                        val latestDeals = response.body()?.deals ?: emptyList()
                        if (latestDeals.isNotEmpty()) {
                            val topId = latestDeals.first().id
                            if (latestKnownDealId == 0) {
                                latestKnownDealId = topId
                            } else if (topId > latestKnownDealId) {
                                val newCount = latestDeals.count { it.id > latestKnownDealId }
                                _newDealsCount.value = newCount
                                _hasNewDeals.value = true
                            }
                        }
                    }
                } catch (e: Exception) {
                    // 백그라운드 폴링 예외 무시
                }
            }
        }
    }

    fun onRefreshNewDeals() {
        _hasNewDeals.value = false
        _newDealsCount.value = 0
        viewModelScope.launch {
            try {
                val response = apiService.getCommunityHotDeals(limit = 5, offset = 0)
                if (response.isSuccessful) {
                    val topDeal = response.body()?.deals?.firstOrNull()
                    if (topDeal != null) {
                        latestKnownDealId = topDeal.id
                    }
                }
            } catch (_: Exception) {}
        }
    }

    fun fetchPopularKeywords() {
        viewModelScope.launch {
            try {
                val response = apiService.getPopularKeywords()
                if (response.isSuccessful) {
                    val body = response.body()
                    _popularKeywords.value = body?.keywords ?: emptyList()
                    _popularRankings.value = body?.rankings ?: emptyList()
                    _popularUpdatedAt.value = body?.updatedAt ?: ""
                }
            } catch (e: Exception) {
                // Ignore failure and fallback to empty keywords
            }
        }
    }

    fun fetchTopHotDeals() {
        viewModelScope.launch {
            try {
                val response = apiService.getTopHotDeals()
                if (response.isSuccessful) {
                    val deals = response.body()?.deals ?: emptyList()
                    android.util.Log.d("HomeViewModel", "getTopHotDeals success, count: ${deals.size}")
                    _topHotDeals.value = deals
                    if (latestKnownDealId == 0 && deals.isNotEmpty()) {
                        latestKnownDealId = deals.first().id
                    }
                } else {
                    android.util.Log.e("HomeViewModel", "getTopHotDeals failed: ${response.code()}")
                }
            } catch (e: Exception) {
                android.util.Log.e("HomeViewModel", "getTopHotDeals error", e)
            }
        }
    }

    data class FilterState(
        val category: String = "전체",
        val keyword: String? = null,
        val platform: String? = null,
        val hideClosed: Boolean = false,
        val popularOnly: Boolean = false,
        val freeShippingOnly: Boolean = false
    )

    private val _filterState = MutableStateFlow(FilterState())
    val filterState: StateFlow<FilterState> = _filterState.asStateFlow()

    // ✨ Paging3 적용: 서버/DB 쿼리 조건(category, keyword, platform)이 바뀔 때만 Pager 재생성하여 플리커링 원천 차단
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class, ExperimentalPagingApi::class)
    private val basePagingData: Flow<PagingData<DealItem>> = _filterState
        .map { Triple(it.category, it.keyword, it.platform) }
        .distinctUntilChanged()
        .flatMapLatest { (category, keyword, platform) ->
            Pager(
                config = PagingConfig(
                    pageSize = 20,
                    enablePlaceholders = false,
                    initialLoadSize = 20
                ),
                remoteMediator = HotDealsRemoteMediator(
                    database = InsightDealApplication.database,
                    apiService = apiService,
                    category = category,
                    keyword = keyword,
                    platform = platform
                ),
                pagingSourceFactory = {
                    InsightDealApplication.database.dealDao().getPagedDeals(
                        category = category,
                        keyword = keyword,
                        platform = platform
                    )
                }
            ).flow.map { pagingData ->
                pagingData.map { it.toDealItem() }
            }
        }
        .cachedIn(viewModelScope)

    // 퀵 필터 칩(품절 숨기기 / 인기·역대가 / 무료배송) 클릭 시 DB/네트워크 재호출 없이 인메모리 즉시 필터링
    val dealsPagingData: Flow<PagingData<DealItem>> = kotlinx.coroutines.flow.combine(
        basePagingData,
        _filterState
    ) { pagingData, state ->
        if (!state.hideClosed && !state.popularOnly && !state.freeShippingOnly) {
            pagingData
        } else {
            pagingData.filter { item ->
                val passClosed = !state.hideClosed || (!item.isClosed && !item.title.contains("종료") && !item.title.contains("품절"))
                val passPopular = !state.popularOnly || (item.honeyScore >= 80 || item.tags.any { t -> t.contains("인기") || t.contains("역대") })
                val passFreeShip = !state.freeShippingOnly || (item.shippingFee?.contains("무료") == true || item.shippingFee?.contains("무배") == true || item.tags.any { t -> t.contains("무료배송") })
                passClosed && passPopular && passFreeShip
            }
        }
    }.cachedIn(viewModelScope)

    fun selectCategory(category: String) {
        if (_filterState.value.category != category) {
            _filterState.value = _filterState.value.copy(category = category)
        }
    }

    fun searchDeals(keyword: String) {
        val normalized = if (keyword.isBlank()) null else keyword
        if (_filterState.value.keyword != normalized) {
            _filterState.value = _filterState.value.copy(keyword = normalized)
        }
    }

    fun selectPlatform(platform: String) {
        val normalized = if (platform.isBlank() || platform == "전체") null else platform
        if (_filterState.value.platform != normalized) {
            _filterState.value = _filterState.value.copy(platform = normalized)
        }
    }

    fun toggleHideClosed() {
        _filterState.value = _filterState.value.copy(hideClosed = !_filterState.value.hideClosed)
    }

    fun togglePopularOnly() {
        _filterState.value = _filterState.value.copy(popularOnly = !_filterState.value.popularOnly)
    }

    fun toggleFreeShippingOnly() {
        _filterState.value = _filterState.value.copy(freeShippingOnly = !_filterState.value.freeShippingOnly)
    }
}

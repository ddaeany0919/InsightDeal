package com.ddaeany0919.insightdeal.presentation.home

import android.util.Log
import androidx.compose.foundation.lazy.LazyListState
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.ExperimentalPagingApi
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.cachedIn
import androidx.paging.filter
import androidx.paging.map
import com.ddaeany0919.insightdeal.data.LocalDataProvider
import com.ddaeany0919.insightdeal.models.DealItem
import com.ddaeany0919.insightdeal.network.ApiService
import com.ddaeany0919.insightdeal.network.NetworkModule
import com.ddaeany0919.insightdeal.network.PopularKeywordRankingItem
import com.ddaeany0919.insightdeal.network.TrackClickRequest
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

class HomeViewModel : ViewModel() {

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
    private var dealWatcherJob: Job? = null

    init {
        fetchPopularKeywords()
        fetchTopHotDeals()
        startRealtimeDealWatcher()
    }

    private fun startRealtimeDealWatcher() {
        dealWatcherJob?.cancel()
        dealWatcherJob = viewModelScope.launch {
            while (true) {
                delay(50_000L)
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
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {
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
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
            }
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
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
            }
        }
    }

    fun fetchTopHotDeals() {
        viewModelScope.launch {
            try {
                val response = apiService.getTopHotDeals()
                if (response.isSuccessful) {
                    val deals = response.body()?.deals ?: emptyList()
                    _topHotDeals.value = deals
                    if (latestKnownDealId == 0 && deals.isNotEmpty()) {
                        latestKnownDealId = deals.first().id
                    }
                } else {
                    Log.e("HomeViewModel", "getTopHotDeals failed: ${response.code()}")
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e("HomeViewModel", "getTopHotDeals error", e)
            }
        }
    }

    fun trackOutboundClick(
        url: String,
        price: Int? = null,
        currency: String? = "KRW"
    ) {
        if (url.isBlank()) return
        viewModelScope.launch {
            try {
                apiService.trackOutboundClick(
                    TrackClickRequest(
                        url = url,
                        price = price?.takeIf { it > 0 },
                        currency = currency ?: "KRW",
                        platform = "android"
                    )
                )
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
            }
        }
    }

    data class FilterState(
        val category: String = "전체",
        val keyword: String? = null,
        val platform: String? = null,
        val communityChip: String = "전체",
        val disabledPlatforms: Set<String> = emptySet(),
        val sortBy: String = "latest",
        val hideClosed: Boolean = false,
        val popularOnly: Boolean = false,
        val freeShippingOnly: Boolean = false
    ) {
        fun resolvedPlatformQuery(): String? {
            if (communityChip.isNotBlank() && communityChip != "전체") {
                return communityChip
            }
            if (!platform.isNullOrBlank() && platform != "전체") {
                return platform
            }
            if (disabledPlatforms.isNotEmpty()) {
                val active = ALL_COMMUNITY_PLATFORMS.filter { it !in disabledPlatforms }
                return if (active.isEmpty()) null else active.joinToString(",")
            }
            return null
        }
    }

    private data class PagerQueryKey(
        val category: String,
        val keyword: String?,
        val platform: String?,
        val sortBy: String
    )

    private val _filterState = MutableStateFlow(FilterState())
    val filterState: StateFlow<FilterState> = _filterState.asStateFlow()

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class, ExperimentalPagingApi::class)
    private val basePagingData: Flow<PagingData<DealItem>> = _filterState
        .map {
            PagerQueryKey(
                category = it.category,
                keyword = it.keyword,
                platform = it.resolvedPlatformQuery(),
                sortBy = it.sortBy
            )
        }
        .distinctUntilChanged()
        .flatMapLatest { query ->
            val db = LocalDataProvider.database
            Pager(
                config = PagingConfig(
                    pageSize = 20,
                    enablePlaceholders = false,
                    initialLoadSize = 20
                ),
                remoteMediator = HotDealsRemoteMediator(
                    database = db,
                    apiService = apiService,
                    category = query.category,
                    keyword = query.keyword,
                    platform = query.platform,
                    sort = query.sortBy
                ),
                pagingSourceFactory = {
                    db.dealDao().getPagedDeals(
                        category = query.category,
                        keyword = query.keyword,
                        platform = query.platform,
                        sort = query.sortBy
                    )
                }
            ).flow.map { pagingData ->
                pagingData.map { it.toDealItem() }
            }
        }
        .cachedIn(viewModelScope)

    val dealsPagingData: Flow<PagingData<DealItem>> = combine(
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
        val normalized = keyword.trim().takeIf { it.isNotEmpty() }
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

    fun selectCommunityChip(chip: String) {
        val normalized = if (chip.isBlank()) "전체" else chip
        if (_filterState.value.communityChip != normalized) {
            _filterState.value = _filterState.value.copy(communityChip = normalized)
        }
    }

    fun updateDisabledPlatforms(disabled: Set<String>) {
        if (_filterState.value.disabledPlatforms != disabled) {
            _filterState.value = _filterState.value.copy(disabledPlatforms = disabled)
        }
    }

    fun selectSort(sortBy: String) {
        val validSort = if (sortBy in SUPPORTED_SORTS) sortBy else "latest"
        if (_filterState.value.sortBy != validSort) {
            _filterState.value = _filterState.value.copy(sortBy = validSort)
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

    fun resetHomeFilters() {
        _filterState.value = _filterState.value.copy(
            category = "전체",
            keyword = null,
            communityChip = "전체",
            sortBy = "latest"
        )
    }

    companion object {
        val SUPPORTED_SORTS = setOf("latest", "popular", "price_asc")
        val COMMUNITY_FILTER_CHIPS = listOf(
            "전체",
            "뽐뿌",
            "펨코",
            "퀘이사존",
            "루리웹",
            "클리앙",
            "알리뽐뿌",
            "빠삭"
        )
        private val ALL_COMMUNITY_PLATFORMS = listOf(
            "뽐뿌",
            "퀘이사존",
            "펨코",
            "루리웹",
            "클리앙",
            "알리뽐뿌",
            "빠삭국내",
            "빠삭해외",
            "빠삭육아"
        )
    }
}

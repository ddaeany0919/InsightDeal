package com.ddaeany0919.insightdeal.presentation.home

import android.util.Log
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
import com.ddaeany0919.insightdeal.models.CategoryCatalog
import com.ddaeany0919.insightdeal.models.DealItem
import com.ddaeany0919.insightdeal.network.ApiService
import com.ddaeany0919.insightdeal.network.NetworkModule
import com.ddaeany0919.insightdeal.network.PopularKeywordRankingItem
import com.ddaeany0919.insightdeal.network.TrackClickRequest
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
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

class HomeViewModel @JvmOverloads constructor(
    private val apiService: ApiService = NetworkModule.createService<ApiService>(),
    private val coroutineScopeOverride: CoroutineScope? = null,
    autoInitialize: Boolean = (coroutineScopeOverride == null)
) : ViewModel() {

    private val vmScope: CoroutineScope
        get() = coroutineScopeOverride ?: viewModelScope

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
        if (autoInitialize) {
            fetchPopularKeywords()
            fetchTopHotDeals()
            startRealtimeDealWatcher()
            vmScope.launch {
                CategoryCatalog.syncWithServer(apiService)
            }
        }
    }

    private fun startRealtimeDealWatcher() {
        dealWatcherJob?.cancel()
        dealWatcherJob = vmScope.launch {
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
        vmScope.launch {
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
        vmScope.launch {
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
        vmScope.launch {
            try {
                val response = apiService.getTopHotDeals()
                if (response.isSuccessful) {
                    val deals = response.body()?.deals ?: emptyList()
                    _topHotDeals.value = deals
                    if (latestKnownDealId == 0 && deals.isNotEmpty()) {
                        latestKnownDealId = deals.first().id
                    }
                } else {
                    runCatching { Log.e("HomeViewModel", "getTopHotDeals failed: ${response.code()}") }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                runCatching { Log.e("HomeViewModel", "getTopHotDeals error", e) }
            }
        }
    }

    fun trackOutboundClick(
        url: String,
        price: Int? = null,
        currency: String? = "KRW"
    ) {
        if (url.isBlank()) return
        vmScope.launch {
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
        val selectedCategories: Set<String> = emptySet(),
        val selectedCommunityChips: Set<String> = emptySet(),
        val disabledPlatforms: Set<String> = emptySet(),
        val sortBy: String = "latest",
        val hideClosed: Boolean = false,
        val popularOnly: Boolean = false,
        val freeShippingOnly: Boolean = false
    ) {
        fun effectiveCommunityChips(): Set<String> {
            if (selectedCommunityChips.isNotEmpty()) {
                return selectedCommunityChips.map { it.trim() }.filter { it.isNotEmpty() && it != "전체" }.toSet()
            }
            if (communityChip.isNotBlank() && communityChip != "전체") {
                return communityChip.split(",")
                    .map { it.trim() }
                    .filter { it.isNotEmpty() && it != "전체" }
                    .toSet()
            }
            return emptySet()
        }

        fun effectiveCategories(): Set<String> {
            if (selectedCategories.isNotEmpty()) {
                return selectedCategories.map { it.trim() }
                    .filter { it.isNotEmpty() && it != "전체" }
                    .map { CategoryCatalog.normalizeCategoryKey(it) }
                    .toSet()
            }
            if (category.isNotBlank() && category != "전체") {
                return category.split(",")
                    .map { it.trim() }
                    .filter { it.isNotEmpty() && it != "전체" }
                    .map { CategoryCatalog.normalizeCategoryKey(it) }
                    .toSet()
            }
            return emptySet()
        }

        fun resolvedCategoryQuery(): String {
            val active = effectiveCategories()
            return if (active.isEmpty()) "전체" else active.sorted().joinToString(",")
        }

        fun resolvedPlatformQuery(): String? {
            val activeChips = effectiveCommunityChips()
            if (activeChips.isNotEmpty()) {
                return activeChips.sorted().joinToString(",")
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
        val sortBy: String,
        val hideClosed: Boolean,
        val popularOnly: Boolean
    )

    private val _filterState = MutableStateFlow(FilterState())
    val filterState: StateFlow<FilterState> = _filterState.asStateFlow()

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class, ExperimentalPagingApi::class)
    private val basePagingData: Flow<PagingData<DealItem>> = _filterState
        .map {
            PagerQueryKey(
                category = it.resolvedCategoryQuery(),
                keyword = it.keyword,
                platform = it.resolvedPlatformQuery(),
                sortBy = it.sortBy,
                hideClosed = it.hideClosed,
                popularOnly = it.popularOnly
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
                    sort = query.sortBy,
                    onlyHot = query.popularOnly,
                    hideClosed = query.hideClosed,
                    includeClosed = false
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
        .cachedIn(vmScope)

    val dealsPagingData: Flow<PagingData<DealItem>> = combine(
        basePagingData,
        _filterState
    ) { pagingData, state ->
        if (!state.hideClosed && !state.popularOnly && !state.freeShippingOnly) {
            pagingData
        } else {
            pagingData.filter { item ->
                val passClosed = !state.hideClosed || (!item.isClosed && !item.title.contains("종료") && !item.title.contains("품절"))
                val passPopular = !state.popularOnly || isPopularDeal(item)
                val passFreeShip = !state.freeShippingOnly || (item.shippingFee?.contains("무료") == true || item.shippingFee?.contains("무배") == true || item.tags.any { t -> t.contains("무료배송") })
                passClosed && passPopular && passFreeShip
            }
        }
    }.cachedIn(vmScope)

    fun selectCategory(category: String) {
        val trimmed = category.trim()
        if (trimmed.isBlank() || trimmed == "전체") {
            if (_filterState.value.category != "전체" || _filterState.value.selectedCategories.isNotEmpty()) {
                _filterState.value = _filterState.value.copy(
                    category = "전체",
                    selectedCategories = emptySet()
                )
            }
            return
        }
        val tokens = trimmed.split(",")
            .map { it.trim() }
            .filter { it.isNotEmpty() && it != "전체" }
            .map { CategoryCatalog.normalizeCategoryKey(it) }
            .toSet()
        val nextJoined = if (tokens.isEmpty()) "전체" else tokens.sorted().joinToString(",")
        if (_filterState.value.category != nextJoined || _filterState.value.selectedCategories != tokens) {
            _filterState.value = _filterState.value.copy(
                category = nextJoined,
                selectedCategories = tokens
            )
        }
    }

    fun toggleCategory(cat: String) {
        val trimmed = cat.trim()
        if (trimmed.isBlank() || trimmed == "전체") {
            if (_filterState.value.category != "전체" || _filterState.value.selectedCategories.isNotEmpty()) {
                _filterState.value = _filterState.value.copy(
                    category = "전체",
                    selectedCategories = emptySet()
                )
            }
            return
        }
        val normalized = CategoryCatalog.normalizeCategoryKey(trimmed)
        val current = _filterState.value.effectiveCategories()
        val next = if (normalized in current) current - normalized else current + normalized
        val nextJoined = if (next.isEmpty()) "전체" else next.sorted().joinToString(",")
        _filterState.value = _filterState.value.copy(
            selectedCategories = next,
            category = nextJoined
        )
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
        toggleCommunityChip(chip)
    }

    fun toggleCommunityChip(chip: String) {
        val trimmed = chip.trim()
        if (trimmed.isBlank() || trimmed == "전체") {
            if (_filterState.value.communityChip != "전체" || _filterState.value.selectedCommunityChips.isNotEmpty()) {
                _filterState.value = _filterState.value.copy(
                    communityChip = "전체",
                    selectedCommunityChips = emptySet()
                )
            }
            return
        }
        val current = _filterState.value.effectiveCommunityChips()
        val next = if (trimmed in current) current - trimmed else current + trimmed
        val nextJoined = if (next.isEmpty()) "전체" else next.sorted().joinToString(",")
        _filterState.value = _filterState.value.copy(
            selectedCommunityChips = next,
            communityChip = nextJoined
        )
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
            selectedCategories = emptySet(),
            keyword = null,
            platform = null,
            communityChip = "전체",
            selectedCommunityChips = emptySet(),
            sortBy = "latest",
            hideClosed = false,
            popularOnly = false,
            freeShippingOnly = false
        )
    }

    companion object {
        const val POPULAR_HONEY_SCORE_THRESHOLD = 100

        fun isPopularDeal(deal: DealItem): Boolean {
            return deal.honeyScore >= POPULAR_HONEY_SCORE_THRESHOLD ||
                deal.tags.any { t -> t.contains("인기") || t.contains("역대") }
        }

        val SUPPORTED_SORTS = setOf("latest", "popular", "price_asc")
        val COMMUNITY_FILTER_CHIPS = CategoryCatalog.STANDARD_SOURCES
        val ALL_COMMUNITY_PLATFORMS = CategoryCatalog.STANDARD_COMMUNITY_PLATFORMS
    }
}

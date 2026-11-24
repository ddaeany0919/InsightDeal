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
import androidx.room.withTransaction
import com.ddaeany0919.insightdeal.data.FeedPreferencesDataSource
import com.ddaeany0919.insightdeal.data.LocalDataProvider
import com.ddaeany0919.insightdeal.local.db.DealDao
import com.ddaeany0919.insightdeal.local.db.DealRemoteKeysDao
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
    autoInitialize: Boolean = (coroutineScopeOverride == null),
    private val feedPreferencesDataSource: FeedPreferencesDataSource = LocalDataProvider.feedPreferencesDataSource,
    private val dealDaoProvider: () -> DealDao = { LocalDataProvider.database.dealDao() },
    private val dealRemoteKeysDaoProvider: () -> DealRemoteKeysDao = { LocalDataProvider.database.dealRemoteKeysDao() },
    private val runInTransactionProvider: suspend (suspend () -> Unit) -> Unit = { block ->
        LocalDataProvider.database.withTransaction { block() }
    },
    private val realtimePollIntervalMs: Long = REALTIME_POLL_INTERVAL_MS
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

    private val _viewMode = MutableStateFlow(feedPreferencesDataSource.getViewMode())
    val viewMode: StateFlow<String> = _viewMode.asStateFlow()

    private val _mutedKeywords = MutableStateFlow(feedPreferencesDataSource.getMutedKeywords())
    val mutedKeywords: StateFlow<Set<String>> = _mutedKeywords.asStateFlow()

    private var latestKnownDealId: Int = 0
    private var dealWatcherJob: Job? = null

    @Volatile
    private var isForeground: Boolean = false

    init {
        if (autoInitialize) {
            fetchPopularKeywords()
            fetchTopHotDeals()
            vmScope.launch {
                CategoryCatalog.syncWithServer(apiService)
            }
        }
    }

    /**
     * 화면 포그라운드/백그라운드 진입 상태 변경 시 호출합니다.
     * 포그라운드 상태이면서 최신순("latest") 정렬일 때만 90초 주기 실시간 감시를 활성화합니다.
     */
    fun onForegroundChanged(isForeground: Boolean) {
        if (this.isForeground == isForeground) return
        this.isForeground = isForeground
        updateRealtimeDealWatcher()
    }

    private fun updateRealtimeDealWatcher() {
        val shouldWatch = isForeground && _filterState.value.sortBy == "latest"
        if (shouldWatch) {
            if (dealWatcherJob?.isActive != true) {
                startRealtimeDealWatcher()
            }
        } else {
            dealWatcherJob?.cancel()
            dealWatcherJob = null
        }
    }

    private fun startRealtimeDealWatcher() {
        dealWatcherJob?.cancel()
        dealWatcherJob = vmScope.launch {
            val intervalMs = realtimePollIntervalMs.coerceAtLeast(1L)
            while (isForeground && _filterState.value.sortBy == "latest") {
                delay(intervalMs)
                if (!isForeground || _filterState.value.sortBy != "latest") break
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
        val freeShippingOnly: Boolean = false,
        val shop: String? = null,
        val minPrice: Int? = null,
        val maxPrice: Int? = null,
        val includeClosed: Boolean = false
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

        fun resolvedShopQuery(): String? {
            return shop?.trim()?.takeIf { it.isNotEmpty() && it != "전체" }
        }

        fun resolvedMinPrice(): Int? = minPrice?.takeIf { it > 0 }

        fun resolvedMaxPrice(): Int? = maxPrice?.takeIf { it > 0 }
    }

    private data class PagerQueryKey(
        val category: String,
        val keyword: String?,
        val platform: String?,
        val sortBy: String,
        val hideClosed: Boolean,
        val includeClosed: Boolean,
        val popularOnly: Boolean,
        val freeShippingOnly: Boolean,
        val shop: String?,
        val minPrice: Int?,
        val maxPrice: Int?
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
                includeClosed = it.includeClosed,
                popularOnly = it.popularOnly,
                freeShippingOnly = it.freeShippingOnly,
                shop = it.resolvedShopQuery(),
                minPrice = it.resolvedMinPrice(),
                maxPrice = it.resolvedMaxPrice()
            )
        }
        .distinctUntilChanged()
        .flatMapLatest { query ->
            val dealDao = dealDaoProvider()
            val dealRemoteKeysDao = dealRemoteKeysDaoProvider()
            Pager(
                config = PagingConfig(
                    pageSize = 20,
                    enablePlaceholders = false,
                    initialLoadSize = 20
                ),
                remoteMediator = HotDealsRemoteMediator(
                    dealDao = dealDao,
                    dealRemoteKeysDao = dealRemoteKeysDao,
                    runInTransaction = runInTransactionProvider,
                    apiService = apiService,
                    category = query.category,
                    keyword = query.keyword,
                    platform = query.platform,
                    sort = query.sortBy,
                    onlyHot = query.popularOnly,
                    hideClosed = query.hideClosed,
                    includeClosed = query.includeClosed,
                    freeShippingOnly = query.freeShippingOnly,
                    shop = query.shop,
                    minPrice = query.minPrice,
                    maxPrice = query.maxPrice
                ),
                pagingSourceFactory = {
                    dealDao.getPagedDeals(
                        category = query.category,
                        keyword = query.keyword,
                        platform = query.platform,
                        sort = query.sortBy,
                        freeShippingOnly = query.freeShippingOnly,
                        shop = query.shop,
                        minPrice = query.minPrice,
                        maxPrice = query.maxPrice
                    )
                }
            ).flow.map { pagingData ->
                pagingData.map { it.toDealItem() }
            }
        }
        .cachedIn(vmScope)

    val dealsPagingData: Flow<PagingData<DealItem>> = combine(
        basePagingData,
        _filterState,
        _mutedKeywords
    ) { pagingData, state, muted ->
        if (!state.hideClosed && !state.popularOnly && muted.isEmpty()) {
            pagingData
        } else {
            pagingData.filter { item ->
                val passClosed = !state.hideClosed || (!item.isClosed && !item.title.contains("종료") && !item.title.contains("품절"))
                val passPopular = !state.popularOnly || isPopularDeal(item)
                val passMuted = muted.isEmpty() || !matchesMutedKeywords(item, muted)
                passClosed && passPopular && passMuted
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
            updateRealtimeDealWatcher()
        }
    }

    fun toggleHideClosed() {
        val nextHide = !_filterState.value.hideClosed
        _filterState.value = _filterState.value.copy(
            hideClosed = nextHide,
            includeClosed = if (nextHide) false else _filterState.value.includeClosed
        )
    }

    fun toggleIncludeClosed() {
        val nextInclude = !_filterState.value.includeClosed
        _filterState.value = _filterState.value.copy(
            includeClosed = nextInclude,
            hideClosed = if (nextInclude) false else _filterState.value.hideClosed
        )
    }

    fun togglePopularOnly() {
        _filterState.value = _filterState.value.copy(popularOnly = !_filterState.value.popularOnly)
    }

    fun toggleFreeShippingOnly() {
        _filterState.value = _filterState.value.copy(freeShippingOnly = !_filterState.value.freeShippingOnly)
    }

    fun setShopFilter(shop: String?) {
        val normalized = shop?.trim()?.takeIf { it.isNotEmpty() && it != "전체" }
        if (_filterState.value.shop != normalized) {
            _filterState.value = _filterState.value.copy(shop = normalized)
        }
    }

    fun setPriceRangeFilter(minPrice: Int?, maxPrice: Int?) {
        val normalizedMin = minPrice?.takeIf { it > 0 }
        val normalizedMax = maxPrice?.takeIf { it > 0 }
        if (_filterState.value.minPrice != normalizedMin || _filterState.value.maxPrice != normalizedMax) {
            _filterState.value = _filterState.value.copy(
                minPrice = normalizedMin,
                maxPrice = normalizedMax
            )
        }
    }

    fun setViewMode(mode: String) {
        feedPreferencesDataSource.saveViewMode(mode)
        _viewMode.value = feedPreferencesDataSource.getViewMode()
    }

    fun addMutedKeyword(keyword: String) {
        val updated = feedPreferencesDataSource.addMutedKeyword(keyword)
        _mutedKeywords.value = updated
    }

    fun removeMutedKeyword(keyword: String) {
        val updated = feedPreferencesDataSource.removeMutedKeyword(keyword)
        _mutedKeywords.value = updated
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
            includeClosed = false,
            popularOnly = false,
            freeShippingOnly = false,
            shop = null,
            minPrice = null,
            maxPrice = null
        )
        updateRealtimeDealWatcher()
    }

    override fun onCleared() {
        super.onCleared()
        dealWatcherJob?.cancel()
        dealWatcherJob = null
    }

    companion object {
        const val POPULAR_HONEY_SCORE_THRESHOLD = 100
        const val REALTIME_POLL_INTERVAL_MS = 90_000L

        fun isPopularDeal(deal: DealItem): Boolean {
            val safeTags = (deal.tags as List<String?>?) ?: emptyList()
            return deal.honeyScore >= POPULAR_HONEY_SCORE_THRESHOLD ||
                safeTags.any { t -> t != null && (t.contains("인기") || t.contains("역대")) }
        }

        fun matchesMutedKeywords(deal: DealItem, mutedKeywords: Set<String>): Boolean {
            if (mutedKeywords.isEmpty()) return false
            val safeTags = (deal.tags as List<String?>?) ?: emptyList()
            return mutedKeywords.any { rawKeyword ->
                val kw = rawKeyword.trim()
                if (kw.isEmpty()) {
                    false
                } else {
                    deal.title.contains(kw, ignoreCase = true) ||
                        (deal.shopName?.contains(kw, ignoreCase = true) == true) ||
                        (deal.aiSummary?.contains(kw, ignoreCase = true) == true) ||
                        safeTags.any { tag -> tag?.contains(kw, ignoreCase = true) == true }
                }
            }
        }

        val SUPPORTED_SORTS = setOf(
            "latest",
            "popular",
            "price_asc",
            "price_desc",
            "comments",
            "recommendations"
        )
        val COMMUNITY_FILTER_CHIPS = CategoryCatalog.STANDARD_SOURCES
        val ALL_COMMUNITY_PLATFORMS = CategoryCatalog.STANDARD_COMMUNITY_PLATFORMS
    }
}

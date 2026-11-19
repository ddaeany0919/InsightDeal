package com.ddaeany0919.insightdeal.presentation.search

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.cachedIn
import com.ddaeany0919.insightdeal.models.DealItem
import com.ddaeany0919.insightdeal.network.ApiService
import com.ddaeany0919.insightdeal.network.NetworkModule
import com.ddaeany0919.insightdeal.network.PopularKeywordRankingItem
import com.ddaeany0919.insightdeal.network.TrackClickRequest
import com.ddaeany0919.insightdeal.presentation.home.HotDealsPagingSource
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Immutable
data class SearchQueryState(
    val keyword: String = "",
    val includeClosed: Boolean = false,
    val sortBy: String = "latest"
)

/**
 * 검색 화면 전용 ViewModel (P1-11: 홈 피드 Room `deals` 테이블과 완전 분리)
 * - 검색 시 Room `clearAllDeals()`를 유발하지 않아 홈 피드 스크롤과 캐시를 그대로 보존합니다.
 * - UI 뷰/컨텍스트 참조 없는 순수 MVVM ViewModel입니다.
 */
class SearchViewModel(
    private val apiService: ApiService = NetworkModule.createService<ApiService>(),
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val coroutineScopeOverride: CoroutineScope? = null
) : ViewModel() {

    private val vmScope: CoroutineScope
        get() = coroutineScopeOverride ?: viewModelScope

    private val _searchState = MutableStateFlow(SearchQueryState())
    val searchState: StateFlow<SearchQueryState> = _searchState.asStateFlow()

    private val _popularKeywords = MutableStateFlow<List<String>>(emptyList())
    val popularKeywords: StateFlow<List<String>> = _popularKeywords.asStateFlow()

    private val _popularRankings = MutableStateFlow<List<PopularKeywordRankingItem>>(emptyList())
    val popularRankings: StateFlow<List<PopularKeywordRankingItem>> = _popularRankings.asStateFlow()

    private val _popularUpdatedAt = MutableStateFlow("")
    val popularUpdatedAt: StateFlow<String> = _popularUpdatedAt.asStateFlow()

    init {
        if (coroutineScopeOverride == null) {
            fetchPopularKeywords()
        }
    }

    fun fetchPopularKeywords() {
        vmScope.launch {
            try {
                val response = withContext(ioDispatcher) {
                    apiService.getPopularKeywords()
                }
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

    @OptIn(ExperimentalCoroutinesApi::class)
    val dealsPagingData: Flow<PagingData<DealItem>> = _searchState
        .flatMapLatest { query ->
            val cleanKeyword = query.keyword.trim()
            if (cleanKeyword.isEmpty()) {
                flowOf(PagingData.empty())
            } else {
                Pager(
                    config = PagingConfig(
                        pageSize = 20,
                        enablePlaceholders = false,
                        initialLoadSize = 20
                    ),
                    pagingSourceFactory = {
                        HotDealsPagingSource(
                            apiService = apiService,
                            keyword = cleanKeyword,
                            sort = query.sortBy,
                            includeClosed = query.includeClosed
                        )
                    }
                ).flow
            }
        }
        .cachedIn(vmScope)

    fun searchDeals(keyword: String) {
        val normalized = keyword.trim()
        if (_searchState.value.keyword != normalized) {
            _searchState.value = _searchState.value.copy(keyword = normalized)
        }
    }

    fun setIncludeClosed(include: Boolean) {
        if (_searchState.value.includeClosed != include) {
            _searchState.value = _searchState.value.copy(includeClosed = include)
        }
    }

    fun toggleIncludeClosed() {
        _searchState.value = _searchState.value.copy(includeClosed = !_searchState.value.includeClosed)
    }

    fun selectSort(sortBy: String) {
        val validSort = if (sortBy in setOf("latest", "popular", "price_asc")) sortBy else "latest"
        if (_searchState.value.sortBy != validSort) {
            _searchState.value = _searchState.value.copy(sortBy = validSort)
        }
    }

    fun trackOutboundClick(
        url: String,
        price: Int? = null,
        currency: String? = "KRW"
    ) {
        if (url.isBlank()) return
        vmScope.launch(ioDispatcher) {
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
}

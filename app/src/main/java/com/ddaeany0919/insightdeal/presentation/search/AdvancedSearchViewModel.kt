package com.ddaeany0919.insightdeal.presentation.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ddaeany0919.insightdeal.models.DealItem
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import com.ddaeany0919.insightdeal.models.SortOption

import com.ddaeany0919.insightdeal.network.NetworkModule
import com.ddaeany0919.insightdeal.network.ApiService

/**
 * 🔍 고급 검색 ViewModel
 */
class AdvancedSearchViewModel : ViewModel() {
    private val apiService = NetworkModule.createService<ApiService>()
    
    // 검색 쿼리
    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()
    
    // 검색 결과
    private val _searchResults = MutableStateFlow<List<DealItem>>(emptyList())
    val searchResults: StateFlow<List<DealItem>> = _searchResults.asStateFlow()
    
    // 자동완성 제안
    private val _searchSuggestions = MutableStateFlow<List<String>>(emptyList())
    val searchSuggestions: StateFlow<List<String>> = _searchSuggestions.asStateFlow()
    
    // 활성 필터
    private val _activeFilters = MutableStateFlow<Map<String, Set<String>>>(emptyMap())
    val activeFilters: StateFlow<Map<String, Set<String>>> = _activeFilters.asStateFlow()
    
    // 인기 검색어
    private val _popularKeywords = MutableStateFlow<List<String>>(emptyList())
    val popularKeywords: StateFlow<List<String>> = _popularKeywords.asStateFlow()
    
    // 로딩 상태
    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()
    
    /**
     * 🔍 검색어 변경 처리
     */
    fun onSearchQueryChanged(query: String) {
        _searchQuery.value = query
        
        // 자동완성 업데이트
        viewModelScope.launch {
            updateSuggestions(query)
        }
    }
    
    /**
     * 💡 자동완성 제안 선택
     */
    fun selectSuggestion(suggestion: String) {
        _searchQuery.value = suggestion
        _searchSuggestions.value = emptyList()
        performSearch()
    }
    
    /**
     * 🔍 검색 실행
     */
    fun performSearch() {
        viewModelScope.launch {
            _isLoading.value = true
            
            try {
                // 실제 검색 로직 (서버 API 호출)
                val results = searchDeals(
                    query = _searchQuery.value,
                    filters = _activeFilters.value
                )
                _searchResults.value = results
                
                // 검색 기록에 추가
                if (_searchQuery.value.isNotEmpty()) {
                    // SearchAutoComplete.getInstance(context).addToSearchHistory(_searchQuery.value)
                }
                
            } catch (e: Exception) {
                // 에러 처리
                _searchResults.value = emptyList()
            } finally {
                _isLoading.value = false
            }
        }
    }
    
    /**
     * 🎯 필터 토글
     */
    fun toggleFilter(category: String, option: String) {
        val currentFilters = _activeFilters.value.toMutableMap()
        val categoryFilters = currentFilters[category]?.toMutableSet() ?: mutableSetOf()
        
        if (option in categoryFilters) {
            categoryFilters.remove(option)
        } else {
            categoryFilters.add(option)
        }
        
        if (categoryFilters.isEmpty()) {
            currentFilters.remove(category)
        } else {
            currentFilters[category] = categoryFilters
        }
        
        _activeFilters.value = currentFilters
        
        // 필터 변경시 자동 재검색
        if (_searchQuery.value.isNotEmpty()) {
            performSearch()
        }
    }
    
    /**
     * 🗑️ 전체 필터 해제
     */
    fun clearAllFilters() {
        _activeFilters.value = emptyMap()
        
        // 필터 해제시 자동 재검색
        if (_searchQuery.value.isNotEmpty()) {
            performSearch()
        }
    }
    
    /**
     * 🔥 인기 검색어 로드
     */
    fun loadPopularKeywords() {
        viewModelScope.launch {
            try {
                val response = apiService.getPopularKeywords()
                if (response.isSuccessful) {
                    _popularKeywords.value = response.body()?.keywords ?: emptyList()
                } else {
                    _popularKeywords.value = emptyList()
                }
            } catch (e: Exception) {
                _popularKeywords.value = emptyList()
            }
        }
    }
    
    /**
     * 💡 자동완성 제안 업데이트
     */
    private suspend fun updateSuggestions(query: String) {
        if (query.length < 2) {
            _searchSuggestions.value = emptyList()
            return
        }
        
        try {
            // 실제로는 SearchAutoComplete.getInstance(context).getSuggestions(query)
            val suggestions = getAutoCompleteSuggestions(query)
            _searchSuggestions.value = suggestions
        } catch (e: Exception) {
            _searchSuggestions.value = emptyList()
        }
    }
    
    /**
     * 🔍 딜 검색 (실제 구현)
     */
    @Suppress("UNUSED_PARAMETER")
    private suspend fun searchDeals(
        query: String,
        filters: Map<String, Set<String>>
    ): List<DealItem> {
        // 실제 구현에서는 서버 API 호출
        // 현재는 더미 데이터 반환
        return listOf(
            DealItem(
                id = 1,
                title = "$query 검색 결과 1",
                price = 299000,
                discountRate = 30,
                imageUrl = "https://example.com/image1.jpg",
                siteName = "뽐뿌",
                postUrl = "https://example.com/deal1"
            ),
            DealItem(
                id = 2,
                title = "$query 검색 결과 2",
                price = 199000,
                discountRate = 50,
                imageUrl = "https://example.com/image2.jpg",
                siteName = "에펨코리아",
                postUrl = "https://example.com/deal2"
            )
        )
    }
    
    /**
     * 💡 자동완성 제안 가져오기
     */
    private fun getAutoCompleteSuggestions(query: String): List<String> {
        val allKeywords = listOf(
            "갤럭시 S24", "갤럭시 S24 Ultra", "갤럭시 워치",
            "아이폰 15", "아이폰 15 Pro", "아이패드",
            "맥북 에어", "맥북 프로", "에어팟 프로",
            "다이슨 헤어드라이어", "다이슨 청소기",
            "그래픽카드", "모니터", "키보드", "마우스"
        )
        
        return allKeywords.filter { keyword ->
            keyword.contains(query, ignoreCase = true)
        }.take(5)
    }
}

/**
 * 📦 검색 필터 데이터 클래스
 */
data class SearchFilters(
    val priceRanges: Set<String> = emptySet(),
    val discountRates: Set<String> = emptySet(),
    val categories: Set<String> = emptySet(),
    val sites: Set<String> = emptySet(),
    val sortBy: SortOption = SortOption.LATEST
) {
    fun isNotEmpty(): Boolean {
        return priceRanges.isNotEmpty() || discountRates.isNotEmpty() ||
               categories.isNotEmpty() || sites.isNotEmpty()
    }
}

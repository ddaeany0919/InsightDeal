package com.example.insightdeal.data

import android.content.Context
import android.content.SharedPreferences
import com.example.insightdeal.model.SearchHistoryItem
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class SearchHistoryManager private constructor(context: Context) {

    private val sharedPrefs: SharedPreferences =
        context.getSharedPreferences("search_history", Context.MODE_PRIVATE)

    private val gson = Gson()
    private val maxHistorySize = 50  // 최대 검색 기록 개수

    private val _searchHistory = MutableStateFlow<List<SearchHistoryItem>>(emptyList())
    val searchHistory: StateFlow<List<SearchHistoryItem>> = _searchHistory.asStateFlow()

    companion object {
        @Volatile
        private var INSTANCE: SearchHistoryManager? = null

        fun getInstance(context: Context): SearchHistoryManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: SearchHistoryManager(context.applicationContext).also { INSTANCE = it }
            }
        }
    }

    init {
        loadSearchHistory()
    }

    /**
     * 검색 기록 추가
     */
    fun addSearchHistory(query: String, resultCount: Int = 0) {
        if (query.isBlank()) return

        val currentHistory = _searchHistory.value.toMutableList()

        // 기존에 같은 검색어가 있으면 제거
        currentHistory.removeAll { it.query.equals(query, ignoreCase = true) }

        // 새로운 검색 기록을 맨 앞에 추가
        currentHistory.add(0, SearchHistoryItem(query, System.currentTimeMillis(), resultCount))

        // 최대 개수 제한
        if (currentHistory.size > maxHistorySize) {
            currentHistory.removeAt(currentHistory.size - 1)
        }

        _searchHistory.value = currentHistory
        saveSearchHistory()
    }

    /**
     * 특정 검색 기록 삭제
     */
    fun removeSearchHistory(query: String) {
        val currentHistory = _searchHistory.value.toMutableList()
        currentHistory.removeAll { it.query == query }
        _searchHistory.value = currentHistory
        saveSearchHistory()
    }

    /**
     * 모든 검색 기록 삭제
     */
    fun clearAllSearchHistory() {
        _searchHistory.value = emptyList()
        saveSearchHistory()
    }

    /**
     * 최근 검색어 가져오기 (제한된 개수)
     */
    fun getRecentSearches(limit: Int = 10): List<String> {
        return _searchHistory.value.take(limit).map { it.query }
    }

    /**
     * 검색어 자동완성 제안
     */
    fun getSuggestions(query: String, limit: Int = 5): List<String> {
        return if (query.isBlank()) {
            getRecentSearches(limit)
        } else {
            _searchHistory.value
                .filter { it.query.contains(query, ignoreCase = true) }
                .take(limit)
                .map { it.query }
        }
    }

    /**
     * 인기 검색어 (검색 횟수 기준)
     */
    fun getPopularSearches(limit: Int = 10): List<Pair<String, Int>> {
        return _searchHistory.value
            .groupBy { it.query }
            .mapValues { it.value.size }
            .toList()
            .sortedByDescending { it.second }
            .take(limit)
    }

    private fun loadSearchHistory() {
        val historyJson = sharedPrefs.getString("search_history_list", "[]")
        val type = object : TypeToken<List<SearchHistoryItem>>() {}.type
        try {
            val history: List<SearchHistoryItem> = gson.fromJson(historyJson, type) ?: emptyList()
            _searchHistory.value = history.sortedByDescending { it.searchedAt }
        } catch (e: Exception) {
            _searchHistory.value = emptyList()
        }
    }

    private fun saveSearchHistory() {
        val historyJson = gson.toJson(_searchHistory.value)
        sharedPrefs.edit().putString("search_history_list", historyJson).apply()
    }
}

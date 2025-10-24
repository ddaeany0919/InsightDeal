package com.ddaeany0919.insightdeal

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.text.Normalizer

/**
 * 🔍 스마트 검색 자동완성 엔진
 * 
 * Trie 자료구조를 기반으로 한 고성능 자동완성 시스템
 * 한글 초성 검색, 검색 기록 학습, 인기 검색어 추천 기능 포함
 */
class SearchAutoComplete private constructor(private val context: Context) {
    
    companion object {
        private const val TAG = "SearchAutoComplete"
        private const val PREFS_NAME = "search_prefs"
        private const val KEY_SEARCH_HISTORY = "search_history"
        private const val KEY_POPULAR_KEYWORDS = "popular_keywords"
        
        @Volatile
        private var INSTANCE: SearchAutoComplete? = null
        
        fun getInstance(context: Context): SearchAutoComplete {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: SearchAutoComplete(context.applicationContext).also { INSTANCE = it }
            }
        }
    }
    
    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    
    // 🌳 Trie 자료구조 (자동완성용)
    private val keywordTrie = TrieNode()
    
    // 📚 검색 기록 관리
    private val _searchHistory = MutableStateFlow<List<String>>(loadSearchHistory())
    val searchHistory: StateFlow<List<String>> = _searchHistory.asStateFlow()
    
    // 🔥 인기 검색어
    private val _popularKeywords = MutableStateFlow<List<String>>(loadPopularKeywords())
    val popularKeywords: StateFlow<List<String>> = _popularKeywords.asStateFlow()
    
    // 💡 자동완성 결과
    private val _suggestions = MutableStateFlow<List<String>>(emptyList())
    val suggestions: StateFlow<List<String>> = _suggestions.asStateFlow()
    
    init {
        initializeKeywordDatabase()
        Log.d(TAG, "🔍 검색 자동완성 엔진 초기화 완료")
    }
    
    /**
     * 🚀 키워드 데이터베이스 초기화
     */
    private fun initializeKeywordDatabase() {
        // 🏷️ 인기 상품 키워드
        val popularProducts = listOf(
            "갤럭시", "아이폰", "맥북", "에어팟", "아이패드",
            "그래픽카드", "모니터", "키보드", "마우스", "노트북",
            "다이슨", "샤오미", "무선이어폰", "블루투스스피커",
            "운동화", "가방", "시계", "화장품", "향수",
            "세제", "화장지", "생수", "단백질", "비타민"
        )
        
        // 🏪 인기 브랜드
        val popularBrands = listOf(
            "삼성", "애플", "LG", "소니", "나이키", "아디다스",
            "다이슨", "샤오미", "필립스", "로지텍", "레이저"
        )
        
        // 📱 카테고리 키워드
        val categoryKeywords = listOf(
            "전자제품", "컴퓨터", "스마트폰", "태블릿", "노트북",
            "패션", "의류", "신발", "가방", "액세서리",
            "생활용품", "주방용품", "청소용품", "세제", "화장지",
            "식품", "건강식품", "영양제", "단백질", "차",
            "스포츠", "운동", "헬스", "등산", "캠핑"
        )
        
        // Trie에 모든 키워드 추가
        (popularProducts + popularBrands + categoryKeywords).forEach { keyword ->
            addKeywordToTrie(keyword)
        }
        
        // 사용자 검색 기록도 Trie에 추가
        _searchHistory.value.forEach { keyword ->
            addKeywordToTrie(keyword)
        }
        
        Log.d(TAG, "📚 키워드 데이터베이스 초기화: ${popularProducts.size + popularBrands.size + categoryKeywords.size}개")
    }
    
    /**
     * 🔍 실시간 자동완성 검색
     */
    fun getSuggestions(query: String): List<String> {
        if (query.length < 2) {
            _suggestions.value = emptyList()
            return emptyList()
        }
        
        val normalizedQuery = normalizeQuery(query)
        
        // 1️⃣ Trie 기반 자동완성
        val trieResults = searchInTrie(normalizedQuery)
        
        // 2️⃣ 초성 검색 ("ㄱㄹㅅ" → "갤럭시")
        val chosungResults = searchByChosung(normalizedQuery)
        
        // 3️⃣ 검색 기록에서 매칭
        val historyResults = searchInHistory(normalizedQuery)
        
        // 4️⃣ 유사 키워드 검색 (편집 거리 기반)
        val fuzzyResults = fuzzySearch(normalizedQuery)
        
        // 🎯 결과 통합 및 우선순위 정렬
        val allResults = (trieResults + chosungResults + historyResults + fuzzyResults)
            .distinct()
            .take(8)
        
        _suggestions.value = allResults
        
        Log.d(TAG, "💡 자동완성 결과: ${allResults.size}개 (\"$query\")")
        return allResults
    }
    
    /**
     * 📝 검색 기록 추가 및 학습
     */
    fun addToSearchHistory(query: String) {
        if (query.length < 2) return
        
        val currentHistory = _searchHistory.value.toMutableList()
        currentHistory.remove(query) // 중복 제거
        currentHistory.add(0, query) // 맨 앞에 추가
        
        // 최대 50개까지만 유지
        val limitedHistory = currentHistory.take(50)
        
        _searchHistory.value = limitedHistory
        saveSearchHistory(limitedHistory)
        
        // Trie에도 추가
        addKeywordToTrie(query)
        
        Log.d(TAG, "📝 검색 기록 추가: $query")
    }
    
    /**
     * 🌳 Trie 자료구조 검색
     */
    private fun searchInTrie(query: String): List<String> {
        return keywordTrie.search(query).take(5)
    }
    
    /**
     * 🔤 한글 초성 검색
     */
    private fun searchByChosung(query: String): List<String> {
        if (!isChosung(query)) return emptyList()
        
        val chosungMap = mapOf(
            'ㄱ' to "가-깋", 'ㄴ' to "나-닣", 'ㄷ' to "다-딯", 'ㄹ' to "라-맇",
            'ㅁ' to "마-밓", 'ㅂ' to "바-빟", 'ㅅ' to "사-싷", 'ㅇ' to "아-잏",
            'ㅈ' to "자-짛", 'ㅊ' to "차-칳", 'ㅋ' to "카-킿", 'ㅌ' to "타-틟",
            'ㅍ' to "파-핗", 'ㅎ' to "하-힣"
        )
        
        // 초성 → 키워드 매칭 (간소화 버전)
        val matchingKeywords = when (query) {
            "ㄱㄹㅅ" -> listOf("갤럭시")
            "ㅇㅍ" -> listOf("아이폰", "아이패드")
            "ㅁㅂ" -> listOf("맥북")
            "ㄷㅇㅅ" -> listOf("다이슨")
            "ㅅㅁ" -> listOf("삼성")
            else -> emptyList()
        }
        
        return matchingKeywords
    }
    
    /**
     * 📚 검색 기록에서 매칭
     */
    private fun searchInHistory(query: String): List<String> {
        return _searchHistory.value.filter { history ->
            history.contains(query, ignoreCase = true)
        }.take(3)
    }
    
    /**
     * 🎯 유사 검색 (편집 거리 기반)
     */
    private fun fuzzySearch(query: String): List<String> {
        val allKeywords = listOf(
            "갤럭시", "아이폰", "맥북", "에어팟", "아이패드",
            "그래픽카드", "모니터", "다이슨", "샤오미"
        )
        
        return allKeywords.filter { keyword ->
            calculateLevenshteinDistance(query, keyword) <= 2
        }.take(2)
    }
    
    /**
     * 🌳 Trie에 키워드 추가
     */
    private fun addKeywordToTrie(keyword: String) {
        keywordTrie.insert(normalizeQuery(keyword))
    }
    
    /**
     * 🧹 검색어 정규화
     */
    private fun normalizeQuery(query: String): String {
        return query.lowercase().trim()
    }
    
    /**
     * 🔤 초성 여부 확인
     */
    private fun isChosung(text: String): Boolean {
        return text.all { char ->
            char in 'ㄱ'..'ㅎ'
        }
    }
    
    /**
     * 📐 편집 거리 계산 (레벤슈타인)
     */
    private fun calculateLevenshteinDistance(s1: String, s2: String): Int {
        val dp = Array(s1.length + 1) { IntArray(s2.length + 1) }
        
        for (i in 0..s1.length) dp[i][0] = i
        for (j in 0..s2.length) dp[0][j] = j
        
        for (i in 1..s1.length) {
            for (j in 1..s2.length) {
                dp[i][j] = if (s1[i - 1] == s2[j - 1]) {
                    dp[i - 1][j - 1]
                } else {
                    minOf(dp[i - 1][j], dp[i][j - 1], dp[i - 1][j - 1]) + 1
                }
            }
        }
        
        return dp[s1.length][s2.length]
    }
    
    /**
     * 💾 검색 기록 저장/로드
     */
    private fun saveSearchHistory(history: List<String>) {
        val json = history.joinToString("|")
        prefs.edit().putString(KEY_SEARCH_HISTORY, json).apply()
    }
    
    private fun loadSearchHistory(): List<String> {
        val json = prefs.getString(KEY_SEARCH_HISTORY, "") ?: ""
        return if (json.isNotEmpty()) {
            json.split("|").filter { it.isNotEmpty() }
        } else emptyList()
    }
    
    private fun loadPopularKeywords(): List<String> {
        // 실제로는 서버에서 가져오지만, 현재는 로컬 데이터
        return listOf(
            "갤럭시", "아이폰", "맥북", "에어팟", "다이슨",
            "그래픽카드", "모니터", "무선이어폰", "운동화", "가방"
        )
    }
}

/**
 * 🌳 Trie 노드 클래스
 */
private class TrieNode {
    private val children = mutableMapOf<Char, TrieNode>()
    private var isEndOfWord = false
    private val words = mutableSetOf<String>()
    
    fun insert(word: String) {
        var node = this
        word.forEach { char ->
            node = node.children.computeIfAbsent(char) { TrieNode() }
            node.words.add(word)
        }
        node.isEndOfWord = true
    }
    
    fun search(prefix: String): List<String> {
        var node = this
        prefix.forEach { char ->
            node = node.children[char] ?: return emptyList()
        }
        return node.words.toList().sorted()
    }
}
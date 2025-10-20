package com.example.insightdeal.data

import android.content.Context
import android.content.SharedPreferences
import com.example.insightdeal.model.BookmarkItem
import com.example.insightdeal.model.DealItem
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class BookmarkManager private constructor(context: Context) {

    private val sharedPrefs: SharedPreferences =
        context.getSharedPreferences("bookmarks", Context.MODE_PRIVATE)

    private val gson = Gson()

    private val _bookmarks = MutableStateFlow<List<BookmarkItem>>(emptyList())
    val bookmarks: StateFlow<List<BookmarkItem>> = _bookmarks.asStateFlow()

    companion object {
        @Volatile
        private var INSTANCE: BookmarkManager? = null

        fun getInstance(context: Context): BookmarkManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: BookmarkManager(context.applicationContext).also { INSTANCE = it }
            }
        }
    }

    init {
        loadBookmarks()
    }

    /**
     * 북마크 추가
     */
    fun addBookmark(dealItem: DealItem) {
        val currentBookmarks = _bookmarks.value.toMutableList()
        val bookmarkItem = BookmarkItem.fromDealItem(dealItem)

        // 이미 북마크된 아이템인지 확인
        if (!currentBookmarks.any { it.id == dealItem.id }) {
            currentBookmarks.add(0, bookmarkItem)  // 최신 순으로 추가
            _bookmarks.value = currentBookmarks
            saveBookmarks()
        }
    }

    /**
     * 북마크 제거
     */
    fun removeBookmark(dealId: Int) {
        val currentBookmarks = _bookmarks.value.toMutableList()
        currentBookmarks.removeAll { it.id == dealId }
        _bookmarks.value = currentBookmarks
        saveBookmarks()
    }

    /**
     * 북마크 여부 확인
     */
    fun isBookmarked(dealId: Int): Boolean {
        return _bookmarks.value.any { it.id == dealId }
    }

    /**
     * 북마크 토글
     */
    fun toggleBookmark(dealItem: DealItem): Boolean {
        return if (isBookmarked(dealItem.id)) {
            removeBookmark(dealItem.id)
            false
        } else {
            addBookmark(dealItem)
            true
        }
    }

    /**
     * 카테고리별 북마크 필터링
     */
    fun getBookmarksByCategory(category: String): List<BookmarkItem> {
        return if (category == "전체") {
            _bookmarks.value
        } else {
            _bookmarks.value.filter { it.category == category }
        }
    }

    /**
     * 검색어로 북마크 필터링
     */
    fun searchBookmarks(query: String): List<BookmarkItem> {
        return if (query.isBlank()) {
            _bookmarks.value
        } else {
            _bookmarks.value.filter { bookmark ->
                bookmark.title.contains(query, ignoreCase = true) ||
                        bookmark.shopName.contains(query, ignoreCase = true) ||
                        bookmark.community.contains(query, ignoreCase = true)
            }
        }
    }

    /**
     * 북마크 개수
     */
    fun getBookmarkCount(): Int = _bookmarks.value.size

    /**
     * 모든 북마크 삭제
     */
    fun clearAllBookmarks() {
        _bookmarks.value = emptyList()
        saveBookmarks()
    }

    private fun loadBookmarks() {
        val bookmarksJson = sharedPrefs.getString("bookmarks_list", "[]")
        val type = object : TypeToken<List<BookmarkItem>>() {}.type
        try {
            val bookmarks: List<BookmarkItem> = gson.fromJson(bookmarksJson, type) ?: emptyList()
            _bookmarks.value = bookmarks.sortedByDescending { it.bookmarkedAt }
        } catch (e: Exception) {
            _bookmarks.value = emptyList()
        }
    }

    private fun saveBookmarks() {
        val bookmarksJson = gson.toJson(_bookmarks.value)
        sharedPrefs.edit().putString("bookmarks_list", bookmarksJson).apply()
    }
}

package com.ddaeany0919.insightdeal.domain

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.ddaeany0919.insightdeal.models.DealItem
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.text.SimpleDateFormat
import java.util.*
import kotlin.collections.HashMap

/**
 * 🔖 스마트 북마크 관리자
 *
 * 로컬 저장 + 클라우드 동기화, 태그 시스템, AI 추천을 통합한
 * 지능형 북마크 관리 시스템
 */
class BookmarkManager private constructor(private val context: Context) {

    companion object {
        private const val TAG = "BookmarkManager"
        private const val PREFS_NAME = "bookmark_prefs"
        private const val KEY_BOOKMARKS = "bookmarks"
        private const val KEY_TAGS = "tags"
        private const val KEY_SETTINGS = "bookmark_settings"

        @Volatile
        private var INSTANCE: BookmarkManager? = null

        fun getInstance(context: Context): BookmarkManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: BookmarkManager(context.applicationContext).also { INSTANCE = it }
            }
        }
    }

    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    // 북마크 상태 관리
    private val _bookmarks = MutableStateFlow<List<BookmarkItem>>(loadBookmarks())
    val bookmarks: StateFlow<List<BookmarkItem>> = _bookmarks.asStateFlow()

    private val _tags = MutableStateFlow<List<BookmarkTag>>(loadTags())
    val tags: StateFlow<List<BookmarkTag>> = _tags.asStateFlow()

    private val _bookmarkStats = MutableStateFlow(calculateBookmarkStats())
    val bookmarkStats: StateFlow<BookmarkStats> = _bookmarkStats.asStateFlow()

    init {
        initializeDefaultTags()
        Log.d(TAG, "🔖 북마크 관리자 초기화: ${_bookmarks.value.size}개 북마크, ${_tags.value.size}개 태그")
    }

    /**
     * ⭐ 북마크 추가
     */
    fun addBookmark(
        deal: DealItem,
        tags: Set<String> = emptySet(),
        note: String = ""
    ): Boolean {
        val existingBookmarks = _bookmarks.value.toMutableList()

        // 중복 체크
        if (existingBookmarks.any { it.dealId == deal.id }) {
            Log.w(TAG, "⚠️ 이미 북마크된 상품: ${deal.title}")
            return false
        }

        // AI 자동 태그 추천
        val autoTags = generateAutoTags(deal)
        val finalTags = (tags + autoTags).take(5).toSet() // 최대 5개 태그

        val bookmark = BookmarkItem(
            id = generateBookmarkId(),
            dealId = deal.id,
            title = deal.title,
            originalPrice = deal.price,
            currentPrice = deal.price,
            imageUrl = deal.imageUrl ?: "",
            siteName = deal.siteName,
            url = deal.postUrl ?: deal.ecommerceUrl ?: "",
            tags = finalTags,
            note = note,
            createdAt = System.currentTimeMillis(),
            isActive = true
        )

        existingBookmarks.add(0, bookmark) // 최신 순으로 추가
        _bookmarks.value = existingBookmarks
        saveBookmarks(existingBookmarks)

        // 태그 사용 횟수 업데이트
        updateTagUsage(finalTags)
        updateBookmarkStats()

        Log.d(TAG, "⭐ 북마크 추가: ${deal.title} (태그: ${finalTags.joinToString(", ")})")
        return true
    }

    /**
     * 🗑️ 북마크 제거
     */
    fun removeBookmark(bookmarkId: String): Boolean {
        val existingBookmarks = _bookmarks.value.toMutableList()
        val removed = existingBookmarks.removeAll { it.id == bookmarkId }

        if (removed) {
            _bookmarks.value = existingBookmarks
            saveBookmarks(existingBookmarks)
            updateBookmarkStats()
            Log.d(TAG, "🗑️ 북마크 제거: $bookmarkId")
        }

        return removed
    }

    /**
     * 🏷️ 북마크 태그 업데이트
     */
    fun updateBookmarkTags(bookmarkId: String, newTags: Set<String>) {
        val existingBookmarks = _bookmarks.value.toMutableList()
        val index = existingBookmarks.indexOfFirst { it.id == bookmarkId }

        if (index != -1) {
            existingBookmarks[index] = existingBookmarks[index].copy(
                tags = newTags,
                updatedAt = System.currentTimeMillis()
            )

            _bookmarks.value = existingBookmarks
            saveBookmarks(existingBookmarks)
            updateTagUsage(newTags)

            Log.d(TAG, "🏷️ 북마크 태그 업데이트: $bookmarkId -> ${newTags.joinToString(", ")}")
        }
    }

    /**
     * 💰 가격 업데이트 (백그라운드에서 주기적 실행)
     */
    fun updateBookmarkPrices(updatedDeals: List<DealItem>) {
        val existingBookmarks = _bookmarks.value.toMutableList()
        var hasChanges = false

        updatedDeals.forEach { deal ->
            val index = existingBookmarks.indexOfFirst { it.dealId == deal.id }
            if (index != -1) {
                val bookmark = existingBookmarks[index]
                val newPrice = deal.price

                if (newPrice != bookmark.currentPrice) {
                    existingBookmarks[index] = bookmark.copy(
                        currentPrice = newPrice,
                        priceHistory = bookmark.priceHistory + PricePoint(
                            price = newPrice,
                            timestamp = System.currentTimeMillis()
                        ),
                        updatedAt = System.currentTimeMillis()
                    )
                    hasChanges = true

                    // 가격 하락 알림 (원래 가격보다 낮아진 경우)
                    if (newPrice < bookmark.originalPrice) {
                        // NotificationService.sendPriceDropAlert(bookmark, newPrice)
                        Log.d(TAG, "💸 가격 하락 알림: ${bookmark.title} ${bookmark.originalPrice}원 -> ${newPrice}원")
                    }
                }
            }
        }

        if (hasChanges) {
            _bookmarks.value = existingBookmarks
            saveBookmarks(existingBookmarks)
        }
    }

    /**
     * 🔍 북마크 검색 및 필터링
     */
    fun searchBookmarks(
        query: String = "",
        tags: Set<String> = emptySet(),
        sortBy: BookmarkSortBy = BookmarkSortBy.DATE_DESC,
        activeOnly: Boolean = true
    ): List<BookmarkItem> {
        var filtered = _bookmarks.value

        // 활성 북마크만 필터링
        if (activeOnly) {
            filtered = filtered.filter { it.isActive }
        }

        // 검색어 필터링
        if (query.isNotEmpty()) {
            filtered = filtered.filter {
                it.title.contains(query, ignoreCase = true) ||
                it.note.contains(query, ignoreCase = true)
            }
        }

        // 태그 필터링
        if (tags.isNotEmpty()) {
            filtered = filtered.filter { bookmark ->
                tags.any { tag -> bookmark.tags.contains(tag) }
            }
        }

        // 정렬
        return when (sortBy) {
            BookmarkSortBy.DATE_DESC -> filtered.sortedByDescending { it.createdAt }
            BookmarkSortBy.DATE_ASC -> filtered.sortedBy { it.createdAt }
            BookmarkSortBy.PRICE_LOW -> filtered.sortedBy { it.currentPrice }
            BookmarkSortBy.PRICE_HIGH -> filtered.sortedByDescending { it.currentPrice }
            BookmarkSortBy.TITLE -> filtered.sortedBy { it.title }
            BookmarkSortBy.SITE -> filtered.sortedBy { it.siteName }
        }
    }

    /**
     * 🧹 자동 북마크 정리
     */
    fun cleanupExpiredBookmarks(): Int {
        val existingBookmarks = _bookmarks.value.toMutableList()
        val thirtyDaysAgo = System.currentTimeMillis() - (30 * 24 * 60 * 60 * 1000L)

        var cleanedCount = 0

        existingBookmarks.forEachIndexed { index, bookmark ->
            // 30일 이상 된 북마크는 비활성화
            if (bookmark.createdAt < thirtyDaysAgo && bookmark.isActive) {
                existingBookmarks[index] = bookmark.copy(
                    isActive = false,
                    updatedAt = System.currentTimeMillis()
                )
                cleanedCount++
            }
        }

        if (cleanedCount > 0) {
            _bookmarks.value = existingBookmarks
            saveBookmarks(existingBookmarks)
            updateBookmarkStats()
            Log.d(TAG, "🧹 자동 정리 완료: ${cleanedCount}개 북마크 비활성화")
        }

        return cleanedCount
    }

    /**
     * 🤖 AI 기반 유사 상품 추천
     */
    fun getSimilarProductRecommendations(bookmarkId: String): List<DealItem> {
        val bookmark = _bookmarks.value.find { it.id == bookmarkId } ?: return emptyList()

        // 같은 태그를 가진 다른 북마크들을 기반으로 추천
        val relatedBookmarks = _bookmarks.value.filter { other ->
            other.id != bookmarkId &&
            other.tags.any { tag -> bookmark.tags.contains(tag) }
        }

        // 실제로는 서버 API를 통해 유사 상품을 가져오지만,
        // 현재는 관련 북마크 기반으로 간단히 구현
        Log.d(TAG, "🤖 유사 상품 추천: ${bookmark.title} 기반으로 ${relatedBookmarks.size}개 발견")

        return emptyList() // 실제 구현에서는 API 호출 결과 반환
    }

    /**
     * 🏷️ 태그 관리
     */
    fun createCustomTag(name: String, color: String = "#2196F3"): Boolean {
        val existingTags = _tags.value.toMutableList()

        if (existingTags.any { it.name.equals(name, ignoreCase = true) }) {
            Log.w(TAG, "⚠️ 이미 존재하는 태그: $name")
            return false
        }

        val newTag = BookmarkTag(
            id = generateTagId(),
            name = name,
            color = color,
            isCustom = true,
            usageCount = 0,
            createdAt = System.currentTimeMillis()
        )

        existingTags.add(newTag)
        _tags.value = existingTags
        saveTags(existingTags)

        Log.d(TAG, "🏷️ 커스텀 태그 생성: $name")
        return true
    }

    /**
     * 📤 북마크 백업 (JSON 형태)
     */
    fun exportBookmarks(): String {
        val backupData = BookmarkBackup(
            version = "1.0",
            exportDate = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date()),
            bookmarks = _bookmarks.value,
            tags = _tags.value
        )

        return com.google.gson.Gson().toJson(backupData)
    }

    /**
     * 📥 북마크 복원
     */
    fun importBookmarks(jsonData: String): Boolean {
        return try {
            val backupData = com.google.gson.Gson().fromJson(jsonData, BookmarkBackup::class.java)

            // 기존 데이터와 병합
            val existingBookmarks = _bookmarks.value.toMutableList()
            val existingTags = _tags.value.toMutableList()

            // 중복되지 않는 북마크만 추가
            backupData.bookmarks.forEach { importedBookmark ->
                if (existingBookmarks.none { it.dealId == importedBookmark.dealId }) {
                    existingBookmarks.add(importedBookmark.copy(
                        id = generateBookmarkId() // 새로운 ID 생성
                    ))
                }
            }

            // 중복되지 않는 태그만 추가
            backupData.tags.forEach { importedTag ->
                if (existingTags.none { it.name.equals(importedTag.name, ignoreCase = true) }) {
                    existingTags.add(importedTag.copy(
                        id = generateTagId() // 새로운 ID 생성
                    ))
                }
            }

            _bookmarks.value = existingBookmarks
            _tags.value = existingTags

            saveBookmarks(existingBookmarks)
            saveTags(existingTags)
            updateBookmarkStats()

            Log.d(TAG, "📥 북마크 복원 완료: ${backupData.bookmarks.size}개 북마크")
            true
        } catch (e: Exception) {
            Log.e(TAG, "❌ 북마크 복원 실패: ${e.message}")
            false
        }
    }

    // Private 헬퍼 메소드들

    private fun initializeDefaultTags() {
        val existingTags = _tags.value
        if (existingTags.isEmpty()) {
            val defaultTags = listOf(
                BookmarkTag("tag_1", "IT", "#2196F3", false, 0),
                BookmarkTag("tag_2", "패션", "#E91E63", false, 0),
                BookmarkTag("tag_3", "생활", "#4CAF50", false, 0),
                BookmarkTag("tag_4", "식품", "#FF9800", false, 0),
                BookmarkTag("tag_5", "해외직구", "#9C27B0", false, 0),
                BookmarkTag("tag_6", "스포츠", "#FF5722", false, 0)
            )

            _tags.value = defaultTags
            saveTags(defaultTags)
        }
    }

    private fun generateAutoTags(deal: DealItem): Set<String> {
        val title = deal.title.lowercase()
        val tags = mutableSetOf<String>()

        // 카테고리 기반 자동 태그
        when {
            title.containsAny(listOf("갤럭시", "아이폰", "맥북", "그래픽카드", "모니터", "키보드", "마우스", "노트북")) -> tags.add("IT")
            title.containsAny(listOf("옷", "신발", "가방", "화장품", "향수", "시계", "액세서리")) -> tags.add("패션")
            title.containsAny(listOf("생활용품", "주방", "청소", "세제", "화장지", "욕실", "침구")) -> tags.add("생활")
            title.containsAny(listOf("식품", "음식", "건강", "영양제", "단백질", "차", "커피")) -> tags.add("식품")
            title.containsAny(listOf("운동", "스포츠", "헬스", "등산", "캠핑", "낚시", "자전거")) -> tags.add("스포츠")
            title.containsAny(listOf("아마존", "알리", "직구", "해외")) -> tags.add("해외직구")
        }

        // 가격 기반 태그
        val price = deal.price
        when {
            price <= 10000 -> tags.add("1만원이하")
            price <= 50000 -> tags.add("5만원이하")
            price <= 100000 -> tags.add("10만원이하")
            price > 100000 -> tags.add("고가상품")
        }

        return tags.take(3).toSet() // 최대 3개 자동 태그
    }

    private fun updateTagUsage(usedTags: Set<String>) {
        val currentTags = _tags.value.toMutableList()
        var hasChanges = false

        usedTags.forEach { tagName ->
            val index = currentTags.indexOfFirst { it.name == tagName }
            if (index != -1) {
                currentTags[index] = currentTags[index].copy(
                    usageCount = currentTags[index].usageCount + 1
                )
                hasChanges = true
            }
        }

        if (hasChanges) {
            _tags.value = currentTags
            saveTags(currentTags)
        }
    }

    private fun updateBookmarkStats() {
        _bookmarkStats.value = calculateBookmarkStats()
    }

    private fun calculateBookmarkStats(): BookmarkStats {
        val bookmarks = _bookmarks.value
        val activeBookmarks = bookmarks.filter { it.isActive }
        
        val totalPrice = activeBookmarks.sumOf { it.currentPrice.toLong() }
        val totalSavings = activeBookmarks.sumOf { 
            if (it.currentPrice < it.originalPrice) 
                (it.originalPrice - it.currentPrice).toLong() 
            else 0L 
        }

        return BookmarkStats(
            totalCount = activeBookmarks.size,
            totalPrice = totalPrice,
            totalSavings = totalSavings,
            mostUsedTag = _tags.value.maxByOrNull { it.usageCount }?.name ?: "없음"
        )
    }

    // 로컬 저장소 관련 (SharedPreferences 사용)
    // 실제 프로덕션에서는 Room Database 사용 권장

    private fun loadBookmarks(): List<BookmarkItem> {
        val json = prefs.getString(KEY_BOOKMARKS, null) ?: return emptyList()
        return try {
            val type = object : com.google.gson.reflect.TypeToken<List<BookmarkItem>>() {}.type
            com.google.gson.Gson().fromJson(json, type)
        } catch (e: Exception) {
            Log.e(TAG, "❌ 북마크 로드 실패", e)
            emptyList()
        }
    }

    private fun saveBookmarks(bookmarks: List<BookmarkItem>) {
        val json = com.google.gson.Gson().toJson(bookmarks)
        prefs.edit().putString(KEY_BOOKMARKS, json).apply()
    }

    private fun loadTags(): List<BookmarkTag> {
        val json = prefs.getString(KEY_TAGS, null) ?: return emptyList()
        return try {
            val type = object : com.google.gson.reflect.TypeToken<List<BookmarkTag>>() {}.type
            com.google.gson.Gson().fromJson(json, type)
        } catch (e: Exception) {
            Log.e(TAG, "❌ 태그 로드 실패", e)
            emptyList()
        }
    }

    private fun saveTags(tags: List<BookmarkTag>) {
        val json = com.google.gson.Gson().toJson(tags)
        prefs.edit().putString(KEY_TAGS, json).apply()
    }

    private fun generateBookmarkId(): String = UUID.randomUUID().toString()
    private fun generateTagId(): String = UUID.randomUUID().toString()

    private fun String.containsAny(keywords: List<String>): Boolean {
        return keywords.any { this.contains(it, ignoreCase = true) }
    }
}

// 데이터 모델들
data class BookmarkItem(
    val id: String,
    val dealId: Int,
    val title: String,
    val originalPrice: Int,
    val currentPrice: Int,
    val imageUrl: String,
    val siteName: String,
    val url: String,
    val tags: Set<String>,
    val note: String,
    val createdAt: Long,
    val updatedAt: Long = createdAt,
    val isActive: Boolean = true,
    val priceHistory: List<PricePoint> = emptyList()
)

data class BookmarkTag(
    val id: String,
    val name: String,
    val color: String,
    val isCustom: Boolean,
    val usageCount: Int,
    val createdAt: Long = System.currentTimeMillis()
)

data class PricePoint(
    val price: Int,
    val timestamp: Long
)

data class BookmarkStats(
    val totalCount: Int,
    val totalPrice: Long,
    val totalSavings: Long,
    val mostUsedTag: String
)

data class BookmarkBackup(
    val version: String,
    val exportDate: String,
    val bookmarks: List<BookmarkItem>,
    val tags: List<BookmarkTag>
)

enum class BookmarkSortBy {
    DATE_DESC,
    DATE_ASC,
    PRICE_LOW,
    PRICE_HIGH,
    TITLE,
    SITE
}
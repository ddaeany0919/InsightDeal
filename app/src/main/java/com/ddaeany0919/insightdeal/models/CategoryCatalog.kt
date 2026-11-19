package com.ddaeany0919.insightdeal.models

import com.ddaeany0919.insightdeal.network.ApiService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 15종 표준 카테고리 및 10종 표준 출처 SSOT 카탈로그 (`backend/core/categories.py`와 1:1 정합)
 */
data class StandardCategory(
    val key: String,
    val label: String
)

object CategoryCatalog {

    val STANDARD_CATEGORIES: List<StandardCategory> = listOf(
        StandardCategory("음식", "식품/건강"),
        StandardCategory("SW/게임", "SW/게임"),
        StandardCategory("PC용품", "PC/하드웨어"),
        StandardCategory("가전제품", "가전/TV"),
        StandardCategory("생활용품", "생활용품"),
        StandardCategory("패션", "의류/패션"),
        StandardCategory("뷰티", "뷰티"),
        StandardCategory("모바일/기프티콘", "모바일/기프티콘"),
        StandardCategory("상품권", "상품권"),
        StandardCategory("적립", "적립/포인트"),
        StandardCategory("이벤트", "이벤트"),
        StandardCategory("육아/유아용품", "육아/유아용품"),
        StandardCategory("해외직구", "해외직구"),
        StandardCategory("핫딜모음", "핫딜모음"),
        StandardCategory("기타", "기타")
    )

    val STANDARD_SOURCES: List<String> = listOf(
        "전체",
        "펨코",
        "뽐뿌",
        "루리웹",
        "퀘이사존",
        "클리앙",
        "알리뽐뿌",
        "빠삭국내",
        "빠삭해외",
        "빠삭육아"
    )

    val STANDARD_COMMUNITY_PLATFORMS: List<String> = STANDARD_SOURCES.filter { it != "전체" }

    private val STANDARD_KEYS: Set<String> = STANDARD_CATEGORIES.map { it.key }.toSet()

    private val _categoriesState = MutableStateFlow(STANDARD_CATEGORIES)
    val categoriesState: StateFlow<List<StandardCategory>> = _categoriesState.asStateFlow()

    private val _sourcesState = MutableStateFlow(STANDARD_SOURCES)
    val sourcesState: StateFlow<List<String>> = _sourcesState.asStateFlow()

    private val CATEGORY_ALIASES: Map<String, String> = mapOf(
        // 음식
        "음식" to "음식",
        "식품/건강" to "음식",
        "식품" to "음식",
        "먹거리" to "음식",
        "간식" to "음식",
        "음료" to "음식",
        "건강" to "음식",
        // SW/게임
        "sw/게임" to "SW/게임",
        "sw" to "SW/게임",
        "게임" to "SW/게임",
        "소프트웨어" to "SW/게임",
        "콘솔" to "SW/게임",
        // PC용품
        "pc용품" to "PC용품",
        "pc/하드웨어" to "PC용품",
        "pc" to "PC용품",
        "하드웨어" to "PC용품",
        "컴퓨터" to "PC용품",
        "노트북" to "PC용품",
        // 가전제품
        "가전제품" to "가전제품",
        "가전/tv" to "가전제품",
        "가전" to "가전제품",
        "가전/디지털" to "가전제품",
        "디지털" to "가전제품",
        "tv" to "가전제품",
        // 생활용품
        "생활용품" to "생활용품",
        "생활/식품" to "생활용품",
        "생활" to "생활용품",
        "생필품" to "생활용품",
        "주방" to "생활용품",
        // 패션
        "패션" to "패션",
        "의류/패션" to "패션",
        "의류" to "패션",
        "신발" to "패션",
        "잡화" to "패션",
        // 뷰티
        "뷰티" to "뷰티",
        "화장품" to "뷰티",
        "미용" to "뷰티",
        // 모바일/기프티콘
        "모바일/기프티콘" to "모바일/기프티콘",
        "모바일" to "모바일/기프티콘",
        "기프티콘" to "모바일/기프티콘",
        "e쿠폰" to "모바일/기프티콘",
        // 상품권
        "상품권" to "상품권",
        "이용권" to "상품권",
        // 적립
        "적립" to "적립",
        "적립/포인트" to "적립",
        "포인트" to "적립",
        // 이벤트
        "이벤트" to "이벤트",
        "체험단" to "이벤트",
        // 육아/유아용품
        "육아/유아용품" to "육아/유아용품",
        "육아" to "육아/유아용품",
        "유아용품" to "육아/유아용품",
        "유아" to "육아/유아용품",
        // 해외직구 (레거시 별칭 정규화)
        "해외직구" to "해외직구",
        "해외쇼핑" to "해외직구",
        "여행.해외핫딜" to "해외직구",
        "해외핫딜" to "해외직구",
        "여행/해외핫딜" to "해외직구",
        "여행/해외" to "해외직구",
        "직구" to "해외직구",
        "해외" to "해외직구",
        // 핫딜모음
        "핫딜모음" to "핫딜모음",
        // 기타
        "기타" to "기타"
    )

    private val KEY_TO_LABEL: Map<String, String>
        get() = _categoriesState.value.associate { it.key to it.label }

    /**
     * 레거시/비표준 카테고리명을 15종 표준 카테고리 키로 정규화합니다.
     */
    fun normalizeCategoryKey(raw: String?): String {
        val cleaned = raw?.trim().orEmpty()
        if (cleaned.isBlank()) return "기타"
        if (cleaned in STANDARD_KEYS) return cleaned

        val lowered = cleaned.lowercase()
        CATEGORY_ALIASES[lowered]?.let { return it }

        return when {
            lowered.contains("해외핫딜") || lowered.contains("해외직구") || lowered.contains("여행.해외") -> "해외직구"
            lowered.contains("가전") -> "가전제품"
            lowered.contains("육아") || lowered.contains("유아") -> "육아/유아용품"
            lowered.contains("식품") || lowered.contains("음식") || lowered.contains("먹거리") -> "음식"
            lowered.contains("pc") || lowered.contains("하드웨어") || lowered.contains("컴퓨터") -> "PC용품"
            lowered.contains("게임") || lowered.contains("sw") -> "SW/게임"
            lowered.contains("패션") || lowered.contains("의류") -> "패션"
            lowered.contains("뷰티") || lowered.contains("화장품") -> "뷰티"
            lowered.contains("모바일") || lowered.contains("기프티콘") -> "모바일/기프티콘"
            lowered.contains("상품권") -> "상품권"
            lowered.contains("적립") || lowered.contains("포인트") -> "적립"
            lowered.contains("이벤트") -> "이벤트"
            lowered.contains("생활") || lowered.contains("생필품") -> "생활용품"
            lowered.contains("핫딜모음") -> "핫딜모음"
            else -> "기타"
        }
    }

    /**
     * 카테고리 키 또는 레거시 키를 사용자에게 노출할 표준 한글 라벨로 변환합니다.
     */
    fun getDisplayLabel(rawOrKey: String?): String {
        val cleaned = rawOrKey?.trim().orEmpty()
        if (cleaned == "전체") return "전체"
        val normalizedKey = normalizeCategoryKey(cleaned)
        return KEY_TO_LABEL[normalizedKey]
            ?: STANDARD_CATEGORIES.firstOrNull { it.key == normalizedKey }?.label
            ?: normalizedKey
    }

    /**
     * 백엔드(`community.py`) 및 `DealDao.getPagedDeals()`와 동일한 카테고리 매칭 여부를 판정합니다.
     */
    fun matchesDealCategory(
        title: String,
        category: String?,
        siteName: String,
        siteNames: List<String> = emptyList(),
        honeyScore: Int = 0,
        targetCategory: String?
    ): Boolean {
        if (targetCategory.isNullOrBlank() || targetCategory == "전체") return true
        val normalizedTarget = normalizeCategoryKey(targetCategory)
        if (normalizedTarget == "핫딜모음") {
            return honeyScore >= 100 || category?.trim() == "핫딜모음"
        }

        val catLower = category?.trim().orEmpty().lowercase()
        val titleLower = title.lowercase()
        val siteLower = siteName.lowercase()
        val allSitesLower = (listOf(siteName) + siteNames).joinToString(" ").lowercase()

        if (catLower == normalizedTarget.lowercase()) return true

        return when (normalizedTarget) {
            "음식" -> listOf("음식", "식품", "먹거리", "간식", "음료", "건강").any { it in catLower }
            "SW/게임" -> listOf("sw", "게임", "소프트웨어", "콘솔").any { it in catLower }
            "PC용품" -> listOf("pc", "하드웨어", "노트북", "컴퓨터", "데스크탑", "모니터").any { it in catLower }
            "가전제품" -> {
                listOf("가전", "냉장고", "tv", "음향", "디지털").any { it in catLower } ||
                    APPLIANCE_TITLE_KEYWORDS.any { it in titleLower }
            }
            "생활용품" -> listOf("생활", "생필품", "주방", "가구", "인테리어").any { it in catLower }
            "패션" -> listOf("패션", "의류", "신발", "잡화", "가방", "화장품").any { it in catLower }
            "뷰티" -> {
                listOf("뷰티", "미용", "화장품").any { it in catLower } ||
                    BEAUTY_TITLE_KEYWORDS.any { it in titleLower }
            }
            "모바일/기프티콘" -> listOf("모바일", "스마트폰", "태블릿", "기프티콘", "쿠폰").any { it in catLower }
            "상품권" -> listOf("상품권", "이용권", "패키지", "기프티콘", "커피", "이커머스").any { it in catLower }
            "적립" -> {
                listOf("적립", "포인트", "사인업", "하이패스", "리퀘스트").any { it in catLower } ||
                    POINT_TITLE_KEYWORDS.any { it in titleLower }
            }
            "이벤트" -> {
                listOf("이벤트", "추석", "명절", "무료증정", "리뷰", "체험단", "선착순", "무료배포").any { it in catLower } ||
                    EVENT_TITLE_KEYWORDS.any { it in titleLower }
            }
            "육아/유아용품" -> {
                listOf("육아", "유아").any { it in catLower } ||
                    "빠삭육아" in allSitesLower ||
                    "bbasak_parenting" in siteLower ||
                    PARENTING_TITLE_KEYWORDS.any { it in titleLower }
            }
            "해외직구" -> {
                listOf("해외직구", "해외핫딜", "여행", "해외", "호텔", "프리미엄", "항공", "직구").any { it in catLower }
            }
            else -> targetCategory.lowercase() in catLower
        }
    }

    private val APPLIANCE_TITLE_KEYWORDS = listOf(
        "선풍기", "에어컨", "청소기", "로봇청소기", "냉장고", "세탁기", "건조기", "공기청정기",
        "제습기", "가습기", "전자레인지", "오븐", "에어프라이어", "밥솥", "식기세척기", "블렌더",
        "믹서기", "다리미", "안마", "드라이기", "면도기", "전동칫솔", "이어폰", "헤드폰", "스피커",
        "사운드바", "버즈", "에어팟", "빔프로젝터", "tv", "티비"
    )
    private val BEAUTY_TITLE_KEYWORDS = listOf(
        "선크림", "바디워시", "샴푸", "트리트먼트", "클렌징", "토너", "세럼", "로션", "올리브영", "이니스프리", "닥터지", "달바"
    )
    private val POINT_TITLE_KEYWORDS = listOf(
        "네이버페이", "적립", "네이버포인트", "페이포인트", "라방", "클릭", "출석"
    )
    private val EVENT_TITLE_KEYWORDS = listOf(
        "이벤트", "선착순", "무료배포", "일시무료", "프로모션", "체험단", "응모"
    )
    private val PARENTING_TITLE_KEYWORDS = listOf(
        "기저귀", "분유", "아기", "유아", "키즈", "하기스", "팸퍼스", "젖병", "카시트", "유모차", "베베", "아동"
    )

    /**
     * 서버 `/api/meta/categories`와 동기화하되, 오프라인/실패 시에도 15종 표준 목록 폴백을 보존합니다.
     */
    suspend fun syncWithServer(apiService: ApiService) {
        runCatching {
            val response = apiService.getMetaCategories()
            if (response.isSuccessful) {
                val body = response.body()
                val remoteCategories = body?.categories
                    ?.mapNotNull { item ->
                        val key = normalizeCategoryKey(item.key)
                        val label = item.label.trim().ifBlank { getDisplayLabel(key) }
                        if (key.isBlank()) null else StandardCategory(key, label)
                    }
                    ?.distinctBy { it.key }
                    .orEmpty()
                if (remoteCategories.size >= STANDARD_CATEGORIES.size) {
                    _categoriesState.value = remoteCategories
                }

                val remoteSources = body?.sources
                    ?.map { it.trim() }
                    ?.filter { it.isNotBlank() }
                    ?.distinct()
                    .orEmpty()
                if (remoteSources.isNotEmpty()) {
                    _sourcesState.value = remoteSources
                }
            }
        }
    }
}

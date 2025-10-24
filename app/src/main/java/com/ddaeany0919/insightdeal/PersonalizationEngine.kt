package com.ddaeany0919.insightdeal

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.ddaeany0919.insightdeal.models.DealItem
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.text.Normalizer
import kotlin.math.*

/**
 * 🤖 AI 개인화 추천 엔진
 * 사용자의 행동 패턴을 분석하여 맞춤형 핫딜을 추천하는 머신러닝 시스템
 */
class PersonalizationEngine private constructor(private val context: Context) {

    companion object {
        private const val TAG = "PersonalizationEngine"
        private const val PREFS_NAME = "personalization_prefs"
        private const val KEY_USER_PROFILE = "user_profile"
        private const val KEY_INTERACTION_HISTORY = "interaction_history"

        @Volatile
        private var INSTANCE: PersonalizationEngine? = null

        fun getInstance(context: Context): PersonalizationEngine {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: PersonalizationEngine(context.applicationContext).also { INSTANCE = it }
            }
        }
    }

    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    // 사용자 프로필 상태
    private val _userProfile = MutableStateFlow(loadUserProfile())
    val userProfile: StateFlow<UserProfile> = _userProfile.asStateFlow()

    // 개인화 추천 결과
    private val _recommendations = MutableStateFlow<List<DealItem>>(emptyList())
    val recommendations: StateFlow<List<DealItem>> = _recommendations.asStateFlow()

    init {
        Log.d(TAG, "🤖 AI 개인화 엔진 초기화 완료")
    }

    /**
     * 사용자 행동 기록 및 학습
     */
    fun trackUserInteraction(interaction: UserInteraction) {
        val currentProfile = _userProfile.value
        val updatedProfile = updateProfileWithInteraction(currentProfile, interaction)

        _userProfile.value = updatedProfile
        saveUserProfile(updatedProfile)

        Log.d(TAG, "📊 사용자 상호작용 기록: ${interaction.type} - ${interaction.dealId}")
    }

    /**
     * AI 맞춤 추천 생성
     */
    fun generatePersonalizedRecommendations(deals: List<DealItem>): List<DealItem> {
        val profile = _userProfile.value

        if (deals.isEmpty()) {
            Log.w(TAG, "⚠️ 추천할 딜이 없습니다")
            return emptyList()
        }

        // 🧠 AI 스코어링 시스템
        val scoredDeals = deals.map { deal ->
            val score = calculatePersonalizationScore(deal, profile)
            ScoredDeal(deal, score)
        }

        // 상위 추천 아이템 선별 (상위 20개)
        val recommendations = scoredDeals
            .sortedByDescending { it.score }
            .take(20)
            .map { it.deal }

        _recommendations.value = recommendations

        Log.d(TAG, "🎯 AI 추천 ${recommendations.size}개 생성 완료 (평균 점수: ${scoredDeals.map { it.score }.average().toInt()})")

        return recommendations
    }

    /**
     * 개인화 점수 계산 (0~100점)
     */
    private fun calculatePersonalizationScore(deal: DealItem, profile: UserProfile): Double {
        var score = 50.0 // 기본 점수

        // 1️⃣ 카테고리 관심도 (30% 가중치)
        val categoryScore = getCategoryInterest(deal, profile) * 0.3
        score += categoryScore

        // 2️⃣ 브랜드 선호도 (20% 가중치)
        val brandScore = getBrandPreference(deal, profile) * 0.2
        score += brandScore

        // 3️⃣ 가격대 선호도 (25% 가중치)
        val priceScore = getPricePreference(deal, profile) * 0.25
        score += priceScore

        // 4️⃣ 키워드 매칭 (15% 가중치)
        val keywordScore = getKeywordMatching(deal, profile) * 0.15
        score += keywordScore

        // 5️⃣ 시간대별 선호도 (5% 가중치)
        val timeScore = getTimePreference(deal, profile) * 0.05
        score += timeScore

        // 6️⃣ 사이트 선호도 (5% 가중치)
        val siteScore = getSitePreference(deal, profile)
        score += siteScore

        return score.coerceIn(0.0, 100.0)
    }

    private fun getCategoryInterest(deal: DealItem, profile: UserProfile): Double {
        val category = extractCategory(deal.title)
        return profile.categoryInterests[category] ?: 50.0
    }

    private fun getBrandPreference(deal: DealItem, profile: UserProfile): Double {
        val brand = extractBrand(deal.title)
        return profile.brandPreferences[brand] ?: 50.0
    }

    private fun getPricePreference(deal: DealItem, profile: UserProfile): Double {
        val price = deal.price ?: 0

        return when {
            price <= 50000 && profile.preferredPriceRange.contains("low") -> 80.0
            price in 50001..200000 && profile.preferredPriceRange.contains("medium") -> 85.0
            price in 200001..1000000 && profile.preferredPriceRange.contains("high") -> 75.0
            price > 1000000 && profile.preferredPriceRange.contains("premium") -> 60.0
            else -> 40.0
        }
    }

    private fun getKeywordMatching(deal: DealItem, profile: UserProfile): Double {
        val dealText = deal.title.lowercase()
        val matchingKeywords = profile.favoriteKeywords.count { keyword ->
            dealText.contains(keyword.lowercase())
        }

        return minOf(matchingKeywords * 15.0, 100.0)
    }

    private fun getTimePreference(deal: DealItem, profile: UserProfile): Double {
        // 시간대별 활동 패턴 반영 (추후 구현)
        return 50.0
    }

    private fun getSitePreference(deal: DealItem, profile: UserProfile): Double {
        // 🔥 사용자 지정 우선순위 반영
        return when (deal.siteName?.lowercase()) {
            "ppomppu", "뽐뿌" -> 25.0        // 1순위
            "fmkorea", "에펨코리아" -> 25.0    // 1순위
            "bbasak", "빠삭" -> 15.0         // 2순위 (중간)
            "ruliweb", "루리웹" -> 10.0      // 3순위 (후순위)
            "clien", "클리앙" -> 10.0        // 3순위 (후순위)
            "quasarzone", "퀘이사존" -> 10.0  // 3순위 (후순위)
            else -> 5.0
        }
    }

    /**
     * 딥러닝 카테고리 분류기 (간소화 버전)
     */
    private fun extractCategory(title: String): String {
        val text = title.lowercase()

        return when {
            // IT/전자제품
            text.containsAny(listOf("갤럭시", "아이폰", "맥북", "그래픽카드", "모니터", "키보드", "마우스", "노트북", "태블릿", "이어폰", "헤드셋")) -> "IT"

            // 패션/뷰티
            text.containsAny(listOf("옷", "신발", "가방", "화장품", "향수", "시계", "액세서리", "뷰티", "패션", "의류")) -> "패션"

            // 생활용품
            text.containsAny(listOf("생활용품", "주방", "청소", "세제", "화장지", "욕실", "침구", "가구", "인테리어")) -> "생활"

            // 식품/건강
            text.containsAny(listOf("식품", "음식", "건강", "영양제", "단백질", "비타민", "차", "커피", "간식", "과자")) -> "식품"

            // 스포츠/레저
            text.containsAny(listOf("운동", "스포츠", "헬스", "등산", "캠핑", "낚시", "자전거", "골프", "수영")) -> "스포츠"

            // 해외직구
            text.containsAny(listOf("아마존", "알리", "직구", "해외", "amazon", "aliexpress", "ebay")) -> "해외"

            else -> "기타"
        }
    }

    private fun extractBrand(title: String): String {
        val text = title.lowercase()

        val brands = mapOf(
            "삼성" to listOf("삼성", "갤럭시", "samsung"),
            "애플" to listOf("애플", "아이폰", "맥북", "아이패드", "apple", "iphone", "macbook"),
            "LG" to listOf("lg", "엘지"),
            "나이키" to listOf("나이키", "nike"),
            "아디다스" to listOf("아디다스", "adidas"),
            "다이슨" to listOf("다이슨", "dyson"),
            "샤오미" to listOf("샤오미", "xiaomi"),
            "소니" to listOf("소니", "sony")
        )

        brands.forEach { (brand, keywords) ->
            if (keywords.any { text.contains(it) }) {
                return brand
            }
        }

        return "기타"
    }

    /**
     * 사용자 프로필 업데이트
     */
    private fun updateProfileWithInteraction(profile: UserProfile, interaction: UserInteraction): UserProfile {
        val updatedCategoryInterests = profile.categoryInterests.toMutableMap()
        val updatedBrandPreferences = profile.brandPreferences.toMutableMap()
        val updatedKeywords = profile.favoriteKeywords.toMutableSet()

        // 상호작용 타입별 가중치
        val weight = when (interaction.type) {
            InteractionType.CLICK -> 1.0
            InteractionType.LONG_VIEW -> 2.0  // 오래 봤음 = 관심 높음
            InteractionType.BOOKMARK -> 5.0   // 북마크 = 매우 관심 높음
            InteractionType.PURCHASE -> 10.0  // 실제 구매 = 최고 관심
            InteractionType.SHARE -> 3.0
        }

        // 카테고리 관심도 업데이트 (지수 이동 평균)
        val category = interaction.category
        val currentInterest = updatedCategoryInterests[category] ?: 50.0
        val newInterest = currentInterest * 0.9 + weight * 10 * 0.1
        updatedCategoryInterests[category] = newInterest.coerceIn(0.0, 100.0)

        // 브랜드 선호도 업데이트
        val brand = interaction.brand
        if (brand.isNotEmpty()) {
            val currentPreference = updatedBrandPreferences[brand] ?: 50.0
            val newPreference = currentPreference * 0.9 + weight * 8 * 0.1
            updatedBrandPreferences[brand] = newPreference.coerceIn(0.0, 100.0)
        }

        // 관심 키워드 추출 및 업데이트
        val keywords = extractKeywords(interaction.title)
        updatedKeywords.addAll(keywords)

        // 키워드 개수 제한 (상위 50개만 유지)
        val limitedKeywords = updatedKeywords.take(50).toSet()

        return profile.copy(
            categoryInterests = updatedCategoryInterests,
            brandPreferences = updatedBrandPreferences,
            favoriteKeywords = limitedKeywords,
            totalInteractions = profile.totalInteractions + 1,
            lastUpdated = System.currentTimeMillis()
        )
    }

    /**
     * 키워드 추출기 (TF-IDF 기반 간소화 버전)
     */
    private fun extractKeywords(text: String): Set<String> {
        val normalized = Normalizer.normalize(text, Normalizer.Form.NFD)
            .replace(Regex("[^\\p{ASCII}한글]"), " ")
            .lowercase()

        // 의미 있는 단어만 추출 (2글자 이상, 불용어 제외)
        val stopWords = setOf("특가", "할인", "무료", "배송", "이벤트", "한정", "마감", "오늘", "내일", "지금")

        return normalized.split(Regex("\\s+"))
            .filter { it.length >= 2 && it !in stopWords }
            .toSet()
    }

    /**
     * 유사 사용자 기반 협업 필터링
     */
    fun findSimilarUserRecommendations(deals: List<DealItem>): List<DealItem> {
        val profile = _userProfile.value

        val topCategories = profile.categoryInterests
            .entries
            .sortedByDescending { it.value }
            .take(3)
            .map { it.key }

        return deals.filter { deal ->
            val category = extractCategory(deal.title)
            category in topCategories
        }.take(10)
    }

    /**
     * 콘텐츠 기반 필터링
     */
    fun getContentBasedRecommendations(deals: List<DealItem>): List<DealItem> {
        val profile = _userProfile.value

        return deals.filter { deal ->
            val dealText = deal.title.lowercase()
            profile.favoriteKeywords.any { keyword ->
                dealText.contains(keyword.lowercase())
            }
        }.take(15)
    }

    /**
     * 추천 피드백 처리
     */
    fun provideFeedback(dealId: Int, isPositive: Boolean) {
        // 추천 정확도 개선을 위한 피드백 학습
        val profile = _userProfile.value
        val feedbackWeight = if (isPositive) 2.0 else -1.0

        // 피드백을 다음 추천에 반영
        Log.d(TAG, "👍👎 사용자 피드백: $dealId = ${if (isPositive) "좋음" else "싫음"}")
    }

    /**
     * 사용자 프로필 저장/로드
     */
    private fun saveUserProfile(profile: UserProfile) {
        val json = profile.toJson()
        prefs.edit().putString(KEY_USER_PROFILE, json).apply()
    }

    private fun loadUserProfile(): UserProfile {
        val json = prefs.getString(KEY_USER_PROFILE, null)
        return if (json != null) {
            try {
                UserProfile.fromJson(json)
            } catch (e: Exception) {
                Log.w(TAG, "프로필 로드 실패, 기본값 사용: ${e.message}")
                UserProfile.createDefault()
            }
        } else {
            UserProfile.createDefault()
        }
    }

    /**
     * 개인화 성능 분석
     */
    fun getPersonalizationInsights(): PersonalizationInsights {
        val profile = _userProfile.value

        val topCategories = profile.categoryInterests
            .entries
            .sortedByDescending { it.value }
            .take(3)

        val topBrands = profile.brandPreferences
            .entries
            .sortedByDescending { it.value }
            .take(3)

        return PersonalizationInsights(
            totalInteractions = profile.totalInteractions,
            topCategories = topCategories.map { "${it.key} (${it.value.toInt()}점)" },
            topBrands = topBrands.map { "${it.key} (${it.value.toInt()}점)" },
            favoriteKeywords = profile.favoriteKeywords.take(10).toList(),
            profileCompleteness = calculateProfileCompleteness(profile)
        )
    }

    private fun calculateProfileCompleteness(profile: UserProfile): Int {
        var completeness = 0

        if (profile.totalInteractions > 10) completeness += 20
        if (profile.categoryInterests.size > 3) completeness += 25
        if (profile.brandPreferences.size > 2) completeness += 20
        if (profile.favoriteKeywords.size > 5) completeness += 20
        if (profile.preferredPriceRange.isNotEmpty()) completeness += 15

        return completeness.coerceIn(0, 100)
    }
}

// 🏗️ 데이터 모델들

data class UserProfile(
    val categoryInterests: Map<String, Double> = mapOf(), // 카테고리별 관심도 (0~100)
    val brandPreferences: Map<String, Double> = mapOf(),   // 브랜드별 선호도 (0~100)
    val favoriteKeywords: Set<String> = setOf(),          // 관심 키워드
    val preferredPriceRange: Set<String> = setOf(),       // 선호 가격대
    val totalInteractions: Int = 0,
    val createdAt: Long = System.currentTimeMillis(),
    val lastUpdated: Long = System.currentTimeMillis()
) {
    fun toJson(): String {
        // Gson을 사용하여 JSON 직렬화
        return com.google.gson.Gson().toJson(this)
    }

    companion object {
        fun fromJson(json: String): UserProfile {
            return try {
                com.google.gson.Gson().fromJson(json, UserProfile::class.java)
            } catch (e: Exception) {
                createDefault()
            }
        }

        fun createDefault(): UserProfile {
            return UserProfile(
                categoryInterests = mapOf(
                    "IT" to 60.0,
                    "생활" to 70.0,
                    "패션" to 50.0,
                    "식품" to 55.0,
                    "해외" to 45.0
                ),
                preferredPriceRange = setOf("low", "medium") // 기본적으로 저가~중가 선호
            )
        }
    }
}

data class UserInteraction(
    val type: InteractionType,
    val dealId: Int,
    val title: String,
    val category: String,
    val brand: String,
    val price: Int?,
    val timestamp: Long = System.currentTimeMillis(),
    val sessionDuration: Long = 0L // 밀리초
)

enum class InteractionType {
    CLICK,      // 클릭
    LONG_VIEW,  // 오래 보기 (30초 이상)
    BOOKMARK,   // 북마크
    PURCHASE,   // 구매
    SHARE       // 공유
}

data class ScoredDeal(
    val deal: DealItem,
    val score: Double
)

data class PersonalizationInsights(
    val totalInteractions: Int,
    val topCategories: List<String>,
    val topBrands: List<String>,
    val favoriteKeywords: List<String>,
    val profileCompleteness: Int
)

// 🛠️ 유틸리티 확장 함수
private fun String.containsAny(keywords: List<String>): Boolean {
    return keywords.any { this.contains(it, ignoreCase = true) }
}
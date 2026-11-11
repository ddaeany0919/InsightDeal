package com.ddaeany0919.insightdeal.presentation.recommendation

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ddaeany0919.insightdeal.data.LocalDataProvider
import com.ddaeany0919.insightdeal.domain.InteractionType
import com.ddaeany0919.insightdeal.domain.PersonalizationInsights
import com.ddaeany0919.insightdeal.domain.UserInteraction
import com.ddaeany0919.insightdeal.models.DealItem
import com.ddaeany0919.insightdeal.network.ApiService
import com.ddaeany0919.insightdeal.network.NetworkModule
import com.ddaeany0919.insightdeal.network.TrackClickRequest
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import retrofit2.HttpException

class RecommendationViewModel : ViewModel() {

    companion object {
        private const val TAG = "RecommendationVM"
    }

    private val personalizationEngine = LocalDataProvider.personalizationEngine
    private val apiService = NetworkModule.createService<ApiService>()

    private val _recommendations = MutableStateFlow<List<DealItem>>(emptyList())
    val recommendations: StateFlow<List<DealItem>> = _recommendations.asStateFlow()

    private val _insights = MutableStateFlow<PersonalizationInsights?>(null)
    val insights: StateFlow<PersonalizationInsights?> = _insights.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    private var allDeals: List<DealItem> = emptyList()
    private var categoryDeals: Map<String, List<DealItem>> = emptyMap()
    private var brandDeals: Map<String, List<DealItem>> = emptyMap()
    private var trendingDeals: List<DealItem> = emptyList()

    init {
        loadInsights()
    }

    fun loadPersonalizedRecommendations() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                _isLoading.value = true
                _errorMessage.value = null

                val dealsResponse = apiService.getCommunityHotDeals(
                    limit = 80,
                    offset = 0,
                    sort = "popular"
                )

                if (dealsResponse.isSuccessful) {
                    allDeals = dealsResponse.body()?.deals.orEmpty()

                    val personalized = personalizationEngine
                        .generatePersonalizedRecommendations(allDeals)
                        .ifEmpty {
                            allDeals
                                .filter { !it.isClosed }
                                .sortedByDescending { it.honeyScore }
                                .take(20)
                        }
                    _recommendations.value = personalized

                    groupDealsByCategory()
                    groupDealsByBrand()
                    loadTrendingDeals()
                } else {
                    _errorMessage.value = "맞춤 핫딜을 불러오지 못했어요. (${dealsResponse.code()})"
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: HttpException) {
                Log.e(TAG, "HTTP error: ${e.code()}", e)
                _errorMessage.value = "서버 응답이 지연되고 있어요. (${e.code()})"
            } catch (e: Exception) {
                Log.e(TAG, "Load recommendations failed", e)
                _errorMessage.value = "네트워크 연결 상태를 확인해 주세요."
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun refresh() {
        loadPersonalizedRecommendations()
        loadInsights()
    }

    fun trackOutboundClick(url: String, price: Int? = null, currency: String? = "KRW") {
        if (url.isBlank()) return
        viewModelScope.launch {
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

    fun provideFeedback(dealId: Int, isPositive: Boolean) {
        personalizationEngine.provideFeedback(dealId, isPositive)
        viewModelScope.launch(Dispatchers.Default) {
            val updated = personalizationEngine.generatePersonalizedRecommendations(allDeals)
            if (updated.isNotEmpty()) {
                _recommendations.value = updated
            }
        }
    }

    fun getCategoryRecommendations(categoryName: String): List<DealItem> =
        categoryDeals[categoryName].orEmpty()

    fun getBrandRecommendations(brandName: String): List<DealItem> =
        brandDeals[brandName].orEmpty()

    fun getTrendingRecommendations(): List<DealItem> = trendingDeals

    fun clearError() {
        _errorMessage.value = null
    }

    private fun loadInsights() {
        _insights.value = personalizationEngine.getPersonalizationInsights()
    }

    private fun groupDealsByCategory() {
        val grouped = allDeals.groupBy { deal -> extractCategoryFromTitle(deal.title) }
        categoryDeals = grouped.mapValues { (_, deals) ->
            deals.sortedByDescending { it.discountRate ?: 0 }.take(10)
        }
    }

    private fun groupDealsByBrand() {
        val grouped = allDeals
            .groupBy { deal -> extractBrandFromTitle(deal.title) }
            .filter { (brand, _) -> brand != "기타" }

        brandDeals = grouped.mapValues { (_, deals) ->
            deals.sortedByDescending { it.discountRate ?: 0 }.take(8)
        }
    }

    private fun loadTrendingDeals() {
        trendingDeals = allDeals
            .filter { !it.isClosed }
            .sortedWith(
                compareByDescending<DealItem> { it.honeyScore }
                    .thenByDescending { it.id }
            )
            .take(15)
    }

    private fun extractCategoryFromTitle(title: String): String {
        val text = title.lowercase()
        return when {
            text.containsAny(listOf("갤럭시", "아이폰", "맥북", "그래픽카드", "모니터", "키보드", "마우스", "노트북", "태블릿", "이어폰", "헤드셋")) -> "IT"
            text.containsAny(listOf("옷", "신발", "가방", "화장품", "향수", "시계", "액세서리", "뷰티", "패션", "의류")) -> "패션"
            text.containsAny(listOf("생활용품", "주방", "청소", "세제", "화장지", "욕실", "침구", "가구", "인테리어")) -> "생활"
            text.containsAny(listOf("식품", "음식", "건강", "영양제", "단백질", "비타민", "차", "커피", "간식", "과자")) -> "식품"
            text.containsAny(listOf("운동", "스포츠", "헬스", "등산", "캠핑", "낚시", "자전거", "골프", "수영")) -> "스포츠"
            text.containsAny(listOf("아마존", "알리", "직구", "해외", "amazon", "aliexpress", "ebay")) -> "해외"
            else -> "기타"
        }
    }

    private fun extractBrandFromTitle(title: String): String {
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

    fun trackInteraction(dealId: Int, type: InteractionType, deal: DealItem) {
        val interaction = UserInteraction(
            type = type,
            dealId = deal.id,
            title = deal.title,
            category = extractCategoryFromTitle(deal.title),
            brand = extractBrandFromTitle(deal.title),
            price = deal.price
        )
        personalizationEngine.trackUserInteraction(interaction)
        loadInsights()
    }

    fun getCollaborativeFilteringRecommendations(): List<DealItem> =
        personalizationEngine.findSimilarUserRecommendations(allDeals)

    fun getContentBasedRecommendations(): List<DealItem> =
        personalizationEngine.getContentBasedRecommendations(allDeals)

    fun getHybridRecommendations(): List<DealItem> {
        val collaborative = getCollaborativeFilteringRecommendations()
        val contentBased = getContentBasedRecommendations()
        val personalized = _recommendations.value
        return (personalized + collaborative + contentBased)
            .distinctBy { it.id }
            .take(30)
    }
}

private fun String.containsAny(keywords: List<String>): Boolean =
    keywords.any { this.contains(it, ignoreCase = true) }

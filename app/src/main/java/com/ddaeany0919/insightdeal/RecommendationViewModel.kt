package com.ddaeany0919.insightdeal

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.ddaeany0919.insightdeal.models.DealItem
import com.ddaeany0919.insightdeal.network.ApiClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import retrofit2.HttpException

/**
 * 🎯 AI 추천 시스템 ViewModel
 * PersonalizationEngine과 연동하여 맞춤형 추천 서비스 제공
 */
class RecommendationViewModel(
    application: Application
) : AndroidViewModel(application) {

    companion object {
        private const val TAG = "RecommendationVM"
    }

    private val personalizationEngine = PersonalizationEngine.getInstance(application)
    private val apiService = ApiClient.create()

    // UI 상태
    private val _recommendations = MutableStateFlow<List<DealItem>>(emptyList())
    val recommendations: StateFlow<List<DealItem>> = _recommendations.asStateFlow()

    private val _insights = MutableStateFlow<PersonalizationInsights?>(null)
    val insights: StateFlow<PersonalizationInsights?> = _insights.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    // 캐시된 데이터
    private var allDeals: List<DealItem> = emptyList()
    private var categoryDeals: Map<String, List<DealItem>> = emptyMap()
    private var brandDeals: Map<String, List<DealItem>> = emptyMap()
    private var trendingDeals: List<DealItem> = emptyList()

    init {
        Log.d(TAG, "🎯 추천 시스템 ViewModel 초기화")
        loadInsights()
    }

    /**
     * 개인화 추천 데이터 로드
     */
    fun loadPersonalizedRecommendations() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                _isLoading.value = true
                _errorMessage.value = null
                
                Log.d(TAG, "🔄 개인화 추천 데이터 로드 시작")
                
                // 1. 전체 딜 데이터 로드
                val dealsResponse = apiService.getDeals(
                    page = 1,
                    limit = 100,
                    sort = "latest"
                )
                
                if (dealsResponse.isSuccessful) {
                    allDeals = dealsResponse.body()?.deals ?: emptyList()
                    Log.d(TAG, "✅ 전체 딜 ${allDeals.size}개 로드 완료")
                    
                    // 2. AI 개인화 추천 생성
                    val personalizedRecommendations = personalizationEngine.generatePersonalizedRecommendations(allDeals)
                    _recommendations.value = personalizedRecommendations
                    
                    // 3. 카테고리/브랜드별 분류
                    groupDealsByCategory()
                    groupDealsByBrand()
                    loadTrendingDeals()
                    
                    Log.d(TAG, "🎯 AI 추천 ${personalizedRecommendations.size}개 생성 완료")
                    
                } else {
                    val error = "데이터 로드에 실패했습니다: ${dealsResponse.code()}"
                    _errorMessage.value = error
                    Log.e(TAG, "❌ $error")
                }
                
            } catch (e: HttpException) {
                val error = "네트워크 오류: ${e.code()}"
                _errorMessage.value = error
                Log.e(TAG, "❌ HTTP Exception: ${e.message}")
            } catch (e: Exception) {
                val error = "데이터 로드 중 오류 발생: ${e.message}"
                _errorMessage.value = error
                Log.e(TAG, "❌ Unexpected error: ${e.message}")
            } finally {
                _isLoading.value = false
            }
        }
    }

    /**
     * 데이터 새로고침
     */
    fun refresh() {
        Log.d(TAG, "🔄 데이터 새로고침")
        loadPersonalizedRecommendations()
        loadInsights()
    }

    /**
     * 사용자 피드백 처리
     */
    fun provideFeedback(dealId: Int, isPositive: Boolean) {
        Log.d(TAG, "👍👎 피드백 수집: $dealId = ${if (isPositive) "좋음" else "싫음"}")
        
        // PersonalizationEngine에 피드백 전달
        personalizationEngine.provideFeedback(dealId, isPositive)
        
        // 추천 알고리즘 개선을 위해 새로고침
        viewModelScope.launch {
            val updatedRecommendations = personalizationEngine.generatePersonalizedRecommendations(allDeals)
            _recommendations.value = updatedRecommendations
        }
    }

    /**
     * 카테고리별 추천 조회
     */
    fun getCategoryRecommendations(categoryName: String): List<DealItem> {
        return categoryDeals[categoryName] ?: emptyList()
    }

    /**
     * 브랜드별 추천 조회
     */
    fun getBrandRecommendations(brandName: String): List<DealItem> {
        return brandDeals[brandName] ?: emptyList()
    }

    /**
     * 트렌딩 추천 조회
     */
    fun getTrendingRecommendations(): List<DealItem> {
        return trendingDeals
    }

    /**
     * 에러 메시지 컴리어
     */
    fun clearError() {
        _errorMessage.value = null
    }

    /**
     * 개인화 인사이트 로드
     */
    private fun loadInsights() {
        val insights = personalizationEngine.getPersonalizationInsights()
        _insights.value = insights
        Log.d(TAG, "📊 인사이트 로드: ${insights.totalInteractions}번 상호작용, ${insights.profileCompleteness}% 완성도")
    }

    /**
     * 카테고리별 딜 분류
     */
    private fun groupDealsByCategory() {
        val grouped = allDeals.groupBy { deal ->
            extractCategoryFromTitle(deal.title)
        }
        categoryDeals = grouped.mapValues { (_, deals) ->
            deals.sortedByDescending { it.discountRate ?: 0 }.take(10)
        }
        Log.d(TAG, "📊 카테고리별 분류 완료: ${categoryDeals.size}개 카테고리")
    }

    /**
     * 브랜드별 딜 분류
     */
    private fun groupDealsByBrand() {
        val grouped = allDeals.groupBy { deal ->
            extractBrandFromTitle(deal.title)
        }.filter { (brand, _) -> brand != "기타" }
        
        brandDeals = grouped.mapValues { (_, deals) ->
            deals.sortedByDescending { it.discountRate ?: 0 }.take(8)
        }
        Log.d(TAG, "🏷️ 브랜드별 분류 완료: ${brandDeals.size}개 브랜드")
    }

    /**
     * 트렌딩 딜 로드 (인기 순)
     */
    private fun loadTrendingDeals() {
        // 할인율 높은 순 + 최근 등록 순으로 정렬
        trendingDeals = allDeals
            .filter { (it.discountRate ?: 0) >= 20 } // 20% 이상 할인
            .sortedWith(
                compareByDescending<DealItem> { it.discountRate ?: 0 }
                    .thenByDescending { it.id } // 최신 등록순
            )
            .take(15)
        
        Log.d(TAG, "🔥 트렌딩 딜 ${trendingDeals.size}개 로드 완료")
    }

    /**
     * 제목에서 카테고리 추출 (간단 버전)
     */
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

    /**
     * 제목에서 브랜드 추출
     */
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

    /**
     * 인사이트 데이터 로드
     */
    private fun loadInsights() {
        val insights = personalizationEngine.getPersonalizationInsights()
        _insights.value = insights
    }

    /**
     * 사용자 상호작용 추적
     */
    fun trackInteraction(dealId: Int, type: InteractionType, deal: DealItem) {
        val interaction = UserInteraction(
            type = type,
            dealId = dealId,
            title = deal.title,
            category = extractCategoryFromTitle(deal.title),
            brand = extractBrandFromTitle(deal.title),
            price = deal.price
        )
        
        personalizationEngine.trackUserInteraction(interaction)
        Log.d(TAG, "📊 상호작용 추적: ${type.name} - $dealId")
        
        // 인사이트 업데이트
        loadInsights()
    }

    /**
     * 고급 추천 기능들
     */
    fun getCollaborativeFilteringRecommendations(): List<DealItem> {
        return personalizationEngine.findSimilarUserRecommendations(allDeals)
    }

    fun getContentBasedRecommendations(): List<DealItem> {
        return personalizationEngine.getContentBasedRecommendations(allDeals)
    }

    fun getHybridRecommendations(): List<DealItem> {
        // 협업 + 콘텐츠 기반 하이브리드 추천
        val collaborative = getCollaborativeFilteringRecommendations()
        val contentBased = getContentBasedRecommendations()
        val personalized = _recommendations.value
        
        // 중단 제거 및 다양성 보장
        val combined = (personalized + collaborative + contentBased)
            .distinctBy { it.id }
            .shuffled()
            .take(30)
        
        Log.d(TAG, "🤖 하이브리드 추천 ${combined.size}개 생성")
        return combined
    }
}

// 🛠️ 유틸리티 확장 함수
private fun String.containsAny(keywords: List<String>): Boolean {
    return keywords.any { this.contains(it, ignoreCase = true) }
}
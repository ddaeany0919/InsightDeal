package com.ddaeany0919.insightdeal.data

import com.ddaeany0919.insightdeal.models.ApiDeal
import com.ddaeany0919.insightdeal.models.ComparisonResponse
import com.ddaeany0919.insightdeal.network.HealthResponse
import kotlinx.coroutines.flow.Flow

/**
 * 📊 딜 데이터 Repository 인터페이스
 * 
 * 사용자 중심 설계 원칙:
 * - 빠른 응답: 캐시 우선, 백그라운드 업데이트
 * - 안정성: 네트워크 오류 시에도 이전 데이터 제공
 * - 투명성: 로딩 상태와 에러 상태 명확히 구분
 */
interface DealsRepository {
    
    /**
     * 🔍 단일 상품 가격 비교
     * 사용자가 가장 많이 사용할 핵심 기능
     * 
     * @param query 검색어 (상품명)
     * @param forceRefresh 강제 새로고침 여부 (Pull-to-Refresh 시 true)
     * @return Flow<Resource<ComparisonResponse>> 상태를 포함한 응답
     */
    fun searchDeal(
        query: String,
        forceRefresh: Boolean = false
    ): Flow<Resource<ComparisonResponse>>
    
    /**
     * 📱 홈 화면용 인기 검색어 기반 딜 목록
     * 사용자가 앱을 열었을 때 즉시 볼 수 있는 콘텐츠
     * 
     * @param popularQueries 인기 검색어 리스트
     * @param maxResults 최대 결과 개수 (성능 고려)
     * @return Flow<Resource<List<ApiDeal>>> 여러 딜의 상태별 응답
     */
    fun getPopularDeals(
        popularQueries: List<String> = defaultPopularQueries,
        maxResults: Int = 10
    ): Flow<Resource<List<ApiDeal>>>
    
    /**
     * ⚡ 서버 상태 확인
     * 앱 시작 시 백엔드 서버 가용성 체크
     * 
     * @return Flow<Resource<HealthResponse>> 서버 상태 정보
     */
    fun checkServerHealth(): Flow<Resource<HealthResponse>>
    
    /**
     * 🧹 캐시 정리
     * 메모리 절약 및 오래된 데이터 제거
     * 
     * @param olderThanMinutes 지정된 시간보다 오래된 캐시 삭제
     */
    suspend fun clearCache(olderThanMinutes: Int = 30)
    
    /**
     * 📊 캐시 통계 정보
     * 개발자/디버깅용
     */
    fun getCacheStats(): CacheStats
    
    companion object {
        /**
         * 📈 기본 인기 검색어 (사용자 행동 데이터 기반)
         * 실제 운영에서는 서버에서 동적으로 가져올 예정
         */
        val defaultPopularQueries = listOf(
            "갤럭시 버즈",
            "에어팟", 
            "아이패드",
            "다이슨",
            "닌텐도 스위치",
            "맥북",
            "삼성 모니터",
            "LG 그램"
        )
    }
}

/**
 * 🎯 리소스 래퍼 클래스 (Loading, Success, Error 상태 관리)
 * 사용자에게 명확한 상태 피드백 제공
 */
sealed class Resource<T> {
    data class Loading<T>(val data: T? = null) : Resource<T>()
    data class Success<T>(val data: T) : Resource<T>()
    data class Error<T>(val message: String, val data: T? = null, val throwable: Throwable? = null) : Resource<T>()
    
    /**
     * 데이터 존재 여부 확인
     */
    val hasData: Boolean
        get() = when (this) {
            is Loading -> data != null
            is Success -> true
            is Error -> data != null
        }
    
    /**
     * 실제 데이터 추출 (null 가능)
     */
    val dataOrNull: T?
        get() = when (this) {
            is Loading -> data
            is Success -> data
            is Error -> data
        }
}

/**
 * 📊 캐시 통계 정보
 */
data class CacheStats(
    val totalEntries: Int,
    val hitRate: Float,
    val averageResponseTime: Long,
    val oldestEntryMinutes: Int,
    val newestEntryMinutes: Int
)
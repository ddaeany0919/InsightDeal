package com.ddaeany0919.insightdeal.data

import com.ddaeany0919.insightdeal.BuildConfig

/**
 * 🏭 리포지토리 제공자 (간단한 DI 패턴)
 * 
 * 사용자 중심 설계:
 * - 빠른 개발: 복잡한 DI 라이브러리 없이도 체계적 의존성 관리
 * - 쉽운 테스트: Mock 과 Real 구현체 쉽게 전환 가능
 * - 메모리 효율: 싱글톤으로 리소스 절약
 * 
 * 향후 Hilt/Dagger 전환 예정시 쉽게 마이그레이션 가능
 */
object RepositoryProvider {
    
    /**
     * 📊 딜 리포지토리 인스턴스 (싱글톤)
     * 디버그/릴리스 환경에 따라 자동 전환
     */
    val dealsRepository: DealsRepository by lazy {
        if (BuildConfig.DEBUG_MODE) {
            // 디버그: 실제 API + 자세한 로깅
            RemoteDealsRepository()
        } else {
            // 릴리스: 실제 API + 최적화된 로깅
            RemoteDealsRepository()
        }
    }
    
    /**
     * 🧪 테스트용 Mock Repository 제공
     * 단위 테스트나 개발 중 네트워크 없이 테스트할 때 사용
     */
    fun createMockRepository(): DealsRepository {
        return MockDealsRepository()
    }
    
    /**
     * 🔧 Repository 강제 교체 (테스트/디버깅용)
     * 특정 상황에서 Mock 데이터를 사용하고 싶을 때
     */
    private var _overrideRepository: DealsRepository? = null
    
    fun setTestRepository(repository: DealsRepository) {
        _overrideRepository = repository
    }
    
    fun clearTestRepository() {
        _overrideRepository = null
    }
    
    fun getCurrentRepository(): DealsRepository {
        return _overrideRepository ?: dealsRepository
    }
    
    /**
     * 📊 현재 구성 정보 (디버깅용)
     */
    fun getRepositoryInfo(): Map<String, Any> {
        val currentRepo = getCurrentRepository()
        return mapOf(
            "repository_type" to currentRepo::class.java.simpleName,
            "debug_mode" to BuildConfig.DEBUG_MODE,
            "is_override" to (_overrideRepository != null),
            "cache_stats" to if (currentRepo is RemoteDealsRepository) {
                currentRepo.getCacheStats()
            } else {
                "N/A"
            }
        )
    }
}
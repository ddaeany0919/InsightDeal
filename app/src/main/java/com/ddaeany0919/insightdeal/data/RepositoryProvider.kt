package com.ddaeany0919.insightdeal.data

import com.ddaeany0919.insightdeal.BuildConfig
import com.ddaeany0919.insightdeal.network.DealsRetrofitClient

/**
 * 🏭 리포지토리 제공자 (간단한 DI 패턴)
 */
object RepositoryProvider {

    /**
     * 📊 딜 리포지토리 인스턴스 (싱글톤)
     * 디버그/릴리스 환경에 따라 자동 전환
     */
    val dealsRepository: DealsRepository by lazy {
        if (BuildConfig.DEBUG) { // ✅ DEBUG_MODE → DEBUG 수정
            // 디버그: 실제 API + 자세한 로깅
            RemoteDealsRepository(DealsRetrofitClient.dealsApiService) // ✅ apiService 제공
        } else {
            // 릴리스: 실제 API + 최적화된 로깅
            RemoteDealsRepository(DealsRetrofitClient.dealsApiService) // ✅ apiService 제공
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
            "debug_mode" to BuildConfig.DEBUG,
            "is_override" to (_overrideRepository != null),
            "cache_stats" to if (currentRepo is RemoteDealsRepository) {
                currentRepo.getCacheStats()
            } else {
                "N/A"
            }
        )
    }
}

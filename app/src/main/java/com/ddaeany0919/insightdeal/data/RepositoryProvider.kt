package com.ddaeany0919.insightdeal.data

import android.util.Log
import com.ddaeany0919.insightdeal.BuildConfig
import com.ddaeany0919.insightdeal.network.NetworkModule
import com.ddaeany0919.insightdeal.network.DealsApiService

/**
 * 🏭 리포지토리 제공자 (간단한 DI 패턴)
 */
object RepositoryProvider {
    
    private const val TAG = "RepositoryProvider"

    /**
     * 📊 딜 리포지토리 인스턴스 (싱글톤)
     */
    val dealsRepository: DealsRepository by lazy {
        Log.d(TAG, "📦 DealsRepository 초기화 중...")
        // NetworkModule을 사용하여 API 서비스 생성
        val dealsApiService = NetworkModule.createService<DealsApiService>()
        Log.d(TAG, "✅ DealsRepository 생성 완료 (NetworkModule 사용)")
        RemoteDealsRepository(dealsApiService)
    }

    /**
     * 🧪 테스트용 Mock Repository 제공
     * 
     * NOTE: MockDealsRepository가 삭제되었으므로 사용 불가
     */
    @Deprecated("MockDealsRepository has been removed. Use test doubles instead.")
    fun createMockRepository(): DealsRepository {
        Log.w(TAG, "⚠️ createMockRepository() 호출됨 - 이 메서드는 deprecated 되었습니다")
        throw UnsupportedOperationException("MockDealsRepository has been removed. Please use test doubles or fakes in your test code.")
    }

    /**
     * 🔧 Repository 강제 교체 (테스트/디버깅용)
     */
    private var _overrideRepository: DealsRepository? = null

    fun setTestRepository(repository: DealsRepository) {
        Log.d(TAG, "🔧 Test Repository 설정: ${repository::class.java.simpleName}")
        _overrideRepository = repository
    }

    fun clearTestRepository() {
        Log.d(TAG, "🗑️ Test Repository 제거")
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
        val info = mapOf(
            "repository_type" to currentRepo::class.java.simpleName,
            "debug_mode" to BuildConfig.DEBUG,
            "is_override" to (_overrideRepository != null),
            "cache_stats" to if (currentRepo is RemoteDealsRepository) {
                currentRepo.getCacheStats()
            } else {
                "N/A"
            }
        )
        Log.d(TAG, "📊 Repository Info: $info")
        return info
    }
}

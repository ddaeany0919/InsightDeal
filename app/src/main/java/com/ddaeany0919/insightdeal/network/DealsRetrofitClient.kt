package com.ddaeany0919.insightdeal.network

import com.ddaeany0919.insightdeal.BuildConfig
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

/**
 * 🌐 InsightDeal 실제 백엔드 API 클라이언트
 * 
 * 사용자 중심 설정:
 * - 2초 내 응답 목표 (connect: 5s, read: 15s, write: 10s)
 * - 디버그/릴리즈 환경별 URL 자동 전환
 * - 상세한 로깅으로 문제 발생 시 즉시 추적
 */
object DealsRetrofitClient {
    
    // 📊 네트워크 성능 목표
    private const val CONNECT_TIMEOUT = 5L // 서버 연결 대기시간
    private const val READ_TIMEOUT = 15L    // 데이터 읽기 대기시간 (4몰 비교 시간 고려)
    private const val WRITE_TIMEOUT = 10L   // 데이터 전송 대기시간
    
    /**
     * 📝 HTTP 로깅 인터셉터 (디버깅용)
     * 릴리즈에서는 비활성화 예정
     */
    private val loggingInterceptor = HttpLoggingInterceptor().apply {
        level = if (BuildConfig.DEBUG_MODE) {
            HttpLoggingInterceptor.Level.BODY // 디버그: 상세 로깅
        } else {
            HttpLoggingInterceptor.Level.BASIC // 릴리즈: 기본 로깅만
        }
    }
    
    /**
     * 🔗 OkHttp 클라이언트 (성능 최적화)
     */
    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(CONNECT_TIMEOUT, TimeUnit.SECONDS)
        .readTimeout(READ_TIMEOUT, TimeUnit.SECONDS)
        .writeTimeout(WRITE_TIMEOUT, TimeUnit.SECONDS)
        .addInterceptor(loggingInterceptor)
        .retryOnConnectionFailure(true) // 네트워크 장애 시 재시도
        .build()
    
    /**
     * 🚀 Retrofit 인스턴스 (Lazy 초기화)
     */
    private val retrofit: Retrofit by lazy {
        Retrofit.Builder()
            .baseUrl(BuildConfig.BASE_URL) // 환경별 URL 자동 전환
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }
    
    /**
     * 🎥 API 서비스 인스턴스 생성
     * 싱글톤 패턴으로 메모리 효율적 사용
     */
    val dealsApiService: DealsApiService by lazy {
        retrofit.create(DealsApiService::class.java)
    }
    
    /**
     * 🔍 네트워크 설정 정보 (디버깅용)
     */
    fun getNetworkConfig(): Map<String, Any> {
        return mapOf(
            "base_url" to BuildConfig.BASE_URL,
            "debug_mode" to BuildConfig.DEBUG_MODE,
            "connect_timeout_sec" to CONNECT_TIMEOUT,
            "read_timeout_sec" to READ_TIMEOUT,
            "write_timeout_sec" to WRITE_TIMEOUT,
            "logging_level" to loggingInterceptor.level.name
        )
    }
}
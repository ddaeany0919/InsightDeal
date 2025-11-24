package com.ddaeany0919.insightdeal.network

import android.util.Log
import com.google.gson.GsonBuilder
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

/**
 * 🌐 통합 네트워크 모듈
 * 
 * 모든 Repository가 공유하는 단일 Retrofit 인스턴스
 */
object NetworkModule {
    
    internal const val TAG = "NetworkModule"
    
    // TODO: BuildConfig 또는 환경 변수로 관리
    private const val BASE_URL = "http://192.168.0.4:8000/"
    
    // 타임아웃 설정 (AI 파서 사용 시 긴 응답 시간 고려)
    private const val CONNECT_TIMEOUT = 120L
    private const val READ_TIMEOUT = 120L
    private const val WRITE_TIMEOUT = 120L
    
    /**
     * 🔧 OkHttp 클라이언트 (싱글톤)
     */
    val okHttpClient: OkHttpClient by lazy {
        Log.d(TAG, "🔧 OkHttpClient 초기화 중...")
        Log.d(TAG, "  ⏱️ Timeouts: connect=${CONNECT_TIMEOUT}s, read=${READ_TIMEOUT}s, write=${WRITE_TIMEOUT}s")
        OkHttpClient.Builder()
            .connectTimeout(CONNECT_TIMEOUT, TimeUnit.SECONDS)
            .readTimeout(READ_TIMEOUT, TimeUnit.SECONDS)
            .writeTimeout(WRITE_TIMEOUT, TimeUnit.SECONDS)
            .addInterceptor(createLoggingInterceptor())
            .addInterceptor(createHeaderInterceptor())
            .build().also {
                Log.d(TAG, "✅ OkHttpClient 생성 완료")
            }
    }
    
    /**
     * 📝 로깅 인터셉터
     */
    private fun createLoggingInterceptor(): HttpLoggingInterceptor {
        return HttpLoggingInterceptor { message ->
            Log.d(TAG, "🌐 HTTP: $message")
        }.apply {
            level = HttpLoggingInterceptor.Level.BODY
        }
    }
    
    /**
     * 📋 공통 헤더 인터셉터
     */
    private fun createHeaderInterceptor() = okhttp3.Interceptor { chain ->
        val original = chain.request()
        Log.d(TAG, "📤 Request: ${original.method} ${original.url}")
        
        val requestBuilder = original.newBuilder()
            .header("Content-Type", "application/json")
            .header("Accept", "application/json")
            .header("User-Agent", "InsightDeal-Android/1.0")
        
        val request = requestBuilder.build()
        val startTime = System.currentTimeMillis()
        
        try {
            val response = chain.proceed(request)
            val duration = System.currentTimeMillis() - startTime
            Log.d(TAG, "📥 Response: ${response.code} in ${duration}ms")
            response
        } catch (e: Exception) {
            val duration = System.currentTimeMillis() - startTime
            Log.e(TAG, "❌ Request failed after ${duration}ms: ${e.message}", e)
            throw e
        }
    }
    
    /**
     * 🔧 Gson 설정
     */
    private val gson = GsonBuilder()
        .setLenient()
        .setDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'")
        .create()
    
    /**
     * 🌐 Retrofit 인스턴스 (싱글톤)
     */
    val retrofit: Retrofit by lazy {
        Log.d(TAG, "🌐 Retrofit 초기화 중...")
        Log.d(TAG, "  🔗 BASE_URL: $BASE_URL")
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create(gson))
            .build().also {
                Log.d(TAG, "✅ Retrofit 생성 완료")
            }
    }
    
    /**
     * 📡 API 서비스 생성 헬퍼
     */
    inline fun <reified T> createService(): T {
        val tag = "NetworkModule" // inline 함수는 클래스 멤버에 접근 불가
        val serviceName = T::class.java.simpleName
        Log.d(tag, "📡 Creating service: $serviceName")
        return retrofit.create(T::class.java).also {
            Log.d(tag, "✅ Service created: $serviceName")
        }
    }
}

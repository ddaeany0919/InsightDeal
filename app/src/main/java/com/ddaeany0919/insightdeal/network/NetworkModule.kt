package com.ddaeany0919.insightdeal.network

import android.util.Log
import com.ddaeany0919.insightdeal.BuildConfig
import com.google.gson.GsonBuilder
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

/**
 * 통합 네트워크 모듈
 *
 * 모든 Repository가 공유하는 단일 Retrofit 인스턴스
 */
object NetworkModule {
    
    internal const val TAG = "NetworkModule"
    
    // BuildConfig.BASE_URL 연동 (Debug: 10.0.2.2:8080, Release: 운영 서버 URL)
    val BASE_URL: String = BuildConfig.BASE_URL
    
    // 빠른 실패 및 무한 로딩 방지를 위한 타임아웃 설정
    private const val CONNECT_TIMEOUT = 8L
    private const val READ_TIMEOUT = 15L
    private const val WRITE_TIMEOUT = 15L
    
    /**
     * OkHttp 클라이언트 (싱글톤)
     */
    val okHttpClient: OkHttpClient by lazy {
        if (BuildConfig.DEBUG_MODE) {
            Log.d(TAG, "OkHttpClient 초기화 중 (BASE_URL=$BASE_URL)")
        }
        OkHttpClient.Builder()
            .connectTimeout(CONNECT_TIMEOUT, TimeUnit.SECONDS)
            .readTimeout(READ_TIMEOUT, TimeUnit.SECONDS)
            .writeTimeout(WRITE_TIMEOUT, TimeUnit.SECONDS)
            .addInterceptor(createLoggingInterceptor())
            .addInterceptor(createHeaderInterceptor())
            .build()
    }
    
    /**
     * 로깅 인터셉터 (출시 빌드에서는 본문 노출 및 성능 저하 방지를 위해 비활성화)
     */
    private fun createLoggingInterceptor(): HttpLoggingInterceptor {
        return HttpLoggingInterceptor { message ->
            if (BuildConfig.DEBUG_MODE) {
                Log.d(TAG, "HTTP: $message")
            }
        }.apply {
            level = if (BuildConfig.DEBUG_MODE) {
                HttpLoggingInterceptor.Level.BODY
            } else {
                HttpLoggingInterceptor.Level.NONE
            }
        }
    }
    
    /**
     * 공통 헤더 인터셉터
     */
    private fun createHeaderInterceptor() = okhttp3.Interceptor { chain ->
        val original = chain.request()
        val requestBuilder = original.newBuilder()
            .header("Content-Type", "application/json")
            .header("Accept", "application/json")
            .header("User-Agent", "InsightDeal-Android/${BuildConfig.VERSION_NAME}")
        
        val request = requestBuilder.build()
        val startTime = System.currentTimeMillis()
        
        try {
            val response = chain.proceed(request)
            if (BuildConfig.DEBUG_MODE) {
                val duration = System.currentTimeMillis() - startTime
                Log.d(TAG, "Response: ${response.code} in ${duration}ms")
            }
            response
        } catch (e: Exception) {
            if (BuildConfig.DEBUG_MODE) {
                val duration = System.currentTimeMillis() - startTime
                Log.e(TAG, "Request failed after ${duration}ms: ${e.message}", e)
            }
            throw e
        }
    }
    
    /**
     * Gson 설정
     */
    private val gson = GsonBuilder()
        .setLenient()
        .setDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'")
        .create()
    
    /**
     * Retrofit 인스턴스 (싱글톤)
     */
    val retrofit: Retrofit by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create(gson))
            .build()
    }
    
    /**
     * API 서비스 생성 헬퍼
     */
    inline fun <reified T> createService(): T {
        return retrofit.create(T::class.java)
    }
}

package com.ddaeany0919.insightdeal.network

import com.google.gson.GsonBuilder
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

/**
 * 🌐 Retrofit 클라이언트 싱글톤
 */
object RetrofitClient {
    
    // 개발 환경에서는 localhost, 프로덕션에서는 실제 서버 URL
    private const val BASE_URL = "http://10.0.2.2:8000/api/" // Android 에뮬레이터용
    // private const val BASE_URL = "http://localhost:8000/api/" // 실제 디바이스용
    // private const val BASE_URL = "https://api.insightdeal.com/" // 프로덕션용
    
    /**
     * 🔧 OkHttp 클라이언트 설정
     */
    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .addInterceptor(createLoggingInterceptor())
        .addInterceptor(createHeaderInterceptor())
        .build()
    
    /**
     * 📝 로깅 인터셉터 생성
     */
    private fun createLoggingInterceptor(): HttpLoggingInterceptor {
        return HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BODY
        }
    }
    
    /**
     * 📋 헤더 인터셉터 생성
     */
    private fun createHeaderInterceptor() = okhttp3.Interceptor { chain ->
        val original = chain.request()
        val requestBuilder = original.newBuilder()
            .header("Content-Type", "application/json")
            .header("Accept", "application/json")
            .header("User-Agent", "InsightDeal-Android/1.0")
            
        // FCM 토큰이 있으면 헤더에 추가
        // val fcmToken = getFCMToken()
        // if (fcmToken.isNotEmpty()) {
        //     requestBuilder.header("FCM-Token", fcmToken)
        // }
        
        chain.proceed(requestBuilder.build())
    }
    
    /**
     * 🔧 Gson 설정
     */
    private val gson = GsonBuilder()
        .setLenient()
        .setDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'")
        .create()
    
    /**
     * 🌐 Retrofit 인스턴스
     */
    private val retrofit = Retrofit.Builder()
        .baseUrl(BASE_URL)
        .client(okHttpClient)
        .addConverterFactory(GsonConverterFactory.create(gson))
        .build()
    
    /**
     * 📡 API 서비스 인스턴스
     */
    val apiService: ApiService = retrofit.create(ApiService::class.java)
    
    /**
     * 🔧 베이스 URL 동적 변경 (개발/프로덕션 전환용)
     */
    fun createApiService(baseUrl: String): ApiService {
        val customRetrofit = Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create(gson))
            .build()
            
        return customRetrofit.create(ApiService::class.java)
    }
}
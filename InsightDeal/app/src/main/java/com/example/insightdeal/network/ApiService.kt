package com.example.insightdeal.network

import com.example.insightdeal.model.DealDetail
import com.example.insightdeal.model.DealItem
import com.example.insightdeal.model.PriceHistoryItem
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query
import java.util.concurrent.TimeUnit

// OkHttpClient 빌더에 커넥션 타임아웃, 읽기 및 쓰기 타임아웃을 60초로 설정하여 네트워크 안정성 강화
private val okHttpClient = OkHttpClient.Builder()
    .connectTimeout(60, TimeUnit.SECONDS)
    .readTimeout(60, TimeUnit.SECONDS)
    .writeTimeout(60, TimeUnit.SECONDS)
    .build()

// Retrofit 인스턴스 생성: BASE_URL 사용, OkHttpClient 지정, Gson 컨버터러 추가
private val retrofit: Retrofit = Retrofit.Builder()
    .baseUrl(ApiClient.BASE_URL)
    .client(okHttpClient)
    .addConverterFactory(GsonConverterFactory.create())
    .build()

interface ApiService {
    /**
     * 페이지 별 상품 목록 조회
     * @param page 페이지 번호
     * @param pageSize 페이지 크기 기본값 20
     * @return 상품 목록 리스트
     */
    @GET("api/deals")
    suspend fun getDeals(
        @Query("page") page: Int,
        @Query("page_size") pageSize: Int = 20
    ): List<DealItem>

    /**
     * 특정 상품 상세정보 조회
     * @param dealId 상품 ID
     * @return 상품 상세정보
     */
    @GET("api/deals/{dealId}")
    suspend fun getDealDetail(@Path("dealId") dealId: Int): DealDetail

    /**
     * 특정 상품 가격 변동 내역 조회
     * @param dealId 상품 ID
     * @return 가격 변동 이력 리스트
     */
    @GET("api/deals/{dealId}/history")
    suspend fun getPriceHistory(@Path("dealId") dealId: Int): List<PriceHistoryItem>
}

/**
 * ApiClient 싱글톤 오브젝트
 */
object ApiClient {
    // 개발 서버 로컬 IP - 필요 시 실제 서버 주소로 변경
    const val BASE_URL = "http://192.168.0.4:8000/"

    // Retrofit 인터페이스 ApiService 생성 (lazy 초기화)
    val apiService: ApiService by lazy {
        retrofit.create(ApiService::class.java)
    }
}

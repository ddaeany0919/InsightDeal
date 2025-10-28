package com.ddaeany0919.insightdeal.network

import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Query
import retrofit2.Response
import com.google.gson.annotations.SerializedName

/**
 * 네이버 쇼핑 API 응답 데이터 클래스들
 */
data class NaverShoppingResponse(
    @SerializedName("lastBuildDate")
    val lastBuildDate: String,
    
    @SerializedName("total")
    val total: Int,
    
    @SerializedName("start")
    val start: Int,
    
    @SerializedName("display")
    val display: Int,
    
    @SerializedName("items")
    val items: List<NaverShoppingItem>
)

data class NaverShoppingItem(
    @SerializedName("title")
    val title: String,
    
    @SerializedName("link")
    val link: String,
    
    @SerializedName("image")
    val image: String,
    
    @SerializedName("lprice")
    val lprice: String, // 최저가
    
    @SerializedName("hprice")
    val hprice: String, // 최고가
    
    @SerializedName("mallName")
    val mallName: String, // 쇼핑몰 이름
    
    @SerializedName("productId")
    val productId: String,
    
    @SerializedName("productType")
    val productType: String,
    
    @SerializedName("brand")
    val brand: String,
    
    @SerializedName("maker")
    val maker: String,
    
    @SerializedName("category1")
    val category1: String,
    
    @SerializedName("category2")
    val category2: String,
    
    @SerializedName("category3")
    val category3: String,
    
    @SerializedName("category4")
    val category4: String
) {
    /**
     * HTML 태그가 포함된 제목을 정리해서 반환
     */
    fun getCleanTitle(): String {
        return title.replace("<[^>]*>".toRegex(), "")
                   .replace("&lt;", "<")
                   .replace("&gt;", ">")
                   .replace("&amp;", "&")
                   .replace("&quot;", "\"")
                   .replace("&#39;", "'")
                   .trim()
    }
    
    /**
     * 최저가를 정수로 변환해서 반환
     */
    fun getLowPriceAsInt(): Int {
        return try {
            lprice.toIntOrNull() ?: 0
        } catch (e: Exception) {
            0
        }
    }
    
    /**
     * 최고가를 정수로 변환해서 반환
     */
    fun getHighPriceAsInt(): Int {
        return try {
            hprice.toIntOrNull() ?: 0
        } catch (e: Exception) {
            0
        }
    }
}

/**
 * 네이버 쇼핑 API Retrofit 서비스 인터페이스
 */
interface NaverShoppingApiService {
    
    /**
     * 네이버 쇼핑 상품 검색
     * 
     * @param clientId 네이버 API 클라이언트 ID
     * @param clientSecret 네이버 API 클라이언트 시크릿
     * @param query 검색어
     * @param display 검색 결과 출력 건수 (1~100, 기본값 10)
     * @param start 검색 시작 위치 (1~1000, 기본값 1)
     * @param sort 정렬 옵션 (sim:정확도순, date:날짜순, asc:가격오름차순, dsc:가격내림차순)
     */
    @GET("v1/search/shop.json")
    suspend fun searchProducts(
        @Header("X-Naver-Client-Id") clientId: String,
        @Header("X-Naver-Client-Secret") clientSecret: String,
        @Query("query") query: String,
        @Query("display") display: Int = 20,
        @Query("start") start: Int = 1,
        @Query("sort") sort: String = "sim"
    ): Response<NaverShoppingResponse>
}

/**
 * 네이버 쇼핑 API 클라이언트 클래스
 */
class NaverShoppingApiClient {
    
    companion object {
        // 네이버 API 클라이언트 ID/SECRET는 BuildConfig 또는 보안 저장소에서 가져와야 합니다
        private const val CLIENT_ID = "EfT2Gww3X24afLxHMB9C" // TODO: BuildConfig로 이동
        private const val CLIENT_SECRET = "5YYnld17Rv" // TODO: BuildConfig로 이동
        
        private const val BASE_URL = "https://openapi.naver.com/"
    }
    
    private val apiService: NaverShoppingApiService by lazy {
        RetrofitClient.getClient(BASE_URL).create(NaverShoppingApiService::class.java)
    }
    
    /**
     * 상품 검색
     */
    suspend fun searchProducts(
        query: String,
        display: Int = 20,
        start: Int = 1,
        sort: String = "sim"
    ): Result<NaverShoppingResponse> {
        return try {
            val response = apiService.searchProducts(
                clientId = CLIENT_ID,
                clientSecret = CLIENT_SECRET,
                query = query,
                display = display,
                start = start,
                sort = sort
            )
            
            if (response.isSuccessful) {
                response.body()?.let { body ->
                    Result.success(body)
                } ?: Result.failure(Exception("Empty response body"))
            } else {
                Result.failure(Exception("API Error: ${response.code()} - ${response.message()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * 가격 범위로 상품 검색
     */
    suspend fun searchProductsByPriceRange(
        query: String,
        minPrice: Int,
        maxPrice: Int,
        display: Int = 50
    ): Result<List<NaverShoppingItem>> {
        return try {
            val searchResult = searchProducts(query, display)
            
            searchResult.fold(
                onSuccess = { response ->
                    val filteredItems = response.items.filter { item ->
                        val price = item.getLowPriceAsInt()
                        price in minPrice..maxPrice
                    }
                    Result.success(filteredItems)
                },
                onFailure = { exception ->
                    Result.failure(exception)
                }
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * 특정 쇼핑몰의 상품만 검색 (필터링)
     */
    suspend fun searchProductsByMall(
        query: String,
        mallName: String,
        display: Int = 50
    ): Result<List<NaverShoppingItem>> {
        return try {
            val searchResult = searchProducts(query, display)
            
            searchResult.fold(
                onSuccess = { response ->
                    val filteredItems = response.items.filter { item ->
                        item.mallName.contains(mallName, ignoreCase = true)
                    }
                    Result.success(filteredItems)
                },
                onFailure = { exception ->
                    Result.failure(exception)
                }
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}

/**
 * 사용 예시:
 * 
 * ```kotlin
 * class ProductRepository {
 *     private val naverApiClient = NaverShoppingApiClient()
 *     
 *     suspend fun searchProducts(query: String): List<NaverShoppingItem> {
 *         return naverApiClient.searchProducts(query).fold(
 *             onSuccess = { response -> response.items },
 *             onFailure = { 
 *                 Log.e("ProductRepository", "Search failed", it)
 *                 emptyList()
 *             }
 *         )
 *     }
 * }
 * ```
 */
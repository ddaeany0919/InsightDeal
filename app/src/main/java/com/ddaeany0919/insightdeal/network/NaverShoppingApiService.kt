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
    fun getCleanTitle(): String {
        return title.replace("<[^>]*>".toRegex(), "")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&amp;", "&")
            .replace("&quot;", "\"")
            .replace("&#39;", "'")
            .trim()
    }
    fun getLowPriceAsInt(): Int = lprice.toIntOrNull() ?: 0
    fun getHighPriceAsInt(): Int = hprice.toIntOrNull() ?: 0
}

interface NaverShoppingApiService {
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

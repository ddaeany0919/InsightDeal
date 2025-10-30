package com.ddaeany0919.insightdeal.data.network

import com.ddaeany0919.insightdeal.BuildConfig
import com.ddaeany0919.insightdeal.presentation.wishlist.*
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.*

/**
 * 💎 관심상품 API 서비스
 */
interface WishlistApiService {
    @POST("api/wishlist")
    suspend fun createWishlist(@Body request: WishlistCreateRequest): WishlistApiResponse

    @GET("api/wishlist")
    suspend fun getWishlist(
        @Query("user_id") userId: String = "default",
        @Query("active_only") activeOnly: Boolean = true
    ): List<WishlistApiResponse>

    @GET("api/wishlist/{wishlist_id}")
    suspend fun getWishlistItem(
        @Path("wishlist_id") wishlistId: Int,
        @Query("user_id") userId: String = "default"
    ): WishlistApiResponse

    @PUT("api/wishlist/{wishlist_id}")
    suspend fun updateWishlist(
        @Path("wishlist_id") wishlistId: Int,
        @Body request: WishlistUpdateRequest,
        @Query("user_id") userId: String = "default"
    ): WishlistApiResponse

    @DELETE("api/wishlist/{wishlist_id}")
    suspend fun deleteWishlist(
        @Path("wishlist_id") wishlistId: Int,
        @Query("user_id") userId: String = "default"
    ): DeleteResponse

    @POST("api/wishlist/{wishlist_id}/check-price")
    suspend fun checkWishlistPrice(
        @Path("wishlist_id") wishlistId: Int,
        @Query("user_id") userId: String = "default"
    ): PriceCheckResponse

    @GET("api/wishlist/{wishlist_id}/history")
    suspend fun getWishlistPriceHistory(
        @Path("wishlist_id") wishlistId: Int,
        @Query("user_id") userId: String = "default",
        @Query("days") days: Int = 30
    ): List<PriceHistoryApiResponse>

    companion object {
        private const val BASE_URL: String = BuildConfig.BASE_URL

        fun create(): WishlistApiService {
            return Retrofit.Builder()
                .baseUrl(BASE_URL)
                .addConverterFactory(GsonConverterFactory.create())
                .build()
                .create(WishlistApiService::class.java)
        }
    }
}

// ======= 네트워크 데이터 모델들 =======

data class WishlistUpdateRequest(
    val targetPrice: Int? = null,
    val isActive: Boolean? = null,
    val alertEnabled: Boolean? = null
)

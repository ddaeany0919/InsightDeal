package com.ddaeany0919.insightdeal.data.network

import com.ddaeany0919.insightdeal.BuildConfig
import com.ddaeany0919.insightdeal.presentation.wishlist.*
import com.google.gson.annotations.SerializedName
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.*

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

    // A. DELETE with body
    @HTTP(method = "DELETE", path = "api/wishlist/{wishlist_id}", hasBody = true)
    suspend fun deleteWithBody(
        @Path("wishlist_id") wishlistId: Int,
        @Body request: DeleteRequest
    ): Response<Unit>

    // B. DELETE with header
    @DELETE("api/wishlist/{wishlist_id}")
    suspend fun deleteWithHeader(
        @Path("wishlist_id") wishlistId: Int,
        @Header("X-User-Id") userId: String
    ): Response<Unit>

    // C. DELETE with query
    @DELETE("api/wishlist/{wishlist_id}")
    suspend fun deleteWithQuery(
        @Path("wishlist_id") wishlistId: Int,
        @Query("user_id") userId: String
    ): Response<Unit>

    // D. alt path {id}
    @HTTP(method = "DELETE", path = "api/wishlist/{id}", hasBody = true)
    suspend fun deleteAltPath(
        @Path("id") id: Int,
        @Body request: DeleteRequest
    ): Response<Unit>

    // E. POST fallback
    @POST("api/wishlist/{wishlist_id}/delete")
    suspend fun postDelete(
        @Path("wishlist_id") wishlistId: Int,
        @Body request: DeleteRequest
    ): Response<Unit>

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
        fun create(): WishlistApiService = Retrofit.Builder()
            .baseUrl(BASE_URL)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(WishlistApiService::class.java)
    }
}

data class WishlistUpdateRequest(
    val targetPrice: Int? = null,
    val isActive: Boolean? = null,
    val alertEnabled: Boolean? = null
)

data class DeleteRequest(@SerializedName("user_id") val userId: String)

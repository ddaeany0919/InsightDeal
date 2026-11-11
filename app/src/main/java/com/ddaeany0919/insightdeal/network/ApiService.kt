package com.ddaeany0919.insightdeal.network

import com.ddaeany0919.insightdeal.models.DealItem
import com.ddaeany0919.insightdeal.models.DealStats
import com.ddaeany0919.insightdeal.models.PriceHistoryPoint
import com.google.gson.annotations.SerializedName
import retrofit2.Response
import retrofit2.http.*

/**
 * InsightDeal API 서비스 인터페이스 (통합 버전)
 */
interface ApiService {

    // 핫딜 목록 조회
    @GET("api/product/deals")
    suspend fun getDeals(
        @Query("page") page: Int = 1,
        @Query("limit") limit: Int = 20,
        @Query("category") category: String? = null,
        @Query("site") site: String? = null,
        @Query("sort") sort: String = "latest"
    ): Response<DealsResponse>

    // 핫딜 검색
    @GET("deals/search")
    suspend fun searchDeals(
        @Query("q") query: String,
        @Query("page") page: Int = 1,
        @Query("limit") limit: Int = 20,
        @Query("min_price") minPrice: Int? = null,
        @Query("max_price") maxPrice: Int? = null,
        @Query("min_discount") minDiscount: Int? = null,
        @Query("category") category: String? = null,
        @Query("site") site: String? = null,
        @Query("sort") sort: String = "relevance"
    ): Response<DealsResponse>

    // 핫딜 통계
    @GET("deals/stats")
    suspend fun getDealStats(): Response<DealStats>

    // 특정 핫딜 상세 정보
    @GET("deals/{id}")
    suspend fun getDealDetail(@Path("id") dealId: Int): Response<DealDetail>

    // 핫딜 가격 히스토리
    @GET("api/community/deals/{dealId}/history")
    suspend fun getDealPriceHistory(
        @Path("dealId") dealId: Int,
        @Query("period") period: String = "7d",
        @Query("query") query: String? = null,
        @Query("currency") currency: String? = null
    ): Response<List<PriceHistoryPoint>>

    // 커뮤니티 핫딜 단건 상세 조회
    @GET("api/community/deals/{dealId}")
    suspend fun getEnhancedDealInfo(@Path("dealId") dealId: Int): Response<DealItem>

    // 쿠팡 상품 추적 API
    @GET("products")
    suspend fun getUserProducts(
        @Query("user_id") userId: String
    ): Response<List<ApiProduct>>

    @POST("products")
    suspend fun addProduct(
        @Body request: Map<String, Any>
    ): Response<ApiProduct>

    @GET("products/{productId}")
    suspend fun getProduct(
        @Path("productId") productId: Int
    ): Response<ApiProduct>

    @GET("products/{productId}/history")
    suspend fun getProductPriceHistory(
        @Path("productId") productId: Int
    ): Response<List<ApiPriceHistory>>

    @PUT("products/{productId}/target")
    suspend fun updateTargetPrice(
        @Path("productId") productId: Int,
        @Body request: Map<String, Any>
    ): Response<ApiProduct>

    @DELETE("products/{productId}")
    suspend fun deleteProduct(
        @Path("productId") productId: Int
    ): Response<Unit>

    // FCM 토큰 관리 API
    @POST("api/push/device")
    suspend fun registerFCMToken(
        @Body request: RegisterDeviceReq
    ): Response<ApiResponse>

    @POST("fcm/test")
    suspend fun sendTestPush(
        @Body request: Map<String, String>
    ): Response<ApiResponse>

    @POST("alerts/keyword")
    suspend fun registerKeywordAlert(
        @Body request: KeywordAlertRequest
    ): Response<ApiResponse>

    @POST("alerts/price")
    suspend fun registerPriceAlert(
        @Body request: PriceAlertRequest
    ): Response<ApiResponse>

    @GET("api/community/popular-keywords")
    suspend fun getPopularKeywords(
        @Query("limit") limit: Int = 10
    ): Response<com.ddaeany0919.insightdeal.network.PopularKeywordsResponse>

    @GET("categories")
    suspend fun getCategories(): Response<List<Category>>

    @GET("sites")
    suspend fun getSupportedSites(): Response<List<Site>>

    // 핫딜 푸시 알람 키워드 관리 API
    @GET("api/push/keywords")
    suspend fun getPushKeywords(
        @Query("device_uuid") deviceUuid: String
    ): Response<KeywordListResponse>

    @POST("api/push/keywords")
    suspend fun addPushKeyword(
        @Body request: AddKeywordRequest
    ): Response<ApiResponse>

    @POST("api/push/keywords/toggle")
    suspend fun togglePushKeyword(
        @Body request: AddKeywordRequest
    ): Response<ApiResponse>

    @HTTP(method = "DELETE", path = "api/push/keywords", hasBody = true)
    suspend fun deletePushKeyword(
        @Body request: AddKeywordRequest
    ): Response<ApiResponse>

    // ==========================================
    // Wishlist API
    // ==========================================
    @POST("api/wishlist/from-keyword")
    suspend fun createWishlistFromKeyword(@Body request: com.ddaeany0919.insightdeal.data.network.WishlistCreateFromKeywordRequest): com.ddaeany0919.insightdeal.presentation.wishlist.WishlistApiResponse

    @POST("api/wishlist/from-url")
    suspend fun createWishlistFromUrl(@Body request: com.ddaeany0919.insightdeal.data.network.WishlistCreateFromUrlRequest): com.ddaeany0919.insightdeal.presentation.wishlist.WishlistApiResponse

    @GET("api/wishlist")
    suspend fun getWishlist(
        @Query("user_id") userId: String = "default"
    ): List<com.ddaeany0919.insightdeal.presentation.wishlist.WishlistApiResponse>

    @GET("api/wishlist/{wishlist_id}")
    suspend fun getWishlistItem(
        @Path("wishlist_id") wishlistId: Int,
        @Query("user_id") userId: String = "default"
    ): com.ddaeany0919.insightdeal.presentation.wishlist.WishlistApiResponse

    @PATCH("api/wishlist/{wishlist_id}")
    suspend fun updateWishlist(
        @Path("wishlist_id") wishlistId: Int,
        @Body request: com.ddaeany0919.insightdeal.data.network.WishlistUpdateRequest,
        @Query("user_id") userId: String = "default"
    ): com.ddaeany0919.insightdeal.presentation.wishlist.WishlistApiResponse

    @DELETE("api/wishlist/{wishlist_id}")
    suspend fun deleteWithQuery(
        @Path("wishlist_id") wishlistId: Int,
        @Query("user_id") userId: String
    ): Response<com.ddaeany0919.insightdeal.presentation.wishlist.DeleteResponse>

    @POST("api/wishlist/{wishlist_id}/check-price")
    suspend fun checkWishlistPrice(
        @Path("wishlist_id") wishlistId: Int,
        @Query("user_id") userId: String = "default"
    ): com.ddaeany0919.insightdeal.presentation.wishlist.PriceCheckResponse

    @GET("api/wishlist/{wishlist_id}/history")
    suspend fun getWishlistPriceHistory(
        @Path("wishlist_id") wishlistId: Int,
        @Query("user_id") userId: String = "default",
        @Query("days") days: Int = 30
    ): List<com.ddaeany0919.insightdeal.presentation.wishlist.PriceHistoryApiResponse>

    @GET("api/wishlist/price-history")
    suspend fun getPriceHistory(
        @Query("user_id") userId: String
    ): Response<List<com.ddaeany0919.insightdeal.presentation.wishlist.PriceHistoryApiResponse>>

    @POST("api/wishlist/alarm")
    suspend fun updateAlarmState(
        @Body request: com.ddaeany0919.insightdeal.presentation.wishlist.UpdateAlarmRequest
    ): Response<Unit>

    @POST("api/product/analyze-link")
    suspend fun analyzeLink(@Body request: com.ddaeany0919.insightdeal.data.network.AnalyzeLinkRequest): com.ddaeany0919.insightdeal.data.network.ProductAnalysisResponse

    // ==========================================
    // Monetization & Community API
    // ==========================================
    @POST("api/coupang/track-click")
    suspend fun trackOutboundClick(
        @Body request: TrackClickRequest
    ): Response<TrackClickResponse>

    @GET("/api/health")
    suspend fun healthCheck(): Response<HealthResponse>

    @GET("api/community/hot-deals")
    suspend fun getCommunityHotDeals(
        @Query("limit") limit: Int = 20,
        @Query("offset") offset: Int = 0,
        @Query("category") category: String? = null,
        @Query("keyword") keyword: String? = null,
        @Query("platform") platform: String? = null,
        @Query("sort") sort: String = "latest"
    ): Response<HotDealsResponse>

    @GET("api/community/top-hot-deals")
    suspend fun getTopHotDeals(): Response<HotDealsResponse>

    @GET("api/community/hot-deals")
    suspend fun getHotDealsDto(
        @Query("category") category: String? = null
    ): com.ddaeany0919.insightdeal.data.HotDealResponse

    @GET("api/community/posts")
    suspend fun getCommunityPosts(
        @Query("post_type") postType: String? = null
    ): Response<List<com.ddaeany0919.insightdeal.data.network.CommunityPostDto>>

    @GET("api/community/posts/{post_id}")
    suspend fun getCommunityPost(@Path("post_id") postId: Int): Response<com.ddaeany0919.insightdeal.data.network.CommunityPostDto>

    @POST("api/community/posts")
    suspend fun createCommunityPost(@Body request: com.ddaeany0919.insightdeal.data.network.CommunityPostCreateReq): Response<com.ddaeany0919.insightdeal.data.network.CommunityPostDto>

    @PUT("api/community/posts/{post_id}")
    suspend fun updateCommunityPost(@Path("post_id") postId: Int, @Body request: com.ddaeany0919.insightdeal.data.network.CommunityPostCreateReq): Response<com.ddaeany0919.insightdeal.data.network.CommunityPostDto>

    @POST("api/community/posts/{post_id}/comments")
    suspend fun createCommunityComment(
        @Path("post_id") postId: Int,
        @Body request: com.ddaeany0919.insightdeal.data.network.CommunityCommentCreateReq
    ): Response<com.ddaeany0919.insightdeal.data.network.CommunityCommentDto>

    @POST("api/community/posts/{post_id}/comments/{comment_id}/accept")
    suspend fun acceptCommunityComment(
        @Path("post_id") postId: Int,
        @Path("comment_id") commentId: Int,
        @Query("user_id") userId: String
    ): Response<Unit>

    @PUT("api/community/posts/{post_id}/comments/{comment_id}")
    suspend fun updateCommunityComment(
        @Path("post_id") postId: Int,
        @Path("comment_id") commentId: Int,
        @Body request: com.ddaeany0919.insightdeal.data.network.CommunityCommentCreateReq
    ): Response<com.ddaeany0919.insightdeal.data.network.CommunityCommentDto>

    @DELETE("api/community/posts/{post_id}/comments/{comment_id}")
    suspend fun deleteCommunityComment(
        @Path("post_id") postId: Int,
        @Path("comment_id") commentId: Int,
        @Query("user_id") userId: String
    ): Response<Unit>

    // Deal Detail enhancements
    @GET("api/community/deals/{id}/comments")
    suspend fun getDealComments(@Path("id") dealId: Int): Response<List<ApiDealComment>>

    @GET("api/community/users/{user_id}/comments")
    suspend fun getUserComments(@Path("user_id") userId: String): Response<List<UserCommentDto>>

    @GET("api/community/deals/{id}/votes")
    suspend fun getDealVotes(
        @Path("id") dealId: Int,
        @Query("user_id") userId: String
    ): Response<ApiDealVotes>

    @POST("api/community/deals/{id}/comments")
    suspend fun addDealComment(
        @Path("id") dealId: Int,
        @Body req: ApiCommentCreate
    ): Response<ApiDealComment>

    @POST("api/community/deals/{id}/votes")
    suspend fun addDealVote(
        @Path("id") dealId: Int,
        @Body req: ApiVoteCreate
    ): Response<ApiDealVotes>

    // 실시간 알림 연동 API
    @GET("api/users/notifications")
    suspend fun getUserNotifications(
        @Query("user_id") userId: String
    ): Response<List<NotificationAlertDto>>

    @POST("api/users/notifications")
    suspend fun addUserNotification(
        @Body request: AddNotificationRequest
    ): Response<ApiResponse>

    @DELETE("api/users/notifications/{alert_id}")
    suspend fun deleteUserNotification(
        @Path("alert_id") alertId: Int,
        @Query("user_id") userId: String
    ): Response<ApiResponse>

    @POST("api/users/notifications/clear")
    suspend fun clearUserNotifications(
        @Query("user_id") userId: String
    ): Response<ApiResponse>

    @GET("api/users/keywords")
    suspend fun getUserKeywords(
        @Query("user_id") userId: String
    ): Response<KeywordListResponse>

    @POST("api/users/keywords")
    suspend fun addUserKeyword(
        @Body request: AddKeywordRequest
    ): Response<ApiResponse>

    @POST("api/users/keywords/toggle")
    suspend fun toggleUserKeyword(
        @Body request: AddKeywordRequest
    ): Response<ApiResponse>

    @HTTP(method = "DELETE", path = "api/users/keywords", hasBody = true)
    suspend fun deleteUserKeyword(
        @Body request: AddKeywordRequest,
        @Query("user_id") userId: String? = request.device_uuid,
        @Query("keyword") keyword: String? = request.keyword
    ): Response<ApiResponse>

    // 회원 탈퇴 API
    @POST("api/auth/withdraw")
    suspend fun withdrawUser(
        @Body request: UserWithdrawRequest
    ): Response<ApiResponse>
}

/**
 * 아웃링크 수익화 클릭 트래킹 요청 DTO (/api/coupang/track-click)
 */
data class TrackClickRequest(
    @SerializedName("url") val url: String,
    @SerializedName("deal_id") val dealId: Int? = null,
    @SerializedName("price") val price: Int? = 0,
    @SerializedName("currency") val currency: String? = "KRW",
    @SerializedName("platform") val platform: String = "android"
)

/**
 * 아웃링크 수익화 클릭 트래킹 응답 DTO (/api/coupang/track-click)
 */
data class TrackClickResponse(
    @SerializedName("status") val status: String = "ok",
    @SerializedName("mall_name") val mallName: String? = null,
    @SerializedName("affiliate_network") val affiliateNetwork: String? = null,
    @SerializedName("is_monetized") val isMonetized: Boolean? = null,
    @SerializedName("estimated_commission_krw") val estimatedCommissionKrw: Double? = null
)

data class UserWithdrawRequest(
    @SerializedName("username") val username: String,
    @SerializedName("password") val password: String
)

data class ApiDealComment(
    val id: Int,
    val user_id: String,
    val content: String,
    val created_at: String
)

data class ApiDealVotes(
    val buy_count: Int,
    val pass_count: Int,
    val user_vote: String? = null
)

data class ApiCommentCreate(
    val user_id: String,
    val content: String
)

data class ApiVoteCreate(
    val user_id: String,
    val vote_type: String
)

data class DetailedKeywordDto(
    val id: Int,
    val keyword: String,
    val is_active: Boolean
)

data class KeywordListResponse(
    val keywords: List<String>,
    val detailed_keywords: List<DetailedKeywordDto>? = null,
    val dnd_enabled: Boolean? = null,
    val dnd_start_time: String? = null,
    val dnd_end_time: String? = null,
    val dnd_settings_json: String? = null,
    val night_push_consent: Boolean? = null
)

data class AddKeywordRequest(
    @SerializedName("user_id") val device_uuid: String,
    val keyword: String
)

data class DealsResponse(
    val deals: List<DealItem>,
    val total: Int,
    val page: Int,
    val totalPages: Int,
    val hasNext: Boolean
)

data class ApiResponse(
    val success: Boolean,
    val message: String,
    val data: Any? = null
)

data class ApiProduct(
    val id: Int,
    val title: String,
    val brand: String?,
    val image_url: String?,
    val current_price: Int?,
    val original_price: Int?,
    val lowest_price: Int?,
    val highest_price: Int?,
    val target_price: Int?,
    val url: String,
    val created_at: String,
    val last_checked: String?
)

data class ApiPriceHistory(
    val price: Int,
    val original_price: Int?,
    val discount_rate: Int?,
    val tracked_at: String,
    val is_available: Boolean
)

data class DealDetail(
    val id: Int,
    val title: String,
    val description: String?,
    val price: Int,
    val originalPrice: Int?,
    val discountRate: Int?,
    val imageUrl: String?,
    val url: String,
    val siteName: String,
    val createdAt: String,
    val viewCount: Int,
    val likeCount: Int,
    val commentCount: Int
)

data class PriceHistoryItem(
    val price: Int,
    val originalPrice: Int?,
    val discountRate: Int?,
    val recordedAt: String
)

data class EnhancedDealInfo(
    val qualityScore: Int,
    val priceRank: String,
    val similarDeals: List<DealItem>,
    val priceAlert: String?,
    val recommendation: String?
)

data class RegisterDeviceReq(
    val device_uuid: String,
    val fcm_token: String,
    val night_push_consent: Boolean = false,
    val dnd_enabled: Boolean = false,
    val dnd_start_time: String = "21:00",
    val dnd_end_time: String = "08:00",
    val dnd_settings_json: String? = null
)

data class KeywordAlertRequest(
    val keyword: String,
    val fcmToken: String,
    val minDiscount: Int? = null,
    val maxPrice: Int? = null
)

data class PriceAlertRequest(
    val dealId: Long,
    val targetPrice: Int,
    val fcmToken: String
)

data class Category(
    val id: String,
    val name: String,
    val icon: String? = null
)

data class Site(
    val id: String,
    val name: String,
    val url: String,
    val icon: String? = null
)

data class UserCommentDto(
    val id: Int,
    val post_id: Int,
    val post_title: String,
    val user_id: String,
    val nickname: String,
    val content: String,
    val deal_url: String?,
    val is_accepted: Boolean,
    val parent_id: Int?,
    val created_at: String?
)

data class NotificationAlertDto(
    val id: String,
    val title: String,
    val keyword: String,
    val receivedAt: Long,
    val dealUrl: String
)

data class AddNotificationRequest(
    val user_id: String,
    val title: String,
    val keyword: String,
    val deal_url: String
)

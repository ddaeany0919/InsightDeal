package com.ddaeany0919.insightdeal.data.network

import com.google.gson.annotations.SerializedName

data class CommunityPostDto(
    @SerializedName("id") val id: Int,
    @SerializedName("post_type") val postType: String,
    @SerializedName("user_id") val userId: String,
    @SerializedName("nickname") val nickname: String,
    @SerializedName("title") val title: String,
    @SerializedName("content") val content: String,
    @SerializedName("target_price") val targetPrice: Int?,
    @SerializedName("bounty_points") val bountyPoints: Int,
    @SerializedName("is_resolved") val isResolved: Boolean,
    @SerializedName("location") val location: String?,
    @SerializedName("view_count") val viewCount: Int,
    @SerializedName("like_count") val likeCount: Int,
    @SerializedName("created_at") val createdAt: String?,
    @SerializedName("comment_count") val commentCount: Int,
    @SerializedName("comments") val comments: List<CommunityCommentDto>? = null
)

data class CommunityCommentDto(
    @SerializedName("id") val id: Int,
    @SerializedName("user_id") val userId: String,
    @SerializedName("nickname") val nickname: String,
    @SerializedName("content") val content: String,
    @SerializedName("deal_url") val dealUrl: String?,
    @SerializedName("is_accepted") val isAccepted: Boolean,
    @SerializedName("parent_id") val parentId: Int? = null,
    @SerializedName("created_at") val createdAt: String?
)

data class CommunityCommentCreateReq(
    @SerializedName("user_id") val userId: String,
    @SerializedName("nickname") val nickname: String,
    @SerializedName("content") val content: String,
    @SerializedName("deal_url") val dealUrl: String? = null,
    @SerializedName("parent_id") val parentId: Int? = null
)

data class CommunityPostCreateReq(
    @SerializedName("post_type") val postType: String,
    @SerializedName("user_id") val userId: String,
    @SerializedName("nickname") val nickname: String,
    @SerializedName("title") val title: String,
    @SerializedName("content") val content: String,
    @SerializedName("target_price") val targetPrice: Int? = null,
    @SerializedName("bounty_points") val bountyPoints: Int = 0,
    @SerializedName("like_count") val likeCount: Int = 0,
    @SerializedName("location") val location: String? = null
)

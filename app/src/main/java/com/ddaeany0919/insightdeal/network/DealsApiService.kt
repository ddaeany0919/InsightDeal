package com.ddaeany0919.insightdeal.network

import com.google.gson.annotations.SerializedName

// Interface moved to ApiService.kt

data class PopularKeywordRankingItem(
    @SerializedName("rank") val rank: Int = 0,
    @SerializedName("keyword") val keyword: String = "",
    @SerializedName("deal_count") val dealCount: Int = 0,
    @SerializedName("recent_count") val recentCount: Int = 0,
    @SerializedName("status") val status: String = "SAME"
)

data class PopularKeywordsResponse(
    @SerializedName("keywords") val keywords: List<String> = emptyList(),
    @SerializedName("rankings") val rankings: List<PopularKeywordRankingItem> = emptyList(),
    @SerializedName("updated_at") val updatedAt: String = ""
)

data class NextCursorDto(
    @SerializedName(value = "indexed_at", alternate = ["indexedAt"]) val indexedAt: String = "",
    @SerializedName("id") val id: Int = 0
)

data class HotDealsResponse(
    @SerializedName("deals") val deals: List<com.ddaeany0919.insightdeal.models.DealItem> = emptyList(),
    @SerializedName(value = "has_more", alternate = ["hasMore"]) val hasMore: Boolean = false,
    @SerializedName(value = "next_cursor", alternate = ["nextCursor"]) val nextCursor: NextCursorDto? = null
)

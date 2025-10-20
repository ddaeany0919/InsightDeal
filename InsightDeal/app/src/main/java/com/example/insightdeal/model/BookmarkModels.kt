package com.example.insightdeal.model

import com.google.gson.annotations.SerializedName

/**
 * 북마크된 딜 정보
 */
data class BookmarkItem(
    @SerializedName("id") val id: Int,
    @SerializedName("title") val title: String,
    @SerializedName("community") val community: String,
    @SerializedName("shopName") val shopName: String,
    @SerializedName("price") val price: String,
    @SerializedName("shippingFee") val shippingFee: String,
    @SerializedName("imageUrl") val imageUrl: String,
    @SerializedName("category") val category: String? = "기타",
    @SerializedName("is_closed") val isClosed: Boolean,
    @SerializedName("deal_type") val dealType: String,
    @SerializedName("ecommerce_link") val ecommerceLink: String? = null,
    val bookmarkedAt: Long = System.currentTimeMillis()  // ✅ 북마크된 시간
) {
    // DealItem에서 BookmarkItem으로 변환
    companion object {
        fun fromDealItem(dealItem: DealItem): BookmarkItem {
            return BookmarkItem(
                id = dealItem.id,
                title = dealItem.title,
                community = dealItem.community,
                shopName = dealItem.shopName,
                price = dealItem.price,
                shippingFee = dealItem.shippingFee,
                imageUrl = dealItem.imageUrl,
                category = dealItem.category,
                isClosed = dealItem.isClosed,
                dealType = dealItem.dealType,
                ecommerceLink = dealItem.ecommerceLink
            )
        }
    }

    // BookmarkItem에서 DealItem으로 변환
    fun toDealItem(): DealItem {
        return DealItem(
            id = id,
            title = title,
            community = community,
            shopName = shopName,
            price = price,
            shippingFee = shippingFee,
            imageUrl = imageUrl,
            category = category,
            isClosed = isClosed,
            dealType = dealType,
            ecommerceLink = ecommerceLink
        )
    }
}

/**
 * 검색 기록 아이템
 */
data class SearchHistoryItem(
    val query: String,
    val searchedAt: Long = System.currentTimeMillis(),
    val resultCount: Int = 0
)

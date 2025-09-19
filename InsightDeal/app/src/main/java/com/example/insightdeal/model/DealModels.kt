package com.example.insightdeal.model

import com.google.gson.annotations.SerializedName

/**
 * 상품 기본 정보 데이터 클래스
 */
data class DealItem(
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
)

/**
 * 딜 상세 데이터 클래스
 */
data class DealDetail(
    @SerializedName("id") val id: Int,
    @SerializedName("title") val title: String,
    @SerializedName("community") val community: String,
    @SerializedName("shop_name") val shopName: String,
    @SerializedName("price") val price: String,
    @SerializedName("shipping_fee") val shippingFee: String,
    @SerializedName("category") val category: String? = "기타",
    @SerializedName("content_html") val contentHtml: String?,
    @SerializedName("purchase_link") val purchaseLink: String?,
    @SerializedName("post_link") val postLink: String?,
    @SerializedName("related_deals")
    val relatedDeals: List<DealItem> = emptyList() // 기본 빈 리스트 할당으로 null 안정성 강화
)

/**
 * 가격 변동 이력 데이터 클래스
 */
data class PriceHistoryItem(
    @SerializedName("checked_at") val checkedAt: String,
    @SerializedName("price") val price: String
)

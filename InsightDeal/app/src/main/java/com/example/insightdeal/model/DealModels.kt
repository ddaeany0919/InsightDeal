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
    @SerializedName("ecommerce_link") val ecommerceLink: String? = null,
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
    @SerializedName("ecommerce_link") val ecommerceLink: String?,
    @SerializedName("post_link") val postLink: String?,
    @SerializedName("has_options") val hasOptions: Boolean = false,
    @SerializedName("options_data") val optionsData: String? = null,
    @SerializedName("base_product_name") val baseProductName: String? = null,
    @SerializedName("related_deals") val relatedDeals: List<DealItem> = emptyList()
)

/**
 * 가격 변동 이력 데이터 클래스
 */
data class PriceHistoryItem(
    @SerializedName("checked_at") val checkedAt: String,
    @SerializedName("price") val price: String
)

/**
 * 옵션 정보 데이터 클래스 (새로 추가)
 */
data class ProductOption(
    @SerializedName("option_name") val optionName: String,
    @SerializedName("price") val price: String,
    @SerializedName("ecommerce_link") val ecommerceLink: String?,
    @SerializedName("full_title") val fullTitle: String
)
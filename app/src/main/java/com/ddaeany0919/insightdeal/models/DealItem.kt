package com.ddaeany0919.insightdeal.models

import androidx.compose.runtime.Stable
import com.google.gson.Gson
import com.google.gson.JsonDeserializationContext
import com.google.gson.JsonDeserializer
import com.google.gson.JsonElement
import com.google.gson.annotations.JsonAdapter
import com.google.gson.annotations.SerializedName
import com.google.gson.reflect.TypeToken
import java.lang.reflect.Type

/**
 * 다중 옵션 아이템 데이터 모델 (웹 DealOptionItem 패리티)
 */
@Stable
data class DealOptionItem(
    @SerializedName("name") val name: String = "",
    @SerializedName("price") val price: Long = 0L,
    @SerializedName("currency") val currency: String? = "KRW",
    @SerializedName("code") val code: String? = null,
    @SerializedName("url") val url: String? = null,
    @SerializedName("image_url") val imageUrl: String? = null,
    @SerializedName("sub_urls") val subUrls: List<String>? = emptyList()
)

/**
 * 백엔드가 options_data를 JSON 문자열(String) 또는 JSON 배열(Array) 형태로 내려줄 때 모두 안전하게 파싱
 */
class OptionsDataDeserializer : JsonDeserializer<List<DealOptionItem>> {
    override fun deserialize(
        json: JsonElement?,
        typeOfT: Type?,
        context: JsonDeserializationContext?
    ): List<DealOptionItem> {
        if (json == null || json.isJsonNull) return emptyList()
        val listType = object : TypeToken<List<DealOptionItem>>() {}.type
        return try {
            when {
                json.isJsonArray -> Gson().fromJson(json, listType) ?: emptyList()
                json.isJsonPrimitive && json.asJsonPrimitive.isString -> {
                    val raw = json.asString.trim()
                    if (raw.startsWith("[")) {
                        Gson().fromJson<List<DealOptionItem>>(raw, listType) ?: emptyList()
                    } else {
                        emptyList()
                    }
                }
                else -> emptyList()
            }
        } catch (_: Exception) {
            emptyList()
        }
    }
}

/**
 * 핫딜 출처 및 옵션 소스 데이터 클래스
 */
@Stable
data class DealSource(
    @SerializedName("site_name") val siteName: String,
    @SerializedName("post_url") val postUrl: String,
    @SerializedName("ecommerce_url") val ecommerceUrl: String? = null,
    @SerializedName("price") val price: Long? = null,
    @SerializedName("currency") val currency: String? = null,
    @SerializedName("code") val code: String? = null,
    @SerializedName("image_url") val imageUrl: String? = null,
    @SerializedName("sub_urls") val subUrls: List<String>? = emptyList()
)

/**
 * 핫딜 아이템 데이터 클래스
 */
@Stable
data class DealItem(
    @SerializedName("id")
    val id: Int,

    @SerializedName("title")
    val title: String,

    @SerializedName("price")
    val price: Long,

    @SerializedName("currency")
    val currency: String = "KRW",

    @SerializedName("original_price")
    val originalPrice: Long? = null,

    @SerializedName("discount_rate")
    val discountRate: Int? = null,

    @SerializedName("image_url")
    val imageUrl: String? = null,

    @SerializedName("ecommerce_url")
    val ecommerceUrl: String? = null,

    @SerializedName("post_url")
    val postUrl: String? = null,

    @SerializedName("shop_name")
    val shopName: String? = null,

    @SerializedName("site_name")
    val siteName: String,

    @SerializedName("site_names")
    val siteNames: List<String> = emptyList(),

    @SerializedName("sources")
    val sources: List<DealSource>? = emptyList(),

    @SerializedName("shipping_fee")
    val shippingFee: String? = null,

    @SerializedName("category")
    val category: String? = null,

    @SerializedName(value = "created_at", alternate = ["indexed_at"])
    val createdAt: String? = null,

    @SerializedName(value = "view_count", alternate = ["views"])
    val viewCount: Int = 0,

    @SerializedName(value = "comment_count", alternate = ["comments"])
    val commentCount: Int = 0,

    @SerializedName(value = "like_count", alternate = ["recommendations"])
    val likeCount: Int = 0,

    @SerializedName("dislike_count")
    val dislikeCount: Int = 0,

    @SerializedName("tags")
    val tags: List<String> = emptyList(),

    @SerializedName(value = "honey_score", alternate = ["score"])
    val honeyScore: Int = 0,

    @SerializedName("ai_summary")
    val aiSummary: String? = null,

    @SerializedName("content_html")
    val contentHtml: String? = null,

    @SerializedName("is_closed")
    val isClosed: Boolean = false,

    @SerializedName("has_options")
    val hasOptions: Boolean = false,

    @SerializedName("options_count")
    val optionsCount: Int = 0,

    @SerializedName("options_data")
    @JsonAdapter(OptionsDataDeserializer::class)
    val optionsData: List<DealOptionItem> = emptyList(),

    @SerializedName("sub_urls")
    val subUrls: List<String> = emptyList()
) {
    /**
     * 할인된 가격 계산
     */
    val discountedPrice: Long
        get() = originalPrice?.let { original ->
            discountRate?.let { rate ->
                (original * (100 - rate) / 100)
            } ?: price
        } ?: price

    /**
     * 핫딜 여부 (할인율 20% 이상)
     */
    val isHotDeal: Boolean
        get() = (discountRate ?: 0) >= 20

    /**
     * 인기도 점수 (조회수, 댓글수, 좋아요 기반)
     */
    val popularityScore: Int
        get() = viewCount + (commentCount * 5) + (likeCount * 10) - (dislikeCount * 5)

    /**
     * 유효 다중 옵션 수 계산 (options_count, options_data, sources 내 옵션 항목 모두 고려)
     */
    val resolvedOptionCount: Int
        get() {
            if (optionsCount > 1) return optionsCount
            if (optionsData.size > 1) return optionsData.size
            val sourceOptionCount = sources?.count { src ->
                src.siteName.contains(" - ") && src.siteName.substringAfter(" - ").isNotBlank()
            } ?: 0
            if (sourceOptionCount > 1) return sourceOptionCount
            return if (hasOptions) maxOf(optionsCount, optionsData.size, sourceOptionCount, 2) else 0
        }

    /**
     * 다중 옵션 보유 여부
     */
    val hasMultipleOptions: Boolean
        get() = hasOptions || resolvedOptionCount > 1

    /**
     * 보조 구매 링크(sub_urls) 통합 목록 (대표 딜 및 대표 출처 기준 중복 제거)
     */
    val resolvedSubUrls: List<String>
        get() {
            val primaryUrl = ecommerceUrl?.trim().orEmpty()
            val merged = buildList {
                addAll(subUrls)
                sources?.forEach { src ->
                    if (!src.siteName.contains(" - ")) {
                        addAll(src.subUrls.orEmpty())
                    }
                }
            }
            return merged
                .map { it.trim() }
                .filter { it.startsWith("http", ignoreCase = true) && it != primaryUrl }
                .distinct()
        }
}

/**
 * 핫딜 통계 데이터
 */
data class DealStats(
    val totalDeals: Int,
    val hotDeals: Int,
    val averageDiscount: Double,
    val topCategories: List<CategoryStats>
)

data class CategoryStats(
    val category: String,
    val count: Int,
    val percentage: Double
)

/**
 * 검색 필터
 */
data class DealFilter(
    val keyword: String? = null,
    val category: String? = null,
    val site: String? = null,
    val minPrice: Int? = null,
    val maxPrice: Int? = null,
    val minDiscount: Int? = null,
    val sortBy: SortType = SortType.LATEST
)

enum class SortType {
    LATEST,     // 최신순
    POPULAR,    // 인기순
    DISCOUNT,   // 할인율순
    PRICE_LOW,  // 가격 낮은순
    PRICE_HIGH  // 가격 높은순
}

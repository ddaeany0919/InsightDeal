package com.ddaeany0919.insightdeal.local.db

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.ddaeany0919.insightdeal.models.DealItem
import com.ddaeany0919.insightdeal.models.DealOptionItem
import com.ddaeany0919.insightdeal.models.DealSource

/**
 * 핫딜 로컬 캐싱을 위한 Room Entity
 */
@Entity(tableName = "deals")
data class DealEntity(
    @PrimaryKey val id: Int,
    val title: String,
    val price: Long,
    val currency: String,
    val originalPrice: Long?,
    val discountRate: Int?,
    val imageUrl: String?,
    val ecommerceUrl: String?,
    val postUrl: String?,
    val shopName: String? = null,
    val siteName: String,
    val siteNames: List<String>,
    val sources: List<DealSource>,
    val shippingFee: String?,
    val category: String?,
    val createdAt: String?,
    val viewCount: Int,
    val commentCount: Int,
    val likeCount: Int,
    val dislikeCount: Int,
    val tags: List<String>,
    val honeyScore: Int,
    val aiSummary: String?,
    val contentHtml: String?,
    val isClosed: Boolean,
    val hasOptions: Boolean = false,
    val optionsCount: Int = 0,
    val optionsData: List<DealOptionItem> = emptyList(),
    val subUrls: List<String> = emptyList()
) {
    /**
     * Entity -> Domain Model 변환
     */
    fun toDealItem(): DealItem {
        return DealItem(
            id = id,
            title = title,
            price = price,
            currency = currency,
            originalPrice = originalPrice,
            discountRate = discountRate,
            imageUrl = imageUrl,
            ecommerceUrl = ecommerceUrl,
            postUrl = postUrl,
            shopName = shopName,
            siteName = siteName,
            siteNames = siteNames,
            sources = sources,
            shippingFee = shippingFee,
            category = category,
            createdAt = createdAt,
            viewCount = viewCount,
            commentCount = commentCount,
            likeCount = likeCount,
            dislikeCount = dislikeCount,
            tags = tags,
            honeyScore = honeyScore,
            aiSummary = aiSummary,
            contentHtml = contentHtml,
            isClosed = isClosed,
            hasOptions = hasOptions,
            optionsCount = optionsCount,
            optionsData = optionsData,
            subUrls = subUrls
        )
    }

    companion object {
        /**
         * Domain Model -> Entity 변환
         */
        fun fromDealItem(deal: DealItem): DealEntity {
            return DealEntity(
                id = deal.id,
                title = deal.title,
                price = deal.price,
                currency = deal.currency,
                originalPrice = deal.originalPrice,
                discountRate = deal.discountRate,
                imageUrl = deal.imageUrl,
                ecommerceUrl = deal.ecommerceUrl,
                postUrl = deal.postUrl,
                shopName = deal.shopName,
                siteName = deal.siteName,
                siteNames = deal.siteNames,
                sources = deal.sources ?: emptyList(),
                shippingFee = deal.shippingFee,
                category = deal.category,
                createdAt = deal.createdAt,
                viewCount = deal.viewCount,
                commentCount = deal.commentCount,
                likeCount = deal.likeCount,
                dislikeCount = deal.dislikeCount,
                tags = deal.tags,
                honeyScore = deal.honeyScore,
                aiSummary = deal.aiSummary,
                contentHtml = deal.contentHtml,
                isClosed = deal.isClosed,
                hasOptions = deal.hasMultipleOptions,
                optionsCount = deal.resolvedOptionCount,
                optionsData = deal.optionsData,
                subUrls = deal.resolvedSubUrls
            )
        }
    }
}

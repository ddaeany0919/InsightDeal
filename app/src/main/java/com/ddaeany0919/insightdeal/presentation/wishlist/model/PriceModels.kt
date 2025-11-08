package com.ddaeany0919.insightdeal.presentation.wishlist.model

import kotlinx.datetime.LocalDate

// 가격 변동/최저가 쇼핑몰 정보

data class PriceHistoryItem(
    val date: LocalDate,
    val lowestPrice: Int,
    val lowestPriceShopName: String
)

enum class Period(val label: String) {
    THREE_MONTHS("3M"),
    ONE_MONTH("1M"),
    ONE_WEEK("1W"),
    ONE_DAY("1D")
}
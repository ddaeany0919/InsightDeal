package com.ddaeany0919.insightdeal.presentation.wishlist

// 숫자에 콤마 붙여 1,000,000원 형태로 표시
fun formatCurrency(price: Int?): String =
    price?.let { String.format("%,d원", it) } ?: "정보 없음"

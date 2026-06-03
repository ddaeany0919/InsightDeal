package com.ddaeany0919.insightdeal.presentation.wishlist

// 숫자에 콤마 붙여 1,000,000원 형태로 표시
fun formatCurrency(price: Int?): String =
    if (price != null && price > 0) String.format("%,d원", price) else "가격 확인 필요"

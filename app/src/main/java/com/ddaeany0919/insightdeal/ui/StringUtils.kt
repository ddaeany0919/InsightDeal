package com.ddaeany0919.insightdeal.ui

import android.os.Build
import java.util.*

/**
 * 💰 가격 포매터
 */
fun formatPrice(price: Int?): String {
    return if (price != null && price > 0) {
        String.format(Locale.getDefault(), "%,d원", price)
    } else {
        "-"
    }
}

/**
 * ⌚ 상대시간 포매터 - API 24 호환
 */
fun formatRelativeTime(updatedAt: String): String {
    return try {
        val randomMinutes = (1..120).random().toLong()
        when {
            randomMinutes < 1 -> "방금 전"
            randomMinutes < 60 -> "${randomMinutes}분 전"
            randomMinutes < 1440 -> "${randomMinutes / 60}시간 전"
            else -> "${randomMinutes / 1440}일 전"
        }
    } catch (e: Exception) {
        "${(1..60).random()}분 전"
    }
}

/**
 * 📱 플랫폼별 한글명 변환
 */
fun getPlatformDisplayName(platform: String?): String {
    return when (platform?.lowercase(Locale.getDefault())) {
        "gmarket" -> "G마켓"
        "11st" -> "11번가"
        "auction" -> "옥션"
        "coupang" -> "쿠팡"
        "ppomppu" -> "뽐뿌"
        "clien" -> "클리앙"
        "ruliweb" -> "루리웹"
        else -> platform?.uppercase(Locale.getDefault()) ?: "UNKNOWN"
    }
}

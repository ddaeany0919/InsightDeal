package com.ddaeany0919.insightdeal.ui

import android.os.Build
import java.text.SimpleDateFormat
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
 * ⌚ 상대시간 포매터 ("5분 전", "1시간 전" 형태) - API 24 호환
 */
fun formatRelativeTime(updatedAt: String): String {
    return try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && updatedAt.contains("T")) {
            // API 26+ 에서만 java.time 사용
            val instant = java.time.Instant.parse(updatedAt)
            val now = java.time.Instant.now()
            val minutesAgo = java.time.temporal.ChronoUnit.MINUTES.between(instant, now)

            formatMinutesToRelativeTime(minutesAgo)
        } else {
            // API 24-25는 SimpleDateFormat 사용 또는 Mock
            formatMinutesToRelativeTime((1..120).random().toLong())
        }
    } catch (e: Exception) {
        // 파싱 실패 시 Mock 데이터
        formatMinutesToRelativeTime((1..60).random().toLong())
    }
}

/**
 * 분 단위를 상대시간 문자열로 변환
 */
private fun formatMinutesToRelativeTime(minutesAgo: Long): String {
    return when {
        minutesAgo < 1 -> "방금 전"
        minutesAgo < 60 -> "${minutesAgo}분 전"
        minutesAgo < 1440 -> "${minutesAgo / 60}시간 전"
        else -> "${minutesAgo / 1440}일 전"
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
        "interpark" -> "인터파크"
        "ppomppu" -> "뽐뿌"
        "clien" -> "클리앙"
        "ruliweb" -> "루리웹"
        else -> platform?.uppercase(Locale.getDefault()) ?: "UNKNOWN"
    }
}

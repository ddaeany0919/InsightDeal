package com.ddaeany0919.insightdeal.presentation

import android.os.Build
import java.util.*
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.delay
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * 💰 가격 포매터
 */
fun formatPrice(price: Long?, currency: String? = "KRW"): String {
    return if (price != null && price > 0) {
        val safeCurrency = currency ?: "KRW"
        when (safeCurrency.uppercase()) {
            "USD" -> String.format(Locale.getDefault(), "$%.2f", price / 100.0)
            "EUR" -> String.format(Locale.getDefault(), "€%.2f", price / 100.0)
            else -> String.format(Locale.getDefault(), "%,d원", price)
        }
    } else {
        "가격 확인 필요"
    }
}

/**
 * ⌚ 상대시간 포매터 - API 24 호환 및 java.time 리팩토링 버전
 * 백엔드 ISO 8601 타임존 오프셋(+09:00, Z 등) 및 소수점 초를 완벽하게 정합적으로 파싱하여 시간 왜곡 문제를 원천 차단합니다.
 */
fun formatRelativeTime(updatedAt: String): String {
    return try {
        // 백엔드 날짜 형식(예: 2026-05-27T23:45:00+09:00)을 타임존 오프셋 보존하며 파싱합니다.
        val parsedTime = try {
            java.time.OffsetDateTime.parse(updatedAt)
        } catch (e: Exception) {
            // 오프셋이 없는 naive datetime(예: 2026-05-27T15:10:00) 형식에 대응하는 2단계 예외 안전망
            val local = java.time.LocalDateTime.parse(updatedAt)
            local.atZone(java.time.ZoneId.systemDefault()).toOffsetDateTime()
        }
        
        // 동일한 시간대 기준으로 현재 시각을 구합니다.
        val now = java.time.OffsetDateTime.now(parsedTime.offset)
        val duration = java.time.Duration.between(parsedTime, now)
        
        val diffInMinutes = duration.toMinutes()
        val diffInHours = duration.toHours()
        val diffInDays = duration.toDays()
        
        when {
            diffInMinutes < 1 -> "방금 전"
            diffInMinutes < 60 -> "${diffInMinutes}분 전"
            diffInHours < 24 -> "${diffInHours}시간 전"
            diffInDays < 7 -> "${diffInDays}일 전"
            else -> {
                val formatter = java.time.format.DateTimeFormatter.ofPattern("MM.dd", Locale.getDefault())
                parsedTime.format(formatter)
            }
        }
    } catch (e: Exception) {
        "알 수 없음"
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

/**
 * ⏱️ 전역 시간 틱커 (Global Time Ticker)
 * 리스트 아이템마다 타이머를 생성하면 메모리를 낭비하므로, 
 * 앱 전체에서 딱 1개의 코루틴만 돌아서 1분마다 신호를 보내도록 최적화합니다.
 */
object GlobalTimeTicker {
    private val _tick = MutableStateFlow(System.currentTimeMillis())
    val tick = _tick.asStateFlow()

    init {
        CoroutineScope(Dispatchers.Default).launch {
            while (true) {
                // 현재 시간 기준 다음 분의 0초까지 대기하여 정확히 분 단위로 갱신
                val now = Calendar.getInstance()
                val millisUntilNextMinute = 60_000L - (now.get(Calendar.SECOND) * 1000 + now.get(Calendar.MILLISECOND))
                delay(millisUntilNextMinute)
                _tick.value = System.currentTimeMillis()
            }
        }
    }
}

/**
 * ⌚ 상태 기반 상대시간 포매터 (메모리 최적화 버전)
 */
@androidx.compose.runtime.Composable
fun rememberRelativeTime(updatedAt: String?): String {
    if (updatedAt.isNullOrEmpty()) return ""
    
    // 전역 타이머(tick)를 구독하여 1분이 지날 때마다 리컴포지션 발생
    val tick by GlobalTimeTicker.tick.collectAsState()
    
    // tick 값이 바뀔 때마다 formatRelativeTime 재호출
    return androidx.compose.runtime.remember(updatedAt, tick) { 
        formatRelativeTime(updatedAt) 
    }
}

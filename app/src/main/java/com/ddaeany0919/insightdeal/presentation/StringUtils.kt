package com.ddaeany0919.insightdeal.presentation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.net.URI
import java.time.Duration
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.Calendar
import java.util.Locale

private val EXPLICIT_CURRENCIES = setOf("KRW", "USD", "EUR", "CNY", "JPY")
private val CENT_SCALED_CURRENCIES = setOf("USD", "EUR", "CNY")

private val SHIPPING_FREE_NOISE_REGEX = Regex(
    "(무료\\s*배송|배송비\\s*무료|배송\\s*무료|무배|와우\\s*무료|스클\\s*무료)"
)
private val FREE_DEAL_KEYWORD_REGEX = Regex(
    "(무료\\s*배포|일시\\s*무료|\\(\\s*무료\\s*\\)|\\[\\s*무료\\s*\\]|(?<![가-힣A-Za-z0-9])무료(?![가-힣A-Za-z0-9])|(?<![0-9,])0원)"
)

private val NUMERIC_DEC_ENTITY_REGEX = Regex("&#(\\d{1,7});")
private val NUMERIC_HEX_ENTITY_REGEX = Regex("&#[xX]([0-9a-fA-F]{1,6});")
private val LEADING_BRACKET_REGEX = Regex("^\\s*\\[([^\\[\\]]{1,20})]\\s*")

private val NON_MALL_BRACKET_WORDS = setOf(
    "특가", "초특가", "타임특가", "재입고", "기타", "역대가", "브랜드데이",
    "핫딜", "공구", "광군제", "할인", "이벤트", "품절", "종료",
    "끌올", "정보", "무료", "무배", "무료배송", "직구", "해외", "국내", "적립"
)

private val COMMUNITY_SITE_WORDS = setOf(
    "뽐뿌", "뽐뿌해외", "알리뽐뿌", "펨코", "에펨코리아", "퀘이사존",
    "루리웹", "클리앙", "빠삭", "빠삭국내", "빠삭해외", "빠삭육아"
)

/**
 * 제목/배송비 등 텍스트 내 HTML 엔티티(&amp;, &#039;, &quot; 등)를 안전하게 디코딩합니다.
 */
fun decodeHtmlEntities(raw: String?): String {
    if (raw.isNullOrEmpty()) return ""
    if (!raw.contains('&')) return raw

    var decoded = raw
        .replace("&amp;", "&")
        .replace("&#039;", "'")
        .replace("&#39;", "'")
        .replace("&apos;", "'")
        .replace("&quot;", "\"")
        .replace("&#34;", "\"")
        .replace("&lt;", "<")
        .replace("&gt;", ">")
        .replace("&nbsp;", " ")

    if (decoded.contains("&#")) {
        decoded = NUMERIC_DEC_ENTITY_REGEX.replace(decoded) { match ->
            val code = match.groupValues[1].toIntOrNull()
            if (code != null && code in 1..0x10FFFF) String(Character.toChars(code)) else match.value
        }
        decoded = NUMERIC_HEX_ENTITY_REGEX.replace(decoded) { match ->
            val code = match.groupValues[1].toIntOrNull(16)
            if (code != null && code in 1..0x10FFFF) String(Character.toChars(code)) else match.value
        }
    }
    return decoded
}

/**
 * 통화가 센트/전(*100) 스케일(USD, EUR, CNY)인지 여부
 */
fun isCentScaledCurrency(currency: String?): Boolean {
    val norm = currency?.trim()?.uppercase(Locale.US).orEmpty()
    return norm in CENT_SCALED_CURRENCIES
}

/**
 * 가격 포매터
 * - 서버 통화(KRW, USD, EUR, CNY, JPY)가 명시된 경우 절대 titleHint로 통화를 덮어쓰지 않습니다.
 * - USD/EUR/CNY는 모든 양수 값을 /100.0 단위로 포맷합니다 (99 USD -> "$0.99", 2100 USD -> "$21").
 * - JPY는 나누지 않고 "¥%,d"로 포맷합니다.
 * - price <= 0 또는 null일 때 무료 배포 키워드는 "무료", 그 외에는 "가격 확인"을 반환합니다.
 */
fun formatPrice(price: Long?, currency: String? = "KRW", titleHint: String? = null): String {
    val decodedHint = titleHint?.let { decodeHtmlEntities(it) }
    if (price != null && price > 0L) {
        val rawCurr = (currency ?: "").trim().uppercase(Locale.US)
        val effectiveCurrency = when {
            rawCurr in EXPLICIT_CURRENCIES -> rawCurr
            rawCurr.isNotEmpty() -> rawCurr
            decodedHint != null && (decodedHint.contains("$") || decodedHint.contains("달러")) && !decodedHint.contains("원") -> "USD"
            decodedHint != null && (decodedHint.contains("€") || decodedHint.contains("유로")) && !decodedHint.contains("원") -> "EUR"
            decodedHint != null && (decodedHint.contains("¥") || decodedHint.contains("엔")) && !decodedHint.contains("원") -> "JPY"
            else -> "KRW"
        }

        return when (effectiveCurrency) {
            "USD" -> formatSubunitPrice("$", price)
            "EUR" -> formatSubunitPrice("€", price)
            "CNY" -> formatSubunitPrice("¥", price)
            "JPY" -> String.format(Locale.US, "¥%,d", price)
            else -> String.format(Locale.US, "%,d원", price)
        }
    }

    if (!decodedHint.isNullOrBlank()) {
        val withoutShippingFree = SHIPPING_FREE_NOISE_REGEX.replace(decodedHint, " ")
        if (FREE_DEAL_KEYWORD_REGEX.containsMatchIn(withoutShippingFree)) {
            return "무료"
        }
    }
    return "가격 확인"
}

private fun formatSubunitPrice(symbol: String, minorUnits: Long): String {
    val major = minorUnits / 100.0
    return if (minorUnits % 100L == 0L) {
        "$symbol${String.format(Locale.US, "%,d", minorUnits / 100L)}"
    } else {
        "$symbol${String.format(Locale.US, "%.2f", major)}"
    }
}

/**
 * 쇼핑몰 배지명 정규화 및 비쇼핑몰 말머리 필터링
 */
fun normalizeMallBadgeName(raw: String?): String? {
    if (raw.isNullOrBlank()) return null
    val cleaned = decodeHtmlEntities(raw)
        .trim()
        .removePrefix("[")
        .removeSuffix("]")
        .trim()
    if (cleaned.isEmpty() || cleaned.length > 16) return null

    val compactLower = cleaned.lowercase(Locale.US).replace(" ", "")
    if (cleaned in NON_MALL_BRACKET_WORDS || compactLower in NON_MALL_BRACKET_WORDS) return null
    if (NON_MALL_BRACKET_WORDS.any { word -> word.length >= 2 && compactLower == word }) return null
    if (compactLower.endsWith("특가") || compactLower.endsWith("할인") || compactLower.endsWith("이벤트") || compactLower == "재입고" || compactLower == "기타") {
        return null
    }
    if (cleaned in COMMUNITY_SITE_WORDS || COMMUNITY_SITE_WORDS.any { compactLower == it }) {
        return null
    }

    return when {
        compactLower in setOf("g마켓", "gmarket", "지마켓") -> "지마켓"
        compactLower in setOf("알리", "알리익스프레스", "aliexpress", "ali") -> "알리익스프레스"
        compactLower in setOf("쿠팡", "coupang") -> "쿠팡"
        compactLower in setOf("11번가", "11st", "십일번가", "11번가쇼킹딜", "아마존11번가") -> "11번가"
        compactLower in setOf("옥션", "auction") -> "옥션"
        compactLower in setOf("네이버", "네이버쇼핑", "스마트스토어", "네이버스마트스토어", "브랜드스토어", "네이버브랜드스토어") -> "네이버"
        compactLower in setOf("롯데온", "lotteon") -> "롯데온"
        compactLower in setOf("ssg", "쓱", "쓱닷컴", "ssg닷컴", "신세계몰", "이마트몰") -> "SSG"
        compactLower in setOf("무신사", "musinsa") -> "무신사"
        compactLower in setOf("아마존", "amazon", "아마존직구") -> "아마존"
        compactLower in setOf("스팀", "steam") -> "스팀"
        compactLower in setOf("에픽", "에픽게임즈", "epic", "epicgames") -> "에픽게임즈"
        compactLower in setOf("토스", "토스쇼핑") -> "토스쇼핑"
        compactLower in setOf("카카오", "카카오톡딜", "톡딜", "카카오쇼핑", "카카오선물하기") -> "카카오쇼핑"
        compactLower in setOf("티몬", "위메프") -> null
        else -> cleaned
    }
}

/**
 * 구매 링크 도메인으로부터 쇼핑몰 배지명을 역추론합니다.
 */
fun inferMallFromUrl(url: String?): String? {
    if (url.isNullOrBlank()) return null
    val host = try {
        val normalized = if (url.startsWith("http", ignoreCase = true)) url else "https://$url"
        URI(normalized).host?.lowercase(Locale.US).orEmpty()
    } catch (_: Exception) {
        url.lowercase(Locale.US)
    }
    if (host.isEmpty()) return null

    return when {
        host.contains("coupang.com") || host.contains("coupa.ng") -> "쿠팡"
        host.contains("gmarket.co.kr") -> "지마켓"
        host.contains("11st.co.kr") -> "11번가"
        host.contains("auction.co.kr") -> "옥션"
        host.contains("aliexpress.com") -> "알리익스프레스"
        host.contains("smartstore.naver.com") || host.contains("brand.naver.com") || host.contains("shopping.naver.com") -> "네이버"
        host.contains("lotteon.com") -> "롯데온"
        host.contains("ssg.com") || host.contains("emart.com") || host.contains("shinsegaemall.com") -> "SSG"
        host.contains("musinsa.com") -> "무신사"
        host.contains("29cm.co.kr") -> "29CM"
        host.contains("ohou.se") -> "오늘의집"
        host.contains("kurly.com") -> "컬리"
        host.contains("oliveyoung.co.kr") -> "올리브영"
        host.contains("hmall.com") -> "현대Hmall"
        host.contains("gsshop.com") -> "GS샵"
        host.contains("cjonstyle.com") || host.contains("cjthemarket.com") -> "CJ온스타일"
        host.contains("amazon.") || host.contains("amzn.to") -> "아마존"
        host.contains("steampowered.com") -> "스팀"
        host.contains("epicgames.com") -> "에픽게임즈"
        host.contains("temu.com") -> "테무"
        host.contains("qoo10.com") -> "큐텐"
        else -> null
    }
}

data class ResolvedShopTitle(
    val shopBadge: String?,
    val cleanTitle: String
)

/**
 * 1순위: shopName -> 2순위: 제목 앞 [말머리] -> 3순위: ecommerceUrl 도메인 역추론으로 쇼핑몰 배지를 결정하고,
 * 배지와 중복되는 제목 선두 [쇼핑몰명] 말머리를 제거합니다.
 */
fun resolveShopBadgeAndCleanTitle(
    rawTitle: String,
    shopName: String? = null,
    ecommerceUrl: String? = null
): ResolvedShopTitle {
    val decodedTitle = decodeHtmlEntities(rawTitle).trim()
    val bracketMatch = LEADING_BRACKET_REGEX.find(decodedTitle)
    val bracketContent = bracketMatch?.groupValues?.getOrNull(1)?.trim()
    val normalizedFromBracket = normalizeMallBadgeName(bracketContent)

    val resolvedBadge = normalizeMallBadgeName(shopName)
        ?: normalizedFromBracket
        ?: inferMallFromUrl(ecommerceUrl)

    val finalTitle = if (bracketMatch != null && resolvedBadge != null) {
        val bracketMatchesBadge = normalizedFromBracket != null &&
            normalizedFromBracket.equals(resolvedBadge, ignoreCase = true)
        val rawMatchesBadge = bracketContent != null &&
            bracketContent.replace(" ", "").equals(resolvedBadge.replace(" ", ""), ignoreCase = true)

        if (bracketMatchesBadge || rawMatchesBadge) {
            decodedTitle.substring(bracketMatch.range.last + 1).trimStart()
                .ifBlank { decodedTitle }
        } else {
            decodedTitle
        }
    } else {
        decodedTitle
    }

    return ResolvedShopTitle(
        shopBadge = resolvedBadge,
        cleanTitle = finalTitle
    )
}

/**
 * 백엔드 UTC/오프셋 ISO 시각 문자열을 Asia/Seoul 기준 OffsetDateTime으로 정합 파싱합니다.
 */
fun parseBackendTimestampToKst(rawTimestamp: String?): OffsetDateTime? {
    val trimmed = rawTimestamp?.trim().orEmpty()
    if (trimmed.isEmpty()) return null
    val seoulZone = ZoneId.of("Asia/Seoul")
    return try {
        val instant = try {
            OffsetDateTime.parse(trimmed).toInstant()
        } catch (_: Exception) {
            val cleanLocal = trimmed.replace(" ", "T")
            LocalDateTime.parse(cleanLocal).toInstant(ZoneOffset.UTC)
        }
        instant.atZone(seoulZone).toOffsetDateTime()
    } catch (_: Exception) {
        null
    }
}

/**
 * 상대시간 포매터 (백엔드 naive ISO 문자열을 UTC로 해석 후 현재 시각과 비교)
 */
fun formatRelativeTime(updatedAt: String): String {
    val trimmed = updatedAt.trim()
    if (trimmed.isEmpty()) return ""
    if (!trimmed.contains("T") && (trimmed.endsWith("전") || trimmed.contains("방금") || trimmed.contains("어제"))) {
        return trimmed
    }
    return try {
        val parsedKst = parseBackendTimestampToKst(trimmed)
            ?: return if (!trimmed.contains("T")) trimmed else trimmed.substringBefore("T")

        val nowKst = OffsetDateTime.now(ZoneId.of("Asia/Seoul"))
        val duration = Duration.between(parsedKst, nowKst)

        val diffInMinutes = duration.toMinutes().coerceAtLeast(0L)
        val diffInHours = duration.toHours().coerceAtLeast(0L)
        val diffInDays = duration.toDays().coerceAtLeast(0L)

        when {
            diffInMinutes < 1 -> "방금 전"
            diffInMinutes < 60 -> "${diffInMinutes}분 전"
            diffInHours < 24 -> "${diffInHours}시간 전"
            diffInDays < 30 -> "${diffInDays}일 전"
            diffInDays < 365 -> "${diffInDays / 30}달 전"
            else -> "${diffInDays / 365}년 전"
        }
    } catch (_: Exception) {
        if (!trimmed.contains("T")) trimmed else trimmed.substringBefore("T")
    }
}

fun getPlatformDisplayName(platform: String?): String {
    return when (platform?.lowercase(Locale.getDefault())) {
        "gmarket" -> "지마켓"
        "11st" -> "11번가"
        "auction" -> "옥션"
        "coupang" -> "쿠팡"
        "ppomppu" -> "뽐뿌"
        "clien" -> "클리앙"
        "ruliweb" -> "루리웹"
        else -> platform?.uppercase(Locale.getDefault()) ?: "UNKNOWN"
    }
}

object GlobalTimeTicker {
    private val _tick = MutableStateFlow(System.currentTimeMillis())
    val tick = _tick.asStateFlow()

    init {
        CoroutineScope(Dispatchers.Default).launch {
            while (true) {
                val now = Calendar.getInstance()
                val millisUntilNextMinute = 60_000L - (now.get(Calendar.SECOND) * 1000 + now.get(Calendar.MILLISECOND))
                delay(millisUntilNextMinute)
                _tick.value = System.currentTimeMillis()
            }
        }
    }
}

@Composable
fun rememberRelativeTime(updatedAt: String?): String {
    if (updatedAt.isNullOrEmpty()) return ""
    val initialFormatted = remember(updatedAt) { formatRelativeTime(updatedAt) }
    if (!updatedAt.contains("T") || initialFormatted.endsWith("일 전") || initialFormatted.endsWith("달 전") || initialFormatted.endsWith("년 전")) {
        return initialFormatted
    }

    val tick by GlobalTimeTicker.tick.collectAsState()
    return remember(updatedAt, tick) {
        formatRelativeTime(updatedAt)
    }
}

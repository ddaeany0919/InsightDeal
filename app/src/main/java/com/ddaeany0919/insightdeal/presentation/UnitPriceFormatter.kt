package com.ddaeany0919.insightdeal.presentation

import androidx.compose.runtime.Immutable
import java.util.Locale
import kotlin.math.roundToLong

/**
 * 피드 카드/상세 UI에 표시할 체감 단위당 가격 배지 정보 (웹 computeUnitPriceBadge 1:1 패리티)
 */
@Immutable
data class UnitPriceBadgeInfo(
    val label: String,
    val detail: String
)

private val UNIT_PRICE_EXCLUDED_CATEGORIES = setOf(
    "PC/하드웨어",
    "PC용품",
    "SW/게임",
    "가전/TV",
    "가전제품",
    "상품권",
    "적립/포인트",
    "적립",
    "이벤트",
    "모바일/기프티콘"
)

private val UNIT_PRICE_TECH_SPEC_REGEX = Regex(
    "(?:^|[\\s(/\\[,])(?:\\d+\\s*(?:gb|tb|mb|ghz|mhz|hz|mah|wh|w|인치|inch)|rtx\\s*\\d+|gtx\\s*\\d+|ddr[45]|nvme|ssd|hdd|모니터|노트북|그래픽카드|메인보드|키보드|마우스|이어폰|헤드셋|충전기|케이블|보조배터리|스마트워치|스마트글래스|스마트\\s*글래스|오디오\\s*안경|블루투스|스피커|태블릿|아이패드|갤럭시탭|맥북)(?=$|[\\s)/\\],])",
    RegexOption.IGNORE_CASE
)

private val PAREN_WON_PRICE_REGEX = Regex("\\([^)]*원[^)]*\\)")
private val LEADING_BRACKET_TITLE_REGEX = Regex("^\\[[^\\]]+\\]\\s*")
private val MULTI_WHITESPACE_REGEX = Regex("\\s+")

private val SHEET_PACK_REGEX = Regex(
    "(\\d{2,4})\\s*매(?:입)?\\s*(?:[xX×*]|,|\\s)\\s*(\\d{1,3})\\s*(팩|봉|개|통|박스)(?![가-힣])"
)
private val WEIGHT_REGEX = Regex(
    "(?<![a-zA-Z0-9.])(\\d+(?:\\.\\d+)?)\\s*(kg|g)(?![a-zA-Z])",
    RegexOption.IGNORE_CASE
)
private val VOLUME_REGEX = Regex(
    "(?<![a-zA-Z0-9.])(\\d+(?:\\.\\d+)?)\\s*(ml|l|리터)(?![a-zA-Z])",
    RegexOption.IGNORE_CASE
)
private val TRAILING_WEIGHT_COUNT_REGEX = Regex(
    "^\\s*(?:[xX×*,]|\\s)+\\s*(\\d{1,3})\\s*(?:개입|개|팩|봉지|봉|캔|병|통|포|곽|입|박스|세트)(?![가-힣])"
)
private val TRAILING_VOLUME_COUNT_REGEX = Regex(
    "^\\s*(?:[xX×*,]|\\s)+\\s*(\\d{1,3})\\s*(?:개입|개|캔|병|팩|페트|pet|포|통|입|박스|세트)(?![a-zA-Z가-힣])",
    RegexOption.IGNORE_CASE
)
private val TRAILING_BONUS_COUNT_REGEX = Regex(
    "^\\s*\\+\\s*(\\d{1,2})\\s*(?:개|팩|봉|캔|병|포|통)"
)
private val BUNDLE_COUNT_REGEX = Regex(
    "(?:^|[\\s,xX×*+(/\\[])(\\d{2,3}|[2-9])\\s*(개입|팩|봉지|봉|캔|병|포|롤|캡슐|정|켤레|박스)(?![가-힣])"
)

private fun formatKorComma(value: Long): String =
    String.format(Locale.KOREA, "%,d", value)

private fun extractTrailingCount(afterStr: String, pattern: Regex): Long {
    val windowStr = afterStr.take(14)
    val match = pattern.find(windowStr) ?: return 1L
    val baseCount = match.groupValues[1].toLongOrNull() ?: return 1L
    if (baseCount !in 1L..500L) return 1L
    val bonusWindow = windowStr.substring(match.range.last + 1)
    val bonusMatch = TRAILING_BONUS_COUNT_REGEX.find(bonusWindow)
    val bonusCount = bonusMatch?.groupValues?.get(1)?.toLongOrNull() ?: 0L
    return baseCount + bonusCount
}

/**
 * 식품·생활용품·소모품 제목에서 중량/용량/매x팩/수량을 추출해 100g당·100ml당·1팩당·개당 체감 단가를 계산한다.
 * IT/가전 스펙이나 비원화 딜은 오탐 방지를 위해 null을 반환한다.
 */
fun computeUnitPriceBadge(
    title: String?,
    price: Long?,
    currency: String? = "KRW",
    category: String? = null
): UnitPriceBadgeInfo? {
    if (title.isNullOrBlank() || price == null || price < 1000L) return null
    if (!currency.isNullOrBlank() && !currency.trim().equals("KRW", ignoreCase = true)) return null
    if (!category.isNullOrBlank() && category.trim() in UNIT_PRICE_EXCLUDED_CATEGORIES) return null

    val cleanedTitle = MULTI_WHITESPACE_REGEX.replace(
        LEADING_BRACKET_TITLE_REGEX.replace(
            PAREN_WON_PRICE_REGEX.replace(decodeHtmlEntities(title), " "),
            ""
        ),
        " "
    ).trim()

    if (cleanedTitle.isEmpty() || UNIT_PRICE_TECH_SPEC_REGEX.containsMatchIn(cleanedTitle)) {
        return null
    }

    // 1. 물티슈/시트류 (N매 x M팩)
    SHEET_PACK_REGEX.find(cleanedTitle)?.let { sm ->
        val sheets = sm.groupValues[1].toLongOrNull() ?: 0L
        val packCount = sm.groupValues[2].toLongOrNull() ?: 0L
        if (packCount in 2L..200L) {
            val perPack = (price.toDouble() / packCount).roundToLong()
            if (perPack in 10L until price) {
                return UnitPriceBadgeInfo(
                    label = "1팩당 ${formatKorComma(perPack)}원",
                    detail = "${formatKorComma(sheets)}매 × ${packCount}팩 기준"
                )
            }
        }
    }

    // 2. 중량 (kg / g) — 0g 영양표기·소량 제외
    for (wm in WEIGHT_REGEX.findAll(cleanedTitle)) {
        val rawVal = wm.groupValues[1].toDoubleOrNull() ?: continue
        if (rawVal <= 0.0) continue
        val unit = wm.groupValues[2].lowercase(Locale.US)
        val unitGrams = if (unit == "kg") rawVal * 1000.0 else rawVal
        if (unitGrams < 10.0) continue
        val afterStr = cleanedTitle.substring(wm.range.last + 1)
        val count = extractTrailingCount(afterStr, TRAILING_WEIGHT_COUNT_REGEX)
        val totalGrams = unitGrams * count
        if (totalGrams in 50.0..100_000.0) {
            val per100g = ((price.toDouble() / totalGrams) * 100.0).roundToLong()
            if (per100g in 10L until price) {
                val totalRounded = totalGrams.roundToLong()
                val detail = if (count >= 2L) {
                    val perCount = (price.toDouble() / count).roundToLong()
                    "총 ${formatKorComma(totalRounded)}g · 개당 ${formatKorComma(perCount)}원"
                } else {
                    "총 ${formatKorComma(totalRounded)}g 기준"
                }
                return UnitPriceBadgeInfo(
                    label = "100g당 ${formatKorComma(per100g)}원",
                    detail = detail
                )
            }
        }
    }

    // 3. 용량 (ml / L / 리터)
    for (vm in VOLUME_REGEX.findAll(cleanedTitle)) {
        val rawVal = vm.groupValues[1].toDoubleOrNull() ?: continue
        if (rawVal <= 0.0) continue
        val unit = vm.groupValues[2].lowercase(Locale.US)
        val unitMl = if (unit == "ml") rawVal else rawVal * 1000.0
        if (unitMl < 10.0) continue
        val afterStr = cleanedTitle.substring(vm.range.last + 1)
        val count = extractTrailingCount(afterStr, TRAILING_VOLUME_COUNT_REGEX)
        val totalMl = unitMl * count
        if (totalMl in 50.0..100_000.0) {
            val per100ml = ((price.toDouble() / totalMl) * 100.0).roundToLong()
            if (per100ml in 10L until price) {
                val totalRounded = totalMl.roundToLong()
                val detail = if (count >= 2L) {
                    val perCount = (price.toDouble() / count).roundToLong()
                    "총 ${formatKorComma(totalRounded)}ml · 개당 ${formatKorComma(perCount)}원"
                } else {
                    "총 ${formatKorComma(totalRounded)}ml 기준"
                }
                return UnitPriceBadgeInfo(
                    label = "100ml당 ${formatKorComma(per100ml)}원",
                    detail = detail
                )
            }
        }
    }

    // 4. 순수 묶음 수량 (롤/캡슐/정/켤레/팩/봉 등)
    BUNDLE_COUNT_REGEX.find(cleanedTitle)?.let { bm ->
        val count = bm.groupValues[1].toLongOrNull() ?: 0L
        val unitLabel = bm.groupValues[2]
        if (count in 2L..500L) {
            val perUnit = (price.toDouble() / count).roundToLong()
            if (perUnit in 10L until price) {
                val prefix = when (unitLabel) {
                    "롤", "캡슐", "정", "켤레" -> "1${unitLabel}당"
                    else -> "개당"
                }
                return UnitPriceBadgeInfo(
                    label = "$prefix ${formatKorComma(perUnit)}원",
                    detail = "총 ${count}${unitLabel} 기준"
                )
            }
        }
    }

    return null
}

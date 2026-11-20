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
    "(?:^|[\\s(/\\[,])(?:\\d+\\s*(?:gb|tb|mb|ghz|mhz|hz|mah|wh|w|인치|inch)|rtx\\s*\\d+|gtx\\s*\\d+|rx\\s*\\d+|ddr[45]|nvme|ssd|hdd|모니터|노트북|그래픽카드|메인보드|키보드|마우스|이어폰|헤드셋|충전기|케이블|보조배터리|스마트워치|스마트글래스|스마트\\s*글래스|오디오\\s*안경|블루투스|스피커|태블릿|아이패드|갤럭시탭|맥북|멀티탭|콘센트|완본체)(?=$|[\\s)/\\],])",
    RegexOption.IGNORE_CASE
)

private val EGG_ITEM_HINT_REGEX = Regex(
    "(?:계란|달걀|구운란|반숙|훈제란|메추리|초란|유정란|특란|대란|왕란|중란|동물복지란)"
)
private val APPLIANCE_OUTLET_REGEX = Regex(
    "(?:멀티탭|콘센트|어댑터|충전기|인덕션|하이라이트|가스레인지|포트|허브|스위치|전등|조명|소켓|usb|hdmi)",
    RegexOption.IGNORE_CASE
)
private val NUTRITION_WORD_REGEX = Regex(
    "(?:당류|지방|포화지방|나트륨|콜레스테롤|단백질|탄수화물|카페인|당)\\s*$"
)
private val SMALL_SACHET_UNIT_REGEX = Regex("^(?:포|스틱|봉|캡슐|정|개입|입)$")
private val SMALL_SACHET_LABEL_UNIT_REGEX = Regex("^(?:포|스틱|봉|캡슐|정)$")

private val PAREN_WON_PRICE_REGEX = Regex("\\([^)]*원[^)]*\\)")
private val LEADING_BRACKET_TITLE_REGEX = Regex("^\\[[^\\]]+\\]\\s*")
private val MULTI_WHITESPACE_REGEX = Regex("\\s+")

private val SHEET_PACK_REGEX = Regex(
    "(\\d{2,4})\\s*매(?:입)?\\s*(?:[xX×*]|,|\\s)\\s*(\\d{1,3})\\s*(팩|봉|개|통|박스)(?![가-힣])"
)
private val WEIGHT_REGEX = Regex(
    "(?<![a-zA-Z0-9.])(\\d+(?:\\.\\d+)?)\\s*(kg|g)(?![a-wyzA-WYZ]|x(?!\\d)|X(?!\\d))",
    RegexOption.IGNORE_CASE
)
private val VOLUME_REGEX = Regex(
    "(?<![a-zA-Z0-9.])(\\d+(?:\\.\\d+)?)\\s*(ml|l|리터)(?![a-wyzA-WYZ]|x(?!\\d)|X(?!\\d))",
    RegexOption.IGNORE_CASE
)
private val TRAILING_WEIGHT_COUNT_REGEX = Regex(
    "^(?:\\s*(?:본품|리필|용기|증정)\\s*(?:[xX×*,]|\\s)+|(?:[xX×*,]|\\s)+)(\\d{1,3})\\s*(개입|개|팩|봉지|봉|캔|병|통|포|스틱|곽|입|박스|세트|종(?:\\s*세트)?)(?![가-힣])"
)
private val TRAILING_VOLUME_COUNT_REGEX = Regex(
    "^(?:\\s*(?:본품|리필|용기|증정)\\s*(?:[xX×*,]|\\s)+|(?:[xX×*,]|\\s)+)(\\d{1,3})\\s*(개입|개|캔|병|팩|페트|pet|포|스틱|통|입|박스|세트|종(?:\\s*세트)?)(?![a-wyzA-WYZ가-힣])",
    RegexOption.IGNORE_CASE
)
private val TRAILING_TIERED_COUNT_REGEX = Regex(
    "^\\s*\\)?\\s*(?:[xX×*]|\\s)*(\\d{1,2})\\s*(?:개입|개|팩|봉지|봉|박스|세트|묶음|번들|통|곽|입|판|종(?:\\s*세트)?)(?![가-힣])"
)
private val TRAILING_BONUS_COUNT_REGEX = Regex(
    "^\\s*\\)?\\s*\\+\\s*(\\d{1,2})\\s*(?:개입|개|팩|봉|캔|병|포|통)(?!\\s*(?:g|kg|ml|l|리터))",
    RegexOption.IGNORE_CASE
)
private val TOTAL_BUNDLE_REGEX = Regex(
    "총\\s*(\\d{1,3})\\s*(개입|팩|봉지|봉|캔|병|포|스틱|롤|캡슐|정|켤레|박스|인분|환|(?<!매)입)(?![가-힣])"
)
private val BUNDLE_COUNT_REGEX = Regex(
    "(?:^|[\\s,xX×*+(/\\[])(\\d{2,3}|[2-9])\\s*(개입|팩|봉지|봉|캔|병|포|스틱|롤|캡슐|정|켤레|박스|인분|환|(?<!매)입)(?![가-힣])"
)
private val BUNDLE_TIER_MULTIPLIER_REGEX = Regex(
    "^\\s*(?:[xX×*]|\\s)\\s*(\\d{1,2})\\s*(?:박스|세트|묶음|번들|팩)(?![가-힣])"
)
private val EGG_COUNT_REGEX = Regex(
    "(?:^|[\\s,xX×*+(/\\[])(\\d{2,3}|[2-9])\\s*구(?![가-힣])"
)
private val EGG_TIER_MULTIPLIER_REGEX = Regex(
    "^\\s*(?:[xX×*]|\\s)\\s*(\\d{1,2})\\s*(?:판|박스|세트|팩)(?![가-힣])"
)
private val FOOD_COMBO_COUNT_REGEX = Regex(
    "([가-힣]{2,10})\\s*(\\d{1,2})\\s*\\+\\s*([가-힣]{2,10})\\s*(\\d{1,2})(?![가-힣0-9]|\\s*(?:만|천|원|%|개월|년|일|시|분))"
)

private data class TrailingCountMeta(
    val count: Long,
    val matched: Boolean,
    val unitLabel: String
)

private data class WeightEntry(
    val totalGrams: Double,
    val count: Long,
    val startIdx: Int,
    val endIdx: Int
)

private data class VolumeEntry(
    val totalMl: Double,
    val count: Long,
    val startIdx: Int,
    val endIdx: Int
)

private fun formatKorComma(value: Long): String =
    String.format(Locale.KOREA, "%,d", value)

private fun extractTrailingMeta(afterStr: String, pattern: Regex): TrailingCountMeta {
    val windowStr = afterStr.take(36)
    val match = pattern.find(windowStr) ?: return TrailingCountMeta(1L, false, "")
    val baseCount = match.groupValues[1].toLongOrNull() ?: return TrailingCountMeta(1L, false, "")
    if (baseCount !in 1L..500L) return TrailingCountMeta(1L, false, "")
    val unitLabel = match.groupValues.getOrElse(2) { "" }
    val tailWindow = windowStr.substring(match.range.last + 1)
    TRAILING_TIERED_COUNT_REGEX.find(tailWindow)?.let { secondMatch ->
        val secondCount = secondMatch.groupValues[1].toLongOrNull() ?: 0L
        val tiered = baseCount * secondCount
        if (tiered in 1L..500L) return TrailingCountMeta(tiered, true, unitLabel)
    }
    val bonusMatch = TRAILING_BONUS_COUNT_REGEX.find(tailWindow)
    val bonusCount = bonusMatch?.groupValues?.get(1)?.toLongOrNull() ?: 0L
    return TrailingCountMeta(baseCount + bonusCount, true, unitLabel)
}

private fun formatBundleBadge(price: Long, count: Long, unitLabel: String): UnitPriceBadgeInfo? {
    if (count !in 2L..500L) return null
    val perUnit = (price.toDouble() / count).roundToLong()
    if (perUnit !in 10L until price) return null
    val prefix = when (unitLabel) {
        "롤", "캡슐", "정", "켤레", "인분", "환", "구", "포", "스틱" -> "1${unitLabel}당"
        else -> "개당"
    }
    return UnitPriceBadgeInfo(
        label = "$prefix ${formatKorComma(perUnit)}원",
        detail = "총 ${count}${unitLabel} 기준"
    )
}

private val VOUCHER_TITLE_REGEX = Regex(
    "(?:상품권|컬쳐랜드|컬처랜드|해피머니|북앤라이프|기프트카드|금액권)"
)
private val VOUCHER_GIFT_ATTACHED_REGEX = Regex(
    "\\+\\s*[^+]{0,16}(?:상품권|컬쳐|컬처|해피머니|기프트|금액권)"
)
private val VOUCHER_MAN_FACE_REGEX = Regex(
    "(?:^|[\\s(\\[,·])(\\d{1,2})\\s*만\\s*(?:원\\s*권|원권|권|원)?(?=$|[\\s)\\],·]|(?![가-힣0-9]))(?!\\s*(?:이상|이하|할인|적립|페이백|증정|답례품))"
)
private val VOUCHER_THOUSAND_FACE_REGEX = Regex("([1-9]\\d?,000)\\s*원?\\s*권")

private val PAYMENT_CARD_REGEX = Regex(
    "(우리카드|삼성카드|신한카드|국민카드|KB\\s*국민카드|KB\\s*카드|현대카드|롯데카드|하나카드|농협카드|NH\\s*농협카드|NH\\s*카드|비씨카드|BC\\s*카드)",
    RegexOption.IGNORE_CASE
)
private val PAYMENT_EASY_PAY_REGEX = Regex(
    "(네이버페이|카카오페이|토스페이|페이코)\\s*(?:결제|할인|시\\s*\\d|적립\\s*할인|체감|최대|\\d+%)",
    RegexOption.IGNORE_CASE
)
private val WOW_MEMBERSHIP_TITLE_REGEX = Regex("(?:와우\\s*(?:할인|회원|특가|전용|\\d)|\\[\\s*와우\\s+\\d)", RegexOption.IGNORE_CASE)
private val WOW_SHIP_REGEX = Regex("와우", RegexOption.IGNORE_CASE)
private val NMEM_MEMBERSHIP_TITLE_REGEX = Regex("(?:네멤\\s*(?:할인|특가|전용)|네이버\\s*멤버십)", RegexOption.IGNORE_CASE)
private val NMEM_SHIP_REGEX = Regex("네멤", RegexOption.IGNORE_CASE)
private val UNIVERSE_CLUB_REGEX = Regex("유니버스\\s*클럽", RegexOption.IGNORE_CASE)
private val SUPER_REWARD_REGEX = Regex("슈퍼\\s*적립", RegexOption.IGNORE_CASE)

/**
 * 제목/배송비에서 특정 카드사·간편결제·멤버십 전용 조건 배지를 추출한다.
 */
fun extractPaymentConditionBadge(
    title: String?,
    shippingFee: String? = null
): String? {
    val cleanTitle = decodeHtmlEntities(title.orEmpty()).trim()
    val cleanShip = decodeHtmlEntities(shippingFee.orEmpty()).trim()
    if (cleanTitle.isEmpty() && cleanShip.isEmpty()) return null

    PAYMENT_CARD_REGEX.find(cleanTitle)?.let { match ->
        val rawUpper = match.groupValues[1].replace(MULTI_WHITESPACE_REGEX, "").uppercase(Locale.US)
        return when {
            rawUpper.startsWith("KB") -> "국민카드 조건"
            rawUpper.startsWith("NH") -> "농협카드 조건"
            rawUpper.startsWith("BC") -> "비씨카드 조건"
            else -> "${match.groupValues[1].replace(MULTI_WHITESPACE_REGEX, "")} 조건"
        }
    }

    PAYMENT_EASY_PAY_REGEX.find(cleanTitle)?.let { match ->
        return "${match.groupValues[1]} 조건"
    }

    if (WOW_MEMBERSHIP_TITLE_REGEX.containsMatchIn(cleanTitle) || WOW_SHIP_REGEX.containsMatchIn(cleanShip)) {
        return "와우회원 조건"
    }
    if (NMEM_MEMBERSHIP_TITLE_REGEX.containsMatchIn(cleanTitle) || NMEM_SHIP_REGEX.containsMatchIn(cleanShip)) {
        return "네멤 조건"
    }
    if (UNIVERSE_CLUB_REGEX.containsMatchIn(cleanTitle)) {
        return "유니버스클럽 조건"
    }
    if (SUPER_REWARD_REGEX.containsMatchIn(cleanTitle)) {
        return "슈퍼적립 조건"
    }
    return null
}

/**
 * 식품·생활용품·소모품 제목에서 중량/용량/매x팩/수량을 추출해 100g당·100ml당·1팩당·개당·상품권 할인율을 계산한다.
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

    // 0. 상품권·금액권 액면가 대비 할인율 배지
    val voucherTitle = MULTI_WHITESPACE_REGEX.replace(
        LEADING_BRACKET_TITLE_REGEX.replace(decodeHtmlEntities(title), ""),
        " "
    ).trim()
    if (
        voucherTitle.isNotEmpty() &&
        !voucherTitle.contains("/") &&
        VOUCHER_TITLE_REGEX.containsMatchIn(voucherTitle) &&
        !VOUCHER_GIFT_ATTACHED_REGEX.containsMatchIn(voucherTitle)
    ) {
        var faceValue = 0L
        val manMatch = VOUCHER_MAN_FACE_REGEX.find(voucherTitle)
        if (manMatch != null) {
            faceValue = (manMatch.groupValues[1].toLongOrNull() ?: 0L) * 10_000L
        } else {
            val thouMatch = VOUCHER_THOUSAND_FACE_REGEX.find(voucherTitle)
            if (thouMatch != null) {
                faceValue = thouMatch.groupValues[1].replace(",", "").toLongOrNull() ?: 0L
            }
        }
        if (faceValue > price) {
            val discountPct = ((faceValue - price).toDouble() / faceValue.toDouble()) * 100.0
            if (discountPct in 1.0..35.0) {
                val rounded10 = (discountPct * 10.0).roundToLong()
                val pctLabel = if (rounded10 % 10L == 0L) {
                    "${rounded10 / 10L}% 할인"
                } else {
                    String.format(Locale.US, "약 %.1f%% 할인", rounded10 / 10.0)
                }
                return UnitPriceBadgeInfo(
                    label = pctLabel,
                    detail = "액면가 ${formatKorComma(faceValue)}원 기준"
                )
            }
        }
    }

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

    // 2. 중량 (kg / g) — 100g당 및 소용량 포(2g x 30포 2박스 -> 1포당) 지원
    val weightEntries = mutableListOf<WeightEntry>()
    for (wm in WEIGHT_REGEX.findAll(cleanedTitle)) {
        val rawVal = wm.groupValues[1].toDoubleOrNull() ?: continue
        if (rawVal <= 0.0) continue
        val unit = wm.groupValues[2].lowercase(Locale.US)
        val unitGrams = if (unit == "kg") rawVal * 1000.0 else rawVal
        val startIdx = wm.range.first
        val endIdx = wm.range.last + 1
        val beforeSlice = cleanedTitle.substring(maxOf(0, startIdx - 12), startIdx)
        val afterStr = cleanedTitle.substring(endIdx)
        val trailing = extractTrailingMeta(afterStr, TRAILING_WEIGHT_COUNT_REGEX)

        if (unitGrams < 10.0) {
            if (
                unitGrams >= 1.0 &&
                !NUTRITION_WORD_REGEX.containsMatchIn(beforeSlice) &&
                trailing.matched &&
                trailing.count >= 5L &&
                SMALL_SACHET_UNIT_REGEX.matches(trailing.unitLabel)
            ) {
                val sachetTotal = unitGrams * trailing.count
                if (sachetTotal in 20.0..100_000.0) {
                    val perSachet = (price.toDouble() / trailing.count).roundToLong()
                    if (perSachet in 10L until price) {
                        val uName = if (SMALL_SACHET_LABEL_UNIT_REGEX.matches(trailing.unitLabel)) {
                            trailing.unitLabel
                        } else {
                            "개"
                        }
                        return UnitPriceBadgeInfo(
                            label = "1${uName}당 ${formatKorComma(perSachet)}원",
                            detail = "총 ${trailing.count}${trailing.unitLabel} · ${formatKorComma(sachetTotal.roundToLong())}g 기준"
                        )
                    }
                }
            }
            continue
        }

        val totalGrams = unitGrams * trailing.count
        if (totalGrams in 10.0..100_000.0) {
            weightEntries.add(WeightEntry(totalGrams, trailing.count, startIdx, endIdx))
        }
    }

    if (weightEntries.size >= 2 && cleanedTitle.contains("+") && !cleanedTitle.contains("/")) {
        var chained = false
        var sumGrams = weightEntries[0].totalGrams
        for (i in 1 until weightEntries.size) {
            val gap = cleanedTitle.substring(weightEntries[i - 1].endIdx, weightEntries[i].startIdx)
            if (gap.contains("+") && !gap.contains("/")) {
                sumGrams += weightEntries[i].totalGrams
                chained = true
            }
        }
        if (chained && sumGrams in 50.0..100_000.0) {
            val per100g = ((price.toDouble() / sumGrams) * 100.0).roundToLong()
            if (per100g in 10L until price) {
                return UnitPriceBadgeInfo(
                    label = "100g당 ${formatKorComma(per100g)}원",
                    detail = "총 ${formatKorComma(sumGrams.roundToLong())}g 기준"
                )
            }
        }
    }

    for (we in weightEntries) {
        if (we.totalGrams in 50.0..100_000.0) {
            val per100g = ((price.toDouble() / we.totalGrams) * 100.0).roundToLong()
            if (per100g in 10L until price) {
                val totalRounded = we.totalGrams.roundToLong()
                val detail = if (we.count >= 2L) {
                    val perCount = (price.toDouble() / we.count).roundToLong()
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

    for (we in weightEntries) {
        if (we.totalGrams >= 10.0 && we.totalGrams < 100.0) {
            val per10g = ((price.toDouble() / we.totalGrams) * 10.0).roundToLong()
            if (per10g in 10L until price) {
                val totalRounded = we.totalGrams.roundToLong()
                val detail = if (we.count >= 2L) {
                    val perCount = (price.toDouble() / we.count).roundToLong()
                    "총 ${formatKorComma(totalRounded)}g · 개당 ${formatKorComma(perCount)}원"
                } else {
                    "총 ${formatKorComma(totalRounded)}g 기준"
                }
                return UnitPriceBadgeInfo(
                    label = "10g당 ${formatKorComma(per10g)}원",
                    detail = detail
                )
            }
        }
    }

    // 3. 용량 (ml / L / 리터)
    val volumeEntries = mutableListOf<VolumeEntry>()
    for (vm in VOLUME_REGEX.findAll(cleanedTitle)) {
        val rawVal = vm.groupValues[1].toDoubleOrNull() ?: continue
        if (rawVal <= 0.0) continue
        val unit = vm.groupValues[2].lowercase(Locale.US)
        val unitMl = if (unit == "ml") rawVal else rawVal * 1000.0
        if (unitMl < 10.0) continue
        val startIdx = vm.range.first
        val endIdx = vm.range.last + 1
        val afterStr = cleanedTitle.substring(endIdx)
        val trailing = extractTrailingMeta(afterStr, TRAILING_VOLUME_COUNT_REGEX)
        val totalMl = unitMl * trailing.count
        if (totalMl in 10.0..100_000.0) {
            volumeEntries.add(VolumeEntry(totalMl, trailing.count, startIdx, endIdx))
        }
    }

    if (volumeEntries.size >= 2 && cleanedTitle.contains("+") && !cleanedTitle.contains("/")) {
        var chained = false
        var sumMl = volumeEntries[0].totalMl
        for (i in 1 until volumeEntries.size) {
            val gap = cleanedTitle.substring(volumeEntries[i - 1].endIdx, volumeEntries[i].startIdx)
            if (gap.contains("+") && !gap.contains("/")) {
                sumMl += volumeEntries[i].totalMl
                chained = true
            }
        }
        if (chained && sumMl in 50.0..100_000.0) {
            val per100ml = ((price.toDouble() / sumMl) * 100.0).roundToLong()
            if (per100ml in 10L until price) {
                return UnitPriceBadgeInfo(
                    label = "100ml당 ${formatKorComma(per100ml)}원",
                    detail = "총 ${formatKorComma(sumMl.roundToLong())}ml 기준"
                )
            }
        }
    }

    for (ve in volumeEntries) {
        if (ve.totalMl in 50.0..100_000.0) {
            val per100ml = ((price.toDouble() / ve.totalMl) * 100.0).roundToLong()
            if (per100ml in 10L until price) {
                val totalRounded = ve.totalMl.roundToLong()
                val detail = if (ve.count >= 2L) {
                    val perCount = (price.toDouble() / ve.count).roundToLong()
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

    for (ve in volumeEntries) {
        if (ve.totalMl >= 10.0 && ve.totalMl < 100.0) {
            val per10ml = ((price.toDouble() / ve.totalMl) * 10.0).roundToLong()
            if (per10ml in 10L until price) {
                val totalRounded = ve.totalMl.roundToLong()
                val detail = if (ve.count >= 2L) {
                    val perCount = (price.toDouble() / ve.count).roundToLong()
                    "총 ${formatKorComma(totalRounded)}ml · 개당 ${formatKorComma(perCount)}원"
                } else {
                    "총 ${formatKorComma(totalRounded)}ml 기준"
                }
                return UnitPriceBadgeInfo(
                    label = "10ml당 ${formatKorComma(per10ml)}원",
                    detail = detail
                )
            }
        }
    }

    // 4. 순수 묶음 수량 (총N입/스틱/인분/환/롤/캡슐/정/켤레/팩/봉 + 계란 한정 N구)
    TOTAL_BUNDLE_REGEX.find(cleanedTitle)?.let { tbm ->
        val cnt = tbm.groupValues[1].toLongOrNull() ?: 0L
        formatBundleBadge(price, cnt, tbm.groupValues[2])?.let { return it }
    }

    for (bm in BUNDLE_COUNT_REGEX.findAll(cleanedTitle)) {
        val baseCount = bm.groupValues[1].toLongOrNull() ?: 0L
        val unitLabel = bm.groupValues[2]
        val afterStr = cleanedTitle.substring(bm.range.last + 1)
        val tierMatch = BUNDLE_TIER_MULTIPLIER_REGEX.find(afterStr.take(20))
        val tierMult = tierMatch?.groupValues?.get(1)?.toLongOrNull() ?: 1L
        val totalCount = if (tierMult > 1L && baseCount * tierMult <= 500L) baseCount * tierMult else baseCount
        formatBundleBadge(price, totalCount, unitLabel)?.let { return it }
    }

    if (
        !APPLIANCE_OUTLET_REGEX.containsMatchIn(cleanedTitle) &&
        (EGG_ITEM_HINT_REGEX.containsMatchIn(cleanedTitle) || (category ?: "").trim() == "음식")
    ) {
        EGG_COUNT_REGEX.find(cleanedTitle)?.let { eggMatch ->
            val baseEgg = eggMatch.groupValues[1].toLongOrNull() ?: 0L
            if (EGG_ITEM_HINT_REGEX.containsMatchIn(cleanedTitle) || baseEgg >= 10L) {
                val afterEgg = cleanedTitle.substring(eggMatch.range.last + 1)
                val tierMatch = EGG_TIER_MULTIPLIER_REGEX.find(afterEgg.take(18))
                val tierMult = tierMatch?.groupValues?.get(1)?.toLongOrNull() ?: 1L
                val totalEggs = if (tierMult > 1L && baseEgg * tierMult <= 500L) baseEgg * tierMult else baseEgg
                formatBundleBadge(price, totalEggs, "구")?.let { return it }
            }
        }
    }

    // 식품 맛 조합 수량 (예: '애슐리 딥디쉬 피자 시카고2+고구마2')
    if ((category ?: "").trim() == "음식" && !cleanedTitle.contains("/")) {
        FOOD_COMBO_COUNT_REGEX.find(cleanedTitle)?.let { comboMatch ->
            val c1 = comboMatch.groupValues[2].toLongOrNull() ?: 0L
            val c2 = comboMatch.groupValues[4].toLongOrNull() ?: 0L
            val totalCombo = c1 + c2
            if (c1 in 1L..20L && c2 in 1L..20L && totalCombo in 2L..40L) {
                formatBundleBadge(price, totalCombo, "개")?.let { return it }
            }
        }
    }

    return null
}

package com.ddaeany0919.insightdeal.presentation.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

// =========================================================================
// 얼만데(InsightDeal) 한국형 플랫 미니멀 디자인 시스템 컬러 토큰 (WCAG AA 4.5:1 준수)
// - 에메랄드 민트(#00B87A 포인트) + 토스 블루(#1B64DA / #60A5FA) + 뉴트럴 표면
// - 테무/알리식 원색·네온·3D 글로우 0% 배제
// =========================================================================

val White = Color(0xFFFFFFFF)
val Black = Color(0xFF000000)

// 브랜드 포인트 (아이콘/로고/장식 도트 전용 — 라이트 본문 텍스트로 단독 사용 금지)
val BrandEmeraldPoint = Color(0xFF00B87A)

// 라이트 모드 고대비 브랜드/액션 토큰 (WCAG AA >= 4.5:1)
val EmeraldPrimaryLight = Color(0xFF006B47)        // vs White 6.57:1
val EmeraldPrimaryStrongLight = Color(0xFF005C3D)  // vs White 7.92:1
val EmeraldContainerLight = Color(0xFFE6F7F0)
val OnEmeraldContainerLight = Color(0xFF005C3D)    // vs #E6F7F0 7.28:1

val TossBlueLight = Color(0xFF1B64DA)              // vs White 5.41:1
val TossBlueContainerLight = Color(0xFFE8F3FF)
val OnTossBlueContainerLight = Color(0xFF154EAD)   // vs #E8F3FF 6.86:1

// 다크/AMOLED 모드 고대비 브랜드/액션 토큰
val EmeraldPrimaryDark = Color(0xFF34D399)         // vs #191F28 9.68:1 / Black 10.92:1
val OnEmeraldDark = Color(0xFF0A1A14)              // vs #34D399 9.33:1
val EmeraldContainerDark = Color(0xFF0D3B2B)
val OnEmeraldContainerDark = Color(0xFFA7F3D0)     // vs #0D3B2B 9.76:1

val TossBlueDark = Color(0xFF60A5FA)               // vs #191F28 7.32:1 / Black 8.26:1
val OnTossBlueDark = Color(0xFF081B33)             // vs #60A5FA 6.80:1
val TossBlueContainerDark = Color(0xFF1E3A5F)
val OnTossBlueContainerDark = Color(0xFFD1E5FF)    // vs #1E3A5F 8.70:1

// 뉴트럴 캔버스 및 카드 표면 토큰
val CanvasLight = Color(0xFFF6F8FA)
val CardLight = Color(0xFFFFFFFF)
val SurfaceVariantLight = Color(0xFFF0F3F7)
val BorderLight = Color(0xFFE5E8EB)
val InkPrimaryLight = Color(0xFF191F28)
val InkSecondaryLight = Color(0xFF525D6B)          // vs #F0F3F7 ~6.0:1, vs #FFFFFF ~6.7:1

val CanvasDark = Color(0xFF101318)
val CardDark = Color(0xFF191F28)
val SurfaceVariantDark = Color(0xFF222934)
val BorderDark = Color(0xFF2B333E)
val BorderAmoled = Color(0xFF262A30)
val InkPrimaryDark = Color(0xFFF9FAFB)
val InkSecondaryDark = Color(0xFFA8B3C2)           // vs #222934 ~6.7:1, vs #191F28 ~7.6:1

// 도메인 시맨틱 컬러 (가격 강조 / 성공·최저가 / 경고·최고가·검색이동 / 품절·에러 / 보조 텍스트)
@Immutable
data class InsightDealSemanticColors(
    val priceAccent: Color,
    val priceContainer: Color,
    val onPriceContainer: Color,
    val success: Color,
    val successContainer: Color,
    val onSuccessContainer: Color,
    val warning: Color,
    val warningContainer: Color,
    val onWarningContainer: Color,
    val danger: Color,
    val dangerContainer: Color,
    val onDangerContainer: Color,
    val info: Color,
    val infoContainer: Color,
    val onInfoContainer: Color,
    val textMuted: Color,
    val brandPoint: Color,
    val cardBorder: Color
)

val LightInsightDealColors = InsightDealSemanticColors(
    priceAccent = Color(0xFFC2410C),               // vs White 5.18:1
    priceContainer = Color(0xFFFFF7ED),
    onPriceContainer = Color(0xFF9A3412),          // vs #FFF7ED 6.88:1
    success = EmeraldPrimaryLight,                 // vs White 6.57:1
    successContainer = EmeraldContainerLight,
    onSuccessContainer = OnEmeraldContainerLight,  // vs #E6F7F0 7.28:1
    warning = Color(0xFF92400E),                   // vs White 7.09:1
    warningContainer = Color(0xFFFEF3C7),
    onWarningContainer = Color(0xFF92400E),        // vs #FEF3C7 6.37:1
    danger = Color(0xFFC81E1E),                    // vs White 5.74:1
    dangerContainer = Color(0xFFFEE2E2),
    onDangerContainer = Color(0xFF991B1B),         // vs #FEE2E2 6.86:1
    info = TossBlueLight,                          // vs White 5.41:1
    infoContainer = TossBlueContainerLight,
    onInfoContainer = OnTossBlueContainerLight,
    textMuted = Color(0xFF525D6B),                 // vs White 6.7:1, vs #F6F8FA 6.2:1
    brandPoint = BrandEmeraldPoint,
    cardBorder = BorderLight
)

val DarkInsightDealColors = InsightDealSemanticColors(
    priceAccent = Color(0xFFFB923C),               // vs #191F28 7.5:1
    priceContainer = Color(0xFF3D2210),
    onPriceContainer = Color(0xFFFDBA74),          // vs #3D2210 7.5:1
    success = EmeraldPrimaryDark,                  // vs #191F28 9.68:1
    successContainer = EmeraldContainerDark,
    onSuccessContainer = OnEmeraldContainerDark,   // vs #0D3B2B 9.76:1
    warning = Color(0xFFFBBF24),                   // vs #191F28 11.15:1
    warningContainer = Color(0xFF3D2E0A),
    onWarningContainer = Color(0xFFFCD34D),        // vs #3D2E0A 8.9:1
    danger = Color(0xFFF87171),                    // vs #191F28 6.73:1
    dangerContainer = Color(0xFF450A0A),
    onDangerContainer = Color(0xFFFECACA),         // vs #450A0A 11.0:1
    info = TossBlueDark,                           // vs #191F28 7.32:1
    infoContainer = TossBlueContainerDark,
    onInfoContainer = OnTossBlueContainerDark,
    textMuted = InkSecondaryDark,                  // vs #191F28 7.6:1
    brandPoint = BrandEmeraldPoint,
    cardBorder = BorderDark
)

val LocalInsightDealColors = staticCompositionLocalOf { LightInsightDealColors }

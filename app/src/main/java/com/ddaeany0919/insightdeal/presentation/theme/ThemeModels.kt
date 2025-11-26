package com.ddaeany0919.insightdeal.presentation.theme

import androidx.compose.ui.graphics.Color

/**
 * 🌙 테마 모드 옵션
 */
enum class ThemeMode(val displayName: String, val description: String) {
    LIGHT("라이트 모드", "밝은 배경으로 낮에 최적"),
    DARK("다크 모드", "어두운 배경으로 눈의 피로 감소"),
    AMOLED("AMOLED 블랙", "완전한 검은색으로 배터리 절약"),
    SYSTEM("시스템 따라가기", "시스템 설정에 맞춰 자동 전환")
}

/**
 * 🎨 컬러 스킴 옵션
 */
enum class AppColorScheme(
    val displayName: String,
    val primaryColor: Color,
    val description: String
) {
    ORANGE_CLASSIC(
        "오렌지 클래식",
        Color(0xFFFF6B35),
        "따뜻하고 에너지 넘치는 InsightDeal 기본 테마"
    ),
    BLUE_MODERN(
        "블루 모던",
        Color(0xFF2196F3),
        "깔끔하고 전문적인 비즈니스 테마"
    ),
    GREEN_NATURAL(
        "그린 내추럴",
        Color(0xFF4CAF50),
        "안정적이고 친환경적인 내추럴 테마"
    ),
    PURPLE_LUXURY(
        "퍼플 럭셔리",
        Color(0xFF9C27B0),
        "럭셔리하고 세련된 프리미엄 테마"
    )
}

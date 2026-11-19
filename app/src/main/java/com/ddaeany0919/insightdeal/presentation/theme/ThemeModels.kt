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
    EMERALD_MINT(
        "얼만데 에메랄드 민트",
        Color(0xFF006B47),
        "고대비 에메랄드 민트와 뉴트럴 표면의 기본 테마"
    ),
    TOSS_BLUE(
        "토스 블루 액센트",
        Color(0xFF1B64DA),
        "깔끔하고 차분한 블루 포인트 테마"
    )
}

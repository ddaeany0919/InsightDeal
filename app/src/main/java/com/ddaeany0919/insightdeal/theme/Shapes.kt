package com.ddaeany0919.insightdeal.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

/**
 * 🔳 InsightDeal Shape 시스템
 */
val InsightDealShapes = Shapes(
    // 작은 컴포넌트 (버튼, 칩)
    small = RoundedCornerShape(8.dp),
    
    // 중간 컴포넌트 (카드, 다이얼로그)
    medium = RoundedCornerShape(12.dp),
    
    // 큰 컴포넌트 (바텀시트, 전체화면)
    large = RoundedCornerShape(16.dp)
)
package com.ddaeany0919.insightdeal.presentation.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ddaeany0919.insightdeal.domain.HotDealScore
import com.ddaeany0919.insightdeal.domain.DealBadge
import com.ddaeany0919.insightdeal.domain.ScoreBreakdown

/**
 * 🏅 꿀딜 지수 배지 UI 컴포넌트
 * 
 * AI 분석 결과를 시각적으로 표현하는 동적 배지 시스템
 */
@Composable
fun HotDealScoreBadge(
    score: HotDealScore,
    modifier: Modifier = Modifier,
    showDetails: Boolean = false,
    animated: Boolean = true
) {
    val badge = score.badge
    val targetColor = Color(android.graphics.Color.parseColor(badge.color))
    
    val badgeColor by animateColorAsState(
        targetValue = if (animated) targetColor else targetColor,
        animationSpec = tween(durationMillis = if (animated) 800 else 0),
        label = "badgeColor"
    )
    
    Surface(
        modifier = modifier.clip(RoundedCornerShape(20.dp)),
        color = badgeColor.copy(alpha = 0.9f),
        shadowElevation = 4.dp
    ) {
        Row(
            modifier = Modifier.padding(
                horizontal = if (showDetails) 12.dp else 8.dp,
                vertical = 6.dp
            ),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            // 🎯 점수 표시
            Text(
                text = "${score.score}",
                fontSize = if (showDetails) 16.sp else 14.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
            
            if (showDetails) {
                Spacer(modifier = Modifier.width(4.dp))
                
                Text(
                    text = "점",
                    fontSize = 12.sp,
                    color = Color.White.copy(alpha = 0.9f)
                )
                
                Spacer(modifier = Modifier.width(6.dp))
            }
            
            // 🏆 배지 아이콘
            Text(
                text = badge.emoji,
                fontSize = if (showDetails) 16.sp else 14.sp
            )
            
            // 📝 상세 텍스트 (옵션)
            if (showDetails) {
                Spacer(modifier = Modifier.width(4.dp))
                
                Text(
                    text = badge.text,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color.White
                )
            }
        }
    }
}

/**
 * 📊 상세 점수 분석 카드
 */
@Composable
fun DetailedScoreCard(
    score: HotDealScore,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            // 🏆 메인 점수 헤더
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "꿀딜 지수 분석",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
                
                HotDealScoreBadge(
                    score = score,
                    showDetails = true
                )
            }
            
            Spacer(modifier = Modifier.height(12.dp))
            
            // 💬 AI 추천 문구
            Text(
                text = score.recommendation,
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 16.dp)
            )
            
            // 📊 점수 세부 분석
            ScoreBreakdownChart(score.breakdown)
        }
    }
}

/**
 * 📈 점수 세부 분석 차트
 */
@Composable
private fun ScoreBreakdownChart(breakdown: ScoreBreakdown) {
    Column {
        Text(
            text = "세부 분석",
            fontSize = 16.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(bottom = 12.dp)
        )
        
        ScoreBarItem("💬 댓글 감정", breakdown.sentiment, Color(0xFF4CAF50))
        Spacer(modifier = Modifier.height(8.dp))
        
        ScoreBarItem("👥 커뮤니티 반응", breakdown.community, Color(0xFF2196F3))
        Spacer(modifier = Modifier.height(8.dp))
        
        ScoreBarItem("🏆 사이트 신뢰도", breakdown.site, Color(0xFFFF9800))
        Spacer(modifier = Modifier.height(8.dp))
        
        ScoreBarItem("⏰ 정보 신선도", breakdown.freshness, Color(0xFF9C27B0))
    }
}

/**
 * 📊 개별 점수 바 아이템
 */
@Composable
private fun ScoreBarItem(
    label: String,
    score: Int,
    color: Color
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            fontSize = 14.sp,
            modifier = Modifier.width(120.dp)
        )
        
        Box(
            modifier = Modifier
                .weight(1f)
                .height(12.dp)
                .background(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(6.dp)
                )
        ) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(score / 100f)
                    .background(
                        color = color,
                        shape = RoundedCornerShape(6.dp)
                    )
            )
        }
        
        Spacer(modifier = Modifier.width(8.dp))
        
        Text(
            text = "${score}점",
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            color = color,
            modifier = Modifier.width(40.dp)
        )
    }
}

/**
 * ⚡ 간단한 인라인 배지 (목록에서 사용)
 */
@Composable
fun InlineScoreBadge(
    score: Int,
    modifier: Modifier = Modifier
) {
    val badge = when {
        score >= 90 -> DealBadge.SUPER_HOT
        score >= 70 -> DealBadge.RECOMMENDED
        score >= 50 -> DealBadge.OKAY
        score >= 30 -> DealBadge.CAUTION
        else -> DealBadge.AVOID
    }
    
    Surface(
        modifier = modifier,
        color = Color(android.graphics.Color.parseColor(badge.color)),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = badge.emoji,
                fontSize = 12.sp
            )
            
            Spacer(modifier = Modifier.width(2.dp))
            
            Text(
                text = "$score",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
        }
    }
}

/**
 * 🎨 배지 미리보기 (설정 화면용)
 */
@Composable
fun ScoreBadgePreview() {
    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.padding(16.dp)
    ) {
        Text(
            text = "꿀딜 지수 배지 시스템",
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 8.dp)
        )
        
        val sampleScores = listOf(95, 80, 65, 45, 20)
        
        sampleScores.forEach { sampleScore ->
            val sampleHotDealScore = HotDealScore(
                score = sampleScore,
                badge = when {
                    sampleScore >= 90 -> DealBadge.SUPER_HOT
                    sampleScore >= 70 -> DealBadge.RECOMMENDED
                    sampleScore >= 50 -> DealBadge.OKAY
                    sampleScore >= 30 -> DealBadge.CAUTION
                    else -> DealBadge.AVOID
                },
                recommendation = "",
                breakdown = ScoreBreakdown()
            )
            
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                HotDealScoreBadge(
                    score = sampleHotDealScore,
                    showDetails = true
                )
                
                Spacer(modifier = Modifier.width(12.dp))
                
                Text(
                    text = "${sampleScore}점 예시",
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
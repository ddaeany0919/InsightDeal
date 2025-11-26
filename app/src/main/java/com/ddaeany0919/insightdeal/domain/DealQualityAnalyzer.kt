package com.ddaeany0919.insightdeal.domain

import android.util.Log
import com.ddaeany0919.insightdeal.models.DealItem
import kotlin.math.*

/**
 * 🧠 AI 기반 꿀딜 품질 분석기
 * * 댓글 감정 분석 + 커뮤니티 반응 + 사이트 신뢰도를 종합하여
 * 0~100점 꿀딜 지수를 계산하는 머신러닝 시스템
 */
class DealQualityAnalyzer {

    companion object {
        private const val TAG = "DealQualityAnalyzer"

        // 🟢 긍정 키워드 (가중치)
        private val POSITIVE_KEYWORDS = mapOf(
            "대박" to 20,
            "꿀딜" to 18,
            "가성비" to 15,
            "강추" to 15,
            "추천" to 12,
            "좋다" to 10,
            "괜찮" to 8,
            "만족" to 10,
            "저렴" to 12,
            "싸다" to 12,
            "혜택" to 10,
            "이득" to 10,
            "득템" to 15,
            "완전" to 8,
            "최고" to 12,
            "굿" to 8,
            "good" to 8,
            "짱" to 10
        )

        // 🔴 부정 키워드 (가중치)
        private val NEGATIVE_KEYWORDS = mapOf(
            "사기" to -25,
            "품절" to -15,
            "비싸" to -12,
            "별로" to -10,
            "실망" to -15,
            "후회" to -18,
            "최악" to -20,
            "망함" to -20,
            "돈아까" to -15,
            "쓰레기" to -25,
            "거품" to -12,
            "과대광고" to -18,
            "주의" to -8,
            "조심" to -8,
            "피하" to -15,
            "사지마" to -20,
            "비추" to -12
        )

        // ⚪ 중립 키워드 (정보성)
        private val NEUTRAL_KEYWORDS = setOf(
            "정보", "후기", "리뷰", "궁금", "문의", "질문",
            "어떤", "어디", "언제", "얼마", "몇개", "크기",
            "배송", "택배", "수령", "도착", "주문", "결제"
        )

        // 🏆 사이트별 신뢰도 (0~100)
        private val SITE_CREDIBILITY = mapOf(
            "ppomppu" to 85,    // 뽐뿌 (1순위)
            "fmkorea" to 85,    // 에펨코리아 (1순위)
            "bbasak" to 75,     // 빠삭 (2순위, 중간)
            "ruliweb" to 70,    // 루리웹 (3순위)
            "clien" to 70,      // 클리앙 (3순위)
            "quasarzone" to 70  // 퀘이사존 (3순위)
        )
    }

    /**
     * 🎯 메인 꿀딜 지수 계산 (0~100점)
     */
    fun calculateHotDealScore(deal: DealItem): HotDealScore {
        try {
            Log.d(TAG, "🔍 꿀딜 분석 시작: ${deal.title.take(30)}...")

            // 1️⃣ 댓글 감정 분석 (40% 가중치)
            // ✅ [수정] deal.content 대신 deal.title을 분석합니다.
            val sentimentScore = analyzeSentiment(deal.title, deal.commentCount)

            // 2️⃣ 커뮤니티 반응 분석 (30% 가중치)
            val communityScore = analyzeCommunityReaction(deal)

            // 3️⃣ 사이트 신뢰도 (20% 가중치)
            val siteScore = getSiteCredibility(deal.siteName)

            // 4️⃣ 시간 신선도 (10% 가중치)
            val freshnessScore = calculateFreshnessScore(deal.createdAt)

            // 🧮 최종 점수 계산
            val finalScore = (sentimentScore * 0.4 +
                    communityScore * 0.3 +
                    siteScore * 0.2 +
                    freshnessScore * 0.1)
                .coerceIn(0.0, 100.0)

            val scoreInt = finalScore.toInt()

            Log.d(TAG, "📊 꿀딜 점수 계산 완료: ${scoreInt}점 (감정:${sentimentScore.toInt()}, 커뮤니티:${communityScore.toInt()}, 사이트:$siteScore, 신선도:${freshnessScore.toInt()})")

            return HotDealScore(
                score = scoreInt,
                badge = getBadgeForScore(scoreInt),
                recommendation = getRecommendationText(scoreInt),
                breakdown = ScoreBreakdown(
                    sentiment = sentimentScore.toInt(),
                    community = communityScore.toInt(),
                    site = siteScore,
                    freshness = freshnessScore.toInt()
                )
            )

        } catch (e: Exception) {
            Log.e(TAG, "❌ 꿀딜 분석 오류: ${e.message}")
            return HotDealScore(
                score = 50,
                badge = DealBadge.OKAY,
                recommendation = "분석 중...",
                breakdown = ScoreBreakdown()
            )
        }
    }

    /**
     * 💬 댓글 감정 분석
     */
    // ✅ [수정] 파라미터 이름을 content에서 text로 변경 (필수는 아니지만 명확성을 위해)
    private fun analyzeSentiment(textToAnalyze: String, commentCount: Int): Double {
        if (textToAnalyze.isEmpty()) return 50.0

        val text = textToAnalyze.lowercase()
        var sentimentScore = 50.0
        var keywordMatches = 0

        // 긍정 키워드 점수
        POSITIVE_KEYWORDS.forEach { (keyword, weight) ->
            val matches = countKeywordOccurrences(text, keyword)
            if (matches > 0) {
                sentimentScore += matches * weight
                keywordMatches += matches
            }
        }

        // 부정 키워드 점수
        NEGATIVE_KEYWORDS.forEach { (keyword, weight) ->
            val matches = countKeywordOccurrences(text, keyword)
            if (matches > 0) {
                sentimentScore += matches * weight // weight는 이미 음수
                keywordMatches += matches
            }
        }

        // 댓글 수 보정 (많을수록 신뢰도 증가)
        val commentBonus = when {
            commentCount >= 50 -> 10.0
            commentCount >= 20 -> 5.0
            commentCount >= 10 -> 2.0
            else -> 0.0
        }

        sentimentScore += commentBonus

        Log.d(TAG, "💬 감정분석: ${sentimentScore.toInt()}점 (키워드:$keywordMatches, 댓글:$commentCount)")
        return sentimentScore.coerceIn(0.0, 100.0)
    }

    /**
     * 👥 커뮤니티 반응 분석
     */
    private fun analyzeCommunityReaction(deal: DealItem): Double {
        var communityScore = 50.0

        // 조회수 보정
        val viewCount = deal.viewCount
        val viewBonus = when {
            viewCount >= 10000 -> 20.0
            viewCount >= 5000 -> 15.0
            viewCount >= 1000 -> 10.0
            viewCount >= 500 -> 5.0
            else -> 0.0
        }
        communityScore += viewBonus

        // 댓글/조회수 비율 (참여도)
        val commentCount = deal.commentCount
        if (viewCount > 0) {
            val participationRatio = (commentCount.toDouble() / viewCount) * 100
            val participationBonus = when {
                participationRatio >= 5.0 -> 15.0  // 고참여도
                participationRatio >= 2.0 -> 10.0
                participationRatio >= 1.0 -> 5.0
                else -> 0.0
            }
            communityScore += participationBonus
        }

        // 추천/반대 비율 (좋아요/싫어요)
        val likeCount = deal.likeCount
        val dislikeCount = deal.dislikeCount
        if (likeCount > 0 || dislikeCount > 0) {
            val totalVotes = likeCount + dislikeCount
            val likeRatio = likeCount.toDouble() / totalVotes
            val voteBonus = when {
                likeRatio >= 0.8 -> 15.0  // 80% 이상 좋아요
                likeRatio >= 0.6 -> 10.0
                likeRatio >= 0.4 -> 0.0
                else -> -10.0             // 부정적 반응 많음
            }
            communityScore += voteBonus
        }

        Log.d(TAG, "👥 커뮤니티: ${communityScore.toInt()}점 (조회:$viewCount, 댓글:$commentCount, 좋아요:$likeCount)")
        return communityScore.coerceIn(0.0, 100.0)
    }

    /**
     * 🏆 사이트 신뢰도
     */
    private fun getSiteCredibility(siteName: String?): Int {
        val site = siteName?.lowercase() ?: return 50
        return SITE_CREDIBILITY[site] ?: 50
    }

    /**
     * ⏰ 시간 신선도 계산
     */
    private fun calculateFreshnessScore(createdAt: String?): Double {
        if (createdAt.isNullOrEmpty()) return 50.0

        // 현재 시간과의 차이 계산 (간소화 버전)
        // 실제로는 ISO 8601 파싱 필요
        val hoursAgo = extractHoursFromCreatedAt(createdAt)

        return when {
            hoursAgo <= 1 -> 100.0   // 1시간 이내
            hoursAgo <= 6 -> 85.0    // 6시간 이내
            hoursAgo <= 24 -> 70.0   // 하루 이내
            hoursAgo <= 72 -> 50.0   // 3일 이내
            else -> 30.0             // 오래됨
        }
    }

    /**
     * 🔍 키워드 발생 횟수 계산
     */
    private fun countKeywordOccurrences(text: String, keyword: String): Int {
        return Regex(keyword).findAll(text).count()
    }

    /**
     * ⏱️ 생성 시간에서 시간 추출 (간소화 버전)
     */
    private fun extractHoursFromCreatedAt(createdAt: String): Int {
        // 실제 구현에서는 정확한 시간 파싱 필요
        // 현재는 간단히 패턴 매칭
        return when {
            createdAt.contains("분 전") -> 0
            createdAt.contains("시간 전") -> {
                val hours = Regex("(\\d+)시간").find(createdAt)?.groupValues?.get(1)?.toIntOrNull() ?: 1
                hours
            }
            createdAt.contains("일 전") -> {
                val days = Regex("(\\d+)일").find(createdAt)?.groupValues?.get(1)?.toIntOrNull() ?: 1
                days * 24
            }
            else -> 24 // 기본 하루
        }
    }

    /**
     * 🏅 점수에 따른 배지 결정
     */
    private fun getBadgeForScore(score: Int): DealBadge {
        return when {
            score >= 90 -> DealBadge.SUPER_HOT
            score >= 70 -> DealBadge.RECOMMENDED
            score >= 50 -> DealBadge.OKAY
            score >= 30 -> DealBadge.CAUTION
            else -> DealBadge.AVOID
        }
    }

    /**
     * 💬 점수에 따른 추천 텍스트
     */
    private fun getRecommendationText(score: Int): String {
        return when {
            score >= 90 -> "🔥 초대박! 지금 당장 구매하세요!"
            score >= 70 -> "⭐ 추천! 좋은 딜입니다"
            score >= 50 -> "👍 괜찮은 가격이에요"
            score >= 30 -> "⚠️ 신중하게 검토해보세요"
            else -> "❌ 이 딜은 피하시는 게 좋겠어요"
        }
    }

    /**
     * 🚫 가짜 핫딜 감지
     */
    fun detectFakeDeal(deal: DealItem): Boolean {
        val title = deal.title.lowercase()
        // ✅ [수정] deal.content가 없으므로 해당 라인을 삭제합니다.
        // val content = deal.content.lowercase()

        // 클릭베이트 패턴 감지
        val clickbaitPatterns = listOf(
            "절대", "무조건", "100%", "확실한", "보장",
            "기회", "놓치면", "마지막", "한정", "특별",
            "!!!", "???", "대박!!!", "헐..."
        )

        // ✅ [수정] content 검사 로직을 제거하고 title만 검사합니다.
        val clickbaitCount = clickbaitPatterns.count { pattern ->
            title.contains(pattern) // || content.contains(pattern)
        }

        // 의심스러운 패턴이 3개 이상이면 가짜 딜 의심
        return clickbaitCount >= 3
    }

    /**
     * 📈 실시간 품질 트렌드 분석
     */
    fun analyzeTrendPattern(recentScores: List<Int>): TrendPattern {
        if (recentScores.size < 3) return TrendPattern.STABLE

        val recent3 = recentScores.takeLast(3)
        val slope = calculateSlope(recent3)

        return when {
            slope > 5 -> TrendPattern.IMPROVING
            slope < -5 -> TrendPattern.DECLINING
            else -> TrendPattern.STABLE
        }
    }

    private fun calculateSlope(scores: List<Int>): Double {
        if (scores.size < 2) return 0.0

        val n = scores.size
        val xMean = (n - 1) / 2.0
        val yMean = scores.average()

        var numerator = 0.0
        var denominator = 0.0

        scores.forEachIndexed { i, score ->
            val xDiff = i - xMean
            val yDiff = score - yMean
            numerator += xDiff * yDiff
            denominator += xDiff * xDiff
        }

        return if (denominator != 0.0) numerator / denominator else 0.0
    }
}

// 📊 데이터 모델들

data class HotDealScore(
    val score: Int,                    // 0~100점
    val badge: DealBadge,              // 배지
    val recommendation: String,        // 추천 문구
    val breakdown: ScoreBreakdown      // 점수 세부내역
)

data class ScoreBreakdown(
    val sentiment: Int = 50,           // 감정분석 점수
    val community: Int = 50,           // 커뮤니티 점수
    val site: Int = 50,                // 사이트 신뢰도
    val freshness: Int = 50            // 신선도 점수
)

enum class DealBadge(val emoji: String, val text: String, val color: String) {
    SUPER_HOT("🔥", "초대박 꿀딜", "#FF3030"),
    RECOMMENDED("⭐", "추천 핫딜", "#FF6B35"),
    OKAY("👍", "괜찮은 딜", "#2196F3"),
    CAUTION("⚠️", "신중하게", "#9E9E9E"),
    AVOID("❌", "피해야 할 딜", "#333333")
}

enum class TrendPattern {
    IMPROVING,    // 품질 상승 추세
    DECLINING,    // 품질 하락 추세
    STABLE        // 안정적
}

// 🛠️ 유틸리티 확장 함수
private fun String.containsAny(keywords: List<String>): Boolean {
    return keywords.any { this.contains(it, ignoreCase = true) }
}

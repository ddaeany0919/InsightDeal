package com.example.insightdeal.utils

import java.util.*
import kotlin.random.Random

object TrendingKeywordManager {

    // 시간대별 인기 키워드
    private fun getTimeBasedKeywords(): List<String> {
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        return when (hour) {
            in 6..11 -> listOf("커피", "아침식사", "출근", "모닝콜", "신문") // 아침
            in 12..17 -> listOf("점심", "배달", "택시", "회의", "업무") // 오후
            in 18..23 -> listOf("저녁", "배송", "쇼핑", "영화", "게임") // 저녁
            else -> listOf("야식", "24시", "새벽배송", "잠옷", "수면") // 새벽
        }
    }

    // 카테고리별 트렌딩 키워드
    private fun getCategoryTrendingKeywords(): List<String> {
        val categoryKeywords = mapOf(
            "디지털/가전" to listOf("갤럭시", "아이폰", "노트북", "에어팟", "충전기"),
            "PC/하드웨어" to listOf("그래픽카드", "모니터", "키보드", "마우스", "CPU"),
            "음식/식품" to listOf("홈플러스", "이마트", "쿠팡", "배달", "할인"),
            "의류/패션" to listOf("유니클로", "자라", "나이키", "아디다스", "겨울옷"),
            "생활/잡화" to listOf("다이소", "무인양품", "이케아", "청소", "수납"),
            "해외핫딜" to listOf("아마존", "알리익스프레스", "직구", "해외배송", "달러")
        )

        return categoryKeywords.values.shuffled().take(3).flatten().take(8)
    }

    // 실시간 트렌딩 키워드
    private fun getRealTimeTrendingKeywords(): List<String> {
        val baseKeywords = listOf(
            "무료배송", "50%할인", "타임세일", "오늘특가", "마감임박",
            "신상품", "베스트", "인기상품", "추천", "핫딜"
        )

        val seed = System.currentTimeMillis() / (1000 * 60 * 30) // 30분마다 변경
        return baseKeywords.shuffled(Random(seed)).take(6)
    }

    // 최종 트렌딩 키워드 조합
    fun getTrendingKeywords(): List<String> {
        val timeKeywords = getTimeBasedKeywords().take(3)
        val categoryKeywords = getCategoryTrendingKeywords().take(5)
        val trendingKeywords = getRealTimeTrendingKeywords().take(4)

        return (timeKeywords + categoryKeywords + trendingKeywords)
            .distinct()
            .shuffled()
            .take(12)
    }

    // 검색어 기반 연관 키워드
    fun getRelatedKeywords(searchQuery: String): List<String> {
        val relatedMap = mapOf(
            "갤럭시" to listOf("삼성", "안드로이드", "스마트폰", "케이스", "충전기"),
            "아이폰" to listOf("애플", "iOS", "에어팟", "맥북", "아이패드"),
            "노트북" to listOf("삼성", "LG", "맥북", "게이밍", "울트라북"),
            "마우스" to listOf("키보드", "모니터", "게이밍", "무선", "로지텍"),
            "커피" to listOf("원두", "드립", "캡슐", "머신", "카페"),
            "무료배송" to listOf("쿠팡", "당일배송", "로켓배송", "택배", "배송비")
        )

        return relatedMap[searchQuery.lowercase()]
            ?: relatedMap.keys.filter { it.contains(searchQuery, ignoreCase = true) }
                .flatMap { relatedMap[it] ?: emptyList() }
                .take(8)
    }
}

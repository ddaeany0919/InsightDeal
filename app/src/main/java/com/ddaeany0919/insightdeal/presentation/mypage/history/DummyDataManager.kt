package com.ddaeany0919.insightdeal.presentation.mypage.history

object DummyDataManager {
    data class DummyPost(
        val id: Int,
        val authorId: String,
        val authorName: String,
        val title: String,
        val content: String,
        val createdAt: String,
        val viewCount: Int,
        val commentCount: Int,
        val likes: Int,
        val dislikes: Int
    )

    val dummyPosts = listOf(
        DummyPost(
            id = 1001,
            authorId = "admin",
            authorName = "admin",
            title = "요즘 알리 할인율 어떤가요?",
            content = "알리익스프레스 세일 기간이라 이것저것 담아뒀는데 평소보다 싼건지 헷갈리네요. 다들 득템 하셨나요?",
            createdAt = "2시간 전",
            viewCount = 142,
            commentCount = 5,
            likes = 3,
            dislikes = 0
        ),
        DummyPost(
            id = 1002,
            authorId = "admin",
            authorName = "admin",
            title = "미니PC 핫딜 탑승했습니다!",
            content = "어제 올라온 미니PC 핫딜 보고 바로 구매했습니다. 이 가격이면 사무용으로 최고일 것 같네요.",
            createdAt = "1일 전",
            viewCount = 532,
            commentCount = 12,
            likes = 15,
            dislikes = 0
        )
    )

    data class DummyComment(
        val id: Int,
        val postTitle: String,
        val content: String,
        val createdAt: String
    )

    val dummyComments = listOf(
        DummyComment(
            id = 2001,
            postTitle = "이번 달 카드 실적 채우기 좋은 핫딜 있을까요?",
            content = "상품권 핫딜 올라오는거 기다렸다가 구매하시는 걸 추천드려요!",
            createdAt = "3시간 전"
        ),
        DummyComment(
            id = 2002,
            postTitle = "다이슨 청소기 V12 V15 고민입니다",
            content = "무게 중요하게 생각하시면 무조건 V12 추천합니다. V15는 좀 무겁더라고요.",
            createdAt = "2일 전"
        ),
        DummyComment(
            id = 2003,
            postTitle = "가성비 무선 이어폰 추천 부탁드립니다",
            content = "QCY 시리즈가 가성비는 최고인 것 같습니다. 최신 모델 한번 찾아보세요.",
            createdAt = "5일 전"
        )
    )
}

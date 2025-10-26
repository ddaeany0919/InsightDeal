package com.ddaeany0919.insightdeal

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import com.ddaeany0919.insightdeal.models.DealItem

class HomeViewModel : ViewModel() {

    private val _selectedCategory = MutableStateFlow("전체")
    val selectedCategory: StateFlow<String> = _selectedCategory.asStateFlow()

    private val _deals = MutableStateFlow<List<DealItem>>(emptyList())
    val deals: StateFlow<List<DealItem>> = _deals.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    // ✅ 1주차용 하드코딩 샘플 데이터
    private val allSampleDeals = listOf(
        DealItem(
            id = 1,
            title = "삼성 갤럭시 S24 Ultra 256GB 자급제 출시기념 특가!",
            price = 1290000,
            originalPrice = 1800000,
            discountRate = 28,
            imageUrl = "https://via.placeholder.com/400x225/FF6B35/FFFFFF?text=Galaxy+S24+Ultra",
            url = "https://ppomppu.co.kr/zboard/view.php?id=ppomppu&no=123456",
            siteName = "뽐뿌",
            category = "전자기기",
            viewCount = 2450,
            commentCount = 67,
            likeCount = 189,
            createdAt = "2025-10-26T16:30:00Z"
        ),
        DealItem(
            id = 2,
            title = "애플 에어팟 프로 2세대 USB-C 정품 최저가 이벤트",
            price = 245000,
            originalPrice = 350000,
            discountRate = 30,
            imageUrl = "https://via.placeholder.com/400x225/007AFF/FFFFFF?text=AirPods+Pro",
            url = "https://clien.net/service/board/jirum/123456",
            siteName = "클리앙",
            category = "전자기기",
            viewCount = 1890,
            commentCount = 45,
            likeCount = 234,
            createdAt = "2025-10-26T17:15:00Z"
        ),
        DealItem(
            id = 3,
            title = "지포스 RTX 4070 Ti SUPER 그래픽카드 역대급 최저가",
            price = 850000,
            originalPrice = 1200000,
            discountRate = 29,
            imageUrl = "https://via.placeholder.com/400x225/76B900/FFFFFF?text=RTX+4070+Ti",
            url = "https://quasarzone.com/bbs/qb_saleinfo/views/123456",
            siteName = "퀘이사존",
            category = "전자기기",
            viewCount = 3200,
            commentCount = 89,
            likeCount = 456,
            createdAt = "2025-10-26T18:00:00Z"
        ),
        DealItem(
            id = 4,
            title = "다이슨 V15 무선청소기 정품 + 추가 브러시 5개 증정",
            price = 450000,
            originalPrice = 699000,
            discountRate = 36,
            imageUrl = "https://via.placeholder.com/400x225/6C5CE7/FFFFFF?text=Dyson+V15",
            url = "https://bbasak.com/board/view/deal/123456",
            siteName = "빠삭",
            category = "생활용품",
            viewCount = 980,
            commentCount = 34,
            likeCount = 123,
            createdAt = "2025-10-26T19:00:00Z"
        ),
        DealItem(
            id = 5,
            title = "닌텐도 스위치 OLED + 젤다 티어스 오브 더 킹덤 번들",
            price = 380000,
            originalPrice = 450000,
            discountRate = 16,
            imageUrl = "https://via.placeholder.com/400x225/E74C3C/FFFFFF?text=Nintendo+Switch",
            url = "https://ruliweb.com/market/board/300143/read/123456",
            siteName = "루리웹",
            category = "게임",
            viewCount = 1450,
            commentCount = 56,
            likeCount = 234,
            createdAt = "2025-10-26T20:15:00Z"
        ),
        DealItem(
            id = 6,
            title = "아이패드 프로 11인치 M4 칩 128GB Wi-Fi 모델 특가",
            price = 1200000,
            originalPrice = 1500000,
            discountRate = 20,
            imageUrl = "https://via.placeholder.com/400x225/A855F7/FFFFFF?text=iPad+Pro+M4",
            url = "https://ppomppu.co.kr/zboard/view.php?id=ppomppu&no=789012",
            siteName = "뽐뿌",
            category = "전자기기",
            viewCount = 1890,
            commentCount = 43,
            likeCount = 167,
            createdAt = "2025-10-26T15:45:00Z"
        ),
        DealItem(
            id = 7,
            title = "LG 그램 17인치 노트북 i7 16GB 1TB 울트라북",
            price = 1650000,
            originalPrice = 2200000,
            discountRate = 25,
            imageUrl = "https://via.placeholder.com/400x225/00C73C/FFFFFF?text=LG+Gram+17",
            url = "https://clien.net/service/board/jirum/789012",
            siteName = "클리앙",
            category = "전자기기",
            viewCount = 2100,
            commentCount = 78,
            likeCount = 345,
            createdAt = "2025-10-26T14:20:00Z"
        ),
        DealItem(
            id = 8,
            title = "샤오미 공기청정기 4 프로 + 필터 2개 증정 세트",
            price = 320000,
            originalPrice = 480000,
            discountRate = 33,
            imageUrl = "https://via.placeholder.com/400x225/FF6900/FFFFFF?text=Xiaomi+Air+Purifier",
            url = "https://bbasak.com/board/view/deal/789012",
            siteName = "빠삭",
            category = "생활용품",
            viewCount = 1200,
            commentCount = 45,
            likeCount = 178,
            createdAt = "2025-10-26T13:10:00Z"
        )
    )

    init {
        loadDeals()
    }

    fun selectCategory(category: String) {
        _selectedCategory.value = category
        filterDealsByCategory(category)
    }

    private fun loadDeals() {
        viewModelScope.launch {
            _isLoading.value = true
            _deals.value = allSampleDeals
            _isLoading.value = false
        }
    }

    private fun filterDealsByCategory(category: String) {
        viewModelScope.launch {
            _deals.value = if (category == "전체") {
                allSampleDeals
            } else {
                allSampleDeals.filter { it.category == category }
            }
        }
    }

    fun refreshDeals() {
        loadDeals()
    }
}

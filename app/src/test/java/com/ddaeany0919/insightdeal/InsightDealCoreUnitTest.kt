package com.ddaeany0919.insightdeal

import com.ddaeany0919.insightdeal.models.ApiDeal
import com.ddaeany0919.insightdeal.models.DealItem
import com.ddaeany0919.insightdeal.models.DealSource
import com.ddaeany0919.insightdeal.presentation.auth.AuthManager
import com.ddaeany0919.insightdeal.presentation.decodeHtmlEntities
import com.ddaeany0919.insightdeal.presentation.formatEditableTargetInput
import com.ddaeany0919.insightdeal.presentation.formatPrice
import com.ddaeany0919.insightdeal.presentation.home.HomeViewModel
import com.ddaeany0919.insightdeal.presentation.isCentScaledCurrency
import com.ddaeany0919.insightdeal.presentation.parseBackendTimestampToKst
import com.ddaeany0919.insightdeal.presentation.parseEditableTargetInput
import com.ddaeany0919.insightdeal.presentation.resolveShopBadgeAndCleanTitle
import com.google.gson.Gson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneOffset

class InsightDealCoreUnitTest {

    @Test
    fun formatPrice_respectsExplicitKrwAndPreventsTitleHijack() {
        assertEquals(
            "78,000원",
            formatPrice(78000L, "KRW", "[11번가] 러닝화 직구 ($58)")
        )
    }

    @Test
    fun formatPrice_scalesAllPositiveSubunitCurrenciesCorrectly() {
        assertEquals("$0.99", formatPrice(99L, "USD", "앱스토어 할인"))
        assertEquals("$21", formatPrice(2100L, "USD", null))
        assertEquals("$21.50", formatPrice(2150L, "USD", null))
        assertEquals("€12.50", formatPrice(1250L, "EUR", null))
        assertEquals("¥189", formatPrice(18900L, "CNY", null))
        assertEquals("¥3,500", formatPrice(3500L, "JPY", null))
    }

    @Test
    fun formatPrice_handlesFreeDealsAndFreeShippingNoise() {
        assertEquals("무료", formatPrice(0L, "KRW", "스팀 무료 배포 게임"))
        assertEquals("무료", formatPrice(0L, "KRW", "에픽게임즈 일시무료 타이틀"))
        assertEquals("가격 확인", formatPrice(0L, "KRW", "쿠팡 무료배송 특가 모음전"))
        assertEquals("가격 확인", formatPrice(null, "KRW", "오늘의 브랜드 기획전"))
    }

    @Test
    fun decodeHtmlEntities_decodesNamedAndNumericEntities() {
        assertEquals(
            "앤커 & 유그린 '초고속' \"충전기\"",
            decodeHtmlEntities("앤커 &amp; 유그린 &#039;초고속&#039; &quot;충전기&quot;")
        )
    }

    @Test
    fun resolveShopBadgeAndCleanTitle_stripsDuplicateMallPrefixAndRejectsNonMallTags() {
        val normalizedGmarket = resolveShopBadgeAndCleanTitle(
            rawTitle = "[G마켓] 신라면 멀티팩 20봉 &amp; 사은품",
            shopName = "지마켓",
            ecommerceUrl = "https://item.gmarket.co.kr/Item?goodscode=123"
        )
        assertEquals("지마켓", normalizedGmarket.shopBadge)
        assertEquals("신라면 멀티팩 20봉 & 사은품", normalizedGmarket.cleanTitle)

        val rejectedNonMallBracket = resolveShopBadgeAndCleanTitle(
            rawTitle = "[특가] 미니 PC N100 베어본",
            shopName = null,
            ecommerceUrl = "https://ko.aliexpress.com/item/1005001.html"
        )
        assertEquals("알리익스프레스", rejectedNonMallBracket.shopBadge)
        assertEquals("[특가] 미니 PC N100 베어본", rejectedNonMallBracket.cleanTitle)

        val rejectedRestockTagWithoutUrl = resolveShopBadgeAndCleanTitle(
            rawTitle = "[재입고] 한정판 컨트롤러",
            shopName = "[기타]",
            ecommerceUrl = null
        )
        assertNull(rejectedRestockTagWithoutUrl.shopBadge)
        assertEquals("[재입고] 한정판 컨트롤러", rejectedRestockTagWithoutUrl.cleanTitle)
    }

    @Test
    fun targetPriceSubunitScaling_roundTripsCleanly() {
        assertTrue(isCentScaledCurrency("USD"))
        assertTrue(isCentScaledCurrency("EUR"))
        assertTrue(isCentScaledCurrency("CNY"))
        assertFalse(isCentScaledCurrency("KRW"))
        assertFalse(isCentScaledCurrency("JPY"))

        assertEquals("0.99", formatEditableTargetInput(99, isSubunitCurrency = true))
        assertEquals("58", formatEditableTargetInput(5800, isSubunitCurrency = true))
        assertEquals(99, parseEditableTargetInput("0.99", isSubunitCurrency = true))
        assertEquals(5800, parseEditableTargetInput("58", isSubunitCurrency = true))

        assertEquals("78000", formatEditableTargetInput(78000, isSubunitCurrency = false))
        assertEquals(78000, parseEditableTargetInput("78000", isSubunitCurrency = false))
    }

    @Test
    fun parseBackendTimestampToKst_interpretsNaiveIsoAsUtcAndConvertsToSeoul() {
        val parsed = parseBackendTimestampToKst("2026-05-27T06:30:00")
        assertNotNull(parsed)
        assertEquals(ZoneOffset.ofHours(9), parsed!!.offset)
        assertEquals(15, parsed.hour)
        assertEquals(30, parsed.minute)
    }

    @Test
    fun optionsDataDeserializer_parsesStringifiedAndArrayJsonAndPreservesZeroPriceOptions() {
        val gson = Gson()
        val stringifiedJson = """
            {
              "id": 501,
              "title": "[알리] 충전 케이블 모음",
              "price": 1900,
              "currency": "KRW",
              "site_name": "알리뽐뿌",
              "shop_name": "알리",
              "has_options": true,
              "options_count": 2,
              "options_data": "[{\"name\":\"1m 기본형\",\"price\":1900,\"url\":\"https://a.aliexpress.com/1\"},{\"name\":\"2m 고급형\",\"price\":0,\"url\":\"https://a.aliexpress.com/2\"}]",
              "sub_urls": ["https://a.aliexpress.com/sub1", "https://a.aliexpress.com/sub1"]
            }
        """.trimIndent()

        val apiDeal = gson.fromJson(stringifiedJson, ApiDeal::class.java)
        assertTrue(apiDeal.hasOptions)
        assertEquals(2, apiDeal.optionsData.size)

        val item: DealItem = gson.fromJson(stringifiedJson, DealItem::class.java)

        assertTrue(item.hasMultipleOptions)
        assertEquals(2, item.optionsData.size)
        assertEquals(2, item.resolvedOptionCount)
        assertEquals(0L, item.optionsData[1].price)
        assertEquals("가격 확인", formatPrice(item.optionsData[1].price, item.currency, item.optionsData[1].name))
        assertEquals(listOf("https://a.aliexpress.com/sub1"), item.resolvedSubUrls)

        val sourceBasedDeal = DealItem(
            id = 502,
            title = "멀티 소스 옵션 상품",
            price = 0L,
            currency = "KRW",
            siteName = "뽐뿌",
            sources = listOf(
                DealSource(siteName = "뽐뿌 - 옵션A", postUrl = "https://ppomppu.co.kr/1", price = 15000L),
                DealSource(siteName = "뽐뿌 - 옵션B", postUrl = "https://ppomppu.co.kr/1", price = 0L)
            )
        )
        assertTrue(sourceBasedDeal.hasMultipleOptions)
        assertEquals(2, sourceBasedDeal.resolvedOptionCount)
    }

    @Test
    fun authManager_passwordHashingVerifiesAndRejectsTampering() {
        val saltHex = "00112233445566778899aabbccddeeff"
        val hash = AuthManager.hashPassword("insight_user", "S3cureP@ss!", saltHex)
        assertTrue(AuthManager.verifyPasswordHash("insight_user", "S3cureP@ss!", saltHex, hash))
        assertFalse(AuthManager.verifyPasswordHash("insight_user", "wrong_password", saltHex, hash))
        assertFalse(AuthManager.verifyPasswordHash("admin", "admin", saltHex, hash))
    }

    @Test
    fun homeFilterState_resolvesPlatformQueryProperly() {
        val chipSelected = HomeViewModel.FilterState(communityChip = "퀘이사존")
        assertEquals("퀘이사존", chipSelected.resolvedPlatformQuery())

        val defaultAll = HomeViewModel.FilterState(communityChip = "전체")
        assertNull(defaultAll.resolvedPlatformQuery())
    }
}

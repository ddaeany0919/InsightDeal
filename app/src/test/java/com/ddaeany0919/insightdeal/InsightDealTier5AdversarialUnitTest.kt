package com.ddaeany0919.insightdeal

import androidx.compose.runtime.saveable.SaverScope
import com.ddaeany0919.insightdeal.models.DealItem
import com.ddaeany0919.insightdeal.presentation.home.HOME_STRING_SET_SAVER
import com.ddaeany0919.insightdeal.presentation.home.HomeEmptyRecoveryAction
import com.ddaeany0919.insightdeal.presentation.home.countVisibleHomeDeals
import com.ddaeany0919.insightdeal.presentation.home.resolveHomeEmptyFeedUiModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Tier 5 적대적 결함(A-GAP-01 ~ A-GAP-03, A-NOTE-01) 해소 및 회귀 방어 단위 테스트
 */
class InsightDealTier5AdversarialUnitTest {

    private fun resolveAppJavaRoot(): File {
        return listOf(File("src/main/java"), File("app/src/main/java"))
            .first { it.exists() }
    }

    private fun sampleDeal(
        id: Int,
        title: String = "테스트 특가 상품 $id",
        shopName: String? = "쿠팡",
        tags: List<String> = emptyList()
    ): DealItem = DealItem(
        id = id,
        title = title,
        price = 29_900L,
        siteName = "펨코",
        shopName = shopName,
        tags = tags
    )

    /**
     * T5-A-01: 공용 DealCardComposable(isExpandedGrid = false)을 호출하는 타 화면 5곳에서
     * LazyColumn 이중 좌우 16.dp 패딩 및 비-0 verticalArrangement 간격이 제거되었는지 검증합니다.
     */
    @Test
    fun t5_a_01_sharedDealCardScreens_haveNoDoubleHorizontalPaddingOrNonZeroSpacing() {
        val javaRoot = resolveAppJavaRoot()
        val targetScreens = listOf(
            "com/ddaeany0919/insightdeal/presentation/mypage/history/RecentDealsScreen.kt",
            "com/ddaeany0919/insightdeal/presentation/platform/PlatformScreen.kt",
            "com/ddaeany0919/insightdeal/presentation/category/CategoryDetailScreen.kt",
            "com/ddaeany0919/insightdeal/presentation/recommendation/RecommendationScreen.kt",
            "com/ddaeany0919/insightdeal/presentation/search/AdvancedSearchScreen.kt"
        )

        val lazyColumnHeaderRegex = Regex("""LazyColumn\s*\((.*?)\)\s*\{""", setOf(RegexOption.DOT_MATCHES_ALL))

        targetScreens.forEach { relPath ->
            val source = File(javaRoot, relPath).readText()
            assertTrue("$relPath 에 DealCardComposable 호출이 있어야 합니다.", source.contains("DealCardComposable("))

            val match = lazyColumnHeaderRegex.findAll(source)
                .firstOrNull { block ->
                    // AdvancedSearchScreen 등 다수 LazyColumn 중 DealCardComposable을 포함하는 목록 영역을 찾습니다.
                    val startIdx = block.range.last
                    val tail = source.substring(startIdx)
                    tail.contains("DealCardComposable(")
                }
            assertNotNull("$relPath 에서 DealCardComposable을 감싸는 LazyColumn을 찾지 못했습니다.", match)

            val lazyParams = match!!.groupValues[1]
            assertFalse(
                "$relPath LazyColumn에 PaddingValues(16.dp) 단일 이중 패딩이 남아 있습니다.",
                lazyParams.contains("PaddingValues(16.dp)")
            )
            assertFalse(
                "$relPath LazyColumn에 start = 16.dp 이중 좌측 패딩이 남아 있습니다.",
                lazyParams.contains("start = 16.dp")
            )
            assertFalse(
                "$relPath LazyColumn에 end = 16.dp 이중 우측 패딩이 남아 있습니다.",
                lazyParams.contains("end = 16.dp")
            )
            assertFalse(
                "$relPath LazyColumn에 horizontal = 16.dp 이중 좌우 패딩이 남아 있습니다.",
                lazyParams.contains("horizontal = 16.dp")
            )
            assertTrue(
                "$relPath LazyColumn은 Arrangement.spacedBy(0.dp) 풀블리드 헤어라인 간격을 사용해야 합니다.",
                lazyParams.contains("Arrangement.spacedBy(0.dp)")
            )
        }

        // RecommendationScreen 및 AdvancedSearchScreen 내 비-카드 요소는 개별 16.dp 좌우 여백이 보존되어야 합니다.
        val recSrc = File(javaRoot, "com/ddaeany0919/insightdeal/presentation/recommendation/RecommendationScreen.kt").readText()
        assertTrue(recSrc.contains(".padding(horizontal = 16.dp)"))
        val searchSrc = File(javaRoot, "com/ddaeany0919/insightdeal/presentation/search/AdvancedSearchScreen.kt").readText()
        assertTrue(searchSrc.contains("modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)"))

        // A-NOTE-01: CompactDealCardComposable 320dp + fontScale 1.3x 폭 확보 및 줄바꿈 방어 검증
        val homeSrc = File(javaRoot, "com/ddaeany0919/insightdeal/presentation/home/HomeScreen.kt").readText()
        val compactSection = homeSrc.substringAfter("fun CompactDealCardComposable(")
            .substringBefore("private fun MutedKeywordsBottomSheet(")
        assertTrue(compactSection.contains("Arrangement.spacedBy(4.dp)"))
        assertTrue(compactSection.contains("if (priceText.length >= 9) 12.5.sp else 14.sp"))
        assertTrue(compactSection.contains("weight(1f, fill = false)"))
        assertTrue(compactSection.contains("softWrap = false"))
    }

    /**
     * T5-A-02: 로드된 딜 전량이 hiddenDeals 또는 mutedKeywords에 의해 숨겨졌을 때
     * visible item count = 0을 정확히 판별하고 적절한 빈 안내 및 복구 CTA를 반환하는지 검증합니다.
     */
    @Test
    fun t5_a_02_hiddenDealsAndMutedKeywords_allHiddenDetectsZeroVisibleAndProvidesRecoveryCta() {
        val loaded = listOf(
            sampleDeal(id = 101, title = "노트북 특가"),
            sampleDeal(id = 102, title = "모니터 특가"),
            sampleDeal(id = 103, title = "키보드 특가")
        )

        // 1) 전량 hiddenDeals로 숨겨진 경우 (totalItemCount > 0 이어도 공백 화면 금지)
        val hiddenAll = setOf("101", "102", "103")
        assertEquals(0, countVisibleHomeDeals(loaded, hiddenAll, emptySet()))
        val hiddenUi = resolveHomeEmptyFeedUiModel(
            loadedDeals = loaded,
            totalItemCount = loaded.size,
            hiddenDealIds = hiddenAll,
            mutedKeywords = emptySet(),
            hasActiveFilter = false
        )
        assertTrue(hiddenUi.isEmpty)
        assertEquals("숨긴 핫딜로 인해 표시할 항목이 없어요", hiddenUi.title)
        assertEquals("숨김 해제", hiddenUi.primaryActionLabel)
        assertEquals(HomeEmptyRecoveryAction.RESTORE_HIDDEN_DEALS, hiddenUi.primaryAction)
        assertNull(hiddenUi.secondaryActionLabel)

        // 2) 활성 필터 + 전량 숨김인 경우 "숨김 해제"와 "필터 초기화" CTA 모두 노출
        val hiddenWithFilterUi = resolveHomeEmptyFeedUiModel(
            loadedDeals = loaded,
            totalItemCount = loaded.size,
            hiddenDealIds = hiddenAll,
            mutedKeywords = emptySet(),
            hasActiveFilter = true
        )
        assertTrue(hiddenWithFilterUi.isEmpty)
        assertEquals(HomeEmptyRecoveryAction.RESTORE_HIDDEN_DEALS, hiddenWithFilterUi.primaryAction)
        assertEquals("필터 초기화", hiddenWithFilterUi.secondaryActionLabel)
        assertEquals(HomeEmptyRecoveryAction.RESET_FILTERS, hiddenWithFilterUi.secondaryAction)

        // 3) 일부만 숨겨진 경우(visibleCount > 0) 빈 상태 카드 미노출
        val hiddenPartial = setOf("101", "102")
        assertEquals(1, countVisibleHomeDeals(loaded, hiddenPartial, emptySet()))
        val partialUi = resolveHomeEmptyFeedUiModel(
            loadedDeals = loaded,
            totalItemCount = loaded.size,
            hiddenDealIds = hiddenPartial,
            mutedKeywords = emptySet(),
            hasActiveFilter = false
        )
        assertFalse(partialUi.isEmpty)

        // 4) 로드된 딜이 뮤트 키워드로 전량 가려진 경우(Paging 필터 전/후 모두 대응)
        assertEquals(0, countVisibleHomeDeals(loaded, emptySet(), setOf("특가")))
        val mutedPagingZeroUi = resolveHomeEmptyFeedUiModel(
            loadedDeals = emptyList(),
            totalItemCount = 0,
            hiddenDealIds = emptySet(),
            mutedKeywords = setOf("특가"),
            hasActiveFilter = false
        )
        assertTrue(mutedPagingZeroUi.isEmpty)
        assertEquals("조건에 맞는 핫딜이 없어요", mutedPagingZeroUi.title)
        assertEquals("숨김 · 뮤트 관리", mutedPagingZeroUi.primaryActionLabel)
        assertEquals(HomeEmptyRecoveryAction.OPEN_MUTED_MANAGER, mutedPagingZeroUi.primaryAction)

        // 5) 일반 필터로 0건인 경우 "필터 초기화" 노출
        val activeFilterZeroUi = resolveHomeEmptyFeedUiModel(
            loadedDeals = emptyList(),
            totalItemCount = 0,
            hiddenDealIds = emptySet(),
            mutedKeywords = emptySet(),
            hasActiveFilter = true
        )
        assertTrue(activeFilterZeroUi.isEmpty)
        assertEquals("조건에 맞는 핫딜이 없어요", activeFilterZeroUi.title)
        assertEquals("필터 초기화", activeFilterZeroUi.primaryActionLabel)
        assertEquals(HomeEmptyRecoveryAction.RESET_FILTERS, activeFilterZeroUi.primaryAction)

        // 6) 순수 빈 피드인 경우
        val pureEmptyUi = resolveHomeEmptyFeedUiModel(
            loadedDeals = emptyList(),
            totalItemCount = 0,
            hiddenDealIds = emptySet(),
            mutedKeywords = emptySet(),
            hasActiveFilter = false
        )
        assertTrue(pureEmptyUi.isEmpty)
        assertEquals("아직 등록된 핫딜이 없어요", pureEmptyUi.title)
        assertEquals(HomeEmptyRecoveryAction.NONE, pureEmptyUi.primaryAction)

        // HomeScreen.kt 실제 UI 배선 계약 확인
        val javaRoot = resolveAppJavaRoot()
        val homeSrc = File(javaRoot, "com/ddaeany0919/insightdeal/presentation/home/HomeScreen.kt").readText()
        assertTrue(homeSrc.contains("resolveHomeEmptyFeedUiModel("))
        assertTrue(homeSrc.contains("HomeEmptyRecoveryAction.RESTORE_HIDDEN_DEALS -> restoreHiddenDeals()"))
        assertTrue(homeSrc.contains("HomeEmptyRecoveryAction.RESET_FILTERS ->"))
        assertTrue(homeSrc.contains("if (!emptyFeedUi.isEmpty)"))
    }

    /**
     * T5-A-03: HomeScreen 시트/팝오버 열림 상태 및 HomeFilterBottomSheet Draft 상태가
     * rememberSaveable 및 Bundle 안전 Saver를 통해 회전 시 보존되는지 검증합니다.
     */
    @Test
    fun t5_a_03_homeSheetAndFilterDraftStates_useRememberSaveableAndSafeSetSaver() {
        // 1) HOME_STRING_SET_SAVER 실제 저장/복원 라운드트립 검증
        val allowAllScope = SaverScope { true }
        val communities = linkedSetOf("펨코", "뽐뿌", "퀘이사존")
        val savedList = with(HOME_STRING_SET_SAVER) { allowAllScope.save(communities) }
        assertNotNull(savedList)
        val restoredSet = HOME_STRING_SET_SAVER.restore(savedList!!)
        assertEquals(communities, restoredSet)

        val emptySaved = with(HOME_STRING_SET_SAVER) { allowAllScope.save(emptySet()) }
        assertEquals(emptySet<String>(), HOME_STRING_SET_SAVER.restore(emptySaved!!))

        // 2) HomeScreen.kt 시트/팝오버/Draft rememberSaveable 계약 검증
        val javaRoot = resolveAppJavaRoot()
        val homeSrc = File(javaRoot, "com/ddaeany0919/insightdeal/presentation/home/HomeScreen.kt").readText()

        val homeStateSection = homeSrc.substringAfter("fun HomeScreen(")
            .substringBefore("BackHandler(enabled = showSearchPopover)")
        listOf(
            "searchInput by rememberSaveable",
            "showSearchPopover by rememberSaveable",
            "showMoreMenu by rememberSaveable",
            "showFilterBottomSheet by rememberSaveable",
            "showDeviceSyncSheet by rememberSaveable"
        ).forEach { expected ->
            assertTrue("HomeScreen 상태 '$expected'는 rememberSaveable을 사용해야 합니다.", homeStateSection.contains(expected))
        }

        val filterSheetSection = homeSrc.substringAfter("private fun HomeFilterBottomSheet(")
            .substringBefore("ModalBottomSheet(")
        listOf(
            "draftCommunities by rememberSaveable(stateSaver = HOME_STRING_SET_SAVER)",
            "draftShop by rememberSaveable",
            "draftMinPriceText by rememberSaveable",
            "draftMaxPriceText by rememberSaveable",
            "draftFreeShipping by rememberSaveable",
            "draftHideClosed by rememberSaveable",
            "draftIncludeClosed by rememberSaveable",
            "draftViewMode by rememberSaveable"
        ).forEach { expected ->
            assertTrue("HomeFilterBottomSheet Draft '$expected'는 rememberSaveable을 사용해야 합니다.", filterSheetSection.contains(expected))
        }
        assertFalse(filterSheetSection.contains("remember(filterState)"))
        assertFalse(filterSheetSection.contains("remember(viewMode)"))
    }
}

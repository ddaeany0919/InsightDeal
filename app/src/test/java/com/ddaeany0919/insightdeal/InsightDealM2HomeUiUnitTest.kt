package com.ddaeany0919.insightdeal

import android.content.SharedPreferences
import androidx.paging.DifferCallback
import androidx.paging.NullPaddedList
import androidx.paging.PagingDataDiffer
import androidx.paging.PagingSource
import androidx.paging.PagingState
import com.ddaeany0919.insightdeal.data.FeedPreferencesDataSource
import com.ddaeany0919.insightdeal.local.db.DealDao
import com.ddaeany0919.insightdeal.local.db.DealEntity
import com.ddaeany0919.insightdeal.local.db.DealRemoteKeysDao
import com.ddaeany0919.insightdeal.local.db.DealRemoteKeysEntity
import com.ddaeany0919.insightdeal.models.DealItem
import com.ddaeany0919.insightdeal.network.ApiService
import com.ddaeany0919.insightdeal.presentation.home.HomeViewModel
import com.ddaeany0919.insightdeal.presentation.home.HotDealsRemoteMediator
import com.ddaeany0919.insightdeal.presentation.home.countActiveHomeSheetFilters
import com.ddaeany0919.insightdeal.presentation.home.hasSecondaryHomeFilter
import com.ddaeany0919.insightdeal.presentation.home.resolveHomeSkeletonCount
import com.ddaeany0919.insightdeal.presentation.home.resolveStudioThumbnailColorFilter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.io.BufferedInputStream
import java.io.Closeable
import java.io.File
import java.lang.reflect.Proxy
import java.net.InetAddress
import java.net.ServerSocket
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import java.util.concurrent.CopyOnWriteArrayList

/**
 * M2 마일스톤(F8~F11) Android 홈 UI/UX 개편 및 회귀 검증 단위 테스트
 */
class InsightDealM2HomeUiUnitTest {

    private data class RecordedHttpRequest(
        val method: String,
        val path: String
    )

    private class LocalStubHttpServer(
        private val handler: (RecordedHttpRequest) -> Pair<Int, String>
    ) : Closeable {
        private val serverSocket = ServerSocket(0, 50, InetAddress.getByName("127.0.0.1"))
        val recordedRequests = CopyOnWriteArrayList<RecordedHttpRequest>()

        @Volatile
        private var running = true

        val baseUrl: String
            get() = "http://127.0.0.1:${serverSocket.localPort}/"

        private val worker = Thread {
            while (running) {
                try {
                    val socket = serverSocket.accept()
                    socket.use { client ->
                        val input = BufferedInputStream(client.getInputStream())
                        val req = readHttpRequest(input) ?: return@use
                        recordedRequests.add(req)
                        val (status, jsonBody) = handler(req)
                        val payload = jsonBody.toByteArray(StandardCharsets.UTF_8)
                        val statusText = if (status == 200) "OK" else "Error"
                        val header = buildString {
                            append("HTTP/1.1 $status $statusText\r\n")
                            append("Content-Type: application/json; charset=utf-8\r\n")
                            append("Content-Length: ${payload.size}\r\n")
                            append("Connection: close\r\n\r\n")
                        }.toByteArray(StandardCharsets.US_ASCII)
                        val out = client.getOutputStream()
                        out.write(header)
                        out.write(payload)
                        out.flush()
                    }
                } catch (_: Exception) {
                    if (!running) break
                }
            }
        }.apply {
            isDaemon = true
            start()
        }

        private fun readHttpRequest(input: BufferedInputStream): RecordedHttpRequest? {
            val headerBytes = ArrayList<Byte>()
            var matched = 0
            while (true) {
                val b = input.read()
                if (b == -1) return null
                headerBytes.add(b.toByte())
                matched = when {
                    matched == 0 && b == '\r'.code -> 1
                    matched == 1 && b == '\n'.code -> 2
                    matched == 2 && b == '\r'.code -> 3
                    matched == 3 && b == '\n'.code -> 4
                    b == '\r'.code -> 1
                    else -> 0
                }
                if (matched == 4) break
            }
            val headerText = String(headerBytes.toByteArray(), StandardCharsets.US_ASCII)
            val lines = headerText.split("\r\n").filter { it.isNotEmpty() }
            if (lines.isEmpty()) return null
            val parts = lines.first().split(" ")
            return RecordedHttpRequest(
                method = parts.getOrElse(0) { "GET" },
                path = parts.getOrElse(1) { "/" }
            )
        }

        override fun close() {
            running = false
            runCatching { serverSocket.close() }
            runCatching { worker.join(500) }
        }
    }

    private class InMemoryDealDao : DealDao {
        private val items = linkedMapOf<Int, DealEntity>()
        private val invalidationTrackers = CopyOnWriteArrayList<PagingSource<Int, DealEntity>>()

        override suspend fun insertAll(deals: List<DealEntity>) {
            deals.forEach { items[it.id] = it }
            invalidationTrackers.forEach { it.invalidate() }
        }
        override fun getPagedDeals(
            category: String?,
            keyword: String?,
            platform: String?,
            sort: String,
            freeShippingOnly: Boolean,
            shop: String?,
            minPrice: Int?,
            maxPrice: Int?
        ): PagingSource<Int, DealEntity> {
            val snapshot = items.values.sortedByDescending { it.id }
            return object : PagingSource<Int, DealEntity>() {
                override fun getRefreshKey(state: PagingState<Int, DealEntity>): Int? = null
                override suspend fun load(params: LoadParams<Int>): LoadResult<Int, DealEntity> {
                    return LoadResult.Page(data = snapshot, prevKey = null, nextKey = null)
                }
            }.also { invalidationTrackers += it }
        }
        override suspend fun updateVoteCounts(dealId: Int, buyCount: Int, passCount: Int) {}
        override suspend fun updateAppCommentCount(dealId: Int, appCommentCount: Int) {}
        override suspend fun clearAllDeals() {
            items.clear()
            invalidationTrackers.forEach { it.invalidate() }
        }
        override suspend fun getDealCount(): Int = items.size
    }

    private class InMemoryDealRemoteKeysDao : DealRemoteKeysDao {
        private val keys = linkedMapOf<Int, DealRemoteKeysEntity>()
        override suspend fun insertAll(remoteKey: List<DealRemoteKeysEntity>) {
            remoteKey.forEach { keys[it.dealId] = it }
        }
        override suspend fun getRemoteKeysForDealId(dealId: Int): DealRemoteKeysEntity? = keys[dealId]
        override suspend fun clearRemoteKeys() { keys.clear() }
    }

    private val scopesToCancel = mutableListOf<CoroutineScope>()
    private val closeables = mutableListOf<Closeable>()

    @After
    fun tearDown() {
        scopesToCancel.forEach { it.cancel() }
        scopesToCancel.clear()
        closeables.forEach { runCatching { it.close() } }
        closeables.clear()
    }

    private fun resolveAppJavaRoot(): File {
        return listOf(File("src/main/java"), File("app/src/main/java"))
            .first { it.exists() }
    }

    private fun createInMemoryPrefs(): SharedPreferences {
        val stringStore = linkedMapOf<String, String?>()
        val editor = Proxy.newProxyInstance(
            SharedPreferences.Editor::class.java.classLoader,
            arrayOf(SharedPreferences.Editor::class.java)
        ) { proxy, method, args ->
            when (method.name) {
                "putString" -> {
                    stringStore[args[0] as String] = args[1] as String?
                    proxy
                }
                "apply", "commit" -> if (method.returnType == Boolean::class.javaPrimitiveType) true else Unit
                else -> proxy
            }
        } as SharedPreferences.Editor
        return Proxy.newProxyInstance(
            SharedPreferences::class.java.classLoader,
            arrayOf(SharedPreferences::class.java)
        ) { _, method, args ->
            when (method.name) {
                "getString" -> stringStore[args[0] as String] ?: (args[1] as String?)
                "edit" -> editor
                else -> null
            }
        } as SharedPreferences
    }

    private fun createJvmPagingDiffer(): PagingDataDiffer<DealItem> {
        val callback = object : DifferCallback {
            override fun onChanged(position: Int, count: Int) {}
            override fun onInserted(position: Int, count: Int) {}
            override fun onRemoved(position: Int, count: Int) {}
        }
        return object : PagingDataDiffer<DealItem>(callback, Dispatchers.Unconfined) {
            override suspend fun presentNewList(
                previousList: NullPaddedList<DealItem>,
                newList: NullPaddedList<DealItem>,
                lastAccessedIndex: Int,
                onListPresentable: () -> Unit
            ): Int? {
                onListPresentable()
                return null
            }
        }
    }

    @Test
    fun resolveStudioThumbnailColorFilter_appliesMultiplyOnlyInLightOpenDeals() {
        val lightOpen = resolveStudioThumbnailColorFilter(isDark = false, isClosed = false)
        val darkOpen = resolveStudioThumbnailColorFilter(isDark = true, isClosed = false)
        val lightClosed = resolveStudioThumbnailColorFilter(isDark = false, isClosed = true)
        val darkClosed = resolveStudioThumbnailColorFilter(isDark = true, isClosed = true)

        assertNotNull("라이트·진행중 딜에는 스튜디오 Multiply ColorFilter가 적용되어야 합니다.", lightOpen)
        assertNull("다크/AMOLED 모드 진행중 딜에는 Multiply ColorFilter를 적용하면 안 됩니다.", darkOpen)
        assertNotNull("종료 딜에는 흑백 ColorFilter가 적용되어야 합니다.", lightClosed)
        assertNotNull("다크 모드 종료 딜에도 흑백 ColorFilter가 적용되어야 합니다.", darkClosed)
        assertEquals("라이트/다크 종료 딜은 동일한 흑백 필터를 사용해야 합니다.", lightClosed, darkClosed)
        assertNotEquals("진행중 Multiply 필터와 종료 흑백 필터는 서로 달라야 합니다.", lightOpen, lightClosed)
    }

    @Test
    fun resolveHomeSkeletonCount_matchesDenseMobileAndCompactTargets() {
        assertEquals(6, resolveHomeSkeletonCount(isExpandedScreen = false, isCompactMode = false))
        assertEquals(6, resolveHomeSkeletonCount(isExpandedScreen = true, isCompactMode = false))
        assertEquals(8, resolveHomeSkeletonCount(isExpandedScreen = false, isCompactMode = true))
        assertEquals(8, resolveHomeSkeletonCount(isExpandedScreen = true, isCompactMode = true))
    }

    @Test
    fun filterHelperFunctions_distinguishCategoryTabFromSecondarySheetFilters() {
        val defaultState = HomeViewModel.FilterState()
        assertFalse(hasSecondaryHomeFilter(defaultState))
        assertEquals(0, countActiveHomeSheetFilters(defaultState, isCompactMode = false))

        val categoryOnlyState = defaultState.copy(
            category = "PC용품",
            selectedCategories = setOf("PC용품")
        )
        assertFalse(
            "카테고리 단독 선택 시에는 보조 적용 필터 칩 바가 노출되지 않아야 합니다.",
            hasSecondaryHomeFilter(categoryOnlyState)
        )
        assertEquals(0, countActiveHomeSheetFilters(categoryOnlyState, isCompactMode = false))

        val multiSheetState = categoryOnlyState.copy(
            selectedCommunityChips = setOf("펨코"),
            communityChip = "펨코",
            shop = "무신사",
            maxPrice = 30_000,
            freeShippingOnly = true,
            includeClosed = true
        )
        assertTrue(hasSecondaryHomeFilter(multiSheetState))
        assertEquals(6, countActiveHomeSheetFilters(multiSheetState, isCompactMode = true))
    }

    @Test
    fun homeViewModel_includeClosedAndHideClosedAreMutuallyExclusiveAndWiredToApi() = runBlocking {
        HotDealsRemoteMediator.clearRefreshCache()
        val server = LocalStubHttpServer {
            200 to """{"deals":[{"id":2001,"title":"종료 포함 테스트 상품","price":12000,"currency":"KRW","site_name":"펨코","is_closed":true}],"has_more":false}"""
        }.also { closeables += it }

        val api = Retrofit.Builder()
            .baseUrl(server.baseUrl)
            .client(OkHttpClient.Builder().build())
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(ApiService::class.java)
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined).also { scopesToCancel += it }
        val prefs = createInMemoryPrefs()
        val vm = HomeViewModel(
            apiService = api,
            coroutineScopeOverride = scope,
            autoInitialize = false,
            feedPreferencesDataSource = FeedPreferencesDataSource(prefsProvider = { prefs }),
            dealDaoProvider = { InMemoryDealDao() },
            dealRemoteKeysDaoProvider = { InMemoryDealRemoteKeysDao() },
            runInTransactionProvider = { block -> block() }
        )

        vm.toggleHideClosed()
        assertTrue(vm.filterState.value.hideClosed)
        assertFalse(vm.filterState.value.includeClosed)

        vm.toggleIncludeClosed()
        assertTrue(vm.filterState.value.includeClosed)
        assertFalse("종료 포함 활성화 시 품절 제외는 자동 해제되어야 합니다.", vm.filterState.value.hideClosed)

        val differ = createJvmPagingDiffer()
        val collectJob = scope.launch {
            vm.dealsPagingData.collectLatest { differ.collectFrom(it) }
        }
        withTimeout(5000L) {
            while (differ.snapshot().items.map { it.id } != listOf(2001) || server.recordedRequests.isEmpty()) {
                delay(20L)
            }
        }
        val decodedPath = URLDecoder.decode(
            server.recordedRequests.last { it.path.startsWith("/api/community/hot-deals") }.path,
            StandardCharsets.UTF_8.name()
        )
        assertTrue("API 요청에 include_closed=true가 포함되어야 합니다: $decodedPath", decodedPath.contains("include_closed=true"))

        vm.toggleHideClosed()
        assertTrue(vm.filterState.value.hideClosed)
        assertFalse("품절 제외 활성화 시 종료 포함은 자동 해제되어야 합니다.", vm.filterState.value.includeClosed)
        withTimeout(5000L) {
            while (differ.snapshot().items.isNotEmpty()) {
                delay(20L)
            }
        }
        assertTrue(
            "품절 제외 활성화 시 종료 딜(id=2001)은 페이징 스냅샷에서 실제로 제외되어야 합니다.",
            differ.snapshot().items.isEmpty()
        )

        vm.resetHomeFilters()
        assertFalse(vm.filterState.value.hideClosed)
        assertFalse(vm.filterState.value.includeClosed)
        collectJob.cancel()
    }

    @Test
    fun homeScreenAndSupportingFiles_satisfyF8ToF10StructuralAndCompatibilityContracts() {
        val javaRoot = resolveAppJavaRoot()
        val homeSrc = File(javaRoot, "com/ddaeany0919/insightdeal/presentation/home/HomeScreen.kt").readText()
        val myPageSrc = File(javaRoot, "com/ddaeany0919/insightdeal/presentation/mypage/MyPageScreen.kt").readText()
        val dealDaoSrc = File(javaRoot, "com/ddaeany0919/insightdeal/local/db/DealDao.kt").readText()
        val recentScreenSrc = File(javaRoot, "com/ddaeany0919/insightdeal/presentation/mypage/history/RecentDealsScreen.kt").readText()

        // 1) F8 상단 2줄 헤더 + 검색 팝오버 + 더보기 메뉴 + 밑줄 탭
        assertFalse("기존 HomeTrendingTickerBar 컴포저블이 제거되어야 합니다.", homeSrc.contains("fun HomeTrendingTickerBar("))
        assertTrue(homeSrc.contains("PopupProperties(focusable = false)"))
        assertTrue(homeSrc.contains("SearchAutoComplete.getInstance(context)"))
        assertTrue(homeSrc.contains("키워드 알림 설정"))
        assertTrue(homeSrc.contains("기기 동기화"))
        assertTrue(homeSrc.contains("테마 전환"))
        assertTrue(homeSrc.contains("앱 환경설정"))
        assertTrue(homeSrc.contains("DeviceSyncBottomSheet("))
        assertTrue(homeSrc.contains("BrandEmeraldPoint"))
        assertTrue(homeSrc.contains("상세 필터"))

        // 2) F9 상세 필터 바텀시트 + 18종 쇼핑몰 + RecentDealsTray + 피드 헤더 + 다크 캡슐
        assertTrue(homeSrc.contains("private fun HomeFilterBottomSheet("))
        assertTrue(homeSrc.contains("\"G마켓\" to \"지마켓\""))
        assertTrue(homeSrc.contains("\"알리\" to \"알리익스프레스\""))
        assertTrue(homeSrc.contains("\"무신사\" to \"무신사\""))
        assertTrue(homeSrc.contains("\"스팀\" to \"스팀\""))
        assertTrue(homeSrc.contains("\"에픽게임즈\" to \"에픽게임즈\""))
        assertTrue(homeSrc.contains("private fun HomeRecentDealsTray("))
        assertTrue(homeSrc.contains("RecentDealManager.recentDeals"))
        assertTrue(homeSrc.contains("실시간 핫딜"))
        assertTrue(homeSrc.contains("Color(0xFF191F28)"))
        assertTrue(homeSrc.contains("방금 새 핫딜"))

        // 3) F10 DealCardComposable 고밀도·줄바꿈 0건·FlowRow 0건·2열 칼정렬·스튜디오 톤·스켈레톤 패리티
        val dealCardSection = homeSrc.substringAfter("fun DealCardComposable(")
            .substringBefore("fun KeywordSettingsBottomSheet(")
        assertFalse("DealCardComposable 내부에 FlowRow가 남아 있으면 안 됩니다.", dealCardSection.contains("FlowRow"))
        assertTrue(dealCardSection.contains("minLines = if (isExpandedGrid) 2 else 1"))
        assertTrue(dealCardSection.contains("softWrap = false"))
        assertTrue(dealCardSection.contains("maxLines = 1"))
        assertTrue(dealCardSection.contains("Modifier.size(88.dp)"))
        assertTrue(dealCardSection.contains("HorizontalDivider(thickness = 0.75.dp"))
        assertTrue(dealCardSection.contains("resolveStudioThumbnailColorFilter(isDark = isDark, isClosed = deal.isClosed)"))

        val compactSection = homeSrc.substringAfter("fun CompactDealCardComposable(")
            .substringBefore("private fun MutedKeywordsBottomSheet(")
        assertTrue(compactSection.contains("softWrap = false"))
        assertTrue(compactSection.contains("resolveStudioThumbnailColorFilter(isDark = isDark, isClosed = deal.isClosed)"))

        assertTrue(homeSrc.contains("STUDIO_THUMB_LIGHT_BG = Color(0xFFF7F8FA)"))
        assertTrue(homeSrc.contains("BlendMode.Multiply"))
        assertTrue(homeSrc.contains("resolveHomeSkeletonCount("))

        // 4) MyPageScreen DeviceSyncBottomSheet 공용화 & DealDao 쇼핑몰 3종 SQL 매칭
        assertTrue(myPageSrc.contains("fun DeviceSyncBottomSheet("))
        assertFalse(myPageSrc.contains("private fun DeviceSyncBottomSheet("))
        assertTrue(dealDaoSrc.contains("TRIM(:shop) = '무신사'"))
        assertTrue(dealDaoSrc.contains("LOWER(TRIM(:shop)) IN ('스팀', 'steam')"))
        assertTrue(dealDaoSrc.contains("LOWER(TRIM(:shop)) IN ('에픽게임즈', '에픽', 'epic', 'epicgames')"))

        // 5) RecentDealsScreen 삭제 버튼(Alignment.BottomEnd)과 DealCard 미니 하트(썸네일 TopEnd) 비충돌 및 여백 예약 확인
        assertTrue(recentScreenSrc.contains(".align(Alignment.BottomEnd)"))
        assertTrue(recentScreenSrc.contains("reserveBottomEndSpace = true"))
        assertTrue(dealCardSection.contains(".align(Alignment.TopEnd)"))
    }

    @Test
    fun homeScreenAndRecentDealsScreen_satisfyFollowUpVisualAndChallengerFixes() {
        val javaRoot = resolveAppJavaRoot()
        val homeSrc = File(javaRoot, "com/ddaeany0919/insightdeal/presentation/home/HomeScreen.kt").readText()
        val recentScreenSrc = File(javaRoot, "com/ddaeany0919/insightdeal/presentation/mypage/history/RecentDealsScreen.kt").readText()

        // 1) Major 3: 검색 팝오버 BackHandler 및 포커스 유지 상태 재탭(PressInteraction.Release) 오픈
        assertTrue(homeSrc.contains("BackHandler(enabled = showSearchPopover)"))
        assertTrue(homeSrc.contains("PressInteraction.Release"))
        assertTrue(homeSrc.contains("interactionSource = searchInteractionSource"))

        // 2) Minor 4 & Reviewer Minor 1: HomeFilterBottomSheet Draft 리셋 방어 및 platform 정규화
        val filterSheetSection = homeSrc.substringAfter("private fun HomeFilterBottomSheet(")
            .substringBefore("@Composable\nprivate fun HomeSelectablePillChip(")
        assertFalse("HomeFilterBottomSheet 내부에 remember(filterState) 키가 남아 있으면 안 됩니다.", filterSheetSection.contains("remember(filterState)"))
        assertFalse("HomeFilterBottomSheet 내부에 remember(viewMode) 키가 남아 있으면 안 됩니다.", filterSheetSection.contains("remember(viewMode)"))
        assertTrue(filterSheetSection.contains("filterState.platform"))
        val filterApplySection = homeSrc.substringAfter("if (showFilterBottomSheet) {")
            .substringBefore("if (showDeviceSyncSheet) {")
        assertTrue(filterApplySection.contains("viewModel.selectPlatform(\"\")"))
        assertFalse(filterApplySection.contains("if (nextCommunities.isEmpty() && !filterState.platform.isNullOrBlank())"))

        // 3) Minor 6: HomeSelectablePillChip softWrap = false
        val pillChipSection = homeSrc.substringAfter("private fun HomeSelectablePillChip(")
            .substringBefore("private fun HomeActiveFilterChip(")
        assertTrue(pillChipSection.contains("softWrap = false"))

        // 4) Follow-up 비주얼 레퍼런스 + Major 1 + Major 2 (DealCardComposable & CompactDealCardComposable)
        val dealCardSection = homeSrc.substringAfter("fun DealCardComposable(")
            .substringBefore("fun KeywordSettingsBottomSheet(")
        val textStackSection = dealCardSection.substringAfter("Column(modifier = Modifier.weight(1f))")
            .substringBefore("AnimatedVisibility(visible = isExpanded)")
        assertFalse("카드 텍스트 4줄 스택 내부에는 네모/알약 Surface 박스가 없어야 합니다.", textStackSection.contains("Surface("))
        assertFalse("카드 텍스트 4줄 스택 내부에는 BorderStroke 테두리가 없어야 합니다.", textStackSection.contains("BorderStroke("))
        assertTrue(dealCardSection.contains("reserveBottomEndSpace: Boolean = false"))
        assertTrue(dealCardSection.contains("if (reserveBottomEndSpace) 52.dp else 36.dp"))
        assertTrue(dealCardSection.contains("if (reserveBottomEndSpace) 52.dp else 0.dp"))
        assertTrue(dealCardSection.contains("RoundedCornerShape(16.dp)"))
        assertTrue(dealCardSection.contains(".align(Alignment.TopStart)"))
        assertTrue(dealCardSection.contains("Color(0xCC191F28)"))
        assertTrue(dealCardSection.contains("Color(0xFF059669)"))
        assertTrue(dealCardSection.contains("Color(0xFFE11D48)"))
        assertTrue(dealCardSection.contains("Color(0xFF191F28)"))
        assertTrue(dealCardSection.contains("weight(1f, fill = false)"))

        val compactSection = homeSrc.substringAfter("fun CompactDealCardComposable(")
            .substringBefore("private fun MutedKeywordsBottomSheet(")
        assertTrue(compactSection.contains("weight(1f, fill = false)"))
        assertTrue(compactSection.contains("compactMiddleMeta"))

        // 5) RecentDealsScreen 연동 확인
        assertTrue(recentScreenSrc.contains("reserveBottomEndSpace = true"))
    }
}

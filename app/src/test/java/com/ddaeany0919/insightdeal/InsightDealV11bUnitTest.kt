package com.ddaeany0919.insightdeal

import androidx.compose.ui.graphics.Color
import androidx.paging.ExperimentalPagingApi
import androidx.paging.LoadType
import androidx.paging.PagingConfig
import androidx.paging.PagingSource
import androidx.paging.PagingState
import androidx.paging.RemoteMediator
import androidx.sqlite.db.SupportSQLiteDatabase
import com.ddaeany0919.insightdeal.data.network.ClaimPairingCodeResponse
import com.ddaeany0919.insightdeal.data.network.PairingCodeResponse
import com.ddaeany0919.insightdeal.data.util.ImageUrlResolver
import com.ddaeany0919.insightdeal.local.db.AppDatabase
import com.ddaeany0919.insightdeal.local.db.DealDao
import com.ddaeany0919.insightdeal.local.db.DealEntity
import com.ddaeany0919.insightdeal.local.db.DealRemoteKeysDao
import com.ddaeany0919.insightdeal.local.db.DealRemoteKeysEntity
import com.ddaeany0919.insightdeal.models.CategoryCatalog
import com.ddaeany0919.insightdeal.models.DealItem
import com.ddaeany0919.insightdeal.network.ApiService
import com.ddaeany0919.insightdeal.presentation.DealDetailViewModel
import com.ddaeany0919.insightdeal.presentation.computeUnitPriceBadge
import com.ddaeany0919.insightdeal.presentation.extractPaymentConditionBadge
import com.ddaeany0919.insightdeal.presentation.formatDetailPriceText
import com.ddaeany0919.insightdeal.presentation.formatPrice
import com.ddaeany0919.insightdeal.presentation.home.HomeViewModel
import com.ddaeany0919.insightdeal.presentation.home.HotDealsPagingSource
import com.ddaeany0919.insightdeal.presentation.home.HotDealsRemoteMediator
import com.ddaeany0919.insightdeal.presentation.mypage.DeviceSyncViewModel
import com.ddaeany0919.insightdeal.presentation.search.SearchViewModel
import com.ddaeany0919.insightdeal.presentation.theme.AmoledColorScheme
import com.ddaeany0919.insightdeal.presentation.theme.DarkColorScheme
import com.ddaeany0919.insightdeal.presentation.theme.DarkInsightDealColors
import com.ddaeany0919.insightdeal.presentation.theme.InsightDealSemanticColors
import com.ddaeany0919.insightdeal.presentation.theme.LightColorScheme
import com.ddaeany0919.insightdeal.presentation.theme.LightInsightDealColors
import com.google.gson.Gson
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
import java.nio.charset.StandardCharsets
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.math.pow

class InsightDealV11bUnitTest {

    private data class RecordedHttpRequest(
        val method: String,
        val path: String,
        val headers: Map<String, String>,
        val body: String
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
                        val statusText = when (status) {
                            200 -> "OK"
                            400 -> "Bad Request"
                            404 -> "Not Found"
                            409 -> "Conflict"
                            429 -> "Too Many Requests"
                            else -> "Internal Server Error"
                        }
                        val responseHeader = buildString {
                            append("HTTP/1.1 $status $statusText\r\n")
                            append("Content-Type: application/json; charset=utf-8\r\n")
                            append("Content-Length: ${payload.size}\r\n")
                            append("Connection: close\r\n\r\n")
                        }.toByteArray(StandardCharsets.US_ASCII)
                        val out = client.getOutputStream()
                        out.write(responseHeader)
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
            val method = parts.getOrElse(0) { "GET" }
            val path = parts.getOrElse(1) { "/" }
            val headers = linkedMapOf<String, String>()
            for (line in lines.drop(1)) {
                val idx = line.indexOf(':')
                if (idx > 0) {
                    headers[line.substring(0, idx).trim().lowercase()] = line.substring(idx + 1).trim()
                }
            }
            val contentLength = headers["content-length"]?.toIntOrNull() ?: 0
            val bodyBytes = ByteArray(contentLength)
            var readTotal = 0
            while (readTotal < contentLength) {
                val n = input.read(bodyBytes, readTotal, contentLength - readTotal)
                if (n <= 0) break
                readTotal += n
            }
            return RecordedHttpRequest(
                method = method,
                path = path,
                headers = headers,
                body = String(bodyBytes, 0, readTotal, StandardCharsets.UTF_8)
            )
        }

        override fun close() {
            running = false
            runCatching { serverSocket.close() }
            runCatching { worker.join(500) }
        }
    }

    private val resourcesToClose = mutableListOf<Closeable>()
    private val scopesToCancel = mutableListOf<CoroutineScope>()

    @After
    fun tearDown() {
        scopesToCancel.forEach { it.cancel() }
        scopesToCancel.clear()
        resourcesToClose.forEach { runCatching { it.close() } }
        resourcesToClose.clear()
    }

    private fun createApiService(baseUrl: String): ApiService {
        val client = OkHttpClient.Builder().build()
        return Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(ApiService::class.java)
    }

    private fun newUnconfinedScope(): CoroutineScope {
        return CoroutineScope(SupervisorJob() + Dispatchers.Unconfined).also {
            scopesToCancel += it
        }
    }

    private fun relativeLuminance(color: Color): Double {
        fun linearize(channel: Float): Double {
            val c = channel.toDouble()
            return if (c <= 0.03928) c / 12.92 else ((c + 0.055) / 1.055).pow(2.4)
        }
        return 0.2126 * linearize(color.red) +
            0.7152 * linearize(color.green) +
            0.0722 * linearize(color.blue)
    }

    private fun contrastRatio(foreground: Color, background: Color): Double {
        val l1 = relativeLuminance(foreground)
        val l2 = relativeLuminance(background)
        val lighter = maxOf(l1, l2)
        val darker = minOf(l1, l2)
        return (lighter + 0.05) / (darker + 0.05)
    }

    private fun assertSemanticContrast(name: String, semantic: InsightDealSemanticColors, surfaceBg: Color, canvasBg: Color) {
        val minAA = 4.5
        val pairs = listOf(
            "priceAccent vs surface" to (semantic.priceAccent to surfaceBg),
            "success vs surface" to (semantic.success to surfaceBg),
            "warning vs surface" to (semantic.warning to surfaceBg),
            "danger vs surface" to (semantic.danger to surfaceBg),
            "info vs surface" to (semantic.info to surfaceBg),
            "textMuted vs surface" to (semantic.textMuted to surfaceBg),
            "textMuted vs canvas" to (semantic.textMuted to canvasBg),
            "onPriceContainer vs priceContainer" to (semantic.onPriceContainer to semantic.priceContainer),
            "onSuccessContainer vs successContainer" to (semantic.onSuccessContainer to semantic.successContainer),
            "onWarningContainer vs warningContainer" to (semantic.onWarningContainer to semantic.warningContainer),
            "onDangerContainer vs dangerContainer" to (semantic.onDangerContainer to semantic.dangerContainer),
            "onInfoContainer vs infoContainer" to (semantic.onInfoContainer to semantic.infoContainer)
        )
        for ((label, colors) in pairs) {
            val ratio = contrastRatio(colors.first, colors.second)
            assertTrue(
                "$name [$label] 대비율(${String.format("%.2f", ratio)}:1)은 WCAG AA 4.5:1 이상이어야 합니다.",
                ratio >= minAA
            )
        }
    }

    // -----------------------------------------------------------------------
    // 1. P1-11: formatDetailPriceText 0원 표기 통일 + 기존 formatPrice 호환 유지
    // -----------------------------------------------------------------------
    @Test
    fun formatDetailPriceText_unifiesZeroWon_whilePreservingLegacyFormatPrice() {
        assertEquals("0원", formatDetailPriceText(0L, "KRW", "일반 상품", isFree = false))
        assertEquals("0원 (무료)", formatDetailPriceText(0L, "KRW", "스팀 일시 무료 배포", isFree = false))
        assertEquals("0원 (무료)", formatDetailPriceText(0L, "KRW", "일반 상품", isFree = true))
        assertEquals("19,900원", formatDetailPriceText(19900L, "KRW", "일반 상품"))
        assertEquals("$12.50", formatDetailPriceText(1250L, "USD", "아마존 직구"))

        // 기존 formatPrice(0L) 계약 보존
        assertEquals("가격 확인", formatPrice(0L, "KRW", "일반 상품"))
        assertEquals("무료", formatPrice(0L, "KRW", "에픽 무료 배포"))
    }

    // -----------------------------------------------------------------------
    // 2. P1-05: 11종 출처(뽐뿌해외 복구) 순서 및 플랫폼 매핑 검증
    // -----------------------------------------------------------------------
    @Test
    fun categoryCatalog_contains11SourcesIncludingPpomppuOverseasAtExactOrder() {
        val expectedSources = listOf(
            "전체", "펨코", "뽐뿌", "뽐뿌해외", "루리웹",
            "퀘이사존", "클리앙", "알리뽐뿌", "빠삭국내", "빠삭해외", "빠삭육아"
        )
        assertEquals(11, CategoryCatalog.STANDARD_SOURCES.size)
        assertEquals(expectedSources, CategoryCatalog.STANDARD_SOURCES)
        assertEquals(10, CategoryCatalog.STANDARD_COMMUNITY_PLATFORMS.size)
        assertFalse(CategoryCatalog.STANDARD_COMMUNITY_PLATFORMS.contains("전체"))
        assertTrue(CategoryCatalog.STANDARD_COMMUNITY_PLATFORMS.contains("뽐뿌해외"))
    }

    // -----------------------------------------------------------------------
    // 3. P1-03 / P1-06 / P1-12: DealEntity <-> DealItem 왕복 매핑 & resolvedBodyImages
    // -----------------------------------------------------------------------
    @Test
    fun dealEntity_roundTripsBodyImagesSearchFallbackIsFreeAndReportCount_andResolvedBodyImages() {
        val sourceDeal = DealItem(
            id = 777,
            title = "본문 이미지 테스트 딜",
            price = 0L,
            currency = "KRW",
            siteName = "뽐뿌해외",
            bodyImages = listOf(
                " https://cdn.example.com/a.jpg ",
                "",
                "ftp://invalid.example.com/b.jpg",
                "/api/proxy-image?url=test",
                "https://cdn.example.com/a.jpg"
            ),
            isSearchFallback = true,
            isFree = true,
            reportCount = 2
        )

        val roundTripped = DealEntity.fromDealItem(sourceDeal).toDealItem()
        assertEquals(sourceDeal.resolvedBodyImages, roundTripped.bodyImages)
        assertTrue(roundTripped.isSearchFallback)
        assertTrue(roundTripped.isFree)
        assertEquals(2, roundTripped.reportCount)
        assertEquals(
            listOf("https://cdn.example.com/a.jpg", "/api/proxy-image?url=test"),
            roundTripped.resolvedBodyImages
        )
    }

    // -----------------------------------------------------------------------
    // 4. P1-06: DealDetailViewModel.reportClosed() 임계치(200) / 409 / 429 처리
    // -----------------------------------------------------------------------
    @Test
    fun dealDetailViewModel_reportClosed_handlesThreshold200_409_and_429() = runBlocking {
        val server = LocalStubHttpServer { req ->
            when {
                req.method == "GET" && req.path.startsWith("/api/community/deals/101") ->
                    200 to """{"id":101,"title":"정상 딜 1","price":12900,"site_name":"뽐뿌","is_closed":false,"report_count":1,"history_count":0}"""
                req.method == "GET" && req.path.startsWith("/api/community/deals/102") ->
                    200 to """{"id":102,"title":"정상 딜 2","price":12900,"site_name":"뽐뿌","is_closed":false,"report_count":2,"history_count":0}"""
                req.method == "GET" && req.path.startsWith("/api/community/deals/103") ->
                    200 to """{"id":103,"title":"중복 제보 딜","price":12900,"site_name":"뽐뿌","is_closed":false,"report_count":1,"history_count":0}"""
                req.method == "GET" && req.path.startsWith("/api/community/deals/104") ->
                    200 to """{"id":104,"title":"레이트리밋 딜","price":12900,"site_name":"뽐뿌","is_closed":false,"report_count":1,"history_count":0}"""
                req.method == "POST" && req.path == "/api/community/deals/101/report-closed" ->
                    200 to """{"is_closed":false,"report_count":2,"threshold":3}"""
                req.method == "POST" && req.path == "/api/community/deals/102/report-closed" ->
                    200 to """{"is_closed":true,"report_count":3,"threshold":3}"""
                req.method == "POST" && req.path == "/api/community/deals/103/report-closed" ->
                    409 to """{"detail":"Already reported"}"""
                req.method == "POST" && req.path == "/api/community/deals/104/report-closed" ->
                    429 to """{"detail":"Too many requests"}"""
                else -> 404 to """{"detail":"Not Found"}"""
            }
        }.also { resourcesToClose += it }

        val vm = DealDetailViewModel(
            apiService = createApiService(server.baseUrl),
            deviceUuidProvider = { "device-uuid-v11b" },
            ioDispatcher = Dispatchers.Unconfined,
            coroutineScopeOverride = newUnconfinedScope()
        )

        // (1) 200 미임계치 (2/3)
        vm.loadDealData(101)
        withTimeout(3000) { while (vm.uiState.value.deal?.id != 101 || vm.uiState.value.isLoading) delay(10) }
        vm.reportClosed()
        withTimeout(3000) { while (vm.uiState.value.reportClosedFeedbackSeq == 0 || vm.uiState.value.isReportingClosed) delay(10) }
        assertFalse(vm.uiState.value.isClosed)
        assertTrue(
            "hasReportedClosed=true 기대, 실제 feedback=${vm.uiState.value.reportClosedFeedback}, reqs=${server.recordedRequests.map { "${it.method} ${it.path}" }}",
            vm.uiState.value.hasReportedClosed
        )
        assertEquals(2, vm.uiState.value.reportCount)
        assertEquals("품절 제보가 접수되었어요 (2/3)", vm.uiState.value.reportClosedFeedback)

        // (2) 200 임계치 도달 (3/3 -> 즉시 품절/종료 전환)
        vm.loadDealData(102)
        withTimeout(3000) { while (vm.uiState.value.deal?.id != 102 || vm.uiState.value.isLoading) delay(10) }
        vm.reportClosed()
        withTimeout(3000) { while (vm.uiState.value.reportClosedFeedback != "품절/종료 처리되었어요") delay(10) }
        assertTrue(vm.uiState.value.isClosed)
        assertTrue(vm.uiState.value.deal?.isClosed == true)
        assertEquals(3, vm.uiState.value.reportCount)

        // (3) 409 중복 제보
        vm.loadDealData(103)
        withTimeout(3000) { while (vm.uiState.value.deal?.id != 103 || vm.uiState.value.isLoading) delay(10) }
        vm.reportClosed()
        withTimeout(3000) { while (vm.uiState.value.reportClosedFeedback != "이미 품절 제보를 완료했어요") delay(10) }
        assertTrue(vm.uiState.value.hasReportedClosed)

        // (4) 429 레이트리밋
        vm.loadDealData(104)
        withTimeout(3000) { while (vm.uiState.value.deal?.id != 104 || vm.uiState.value.isLoading) delay(10) }
        vm.reportClosed()
        withTimeout(3000) { while (vm.uiState.value.reportClosedFeedback != "요청이 너무 많아요. 잠시 후 다시 시도해 주세요") delay(10) }
        assertFalse(vm.uiState.value.hasReportedClosed)
    }

    // -----------------------------------------------------------------------
    // 5. P1-05 / P1-06: HotDealsPagingSource 커서 + 서버 필터 파라미터 + next_cursor
    // -----------------------------------------------------------------------
    @Test
    fun hotDealsPagingSource_sendsCursorAndServerFilterParams_andParsesNextCursor() = runBlocking {
        val server = LocalStubHttpServer { req ->
            if (req.path.contains("cursor_id=500")) {
                200 to """{
                    "deals": [{"id": 499, "title": "두번째 페이지 딜", "price": 9900, "site_name": "뽐뿌해외"}],
                    "has_more": false
                }"""
            } else {
                200 to """{
                    "deals": [{"id": 500, "title": "첫번째 페이지 딜", "price": 19900, "site_name": "뽐뿌해외"}],
                    "has_more": true,
                    "next_cursor": {"indexed_at": "2026-11-19T12:00:00Z", "id": 500}
                }"""
            }
        }.also { resourcesToClose += it }

        val pagingSource = HotDealsPagingSource(
            apiService = createApiService(server.baseUrl),
            platform = "뽐뿌해외",
            sort = "latest",
            includeClosed = false,
            onlyHot = true,
            hideClosed = true
        )

        val page1 = pagingSource.load(
            PagingSource.LoadParams.Refresh(key = 0, loadSize = 20, placeholdersEnabled = false)
        )
        assertTrue(page1 is PagingSource.LoadResult.Page)
        val p1 = page1 as PagingSource.LoadResult.Page
        assertEquals(1, p1.data.size)
        assertEquals(1, p1.nextKey)

        val page2 = pagingSource.load(
            PagingSource.LoadParams.Append(key = p1.nextKey!!, loadSize = 20, placeholdersEnabled = false)
        )
        assertTrue(page2 is PagingSource.LoadResult.Page)
        val p2 = page2 as PagingSource.LoadResult.Page
        assertEquals(1, p2.data.size)
        assertNull(p2.nextKey)

        assertEquals(2, server.recordedRequests.size)
        val req1Path = server.recordedRequests[0].path
        val req2Path = server.recordedRequests[1].path
        assertTrue(req1Path.contains("only_hot=true"))
        assertTrue(req1Path.contains("hide_closed=true"))
        assertTrue(req1Path.contains("include_closed=false"))
        assertTrue(req2Path.contains("cursor_id=500"))
        assertTrue(req2Path.contains("cursor_indexed_at=2026-11-19T12"))
        assertTrue(req2Path.contains("offset=0"))
    }

    // -----------------------------------------------------------------------
    // 6. P1-01: DeviceSyncViewModel 페어링 코드 발급/병합(200/409/429) & DTO 호환
    // -----------------------------------------------------------------------
    @Test
    fun deviceSyncViewModel_issueAndClaimPairingCode_handles200_409_429_andDtoParsing() = runBlocking {
        val gson = Gson()
        val parsedIssue = gson.fromJson(
            """{"code":"482910","expiresAt":"2026-11-20T02:00:00Z","expiresInSec":600}""",
            PairingCodeResponse::class.java
        )
        assertEquals("482910", parsedIssue.code)
        assertEquals("2026-11-20T02:00:00Z", parsedIssue.expiresAt)
        assertEquals(600, parsedIssue.expiresInSec)

        val parsedClaim = gson.fromJson(
            """{"status":"ok","success":true,"mergedKeywords":2,"mergedWishlists":3,"mergedAlerts":1,"dndSynced":true}""",
            ClaimPairingCodeResponse::class.java
        )
        assertTrue(parsedClaim.success)
        assertEquals(2, parsedClaim.mergedKeywords)
        assertEquals(3, parsedClaim.mergedWishlists)
        assertEquals(1, parsedClaim.mergedAlerts)
        assertTrue(parsedClaim.dndSynced)

        val server = LocalStubHttpServer { req ->
            when (req.path) {
                "/api/auth/pairing-code" ->
                    200 to """{"code":"482910","expires_at":"2026-11-20T02:00:00Z","expires_in_sec":5}"""
                "/api/auth/claim-pairing-code" -> when {
                    req.body.contains("482910") ->
                        200 to """{"status":"ok","success":true,"merged_keywords":2,"merged_wishlists":3,"merged_alerts":1,"dnd_synced":true}"""
                    req.body.contains("111111") ->
                        409 to """{"detail":"already claimed"}"""
                    req.body.contains("222222") ->
                        429 to """{"detail":"rate limited"}"""
                    else -> 404 to """{"detail":"not found"}"""
                }
                else -> 404 to """{"detail":"not found"}"""
            }
        }.also { resourcesToClose += it }

        val vm = DeviceSyncViewModel(
            apiService = createApiService(server.baseUrl),
            ioDispatcher = Dispatchers.Unconfined,
            coroutineScopeOverride = newUnconfinedScope()
        )

        // 잘못된 5자리 입력은 서버 호출 없이 차단
        vm.claimPairingCode("12ab3")
        assertEquals("6자리 숫자 동기화 코드를 정확히 입력해 주세요.", vm.uiState.value.claimError)
        assertEquals(0, server.recordedRequests.size)

        // 코드 발급 200
        vm.issuePairingCode()
        withTimeout(3000) { while (vm.uiState.value.issuedCode == null) delay(10) }
        assertEquals("482910", vm.uiState.value.issuedCode)
        assertTrue(vm.uiState.value.remainingSec > 0)

        // 코드 병합 200
        vm.claimPairingCode("482910")
        withTimeout(3000) { while (vm.uiState.value.syncCompletedSeq == 0 || vm.uiState.value.isClaiming) delay(10) }
        assertEquals(1, vm.uiState.value.syncCompletedSeq)
        assertTrue(vm.uiState.value.claimSuccessMessage!!.contains("키워드 2건 · 찜 3건 · 알림 1건"))

        // 409 중복/자기 기기
        vm.claimPairingCode("111111")
        withTimeout(3000) { while (vm.uiState.value.claimError == null || vm.uiState.value.isClaiming) delay(10) }
        assertEquals("이미 사용된 코드이거나 같은 기기의 코드예요.", vm.uiState.value.claimError)

        // 429 레이트리밋
        vm.claimPairingCode("222222")
        withTimeout(3000) { while (vm.uiState.value.claimError != "시도 횟수가 너무 많아요. 잠시 후 다시 시도해 주세요.") delay(10) }
    }

    // -----------------------------------------------------------------------
    // 7. P1-11: SearchViewModel 검색-홈 Room 분리 & include_closed 전달 검증
    // -----------------------------------------------------------------------
    @Test
    fun searchViewModel_isolatesSearchFromHomeRoom_andPassesIncludeClosedAndSort() = runBlocking {
        val fieldTypeNames = SearchViewModel::class.java.declaredFields.map { it.type.name }
        assertFalse(fieldTypeNames.any { it.contains("AppDatabase") || it.contains("DealDao") || it.contains("HomeViewModel") })

        val server = LocalStubHttpServer { req ->
            when {
                req.path.startsWith("/api/community/popular-keywords") ->
                    200 to """{"keywords":["RTX 5070","맥북"],"rankings":[{"keyword":"RTX 5070","count":15}],"updated_at":"2026-11-20T01:00:00Z"}"""
                req.path.startsWith("/api/community/hot-deals") ->
                    200 to """{"deals":[{"id":888,"title":"검색 결과 딜","price":590000,"site_name":"퀘이사존","is_closed":true}],"has_more":false}"""
                req.path == "/api/coupang/track-click" ->
                    200 to """{"status":"ok"}"""
                else -> 404 to """{"detail":"not found"}"""
            }
        }.also { resourcesToClose += it }

        val apiService = createApiService(server.baseUrl)
        val vm = SearchViewModel(
            apiService = apiService,
            ioDispatcher = Dispatchers.Unconfined,
            coroutineScopeOverride = newUnconfinedScope()
        )

        vm.fetchPopularKeywords()
        withTimeout(3000) { while (vm.popularKeywords.value.isEmpty()) delay(10) }
        assertEquals(listOf("RTX 5070", "맥북"), vm.popularKeywords.value)

        vm.searchDeals("  RTX 5070  ")
        vm.setIncludeClosed(true)
        vm.selectSort("price_asc")
        assertEquals("RTX 5070", vm.searchState.value.keyword)
        assertTrue(vm.searchState.value.includeClosed)
        assertEquals("price_asc", vm.searchState.value.sortBy)

        vm.selectSort("invalid_sort")
        assertEquals("latest", vm.searchState.value.sortBy)

        val searchPaging = HotDealsPagingSource(
            apiService = apiService,
            keyword = vm.searchState.value.keyword,
            sort = "price_asc",
            includeClosed = vm.searchState.value.includeClosed
        )
        val res = searchPaging.load(
            PagingSource.LoadParams.Refresh(key = 0, loadSize = 20, placeholdersEnabled = false)
        )
        assertTrue(res is PagingSource.LoadResult.Page)
        assertEquals(1, (res as PagingSource.LoadResult.Page).data.size)

        val searchReq = server.recordedRequests.last { it.path.startsWith("/api/community/hot-deals") }
        assertTrue(searchReq.path.contains("include_closed=true"))
        assertTrue(searchReq.path.contains("sort=price_asc"))
        assertTrue(searchReq.path.contains("RTX"))

        vm.trackOutboundClick("https://example.com/deal/888", price = 590000)
        withTimeout(3000) { while (server.recordedRequests.none { it.path == "/api/coupang/track-click" }) delay(10) }
    }

    // -----------------------------------------------------------------------
    // 8. P1-07 & Room v8: 시맨틱 컬러 WCAG AA 4.5:1 & MIGRATION_7_8 SQL 검증
    // -----------------------------------------------------------------------
    @Test
    fun semanticColors_meetWcagAA_45To1_andAppDatabaseMigration7To8ContainsAllColumns() {
        assertSemanticContrast(
            name = "LightInsightDealColors",
            semantic = LightInsightDealColors,
            surfaceBg = LightColorScheme.surface,
            canvasBg = LightColorScheme.background
        )
        assertSemanticContrast(
            name = "DarkInsightDealColors",
            semantic = DarkInsightDealColors,
            surfaceBg = DarkColorScheme.surface,
            canvasBg = DarkColorScheme.background
        )
        assertSemanticContrast(
            name = "DarkInsightDealColors(AMOLED)",
            semantic = DarkInsightDealColors,
            surfaceBg = AmoledColorScheme.surface,
            canvasBg = AmoledColorScheme.background
        )

        val executedSqls = mutableListOf<String>()
        val fakeDb = Proxy.newProxyInstance(
            SupportSQLiteDatabase::class.java.classLoader,
            arrayOf(SupportSQLiteDatabase::class.java)
        ) { _, method, args ->
            if (method.name == "execSQL" && args != null && args.isNotEmpty()) {
                executedSqls += args[0] as String
            }
            when (method.returnType) {
                Boolean::class.javaPrimitiveType -> false
                Int::class.javaPrimitiveType -> 0
                Long::class.javaPrimitiveType -> 0L
                else -> null
            }
        } as SupportSQLiteDatabase

        assertEquals(7, AppDatabase.MIGRATION_7_8.startVersion)
        assertEquals(8, AppDatabase.MIGRATION_7_8.endVersion)
        AppDatabase.MIGRATION_7_8.migrate(fakeDb)

        assertTrue(executedSqls.any { it.contains("deals") && it.contains("bodyImages") })
        assertTrue(executedSqls.any { it.contains("deals") && it.contains("isSearchFallback") })
        assertTrue(executedSqls.any { it.contains("deals") && it.contains("isFree") })
        assertTrue(executedSqls.any { it.contains("deals") && it.contains("reportCount") })
        assertTrue(executedSqls.any { it.contains("deal_remote_keys") && it.contains("nextCursorIndexedAt") })
        assertTrue(executedSqls.any { it.contains("deal_remote_keys") && it.contains("nextCursorId") })
    }

    // -----------------------------------------------------------------------
    // 9. P1-05: HotDealsRemoteMediator 커서 전진 · 서버 필터 · 종료 후 중복 차단
    // -----------------------------------------------------------------------
    private class InMemoryDealDao : DealDao {
        val deals = linkedMapOf<Int, DealEntity>()

        override suspend fun insertAll(deals: List<DealEntity>) {
            deals.forEach { this.deals[it.id] = it }
        }

        override fun getPagedDeals(
            category: String?,
            keyword: String?,
            platform: String?,
            sort: String
        ): PagingSource<Int, DealEntity> {
            throw UnsupportedOperationException("단위 테스트에서는 orderedDeals()를 사용합니다.")
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
            throw UnsupportedOperationException("단위 테스트에서는 orderedDeals()를 사용합니다.")
        }

        private fun toKrwEquiv(entity: DealEntity): Double {
            val rate = when (entity.currency.trim().uppercase()) {
                "USD" -> 14.0
                "EUR" -> 15.2
                "CNY" -> 1.95
                "JPY" -> 9.3
                else -> 1.0
            }
            return entity.price * rate
        }

        fun orderedDeals(sort: String): List<DealEntity> {
            return when (sort) {
                "popular" -> deals.values.sortedWith(
                    compareByDescending<DealEntity> { it.honeyScore }
                        .thenByDescending { it.likeCount }
                        .thenByDescending { it.commentCount }
                        .thenByDescending { it.createdAt ?: "" }
                        .thenByDescending { it.id }
                )
                "comments" -> deals.values.sortedWith(
                    compareByDescending<DealEntity> { it.commentCount }
                        .thenByDescending { it.likeCount }
                        .thenByDescending { it.honeyScore }
                        .thenByDescending { it.createdAt ?: "" }
                        .thenByDescending { it.id }
                )
                "recommendations" -> deals.values.sortedWith(
                    compareByDescending<DealEntity> { it.likeCount }
                        .thenByDescending { it.honeyScore }
                        .thenByDescending { it.commentCount }
                        .thenByDescending { it.createdAt ?: "" }
                        .thenByDescending { it.id }
                )
                "price_asc" -> deals.values.sortedWith(
                    compareBy<DealEntity> { if (it.price > 0L) 0 else 1 }
                        .thenBy { toKrwEquiv(it) }
                        .thenByDescending { it.createdAt ?: "" }
                        .thenByDescending { it.id }
                )
                "price_desc" -> deals.values.sortedWith(
                    compareBy<DealEntity> { if (it.price > 0L) 0 else 1 }
                        .thenByDescending { toKrwEquiv(it) }
                        .thenByDescending { it.createdAt ?: "" }
                        .thenByDescending { it.id }
                )
                else -> deals.values.sortedWith(
                    compareByDescending<DealEntity> { it.createdAt ?: "" }
                        .thenByDescending { it.id }
                )
            }
        }

        override suspend fun updateVoteCounts(dealId: Int, buyCount: Int, passCount: Int) {
            deals[dealId]?.let { existing ->
                deals[dealId] = existing.copy(buyCount = buyCount, passCount = passCount)
            }
        }

        override suspend fun updateAppCommentCount(dealId: Int, appCommentCount: Int) {
            deals[dealId]?.let { existing ->
                deals[dealId] = existing.copy(appCommentCount = appCommentCount)
            }
        }

        override suspend fun clearAllDeals() {
            deals.clear()
        }

        override suspend fun getDealCount(): Int = deals.size
    }

    private class InMemoryDealRemoteKeysDao : DealRemoteKeysDao {
        val keys = linkedMapOf<Int, DealRemoteKeysEntity>()

        override suspend fun insertAll(remoteKey: List<DealRemoteKeysEntity>) {
            remoteKey.forEach { keys[it.dealId] = it }
        }

        override suspend fun getRemoteKeysForDealId(dealId: Int): DealRemoteKeysEntity? = keys[dealId]

        override suspend fun clearRemoteKeys() {
            keys.clear()
        }
    }

    private fun buildPagingState(pagesData: List<List<DealEntity>>, pageSize: Int = 20): PagingState<Int, DealEntity> {
        val pages = pagesData.map { list ->
            PagingSource.LoadResult.Page<Int, DealEntity>(data = list, prevKey = null, nextKey = null)
        }
        return PagingState<Int, DealEntity>(
            pages = pages,
            anchorPosition = null,
            config = PagingConfig(pageSize = pageSize, enablePlaceholders = false),
            leadingPlaceholderCount = 0
        )
    }

    @OptIn(ExperimentalPagingApi::class)
    @Test
    fun hotDealsRemoteMediator_refreshAndAppend_advancesCursorAndStopsAfterEnd() = runBlocking {
        val server = LocalStubHttpServer { req ->
            if (req.path.contains("cursor_id=500")) {
                200 to """{
                    "deals": [{"id": 499, "title": "두번째 페이지", "price": 9900, "site_name": "뽐뿌해외"}],
                    "has_more": false,
                    "next_cursor": {"indexed_at": "2026-11-19T11:00:00Z", "id": 499}
                }"""
            } else {
                200 to """{
                    "deals": [{"id": 500, "title": "첫번째 페이지", "price": 19900, "site_name": "뽐뿌해외"}],
                    "has_more": true,
                    "next_cursor": {"indexed_at": "2026-11-19T12:00:00Z", "id": 500}
                }"""
            }
        }.also { resourcesToClose += it }

        val dealDao = InMemoryDealDao()
        val keysDao = InMemoryDealRemoteKeysDao()
        val mediator = HotDealsRemoteMediator(
            dealDao = dealDao,
            dealRemoteKeysDao = keysDao,
            runInTransaction = { block -> block() },
            apiService = createApiService(server.baseUrl),
            category = "전체",
            keyword = null,
            platform = "뽐뿌해외",
            sort = "latest",
            onlyHot = true,
            hideClosed = true
        )

        val refreshResult = mediator.load(LoadType.REFRESH, buildPagingState(emptyList()))
        assertTrue(refreshResult is RemoteMediator.MediatorResult.Success)
        assertFalse((refreshResult as RemoteMediator.MediatorResult.Success).endOfPaginationReached)
        assertEquals(1, dealDao.getDealCount())
        val sentinelAfterRefresh = keysDao.getRemoteKeysForDealId(HotDealsRemoteMediator.STREAM_CURSOR_SENTINEL_ID)
        assertNotNull(sentinelAfterRefresh)
        assertEquals(500, sentinelAfterRefresh?.nextCursorId)

        val appendResult = mediator.load(
            LoadType.APPEND,
            buildPagingState(listOf(dealDao.orderedDeals("latest")))
        )
        assertTrue(appendResult is RemoteMediator.MediatorResult.Success)
        assertTrue((appendResult as RemoteMediator.MediatorResult.Success).endOfPaginationReached)
        assertEquals(2, dealDao.getDealCount())
        assertEquals(2, server.recordedRequests.size)

        // 종료 후 재차 APPEND 호출 시 네트워크 추가 호출 없이 즉시 종료
        val redundantAppend = mediator.load(
            LoadType.APPEND,
            buildPagingState(listOf(dealDao.orderedDeals("latest")))
        )
        assertTrue(redundantAppend is RemoteMediator.MediatorResult.Success)
        assertTrue((redundantAppend as RemoteMediator.MediatorResult.Success).endOfPaginationReached)
        assertEquals(2, server.recordedRequests.size)

        val req1 = server.recordedRequests[0].path
        val req2 = server.recordedRequests[1].path
        assertTrue(req1.contains("only_hot=true"))
        assertTrue(req1.contains("hide_closed=true"))
        assertTrue(req2.contains("cursor_id=500"))
        assertTrue(req2.contains("cursor_indexed_at=2026-11-19T12"))
    }

    // -----------------------------------------------------------------------
    // 10. P1-05: has_more=false 우선 종료 및 동일 커서 비진행 무한 루프 차단
    // -----------------------------------------------------------------------
    @OptIn(ExperimentalPagingApi::class)
    @Test
    fun hotDealsRemoteMediator_respectsHasMoreFalse_andStopsOnNonAdvancingCursor() = runBlocking {
        // (1) has_more=false 이지만 next_cursor가 응답에 남아 있는 경우에도 즉시 종료해야 함
        val serverHasMoreFalse = LocalStubHttpServer { _ ->
            200 to """{
                "deals": [{"id": 400, "title": "마지막 페이지", "price": 10000, "site_name": "뽐뿌"}],
                "has_more": false,
                "next_cursor": {"indexed_at": "2026-11-19T10:00:00Z", "id": 400}
            }"""
        }.also { resourcesToClose += it }

        val dealDao1 = InMemoryDealDao()
        val keysDao1 = InMemoryDealRemoteKeysDao()
        val mediator1 = HotDealsRemoteMediator(
            dealDao = dealDao1,
            dealRemoteKeysDao = keysDao1,
            runInTransaction = { block -> block() },
            apiService = createApiService(serverHasMoreFalse.baseUrl),
            category = null,
            keyword = null,
            platform = null
        )

        val res1 = mediator1.load(LoadType.REFRESH, buildPagingState(emptyList()))
        assertTrue(res1 is RemoteMediator.MediatorResult.Success)
        assertTrue((res1 as RemoteMediator.MediatorResult.Success).endOfPaginationReached)
        val appendAfterFalse = mediator1.load(
            LoadType.APPEND,
            buildPagingState(listOf(dealDao1.orderedDeals("latest")))
        )
        assertTrue((appendAfterFalse as RemoteMediator.MediatorResult.Success).endOfPaginationReached)
        assertEquals(1, serverHasMoreFalse.recordedRequests.size)

        // (2) APPEND에서 직전 요청과 동일한 (cursorIndexedAt, cursorId)가 반환되면 즉시 종료
        val serverSameCursor = LocalStubHttpServer { req ->
            if (req.path.contains("cursor_id=300")) {
                200 to """{
                    "deals": [{"id": 299, "title": "정체 커서 페이지", "price": 12000, "site_name": "뽐뿌"}],
                    "has_more": true,
                    "next_cursor": {"indexed_at": "2026-11-19T09:00:00Z", "id": 300}
                }"""
            } else {
                200 to """{
                    "deals": [{"id": 300, "title": "초기 페이지", "price": 15000, "site_name": "뽐뿌"}],
                    "has_more": true,
                    "next_cursor": {"indexed_at": "2026-11-19T09:00:00Z", "id": 300}
                }"""
            }
        }.also { resourcesToClose += it }

        val dealDao2 = InMemoryDealDao()
        val keysDao2 = InMemoryDealRemoteKeysDao()
        val mediator2 = HotDealsRemoteMediator(
            dealDao = dealDao2,
            dealRemoteKeysDao = keysDao2,
            runInTransaction = { block -> block() },
            apiService = createApiService(serverSameCursor.baseUrl),
            category = null,
            keyword = null,
            platform = null
        )

        val r1 = mediator2.load(LoadType.REFRESH, buildPagingState(emptyList()))
        assertFalse((r1 as RemoteMediator.MediatorResult.Success).endOfPaginationReached)

        val r2 = mediator2.load(
            LoadType.APPEND,
            buildPagingState(listOf(dealDao2.orderedDeals("latest")))
        )
        assertTrue(r2 is RemoteMediator.MediatorResult.Success)
        assertTrue((r2 as RemoteMediator.MediatorResult.Success).endOfPaginationReached)
    }

    // -----------------------------------------------------------------------
    // 11. P1-05: sort=popular Room 재정렬 시에도 Page 1 키 재요청 무한 루프 방지
    // -----------------------------------------------------------------------
    @OptIn(ExperimentalPagingApi::class)
    @Test
    fun hotDealsRemoteMediator_sortPopularReordering_neverLoopsPreviousOffset() = runBlocking {
        val server = LocalStubHttpServer { req ->
            when {
                req.path.contains("offset=0") ->
                    200 to """{
                        "deals": [{"id": 1, "title": "인기1페이지", "price": 10000, "site_name": "뽐뿌", "honey_score": 100, "like_count": 5}],
                        "has_more": true
                    }"""
                req.path.contains("offset=1") ->
                    200 to """{
                        "deals": [{"id": 2, "title": "인기2페이지", "price": 10000, "site_name": "뽐뿌", "honey_score": 100, "like_count": 10}],
                        "has_more": true
                    }"""
                req.path.contains("offset=2") ->
                    200 to """{
                        "deals": [{"id": 3, "title": "인기3페이지", "price": 10000, "site_name": "뽐뿌", "honey_score": 100, "like_count": 15}],
                        "has_more": false
                    }"""
                else -> 404 to """{"detail":"unexpected"}"""
            }
        }.also { resourcesToClose += it }

        val dealDao = InMemoryDealDao()
        val keysDao = InMemoryDealRemoteKeysDao()
        val mediator = HotDealsRemoteMediator(
            dealDao = dealDao,
            dealRemoteKeysDao = keysDao,
            runInTransaction = { block -> block() },
            apiService = createApiService(server.baseUrl),
            category = null,
            keyword = null,
            platform = null,
            sort = "popular"
        )

        val page1Res = mediator.load(LoadType.REFRESH, buildPagingState(emptyList(), pageSize = 1))
        assertFalse((page1Res as RemoteMediator.MediatorResult.Success).endOfPaginationReached)

        val page2Res = mediator.load(
            LoadType.APPEND,
            buildPagingState(listOf(dealDao.orderedDeals("popular")), pageSize = 1)
        )
        assertFalse((page2Res as RemoteMediator.MediatorResult.Success).endOfPaginationReached)

        // Room popular 정렬에 의해 likeCount=5 인 Page 1 아이템(id=1)이 마지막 행으로 밀려난 상태 재현
        val reorderedDeals = dealDao.orderedDeals("popular")
        assertEquals(listOf(2, 1), reorderedDeals.map { it.id })

        val page3Res = mediator.load(
            LoadType.APPEND,
            buildPagingState(listOf(reorderedDeals), pageSize = 1)
        )
        assertTrue(page3Res is RemoteMediator.MediatorResult.Success)
        assertTrue((page3Res as RemoteMediator.MediatorResult.Success).endOfPaginationReached)
        assertEquals(3, server.recordedRequests.size)
        assertTrue(server.recordedRequests[0].path.contains("offset=0"))
        assertTrue(server.recordedRequests[1].path.contains("offset=1"))
        assertTrue(server.recordedRequests[2].path.contains("offset=2"))
    }

    // -----------------------------------------------------------------------
    // 12. P1-06: DealDetailViewModel.reportClosed() 딜 전환 시 지연 응답 격리
    // -----------------------------------------------------------------------
    @Test
    fun dealDetailViewModel_reportClosed_ignoresStaleResponseOnDealSwitch() = runBlocking {
        val server = LocalStubHttpServer { req ->
            when {
                req.method == "GET" && req.path.startsWith("/api/community/deals/201") ->
                    200 to """{"id":201,"title":"이전 딜","price":10000,"site_name":"뽐뿌","is_closed":false,"report_count":2,"history_count":0}"""
                req.method == "GET" && req.path.startsWith("/api/community/deals/202") ->
                    200 to """{"id":202,"title":"새 딜","price":20000,"site_name":"퀘이사존","is_closed":false,"report_count":0,"history_count":0}"""
                req.method == "POST" && req.path == "/api/community/deals/201/report-closed" -> {
                    Thread.sleep(150)
                    200 to """{"is_closed":true,"report_count":3,"threshold":3}"""
                }
                else -> 404 to """{"detail":"not found"}"""
            }
        }.also { resourcesToClose += it }

        val vm = DealDetailViewModel(
            apiService = createApiService(server.baseUrl),
            deviceUuidProvider = { "device-uuid-race" },
            ioDispatcher = Dispatchers.IO,
            coroutineScopeOverride = newUnconfinedScope()
        )

        vm.loadDealData(201)
        withTimeout(3000) { while (vm.uiState.value.deal?.id != 201 || vm.uiState.value.isLoading) delay(10) }

        vm.reportClosed()
        delay(20)
        vm.loadDealData(202)
        withTimeout(3000) { while (vm.uiState.value.deal?.id != 202 || vm.uiState.value.isLoading) delay(10) }
        delay(220)

        assertEquals(202, vm.uiState.value.deal?.id)
        assertFalse(vm.uiState.value.isClosed)
        assertFalse(vm.uiState.value.hasReportedClosed)
        assertEquals(0, vm.uiState.value.reportCount)
        assertNull(vm.uiState.value.reportClosedFeedback)
    }

    // -----------------------------------------------------------------------
    // 13. M4 결함 #2 회귀: HomeViewModel.COMMUNITY_FILTER_CHIPS ↔ CategoryCatalog.STANDARD_SOURCES SSOT 11종 순서 일치
    // -----------------------------------------------------------------------
    @Test
    fun homeViewModel_communityFilterChips_matchesStandardSources11Items() {
        val expected = listOf(
            "전체",
            "펨코",
            "뽐뿌",
            "뽐뿌해외",
            "루리웹",
            "퀘이사존",
            "클리앙",
            "알리뽐뿌",
            "빠삭국내",
            "빠삭해외",
            "빠삭육아"
        )
        assertEquals(expected, CategoryCatalog.STANDARD_SOURCES)
        assertEquals(CategoryCatalog.STANDARD_SOURCES, HomeViewModel.COMMUNITY_FILTER_CHIPS)
        assertEquals(11, HomeViewModel.COMMUNITY_FILTER_CHIPS.size)
    }

    // -----------------------------------------------------------------------
    // 14. Track 3: HomeViewModel 커뮤니티 다중 선택 · 재클릭 해제 · 전체 초기화
    // -----------------------------------------------------------------------
    @Test
    fun homeViewModel_toggleCommunityChip_supportsMultiSelectReclickRemoveAndResetAll() {
        val server = LocalStubHttpServer { _ ->
            200 to """{"deals":[],"has_more":false}"""
        }.also { resourcesToClose += it }

        val vm = HomeViewModel(
            apiService = createApiService(server.baseUrl),
            coroutineScopeOverride = newUnconfinedScope()
        )

        // 초기 상태: 전체
        assertTrue(vm.filterState.value.selectedCommunityChips.isEmpty())
        assertEquals("전체", vm.filterState.value.communityChip)
        assertNull(vm.filterState.value.resolvedPlatformQuery())

        // 2개 추가: 뽐뿌 + 펨코 -> 정렬된 쉼표 조인 "뽐뿌,펨코"
        vm.toggleCommunityChip("뽐뿌")
        assertEquals(setOf("뽐뿌"), vm.filterState.value.selectedCommunityChips)
        assertEquals("뽐뿌", vm.filterState.value.communityChip)
        assertEquals("뽐뿌", vm.filterState.value.resolvedPlatformQuery())

        vm.toggleCommunityChip("펨코")
        assertEquals(setOf("뽐뿌", "펨코"), vm.filterState.value.selectedCommunityChips)
        assertEquals("뽐뿌,펨코", vm.filterState.value.communityChip)
        assertEquals("뽐뿌,펨코", vm.filterState.value.resolvedPlatformQuery())

        // 1개 재클릭 제거: 뽐뿌 해제 -> "펨코"만 유지
        vm.toggleCommunityChip("뽐뿌")
        assertEquals(setOf("펨코"), vm.filterState.value.selectedCommunityChips)
        assertEquals("펨코", vm.filterState.value.communityChip)
        assertEquals("펨코", vm.filterState.value.resolvedPlatformQuery())

        // 추가 선택 후 "전체" 클릭 시 전체 초기화
        vm.toggleCommunityChip("루리웹")
        assertEquals(setOf("펨코", "루리웹"), vm.filterState.value.selectedCommunityChips)
        vm.toggleCommunityChip("전체")
        assertTrue(vm.filterState.value.selectedCommunityChips.isEmpty())
        assertEquals("전체", vm.filterState.value.communityChip)
        assertNull(vm.filterState.value.resolvedPlatformQuery())

        // 레거시 selectCommunityChip 호환 (첫 호출 시 추가, 재호출 시 토글 해제)
        vm.selectCommunityChip("퀘이사존")
        assertEquals("퀘이사존", vm.filterState.value.resolvedPlatformQuery())
        vm.selectCommunityChip("퀘이사존")
        assertEquals("전체", vm.filterState.value.communityChip)
        assertNull(vm.filterState.value.resolvedPlatformQuery())
    }

    // -----------------------------------------------------------------------
    // 15. Track 3: HomeViewModel 카테고리 다중 선택 · 조인 · 해제 & DealDao 다중 SQL 정합
    // -----------------------------------------------------------------------
    @Test
    fun homeViewModel_toggleCategory_supportsMultiSelectJoinRemoveAndDealDaoSqlContract() {
        val server = LocalStubHttpServer { _ ->
            200 to """{"deals":[],"has_more":false}"""
        }.also { resourcesToClose += it }

        val vm = HomeViewModel(
            apiService = createApiService(server.baseUrl),
            coroutineScopeOverride = newUnconfinedScope()
        )

        assertTrue(vm.filterState.value.selectedCategories.isEmpty())
        assertEquals("전체", vm.filterState.value.category)
        assertEquals("전체", vm.filterState.value.resolvedCategoryQuery())

        // 다중 추가 + 레거시 별칭 정규화("PC/하드웨어" -> "PC용품")
        vm.toggleCategory("가전제품")
        assertEquals(setOf("가전제품"), vm.filterState.value.selectedCategories)
        assertEquals("가전제품", vm.filterState.value.category)

        vm.toggleCategory("PC/하드웨어")
        assertEquals(setOf("가전제품", "PC용품"), vm.filterState.value.selectedCategories)
        assertEquals("PC용품,가전제품", vm.filterState.value.category)
        assertEquals("PC용품,가전제품", vm.filterState.value.resolvedCategoryQuery())

        // 재클릭 1개 제거 -> "PC용품"만 유지
        vm.toggleCategory("가전제품")
        assertEquals(setOf("PC용품"), vm.filterState.value.selectedCategories)
        assertEquals("PC용품", vm.filterState.value.category)

        // 마지막 1개 재클릭 제거 -> "전체" 복귀
        vm.toggleCategory("PC용품")
        assertTrue(vm.filterState.value.selectedCategories.isEmpty())
        assertEquals("전체", vm.filterState.value.category)

        // 복합 필터 설정 후 resetHomeFilters() 일괄 초기화 검증
        vm.toggleCategory("음식")
        vm.toggleCategory("뷰티")
        vm.toggleCommunityChip("뽐뿌")
        vm.searchDeals("선크림")
        vm.toggleHideClosed()
        vm.toggleFreeShippingOnly()
        vm.selectSort("popular")
        vm.resetHomeFilters()

        assertTrue(vm.filterState.value.selectedCategories.isEmpty())
        assertEquals("전체", vm.filterState.value.category)
        assertTrue(vm.filterState.value.selectedCommunityChips.isEmpty())
        assertEquals("전체", vm.filterState.value.communityChip)
        assertNull(vm.filterState.value.keyword)
        assertEquals("latest", vm.filterState.value.sortBy)
        assertFalse(vm.filterState.value.hideClosed)
        assertFalse(vm.filterState.value.freeShippingOnly)

        // DealDao.kt가 모든 표준 카테고리에 대해 단일 값과 쉼표 다중 값 경계 매칭을 포함하는지 확인
        val sourceRoot = listOf(File("src/main/java"), File("app/src/main/java")).firstOrNull { it.exists() }
        assertNotNull(sourceRoot)
        val daoText = File(sourceRoot, "com/ddaeany0919/insightdeal/local/db/DealDao.kt").readText()
        for (cat in CategoryCatalog.STANDARD_CATEGORIES) {
            val key = cat.key
            assertTrue("DealDao.kt에 '$key' 단일/다중 경계 매칭 SQL이 포함되어야 합니다.", daoText.contains(":category = '$key'"))
            assertTrue("DealDao.kt에 '$key,%' 접두 다중 매칭 SQL이 포함되어야 합니다.", daoText.contains(":category LIKE '$key,%'"))
            assertTrue("DealDao.kt에 '%,$key' 접미 다중 매칭 SQL이 포함되어야 합니다.", daoText.contains(":category LIKE '%,$key'"))
            assertTrue("DealDao.kt에 '%,$key,%' 중간 다중 매칭 SQL이 포함되어야 합니다.", daoText.contains(":category LIKE '%,$key,%'"))
        }
    }

    // -----------------------------------------------------------------------
    // 16. Track 3: ImageUrlResolver 차단 커뮤니티 백엔드 프록시 &w=600&v=5 파라미터 검증
    // -----------------------------------------------------------------------
    @Test
    fun imageUrlResolver_appendsWidthParamBeforeVersionTagForBlockedCommunities() {
        val debugBase = "http://10.0.2.2:8080/"
        val releaseBase = "http://insightdeal.site/"

        val debugWithWidth = ImageUrlResolver.resolve("http://cdn.ppomppu.co.kr/a.jpg", 600, debugBase, isDebug = true)
        assertNotNull(debugWithWidth)
        assertTrue(debugWithWidth!!.startsWith("http://10.0.2.2:8080/api/proxy-image?url="))
        assertTrue(debugWithWidth.contains("&w=600&v=5"))
        assertTrue(debugWithWidth.endsWith("&v=5"))
        assertFalse(debugWithWidth.contains("wsrv.nl"))

        val releaseWithWidth = ImageUrlResolver.resolve("https://img.fmkorea.com/b.png", 600, releaseBase, isDebug = false)
        assertNotNull(releaseWithWidth)
        assertTrue(releaseWithWidth!!.startsWith("https://insightdeal.site/api/proxy-image?url="))
        assertTrue(releaseWithWidth.contains("&w=600&v=5"))
        assertTrue(releaseWithWidth.endsWith("&v=5"))
        assertFalse(releaseWithWidth.contains("wsrv.nl"))

        val withoutWidth = ImageUrlResolver.resolve("https://ruliweb.com/c.webp", null, releaseBase, isDebug = false)
        assertNotNull(withoutWidth)
        assertFalse(withoutWidth!!.contains("&w="))
        assertTrue(withoutWidth.endsWith("&v=5"))

        val zeroWidth = ImageUrlResolver.resolve("https://ruliweb.com/c.webp", 0, releaseBase, isDebug = false)
        assertNotNull(zeroWidth)
        assertFalse(zeroWidth!!.contains("&w="))
        assertTrue(zeroWidth.endsWith("&v=5"))
    }

    // -----------------------------------------------------------------------
    // 17. Track 3: DealDetailViewModel initialHistory 1-RTT 무왕복 즉시 적재 및 캐시 검증
    // -----------------------------------------------------------------------
    @Test
    fun dealDetailViewModel_loadsInitialHistoryInSingleRttWithoutExtraHistoryCall() = runBlocking {
        val gson = Gson()
        val camelDeal = gson.fromJson(
            """{"id":1,"title":"카멜","price":10000,"site_name":"뽐뿌","initialHistory":[{"recordedAt":"2026-11-18","price":12000,"platform":"뽐뿌"}]}""",
            DealItem::class.java
        )
        assertEquals(1, camelDeal.initialHistory?.size)
        assertEquals(12000, camelDeal.initialHistory?.firstOrNull()?.price)

        val server = LocalStubHttpServer { req ->
            when {
                req.method == "GET" && req.path.startsWith("/api/community/deals/301/history") ->
                    200 to """[{"recordedAt":"26.09.01","price":32000,"platform":"뽐뿌"},{"recordedAt":"26.11.19","price":25900,"platform":"뽐뿌"}]"""
                req.method == "GET" && req.path.startsWith("/api/community/deals/301") ->
                    200 to """{
                        "id": 301,
                        "title": "1-RTT 시세 포함 단일 딜",
                        "price": 25900,
                        "currency": "KRW",
                        "site_name": "뽐뿌",
                        "history_count": 2,
                        "initial_history": [
                            {"recordedAt": "11.18", "price": 29900, "platform": "뽐뿌"},
                            {"recordedAt": "11.19", "price": 25900, "platform": "뽐뿌"},
                            {"recordedAt": "11.20", "price": 0, "platform": "뽐뿌"}
                        ]
                    }"""
                req.method == "GET" && req.path.startsWith("/api/community/deals/302/history") ->
                    200 to """[{"recordedAt":"11.18","price":19900,"platform":"퀘이사존"}]"""
                req.method == "GET" && req.path.startsWith("/api/community/deals/302") ->
                    200 to """{
                        "id": 302,
                        "title": "3m 자동확장 시세 포함 딜",
                        "price": 18000,
                        "currency": "KRW",
                        "site_name": "퀘이사존",
                        "history_count": 2,
                        "initialHistory": [
                            {"recordedAt": "26.10.15", "price": 22000, "platform": "퀘이사존"},
                            {"recordedAt": "26.11.19", "price": 18000, "platform": "퀘이사존"}
                        ]
                    }"""
                req.method == "GET" && req.path.startsWith("/api/community/deals/303/history") ->
                    200 to """[{"recordedAt":"11.18","price":45000,"platform":"펨코"},{"recordedAt":"11.19","price":42000,"platform":"펨코"}]"""
                req.method == "GET" && req.path.startsWith("/api/community/deals/303") ->
                    200 to """{
                        "id": 303,
                        "title": "멀티옵션 시세 분리 딜",
                        "price": 42000,
                        "currency": "KRW",
                        "site_name": "펨코",
                        "history_count": 2,
                        "options_data": [
                            {"name": "옵션 A", "price": 42000, "currency": "KRW"},
                            {"name": "옵션 B", "price": 52000, "currency": "KRW"}
                        ],
                        "initial_history": [
                            {"recordedAt": "11.18", "price": 99000, "platform": "펨코"}
                        ]
                    }"""
                else -> 404 to """{"detail":"Not Found"}"""
            }
        }.also { resourcesToClose += it }

        // (1) 단일 옵션 + initial_history -> 추가 /history 왕복 0회 + 0원 필터링 + historyCache 저장 검증
        val vm1 = DealDetailViewModel(
            apiService = createApiService(server.baseUrl),
            deviceUuidProvider = { "device-1rtt-test" },
            ioDispatcher = Dispatchers.Unconfined,
            coroutineScopeOverride = newUnconfinedScope()
        )
        vm1.loadDealData(301)
        withTimeout(3000) { while (vm1.uiState.value.deal?.id != 301 || vm1.uiState.value.isLoading) delay(10) }

        assertEquals(0, server.recordedRequests.count { it.path.startsWith("/api/community/deals/301/history") })
        assertEquals(listOf(29900, 25900), vm1.uiState.value.priceHistory.map { it.price })
        assertTrue(vm1.uiState.value.isRecordLow)
        assertEquals(2, vm1.uiState.value.historyCount)
        assertEquals("7d", vm1.selectedPeriod.value)

        // 기간 전환("3m")은 네트워크 1회 호출 후 캐시, 다시 "7d" 복귀 시 initialHistory 캐시 히트로 추가 왕복 0회
        vm1.setPeriod("3m")
        withTimeout(3000) { while (vm1.uiState.value.isHistoryLoading || vm1.uiState.value.priceHistory.firstOrNull()?.price != 32000) delay(10) }
        assertEquals(1, server.recordedRequests.count { it.path.startsWith("/api/community/deals/301/history") })

        vm1.setPeriod("7d")
        withTimeout(3000) { while (vm1.uiState.value.isHistoryLoading || vm1.uiState.value.priceHistory.firstOrNull()?.price != 29900) delay(10) }
        assertEquals(1, server.recordedRequests.count { it.path.startsWith("/api/community/deals/301/history") })

        // (2) yy.MM.dd 3m 자동 확장 initialHistory -> 0회 호출 & selectedPeriod="3m" 자동 전환 & 캐시 히트
        val vm2 = DealDetailViewModel(
            apiService = createApiService(server.baseUrl),
            deviceUuidProvider = { "device-1rtt-test" },
            ioDispatcher = Dispatchers.Unconfined,
            coroutineScopeOverride = newUnconfinedScope()
        )
        vm2.loadDealData(302)
        withTimeout(3000) { while (vm2.uiState.value.deal?.id != 302 || vm2.uiState.value.isLoading) delay(10) }
        assertEquals(0, server.recordedRequests.count { it.path.startsWith("/api/community/deals/302/history") })
        assertEquals("3m", vm2.selectedPeriod.value)
        assertEquals(listOf(22000, 18000), vm2.uiState.value.priceHistory.map { it.price })
        assertTrue(vm2.uiState.value.isRecordLow)

        // (3) 멀티옵션(firstOptQuery != null)인 경우 initial_history가 있어도 옵션별 /history API 호출 유지
        val vm3 = DealDetailViewModel(
            apiService = createApiService(server.baseUrl),
            deviceUuidProvider = { "device-1rtt-test" },
            ioDispatcher = Dispatchers.Unconfined,
            coroutineScopeOverride = newUnconfinedScope()
        )
        vm3.loadDealData(303)
        withTimeout(3000) { while (vm3.uiState.value.deal?.id != 303 || vm3.uiState.value.isLoading) delay(10) }
        assertEquals(1, server.recordedRequests.count { it.path.startsWith("/api/community/deals/303/history") })
        assertEquals(listOf(45000, 42000), vm3.uiState.value.priceHistory.map { it.price })
    }

    @Test
    fun unitPriceFormatter_matchesWebParityAndBlocksTechOrUsd() {
        val guk = computeUnitPriceBadge("[쿠팡] 비비고 육개장 500g x 6개", 17900L, "KRW", "음식")
        assertNotNull(guk)
        assertEquals("100g당 597원", guk?.label)
        assertEquals("총 3,000g · 개당 2,983원", guk?.detail)

        val coke = computeUnitPriceBadge("[지마켓] 코카콜라 제로 당류 0g 355ml x 24캔", 16900L, "KRW", "음식")
        assertNotNull(coke)
        assertEquals("100ml당 198원", coke?.label)
        assertEquals("총 8,520ml · 개당 704원", coke?.detail)

        val wipes = computeUnitPriceBadge("[티몬] 베베숲 물티슈 70매 x 10팩", 15900L, "KRW", "육아/유아용품")
        assertNotNull(wipes)
        assertEquals("1팩당 1,590원", wipes?.label)
        assertEquals("70매 × 10팩 기준", wipes?.detail)

        val rolls = computeUnitPriceBadge("[쿠팡] 두루마리 휴지 30롤", 18000L, "KRW", "생활용품")
        assertNotNull(rolls)
        assertEquals("1롤당 600원", rolls?.label)
        assertEquals("총 30롤 기준", rolls?.detail)

        val shinRamen = computeUnitPriceBadge("농심 신라면 120g 5봉 x 4팩", 13800L, "KRW", "음식")
        assertNotNull(shinRamen)
        assertEquals("100g당 575원", shinRamen?.label)
        assertEquals("총 2,400g · 개당 690원", shinRamen?.detail)

        assertNull(computeUnitPriceBadge("[알리] Netac NVMe SSD 1TB", 69000L, "KRW", "PC용품"))
        assertNull(computeUnitPriceBadge("[쿠팡] 27인치 게이밍 모니터 165Hz", 199000L, "KRW", "생활용품"))
        assertNull(computeUnitPriceBadge("스카이 핏 스마트 글래스 S1 블루투스6.0 오디오 안경 2개", 36900L, "KRW", "기타"))
        assertNull(computeUnitPriceBadge("[아마존] 비타민 500g", 2000L, "USD", "음식"))

        // Wave 1 (v1.1d) 확장 케이스 1:1 패리티 검증
        val noXSpace = computeUnitPriceBadge("삼다수 무라벨 500mlx2개+2개", 4000L, "KRW", "음식")
        assertNotNull(noXSpace)
        assertEquals("100ml당 200원", noXSpace?.label)
        assertEquals("총 2,000ml · 개당 1,000원", noXSpace?.detail)

        val sachetBox = computeUnitPriceBadge("종근당 락토핏 (2g x 30포) 2박스", 12900L, "KRW", "음식")
        assertNotNull(sachetBox)
        assertEquals("1포당 215원", sachetBox?.label)
        assertEquals("총 60포 · 120g 기준", sachetBox?.detail)

        val refillSum = computeUnitPriceBadge("피죤 리치퍼퓸 4.2L 본품+2L 리필 2개", 25200L, "KRW", "생활용품")
        assertNotNull(refillSum)
        assertEquals("100ml당 307원", refillSum?.label)
        assertEquals("총 8,200ml 기준", refillSum?.detail)

        val kimchiSet = computeUnitPriceBadge("김치 900g 3종 세트", 18900L, "KRW", "음식")
        assertNotNull(kimchiSet)
        assertEquals("100g당 700원", kimchiSet?.label)

        val eggs = computeUnitPriceBadge("동물복지 계란 특란 30구 x 2판", 15800L, "KRW", "음식")
        assertNotNull(eggs)
        assertEquals("1구당 263원", eggs?.label)
        assertEquals("총 60구 기준", eggs?.detail)

        assertNull(computeUnitPriceBadge("[알리] 8구 개별스위치 멀티탭 2M", 12900L, "KRW", "생활용품"))

        val pizzaCombo = computeUnitPriceBadge("애슐리 딥디쉬 피자 시카고2+고구마2", 19900L, "KRW", "음식")
        assertNotNull(pizzaCombo)
        assertEquals("개당 4,975원", pizzaCombo?.label)
        assertEquals("총 4개 기준", pizzaCombo?.detail)

        val mist60ml = computeUnitPriceBadge("[네이버] 레스노베 워터 앰플 미스트60ml", 4900L, "KRW", "뷰티")
        assertNotNull(mist60ml)
        assertEquals("10ml당 817원", mist60ml?.label)
        assertEquals("총 60ml 기준", mist60ml?.detail)

        val giftVoucher = computeUnitPriceBadge("와인25플러스 모바일상품권 5만원권", 46560L, "KRW", "모바일/기프티콘")
        assertNotNull(giftVoucher)
        assertEquals("약 6.9% 할인", giftVoucher?.label)
        assertEquals("액면가 50,000원 기준", giftVoucher?.detail)

        // v1.1e 회귀: 영양성분 22g 무시 -> 개당 1,056원, 패션 중량/비액체 20L 차단, 패션 5켤레 유지, 세럼 50ml 1+1
        val himune = computeUnitPriceBadge("하이뮨 프로틴 밸런스 액티브 22g 바닐라봉봉 ZERO 18개", 19000L, "KRW", "음식")
        assertNotNull(himune)
        assertEquals("개당 1,056원", himune?.label)
        assertEquals("총 18개 기준", himune?.detail)

        assertNull(computeUnitPriceBadge("노스페이스 구스다운 300g", 199000L, "KRW", "패션"))
        assertNull(computeUnitPriceBadge("종량제 쓰레기봉투 20L 100매", 12000L, "KRW", "생활용품"))

        val socks5 = computeUnitPriceBadge("양말 크루삭스 5켤레", 10000L, "KRW", "패션")
        assertNotNull(socks5)
        assertEquals("1켤레당 2,000원", socks5?.label)
        assertEquals("총 5켤레 기준", socks5?.detail)

        val serum = computeUnitPriceBadge("세럼 50ml 1+1", 19000L, "KRW", "화장품")
        assertNotNull(serum)
        assertEquals("10ml당 1,900원", serum?.label)
        assertEquals("총 100ml · 개당 9,500원", serum?.detail)

        assertEquals("네이버페이 조건", extractPaymentConditionBadge("[던킨도너츠] 네이버페이 결제시 50% 할인", ""))
        assertEquals("우리카드 조건", extractPaymentConditionBadge("[컴퓨존] 스위치2 포켓몬 레전드 ZA (우리카드)", ""))
        assertEquals("네멤 조건", extractPaymentConditionBadge("퍼실 세탁세제", "네멤무료"))

        // kakaocdn 저해상도 및 404 빈발 656x0_80_wr 썸네일 치유 검증
        assertEquals(
            "https://search1.kakaocdn.net/argon/600x0_65_wr/6W6ac7UeBi1",
            ImageUrlResolver.upgradeLowresImageUrl("https://search1.kakaocdn.net/argon/130x130_85_c/6W6ac7UeBi1")
        )
        assertEquals(
            "https://search2.kakaocdn.net/argon/600x0_65_wr/Ij9QZhtv6N0",
            ImageUrlResolver.upgradeLowresImageUrl("https://search2.kakaocdn.net/argon/656x0_80_wr/Ij9QZhtv6N0")
        )
    }

    @Test
    fun v11f_mallNormalizationAndPaymentBadges_matchCrossPlatformRules() {
        assertEquals("카카오쇼핑", com.ddaeany0919.insightdeal.presentation.normalizeMallBadgeName("[카카오톡쇼핑하기]"))
        assertEquals("CJ온스타일", com.ddaeany0919.insightdeal.presentation.normalizeMallBadgeName("cj온"))
        assertEquals("엑스박스", com.ddaeany0919.insightdeal.presentation.normalizeMallBadgeName("엑박"))

        assertEquals("엑스박스", com.ddaeany0919.insightdeal.presentation.inferMallFromUrl("https://www.xbox.com/ko-KR/games/store/foo"))
        assertEquals("카카오쇼핑", com.ddaeany0919.insightdeal.presentation.inferMallFromUrl("https://store.kakao.com/brand/products/123"))

        assertEquals("유니버스클럽 조건", extractPaymentConditionBadge("[G마켓] 생수 특가", "유클무료"))
        assertEquals("스마일클럽 조건", extractPaymentConditionBadge("[옥션] 휴지 특가", "스클무료"))

        val cleanedPromo = com.ddaeany0919.insightdeal.presentation.resolveShopBadgeAndCleanTitle(
            "[오늘끝딜 668400원] [네이버] 삼성공식파트너 JBL 사운드바",
            shopName = "네이버",
            ecommerceUrl = "https://brand.naver.com/samsung/products/123"
        )
        assertEquals("네이버", cleanedPromo.shopBadge)
        assertEquals("삼성공식파트너 JBL 사운드바", cleanedPromo.cleanTitle)
    }

    @OptIn(ExperimentalPagingApi::class)
    @Test
    fun v11f_hotDealsRemoteMediator_initializeHonorsFiveMinuteRefreshTtl() = runBlocking {
        HotDealsRemoteMediator.clearRefreshCache()
        var nowMs = 1_000_000L
        val server = LocalStubHttpServer {
            200 to """{
                "deals": [{"id": 700, "title": "TTL 테스트 딜", "price": 12900, "site_name": "뽐뿌"}],
                "has_more": false,
                "next_cursor": null
            }"""
        }.also { resourcesToClose += it }

        val dealDao = InMemoryDealDao()
        val keysDao = InMemoryDealRemoteKeysDao()
        val mediator = HotDealsRemoteMediator(
            dealDao = dealDao,
            dealRemoteKeysDao = keysDao,
            runInTransaction = { block -> block() },
            apiService = createApiService(server.baseUrl),
            category = null,
            keyword = null,
            platform = null,
            sort = "latest",
            clockMs = { nowMs }
        )

        assertEquals(RemoteMediator.InitializeAction.LAUNCH_INITIAL_REFRESH, mediator.initialize())

        val refresh = mediator.load(LoadType.REFRESH, buildPagingState(emptyList()))
        assertTrue(refresh is RemoteMediator.MediatorResult.Success)

        nowMs += 60_000L
        assertEquals(RemoteMediator.InitializeAction.SKIP_INITIAL_REFRESH, mediator.initialize())

        nowMs += 300_000L
        assertEquals(RemoteMediator.InitializeAction.LAUNCH_INITIAL_REFRESH, mediator.initialize())

        HotDealsRemoteMediator.clearRefreshCache()
    }
}

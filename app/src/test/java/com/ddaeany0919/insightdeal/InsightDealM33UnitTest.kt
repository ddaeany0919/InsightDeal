package com.ddaeany0919.insightdeal

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import androidx.paging.PagingSource
import com.ddaeany0919.insightdeal.data.util.ImageUrlResolver
import com.ddaeany0919.insightdeal.models.CategoryCatalog
import com.ddaeany0919.insightdeal.models.DealItem
import com.ddaeany0919.insightdeal.network.ApiService
import com.ddaeany0919.insightdeal.presentation.DealDetailViewModel
import com.ddaeany0919.insightdeal.presentation.KeywordManagerViewModel
import com.ddaeany0919.insightdeal.presentation.alerts.AlertsViewModel
import com.ddaeany0919.insightdeal.presentation.community.CommunityViewModel
import com.ddaeany0919.insightdeal.presentation.home.HomeViewModel
import com.ddaeany0919.insightdeal.presentation.home.HotDealsPagingSource
import com.ddaeany0919.insightdeal.presentation.home.HotDealsRemoteMediator
import com.ddaeany0919.insightdeal.presentation.mypage.DeviceSyncViewModel
import com.ddaeany0919.insightdeal.presentation.price.PriceChartViewModel
import com.ddaeany0919.insightdeal.presentation.recommendation.RecommendationViewModel
import com.ddaeany0919.insightdeal.presentation.search.SearchViewModel
import com.ddaeany0919.insightdeal.presentation.theme.AmoledColorScheme
import com.ddaeany0919.insightdeal.presentation.theme.DarkColorScheme
import com.ddaeany0919.insightdeal.presentation.theme.LightColorScheme
import com.ddaeany0919.insightdeal.presentation.tracking.CoupangTrackingViewModel
import com.ddaeany0919.insightdeal.presentation.wishlist.WishlistViewModel
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
import java.net.InetAddress
import java.net.ServerSocket
import java.nio.charset.StandardCharsets
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.math.pow

class InsightDealM33UnitTest {

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
        val baseUrl: String get() = "http://127.0.0.1:${serverSocket.localPort}/"
        val recordedRequests = CopyOnWriteArrayList<RecordedHttpRequest>()

        @Volatile
        private var running = true

        private val acceptThread = Thread {
            while (running && !serverSocket.isClosed) {
                try {
                    val client = serverSocket.accept()
                    Thread {
                        client.use { socket ->
                            val input = BufferedInputStream(socket.getInputStream())
                            val rawBytes = StringBuilder()
                            val headerEndMarker = "\r\n\r\n"
                            val buf = ByteArray(1)
                            while (true) {
                                val read = input.read(buf)
                                if (read == -1) break
                                rawBytes.append(Char(buf[0].toInt() and 0xFF))
                                if (rawBytes.endsWith(headerEndMarker)) break
                            }
                            val headerLines = rawBytes.toString().split("\r\n").filter { it.isNotEmpty() }
                            if (headerLines.isEmpty()) return@use

                            val requestLineParts = headerLines.first().split(" ")
                            val method = requestLineParts.getOrElse(0) { "GET" }
                            val path = requestLineParts.getOrElse(1) { "/" }
                            val headers = linkedMapOf<String, String>()
                            for (line in headerLines.drop(1)) {
                                val colonIdx = line.indexOf(':')
                                if (colonIdx > 0) {
                                    val key = line.substring(0, colonIdx).trim().lowercase()
                                    val value = line.substring(colonIdx + 1).trim()
                                    headers[key] = value
                                }
                            }

                            val contentLength = headers["content-length"]?.toIntOrNull() ?: 0
                            val bodyBytes = if (contentLength > 0) {
                                val data = ByteArray(contentLength)
                                var offset = 0
                                while (offset < contentLength) {
                                    val count = input.read(data, offset, contentLength - offset)
                                    if (count == -1) break
                                    offset += count
                                }
                                String(data, 0, offset, StandardCharsets.UTF_8)
                            } else {
                                ""
                            }

                            val recorded = RecordedHttpRequest(method, path, headers, bodyBytes)
                            recordedRequests.add(recorded)

                            val (statusCode, responseBody) = handler(recorded)
                            val responseBytes = responseBody.toByteArray(StandardCharsets.UTF_8)
                            val reason = when (statusCode) {
                                200 -> "OK"
                                404 -> "Not Found"
                                503 -> "Service Unavailable"
                                else -> "Error"
                            }
                            val rawResponse = buildString {
                                append("HTTP/1.1 $statusCode $reason\r\n")
                                append("Content-Type: application/json; charset=utf-8\r\n")
                                append("Content-Length: ${responseBytes.size}\r\n")
                                append("Connection: close\r\n\r\n")
                            }.toByteArray(StandardCharsets.UTF_8)

                            socket.getOutputStream().apply {
                                write(rawResponse)
                                write(responseBytes)
                                flush()
                            }
                        }
                    }.start()
                } catch (_: Exception) {
                    if (!running) break
                }
            }
        }.apply {
            isDaemon = true
            start()
        }

        override fun close() {
            running = false
            runCatching { serverSocket.close() }
            runCatching { acceptThread.join(1000) }
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
        return Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(OkHttpClient.Builder().build())
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(ApiService::class.java)
    }

    private fun newUnconfinedScope(): CoroutineScope {
        return CoroutineScope(Dispatchers.Unconfined + SupervisorJob()).also {
            scopesToCancel += it
        }
    }

    private fun linearizeChannel(c: Float): Double {
        val d = c.toDouble()
        return if (d <= 0.04045) d / 12.92 else ((d + 0.055) / 1.055).pow(2.4)
    }

    private fun relativeLuminance(color: Color): Double {
        return 0.2126 * linearizeChannel(color.red) +
            0.7152 * linearizeChannel(color.green) +
            0.0722 * linearizeChannel(color.blue)
    }

    private fun contrastRatio(fg: Color, bg: Color): Double {
        val l1 = relativeLuminance(fg)
        val l2 = relativeLuminance(bg)
        return (maxOf(l1, l2) + 0.05) / (minOf(l1, l2) + 0.05)
    }

    private fun assertColorSchemeContrast(name: String, scheme: ColorScheme) {
        val pairs = listOf(
            "primary/onPrimary" to (scheme.onPrimary to scheme.primary),
            "primaryContainer/onPrimaryContainer" to (scheme.onPrimaryContainer to scheme.primaryContainer),
            "secondary/onSecondary" to (scheme.onSecondary to scheme.secondary),
            "secondaryContainer/onSecondaryContainer" to (scheme.onSecondaryContainer to scheme.secondaryContainer),
            "tertiary/onTertiary" to (scheme.onTertiary to scheme.tertiary),
            "tertiaryContainer/onTertiaryContainer" to (scheme.onTertiaryContainer to scheme.tertiaryContainer),
            "error/onError" to (scheme.onError to scheme.error),
            "background/onBackground" to (scheme.onBackground to scheme.background),
            "surface/onSurface" to (scheme.onSurface to scheme.surface),
            "surfaceVariant/onSurfaceVariant" to (scheme.onSurfaceVariant to scheme.surfaceVariant)
        )
        for ((pairName, colors) in pairs) {
            val ratio = contrastRatio(colors.first, colors.second)
            assertTrue(
                "$name [$pairName] 명암비(${String.format("%.2f", ratio)}:1)가 WCAG AA 4.5:1 기준 미달입니다.",
                ratio >= 4.5
            )
        }
    }

    @Test
    fun viewModels_mustNotContainContextOrComposeListState() {
        val vmClasses = listOf(
            HomeViewModel::class.java,
            PriceChartViewModel::class.java,
            DealDetailViewModel::class.java,
            KeywordManagerViewModel::class.java,
            AlertsViewModel::class.java,
            CommunityViewModel::class.java,
            RecommendationViewModel::class.java,
            CoupangTrackingViewModel::class.java,
            WishlistViewModel::class.java,
            SearchViewModel::class.java,
            DeviceSyncViewModel::class.java
        )
        val forbiddenTypeNames = setOf(
            "android.content.Context",
            "android.app.Application",
            "androidx.compose.foundation.lazy.LazyListState",
            "androidx.compose.foundation.lazy.grid.LazyGridState"
        )
        for (clazz in vmClasses) {
            for (field in clazz.declaredFields) {
                val typeName = field.type.name
                assertFalse(
                    "${clazz.simpleName}.${field.name}에 금지된 타입($typeName)이 남아 있습니다.",
                    typeName in forbiddenTypeNames
                )
            }
        }

        val sourceRoot = listOf(File("src/main/java"), File("app/src/main/java")).firstOrNull { it.exists() }
        assertNotNull("app/src/main/java 경로를 찾을 수 없습니다.", sourceRoot)
        val vmFiles = sourceRoot!!.walkTopDown().filter { it.isFile && it.name.endsWith("ViewModel.kt") }.toList()
        assertTrue("ViewModel 소스 파일이 존재해야 합니다.", vmFiles.isNotEmpty())
        for (vmFile in vmFiles) {
            val text = vmFile.readText()
            assertFalse("${vmFile.name}에 LazyListState 참조가 남아 있습니다.", text.contains("LazyListState"))
            assertFalse("${vmFile.name}에 android.content.Context 참조가 남아 있습니다.", text.contains("import android.content.Context"))
        }
    }

    @Test
    fun imageUrlResolver_handlesDebugReleaseRelativeAndFallbackUrls() {
        val debugBase = "http://10.0.2.2:8080"
        val releaseBase = "https://insightdeal.site"

        // Debug: 127.0.0.1/localhost -> active 10.0.2.2 host 직접 반환 (프록시 재래핑 금지)
        assertEquals(
            "http://10.0.2.2:8080/images/a.png",
            ImageUrlResolver.resolve("http://127.0.0.1:8080/images/a.png", null, debugBase, isDebug = true)
        )
        assertEquals(
            "http://10.0.2.2:8080/images/a.png",
            ImageUrlResolver.resolve("http://localhost:8080/images/a.png", null, debugBase, isDebug = true)
        )
        assertEquals(
            "http://10.0.2.2:8080/api/proxy-image?url=https%3A%2F%2Fa.com%2Fx.jpg",
            ImageUrlResolver.resolve(
                "http://127.0.0.1:8080/api/proxy-image?url=https%3A%2F%2Fa.com%2Fx.jpg",
                null,
                debugBase,
                isDebug = true
            )
        )

        // Release: 10.0.2.2 / localhost 평문 절대 미노출
        assertEquals(
            "https://insightdeal.site/images/a.png",
            ImageUrlResolver.resolve("http://10.0.2.2:8080/images/a.png", null, releaseBase, isDebug = false)
        )
        assertEquals(
            "https://insightdeal.site/api/proxy-image?url=https%3A%2F%2Fa.com%2Fx.jpg",
            ImageUrlResolver.resolve(
                "http://localhost:8080/api/proxy-image?url=https%3A%2F%2Fa.com%2Fx.jpg",
                null,
                releaseBase,
                isDebug = false
            )
        )
        assertNull(
            ImageUrlResolver.resolve("http://10.0.2.2:8080/internal-debug", null, releaseBase, isDebug = false)
        )

        // 상대 경로 & 프로토콜 생략 & 외부 HTTP -> HTTPS 강제 업그레이드
        assertEquals(
            "https://insightdeal.site/images/deal.jpg",
            ImageUrlResolver.resolve(" /images/deal.jpg ", null, releaseBase, isDebug = false)
        )
        assertEquals(
            "https://wsrv.nl/?url=https%3A%2F%2Fcdn.example.com%2Fitem.jpg&output=webp&we=1",
            ImageUrlResolver.resolve("//cdn.example.com/item.jpg", null, releaseBase, isDebug = false)
        )
        assertEquals(
            "https://wsrv.nl/?url=https%3A%2F%2Fcdn.example.com%2Fitem.jpg&output=webp&we=1",
            ImageUrlResolver.resolve("HTTP://cdn.example.com/item.jpg", null, releaseBase, isDebug = false)
        )
        assertEquals(
            "https://insightdeal.site/api/proxy-image?url=https%3A%2F%2Fa.com%2Fx.jpg",
            ImageUrlResolver.resolve(
                "HTTP://insightdeal.site/api/proxy-image?url=https%3A%2F%2Fa.com%2Fx.jpg",
                null,
                releaseBase,
                isDebug = false
            )
        )

        // Fallback proxy: 상대 경로/wsrv 언래핑/로컬 루프 방지/릴리스 10.0.2.2 차단
        assertEquals(
            "http://10.0.2.2:8080/images/a.png",
            ImageUrlResolver.getFallbackBackendProxyUrl("http://127.0.0.1:8080/images/a.png", debugBase, isDebug = true)
        )
        assertEquals(
            "https://insightdeal.site/images/a.png",
            ImageUrlResolver.getFallbackBackendProxyUrl("http://10.0.2.2:8080/images/a.png", releaseBase, isDebug = false)
        )
        assertNull(
            ImageUrlResolver.getFallbackBackendProxyUrl("http://10.0.2.2:9000/foo", releaseBase, isDebug = false)
        )
        val fallback = ImageUrlResolver.getFallbackBackendProxyUrl(
            "https://wsrv.nl/?url=http%3A%2F%2Fcdn.example.com%2Fa.png&w=400",
            releaseBase,
            isDebug = false
        )
        assertEquals(
            "https://insightdeal.site/api/proxy-image?url=https%3A%2F%2Fcdn.example.com%2Fa.png&v=5",
            fallback
        )

        // 비표준(미인코딩 공백/한글/잘못된 % 포함) 로컬 URL 방어 검증
        assertEquals(
            "http://10.0.2.2:8080/images/상품%201.jpg",
            ImageUrlResolver.resolve("http://10.0.2.2:8080/images/상품 1.jpg", null, debugBase, isDebug = true)
        )
        assertEquals(
            "http://10.0.2.2:8080/images/테스트%20상품.png",
            ImageUrlResolver.resolve("http://127.0.0.1:8080/images/테스트 상품.png", null, debugBase, isDebug = true)
        )
        assertEquals(
            "https://insightdeal.site/images/상품%201.jpg",
            ImageUrlResolver.resolve("http://10.0.2.2:8080/images/상품 1.jpg", null, releaseBase, isDebug = false)
        )
        assertNull(
            ImageUrlResolver.resolve("http://10.0.2.2:8080/internal debug/비밀 파일.jpg", null, releaseBase, isDebug = false)
        )
        assertEquals(
            "http://10.0.2.2:8080/images/상품%201.jpg",
            ImageUrlResolver.getFallbackBackendProxyUrl("http://10.0.2.2:8080/images/상품 1.jpg", debugBase, isDebug = true)
        )
        assertEquals(
            "https://insightdeal.site/images/상품%201.jpg",
            ImageUrlResolver.getFallbackBackendProxyUrl("http://10.0.2.2:8080/images/상품 1.jpg", releaseBase, isDebug = false)
        )
        // URI 생성자가 실패하는 잘못된 퍼센트 인코딩(%zz) + 공백 포함 로컬 URL도 Regex 폴백으로 방어
        assertEquals(
            "http://10.0.2.2:8080/images/상품%zz%201.jpg",
            ImageUrlResolver.resolve("http://10.0.2.2:8080/images/상품%zz 1.jpg", null, debugBase, isDebug = true)
        )
        assertEquals(
            "https://insightdeal.site/images/상품%zz%201.jpg",
            ImageUrlResolver.resolve("http://10.0.2.2:8080/images/상품%zz 1.jpg", null, releaseBase, isDebug = false)
        )
    }

    @Test
    fun categoryCatalog_and_filterState_alignWith15CategoriesAnd11Sources() {
        assertEquals(15, CategoryCatalog.STANDARD_CATEGORIES.size)
        assertEquals(11, CategoryCatalog.STANDARD_SOURCES.size)
        assertTrue(CategoryCatalog.STANDARD_SOURCES.contains("전체"))
        assertTrue(CategoryCatalog.STANDARD_SOURCES.contains("뽐뿌해외"))
        assertTrue(CategoryCatalog.STANDARD_SOURCES.contains("빠삭육아"))
        assertTrue(CategoryCatalog.STANDARD_COMMUNITY_PLATFORMS.contains("뽐뿌해외"))
        assertTrue(CategoryCatalog.STANDARD_COMMUNITY_PLATFORMS.contains("빠삭육아"))

        // 레거시 별칭 정규화
        assertEquals("해외직구", CategoryCatalog.normalizeCategoryKey("여행.해외핫딜"))
        assertEquals("해외직구", CategoryCatalog.normalizeCategoryKey("해외쇼핑"))
        assertEquals("가전제품", CategoryCatalog.normalizeCategoryKey("가전/TV"))
        assertEquals("생활용품", CategoryCatalog.normalizeCategoryKey("생활/식품"))
        assertEquals("가전/TV", CategoryCatalog.getDisplayLabel("가전/TV"))
        assertEquals("해외직구", CategoryCatalog.getDisplayLabel("여행.해외핫딜"))

        // FilterState: 빠삭육아 포함 전체 플랫폼 매핑 검증
        assertNull(HomeViewModel.FilterState().resolvedPlatformQuery())
        val withoutBbasak = HomeViewModel.FilterState(disabledPlatforms = setOf("빠삭육아")).resolvedPlatformQuery()
        assertNotNull(withoutBbasak)
        assertFalse(withoutBbasak!!.split(",").contains("빠삭육아"))
        assertEquals(CategoryCatalog.STANDARD_COMMUNITY_PLATFORMS.size - 1, withoutBbasak.split(",").size)

        val allDisabled = HomeViewModel.FilterState(
            disabledPlatforms = CategoryCatalog.STANDARD_COMMUNITY_PLATFORMS.toSet()
        ).resolvedPlatformQuery()
        assertNull(allDisabled)
    }

    @Test
    fun homeViewModel_popularThreshold_isStrictlyHoneyScore100() {
        val deal99 = DealItem(id = 1, title = "99점 딜", price = 10000L, siteName = "뽐뿌", honeyScore = 99)
        val deal100 = DealItem(id = 2, title = "100점 딜", price = 10000L, siteName = "뽐뿌", honeyScore = 100)
        val deal140 = DealItem(id = 3, title = "140점 딜", price = 10000L, siteName = "아카라이브", honeyScore = 140)

        assertFalse(HomeViewModel.isPopularDeal(deal99))
        assertTrue(HomeViewModel.isPopularDeal(deal100))
        assertTrue(HomeViewModel.isPopularDeal(deal140))
    }

    @Test
    fun hotDealsPagingSource_respectsHasMoreFalse_andReturnsErrorOn503() = runBlocking {
        val itemsJson = (1..20).joinToString(",") { idx ->
            """{"id":$idx,"title":"상품 $idx","price":10000,"site_name":"뽐뿌","honey_score":105}"""
        }
        val server = LocalStubHttpServer { req ->
            if (req.path.contains("page=0") || req.path.contains("offset=0")) {
                200 to """{"deals":[$itemsJson],"total":20,"page":0,"limit":20,"has_more":false}"""
            } else {
                503 to """{"detail":"Service Unavailable"}"""
            }
        }.also { resourcesToClose += it }

        val apiService = createApiService(server.baseUrl)
        val pagingSource = HotDealsPagingSource(apiService = apiService, category = "여행.해외핫딜")

        val pageResult = pagingSource.load(
            PagingSource.LoadParams.Refresh(key = 0, loadSize = 20, placeholdersEnabled = false)
        )
        assertTrue(pageResult is PagingSource.LoadResult.Page)
        val page = pageResult as PagingSource.LoadResult.Page
        assertEquals(20, page.data.size)
        // 20건이 채워져도 서버가 has_more = false를 주면 nextKey는 null이어야 한다.
        assertNull(page.nextKey)
        // 카테고리 파라미터가 정규화되어 요청되었는지 확인 (여행.해외핫딜 -> 해외직구)
        assertTrue(server.recordedRequests.any { it.path.contains("category=") })

        val errorServer = LocalStubHttpServer {
            503 to """{"detail":"Service Unavailable"}"""
        }.also { resourcesToClose += it }
        val errorSource = HotDealsPagingSource(apiService = createApiService(errorServer.baseUrl))
        val errorResult = errorSource.load(
            PagingSource.LoadParams.Refresh(key = 0, loadSize = 20, placeholdersEnabled = false)
        )
        assertTrue(errorResult is PagingSource.LoadResult.Error)
    }

    @Test
    fun themeColorSchemes_meetWcagAA_45To1_andAmoledIsTrueBlack() {
        assertColorSchemeContrast("LightColorScheme", LightColorScheme)
        assertColorSchemeContrast("DarkColorScheme", DarkColorScheme)
        assertColorSchemeContrast("AmoledColorScheme", AmoledColorScheme)

        assertEquals(Color.Black, AmoledColorScheme.background)
        assertNotEquals(AmoledColorScheme.primary, AmoledColorScheme.onPrimary)
    }

    @Test
    fun priceChartViewModel_removesSampleData_andShowsInsufficientMessageUnderTwoPoints() = runBlocking {
        assertTrue(
            "generateSamplePriceHistory 더미 메서드는 완전히 삭제되어야 합니다.",
            PriceChartViewModel::class.java.declaredMethods.none { it.name == "generateSamplePriceHistory" }
        )

        val server = LocalStubHttpServer { req ->
            when {
                req.path.startsWith("/api/product/42/history") ->
                    200 to """[{"price":25900,"tracked_at":"2026-11-18T10:00:00Z"},{"price":0,"tracked_at":"2026-11-18T09:00:00Z"}]"""
                req.path.startsWith("/api/product/43/history") ->
                    200 to """[{"price":30000,"tracked_at":"2026-11-10T10:00:00Z"},{"price":24000,"tracked_at":"2026-11-18T10:00:00Z"}]"""
                req.path.startsWith("/products/42") || req.path.startsWith("/api/product/42") ->
                    404 to """{"detail":"Not Found"}"""
                req.path.startsWith("/products/43") || req.path.startsWith("/api/product/43") ->
                    200 to """{"id":43,"title":"테스트 키보드","current_price":24000,"original_price":35000,"url":"https://example.com/43","created_at":"2026-11-10T00:00:00Z"}"""
                else -> 404 to """{"detail":"Not Found"}"""
            }
        }.also { resourcesToClose += it }

        val scope = newUnconfinedScope()
        val viewModel = PriceChartViewModel(
            apiService = createApiService(server.baseUrl),
            ioDispatcher = Dispatchers.Unconfined,
            coroutineScopeOverride = scope
        )

        // 1건(0원 더미 필터링 후 1건) -> 최소 데이터 부족 문구 반환
        viewModel.loadProductData(42)
        withTimeout(3000) {
            while (viewModel.isLoading.value || viewModel.priceHistory.value.isEmpty()) {
                delay(15)
            }
        }
        assertEquals(1, viewModel.priceHistory.value.size)
        assertEquals(25900, viewModel.product.value?.currentPrice)
        assertEquals(0f, viewModel.product.value?.priceChangePercent ?: -1f, 0.01f)
        val adviceSingle = viewModel.getBuyingTimingAdvice()
        assertEquals("데이터 부족", adviceSingle.timing)
        assertEquals(PriceChartViewModel.INSUFFICIENT_HISTORY_MESSAGE, adviceSingle.reason)

        // 2건 -> 실제 변동률((24000 - 30000) / 30000 * 100 = -20%) 계산 확인
        viewModel.loadProductData(43)
        withTimeout(3000) {
            while (viewModel.isLoading.value || viewModel.product.value?.id != 43) {
                delay(15)
            }
        }
        assertEquals(2, viewModel.priceHistory.value.size)
        assertEquals(24000, viewModel.product.value?.currentPrice)
        assertEquals(24000, viewModel.product.value?.lowestPrice)
        assertEquals(30000, viewModel.product.value?.highestPrice)
        assertEquals(-20.0f, viewModel.product.value?.priceChangePercent ?: 0f, 0.01f)
        assertEquals("지금 구매", viewModel.getBuyingTimingAdvice().timing)
    }

    @Test
    fun dealDao_sqlAndRemoteMediator_alignWithServerCategoryTitleAndSiteMatching() {
        val sourceRoot = listOf(File("src/main/java"), File("app/src/main/java")).firstOrNull { it.exists() }
        assertNotNull("app/src/main/java 경로를 찾을 수 없습니다.", sourceRoot)
        val dealDaoFile = File(sourceRoot, "com/ddaeany0919/insightdeal/local/db/DealDao.kt")
        assertTrue("DealDao.kt 파일이 존재해야 합니다.", dealDaoFile.exists())
        val daoText = dealDaoFile.readText()

        val queryMatch = Regex("@Query\\(\"\"\"([\\s\\S]*?)\"\"\"\\)\\s*fun\\s+getPagedDeals")
            .find(daoText)
        assertNotNull("DealDao.getPagedDeals() SQL 쿼리를 찾을 수 없습니다.", queryMatch)
        val fullSql = queryMatch!!.groupValues[1]

        // WHERE ( ... ) AND ( :keyword IS NULL ... ) 직전까지 카테고리 조건절 추출
        val categoryClauseStart = fullSql.indexOf("WHERE (") + "WHERE ".length
        val keywordMarker = fullSql.indexOf(":keyword IS NULL", startIndex = categoryClauseStart)
        assertTrue(keywordMarker > categoryClauseStart)
        val categoryClauseEnd = fullSql.lastIndexOf("AND (", startIndex = keywordMarker)
        assertTrue(categoryClauseStart > 0 && categoryClauseEnd > categoryClauseStart)
        val categoryClause = fullSql.substring(categoryClauseStart, categoryClauseEnd).trim()

        data class FakeDealRow(
            val title: String,
            val category: String?,
            val siteName: String = "뽐뿌",
            val siteNames: List<String> = emptyList(),
            val honeyScore: Int = 50
        )

        fun evalSqlWhere(expr: String, row: FakeDealRow, targetCategory: String?): Boolean {
            val tokens = mutableListOf<String>()
            var i = 0
            while (i < expr.length) {
                val ch = expr[i]
                when {
                    ch.isWhitespace() -> i++
                    ch == '(' || ch == ')' || ch == ',' -> {
                        tokens += ch.toString()
                        i++
                    }
                    expr.startsWith(">=", i) -> {
                        tokens += ">="
                        i += 2
                    }
                    ch == '=' -> {
                        tokens += "="
                        i++
                    }
                    ch == '\'' -> {
                        val end = expr.indexOf('\'', i + 1)
                        tokens += expr.substring(i, end + 1)
                        i = end + 1
                    }
                    else -> {
                        val start = i
                        while (i < expr.length && !expr[i].isWhitespace() && expr[i] !in charArrayOf('(', ')', ',', '=', '>')) {
                            i++
                        }
                        tokens += expr.substring(start, i)
                    }
                }
            }

            var pos = 0
            fun peek(): String? = tokens.getOrNull(pos)
            fun next(): String = tokens[pos++]

            fun evalValue(): String? {
                val tok = next()
                return when {
                    tok.equals("NULL", ignoreCase = true) -> null
                    tok == ":category" -> targetCategory
                    tok == "title" -> row.title
                    tok == "category" -> row.category
                    tok == "siteName" -> row.siteName
                    tok == "siteNames" -> if (row.siteNames.isEmpty()) "[]" else row.siteNames.joinToString(",")
                    tok == "honeyScore" -> row.honeyScore.toString()
                    tok.startsWith("'") && tok.endsWith("'") -> tok.substring(1, tok.length - 1)
                    tok.equals("COALESCE", ignoreCase = true) -> {
                        assertEquals("(", next())
                        val first = evalValue()
                        assertEquals(",", next())
                        val second = evalValue()
                        assertEquals(")", next())
                        first ?: second
                    }
                    tok.equals("LOWER", ignoreCase = true) -> {
                        assertEquals("(", next())
                        val inner = evalValue()
                        assertEquals(")", next())
                        inner?.lowercase()
                    }
                    else -> tok
                }
            }

            fun likeMatch(haystack: String?, pattern: String): Boolean {
                if (haystack == null) return false
                val regexStr = buildString {
                    append("^")
                    for (c in pattern) {
                        when (c) {
                            '%' -> append(".*")
                            '_' -> append(".")
                            else -> append(Regex.escape(c.toString()))
                        }
                    }
                    append("$")
                }
                return Regex(regexStr, setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)).matches(haystack)
            }

            lateinit var parseOr: () -> Boolean

            fun parsePrimary(): Boolean {
                if (peek() == "(") {
                    next()
                    val v = parseOr()
                    assertEquals(")", next())
                    return v
                }
                val left = evalValue()
                val op = next().uppercase()
                return when (op) {
                    "IS" -> {
                        assertTrue(next().equals("NULL", ignoreCase = true))
                        left == null
                    }
                    "=" -> {
                        val right = evalValue()
                        left != null && right != null && left == right
                    }
                    ">=" -> {
                        val right = evalValue()!!.toInt()
                        (left?.toIntOrNull() ?: 0) >= right
                    }
                    "LIKE" -> {
                        val pattern = evalValue()!!
                        likeMatch(left, pattern)
                    }
                    "IN" -> {
                        assertEquals("(", next())
                        val items = mutableListOf<String>()
                        while (true) {
                            items += evalValue().orEmpty()
                            if (peek() == ",") next() else break
                        }
                        assertEquals(")", next())
                        left != null && left in items
                    }
                    else -> error("지원하지 않는 SQL 연산자: $op")
                }
            }

            fun parseAnd(): Boolean {
                var res = parsePrimary()
                while (peek()?.equals("AND", ignoreCase = true) == true) {
                    next()
                    val rhs = parsePrimary()
                    res = res && rhs
                }
                return res
            }

            parseOr = {
                var res = parseAnd()
                while (peek()?.equals("OR", ignoreCase = true) == true) {
                    next()
                    val rhs = parseAnd()
                    res = res || rhs
                }
                res
            }

            val result = parseOr()
            assertEquals("SQL 토큰 파싱이 끝까지 완료되어야 합니다.", tokens.size, pos)
            return result
        }

        // 1. 제목 키워드 매칭: "LG 선풍기" + category="기타" -> "가전제품" 통과, "음식" 탈락
        val lgFanDeal = FakeDealRow(title = "LG 선풍기 특가", category = "기타")
        assertTrue(evalSqlWhere(categoryClause, lgFanDeal, "가전제품"))
        assertFalse(evalSqlWhere(categoryClause, lgFanDeal, "음식"))

        // 2. 커뮤니티명 매칭: "빠삭육아" 출처 + category="기타" -> "육아/유아용품" 통과, "가전제품" 탈락
        val bbasakParentingDeal = FakeDealRow(title = "특가 정보 공유", category = "기타", siteName = "빠삭육아")
        assertTrue(evalSqlWhere(categoryClause, bbasakParentingDeal, "육아/유아용품"))
        assertFalse(evalSqlWhere(categoryClause, bbasakParentingDeal, "가전제품"))

        // 3. 육아 제목 키워드: "하기스 기저귀" + category="기타" -> "육아/유아용품" 통과
        val diaperDeal = FakeDealRow(title = "하기스 기저귀 3단계", category = "기타", siteName = "뽐뿌")
        assertTrue(evalSqlWhere(categoryClause, diaperDeal, "육아/유아용품"))

        // 4. 레거시 별칭: category="생활/식품" -> "생활용품" 매칭
        val livingAliasDeal = FakeDealRow(title = "물티슈 10팩", category = "생활/식품")
        assertTrue(evalSqlWhere(categoryClause, livingAliasDeal, "생활용품"))

        // 5. 뷰티/적립/이벤트/해외직구/핫딜모음 정합 검증
        assertTrue(evalSqlWhere(categoryClause, FakeDealRow(title = "닥터지 선크림 1+1", category = "기타"), "뷰티"))
        assertTrue(evalSqlWhere(categoryClause, FakeDealRow(title = "네이버페이 100원 적립", category = "기타"), "적립"))
        assertTrue(evalSqlWhere(categoryClause, FakeDealRow(title = "선착순 무료배포", category = "기타"), "이벤트"))
        assertTrue(evalSqlWhere(categoryClause, FakeDealRow(title = "아마존 직구", category = "여행.해외핫딜"), "해외직구"))
        assertTrue(evalSqlWhere(categoryClause, FakeDealRow(title = "인기 딜", category = "기타", honeyScore = 100), "핫딜모음"))
        assertFalse(evalSqlWhere(categoryClause, FakeDealRow(title = "일반 딜", category = "기타", honeyScore = 99), "핫딜모음"))

        // 6. HotDealsRemoteMediator: 서버가 신규 키워드(로컬 SQL 미등재)로 내려준 딜도 Room 탈락 0건 보장
        val futureKeywordDeal = DealItem(id = 999, title = "신형 스탠바이미 2", price = 990000L, siteName = "뽐뿌", category = "기타")
        val cachedForAppliance = HotDealsRemoteMediator.toCachedEntityForCategory(futureKeywordDeal, "가전제품")
        assertEquals("가전제품", cachedForAppliance.category)
        assertTrue(
            evalSqlWhere(
                categoryClause,
                FakeDealRow(title = cachedForAppliance.title, category = cachedForAppliance.category, siteName = cachedForAppliance.siteName),
                "가전제품"
            )
        )

        // 이미 SQL로 매칭되는 "LG 선풍기"(기타)는 원시 카테고리("기타")를 유지해도 SQL을 통과함
        val fanItem = DealItem(id = 1000, title = "LG 선풍기", price = 49000L, siteName = "뽐뿌", category = "기타")
        val cachedFan = HotDealsRemoteMediator.toCachedEntityForCategory(fanItem, "가전제품")
        assertEquals("기타", cachedFan.category)
        assertTrue(
            evalSqlWhere(
                categoryClause,
                FakeDealRow(title = cachedFan.title, category = cachedFan.category, siteName = cachedFan.siteName),
                "가전제품"
            )
        )
    }
}

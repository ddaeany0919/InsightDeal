package com.ddaeany0919.insightdeal

import android.content.SharedPreferences
import androidx.compose.ui.graphics.Color
import androidx.paging.DifferCallback
import androidx.paging.NullPaddedList
import androidx.paging.PagingDataDiffer
import androidx.paging.PagingSource
import androidx.paging.PagingState
import androidx.sqlite.db.SupportSQLiteDatabase
import com.ddaeany0919.insightdeal.core.security.DeviceIdentityStore
import com.ddaeany0919.insightdeal.core.security.EncryptedDeviceIdentityStore
import com.ddaeany0919.insightdeal.data.FeedPreferencesDataSource
import com.ddaeany0919.insightdeal.data.LocalDataProvider
import com.ddaeany0919.insightdeal.local.db.AppDatabase
import com.ddaeany0919.insightdeal.local.db.DealDao
import com.ddaeany0919.insightdeal.local.db.DealEntity
import com.ddaeany0919.insightdeal.local.db.DealRemoteKeysDao
import com.ddaeany0919.insightdeal.local.db.DealRemoteKeysEntity
import com.ddaeany0919.insightdeal.local.db.KeywordDao
import com.ddaeany0919.insightdeal.local.db.KeywordEntity
import com.ddaeany0919.insightdeal.models.DealItem
import com.ddaeany0919.insightdeal.network.ApiService
import com.ddaeany0919.insightdeal.presentation.DealDetailViewModel
import com.ddaeany0919.insightdeal.presentation.KeywordManagerViewModel
import com.ddaeany0919.insightdeal.presentation.home.HomeViewModel
import com.ddaeany0919.insightdeal.presentation.home.HotDealsRemoteMediator
import com.ddaeany0919.insightdeal.presentation.price.PriceChartViewModel
import com.ddaeany0919.insightdeal.presentation.theme.AmoledColorScheme
import com.ddaeany0919.insightdeal.presentation.theme.DarkColorScheme
import com.ddaeany0919.insightdeal.presentation.theme.LightColorScheme
import com.google.gson.Gson
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
import java.util.Locale
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

/**
 * M4 마일스톤 통합 검증 단위 테스트 (T1 ~ T5)
 * - T1: t13 커뮤니티 '살까' 선택 배지 WCAG 2.2 AA 명암비 실측 및 소스 교체 검증
 * - T2: PriceGraphScreen MPAndroidChart -> PriceChart 교체, PriceChartViewModel 필터 경계, 레거시 의존성 0건 검증
 * - T3: HomeScreen 6종 정렬·서버 퀵필터·컴팩트 보기·뮤트 키워드·포그라운드 라이프사이클 배선 및 런타임 연동 검증
 * - T4: DealDetailScreen similarDeals & MainActivity 백스택 네비게이션 + KeywordManagerScreen/SettingsScreen 테스트 푸시 검증
 * - T5: ViewModel Context=0, 포트 8080 정합성, PriceComparison 배제 불변식 검증
 */
class InsightDealM4UnitTest {

    private data class RecordedHttpRequest(
        val method: String,
        val path: String,
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
                            404 -> "Not Found"
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
                if (b == -1) break
                headerBytes.add(b.toByte())
                val c = b.toChar()
                matched = when {
                    matched == 0 && c == '\r' -> 1
                    matched == 1 && c == '\n' -> 2
                    matched == 2 && c == '\r' -> 3
                    matched == 3 && c == '\n' -> 4
                    c == '\r' -> 1
                    else -> 0
                }
                if (matched == 4) break
            }
            if (headerBytes.isEmpty()) return null
            val headerText = String(headerBytes.toByteArray(), StandardCharsets.US_ASCII)
            val lines = headerText.split("\r\n").filter { it.isNotEmpty() }
            if (lines.isEmpty()) return null
            val parts = lines[0].split(" ")
            val method = parts.getOrElse(0) { "GET" }
            val path = parts.getOrElse(1) { "/" }
            var contentLength = 0
            for (i in 1 until lines.size) {
                val idx = lines[i].indexOf(':')
                if (idx > 0) {
                    val key = lines[i].substring(0, idx).trim().lowercase(Locale.US)
                    val value = lines[i].substring(idx + 1).trim()
                    if (key == "content-length") {
                        contentLength = value.toIntOrNull() ?: 0
                    }
                }
            }
            val bodyBytes = ByteArray(contentLength)
            var offset = 0
            while (offset < contentLength) {
                val read = input.read(bodyBytes, offset, contentLength - offset)
                if (read <= 0) break
                offset += read
            }
            return RecordedHttpRequest(
                method = method,
                path = path,
                body = String(bodyBytes, 0, offset, StandardCharsets.UTF_8)
            )
        }

        override fun close() {
            running = false
            runCatching { serverSocket.close() }
            worker.join(1000)
        }
    }

    private class InMemoryDeviceIdentityStore(
        initialUuid: String? = "test-device-uuid-m4",
        initialToken: String? = "test-device-token-m4"
    ) : DeviceIdentityStore {
        @Volatile
        private var uuid: String? = initialUuid

        @Volatile
        private var token: String? = initialToken

        @Volatile
        private var fcmToken: String? = null

        override fun getOrCreateDeviceUuid(): String {
            return uuid ?: EncryptedDeviceIdentityStore.generateDeviceUuid().also { uuid = it }
        }

        override fun getDeviceToken(): String? = token

        override fun saveDeviceToken(token: String, deviceUuid: String) {
            this.token = token
            if (deviceUuid.isNotBlank()) this.uuid = deviceUuid
        }

        override fun clearDeviceToken() {
            token = null
        }

        override fun getCachedFcmToken(): String? = fcmToken

        override fun saveFcmToken(fcmToken: String) {
            this.fcmToken = fcmToken
        }
    }

    private class InMemoryDealPagingSource(
        private val items: List<DealEntity>
    ) : PagingSource<Int, DealEntity>() {
        override fun getRefreshKey(state: PagingState<Int, DealEntity>): Int? = null
        override suspend fun load(params: LoadParams<Int>): LoadResult<Int, DealEntity> {
            return LoadResult.Page(data = items, prevKey = null, nextKey = null)
        }
    }

    private class InMemoryDealDao : DealDao {
        private val deals = linkedMapOf<Int, DealEntity>()
        private val invalidationTrackers = CopyOnWriteArrayList<PagingSource<Int, DealEntity>>()

        override suspend fun insertAll(deals: List<DealEntity>) {
            deals.forEach { this.deals[it.id] = it }
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
            val filtered = deals.values.filter { item ->
                val passCategory = category.isNullOrBlank() || category == "전체" || item.category == category
                val passFree = !freeShippingOnly || (item.shippingFee?.contains("무료") == true || item.shippingFee == "0")
                val passShop = shop.isNullOrBlank() || (item.shopName?.contains(shop, ignoreCase = true) == true)
                val passMin = minPrice == null || item.price >= minPrice
                val passMax = maxPrice == null || (item.price > 0 && item.price <= maxPrice)
                passCategory && passFree && passShop && passMin && passMax
            }.let { list ->
                when (sort) {
                    "price_asc" -> list.sortedBy { it.price }
                    "price_desc" -> list.sortedByDescending { it.price }
                    "comments" -> list.sortedByDescending { it.commentCount }
                    "recommendations" -> list.sortedByDescending { it.likeCount }
                    "popular" -> list.sortedByDescending { it.honeyScore }
                    else -> list.sortedByDescending { it.id }
                }
            }
            val source = InMemoryDealPagingSource(filtered)
            invalidationTrackers.add(source)
            return source
        }

        override suspend fun updateVoteCounts(dealId: Int, buyCount: Int, passCount: Int) {
            deals[dealId]?.let { existing ->
                deals[dealId] = existing.copy(buyCount = buyCount, passCount = passCount)
            }
            invalidationTrackers.forEach { it.invalidate() }
        }

        override suspend fun updateAppCommentCount(dealId: Int, appCommentCount: Int) {
            deals[dealId]?.let { existing ->
                deals[dealId] = existing.copy(appCommentCount = appCommentCount)
            }
            invalidationTrackers.forEach { it.invalidate() }
        }

        override suspend fun clearAllDeals() {
            deals.clear()
        }

        override suspend fun getDealCount(): Int = deals.size
    }

    private class InMemoryDealRemoteKeysDao : DealRemoteKeysDao {
        private val keys = linkedMapOf<Int, DealRemoteKeysEntity>()
        override suspend fun insertAll(remoteKey: List<DealRemoteKeysEntity>) {
            remoteKey.forEach { keys[it.dealId] = it }
        }
        override suspend fun getRemoteKeysForDealId(dealId: Int): DealRemoteKeysEntity? = keys[dealId]
        override suspend fun clearRemoteKeys() {
            keys.clear()
        }
    }

    private class InMemoryKeywordDao : KeywordDao {
        private val items = linkedMapOf<Int, KeywordEntity>()
        private val flow = MutableStateFlow<List<KeywordEntity>>(emptyList())
        private var nextId = 1

        override fun getAllKeywordsFlow(): Flow<List<KeywordEntity>> = flow.asStateFlow()
        override suspend fun getAllKeywords(): List<KeywordEntity> = items.values.toList()
        override suspend fun insertKeyword(keyword: KeywordEntity) {
            val id = if (keyword.id == 0) nextId++ else keyword.id
            items[id] = keyword.copy(id = id)
            flow.value = items.values.toList()
        }
        override suspend fun deleteKeyword(keyword: KeywordEntity) {
            items.remove(keyword.id)
            flow.value = items.values.toList()
        }
        override suspend fun deleteKeywordById(id: Int) {
            items.remove(id)
            flow.value = items.values.toList()
        }
        override suspend fun isKeywordExists(keyword: String): Boolean {
            return items.values.any { it.keyword == keyword }
        }
        override suspend fun updateKeywordActive(id: Int, isActive: Boolean) {
            items[id]?.let { existing ->
                items[id] = existing.copy(isActive = isActive)
                flow.value = items.values.toList()
            }
        }
        override suspend fun insertKeywords(keywords: List<KeywordEntity>) {
            keywords.forEach { insertKeyword(it) }
        }
    }

    private val openResources = mutableListOf<Closeable>()
    private val testScopes = mutableListOf<CoroutineScope>()

    @After
    fun tearDown() {
        testScopes.forEach { it.cancel() }
        testScopes.clear()
        openResources.forEach { runCatching { it.close() } }
        openResources.clear()
        HotDealsRemoteMediator.clearRefreshCache()
        LocalDataProvider.setDeviceIdentityStoreForTest(null)
        LocalDataProvider.setFeedPreferencesDataSourceForTest(null)
    }

    private fun newApiService(server: LocalStubHttpServer): ApiService {
        openResources.add(server)
        return Retrofit.Builder()
            .baseUrl(server.baseUrl)
            .client(OkHttpClient.Builder().build())
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(ApiService::class.java)
    }

    private fun newScope(): CoroutineScope {
        return CoroutineScope(SupervisorJob() + Dispatchers.Unconfined).also {
            testScopes.add(it)
        }
    }

    private suspend fun waitUntil(timeoutMs: Long = 3000L, predicate: () -> Boolean) {
        withTimeout(timeoutMs) {
            while (!predicate()) {
                delay(10)
            }
        }
    }

    private fun resolveProjectRoot(): File {
        val candidates = listOf(File("."), File(".."))
        return candidates.first { File(it, "settings.gradle").exists() && File(it, "app/build.gradle").exists() }
    }

    private fun resolveAppJavaRoot(): File {
        return listOf(File("src/main/java"), File("app/src/main/java")).first { it.exists() }
    }

    private fun createInMemoryPrefs(): SharedPreferences {
        val stringMap = linkedMapOf<String, String?>()
        val boolMap = linkedMapOf<String, Boolean>()
        val editorProxy = java.lang.reflect.Proxy.newProxyInstance(
            SharedPreferences.Editor::class.java.classLoader,
            arrayOf(SharedPreferences.Editor::class.java)
        ) { proxy, method, args ->
            when (method.name) {
                "putString" -> {
                    stringMap[args[0] as String] = args[1] as String?
                    proxy
                }
                "putBoolean" -> {
                    boolMap[args[0] as String] = args[1] as Boolean
                    proxy
                }
                "remove" -> {
                    val key = args[0] as String
                    stringMap.remove(key)
                    boolMap.remove(key)
                    proxy
                }
                "apply", "commit" -> if (method.returnType == Boolean::class.javaPrimitiveType) true else Unit
                else -> proxy
            }
        } as SharedPreferences.Editor

        return java.lang.reflect.Proxy.newProxyInstance(
            SharedPreferences::class.java.classLoader,
            arrayOf(SharedPreferences::class.java)
        ) { _, method, args ->
            when (method.name) {
                "getString" -> stringMap[args[0] as String] ?: (args[1] as String?)
                "getBoolean" -> boolMap[args[0] as String] ?: (args[1] as Boolean)
                "edit" -> editorProxy
                else -> null
            }
        } as SharedPreferences
    }

    private fun createJvmPagingDiffer(): PagingDataDiffer<DealItem> {
        val callback = object : DifferCallback {
            override fun onChanged(position: Int, count: Int) = Unit
            override fun onInserted(position: Int, count: Int) = Unit
            override fun onRemoved(position: Int, count: Int) = Unit
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

    private fun srgbChannelToLinear(channel: Float): Double {
        val c = channel.toDouble()
        return if (c <= 0.03928) c / 12.92 else ((c + 0.055) / 1.055).pow(2.4)
    }

    private fun relativeLuminance(color: Color): Double {
        return 0.2126 * srgbChannelToLinear(color.red) +
            0.7152 * srgbChannelToLinear(color.green) +
            0.0722 * srgbChannelToLinear(color.blue)
    }

    private fun alphaBlend(fg: Color, bg: Color, alpha: Float): Color {
        val inv = 1f - alpha
        return Color(
            red = fg.red * alpha + bg.red * inv,
            green = fg.green * alpha + bg.green * inv,
            blue = fg.blue * alpha + bg.blue * inv,
            alpha = 1f
        )
    }

    private fun contrastRatio(fg: Color, bg: Color): Double {
        val l1 = relativeLuminance(fg)
        val l2 = relativeLuminance(bg)
        val lighter = max(l1, l2)
        val darker = min(l1, l2)
        return (lighter + 0.05) / (darker + 0.05)
    }

    // -----------------------------------------------------------------------
    // T1: t13 커뮤니티 '살까' 배지 WCAG 2.2 AA 명암비 개선 및 소스 적용 검증
    // -----------------------------------------------------------------------
    @Test
    fun t1_t13_communityBuySelectedContrast_meetsWcagAA_acrossAllThemes() {
        val legacyLightBg = alphaBlend(LightColorScheme.secondary, LightColorScheme.surface, 0.14f)
        val legacyLightRatio = contrastRatio(LightColorScheme.secondary, legacyLightBg)
        assertTrue(
            String.format(Locale.US, "기존 Light 반투명 배지 대비율(%.2f)은 4.5 미만이어야 합니다.", legacyLightRatio),
            legacyLightRatio < 4.5
        )

        val schemes = listOf(
            "Light" to LightColorScheme,
            "Dark" to DarkColorScheme,
            "AMOLED" to AmoledColorScheme
        )
        for ((name, scheme) in schemes) {
            val buyRatio = contrastRatio(scheme.onSecondaryContainer, scheme.secondaryContainer)
            assertTrue(
                String.format(Locale.US, "%s 모드 살까(secondaryContainer) 대비율 %.2f < 4.5", name, buyRatio),
                buyRatio >= 4.5
            )
            val skipRatio = contrastRatio(scheme.onSurface, scheme.surfaceVariant)
            assertTrue(
                String.format(Locale.US, "%s 모드 말까(surfaceVariant) 대비율 %.2f < 4.5", name, skipRatio),
                skipRatio >= 4.5
            )
        }

        val javaRoot = resolveAppJavaRoot()
        val boardSrc = File(javaRoot, "com/ddaeany0919/insightdeal/presentation/community/CommunityBoardScreen.kt").readText()
        val detailSrc = File(javaRoot, "com/ddaeany0919/insightdeal/presentation/community/CommunityPostDetailScreen.kt").readText()

        assertFalse("CommunityBoardScreen.kt에 secondary.copy(alpha = 0.14f)가 남아있습니다.", boardSrc.contains("secondary.copy(alpha"))
        assertFalse("CommunityPostDetailScreen.kt에 secondary.copy(alpha = 0.14f)가 남아있습니다.", detailSrc.contains("secondary.copy(alpha"))
        assertTrue(boardSrc.contains("MaterialTheme.colorScheme.secondaryContainer"))
        assertTrue(boardSrc.contains("MaterialTheme.colorScheme.onSecondaryContainer"))
        assertTrue(detailSrc.contains("MaterialTheme.colorScheme.secondaryContainer"))
        assertTrue(detailSrc.contains("MaterialTheme.colorScheme.onSecondaryContainer"))
    }

    // -----------------------------------------------------------------------
    // T2: PriceGraphScreen 차트 교체 & 레거시(MPAndroidChart/Vico/jitpack) 0건 검증
    // -----------------------------------------------------------------------
    @Test
    fun t2_priceGraph_usesComposePriceChart_andZeroLegacyChartDependencies() = runBlocking {
        val server = LocalStubHttpServer { req ->
            when {
                req.path.startsWith("/api/product/77/history") -> 200 to """
                    [
                      {"price": 0, "tracked_at": "2026-11-01T10:00:00Z"},
                      {"price": 25000, "tracked_at": "2026-11-12T10:00:00Z"},
                      {"price": 22000, "tracked_at": "2026-11-15T10:00:00Z"},
                      {"price": 21000, "tracked_at": "2026-11-20T10:00:00Z"}
                    ]
                """.trimIndent()
                req.path.startsWith("/api/product/78/history") -> 200 to """
                    [
                      {"price": 19900, "tracked_at": "2026-11-20T10:00:00Z"}
                    ]
                """.trimIndent()
                else -> 404 to "{}"
            }
        }
        val api = newApiService(server)
        val vm = PriceChartViewModel(
            apiService = api,
            ioDispatcher = Dispatchers.Unconfined,
            coroutineScopeOverride = newScope()
        )

        vm.loadProductData(78)
        waitUntil { vm.priceHistory.value.size == 1 && !vm.isLoading.value }
        assertEquals(1, vm.getFilteredHistory(90).size)
        assertEquals(PriceChartViewModel.INSUFFICIENT_HISTORY_MESSAGE, vm.getBuyingTimingAdvice().reason)

        vm.loadProductData(77)
        waitUntil { vm.priceHistory.value.size == 3 && !vm.isLoading.value }
        val filtered90 = vm.getFilteredHistory(90)
        assertEquals(3, filtered90.size)
        val chartPrices = filtered90.map { it.price.toFloat() }
        val chartDates = filtered90.map { item ->
            val raw = item.date.trim()
            if (raw.length >= 10) raw.substring(5, 10).replace("-", ".") else raw
        }
        assertEquals(listOf(25000f, 22000f, 21000f), chartPrices)
        assertEquals(listOf("11.12", "11.15", "11.20"), chartDates)

        val root = resolveProjectRoot()
        val settingsText = File(root, "settings.gradle").readText()
        val appGradleText = File(root, "app/build.gradle").readText()
        val proguardText = File(root, "app/proguard-rules.pro").readText()

        assertFalse("settings.gradle에 jitpack.io가 남아있습니다.", settingsText.contains("jitpack", ignoreCase = true))
        assertFalse("app/build.gradle에 MPAndroidChart가 남아있습니다.", appGradleText.contains("MPAndroidChart"))
        assertFalse("app/build.gradle에 Vico가 남아있습니다.", appGradleText.contains("vico", ignoreCase = true))
        assertFalse("proguard-rules.pro에 mikephil이 남아있습니다.", proguardText.contains("mikephil"))
        assertFalse("proguard-rules.pro에 vico가 남아있습니다.", proguardText.contains("vico"))

        val javaRoot = resolveAppJavaRoot()
        val allJavaSources = javaRoot.walkTopDown().filter { it.isFile && it.extension == "kt" }.toList()
        for (file in allJavaSources) {
            val content = file.readText()
            assertFalse("${file.name}에 mikephil 참조가 남아있습니다.", content.contains("mikephil"))
            assertFalse("${file.name}에 patrykandpatrick.vico 참조가 남아있습니다.", content.contains("patrykandpatrick.vico"))
        }

        val priceGraphSrc = File(javaRoot, "com/ddaeany0919/insightdeal/presentation/price/PriceGraphScreen.kt").readText()
        assertFalse("PriceGraphScreen.kt에 AndroidView가 남아있습니다.", priceGraphSrc.contains("AndroidView"))
        assertFalse("PriceGraphScreen.kt에 LineChart가 남아있습니다.", priceGraphSrc.contains("LineChart"))
        assertTrue("PriceGraphScreen.kt가 공용 PriceChart를 호출해야 합니다.", priceGraphSrc.contains("PriceChart("))
    }

    // -----------------------------------------------------------------------
    // T3: HomeScreen 6종 정렬·서버 퀵필터·컴팩트·뮤트·포그라운드 연동 검증
    // -----------------------------------------------------------------------
    @Test
    fun t3_homeScreen_sixSorts_quickFilters_compact_mute_foreground_wiring() = runBlocking {
        HotDealsRemoteMediator.clearRefreshCache()
        val dao = InMemoryDealDao()
        val keysDao = InMemoryDealRemoteKeysDao()
        val prefs = createInMemoryPrefs()
        val feedPrefs = FeedPreferencesDataSource(prefsProvider = { prefs })

        val server = LocalStubHttpServer { req ->
            when {
                req.path.startsWith("/api/community/hot-deals") -> 200 to """
                    {
                      "deals": [
                        {
                          "id": 1001,
                          "title": "[지마켓] 삼성 모니터 특가",
                          "price": 29000,
                          "currency": "KRW",
                          "shop_name": "지마켓",
                          "site_name": "뽐뿌",
                          "shipping_fee": "무료",
                          "category": "가전/디지털",
                          "created_at": "2026-11-22T04:00:00Z",
                          "tags": ["인기"]
                        },
                        {
                          "id": 1002,
                          "title": "[알리] 광고성 게임기",
                          "price": 15000,
                          "currency": "KRW",
                          "shop_name": "알리익스프레스",
                          "site_name": "알리뽐뿌",
                          "shipping_fee": "무료",
                          "category": "가전/디지털",
                          "created_at": "2026-11-22T03:00:00Z"
                        }
                      ],
                      "has_more": false
                    }
                """.trimIndent()
                else -> 200 to "{}"
            }
        }
        val api = newApiService(server)
        val scope = newScope()
        val vm = HomeViewModel(
            apiService = api,
            coroutineScopeOverride = scope,
            autoInitialize = false,
            feedPreferencesDataSource = feedPrefs,
            dealDaoProvider = { dao },
            dealRemoteKeysDaoProvider = { keysDao },
            runInTransactionProvider = { block -> block() },
            realtimePollIntervalMs = 200L
        )

        for (sortKey in HomeViewModel.SUPPORTED_SORTS) {
            vm.selectSort(sortKey)
            assertEquals(sortKey, vm.filterState.value.sortBy)
        }
        vm.selectSort("price_desc")
        vm.setShopFilter("지마켓")
        vm.setPriceRangeFilter(null, 30_000)
        vm.toggleFreeShippingOnly()
        vm.setViewMode(FeedPreferencesDataSource.VIEW_MODE_COMPACT)
        vm.addMutedKeyword("게임기")
        vm.onForegroundChanged(true)
        vm.onForegroundChanged(false)

        val differ = createJvmPagingDiffer()
        val collectJob = scope.launch {
            vm.dealsPagingData.collectLatest { differ.collectFrom(it) }
        }

        waitUntil { differ.snapshot().items.map { it.id } == listOf(1001) && server.recordedRequests.isNotEmpty() }

        assertEquals("지마켓", vm.filterState.value.shop)
        assertEquals(30_000, vm.filterState.value.maxPrice)
        assertTrue(vm.filterState.value.freeShippingOnly)
        assertEquals(FeedPreferencesDataSource.VIEW_MODE_COMPACT, vm.viewMode.value)
        assertTrue(vm.mutedKeywords.value.contains("게임기"))

        val recordedQuery = server.recordedRequests.lastOrNull { it.path.startsWith("/api/community/hot-deals") }?.path.orEmpty()
        val decodedQuery = URLDecoder.decode(recordedQuery, StandardCharsets.UTF_8.name())
        assertTrue(decodedQuery.contains("sort=price_desc"))
        assertTrue(decodedQuery.contains("shop=지마켓"))
        assertTrue(decodedQuery.contains("max_price=30000"))
        assertTrue(decodedQuery.contains("free_shipping=true"))
        assertEquals(listOf(1001), differ.snapshot().items.map { it.id })
        collectJob.cancel()

        val javaRoot = resolveAppJavaRoot()
        val homeSrc = File(javaRoot, "com/ddaeany0919/insightdeal/presentation/home/HomeScreen.kt").readText()
        for (sortKey in HomeViewModel.SUPPORTED_SORTS) {
            assertTrue("HomeScreen.kt에 정렬 키 '$sortKey'가 누락되었습니다.", homeSrc.contains("\"$sortKey\""))
        }
        assertTrue("G마켓 라벨이 지마켓 서버 키로 매핑되어야 합니다.", homeSrc.contains("\"G마켓\" to \"지마켓\""))
        assertTrue("알리 라벨이 알리익스프레스 서버 키로 매핑되어야 합니다.", homeSrc.contains("\"알리\" to \"알리익스프레스\""))
        assertTrue(homeSrc.contains("viewModel.setShopFilter("))
        assertTrue(homeSrc.contains("viewModel.setPriceRangeFilter("))
        assertTrue(homeSrc.contains("viewModel.setViewMode("))
        assertTrue(homeSrc.contains("viewModel.addMutedKeyword("))
        assertTrue(homeSrc.contains("viewModel.removeMutedKeyword("))
        assertTrue(homeSrc.contains("Lifecycle.Event.ON_START"))
        assertTrue(homeSrc.contains("Lifecycle.Event.ON_STOP"))
        assertTrue(homeSrc.contains("viewModel.onForegroundChanged(true)"))
        assertTrue(homeSrc.contains("viewModel.onForegroundChanged(false)"))
        assertTrue(homeSrc.contains("CompactDealCardComposable("))
        assertTrue(homeSrc.contains("GridItemSpan(maxLineSpan)"))
        assertTrue(homeSrc.contains("MutedKeywordsBottomSheet("))
    }

    // -----------------------------------------------------------------------
    // T4: DealDetail similarDeals + MainActivity 백스택 + TestPush UI 배선 검증
    // -----------------------------------------------------------------------
    @Test
    fun t4_dealDetail_similarDeals_and_testPush_wiring() = runBlocking {
        LocalDataProvider.setDeviceIdentityStoreForTest(InMemoryDeviceIdentityStore("uuid-push-m4", "tok-m4"))

        val detailServer = LocalStubHttpServer { req ->
            when {
                req.path.startsWith("/api/community/deals/500") -> 200 to """
                    {
                      "id": 500,
                      "title": "메인 특가 상품",
                      "price": 49000,
                      "currency": "KRW",
                      "site_name": "뽐뿌",
                      "content_html": "<p>상세 본문</p>",
                      "post_url": "https://ppomppu.co.kr/500",
                      "ecommerce_url": "https://coupang.com/500",
                      "history_count": 0,
                      "similar_deals": [
                        {
                          "id": 501,
                          "title": "[쿠팡] 연관 상품 A",
                          "price": 45000,
                          "currency": "KRW",
                          "shop_name": "쿠팡",
                          "image_url": "https://example.com/a.jpg",
                          "shipping_fee": "무료",
                          "site_name": "펨코",
                          "category": "가전/디지털",
                          "created_at": "2026-11-22T01:00:00Z"
                        },
                        {
                          "id": 500,
                          "title": "자기 자신은 제외",
                          "price": 49000,
                          "currency": "KRW",
                          "site_name": "뽐뿌"
                        }
                      ]
                    }
                """.trimIndent()
                else -> 404 to "{}"
            }
        }
        val detailApi = newApiService(detailServer)
        val detailVm = DealDetailViewModel(
            apiService = detailApi,
            deviceUuidProvider = { "test-device-uuid" },
            ioDispatcher = Dispatchers.Unconfined,
            coroutineScopeOverride = newScope()
        )
        detailVm.loadDealData(500)
        waitUntil { !detailVm.uiState.value.isLoading && detailVm.uiState.value.deal != null }
        assertEquals(1, detailVm.uiState.value.similarDeals.size)
        assertEquals(501, detailVm.uiState.value.similarDeals.first().id)

        val pushServer = LocalStubHttpServer { req ->
            when (req.path) {
                "/api/push/test" -> 200 to """{"success": true, "message": "테스트 알림 발송 완료"}"""
                else -> 404 to "{}"
            }
        }
        val pushApi = newApiService(pushServer)
        val kwVm = KeywordManagerViewModel(
            apiService = pushApi,
            keywordDaoProvider = { InMemoryKeywordDao() },
            ioDispatcher = Dispatchers.Unconfined,
            coroutineScopeOverride = newScope(),
            autoInitialize = false
        )
        kwVm.sendTestPush()
        waitUntil { kwVm.testPushState.value.feedbackSeq == 1 }
        assertEquals(true, kwVm.testPushState.value.isSuccess)
        assertEquals("테스트 알림 발송 완료", kwVm.testPushState.value.message)
        kwVm.clearTestPushFeedback()
        assertNull(kwVm.testPushState.value.message)
        assertNull(kwVm.testPushState.value.isSuccess)

        val javaRoot = resolveAppJavaRoot()
        val detailScreenSrc = File(javaRoot, "com/ddaeany0919/insightdeal/presentation/DealDetailScreen.kt").readText()
        val kwScreenSrc = File(javaRoot, "com/ddaeany0919/insightdeal/presentation/KeywordManagerScreen.kt").readText()
        val settingsScreenSrc = File(javaRoot, "com/ddaeany0919/insightdeal/presentation/settings/SettingsScreen.kt").readText()
        val mainActivitySrc = File(javaRoot, "com/ddaeany0919/insightdeal/MainActivity.kt").readText()

        assertTrue(detailScreenSrc.contains("similarDeals: List<DealItem> = emptyList()"))
        assertTrue(detailScreenSrc.contains("onNavigateToDeal: (Int) -> Unit = {}"))
        assertTrue(detailScreenSrc.contains("SimilarDealsSection("))
        assertTrue(detailScreenSrc.contains("원본 글 보기"))
        assertFalse("PriceComparison 기능이 추가되면 안 됩니다.", detailScreenSrc.contains("PriceComparison"))

        val similarSectionSrc = detailScreenSrc.substringAfter("private fun SimilarDealsSection(")
        assertFalse(similarSectionSrc.contains(".tags"))
        assertFalse(similarSectionSrc.contains(".siteNames"))
        assertFalse(similarSectionSrc.contains(".optionsData"))
        assertFalse(similarSectionSrc.contains(".subUrls"))

        val dealRouteBlock = mainActivitySrc.substringAfter("composable(\"deal_detail/{dealId}\")")
            .substringBefore("composable(")
        assertTrue(dealRouteBlock.contains("onNavigateToDeal ="))
        assertTrue(dealRouteBlock.contains("navController.navigate(\"deal_detail/\$targetDealId\")"))
        assertFalse("similarDeals 이동 시 launchSingleTop = true 를 사용하면 안 됩니다.", dealRouteBlock.contains("launchSingleTop"))

        assertTrue(kwScreenSrc.contains("SnackbarHost"))
        assertTrue(kwScreenSrc.contains("viewModel.testPushState"))
        assertTrue(kwScreenSrc.contains("viewModel.sendTestPush()"))
        assertTrue(kwScreenSrc.contains("viewModel.clearTestPushFeedback()"))
        assertTrue(kwScreenSrc.contains("테스트 알림 보내기"))

        assertTrue(settingsScreenSrc.contains("SnackbarHost"))
        assertTrue(settingsScreenSrc.contains("testPushState: TestPushState"))
        assertTrue(settingsScreenSrc.contains("onSendTestPush"))
        assertTrue(settingsScreenSrc.contains("onClearTestPushFeedback"))
        assertTrue(settingsScreenSrc.contains("테스트 알림 보내기"))

        val settingsRouteBlock = mainActivitySrc.substringAfter("composable(\"settings\")")
            .substringBefore("composable(\"keyword_alarm\")")
        assertTrue(settingsRouteBlock.contains("KeywordManagerViewModel"))
        assertTrue(settingsRouteBlock.contains("onSendTestPush"))
        assertTrue(settingsRouteBlock.contains("onClearTestPushFeedback"))
    }

    // -----------------------------------------------------------------------
    // T5: 전역 불변식 검증 (ViewModel Context 0건, 포트 8080, PriceComparison 배제)
    // -----------------------------------------------------------------------
    @Test
    fun t5_globalInvariants_viewModelPurity_and_port8080() {
        val root = resolveProjectRoot()
        val javaRoot = resolveAppJavaRoot()

        val viewModelFiles = javaRoot.walkTopDown()
            .filter { it.isFile && it.name.endsWith("ViewModel.kt") }
            .toList()
        assertTrue(viewModelFiles.isNotEmpty())
        for (vmFile in viewModelFiles) {
            val text = vmFile.readText()
            assertFalse("${vmFile.name}에 android.content.Context 참조가 있습니다.", text.contains("android.content.Context"))
            assertFalse("${vmFile.name}에 android.app.Application 참조가 있습니다.", text.contains("android.app.Application"))
            assertFalse("${vmFile.name}에 LazyListState 참조가 있습니다.", text.contains("LazyListState"))
            assertFalse("${vmFile.name}에 LazyGridState 참조가 있습니다.", text.contains("LazyGridState"))
        }

        val appGradle = File(root, "app/build.gradle").readText()
        val netConfig = File(javaRoot, "com/ddaeany0919/insightdeal/data/network/NetworkConfig.kt").readText()
        val netModule = File(javaRoot, "com/ddaeany0919/insightdeal/network/NetworkModule.kt").readText()
        val priceRepo = File(javaRoot, "com/ddaeany0919/insightdeal/data/PriceHistoryRepository.kt").readText()

        assertTrue(appGradle.contains("http://10.0.2.2:8080/"))
        assertTrue(netConfig.contains("SERVER_PORT = 8080"))
        assertTrue(netModule.contains("BuildConfig.BASE_URL"))
        assertTrue(priceRepo.contains("BuildConfig.BASE_URL"))
    }

    // -----------------------------------------------------------------------
    // T6: 얼만데 자체 투표/의견 분리 파싱 · Room v9 마이그레이션 · 왕복 매핑 검증
    // -----------------------------------------------------------------------
    @Test
    fun t6_appOnlyInteractionCounts_gsonParsing_roomRoundTrip_andMigration8To9() {
        val gson = Gson()
        val scrapedOnlyJson = """
            {
              "id": 9101,
              "title": "[쿠팡] 수미 햇 감자 3kg",
              "price": 3900,
              "site_name": "펨코",
              "like_count": 3,
              "recommendations": 3,
              "comment_count": 4,
              "comments": 4,
              "buy_count": 0,
              "pass_count": 0,
              "app_comment_count": 0
            }
        """.trimIndent()
        val parsed = gson.fromJson(scrapedOnlyJson, DealItem::class.java)
        assertEquals(3, parsed.likeCount)
        assertEquals(0, parsed.buyCount)
        assertEquals(0, parsed.passCount)
        assertEquals(0, parsed.appCommentCount)

        val engagedJson = """
            {
              "id": 9102,
              "title": "[쿠팡] 얼만데 자체 반응 딜",
              "price": 12900,
              "site_name": "뽐뿌",
              "like_count": 20,
              "comment_count": 15,
              "buy_count": 4,
              "pass_count": 2,
              "app_comment_count": 3
            }
        """.trimIndent()
        val engaged = gson.fromJson(engagedJson, DealItem::class.java)
        val roundTripped = DealEntity.fromDealItem(engaged).toDealItem()
        assertEquals(4, roundTripped.buyCount)
        assertEquals(2, roundTripped.passCount)
        assertEquals(3, roundTripped.appCommentCount)
        assertEquals(4, roundTripped.withClosedReportState(true, 1).buyCount)
        assertEquals(3, roundTripped.withClosedReportState(true, 1).appCommentCount)

        val executedSqls = mutableListOf<String>()
        val fakeDb = Proxy.newProxyInstance(
            androidx.sqlite.db.SupportSQLiteDatabase::class.java.classLoader,
            arrayOf(androidx.sqlite.db.SupportSQLiteDatabase::class.java)
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
        } as androidx.sqlite.db.SupportSQLiteDatabase

        assertEquals(8, com.ddaeany0919.insightdeal.local.db.AppDatabase.MIGRATION_8_9.startVersion)
        assertEquals(9, com.ddaeany0919.insightdeal.local.db.AppDatabase.MIGRATION_8_9.endVersion)
        com.ddaeany0919.insightdeal.local.db.AppDatabase.MIGRATION_8_9.migrate(fakeDb)
        assertTrue(executedSqls.any { it.contains("deals") && it.contains("buyCount") })
        assertTrue(executedSqls.any { it.contains("deals") && it.contains("passCount") })
        assertTrue(executedSqls.any { it.contains("deals") && it.contains("appCommentCount") })
    }
}

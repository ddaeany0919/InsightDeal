package com.ddaeany0919.insightdeal

import com.ddaeany0919.insightdeal.core.security.DeviceIdentityStore
import com.ddaeany0919.insightdeal.core.security.EncryptedDeviceIdentityStore
import com.ddaeany0919.insightdeal.data.ApiPricePoint
import com.ddaeany0919.insightdeal.data.KeywordPreferencesDataSource
import com.ddaeany0919.insightdeal.data.LocalDataProvider
import com.ddaeany0919.insightdeal.data.util.ImageUrlResolver
import com.ddaeany0919.insightdeal.local.db.DealEntity
import com.ddaeany0919.insightdeal.models.CategoryCatalog
import com.ddaeany0919.insightdeal.models.DealItem
import com.ddaeany0919.insightdeal.models.DealOptionItem
import com.ddaeany0919.insightdeal.models.DealSource
import com.ddaeany0919.insightdeal.models.StandardCategory
import com.ddaeany0919.insightdeal.network.ApiDealVotes
import com.ddaeany0919.insightdeal.network.ApiPriceHistory
import com.ddaeany0919.insightdeal.network.ApiService
import com.ddaeany0919.insightdeal.network.NetworkModule
import com.ddaeany0919.insightdeal.network.UserCommentDto
import com.ddaeany0919.insightdeal.presentation.DealDetailViewModel
import com.ddaeany0919.insightdeal.presentation.home.HotDealsPagingSource
import androidx.paging.PagingSource
import com.google.gson.Gson
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import okhttp3.OkHttpClient
import okhttp3.Request
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
import java.net.InetAddress
import java.net.ServerSocket
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger

class InsightDealM4P0UnitTest {

    private class InMemoryDeviceIdentityStore(
        initialUuid: String? = null,
        initialToken: String? = null,
        initialFcmToken: String? = null
    ) : DeviceIdentityStore {
        @Volatile
        private var uuid: String? = initialUuid

        @Volatile
        private var token: String? = initialToken

        @Volatile
        private var fcmToken: String? = initialFcmToken

        private val lock = Any()

        override fun getOrCreateDeviceUuid(): String {
            val current = uuid
            if (!current.isNullOrBlank()) return current
            return synchronized(lock) {
                val rechecked = uuid
                if (!rechecked.isNullOrBlank()) {
                    rechecked
                } else {
                    val created = EncryptedDeviceIdentityStore.generateDeviceUuid()
                    uuid = created
                    created
                }
            }
        }

        override fun getDeviceToken(): String? = token

        override fun saveDeviceToken(token: String, deviceUuid: String) {
            val cleanToken = token.trim()
            if (cleanToken.isBlank()) return
            synchronized(lock) {
                this.token = cleanToken
                if (deviceUuid.isNotBlank()) {
                    this.uuid = deviceUuid.trim()
                }
            }
        }

        override fun clearDeviceToken() {
            synchronized(lock) {
                this.token = null
            }
        }

        override fun getCachedFcmToken(): String? = fcmToken

        override fun saveFcmToken(fcmToken: String) {
            val clean = fcmToken.trim()
            if (clean.isBlank()) return
            synchronized(lock) {
                this.fcmToken = clean
            }
        }
    }

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
                                500 -> "Internal Server Error"
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
        LocalDataProvider.setDeviceIdentityStoreForTest(null)
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

    // -----------------------------------------------------------------------
    // 1. ImageUrlResolver 전 구간 무결성 (차단 커뮤니티, targetWidth, null/data:, 릴리스 http->https, wsrv 언래핑)
    // -----------------------------------------------------------------------
    @Test
    fun imageUrlResolver_routesBlockedCommunitiesToBackendProxyAndRespectsTargetWidthAndNull() {
        val debugBase = "http://10.0.2.2:8080/"
        val releaseHttpBase = "http://insightdeal.site/"

        // null / 공백 / data: URI
        assertNull(ImageUrlResolver.resolve(null, 600, debugBase, true))
        assertNull(ImageUrlResolver.resolve("   ", 600, debugBase, true))
        val dataUri = "data:image/png;base64,AAA"
        assertEquals(dataUri, ImageUrlResolver.resolve(dataUri, 600, releaseHttpBase, false))

        // 차단 커뮤니티 -> wsrv.nl 미경유, 백엔드 프록시 직행 + 릴리스 http:// base -> https:// 보정
        val blockedSamples = listOf(
            "http://cdn.ppomppu.co.kr/a.jpg",
            "https://img.fmkorea.com/b.png",
            "https://ruliweb.com/c.webp"
        )
        for (sample in blockedSamples) {
            val debugResolved = ImageUrlResolver.resolve(sample, 600, debugBase, true)
            assertNotNull(debugResolved)
            assertTrue(debugResolved!!.startsWith("http://10.0.2.2:8080/api/proxy-image?url="))
            assertTrue(debugResolved.endsWith("&v=5"))
            assertFalse(debugResolved.contains("wsrv.nl"))

            val releaseResolved = ImageUrlResolver.resolve(sample, 600, releaseHttpBase, false)
            assertNotNull(releaseResolved)
            assertTrue(releaseResolved!!.startsWith("https://insightdeal.site/api/proxy-image?url="))
            assertTrue(releaseResolved.endsWith("&v=5"))
            assertFalse(releaseResolved.contains("wsrv.nl"))
        }

        // 비차단 외부 도메인 -> targetWidth(&w=600) 반영
        val cdnResolved = ImageUrlResolver.resolve("https://cdn.example.com/item.jpg", 600, releaseHttpBase, false)
        assertNotNull(cdnResolved)
        assertTrue(cdnResolved!!.startsWith("https://wsrv.nl/?url="))
        assertTrue(cdnResolved.contains("&w=600"))

        // wsrv.nl 내부에 차단 커뮤니티 URL이 포함된 경우 getFallbackBackendProxyUrl 언래핑 검증
        val wrapped = "https://wsrv.nl/?url=http%3A%2F%2Fimg.ppomppu.co.kr%2Fa.jpg&output=webp"
        val fallbackUrl = ImageUrlResolver.getFallbackBackendProxyUrl(wrapped, releaseHttpBase, false)
        assertNotNull(fallbackUrl)
        assertTrue(fallbackUrl!!.startsWith("https://insightdeal.site/api/proxy-image?url="))
        assertTrue(fallbackUrl.contains(java.net.URLEncoder.encode("http://img.ppomppu.co.kr/a.jpg", "UTF-8")))
        assertTrue(fallbackUrl.endsWith("&v=5"))
    }

    // -----------------------------------------------------------------------
    // 2. 단일 device_uuid SSOT, 서버 발급 UUID 동기화, 공개 GET 비등록 vs 보호 GET 자동 등록
    // -----------------------------------------------------------------------
    @Test
    fun deviceIdentitySsot_syncsServerIssuedUuidAndPublicGetSkipsRegistration() = runBlocking {
        val store = InMemoryDeviceIdentityStore(initialUuid = "device_local_111", initialToken = null)
        LocalDataProvider.setDeviceIdentityStoreForTest(store)

        val keywordPrefs = KeywordPreferencesDataSource()
        assertEquals("device_local_111", keywordPrefs.resolveUserOrDeviceKey(""))
        assertEquals("device_custom_1", keywordPrefs.resolveUserOrDeviceKey("device_custom_1"))

        // 공백 토큰 저장 무시 방어
        store.saveDeviceToken("   ", "device_should_ignore")
        assertNull(store.getDeviceToken())

        val registerCalls = AtomicInteger(0)
        val server = LocalStubHttpServer { req ->
            when {
                req.path.startsWith("/api/push/device") -> {
                    registerCalls.incrementAndGet()
                    200 to """{"status":"ok","device_uuid":"device_server_999","device_token":"srv_tok_999"}"""
                }
                req.path.startsWith("/api/community/hot-deals") -> 200 to """{"deals":[],"has_more":false}"""
                req.path.startsWith("/api/meta/categories") -> 200 to """{"categories":[],"sources":[]}"""
                req.path.startsWith("/api/wishlist") -> 200 to """[]"""
                else -> 200 to """{"ok":true}"""
            }
        }.also { resourcesToClose += it }

        val client = NetworkModule.createOkHttpClient(
            baseUrl = server.baseUrl,
            identityStoreProvider = { store },
            registrationClient = OkHttpClient.Builder().build()
        )

        // 공개 GET 2종은 토큰 미보유 상태여도 /api/push/device 자동 등록을 유발하지 않아야 함
        client.newCall(Request.Builder().url("${server.baseUrl}api/community/hot-deals").get().build()).execute().close()
        client.newCall(Request.Builder().url("${server.baseUrl}api/meta/categories").get().build()).execute().close()
        assertEquals(0, registerCalls.get())
        assertNull(store.getDeviceToken())

        // 보호 GET (/api/wishlist/) 호출 시 자동 등록 1회 트리거 + 서버 발급 UUID 동기화 + 헤더 첨부
        client.newCall(Request.Builder().url("${server.baseUrl}api/wishlist/").get().build()).execute().close()
        assertEquals(1, registerCalls.get())
        assertEquals("srv_tok_999", store.getDeviceToken())
        assertEquals("device_server_999", store.getOrCreateDeviceUuid())

        val wishlistReq = server.recordedRequests.last { it.path.startsWith("/api/wishlist") }
        assertEquals("srv_tok_999", wishlistReq.headers["x-device-token"])
    }

    // -----------------------------------------------------------------------
    // 3. DTO 이중 키 역직렬화 (comments vs comment_count 우선순위, snake_case, 보조 DTO, 깨진 JSON 방어)
    // -----------------------------------------------------------------------
    @Test
    fun dtoDualKeyDeserialization_coversDealItemPrecedenceSnakeCaseAndSecondaryDtos() {
        val gson = Gson()

        // 원글 comments(45)와 앱 한줄평 comment_count(2) 공존 시 후속 키(comment_count=2) 반영 확인
        val dealJson = """
            {
              "id": 777,
              "title": "스네이크케이스 테스트 딜",
              "price": 19900,
              "site_name": "뽐뿌",
              "created_at": "2026-11-19T10:00:00Z",
              "view_count": 321,
              "like_count": 15,
              "dislike_count": 1,
              "honey_score": 135,
              "comments": 45,
              "comment_count": 2,
              "buy_count": 9,
              "save_count": 4,
              "user_vote": "buy",
              "history_count": 3,
              "is_search_fallback": true,
              "is_free": true
            }
        """.trimIndent()
        val deal = gson.fromJson(dealJson, DealItem::class.java)
        assertEquals(777, deal.id)
        assertEquals("2026-11-19T10:00:00Z", deal.createdAt)
        assertEquals(321, deal.viewCount)
        assertEquals(15, deal.likeCount)
        assertEquals(1, deal.dislikeCount)
        assertEquals(135, deal.honeyScore)
        assertEquals(2, deal.commentCount)
        assertEquals(9, deal.buyCount)
        assertEquals(4, deal.passCount)
        assertEquals("buy", deal.userVote)
        assertEquals(3, deal.historyCount)
        assertTrue(deal.isSearchFallback)
        assertTrue(deal.isFree)

        // 깨진 options_data JSON 문자열 및 JsonNull 안전 파싱
        val brokenOptsDeal = gson.fromJson(
            """{"id":1,"title":"A","price":100,"site_name":"뽐뿌","options_data":"{broken"}""",
            DealItem::class.java
        )
        assertTrue(brokenOptsDeal.optionsData.isEmpty())

        val nullOptsDeal = gson.fromJson(
            """{"id":2,"title":"B","price":100,"site_name":"뽐뿌","options_data":null}""",
            DealItem::class.java
        )
        assertTrue(DealDetailViewModel.extractValidMultiOptions(nullOptsDeal).isEmpty())
        val deserializer = com.ddaeany0919.insightdeal.models.OptionsDataDeserializer()
        assertTrue(deserializer.deserialize(com.google.gson.JsonNull.INSTANCE, null, null).isEmpty())
        assertTrue(deserializer.deserialize(null, null, null).isEmpty())

        // ApiPriceHistory / ApiPricePoint / UserCommentDto / ApiDealVotes(save_count) 양방향 역직렬화
        val histCamel = gson.fromJson("""{"price":12000,"trackedAt":"2026-11-19T00:00:00Z","isAvailable":false}""", ApiPriceHistory::class.java)
        assertEquals("2026-11-19T00:00:00Z", histCamel.tracked_at)
        assertFalse(histCamel.is_available)

        val pointSnake = gson.fromJson("""{"tracked_at":"2026-11-19T01:00:00Z","price":9900,"is_available":true}""", ApiPricePoint::class.java)
        assertEquals("2026-11-19T01:00:00Z", pointSnake.date)
        assertTrue(pointSnake.is_available)

        val commentCamel = gson.fromJson(
            """{"id":5,"postId":12,"postTitle":"제목","userId":"dev_1","nickname":"테스터","content":"안녕","dealUrl":"https://a.com","isAccepted":true,"parentId":1,"createdAt":"2026-11-19T02:00:00Z"}""",
            UserCommentDto::class.java
        )
        assertEquals(12, commentCamel.post_id)
        assertEquals("dev_1", commentCamel.user_id)
        assertTrue(commentCamel.is_accepted)
        assertEquals(1, commentCamel.parent_id)
        assertEquals("2026-11-19T02:00:00Z", commentCamel.created_at)

        val votesSaveSnake = gson.fromJson("""{"buy_count":8,"save_count":5,"user_vote":"save"}""", ApiDealVotes::class.java)
        assertEquals(8, votesSaveSnake.buy_count)
        assertEquals(5, votesSaveSnake.pass_count)
        assertEquals("save", votesSaveSnake.user_vote)
    }

    // -----------------------------------------------------------------------
    // 4. 15종 카테고리 전수 검증 + matchesDealCategory + syncWithServer
    // -----------------------------------------------------------------------
    @Test
    fun categoryCatalog_verifiesAll15PairsAndMatchesDirectly() {
        val expectedPairs = listOf(
            StandardCategory("음식", "식품/건강"),
            StandardCategory("SW/게임", "SW/게임"),
            StandardCategory("PC용품", "PC/하드웨어"),
            StandardCategory("가전제품", "가전/TV"),
            StandardCategory("생활용품", "생활용품"),
            StandardCategory("패션", "의류/패션"),
            StandardCategory("뷰티", "뷰티"),
            StandardCategory("모바일/기프티콘", "모바일/기프티콘"),
            StandardCategory("상품권", "상품권"),
            StandardCategory("적립", "적립/포인트"),
            StandardCategory("이벤트", "이벤트"),
            StandardCategory("육아/유아용품", "육아/유아용품"),
            StandardCategory("해외직구", "해외직구"),
            StandardCategory("핫딜모음", "핫딜모음"),
            StandardCategory("기타", "기타")
        )
        assertEquals(expectedPairs, CategoryCatalog.STANDARD_CATEGORIES)
        assertTrue(CategoryCatalog.STANDARD_CATEGORIES.none { it.key == "여행.해외핫딜" || it.label == "여행.해외핫딜" })

        assertTrue(CategoryCatalog.matchesDealCategory("LG 선풍기 특가", "기타", "뽐뿌", targetCategory = "가전제품"))
        assertTrue(CategoryCatalog.matchesDealCategory("아마존 SSD", "여행.해외핫딜", "뽐뿌해외", targetCategory = "해외직구"))
        assertTrue(CategoryCatalog.matchesDealCategory("아기 물티슈", "생활용품", "빠삭육아", targetCategory = "육아/유아용품"))
        assertTrue(CategoryCatalog.matchesDealCategory("아기 물티슈", "생활용품", "bbasak_parenting", targetCategory = "육아/유아용품"))
        assertTrue(CategoryCatalog.matchesDealCategory("아기 물티슈", "생활용품", "뽐뿌", siteNames = listOf("빠삭육아"), targetCategory = "육아/유아용품"))
        assertTrue(CategoryCatalog.matchesDealCategory("초특가", "기타", "뽐뿌", honeyScore = 100, targetCategory = "핫딜모음"))
        assertFalse(CategoryCatalog.matchesDealCategory("일반가", "기타", "뽐뿌", honeyScore = 99, targetCategory = "핫딜모음"))
        assertTrue(CategoryCatalog.matchesDealCategory("무관", "기타", "뽐뿌", targetCategory = null))
        assertTrue(CategoryCatalog.matchesDealCategory("무관", "기타", "뽐뿌", targetCategory = "전체"))
    }

    @Test
    fun categoryCatalog_syncWithServerUpdatesAndPreservesFallbackOnError() = runBlocking {
        val okPayload = """
            {
              "categories": [
                {"key":"음식","label":"식품/건강"},
                {"key":"SW/게임","label":"SW/게임"},
                {"key":"PC용품","label":"PC/하드웨어"},
                {"key":"가전제품","label":"가전/TV"},
                {"key":"생활용품","label":"생활용품"},
                {"key":"패션","label":"의류/패션"},
                {"key":"뷰티","label":"뷰티"},
                {"key":"모바일/기프티콘","label":"모바일/기프티콘"},
                {"key":"상품권","label":"상품권"},
                {"key":"적립","label":"적립/포인트"},
                {"key":"이벤트","label":"이벤트"},
                {"key":"육아/유아용품","label":"육아/유아용품"},
                {"key":"해외직구","label":"해외직구"},
                {"key":"핫딜모음","label":"핫딜모음"},
                {"key":"기타","label":"기타"}
              ],
              "sources": ["전체","펨코","뽐뿌","뽐뿌해외","루리웹","퀘이사존","클리앙","알리뽐뿌","빠삭국내","빠삭해외","빠삭육아"]
            }
        """.trimIndent()

        var failMode = false
        val server = LocalStubHttpServer {
            if (failMode) {
                500 to """{"detail":"error"}"""
            } else {
                200 to okPayload
            }
        }.also { resourcesToClose += it }

        val apiService = createApiService(server.baseUrl)
        CategoryCatalog.syncWithServer(apiService)
        assertEquals(15, CategoryCatalog.categoriesState.value.size)
        assertTrue(CategoryCatalog.sourcesState.value.contains("빠삭육아"))

        failMode = true
        CategoryCatalog.syncWithServer(apiService)
        assertEquals(15, CategoryCatalog.categoriesState.value.size)
        assertEquals(11, CategoryCatalog.sourcesState.value.size)
    }

    // -----------------------------------------------------------------------
    // 5. HotDealsPagingSource "빠삭" 확장 & DealEntity.fromDealItem 정규화
    // -----------------------------------------------------------------------
    @Test
    fun hotDealsPagingSource_expandsBbasakPlatformAndSetsNextKeyOnHasMoreTrue() = runBlocking {
        val itemsJson = (1..2).joinToString(",") { idx ->
            """{"id":$idx,"title":"빠삭 딜 $idx","price":10000,"site_name":"빠삭국내","honey_score":110}"""
        }
        val server = LocalStubHttpServer {
            200 to """{"deals":[$itemsJson],"has_more":true}"""
        }.also { resourcesToClose += it }

        val pagingSource = HotDealsPagingSource(
            apiService = createApiService(server.baseUrl),
            platform = "빠삭"
        )
        val result = pagingSource.load(
            PagingSource.LoadParams.Refresh(key = 0, loadSize = 20, placeholdersEnabled = false)
        )
        assertTrue(result is PagingSource.LoadResult.Page)
        val page = result as PagingSource.LoadResult.Page
        assertEquals(2, page.nextKey)

        val rawPath = server.recordedRequests.single().path
        val decodedPath = URLDecoder.decode(rawPath, "UTF-8")
        assertTrue(decodedPath.contains("platform=빠삭국내,빠삭해외,빠삭육아"))
    }

    @Test
    fun dealEntity_fromDealItemNormalizesLegacyCategoryAndPreservesMultiOptions() {
        val item = DealItem(
            id = 88,
            title = "레거시 해외직구 딜",
            price = 50000L,
            siteName = "뽐뿌해외",
            category = "여행.해외핫딜",
            hasOptions = true,
            optionsData = listOf(
                DealOptionItem(name = "사은품", price = 0L),
                DealOptionItem(name = "본품", price = 50000L)
            )
        )
        val entity = DealEntity.fromDealItem(item)
        assertEquals("해외직구", entity.category)
        assertTrue(entity.hasOptions)
        assertEquals(2, entity.optionsCount)
    }

    // -----------------------------------------------------------------------
    // 6. DealDetailViewModel 0원 옵션 선택 시 isRecordLow == false & trackOutboundClick(price=0L) & 중복/null 제외
    // -----------------------------------------------------------------------
    @Test
    fun dealDetailViewModel_zeroPriceOptionSelectionDoesNotMarkRecordLowAndTracksZeroPrice() = runBlocking {
        val trackBodies = CopyOnWriteArrayList<String>()
        val server = LocalStubHttpServer { req ->
            when {
                req.path.startsWith("/api/community/deals/901/history") -> {
                    200 to """[
                        {"price":25000,"date":"2026-11-18"},
                        {"price":28000,"date":"2026-11-19"}
                    ]"""
                }
                req.path.startsWith("/api/community/deals/901") -> {
                    200 to """{
                        "id": 901,
                        "title": "0원 옵션 포함 딜",
                        "price": 0,
                        "currency": "KRW",
                        "site_name": "뽐뿌",
                        "ecommerce_url": "https://shop.example.com/main",
                        "post_url": "https://www.ppomppu.co.kr/zboard/view.php?id=ppomppu&no=901",
                        "history_count": 2,
                        "is_free": true,
                        "options_data": [
                            {"name": "사은품 증정", "price": 0, "currency": "KRW", "url": "https://shop.example.com/gift"},
                            {"name": "본품 특가", "price": 24000, "currency": "KRW", "url": "https://shop.example.com/item"}
                        ]
                    }"""
                }
                req.path.startsWith("/api/coupang/track-click") -> {
                    trackBodies.add(req.body)
                    200 to """{"status":"ok"}"""
                }
                else -> 404 to """{"detail":"Not Found"}"""
            }
        }.also { resourcesToClose += it }

        val scope = newUnconfinedScope()
        val viewModel = DealDetailViewModel(
            apiService = createApiService(server.baseUrl),
            deviceUuidProvider = { "device_m4_zero" },
            ioDispatcher = Dispatchers.Unconfined,
            coroutineScopeOverride = scope
        )

        viewModel.loadDealData(901)
        withTimeout(3000) {
            while (viewModel.uiState.value.isLoading) {
                delay(10)
            }
        }

        // 초기 선택(0원 옵션) -> isRecordLow는 반드시 false
        assertEquals(0, viewModel.uiState.value.selectedOptionIndex)
        assertFalse(viewModel.uiState.value.isRecordLow)

        // 24,000원 본품 옵션 선택(최저가 25,000원 이하) -> isRecordLow == true
        viewModel.selectOption(1)
        withTimeout(3000) {
            while (viewModel.uiState.value.isHistoryLoading || !viewModel.uiState.value.isRecordLow) {
                delay(10)
            }
        }
        assertTrue(viewModel.uiState.value.isRecordLow)

        // 다시 0원 옵션 선택 -> isRecordLow == false 복귀
        viewModel.selectOption(0)
        withTimeout(3000) {
            while (viewModel.uiState.value.isHistoryLoading || viewModel.uiState.value.isRecordLow) {
                delay(10)
            }
        }
        assertFalse(viewModel.uiState.value.isRecordLow)

        // trackOutboundClick(price = 0L) 정상 전송 확인
        viewModel.trackOutboundClick("https://shop.example.com/gift", price = 0L, currency = "KRW")
        withTimeout(3000) {
            while (trackBodies.isEmpty()) {
                delay(10)
            }
        }
        assertTrue(trackBodies.single().contains("\"price\":0"))

        // extractValidMultiOptions 대소문자/공백 무시 중복 제거 & DealSource(price == null) 제외 검증
        val dedupDeal = DealItem(
            id = 902,
            title = "중복/Null 필터",
            price = 10000L,
            siteName = "뽐뿌",
            sources = listOf(
                DealSource(siteName = "뽐뿌 - Option A", postUrl = "https://a.com", price = 10000L),
                DealSource(siteName = "뽐뿌 - optiona", postUrl = "https://b.com", price = 11000L),
                DealSource(siteName = "뽐뿌 - NullPrice", postUrl = "https://c.com", price = null),
                DealSource(siteName = "뽐뿌 - FreeOpt", postUrl = "https://d.com", price = 0L)
            )
        )
        val extracted = DealDetailViewModel.extractValidMultiOptions(dedupDeal)
        assertEquals(2, extracted.size)
        assertEquals(10000L, extracted[0].price)
        assertEquals(0L, extracted[1].price)
    }
}

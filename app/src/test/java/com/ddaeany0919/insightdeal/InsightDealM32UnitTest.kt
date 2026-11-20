package com.ddaeany0919.insightdeal

import com.ddaeany0919.insightdeal.core.security.DeviceIdentityStore
import com.ddaeany0919.insightdeal.core.security.EncryptedDeviceIdentityStore
import com.ddaeany0919.insightdeal.data.LocalDataProvider
import com.ddaeany0919.insightdeal.models.DealItem
import com.ddaeany0919.insightdeal.network.ApiCommentCreate
import com.ddaeany0919.insightdeal.network.ApiDealComment
import com.ddaeany0919.insightdeal.network.ApiDealVotes
import com.ddaeany0919.insightdeal.network.ApiDeleteCommentResponse
import com.ddaeany0919.insightdeal.network.ApiService
import com.ddaeany0919.insightdeal.network.HotDealsResponse
import com.ddaeany0919.insightdeal.network.MetaCategoriesResponse
import com.ddaeany0919.insightdeal.network.NetworkModule
import com.ddaeany0919.insightdeal.presentation.DealDetailViewModel
import com.ddaeany0919.insightdeal.presentation.formatDetailPriceText
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
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
import java.net.InetAddress
import java.net.ServerSocket
import java.nio.charset.StandardCharsets
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

class InsightDealM32UnitTest {

    private class InMemoryDeviceIdentityStore(
        initialUuid: String? = null,
        initialToken: String? = null
    ) : DeviceIdentityStore {
        @Volatile
        private var uuid: String? = initialUuid

        @Volatile
        private var token: String? = initialToken

        @Volatile
        private var fcmToken: String? = null

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
            synchronized(lock) {
                this.token = token.trim()
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
            synchronized(lock) {
                this.fcmToken = fcmToken.trim()
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
                            socket.soTimeout = 5000
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
                                401 -> "Unauthorized"
                                403 -> "Forbidden"
                                500 -> "Internal Server Error"
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
            runCatching { acceptThread.interrupt() }
            runCatching { acceptThread.join(500) }
        }
    }

    private fun createApiService(server: LocalStubHttpServer, store: DeviceIdentityStore): ApiService {
        val gson = GsonBuilder()
            .setLenient()
            .setDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'")
            .create()
        val client = NetworkModule.createOkHttpClient(
            baseUrl = server.baseUrl,
            identityStoreProvider = { store },
            registrationClient = OkHttpClient.Builder().build()
        )
        return Retrofit.Builder()
            .baseUrl(server.baseUrl)
            .client(client)
            .addConverterFactory(GsonConverterFactory.create(gson))
            .build()
            .create(ApiService::class.java)
    }

    private fun waitUntil(timeoutMs: Long = 10000L, condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            if (condition()) return
            Thread.sleep(10)
        }
        assertTrue("비동기 상태 조건이 ${timeoutMs}ms 내에 충족되지 않았습니다.", condition())
    }

    private val activeScopes = CopyOnWriteArrayList<CoroutineScope>()

    private fun newTestScope(): CoroutineScope {
        return CoroutineScope(SupervisorJob() + Dispatchers.Unconfined).also {
            activeScopes.add(it)
        }
    }

    @After
    fun tearDown() {
        LocalDataProvider.setDeviceIdentityStoreForTest(null)
        activeScopes.forEach { it.cancel() }
        activeScopes.clear()
    }

    @Test
    fun dealItemAndCommunityDtos_deserializeBothSnakeAndCamelCaseWith1RttSummary() {
        val gson = Gson()

        // 1. DealItem camelCase + snake_case 혼합 1-RTT 요약 필 파싱
        val camelDealJson = """
            {
              "id": 77,
              "title": "[스팀] 인디 게임 무료 배포",
              "price": 0,
              "currency": "KRW",
              "site_name": "루리웹",
              "createdAt": "2026-11-19T10:00:00Z",
              "viewCount": 1234,
              "commentCount": 8,
              "likeCount": 45,
              "dislikeCount": 2,
              "honeyScore": 120,
              "buyCount": 19,
              "saveCount": 3,
              "userVote": "buy",
              "historyCount": 5,
              "isSearchFallback": true,
              "isFree": true
            }
        """.trimIndent()
        val camelDeal = gson.fromJson(camelDealJson, DealItem::class.java)
        assertEquals("2026-11-19T10:00:00Z", camelDeal.createdAt)
        assertEquals(1234, camelDeal.viewCount)
        assertEquals(8, camelDeal.commentCount)
        assertEquals(45, camelDeal.likeCount)
        assertEquals(2, camelDeal.dislikeCount)
        assertEquals(120, camelDeal.honeyScore)
        assertEquals(19, camelDeal.buyCount)
        assertEquals(3, camelDeal.passCount)
        assertEquals("buy", camelDeal.userVote)
        assertEquals(5, camelDeal.historyCount)
        assertTrue(camelDeal.isSearchFallback)
        assertTrue(camelDeal.isFree)

        // 1-2. DealItem 생략 시 안전한 기본값
        val minimalDeal = gson.fromJson(
            """{"id":1,"title":"기본 딜","price":10000,"site_name":"뽐뿌"}""",
            DealItem::class.java
        )
        assertEquals(0, minimalDeal.buyCount)
        assertEquals(0, minimalDeal.passCount)
        assertNull(minimalDeal.userVote)
        assertEquals(0, minimalDeal.historyCount)
        assertFalse(minimalDeal.isSearchFallback)
        assertFalse(minimalDeal.isFree)

        // 2. ApiDealComment snake_case & camelCase 양방향 파싱
        val camelComment = gson.fromJson(
            """{"id":11,"dealId":77,"userId":"device_abc","nickname":"핫딜고수","content":"무료 좋아요","createdAt":"2026-11-19T10:01:00Z"}""",
            ApiDealComment::class.java
        )
        assertEquals(11, camelComment.id)
        assertEquals(77, camelComment.deal_id)
        assertEquals("device_abc", camelComment.user_id)
        assertEquals("핫딜고수", camelComment.nickname)
        assertEquals("2026-11-19T10:01:00Z", camelComment.created_at)

        val snakeComment = gson.fromJson(
            """{"id":12,"deal_id":77,"user_id":"device_xyz","nickname":"익명의찰떡이","content":"추천합니다","created_at":"2026-11-19T10:02:00Z"}""",
            ApiDealComment::class.java
        )
        assertEquals("device_xyz", snakeComment.user_id)
        assertEquals("익명의찰떡이", snakeComment.nickname)

        // 3. ApiDealVotes saveCount / passCount / buyCount / userVote 양방향 파싱
        val camelVotes = gson.fromJson(
            """{"buyCount":15,"saveCount":4,"userVote":"save"}""",
            ApiDealVotes::class.java
        )
        assertEquals(15, camelVotes.buy_count)
        assertEquals(4, camelVotes.pass_count)
        assertEquals("save", camelVotes.user_vote)

        val snakeVotes = gson.fromJson(
            """{"buy_count":20,"pass_count":7,"user_vote":"buy"}""",
            ApiDealVotes::class.java
        )
        assertEquals(20, snakeVotes.buy_count)
        assertEquals(7, snakeVotes.pass_count)
        assertEquals("buy", snakeVotes.user_vote)

        // 4. HotDealsResponse has_more / hasMore / 기본값 검증
        val hotDealsSnake = gson.fromJson("""{"deals":[],"has_more":true}""", HotDealsResponse::class.java)
        assertTrue(hotDealsSnake.hasMore)
        val hotDealsCamel = gson.fromJson("""{"deals":[],"hasMore":true}""", HotDealsResponse::class.java)
        assertTrue(hotDealsCamel.hasMore)
        val hotDealsDefault = gson.fromJson("""{"deals":[]}""", HotDealsResponse::class.java)
        assertFalse(hotDealsDefault.hasMore)

        // 5. MetaCategoriesResponse & ApiCommentCreate & ApiDeleteCommentResponse
        val metaRes = gson.fromJson(
            """{"categories":[{"key":"가전제품","label":"가전/TV"},{"key":"해외직구","label":"해외직구"}],"sources":["전체","빠삭육아"]}""",
            MetaCategoriesResponse::class.java
        )
        assertEquals(2, metaRes.categories.size)
        assertEquals("가전/TV", metaRes.categories[0].label)
        assertTrue(metaRes.sources.contains("빠삭육아"))

        val commentCreateJson = gson.toJson(ApiCommentCreate(user_id = "device_111", content = "테스트", nickname = "회원_111"))
        assertTrue(commentCreateJson.contains("\"nickname\":\"회원_111\""))

        val deleteRes = gson.fromJson(
            """{"status":"ok","message":"Comment deleted successfully","id":99,"dealId":77}""",
            ApiDeleteCommentResponse::class.java
        )
        assertEquals(99, deleteRes.id)
        assertEquals(77, deleteRes.dealId)
    }

    @Test
    fun dealDetailViewModel_usesSingleDeviceUuidSsotAndMatchesXDeviceTokenHeader() {
        val store = InMemoryDeviceIdentityStore(
            initialUuid = "device_m32_ssot_01",
            initialToken = "signed_token_ssot_01"
        )
        LocalDataProvider.setDeviceIdentityStoreForTest(store)

        val server = LocalStubHttpServer { req ->
            when {
                req.method == "GET" && req.path.startsWith("/api/community/deals/42") && !req.path.contains("/comments") && !req.path.contains("/votes") -> {
                    200 to """{"id":42,"title":"SSOT 테스트 딜","price":19900,"site_name":"뽐뿌","buy_count":3,"pass_count":1,"comment_count":0,"history_count":0}"""
                }
                req.method == "POST" && req.path == "/api/community/deals/42/votes" -> {
                    200 to """{"buy_count":4,"pass_count":1,"user_vote":"buy"}"""
                }
                req.method == "POST" && req.path == "/api/community/deals/42/comments" -> {
                    200 to """{"id":501,"deal_id":42,"user_id":"device_m32_ssot_01","nickname":"회원_t_01","content":"좋아요","created_at":"2026-11-19T11:00:00Z"}"""
                }
                req.method == "DELETE" && req.path == "/api/community/deals/42/comments/501" -> {
                    200 to """{"status":"ok","message":"Comment deleted successfully","id":501,"deal_id":42}"""
                }
                else -> 404 to """{"detail":"Not found"}"""
            }
        }

        try {
            val apiService = createApiService(server, store)
            val vm = DealDetailViewModel(
                apiService = apiService,
                ioDispatcher = Dispatchers.Unconfined,
                coroutineScopeOverride = newTestScope()
            )

            vm.loadDealData(42)
            waitUntil { !vm.uiState.value.isLoading && vm.uiState.value.deal != null }
            assertEquals("device_m32_ssot_01", vm.uiState.value.currentDeviceUuid)
            assertFalse(vm.uiState.value.currentDeviceUuid.startsWith("anon_"))

            vm.vote("buy")
            waitUntil { !vm.uiState.value.isVoting && vm.uiState.value.userVote == "buy" }

            vm.addComment("좋아요")
            waitUntil { !vm.uiState.value.isSubmittingComment && vm.uiState.value.comments.size == 1 }

            vm.deleteComment(501)
            waitUntil { vm.uiState.value.deletingCommentId == null && vm.uiState.value.comments.isEmpty() }

            val detailReq = server.recordedRequests.first { it.method == "GET" && it.path.startsWith("/api/community/deals/42") }
            assertTrue("getEnhancedDealInfo는 user_id 쿼리를 포함해야 합니다.", detailReq.path.contains("user_id=device_m32_ssot_01"))
            assertEquals("signed_token_ssot_01", detailReq.headers["x-device-token"])

            val voteReq = server.recordedRequests.first { it.method == "POST" && it.path == "/api/community/deals/42/votes" }
            assertTrue(voteReq.body.contains("\"user_id\":\"device_m32_ssot_01\""))
            assertFalse(voteReq.body.contains("anon_"))
            assertEquals("signed_token_ssot_01", voteReq.headers["x-device-token"])

            val commentReq = server.recordedRequests.first { it.method == "POST" && it.path == "/api/community/deals/42/comments" }
            assertTrue(commentReq.body.contains("\"user_id\":\"device_m32_ssot_01\""))
            assertFalse(commentReq.body.contains("anon_"))
            assertEquals("signed_token_ssot_01", commentReq.headers["x-device-token"])

            val deleteReq = server.recordedRequests.first { it.method == "DELETE" && it.path == "/api/community/deals/42/comments/501" }
            assertFalse(deleteReq.path.contains("user_id="))
            assertEquals("signed_token_ssot_01", deleteReq.headers["x-device-token"])
        } finally {
            server.close()
        }
    }

    @Test
    fun dealDetailViewModel_loadDealDataUses1RttAndLazyFetchesCommentsOnce() {
        val store = InMemoryDeviceIdentityStore(
            initialUuid = "device_m32_1rtt_02",
            initialToken = "tok_1rtt_02"
        )
        val commentsFetchCount = AtomicInteger(0)

        val server = LocalStubHttpServer { req ->
            when {
                req.method == "GET" && req.path.startsWith("/api/community/deals/88") && !req.path.contains("/comments") && !req.path.contains("/votes") && !req.path.contains("/history") -> {
                    200 to """{
                        "id": 88,
                        "title": "1-RTT 테스트 상품",
                        "price": 15900,
                        "site_name": "펨코",
                        "buy_count": 12,
                        "pass_count": 4,
                        "user_vote": "save",
                        "comment_count": 2,
                        "history_count": 0,
                        "is_search_fallback": true,
                        "is_free": false
                    }""".trimIndent()
                }
                req.method == "GET" && req.path == "/api/community/deals/88/comments" -> {
                    commentsFetchCount.incrementAndGet()
                    200 to """[
                        {"id":1,"deal_id":88,"user_id":"device_other","nickname":"유저1","content":"괜찮네요","created_at":"2026-11-19T10:00:00Z"},
                        {"id":2,"deal_id":88,"user_id":"device_m32_1rtt_02","nickname":"내닉네임","content":"샀어요","created_at":"2026-11-19T10:05:00Z"}
                    ]""".trimIndent()
                }
                else -> 404 to """{"detail":"Unexpected path ${req.path}"}"""
            }
        }

        try {
            val apiService = createApiService(server, store)
            val vm = DealDetailViewModel(
                apiService = apiService,
                deviceUuidProvider = { store.getOrCreateDeviceUuid() },
                ioDispatcher = Dispatchers.Unconfined,
                coroutineScopeOverride = newTestScope()
            )

            vm.loadDealData(88)
            waitUntil { !vm.uiState.value.isLoading && vm.uiState.value.deal != null }

            // 1-RTT 요약 필 즉시 반영 및 /comments, /votes, /history(0건) 미호출 검증
            val state = vm.uiState.value
            assertEquals(12, state.buyCount)
            assertEquals(4, state.passCount)
            assertEquals("save", state.userVote)
            assertEquals(2, state.commentCount)
            assertEquals(0, state.historyCount)
            assertTrue(state.isSearchFallback)
            assertFalse(state.isFree)
            assertFalse(state.commentsLoaded)
            assertEquals(1, server.recordedRequests.size)
            assertEquals(0, commentsFetchCount.get())

            // [안 B] 아코디언 펼침 시 Lazy-fetch 1회만 실행, 재호출 시 캐시 사용
            vm.loadCommentsIfNeeded()
            waitUntil { vm.uiState.value.commentsLoaded && !vm.uiState.value.isCommentsLoading }
            assertEquals(2, vm.uiState.value.comments.size)
            assertEquals(1, commentsFetchCount.get())

            vm.loadCommentsIfNeeded()
            assertEquals(1, commentsFetchCount.get())

            // 강제 새로고침 시 재조회
            vm.loadCommentsIfNeeded(forceRefresh = true)
            waitUntil { commentsFetchCount.get() == 2 }
        } finally {
            server.close()
        }
    }

    @Test
    fun dealDetailViewModel_voteNormalizesPassToSaveAndRollsBackOnServerFailure() {
        val store = InMemoryDeviceIdentityStore(
            initialUuid = "device_m32_vote_03",
            initialToken = "tok_vote_03"
        )
        val shouldFailVote = AtomicBoolean(false)

        val server = LocalStubHttpServer { req ->
            when {
                req.method == "GET" && req.path.startsWith("/api/community/deals/99") -> {
                    200 to """{"id":99,"title":"투표 롤백 테스트","price":29000,"site_name":"퀘이사존","buy_count":5,"pass_count":2,"user_vote":null,"history_count":0}"""
                }
                req.method == "POST" && req.path == "/api/community/deals/99/votes" -> {
                    if (shouldFailVote.get()) {
                        500 to """{"detail":"Database unavailable"}"""
                    } else if (req.body.contains("\"vote_type\":\"save\"")) {
                        200 to """{"buy_count":5,"pass_count":3,"user_vote":"save"}"""
                    } else {
                        200 to """{"buy_count":6,"pass_count":2,"user_vote":"buy"}"""
                    }
                }
                else -> 404 to """{"detail":"Not found"}"""
            }
        }

        try {
            val apiService = createApiService(server, store)
            val vm = DealDetailViewModel(
                apiService = apiService,
                deviceUuidProvider = { store.getOrCreateDeviceUuid() },
                ioDispatcher = Dispatchers.Unconfined,
                coroutineScopeOverride = newTestScope()
            )

            vm.loadDealData(99)
            waitUntil { !vm.uiState.value.isLoading && vm.uiState.value.deal != null }

            // "pass" 입력이 "save"로 정규화되어 전송 및 상태 반영
            vm.vote("pass")
            waitUntil { !vm.uiState.value.isVoting && vm.uiState.value.userVote == "save" }
            assertEquals(5, vm.uiState.value.buyCount)
            assertEquals(3, vm.uiState.value.passCount)
            assertNull(vm.uiState.value.voteError)

            val sentVoteReq = server.recordedRequests.last { it.path == "/api/community/deals/99/votes" }
            assertTrue(sentVoteReq.body.contains("\"vote_type\":\"save\""))

            // 서버 500 장애 시 직전 상태(buy=5, pass=3, userVote="save")로 롤백 및 에러 노출
            shouldFailVote.set(true)
            vm.vote("buy")
            waitUntil { !vm.uiState.value.isVoting && vm.uiState.value.voteError != null }

            assertEquals(5, vm.uiState.value.buyCount)
            assertEquals(3, vm.uiState.value.passCount)
            assertEquals("save", vm.uiState.value.userVote)
            assertNotNull(vm.uiState.value.voteError)
        } finally {
            server.close()
        }
    }

    @Test
    fun dealDetailViewModel_addAndDeleteCommentHandles403AndBlankGuard() {
        val store = InMemoryDeviceIdentityStore(
            initialUuid = "device_m32_cmt_04",
            initialToken = "tok_cmt_04"
        )
        val server = LocalStubHttpServer { req ->
            when {
                req.method == "GET" && req.path.startsWith("/api/community/deals/105") && !req.path.contains("/comments") -> {
                    200 to """{"id":105,"title":"댓글 CRUD 테스트","price":12000,"site_name":"클리앙","comment_count":1,"history_count":0}"""
                }
                req.method == "GET" && req.path == "/api/community/deals/105/comments" -> {
                    200 to """[{"id":200,"deal_id":105,"user_id":"other_user","nickname":"타인","content":"기존 의견","created_at":"2026-11-19T09:00:00Z"}]"""
                }
                req.method == "POST" && req.path == "/api/community/deals/105/comments" -> {
                    200 to """{"id":201,"deal_id":105,"user_id":"device_m32_cmt_04","nickname":"회원_t_04","content":"새 한줄평","created_at":"2026-11-19T11:10:00Z"}"""
                }
                req.method == "DELETE" && req.path == "/api/community/deals/105/comments/200" -> {
                    403 to """{"detail":"Not authorized to delete this comment"}"""
                }
                req.method == "DELETE" && req.path == "/api/community/deals/105/comments/201" -> {
                    200 to """{"status":"ok","message":"Comment deleted successfully","id":201,"deal_id":105}"""
                }
                else -> 404 to """{"detail":"Not found"}"""
            }
        }

        try {
            val apiService = createApiService(server, store)
            val vm = DealDetailViewModel(
                apiService = apiService,
                deviceUuidProvider = { store.getOrCreateDeviceUuid() },
                ioDispatcher = Dispatchers.Unconfined,
                coroutineScopeOverride = newTestScope()
            )

            vm.loadDealData(105)
            waitUntil { !vm.uiState.value.isLoading && vm.uiState.value.deal != null }

            vm.loadCommentsIfNeeded()
            waitUntil { vm.uiState.value.commentsLoaded }

            // 공백 입력 가드
            val beforeRequests = server.recordedRequests.size
            vm.addComment("   ")
            assertEquals(beforeRequests, server.recordedRequests.size)

            // 정상 댓글 등록
            vm.addComment("  새 한줄평  ")
            waitUntil { !vm.uiState.value.isSubmittingComment && vm.uiState.value.comments.size == 2 }
            assertEquals(201, vm.uiState.value.comments.first().id)
            assertEquals(2, vm.uiState.value.commentCount)

            // 타인 댓글 삭제 시도 -> 403 에러 메시지, 목록 보존
            vm.deleteComment(200)
            waitUntil { vm.uiState.value.deletingCommentId == null && vm.uiState.value.commentActionError != null }
            assertEquals("본인이 작성한 의견만 삭제할 수 있어요.", vm.uiState.value.commentActionError)
            assertEquals(2, vm.uiState.value.comments.size)

            // 본인 댓글 삭제 성공 -> 목록 제거 및 카운트 감소
            vm.deleteComment(201)
            waitUntil { vm.uiState.value.deletingCommentId == null && vm.uiState.value.comments.size == 1 }
            assertNull(vm.uiState.value.commentActionError)
            assertEquals(1, vm.uiState.value.commentCount)
            assertEquals(200, vm.uiState.value.comments.single().id)
        } finally {
            server.close()
        }
    }

    @Test
    fun formatDetailPriceText_preservesZeroWonDisplayWithoutBreakingFormatPrice() {
        assertEquals("0원", formatDetailPriceText(0L, "KRW", "사은품 증정", isFree = false))
        assertEquals("0원 (무료)", formatDetailPriceText(0L, "KRW", "스팀 무료 배포", isFree = true))
        assertEquals("15,000원", formatDetailPriceText(15000L, "KRW", "일반 상품", isFree = false))
    }

    @Test
    fun dealDetailViewModel_addCommentPreservesSubmitSeqOnFailureAndIncrementsOnRetrySuccess() {
        val store = InMemoryDeviceIdentityStore(
            initialUuid = "device_m32_retry_05",
            initialToken = "tok_retry_05"
        )
        val shouldFailComment = AtomicBoolean(true)

        val server = LocalStubHttpServer { req ->
            when {
                req.method == "GET" && req.path.startsWith("/api/community/deals/110") && !req.path.contains("/comments") -> {
                    200 to """{"id":110,"title":"한줄평 실패 시 입력 보존 테스트","price":15000,"site_name":"뽐뿌","comment_count":0,"history_count":0}"""
                }
                req.method == "POST" && req.path == "/api/community/deals/110/comments" -> {
                    if (shouldFailComment.get()) {
                        500 to """{"detail":"Temporary write error"}"""
                    } else {
                        200 to """{"id":301,"deal_id":110,"user_id":"device_m32_retry_05","nickname":"회원_y_05","content":"재시도 성공 테스트","created_at":"2026-11-19T11:20:00Z"}"""
                    }
                }
                else -> 404 to """{"detail":"Not found"}"""
            }
        }

        try {
            val apiService = createApiService(server, store)
            val vm = DealDetailViewModel(
                apiService = apiService,
                deviceUuidProvider = { store.getOrCreateDeviceUuid() },
                ioDispatcher = Dispatchers.Unconfined,
                coroutineScopeOverride = newTestScope()
            )

            vm.loadDealData(110)
            waitUntil { !vm.uiState.value.isLoading && vm.uiState.value.deal != null }
            assertEquals(0, vm.uiState.value.commentSubmitSuccessSeq)

            // 1차 등록 실패: commentSubmitSuccessSeq가 0으로 유지되고 commentActionError가 설정되어야 함
            vm.addComment("실패 시 보존 테스트")
            waitUntil { !vm.uiState.value.isSubmittingComment && vm.uiState.value.commentActionError != null }
            assertEquals(0, vm.uiState.value.commentSubmitSuccessSeq)
            assertNotNull(vm.uiState.value.commentActionError)
            assertEquals(0, vm.uiState.value.comments.size)

            // 2차 재시도 성공: 이때만 commentSubmitSuccessSeq가 1로 증가해야 함
            shouldFailComment.set(false)
            vm.addComment("재시도 성공 테스트")
            waitUntil { !vm.uiState.value.isSubmittingComment && vm.uiState.value.commentSubmitSuccessSeq == 1 }
            assertEquals(1, vm.uiState.value.commentSubmitSuccessSeq)
            assertNull(vm.uiState.value.commentActionError)
            assertEquals(1, vm.uiState.value.comments.size)
            assertEquals(301, vm.uiState.value.comments.single().id)
        } finally {
            server.close()
        }
    }

    @Test
    fun dealDetailViewModel_addCommentDoesNotHideUnloadedCommentsWhenInitialFetchFailed() {
        val store = InMemoryDeviceIdentityStore(
            initialUuid = "device_m32_recover_06",
            initialToken = "tok_recover_06"
        )
        val failCommentsGet = AtomicBoolean(true)
        val commentsGetCount = AtomicInteger(0)

        val server = LocalStubHttpServer { req ->
            when {
                req.method == "GET" && req.path.startsWith("/api/community/deals/112") && !req.path.contains("/comments") -> {
                    200 to """{"id":112,"title":"미로드 댓글 은폐 방지 테스트","price":22000,"site_name":"루리웹","comment_count":2,"history_count":0}"""
                }
                req.method == "GET" && req.path == "/api/community/deals/112/comments" -> {
                    commentsGetCount.incrementAndGet()
                    if (failCommentsGet.get()) {
                        500 to """{"detail":"Comments temporarily unavailable"}"""
                    } else {
                        200 to """[
                            {"id":403,"deal_id":112,"user_id":"device_m32_recover_06","nickname":"회원_r_06","content":"추가 의견","created_at":"2026-11-19T11:30:00Z"},
                            {"id":402,"deal_id":112,"user_id":"other_2","nickname":"유저2","content":"기존 의견2","created_at":"2026-11-19T10:30:00Z"},
                            {"id":401,"deal_id":112,"user_id":"other_1","nickname":"유저1","content":"기존 의견1","created_at":"2026-11-19T09:30:00Z"}
                        ]""".trimIndent()
                    }
                }
                req.method == "POST" && req.path == "/api/community/deals/112/comments" -> {
                    200 to """{"id":403,"deal_id":112,"user_id":"device_m32_recover_06","nickname":"회원_r_06","content":"추가 의견","created_at":"2026-11-19T11:30:00Z"}"""
                }
                else -> 404 to """{"detail":"Not found"}"""
            }
        }

        try {
            val apiService = createApiService(server, store)
            val vm = DealDetailViewModel(
                apiService = apiService,
                deviceUuidProvider = { store.getOrCreateDeviceUuid() },
                ioDispatcher = Dispatchers.Unconfined,
                coroutineScopeOverride = newTestScope()
            )

            vm.loadDealData(112)
            waitUntil { !vm.uiState.value.isLoading && vm.uiState.value.deal != null }
            assertEquals(2, vm.uiState.value.commentCount)

            // 초기 댓글 조회 실패 -> commentsLoaded=false 유지
            vm.loadCommentsIfNeeded()
            waitUntil { !vm.uiState.value.isCommentsLoading && vm.uiState.value.commentsError != null && commentsGetCount.get() >= 1 }
            assertFalse(vm.uiState.value.commentsLoaded)
            assertEquals(2, vm.uiState.value.commentCount)

            // 1) 재조회도 여전히 실패하는 상태에서는 commentsLoaded=true로 덮어쓰지 않고 commentCount=3(2+1)을 유지
            //    (내부 recovery GET까지 완전히 종료된 후 다음 단계로 진행해 레이스 컨디션 차단)
            vm.addComment("추가 의견")
            waitUntil {
                !vm.uiState.value.isSubmittingComment &&
                    vm.uiState.value.commentSubmitSuccessSeq == 1 &&
                    !vm.uiState.value.isCommentsLoading &&
                    commentsGetCount.get() >= 2
            }
            assertFalse(vm.uiState.value.commentsLoaded)
            assertEquals(3, vm.uiState.value.commentCount)

            // 2) 재조회가 복구된 상태에서 등록 시 기존 2개+신규 1개 전체 3개 복구
            failCommentsGet.set(false)
            vm.addComment("추가 의견")
            waitUntil {
                !vm.uiState.value.isSubmittingComment &&
                    !vm.uiState.value.isCommentsLoading &&
                    vm.uiState.value.commentsLoaded &&
                    vm.uiState.value.comments.size == 3 &&
                    vm.uiState.value.commentSubmitSuccessSeq == 2
            }
            assertTrue(vm.uiState.value.commentsLoaded)
            assertEquals(3, vm.uiState.value.commentCount)
            assertNull(vm.uiState.value.commentsError)
            assertEquals(2, vm.uiState.value.commentSubmitSuccessSeq)
        } finally {
            server.close()
        }
    }

    @Test
    fun dealDetailViewModel_loadDealDataCancelsPreviousDetailJobAndSiteNamesNullSafe() {
        val gson = Gson()
        val omittedSiteNamesDeal = gson.fromJson(
            """{"id":202,"title":"site_names 생략 딜","price":9900,"site_name":"펨코"}""",
            DealItem::class.java
        )
        val safeSiteNames = (omittedSiteNamesDeal.siteNames as List<String>?) ?: emptyList()
        assertTrue(safeSiteNames.isEmpty())

        val store = InMemoryDeviceIdentityStore(
            initialUuid = "device_m32_cancel_07",
            initialToken = "tok_cancel_07"
        )
        val slowRequestStarted = AtomicBoolean(false)
        val slowResponseDone = AtomicBoolean(false)
        val server = LocalStubHttpServer { req ->
            when {
                req.method == "GET" && req.path.startsWith("/api/community/deals/201") -> {
                    slowRequestStarted.set(true)
                    try {
                        Thread.sleep(150)
                        200 to """{"id":201,"title":"느린 이전 딜","price":10000,"site_name":"뽐뿌","history_count":0}"""
                    } finally {
                        slowResponseDone.set(true)
                    }
                }
                req.method == "GET" && req.path.startsWith("/api/community/deals/202") -> {
                    200 to """{"id":202,"title":"최신 빠른 딜","price":20000,"site_name":"퀘이사존","history_count":0}"""
                }
                else -> 404 to """{"detail":"Not found"}"""
            }
        }

        try {
            val apiService = createApiService(server, store)
            val asyncScope = CoroutineScope(SupervisorJob() + Dispatchers.Default).also {
                activeScopes.add(it)
            }
            val vm = DealDetailViewModel(
                apiService = apiService,
                deviceUuidProvider = { store.getOrCreateDeviceUuid() },
                ioDispatcher = Dispatchers.IO,
                coroutineScopeOverride = asyncScope
            )

            vm.loadDealData(201)
            vm.loadDealData(202)
            waitUntil { !vm.uiState.value.isLoading && vm.uiState.value.deal?.id == 202 }
            if (slowRequestStarted.get()) {
                waitUntil { slowResponseDone.get() }
                Thread.sleep(50)
            }
            assertEquals(202, vm.uiState.value.deal?.id)
            assertEquals("최신 빠른 딜", vm.uiState.value.deal?.title)
        } finally {
            server.close()
        }
    }
}

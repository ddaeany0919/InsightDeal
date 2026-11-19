package com.ddaeany0919.insightdeal

import android.content.ContextWrapper
import com.ddaeany0919.insightdeal.core.security.DeviceIdProvider
import com.ddaeany0919.insightdeal.core.security.DeviceIdentityStore
import com.ddaeany0919.insightdeal.core.security.EncryptedDeviceIdentityStore
import com.ddaeany0919.insightdeal.data.LocalDataProvider
import com.ddaeany0919.insightdeal.network.DeviceRegistrationResponse
import com.ddaeany0919.insightdeal.network.NetworkModule
import com.ddaeany0919.insightdeal.network.RegisterDeviceReq
import com.ddaeany0919.insightdeal.network.UpdateDndReq
import com.ddaeany0919.insightdeal.presentation.mypage.history.NotificationHistoryManager
import com.ddaeany0919.insightdeal.presentation.webview.resolveMobileWebViewUrl
import com.google.gson.Gson
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.runBlocking
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.BufferedInputStream
import java.io.Closeable
import java.net.InetAddress
import java.net.ServerSocket
import java.nio.charset.StandardCharsets
import java.util.concurrent.Callable
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

class InsightDealM31UnitTest {

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
        val port: Int get() = serverSocket.localPort
        val baseUrl: String get() = "http://127.0.0.1:$port/"
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
                                401 -> "Unauthorized"
                                403 -> "Forbidden"
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
        }
    }

    @After
    fun tearDown() {
        LocalDataProvider.setDeviceIdentityStoreForTest(null)
    }

    @Test
    fun deviceUuidSsot_isThreadSafeAndSharedAcrossProviders() {
        val store = InMemoryDeviceIdentityStore()
        LocalDataProvider.setDeviceIdentityStoreForTest(store)

        val threadCount = 16
        val executor = Executors.newFixedThreadPool(threadCount)
        val startLatch = CountDownLatch(1)
        val futures = (1..threadCount).map {
            executor.submit(
                Callable {
                    startLatch.await(3, TimeUnit.SECONDS)
                    LocalDataProvider.getDeviceUuid()
                }
            )
        }
        startLatch.countDown()
        val uuids = futures.map { it.get(5, TimeUnit.SECONDS) }.toSet()
        executor.shutdownNow()

        assertEquals(1, uuids.size)
        val singleUuid = uuids.first()
        assertTrue(singleUuid.startsWith("device_"))
        assertEquals(19, singleUuid.length)
        assertEquals(singleUuid, DeviceIdProvider.getOrCreateDeviceUuid())

        DeviceIdProvider.saveDeviceToken("tok_abc_123", singleUuid)
        assertEquals("tok_abc_123", LocalDataProvider.getDeviceToken())
        DeviceIdProvider.clearDeviceToken()
        assertNull(LocalDataProvider.getDeviceToken())
    }

    @Test
    fun networkModule_injectsTokenOnlyOnSameHostAndAutoRegistersOnceWithoutRecursion() {
        val store = InMemoryDeviceIdentityStore(initialFcmToken = null)
        val registerCallCount = AtomicInteger(0)

        val backendServer = LocalStubHttpServer { req ->
            when (req.path) {
                "/api/push/device" -> {
                    registerCallCount.incrementAndGet()
                    200 to """{"status":"ok","device_uuid":"${store.getOrCreateDeviceUuid()}","device_token":"signed_hmac_token_001"}"""
                }
                else -> 200 to """{"ok":true}"""
            }
        }
        val externalCdnServer = LocalStubHttpServer {
            200 to """{"cdn":"ok"}"""
        }

        try {
            val registrationClient = OkHttpClient.Builder().build()
            val client = NetworkModule.createOkHttpClient(
                baseUrl = backendServer.baseUrl,
                identityStoreProvider = { store },
                registrationClient = registrationClient
            )

            // 1. 토큰 미발급 상태에서 2개의 동시 보호 요청 -> 자동 등록은 정확히 1회만 수행되어야 함
            val executor = Executors.newFixedThreadPool(2)
            val latch = CountDownLatch(1)
            val jsonMedia = "application/json; charset=utf-8".toMediaType()
            val calls = (1..2).map {
                executor.submit(
                    Callable {
                        latch.await(3, TimeUnit.SECONDS)
                        val req = Request.Builder()
                            .url("${backendServer.baseUrl}api/wishlist/from-keyword")
                            .post("""{"keyword":"맥북","target_price":1000000,"user_id":"${store.getOrCreateDeviceUuid()}"}""".toRequestBody(jsonMedia))
                            .build()
                        client.newCall(req).execute().use { it.code }
                    }
                )
            }
            latch.countDown()
            val codes = calls.map { it.get(5, TimeUnit.SECONDS) }
            executor.shutdownNow()

            assertEquals(listOf(200, 200), codes)
            assertEquals(1, registerCallCount.get())

            val registerReq = backendServer.recordedRequests.first { it.path == "/api/push/device" }
            assertFalse("fcm_token은 빈 문자열('')로 전송되어서는 안 됩니다.", registerReq.body.contains("\"fcm_token\":\"\""))

            val wishlistReqs = backendServer.recordedRequests.filter { it.path == "/api/wishlist/from-keyword" }
            assertEquals(2, wishlistReqs.size)
            wishlistReqs.forEach { req ->
                assertEquals("signed_hmac_token_001", req.headers["x-device-token"])
            }

            // 2. Coil 등 외부 호스트 요청에는 X-Device-Token을 절대 첨부하지 않음
            val externalReq = Request.Builder()
                .url("${externalCdnServer.baseUrl}images/hotdeal_thumb.jpg")
                .get()
                .build()
            client.newCall(externalReq).execute().use { assertEquals(200, it.code) }
            val recordedExternal = externalCdnServer.recordedRequests.single()
            assertNull(recordedExternal.headers["x-device-token"])

            // 3. /api/push/device 직접 호출 시 재귀 등록 없이 저장된 토큰만 헤더에 첨부
            val directRegisterReq = Request.Builder()
                .url("${backendServer.baseUrl}api/push/device")
                .post("""{"device_uuid":"${store.getOrCreateDeviceUuid()}","fcm_token":"fcm_xyz"}""".toRequestBody(jsonMedia))
                .build()
            client.newCall(directRegisterReq).execute().use { assertEquals(200, it.code) }
            assertEquals(2, registerCallCount.get())
            val lastRegisterReq = backendServer.recordedRequests.last { it.path == "/api/push/device" }
            assertEquals("signed_hmac_token_001", lastRegisterReq.headers["x-device-token"])
        } finally {
            backendServer.close()
            externalCdnServer.close()
        }
    }

    @Test
    fun networkModule_doesNotRetryLoopWhenDeviceRegistrationReturns401() {
        val store = InMemoryDeviceIdentityStore()
        val registerCallCount = AtomicInteger(0)

        val backendServer = LocalStubHttpServer { req ->
            when (req.path) {
                "/api/push/device" -> {
                    registerCallCount.incrementAndGet()
                    401 to """{"detail":"Invalid device token"}"""
                }
                else -> 401 to """{"detail":"Unauthorized"}"""
            }
        }

        try {
            val client = NetworkModule.createOkHttpClient(
                baseUrl = backendServer.baseUrl,
                identityStoreProvider = { store },
                registrationClient = OkHttpClient.Builder().build()
            )
            val jsonMedia = "application/json; charset=utf-8".toMediaType()
            val req = Request.Builder()
                .url("${backendServer.baseUrl}api/push/dnd")
                .patch("""{"device_uuid":"${store.getOrCreateDeviceUuid()}","dnd_enabled":true}""".toRequestBody(jsonMedia))
                .build()

            val responseCode = client.newCall(req).execute().use { it.code }
            assertEquals(401, responseCode)
            assertEquals(1, registerCallCount.get())
            assertNull(store.getDeviceToken())
        } finally {
            backendServer.close()
        }
    }

    @Test
    fun gsonContracts_deviceRegistrationAndUpdateDndMatchBackendSchema() {
        val gson = Gson()

        val regReqJson = gson.toJson(
            RegisterDeviceReq(
                device_uuid = "device_1234567890ab",
                fcm_token = null,
                night_push_consent = true,
                dnd_enabled = true,
                dnd_start_time = "22:00",
                dnd_end_time = "08:00"
            )
        )
        assertTrue(regReqJson.contains("\"device_uuid\":\"device_1234567890ab\""))
        assertFalse(regReqJson.contains("\"fcm_token\":\"\""))

        val snakeRes = gson.fromJson(
            """{"status":"ok","device_uuid":"device_aaa","device_token":"tok_snake"}""",
            DeviceRegistrationResponse::class.java
        )
        assertEquals("device_aaa", snakeRes.deviceUuid)
        assertEquals("tok_snake", snakeRes.deviceToken)

        val camelRes = gson.fromJson(
            """{"status":"ok","deviceUuid":"device_bbb","deviceToken":"tok_camel"}""",
            DeviceRegistrationResponse::class.java
        )
        assertEquals("device_bbb", camelRes.deviceUuid)
        assertEquals("tok_camel", camelRes.deviceToken)

        val dndJson = gson.toJson(
            UpdateDndReq(
                device_uuid = "device_1234567890ab",
                dnd_enabled = true,
                dnd_start_time = "23:00",
                dnd_end_time = "07:00",
                dnd_settings_json = """{"mon":{"enabled":true}}"""
            )
        )
        assertTrue(dndJson.contains("\"device_uuid\":\"device_1234567890ab\""))
        assertTrue(dndJson.contains("\"dnd_enabled\":true"))
        assertTrue(dndJson.contains("\"dnd_start_time\":\"23:00\""))
        assertTrue(dndJson.contains("\"dnd_end_time\":\"07:00\""))
        assertFalse(dndJson.contains("fcm_token"))
    }

    @Test
    fun resolveMobileWebViewUrl_enforcesHttpsAndUpgradesLegacyMallUrls() {
        val auctionUrl = resolveMobileWebViewUrl("http://itempage3.auction.co.kr/DetailView.aspx?ItemNo=C123456")
        assertTrue(auctionUrl.startsWith("https://"))
        assertEquals("https://mitem.auction.co.kr/vip?itemNo=C123456", auctionUrl)

        val gmarketUrl = resolveMobileWebViewUrl("http://item.gmarket.co.kr/Item?goodscode=987654")
        assertTrue(gmarketUrl.startsWith("https://"))
        assertEquals("https://mitem.gmarket.co.kr/vi/product/987654", gmarketUrl)

        val st11Url = resolveMobileWebViewUrl("http://www.11st.co.kr/products/555666")
        assertEquals("https://m.11st.co.kr/products/m/555666", st11Url)

        val coupangUrl = resolveMobileWebViewUrl("http://www.coupang.com/vp/products/112233")
        assertEquals("https://m.coupang.com/vm/products/112233", coupangUrl)

        val genericHttpUrl = resolveMobileWebViewUrl("http://example.com/deals/100")
        assertEquals("https://example.com/deals/100", genericHttpUrl)

        assertNotNull(resolveMobileWebViewUrl("bbasak.com/bbs/board.php?bo_table=hotdeal&wr_id=1"))
    }

    @Test
    fun networkModule_coldStartConcurrentPushDeviceRegistration_serializesWithTokenLockWithout401() {
        val jsonMedia = "application/json; charset=utf-8".toMediaType()

        // 시나리오 1: 보호 API의 ensureDeviceRegisteredSync()가 먼저 tokenLock을 점유한 상태에서 POST /api/push/device가 동시 진입
        run {
            val store = InMemoryDeviceIdentityStore(initialUuid = "device_cold_000001", initialToken = null)
            val issued = AtomicBoolean(false)
            val registerCalls = AtomicInteger(0)
            val firstRegisterArrived = CountDownLatch(1)
            val allowFirstRegisterResponse = CountDownLatch(1)

            val server = LocalStubHttpServer { req ->
                when (req.path) {
                    "/api/push/device" -> {
                        val callIndex = registerCalls.incrementAndGet()
                        if (callIndex == 1) {
                            firstRegisterArrived.countDown()
                            allowFirstRegisterResponse.await(3, TimeUnit.SECONDS)
                        }
                        if (issued.compareAndSet(false, true)) {
                            200 to """{"status":"ok","device_uuid":"device_cold_000001","device_token":"tok_cold_001"}"""
                        } else if (req.headers["x-device-token"] == "tok_cold_001") {
                            200 to """{"status":"ok","device_uuid":"device_cold_000001","device_token":"tok_cold_001"}"""
                        } else {
                            401 to """{"detail":"Missing X-Device-Token header"}"""
                        }
                    }
                    "/api/wishlist/from-keyword" -> {
                        if (req.headers["x-device-token"] == "tok_cold_001") {
                            200 to """{"ok":true}"""
                        } else {
                            401 to """{"detail":"Missing X-Device-Token header"}"""
                        }
                    }
                    else -> 200 to """{"ok":true}"""
                }
            }

            try {
                val client = NetworkModule.createOkHttpClient(
                    baseUrl = server.baseUrl,
                    identityStoreProvider = { store },
                    registrationClient = OkHttpClient.Builder().build()
                )
                val protectedResult = AtomicInteger(-1)
                val directResult = AtomicInteger(-1)

                val t1 = Thread {
                    val req = Request.Builder()
                        .url("${server.baseUrl}api/wishlist/from-keyword")
                        .post("""{"keyword":"맥북","target_price":1000000,"user_id":"device_cold_000001"}""".toRequestBody(jsonMedia))
                        .build()
                    protectedResult.set(client.newCall(req).execute().use { it.code })
                }
                val t2 = Thread {
                    val req = Request.Builder()
                        .url("${server.baseUrl}api/push/device")
                        .post("""{"device_uuid":"device_cold_000001","fcm_token":"fcm_cold_start"}""".toRequestBody(jsonMedia))
                        .build()
                    directResult.set(client.newCall(req).execute().use { it.code })
                }

                t1.start()
                assertTrue(firstRegisterArrived.await(3, TimeUnit.SECONDS))
                t2.start()
                val deadline = System.currentTimeMillis() + 2000L
                while (t2.state != Thread.State.BLOCKED && t2.isAlive && System.currentTimeMillis() < deadline) {
                    Thread.sleep(5)
                }
                allowFirstRegisterResponse.countDown()
                t1.join(5000)
                t2.join(5000)

                assertEquals(200, protectedResult.get())
                assertEquals(200, directResult.get())
                assertEquals(2, registerCalls.get())
                val registerRequests = server.recordedRequests.filter { it.path == "/api/push/device" }
                assertEquals(2, registerRequests.size)
                assertEquals("tok_cold_001", registerRequests[1].headers["x-device-token"])
                assertTrue(registerRequests[1].body.contains("fcm_cold_start"))
            } finally {
                server.close()
            }
        }

        // 시나리오 2: 직접 POST /api/push/device가 먼저 tokenLock을 점유한 상태에서 보호 API가 동시 진입
        run {
            val store = InMemoryDeviceIdentityStore(initialUuid = "device_cold_000002", initialToken = null)
            val issued = AtomicBoolean(false)
            val registerCalls = AtomicInteger(0)
            val firstRegisterArrived = CountDownLatch(1)
            val allowFirstRegisterResponse = CountDownLatch(1)

            val server = LocalStubHttpServer { req ->
                when (req.path) {
                    "/api/push/device" -> {
                        val callIndex = registerCalls.incrementAndGet()
                        if (callIndex == 1) {
                            firstRegisterArrived.countDown()
                            allowFirstRegisterResponse.await(3, TimeUnit.SECONDS)
                        }
                        if (issued.compareAndSet(false, true)) {
                            200 to """{"status":"ok","device_uuid":"device_cold_000002","device_token":"tok_cold_002"}"""
                        } else {
                            401 to """{"detail":"Duplicate unauthenticated registration"}"""
                        }
                    }
                    "/api/wishlist/from-keyword" -> {
                        if (req.headers["x-device-token"] == "tok_cold_002") {
                            200 to """{"ok":true}"""
                        } else {
                            401 to """{"detail":"Missing X-Device-Token header"}"""
                        }
                    }
                    else -> 200 to """{"ok":true}"""
                }
            }

            try {
                val client = NetworkModule.createOkHttpClient(
                    baseUrl = server.baseUrl,
                    identityStoreProvider = { store },
                    registrationClient = OkHttpClient.Builder().build()
                )
                val directResult = AtomicInteger(-1)
                val directBody = AtomicReference("")
                val protectedResult = AtomicInteger(-1)

                val t1 = Thread {
                    val req = Request.Builder()
                        .url("${server.baseUrl}api/push/device")
                        .post("""{"device_uuid":"device_cold_000002","fcm_token":"fcm_direct_first"}""".toRequestBody(jsonMedia))
                        .build()
                    client.newCall(req).execute().use { res ->
                        directResult.set(res.code)
                        directBody.set(res.body?.string().orEmpty())
                    }
                }
                val t2 = Thread {
                    val req = Request.Builder()
                        .url("${server.baseUrl}api/wishlist/from-keyword")
                        .post("""{"keyword":"아이패드","target_price":900000,"user_id":"device_cold_000002"}""".toRequestBody(jsonMedia))
                        .build()
                    protectedResult.set(client.newCall(req).execute().use { it.code })
                }

                t1.start()
                assertTrue(firstRegisterArrived.await(3, TimeUnit.SECONDS))
                t2.start()
                val deadline = System.currentTimeMillis() + 2000L
                while (t2.state != Thread.State.BLOCKED && t2.isAlive && System.currentTimeMillis() < deadline) {
                    Thread.sleep(5)
                }
                allowFirstRegisterResponse.countDown()
                t1.join(5000)
                t2.join(5000)

                assertEquals(200, directResult.get())
                assertTrue(directBody.get().contains("tok_cold_002"))
                assertEquals(200, protectedResult.get())
                assertEquals(1, registerCalls.get())
                val protectedReq = server.recordedRequests.single { it.path == "/api/wishlist/from-keyword" }
                assertEquals("tok_cold_002", protectedReq.headers["x-device-token"])
            } finally {
                server.close()
            }
        }
    }

    @Test
    fun notificationHistoryManager_survivesIllegalStateFromKeystoreCorruption() {
        val failingStore = object : DeviceIdentityStore {
            override fun getOrCreateDeviceUuid(): String {
                throw IllegalStateException("Keystore permanently corrupted")
            }

            override fun getDeviceToken(): String? = null
            override fun saveDeviceToken(token: String, deviceUuid: String) {}
            override fun clearDeviceToken() {}
            override fun getCachedFcmToken(): String? = null
            override fun saveFcmToken(fcmToken: String) {}
        }
        LocalDataProvider.setDeviceIdentityStoreForTest(failingStore)

        val uncaught = CopyOnWriteArrayList<Throwable>()
        val previousHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { _, throwable ->
            uncaught.add(throwable)
        }

        try {
            val stubContext = ContextWrapper(null)

            runBlocking {
                NotificationHistoryManager.clearAll(stubContext)
                NotificationHistoryManager.coroutineScopeForTest.coroutineContext[Job]?.children?.toList()?.joinAll()

                NotificationHistoryManager.addAlert(
                    context = stubContext,
                    title = "Keystore 손상 안전 테스트 핫딜",
                    keyword = "맥북",
                    dealUrl = "https://example.com/deal/1"
                )
                NotificationHistoryManager.coroutineScopeForTest.coroutineContext[Job]?.children?.toList()?.joinAll()
                val added = NotificationHistoryManager.alerts.value.firstOrNull {
                    it.title == "Keystore 손상 안전 테스트 핫딜"
                }
                assertNotNull(added)

                NotificationHistoryManager.deleteAlert(stubContext, added!!.id)
                NotificationHistoryManager.coroutineScopeForTest.coroutineContext[Job]?.children?.toList()?.joinAll()
                assertTrue(NotificationHistoryManager.alerts.value.none { it.id == added.id })

                NotificationHistoryManager.addAlert(
                    context = stubContext,
                    title = "전체 삭제 안전 테스트 핫딜",
                    keyword = "아이패드",
                    dealUrl = "https://example.com/deal/2"
                )
                NotificationHistoryManager.coroutineScopeForTest.coroutineContext[Job]?.children?.toList()?.joinAll()
                assertFalse(NotificationHistoryManager.alerts.value.isEmpty())

                NotificationHistoryManager.clearAll(stubContext)
                NotificationHistoryManager.coroutineScopeForTest.coroutineContext[Job]?.children?.toList()?.joinAll()
                assertTrue(NotificationHistoryManager.alerts.value.isEmpty())
            }

            assertTrue(
                "Keystore IllegalStateException 발생 후에도 코루틴 스코프가 생존해야 합니다.",
                NotificationHistoryManager.coroutineScopeForTest.isActive
            )
            assertTrue("백그라운드 미처리 예외가 없어야 합니다: $uncaught", uncaught.isEmpty())
        } finally {
            Thread.setDefaultUncaughtExceptionHandler(previousHandler)
        }
    }
}

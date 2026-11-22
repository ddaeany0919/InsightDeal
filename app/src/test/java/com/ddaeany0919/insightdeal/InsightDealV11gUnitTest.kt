package com.ddaeany0919.insightdeal

import android.content.SharedPreferences
import androidx.paging.DifferCallback
import androidx.paging.ExperimentalPagingApi
import androidx.paging.LoadType
import androidx.paging.NullPaddedList
import androidx.paging.PagingConfig
import androidx.paging.PagingDataDiffer
import androidx.paging.PagingSource
import androidx.paging.PagingState
import androidx.paging.RemoteMediator
import com.ddaeany0919.insightdeal.core.security.DeviceIdentityStore
import com.ddaeany0919.insightdeal.core.security.EncryptedDeviceIdentityStore
import com.ddaeany0919.insightdeal.data.FeedPreferencesDataSource
import com.ddaeany0919.insightdeal.data.LocalDataProvider
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
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

class InsightDealV11gUnitTest {

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
                            401 -> "Unauthorized"
                            403 -> "Forbidden"
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

            val headerText = String(headerBytes.toByteArray(), StandardCharsets.ISO_8859_1)
            val lines = headerText.split("\r\n").filter { it.isNotEmpty() }
            if (lines.isEmpty()) return null
            val requestParts = lines.first().split(" ")
            val method = requestParts.getOrNull(0).orEmpty()
            val path = requestParts.getOrNull(1).orEmpty()

            val headers = linkedMapOf<String, String>()
            for (line in lines.drop(1)) {
                val idx = line.indexOf(':')
                if (idx > 0) {
                    headers[line.substring(0, idx).trim().lowercase()] = line.substring(idx + 1).trim()
                }
            }

            val contentLength = headers["content-length"]?.toIntOrNull() ?: 0
            val bodyBytes = ByteArray(contentLength)
            var offset = 0
            while (offset < contentLength) {
                val read = input.read(bodyBytes, offset, contentLength - offset)
                if (read == -1) break
                offset += read
            }
            val body = String(bodyBytes, 0, offset, StandardCharsets.UTF_8)
            return RecordedHttpRequest(method, path, headers, body)
        }

        override fun close() {
            running = false
            runCatching { serverSocket.close() }
            worker.join(1000)
        }
    }

    private class InMemoryDeviceIdentityStore(
        initialUuid: String? = "test-device-uuid-v11g",
        initialToken: String? = "test-device-token"
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
            return LoadResult.Page(
                data = items,
                prevKey = null,
                nextKey = null
            )
        }
    }

    private class InMemoryDealDao : DealDao {
        val deals = linkedMapOf<Int, DealEntity>()
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
            val source = InMemoryDealPagingSource(orderedDeals(sort))
            invalidationTrackers.add(source)
            return source
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

    private val resourcesToClose = mutableListOf<Closeable>()
    private val scopesToCancel = mutableListOf<CoroutineScope>()

    private fun newUnconfinedScope(): CoroutineScope {
        return CoroutineScope(SupervisorJob() + Dispatchers.Unconfined).also { scopesToCancel += it }
    }

    private fun createApiService(baseUrl: String): ApiService {
        return Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(OkHttpClient.Builder().build())
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(ApiService::class.java)
    }

    private fun buildPagingState(pagesData: List<List<DealEntity>>, pageSize: Int = 20): PagingState<Int, DealEntity> {
        val pages = pagesData.map { list ->
            PagingSource.LoadResult.Page<Int, DealEntity>(data = list, prevKey = null, nextKey = null)
        }
        return PagingState(
            pages = pages,
            anchorPosition = null,
            config = PagingConfig(pageSize = pageSize, enablePlaceholders = false),
            leadingPlaceholderCount = 0
        )
    }

    private suspend fun waitUntil(timeoutMs: Long = 3000L, predicate: () -> Boolean) {
        withTimeout(timeoutMs) {
            while (!predicate()) {
                delay(10)
            }
        }
    }

    private fun createJvmPagingDiffer(): PagingDataDiffer<DealItem> {
        val noopCallback = object : DifferCallback {
            override fun onChanged(position: Int, count: Int) {}
            override fun onInserted(position: Int, count: Int) {}
            override fun onRemoved(position: Int, count: Int) {}
        }
        return object : PagingDataDiffer<DealItem>(
            differCallback = noopCallback,
            mainContext = Dispatchers.Unconfined
        ) {
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

    @After
    fun tearDown() {
        scopesToCancel.forEach { it.cancel() }
        scopesToCancel.clear()
        resourcesToClose.forEach { runCatching { it.close() } }
        resourcesToClose.clear()
        HotDealsRemoteMediator.clearRefreshCache()
        LocalDataProvider.setDeviceIdentityStoreForTest(null)
        LocalDataProvider.setFeedPreferencesDataSourceForTest(null)
    }

    // -----------------------------------------------------------------------
    // 1. 6종 정렬 (HomeViewModel, DealDao SQL, HotDealsRemoteMediator, InMemoryDealDao)
    // -----------------------------------------------------------------------
    @OptIn(ExperimentalPagingApi::class)
    @Test
    fun v11g_sixSortModes_supportedAcrossViewModelMediatorAndDealDao() = runBlocking {
        val expectedSorts = setOf(
            "latest",
            "popular",
            "price_asc",
            "price_desc",
            "comments",
            "recommendations"
        )
        assertEquals(expectedSorts, HomeViewModel.SUPPORTED_SORTS)

        val server = LocalStubHttpServer { _ ->
            200 to """{
                "deals": [
                    {"id": 1, "title": "가성비 딜", "price": 15000, "currency": "KRW", "site_name": "뽐뿌", "honey_score": 80, "comment_count": 30, "like_count": 5, "created_at": "2026-11-22T01:00:00Z"},
                    {"id": 2, "title": "고액 딜", "price": 50000, "currency": "KRW", "site_name": "뽐뿌", "honey_score": 50, "comment_count": 2, "like_count": 40, "created_at": "2026-11-22T02:00:00Z"},
                    {"id": 3, "title": "가격미정 인기 딜", "price": 0, "currency": "KRW", "site_name": "뽐뿌", "honey_score": 150, "comment_count": 10, "like_count": 20, "created_at": "2026-11-22T03:00:00Z"}
                ],
                "has_more": false
            }"""
        }.also { resourcesToClose += it }

        val vm = HomeViewModel(
            apiService = createApiService(server.baseUrl),
            coroutineScopeOverride = newUnconfinedScope(),
            feedPreferencesDataSource = FeedPreferencesDataSource()
        )
        for (sortKey in expectedSorts) {
            vm.selectSort(sortKey)
            assertEquals(sortKey, vm.filterState.value.sortBy)
        }
        vm.selectSort("invalid_sort_key")
        assertEquals("latest", vm.filterState.value.sortBy)

        // DealDao SQL 정적 검증: 6종 정렬 분기 및 price_desc 0원 후순위, 통화 환산 계수 확인
        val sourceRoot = listOf(File("src/main/java"), File("app/src/main/java")).first { it.exists() }
        val daoText = File(sourceRoot, "com/ddaeany0919/insightdeal/local/db/DealDao.kt").readText()
        val queryMatch = Regex("@Query\\(\"\"\"([\\s\\S]*?)\"\"\"\\)\\s*fun\\s+getPagedDeals").find(daoText)
        assertNotNull("DealDao.kt @Query 직후 fun getPagedDeals 매칭이 유지되어야 합니다.", queryMatch)
        val sql = queryMatch!!.groupValues[1]
        assertTrue(sql.contains(":sort = 'popular'"))
        assertTrue(sql.contains(":sort = 'comments'"))
        assertTrue(sql.contains(":sort = 'recommendations'"))
        assertTrue(sql.contains(":sort = 'price_asc'"))
        assertTrue(sql.contains(":sort = 'price_desc'"))
        assertTrue(sql.contains("WHEN 'USD' THEN price * 14.0"))
        assertTrue(sql.contains("WHEN 'EUR' THEN price * 15.2"))
        assertTrue(sql.contains("WHEN 'CNY' THEN price * 1.95"))
        assertTrue(sql.contains("WHEN 'JPY' THEN price * 9.3"))

        // HotDealsRemoteMediator가 6종 sort를 서버로 정확히 전달하고 InMemoryDealDao가 6종 정렬을 수행하는지 확인
        // (각 sort 키가 다르므로 루프 내부 수동 clearRefreshCache() 우회 없이도 자연 동작해야 함)
        val apiService = createApiService(server.baseUrl)
        val dealDao = InMemoryDealDao()
        val keysDao = InMemoryDealRemoteKeysDao()
        HotDealsRemoteMediator.clearRefreshCache()
        for (sortKey in listOf("price_desc", "comments", "recommendations")) {
            val mediator = HotDealsRemoteMediator(
                dealDao = dealDao,
                dealRemoteKeysDao = keysDao,
                runInTransaction = { block -> block() },
                apiService = apiService,
                category = null,
                keyword = null,
                platform = null,
                sort = sortKey
            )
            assertEquals(RemoteMediator.InitializeAction.LAUNCH_INITIAL_REFRESH, mediator.initialize())
            val result = mediator.load(LoadType.REFRESH, buildPagingState(emptyList()))
            assertTrue(result is RemoteMediator.MediatorResult.Success)
        }
        assertTrue(server.recordedRequests.any { it.path.contains("sort=price_desc") })
        assertTrue(server.recordedRequests.any { it.path.contains("sort=comments") })
        assertTrue(server.recordedRequests.any { it.path.contains("sort=recommendations") })

        assertEquals(listOf(2, 1, 3), dealDao.orderedDeals("price_desc").map { it.id })
        assertEquals(listOf(1, 2, 3), dealDao.orderedDeals("price_asc").map { it.id })
        assertEquals(listOf(1, 3, 2), dealDao.orderedDeals("comments").map { it.id })
        assertEquals(listOf(2, 3, 1), dealDao.orderedDeals("recommendations").map { it.id })
        assertEquals(listOf(3, 1, 2), dealDao.orderedDeals("popular").map { it.id })
        assertEquals(listOf(3, 2, 1), dealDao.orderedDeals("latest").map { it.id })
    }

    // -----------------------------------------------------------------------
    // 2. PagerQueryKey 서버 퀵필터 + 로컬 freeShippingOnly 제거 + RemoteMediator 캐시키/보정
    // -----------------------------------------------------------------------
    @OptIn(ExperimentalPagingApi::class)
    @Test
    fun v11g_quickFilters_propagateToServerAndMediator_andRemoveLocalFreeShipDrop() = runBlocking {
        HotDealsRemoteMediator.clearRefreshCache()
        val server = LocalStubHttpServer { _ ->
            // shipping_fee가 null인 딜을 내려주어, HomeViewModel의 로컬 freeShippingOnly 필터가 제거되었는지 직접 검증
            200 to """{
                "deals": [
                    {
                        "id": 701,
                        "title": "쿠팡 무료배송 테스트 상품",
                        "price": 19900,
                        "currency": "KRW",
                        "site_name": "뽐뿌",
                        "shop_name": null,
                        "shipping_fee": null,
                        "created_at": "2026-11-22T04:00:00Z"
                    }
                ],
                "has_more": false
            }"""
        }.also { resourcesToClose += it }

        val dealDao = InMemoryDealDao()
        val keysDao = InMemoryDealRemoteKeysDao()
        val scope = newUnconfinedScope()
        val vm = HomeViewModel(
            apiService = createApiService(server.baseUrl),
            coroutineScopeOverride = scope,
            autoInitialize = false,
            feedPreferencesDataSource = FeedPreferencesDataSource(),
            dealDaoProvider = { dealDao },
            dealRemoteKeysDaoProvider = { keysDao },
            runInTransactionProvider = { block -> block() }
        )

        // FilterState 조작 & 정규화 검증
        vm.toggleFreeShippingOnly()
        vm.setShopFilter("  쿠팡  ")
        vm.setPriceRangeFilter(10_000, 50_000)

        assertTrue(vm.filterState.value.freeShippingOnly)
        assertEquals("쿠팡", vm.filterState.value.shop)
        assertEquals(10_000, vm.filterState.value.minPrice)
        assertEquals(50_000, vm.filterState.value.maxPrice)

        val differ = createJvmPagingDiffer()
        val collectJob = scope.launch {
            vm.dealsPagingData.collectLatest { pagingData ->
                differ.collectFrom(pagingData)
            }
        }

        waitUntil { differ.snapshot().items.isNotEmpty() && server.recordedRequests.isNotEmpty() }

        val firstPath = URLDecoder.decode(server.recordedRequests.first().path, "UTF-8")
        assertTrue("free_shipping=true가 전송되어야 합니다: $firstPath", firstPath.contains("free_shipping=true"))
        assertTrue("shop=쿠팡이 전송되어야 합니다: $firstPath", firstPath.contains("shop=쿠팡"))
        assertTrue("min_price=10000이 전송되어야 합니다: $firstPath", firstPath.contains("min_price=10000"))
        assertTrue("max_price=50000이 전송되어야 합니다: $firstPath", firstPath.contains("max_price=50000"))

        // 로컬 PagingData.filter(freeShippingOnly)가 제거되어 shippingFee=null 아이템도 그대로 노출됨
        assertEquals(1, differ.snapshot().items.size)
        assertEquals(701, differ.snapshot().items.first().id)
        // RemoteMediator에서 shopName이 비어 있을 때 resolvedShop으로 안전 보정됨
        assertEquals("쿠팡", differ.snapshot().items.first().shopName)

        // resetHomeFilters() 시 shop/minPrice/maxPrice 일괄 초기화
        vm.resetHomeFilters()
        assertFalse(vm.filterState.value.freeShippingOnly)
        assertNull(vm.filterState.value.shop)
        assertNull(vm.filterState.value.minPrice)
        assertNull(vm.filterState.value.maxPrice)

        vm.setShopFilter("전체")
        assertNull(vm.filterState.value.shop)

        collectJob.cancel()

        // buildCacheKey 독립성 검증
        val apiService = createApiService(server.baseUrl)
        val baseKey = HotDealsRemoteMediator(
            dealDao = dealDao,
            dealRemoteKeysDao = keysDao,
            runInTransaction = { it() },
            apiService = apiService,
            category = null,
            keyword = null,
            platform = null
        ).buildCacheKey()
        val shipKey = HotDealsRemoteMediator(
            dealDao = dealDao,
            dealRemoteKeysDao = keysDao,
            runInTransaction = { it() },
            apiService = apiService,
            category = null,
            keyword = null,
            platform = null,
            freeShippingOnly = true
        ).buildCacheKey()
        val shopKey = HotDealsRemoteMediator(
            dealDao = dealDao,
            dealRemoteKeysDao = keysDao,
            runInTransaction = { it() },
            apiService = apiService,
            category = null,
            keyword = null,
            platform = null,
            shop = "네이버"
        ).buildCacheKey()
        val priceKey = HotDealsRemoteMediator(
            dealDao = dealDao,
            dealRemoteKeysDao = keysDao,
            runInTransaction = { it() },
            apiService = apiService,
            category = null,
            keyword = null,
            platform = null,
            minPrice = 10000,
            maxPrice = 30000
        ).buildCacheKey()

        assertEquals(4, setOf(baseKey, shipKey, shopKey, priceKey).size)
    }

    // -----------------------------------------------------------------------
    // 3. viewMode 영속화 + mutedKeywords 추가/삭제/영속화/피드 필터링
    // -----------------------------------------------------------------------
    @Test
    fun v11g_viewModeAndMutedKeywords_persistAndFilterPagingData() = runBlocking {
        val prefs = FeedPreferencesDataSource()
        LocalDataProvider.setFeedPreferencesDataSourceForTest(prefs)

        val server = LocalStubHttpServer { _ ->
            200 to """{
                "deals": [
                    {"id": 801, "title": "애플 맥북 프로 M4 특가", "price": 2100000, "site_name": "퀘이사존", "shop_name": "쿠팡", "created_at": "2026-11-22T05:00:00Z"},
                    {"id": 802, "title": "알리 천원마트 케이블", "price": 1500, "site_name": "뽐뿌", "shop_name": "알리익스프레스", "created_at": "2026-11-22T04:00:00Z"},
                    {"id": 803, "title": "삼성 갤럭시 버즈3", "price": 129000, "site_name": "펨코", "shop_name": "네이버", "tags": ["음향", "특가"], "created_at": "2026-11-22T03:00:00Z"}
                ],
                "has_more": false
            }"""
        }.also { resourcesToClose += it }

        val dealDao = InMemoryDealDao()
        val keysDao = InMemoryDealRemoteKeysDao()
        val scope = newUnconfinedScope()
        val vm1 = HomeViewModel(
            apiService = createApiService(server.baseUrl),
            coroutineScopeOverride = scope,
            autoInitialize = false,
            feedPreferencesDataSource = LocalDataProvider.feedPreferencesDataSource,
            dealDaoProvider = { dealDao },
            dealRemoteKeysDaoProvider = { keysDao },
            runInTransactionProvider = { it() }
        )

        assertEquals(FeedPreferencesDataSource.VIEW_MODE_CARD, vm1.viewMode.value)
        vm1.setViewMode(FeedPreferencesDataSource.VIEW_MODE_COMPACT)
        assertEquals(FeedPreferencesDataSource.VIEW_MODE_COMPACT, vm1.viewMode.value)

        vm1.addMutedKeyword("   ")
        vm1.addMutedKeyword("  apple  ")
        vm1.addMutedKeyword("APPLE") // 대소문자 중복 무시
        vm1.addMutedKeyword("알리")
        assertEquals(setOf("apple", "알리"), vm1.mutedKeywords.value)

        // 신규 HomeViewModel 인스턴스에서 viewMode & mutedKeywords 복원 확인 + 잘못된 모드 정규화 확인
        val vm2 = HomeViewModel(
            apiService = createApiService(server.baseUrl),
            coroutineScopeOverride = scope,
            autoInitialize = false,
            feedPreferencesDataSource = LocalDataProvider.feedPreferencesDataSource,
            dealDaoProvider = { dealDao },
            dealRemoteKeysDaoProvider = { keysDao },
            runInTransactionProvider = { it() }
        )
        assertEquals(FeedPreferencesDataSource.VIEW_MODE_COMPACT, vm2.viewMode.value)
        assertEquals(setOf("apple", "알리"), vm2.mutedKeywords.value)

        // PagingData 내 대소문자 무시 뮤트 필터링 검증
        val differ = createJvmPagingDiffer()
        val collectJob = scope.launch {
            vm2.dealsPagingData.collectLatest { differ.collectFrom(it) }
        }

        // apple/알리는 제외되지 않았는지? title="애플 맥북 프로 M4 특가"는 한글이라 "apple" 매칭 안 됨 -> "맥북"으로 변경 테스트
        waitUntil { differ.snapshot().items.size == 2 } // 802("알리") 제외 -> 801, 803 남음
        assertEquals(listOf(801, 803), differ.snapshot().items.map { it.id })

        // 대소문자 무시("m4") 추가 시 801("M4")도 즉시 제외됨
        vm2.addMutedKeyword("m4")
        waitUntil { differ.snapshot().items.map { it.id } == listOf(803) }

        // 뮤트 해제 시 즉시 재노출
        vm2.removeMutedKeyword("M4")
        waitUntil { differ.snapshot().items.map { it.id } == listOf(801, 803) }

        // 잘못된 보기 모드 입력 시 "card"로 정규화
        vm2.setViewMode("unknown_mode")
        assertEquals(FeedPreferencesDataSource.VIEW_MODE_CARD, vm2.viewMode.value)

        collectJob.cancel()
    }

    // -----------------------------------------------------------------------
    // 4. onForegroundChanged(Boolean) + sortBy == "latest" 90초 폴링 제어
    // -----------------------------------------------------------------------
    @Test
    fun v11g_foregroundAndLatestSortOnly_controlsRealtimeDealPolling() = runBlocking {
        assertEquals(90_000L, HomeViewModel.REALTIME_POLL_INTERVAL_MS)

        val pollCount = AtomicInteger(0)
        val latestTopId = AtomicInteger(100)
        val server = LocalStubHttpServer { _ ->
            pollCount.incrementAndGet()
            val topId = latestTopId.get()
            200 to """{
                "deals": [{"id": $topId, "title": "실시간 딜 $topId", "price": 10000, "site_name": "뽐뿌"}],
                "has_more": false
            }"""
        }.also { resourcesToClose += it }

        val vm = HomeViewModel(
            apiService = createApiService(server.baseUrl),
            coroutineScopeOverride = newUnconfinedScope(),
            autoInitialize = false,
            feedPreferencesDataSource = FeedPreferencesDataSource(),
            realtimePollIntervalMs = 30L
        )

        // 1) 초기 백그라운드 상태: 폴링 0회
        delay(80)
        assertEquals(0, pollCount.get())

        // 2) 포그라운드 진입 + latest 정렬: 폴링 동작 및 신규 딜 감지
        vm.onForegroundChanged(true)
        waitUntil { pollCount.get() >= 1 }
        assertFalse(vm.hasNewDeals.value)

        latestTopId.set(102)
        waitUntil { vm.hasNewDeals.value && vm.newDealsCount.value == 1 }

        // 3) popular 정렬 전환: 폴링 즉시 중단
        vm.selectSort("popular")
        val countAfterPopular = pollCount.get()
        delay(100)
        assertEquals(countAfterPopular, pollCount.get())

        // 4) latest 정렬 복귀(여전히 포그라운드): 폴링 자동 재개
        vm.selectSort("latest")
        waitUntil { pollCount.get() > countAfterPopular }

        // 5) 백그라운드 전환: 폴링 즉시 중단
        vm.onForegroundChanged(false)
        val countAfterBackground = pollCount.get()
        delay(100)
        assertEquals(countAfterBackground, pollCount.get())
    }

    // -----------------------------------------------------------------------
    // 5. similarDeals DTO 역직렬화 · withClosedReportState 보존 · DealDetailViewModel 매핑
    // -----------------------------------------------------------------------
    @Test
    fun v11g_similarDeals_deserializesPreservesOnReportClosedAndMapsInViewModel() = runBlocking {
        val gson = Gson()
        val snakeDeal = gson.fromJson(
            """{
                "id": 10,
                "title": "메인 딜",
                "price": 19900,
                "site_name": "뽐뿌",
                "similar_deals": [
                    {"id": 11, "title": "유사 딜 1", "price": 18900, "site_name": "펨코"},
                    {"id": 10, "title": "자기 자신 제외", "price": 19900, "site_name": "뽐뿌"},
                    {"id": 0, "title": "잘못된 ID 제외", "price": 1000, "site_name": "뽐뿌"},
                    {"id": 11, "title": "중복 ID 제외", "price": 18900, "site_name": "펨코"}
                ]
            }""",
            DealItem::class.java
        )
        assertEquals(4, snakeDeal.similarDeals?.size)
        val afterClosed = snakeDeal.withClosedReportState(closed = true, count = 3)
        assertEquals(4, afterClosed.similarDeals?.size)
        assertTrue(afterClosed.isClosed)
        assertEquals(3, afterClosed.reportCount)

        val camelDeal = gson.fromJson(
            """{"id": 20, "title": "카멜", "price": 10000, "site_name": "뽐뿌", "similarDeals": [{"id": 21, "title": "유사 카멜", "price": 9000, "site_name": "루리웹"}]}""",
            DealItem::class.java
        )
        assertEquals(listOf(21), camelDeal.similarDeals?.map { it.id })

        val omittedDeal = gson.fromJson(
            """{"id": 30, "title": "누락", "price": 10000, "site_name": "뽐뿌"}""",
            DealItem::class.java
        )
        assertTrue(omittedDeal.similarDeals.orEmpty().isEmpty())

        // similar_deals가 명시적 null로 전달되어도 NPE 없이 복원
        val nullListDeal = gson.fromJson(
            """{"id": 40, "title": "명시적 널", "price": 10000, "site_name": "뽐뿌", "similar_deals": null}""",
            DealItem::class.java
        )
        assertNull(nullListDeal.similarDeals)
        assertTrue(nullListDeal.withClosedReportState(closed = true, count = 1).similarDeals.orEmpty().isEmpty())

        val server = LocalStubHttpServer { req ->
            when {
                req.path.startsWith("/api/community/deals/10") -> 200 to """{
                    "id": 10,
                    "title": "메인 딜",
                    "price": 19900,
                    "site_name": "뽐뿌",
                    "history_count": 0,
                    "similar_deals": [
                        null,
                        {"id": 11, "title": "유사 딜 A", "price": 18900, "site_name": "펨코"},
                        {"id": 10, "title": "자기 자신", "price": 19900, "site_name": "뽐뿌"},
                        {"id": -1, "title": "음수 ID", "price": 5000, "site_name": "뽐뿌"},
                        {"id": 12, "title": "유사 딜 B", "price": 21000, "site_name": "루리웹"},
                        {"id": 11, "title": "중복 A", "price": 18900, "site_name": "펨코"}
                    ]
                }"""
                req.path.startsWith("/api/community/deals/20") -> 200 to """{
                    "id": 20,
                    "title": "널 유사딜",
                    "price": 15000,
                    "site_name": "뽐뿌",
                    "history_count": 0,
                    "similar_deals": null
                }"""
                else -> 404 to """{"detail":"Not Found"}"""
            }
        }.also { resourcesToClose += it }

        val vm = DealDetailViewModel(
            apiService = createApiService(server.baseUrl),
            deviceUuidProvider = { "test-uuid" },
            ioDispatcher = Dispatchers.Unconfined,
            coroutineScopeOverride = newUnconfinedScope()
        )
        vm.loadDealData(10)
        waitUntil { !vm.uiState.value.isLoading }

        assertEquals(listOf(11, 12), vm.uiState.value.similarDeals.map { it.id })
        assertNull(vm.uiState.value.error)

        // similar_deals: null 응답 시 NPE 없이 빈 리스트
        vm.loadDealData(20)
        waitUntil { !vm.uiState.value.isLoading && vm.uiState.value.deal?.id == 20 }
        assertTrue(vm.uiState.value.similarDeals.isEmpty())
        assertNull(vm.uiState.value.error)

        // 에러 시 emptyList 초기화
        vm.loadDealData(999)
        waitUntil { !vm.uiState.value.isLoading && vm.uiState.value.error != null }
        assertTrue(vm.uiState.value.similarDeals.isEmpty())
    }

    // -----------------------------------------------------------------------
    // 6. KeywordManagerViewModel.sendTestPush (api/push/test, 로딩/200/404/429/401/403/500/네트워크/초기화)
    // -----------------------------------------------------------------------
    @Test
    fun v11g_keywordManagerViewModel_sendTestPush_handlesAllResponsesAndLoadingGuard() = runBlocking {
        LocalDataProvider.setDeviceIdentityStoreForTest(InMemoryDeviceIdentityStore("uuid-push-123", "tok-1"))

        val mode = AtomicInteger(200)
        val requestStartedLatch = CountDownLatch(1)
        val releaseResponseLatch = CountDownLatch(1)
        var holdResponse = true

        val server = LocalStubHttpServer { _ ->
            if (holdResponse) {
                requestStartedLatch.countDown()
                releaseResponseLatch.await(3, TimeUnit.SECONDS)
            }
            when (mode.get()) {
                200 -> 200 to """{"success":true,"message":"테스트 푸시 알림을 발송했어요!"}"""
                201 -> 200 to """{"success":true,"message":null}"""
                202 -> 200 to """{"success":false,"message":null}"""
                404 -> 404 to """{"detail":"등록된 알림 기기를 찾지 못했어요."}"""
                429 -> 429 to """{"detail":"테스트 알림은 1분에 최대 6회까지 보낼 수 있어요."}"""
                401 -> 401 to """{"detail":"Missing X-Device-Token header"}"""
                403 -> 403 to """{"detail":"X-Device-Token does not match target device_uuid"}"""
                else -> 500 to """{"detail":"Internal Server Error"}"""
            }
        }.also { resourcesToClose += it }

        val vm = KeywordManagerViewModel(
            apiService = createApiService(server.baseUrl),
            keywordDaoProvider = { InMemoryKeywordDao() },
            ioDispatcher = Dispatchers.IO,
            coroutineScopeOverride = newUnconfinedScope(),
            autoInitialize = false
        )

        // 1) 로딩 중 상태 및 중복 호출 차단 + 200 OK
        vm.sendTestPush()
        assertTrue(requestStartedLatch.await(2, TimeUnit.SECONDS))
        assertTrue(vm.testPushState.value.isSending)

        // 중복 호출 무시 검증
        vm.sendTestPush()
        releaseResponseLatch.countDown()

        waitUntil { !vm.testPushState.value.isSending && vm.testPushState.value.feedbackSeq == 1 }
        assertEquals(1, server.recordedRequests.size)
        val req1 = server.recordedRequests.first()
        assertEquals("POST", req1.method)
        assertEquals("/api/push/test", req1.path)
        assertTrue(req1.body.contains("uuid-push-123"))

        assertEquals(true, vm.testPushState.value.isSuccess)
        assertEquals("테스트 푸시 알림을 발송했어요!", vm.testPushState.value.message)
        assertNull("모달 errorMessage와 분리되어야 합니다.", vm.errorMessage.value)

        // 2) 404 Not Found (서버 한국어 detail 우선)
        holdResponse = false
        mode.set(404)
        vm.sendTestPush()
        waitUntil { vm.testPushState.value.feedbackSeq == 2 }
        assertEquals(false, vm.testPushState.value.isSuccess)
        assertEquals("등록된 알림 기기를 찾지 못했어요.", vm.testPushState.value.message)
        assertNull(vm.errorMessage.value)

        // 3) 429 Rate Limit (서버 한국어 detail 우선)
        mode.set(429)
        vm.sendTestPush()
        waitUntil { vm.testPushState.value.feedbackSeq == 3 }
        assertEquals(false, vm.testPushState.value.isSuccess)
        assertEquals("테스트 알림은 1분에 최대 6회까지 보낼 수 있어요.", vm.testPushState.value.message)

        // 4) 401/403 인증 오류: 영문 detail 대신 사용자 친화적 한국어 폴백 노출
        mode.set(401)
        vm.sendTestPush()
        waitUntil { vm.testPushState.value.feedbackSeq == 4 }
        assertEquals(false, vm.testPushState.value.isSuccess)
        assertEquals("기기 인증에 실패했어요. 앱을 다시 실행한 후 시도해 주세요.", vm.testPushState.value.message)

        mode.set(403)
        vm.sendTestPush()
        waitUntil { vm.testPushState.value.feedbackSeq == 5 }
        assertEquals(false, vm.testPushState.value.isSuccess)
        assertEquals("기기 인증에 실패했어요. 앱을 다시 실행한 후 시도해 주세요.", vm.testPushState.value.message)

        // 5) 500 Server Error: 영문 detail 대신 한국어 폴백 노출
        mode.set(500)
        vm.sendTestPush()
        waitUntil { vm.testPushState.value.feedbackSeq == 6 }
        assertEquals(false, vm.testPushState.value.isSuccess)
        assertEquals("테스트 알림 발송에 실패했어요. (500)", vm.testPushState.value.message)

        // 6) 200 OK with message: null / success: false with message: null (NPE 방어)
        mode.set(201)
        vm.sendTestPush()
        waitUntil { vm.testPushState.value.feedbackSeq == 7 }
        assertEquals(true, vm.testPushState.value.isSuccess)
        assertEquals("테스트 푸시 알림을 발송했어요!", vm.testPushState.value.message)

        mode.set(202)
        vm.sendTestPush()
        waitUntil { vm.testPushState.value.feedbackSeq == 8 }
        assertEquals(false, vm.testPushState.value.isSuccess)
        assertEquals("테스트 알림 발송에 실패했어요.", vm.testPushState.value.message)

        // 7) 스낵바 피드백 초기화 (feedbackSeq 유지)
        vm.clearTestPushFeedback()
        assertNull(vm.testPushState.value.message)
        assertNull(vm.testPushState.value.isSuccess)
        assertEquals(8, vm.testPushState.value.feedbackSeq)

        // 8) 네트워크 예외 처리
        server.close()
        vm.sendTestPush()
        waitUntil { vm.testPushState.value.feedbackSeq == 9 }
        assertEquals(false, vm.testPushState.value.isSuccess)
        assertEquals("네트워크 연결을 확인해 주세요.", vm.testPushState.value.message)
        assertNull(vm.errorMessage.value)
    }

    // -----------------------------------------------------------------------
    // 7. HotDealsRemoteMediator 교차 필터·정렬 5분 TTL 캐시 무효화 (K1 -> K2 -> K1) 회귀
    // -----------------------------------------------------------------------
    @OptIn(ExperimentalPagingApi::class)
    @Test
    fun v11g_hotDealsRemoteMediator_crossFilterAndSortRoundTrip_invalidatesStaleCache() = runBlocking {
        HotDealsRemoteMediator.clearRefreshCache()
        var nowMs = 2_000_000L

        val k1FetchCount = AtomicInteger(0)
        val server = LocalStubHttpServer { req ->
            val decoded = URLDecoder.decode(req.path, "UTF-8")
            when {
                decoded.contains("shop=쿠팡") -> 200 to """{
                    "deals": [
                        {"id": 9101, "title": "쿠팡 전용 필터 딜", "price": 9900, "site_name": "뽐뿌", "shop_name": "쿠팡", "created_at": "2026-11-22T06:00:00Z"}
                    ],
                    "has_more": false
                }"""
                decoded.contains("sort=popular") -> 200 to """{
                    "deals": [
                        {"id": 9201, "title": "인기순 전용 딜", "price": 15900, "site_name": "펨코", "honey_score": 300, "created_at": "2026-11-22T05:00:00Z"}
                    ],
                    "has_more": false
                }"""
                else -> {
                    val seq = k1FetchCount.incrementAndGet()
                    200 to """{
                        "deals": [
                            {"id": ${9000 + seq}, "title": "기본 전체 피드 딜 $seq", "price": 29000, "site_name": "뽐뿌", "created_at": "2026-11-22T07:00:00Z"}
                        ],
                        "has_more": false
                    }"""
                }
            }
        }.also { resourcesToClose += it }

        val apiService = createApiService(server.baseUrl)
        val dealDao = InMemoryDealDao()
        val keysDao = InMemoryDealRemoteKeysDao()

        fun createMediator(
            keyword: String? = null,
            sort: String = "latest",
            shop: String? = null
        ): HotDealsRemoteMediator = HotDealsRemoteMediator(
            dealDao = dealDao,
            dealRemoteKeysDao = keysDao,
            runInTransaction = { block -> block() },
            apiService = apiService,
            category = "전체",
            keyword = keyword,
            platform = null,
            sort = sort,
            shop = shop,
            clockMs = { nowMs }
        )

        // 1) K1 최초 진입 -> LAUNCH_INITIAL_REFRESH & REFRESH 실행
        val mK1First = createMediator()
        assertEquals(RemoteMediator.InitializeAction.LAUNCH_INITIAL_REFRESH, mK1First.initialize())
        assertTrue(mK1First.load(LoadType.REFRESH, buildPagingState(emptyList())) is RemoteMediator.MediatorResult.Success)
        assertEquals(listOf(9001), dealDao.orderedDeals("latest").map { it.id })

        // 2) 동일 키 K1 5분 이내 재진입 -> SKIP_INITIAL_REFRESH 유지
        nowMs += 30_000L
        assertEquals(RemoteMediator.InitializeAction.SKIP_INITIAL_REFRESH, createMediator().initialize())

        // 3) K2(퀵필터 shop=쿠팡) REFRESH 실행 -> 단일 Room 테이블이 K2 데이터로 교체됨
        nowMs += 10_000L
        val mK2Shop = createMediator(shop = "쿠팡")
        assertEquals(RemoteMediator.InitializeAction.LAUNCH_INITIAL_REFRESH, mK2Shop.initialize())
        assertTrue(mK2Shop.load(LoadType.REFRESH, buildPagingState(emptyList())) is RemoteMediator.MediatorResult.Success)
        assertEquals(listOf(9101), dealDao.orderedDeals("latest").map { it.id })

        // 4) 핵심 회귀 검증: 5분 이내에 K1으로 복귀 시 반드시 LAUNCH_INITIAL_REFRESH 반환 및 K1 복원
        nowMs += 10_000L
        val mK1Return = createMediator()
        assertEquals(
            "K2 REFRESH가 단일 Room 테이블을 교체했으므로 5분 내 K1 복귀 시에도 LAUNCH_INITIAL_REFRESH여야 합니다.",
            RemoteMediator.InitializeAction.LAUNCH_INITIAL_REFRESH,
            mK1Return.initialize()
        )
        assertTrue(mK1Return.load(LoadType.REFRESH, buildPagingState(emptyList())) is RemoteMediator.MediatorResult.Success)
        assertEquals(listOf(9002), dealDao.orderedDeals("latest").map { it.id })

        // 5) K3(sort=popular) REFRESH -> 5분 내 K1 복귀도 LAUNCH_INITIAL_REFRESH
        nowMs += 10_000L
        val mK3Popular = createMediator(sort = "popular")
        assertEquals(RemoteMediator.InitializeAction.LAUNCH_INITIAL_REFRESH, mK3Popular.initialize())
        assertTrue(mK3Popular.load(LoadType.REFRESH, buildPagingState(emptyList())) is RemoteMediator.MediatorResult.Success)
        assertEquals(listOf(9201), dealDao.orderedDeals("popular").map { it.id })
        assertEquals(RemoteMediator.InitializeAction.LAUNCH_INITIAL_REFRESH, createMediator().initialize())

        // 6) keyword 공백 정규화 일치 (buildCacheKey 및 API 전송 keyword 모두 trim)
        val trimmedKey = createMediator(keyword = "맥북").buildCacheKey()
        val spacedMediator = createMediator(keyword = "  맥북  ")
        assertEquals(trimmedKey, spacedMediator.buildCacheKey())
        assertTrue(spacedMediator.load(LoadType.REFRESH, buildPagingState(emptyList())) is RemoteMediator.MediatorResult.Success)
        val lastPath = URLDecoder.decode(server.recordedRequests.last().path, "UTF-8")
        assertTrue("API 요청 keyword도 trim되어야 합니다: $lastPath", lastPath.contains("keyword=맥북"))
        assertFalse("API 요청 keyword에 앞뒤 공백이 남으면 안 됩니다: $lastPath", lastPath.contains("keyword=  맥북"))

        // 7) HomeViewModel + PagingDataDiffer 엔드투엔드 K1 -> K2 -> K1(resetHomeFilters) 복귀 검증
        HotDealsRemoteMediator.clearRefreshCache()
        val vmDao = InMemoryDealDao()
        val vmKeysDao = InMemoryDealRemoteKeysDao()
        val scope = newUnconfinedScope()
        val vm = HomeViewModel(
            apiService = apiService,
            coroutineScopeOverride = scope,
            autoInitialize = false,
            feedPreferencesDataSource = FeedPreferencesDataSource(),
            dealDaoProvider = { vmDao },
            dealRemoteKeysDaoProvider = { vmKeysDao },
            runInTransactionProvider = { block -> block() }
        )
        val differ = createJvmPagingDiffer()
        val collectJob = scope.launch {
            vm.dealsPagingData.collectLatest { differ.collectFrom(it) }
        }
        waitUntil { differ.snapshot().items.firstOrNull()?.id in 9003..9999 }
        val initialK1Id = differ.snapshot().items.first().id

        vm.setShopFilter("쿠팡")
        waitUntil { differ.snapshot().items.firstOrNull()?.id == 9101 }

        vm.resetHomeFilters()
        waitUntil {
            val topId = differ.snapshot().items.firstOrNull()?.id
            topId != null && topId != 9101 && topId > initialK1Id
        }
        collectJob.cancel()
    }

    // -----------------------------------------------------------------------
    // 8. DealDao :shop 별칭 대소문자 정규화 및 M33 정규식 구조 검증
    // -----------------------------------------------------------------------
    @Test
    fun v11g_dealDao_shopAliases_caseInsensitiveAndBoundarySafe() {
        val sourceRoot = listOf(File("src/main/java"), File("app/src/main/java")).first { it.exists() }
        val daoText = File(sourceRoot, "com/ddaeany0919/insightdeal/local/db/DealDao.kt").readText()
        val queryMatch = Regex("@Query\\(\"\"\"([\\s\\S]*?)\"\"\"\\)\\s*fun\\s+getPagedDeals").find(daoText)
        assertNotNull("DealDao.kt @Query 직후 fun getPagedDeals 매칭이 유지되어야 합니다.", queryMatch)
        val sql = queryMatch!!.groupValues[1]

        assertTrue(sql.contains("LOWER(TRIM(:shop)) = 'ssg'"))
        assertTrue(sql.contains("LOWER(TRIM(:shop)) IN ('지마켓', 'g마켓')"))
        assertTrue(sql.contains("LOWER(TRIM(:shop)) = 'cj온스타일'"))
    }

    // -----------------------------------------------------------------------
    // 9. 방어적 null 경계: HomeViewModel tags=null / FeedPreferencesDataSource [null]
    // -----------------------------------------------------------------------
    @Test
    fun v11g_defensiveNullBoundaries_mutedKeywordsAndPrefs() {
        val gson = Gson()
        val nullTagsDeal = gson.fromJson(
            """{"id": 501, "title": "태그 널 상품", "price": 10000, "site_name": "뽐뿌", "tags": null}""",
            DealItem::class.java
        )
        assertFalse(HomeViewModel.matchesMutedKeywords(nullTagsDeal, setOf("광고")))
        assertFalse(HomeViewModel.isPopularDeal(nullTagsDeal))

        val nullElementTagsDeal = gson.fromJson(
            """{"id": 502, "title": "일반 상품", "price": 10000, "site_name": "뽐뿌", "tags": [null, "인기"]}""",
            DealItem::class.java
        )
        assertTrue(HomeViewModel.matchesMutedKeywords(nullElementTagsDeal, setOf("인기")))
        assertTrue(HomeViewModel.isPopularDeal(nullElementTagsDeal))

        val store = linkedMapOf<String, String?>(
            "muted_keywords_json" to """[null, "  쿠팡  ", "", null, "쿠팡"]"""
        )
        val editorProxy = java.lang.reflect.Proxy.newProxyInstance(
            SharedPreferences.Editor::class.java.classLoader,
            arrayOf(SharedPreferences.Editor::class.java)
        ) { proxy, method, args ->
            when (method.name) {
                "putString" -> {
                    store[args[0] as String] = args[1] as String?
                    proxy
                }
                "apply", "commit" -> if (method.returnType == Boolean::class.javaPrimitiveType) true else Unit
                else -> proxy
            }
        } as SharedPreferences.Editor

        val prefsProxy = java.lang.reflect.Proxy.newProxyInstance(
            SharedPreferences::class.java.classLoader,
            arrayOf(SharedPreferences::class.java)
        ) { _, method, args ->
            when (method.name) {
                "getString" -> store[args[0] as String] ?: (args[1] as String?)
                "edit" -> editorProxy
                else -> null
            }
        } as SharedPreferences

        val dataSource = FeedPreferencesDataSource(prefsProvider = { prefsProxy })
        assertEquals(setOf("쿠팡"), dataSource.getMutedKeywords())
        assertEquals(setOf("쿠팡", "알리"), dataSource.addMutedKeyword("알리"))
    }

    // -----------------------------------------------------------------------
    // 10. ViewModel 순수성 정적 검사 (Context / LazyListState 0건)
    // -----------------------------------------------------------------------
    @Test
    fun v11g_modifiedViewModels_containZeroContextReferences() {
        val sourceRoot = listOf(File("src/main/java"), File("app/src/main/java")).first { it.exists() }
        val targets = listOf(
            "com/ddaeany0919/insightdeal/presentation/home/HomeViewModel.kt",
            "com/ddaeany0919/insightdeal/presentation/DealDetailViewModel.kt",
            "com/ddaeany0919/insightdeal/presentation/KeywordManagerViewModel.kt"
        )
        for (relPath in targets) {
            val content = File(sourceRoot, relPath).readText()
            assertFalse("$relPath 에 android.content.Context 문자열이 남아 있습니다.", content.contains("android.content.Context"))
            assertFalse("$relPath 에 LazyListState 문자열이 남아 있습니다.", content.contains("LazyListState"))
        }
        assertNotEquals(FeedPreferencesDataSource.VIEW_MODE_CARD, FeedPreferencesDataSource.VIEW_MODE_COMPACT)
    }
}

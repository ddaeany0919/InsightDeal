package com.ddaeany0919.insightdeal.presentation.home

import androidx.paging.ExperimentalPagingApi
import androidx.paging.LoadType
import androidx.paging.PagingState
import androidx.paging.RemoteMediator
import androidx.room.withTransaction
import com.ddaeany0919.insightdeal.local.db.AppDatabase
import com.ddaeany0919.insightdeal.local.db.DealDao
import com.ddaeany0919.insightdeal.local.db.DealEntity
import com.ddaeany0919.insightdeal.local.db.DealRemoteKeysDao
import com.ddaeany0919.insightdeal.local.db.DealRemoteKeysEntity
import com.ddaeany0919.insightdeal.models.CategoryCatalog
import com.ddaeany0919.insightdeal.models.DealItem
import com.ddaeany0919.insightdeal.network.ApiService
import kotlinx.coroutines.CancellationException
import retrofit2.HttpException
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap

/**
 * Paging 3 RemoteMediator
 * - 네트워크 API로부터 데이터를 불러와 로컬 Room DB에 오프라인 캐싱을 수행합니다.
 * - Room DealDao의 정렬(popular/price_asc 등)과 무관하게 전용 센티넬 키(dealId = -1)로
 *   마지막 수신 페이지의 커서/오프셋을 독립 추적하여 APPEND 무한 루프를 차단합니다.
 */
@OptIn(ExperimentalPagingApi::class)
class HotDealsRemoteMediator(
    private val dealDao: DealDao,
    private val dealRemoteKeysDao: DealRemoteKeysDao,
    private val runInTransaction: suspend (suspend () -> Unit) -> Unit,
    private val apiService: ApiService,
    private val category: String?,
    private val keyword: String?,
    private val platform: String?,
    private val sort: String = "latest",
    private val onlyHot: Boolean = false,
    private val hideClosed: Boolean = false,
    private val includeClosed: Boolean = false,
    private val freeShippingOnly: Boolean = false,
    private val shop: String? = null,
    private val minPrice: Int? = null,
    private val maxPrice: Int? = null,
    private val clockMs: () -> Long = { System.currentTimeMillis() }
) : RemoteMediator<Int, DealEntity>() {

    constructor(
        database: AppDatabase,
        apiService: ApiService,
        category: String?,
        keyword: String?,
        platform: String?,
        sort: String = "latest",
        onlyHot: Boolean = false,
        hideClosed: Boolean = false,
        includeClosed: Boolean = false,
        freeShippingOnly: Boolean = false,
        shop: String? = null,
        minPrice: Int? = null,
        maxPrice: Int? = null
    ) : this(
        dealDao = database.dealDao(),
        dealRemoteKeysDao = database.dealRemoteKeysDao(),
        runInTransaction = { block -> database.withTransaction { block() } },
        apiService = apiService,
        category = category,
        keyword = keyword,
        platform = platform,
        sort = sort,
        onlyHot = onlyHot,
        hideClosed = hideClosed,
        includeClosed = includeClosed,
        freeShippingOnly = freeShippingOnly,
        shop = shop,
        minPrice = minPrice,
        maxPrice = maxPrice
    )

    internal fun buildCacheKey(): String = listOf(
        CategoryCatalog.normalizeCategoryQuery(category).orEmpty(),
        normalizePlatformQuery(platform).orEmpty(),
        keyword?.trim().orEmpty(),
        sort,
        onlyHot.toString(),
        hideClosed.toString(),
        includeClosed.toString(),
        freeShippingOnly.toString(),
        normalizeShopQuery(shop).orEmpty(),
        minPrice?.takeIf { it > 0 }?.toString().orEmpty(),
        maxPrice?.takeIf { it > 0 }?.toString().orEmpty()
    ).joinToString("|") { it.replace("%", "%25").replace("|", "%7C") }

    override suspend fun initialize(): InitializeAction {
        val currentKey = buildCacheKey()
        val lastSuccessMs = synchronized(refreshCacheLock) {
            if (activeRefreshCacheKey != currentKey) {
                return@synchronized null
            }
            lastRefreshSuccessMsByKey[currentKey]
        } ?: return InitializeAction.LAUNCH_INITIAL_REFRESH
        val elapsedMs = clockMs() - lastSuccessMs
        if (elapsedMs !in 0L until REFRESH_TTL_MS) {
            return InitializeAction.LAUNCH_INITIAL_REFRESH
        }
        val hasSentinel = dealRemoteKeysDao.getRemoteKeysForDealId(STREAM_CURSOR_SENTINEL_ID) != null
        return if (hasSentinel) {
            InitializeAction.SKIP_INITIAL_REFRESH
        } else {
            InitializeAction.LAUNCH_INITIAL_REFRESH
        }
    }

    override suspend fun load(
        loadType: LoadType,
        state: PagingState<Int, DealEntity>
    ): MediatorResult {
        return try {
            var cursorIndexedAt: String? = null
            var cursorId: Int? = null
            val offset = when (loadType) {
                LoadType.REFRESH -> 0
                LoadType.PREPEND -> {
                    return MediatorResult.Success(endOfPaginationReached = true)
                }
                LoadType.APPEND -> {
                    val remoteKeys = dealRemoteKeysDao.getRemoteKeysForDealId(STREAM_CURSOR_SENTINEL_ID)
                        ?: getRemoteKeyForLastItem(state)
                    if (remoteKeys == null) {
                        val hasExistingData = state.pages.any { it.data.isNotEmpty() }
                        return MediatorResult.Success(endOfPaginationReached = hasExistingData)
                    }
                    val cursorTs = remoteKeys.nextCursorIndexedAt?.trim()?.takeIf { it.isNotEmpty() }
                    val cursorItemId = remoteKeys.nextCursorId?.takeIf { it > 0 }
                    if (cursorTs != null && cursorItemId != null) {
                        cursorIndexedAt = cursorTs
                        cursorId = cursorItemId
                        0
                    } else {
                        remoteKeys.nextKey
                            ?: return MediatorResult.Success(endOfPaginationReached = true)
                    }
                }
            }

            val limit = state.config.pageSize
            val resolvedPlatform = normalizePlatformQuery(platform)
            val resolvedCategory = CategoryCatalog.normalizeCategoryQuery(category)
            val resolvedKeyword = keyword?.trim()?.takeIf { it.isNotEmpty() }
            val resolvedShop = normalizeShopQuery(shop)
            val resolvedMinPrice = minPrice?.takeIf { it > 0 }
            val resolvedMaxPrice = maxPrice?.takeIf { it > 0 }

            val response = apiService.getCommunityHotDeals(
                limit = limit,
                offset = offset,
                category = resolvedCategory,
                keyword = resolvedKeyword,
                platform = resolvedPlatform,
                sort = sort,
                cursorIndexedAt = cursorIndexedAt,
                cursorId = cursorId,
                includeClosed = if (includeClosed) true else null,
                onlyHot = if (onlyHot) true else null,
                hideClosed = if (hideClosed) true else null,
                freeShipping = if (freeShippingOnly) true else null,
                shop = resolvedShop,
                minPrice = resolvedMinPrice,
                maxPrice = resolvedMaxPrice
            )

            if (response.isSuccessful) {
                val responseBody = response.body()
                val deals = responseBody?.deals ?: emptyList()
                val nextCursor = responseBody?.nextCursor
                val resolvedNextCursorTs = nextCursor?.indexedAt?.trim()?.takeIf { it.isNotEmpty() }
                val resolvedNextCursorId = nextCursor?.id?.takeIf { it > 0 }
                val hasValidCursor = resolvedNextCursorTs != null && resolvedNextCursorId != null
                val hasMore = responseBody?.hasMore ?: false

                val isSameCursor = loadType == LoadType.APPEND &&
                    cursorIndexedAt != null &&
                    cursorId != null &&
                    hasValidCursor &&
                    resolvedNextCursorTs == cursorIndexedAt &&
                    resolvedNextCursorId == cursorId

                val candidateNextOffset = offset + deals.size
                val isOffsetNonAdvancing = loadType == LoadType.APPEND &&
                    !hasValidCursor &&
                    candidateNextOffset <= offset

                var endOfPaginationReached = deals.isEmpty() || !hasMore || isSameCursor || isOffsetNonAdvancing

                runInTransaction {
                    if (loadType == LoadType.REFRESH) {
                        dealRemoteKeysDao.clearRemoteKeys()
                        dealDao.clearAllDeals()
                    } else if (!endOfPaginationReached && deals.isNotEmpty()) {
                        val allAlreadyFetched = deals.all {
                            dealRemoteKeysDao.getRemoteKeysForDealId(it.id) != null
                        }
                        if (allAlreadyFetched) {
                            endOfPaginationReached = true
                        }
                    }

                    val prevKey = if (offset <= 0 || hasValidCursor) null else (offset - limit).coerceAtLeast(0)
                    val nextKey = if (endOfPaginationReached || hasValidCursor) null else candidateNextOffset
                    val persistedCursorTs = if (endOfPaginationReached || !hasValidCursor) null else resolvedNextCursorTs
                    val persistedCursorId = if (endOfPaginationReached || !hasValidCursor) null else resolvedNextCursorId

                    val sentinelKey = DealRemoteKeysEntity(
                        dealId = STREAM_CURSOR_SENTINEL_ID,
                        prevKey = prevKey,
                        nextKey = nextKey,
                        nextCursorIndexedAt = persistedCursorTs,
                        nextCursorId = persistedCursorId
                    )

                    val dealKeys = deals
                        .filter { it.id != STREAM_CURSOR_SENTINEL_ID }
                        .map { deal ->
                            DealRemoteKeysEntity(
                                dealId = deal.id,
                                prevKey = prevKey,
                                nextKey = nextKey,
                                nextCursorIndexedAt = persistedCursorTs,
                                nextCursorId = persistedCursorId
                            )
                        }

                    dealRemoteKeysDao.insertAll(dealKeys + sentinelKey)
                    if (deals.isNotEmpty()) {
                        dealDao.insertAll(
                            deals.map { deal ->
                                val baseEntity = toCachedEntityForCategory(deal, resolvedCategory)
                                if (resolvedShop != null && baseEntity.shopName.isNullOrBlank()) {
                                    baseEntity.copy(shopName = resolvedShop)
                                } else {
                                    baseEntity
                                }
                            }
                        )
                    }
                }

                if (loadType == LoadType.REFRESH) {
                    val currentKey = buildCacheKey()
                    synchronized(refreshCacheLock) {
                        lastRefreshSuccessMsByKey.clear()
                        lastRefreshSuccessMsByKey[currentKey] = clockMs()
                        activeRefreshCacheKey = currentKey
                    }
                }

                MediatorResult.Success(endOfPaginationReached = endOfPaginationReached)
            } else {
                MediatorResult.Error(HttpException(response))
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: IOException) {
            MediatorResult.Error(e)
        } catch (e: Exception) {
            MediatorResult.Error(e)
        }
    }

    companion object {
        const val STREAM_CURSOR_SENTINEL_ID = -1
        internal const val REFRESH_TTL_MS = 5 * 60 * 1000L
        private val refreshCacheLock = Any()
        private val lastRefreshSuccessMsByKey = ConcurrentHashMap<String, Long>()
        private var activeRefreshCacheKey: String? = null

        internal fun clearRefreshCache() {
            synchronized(refreshCacheLock) {
                lastRefreshSuccessMsByKey.clear()
                activeRefreshCacheKey = null
            }
        }

        internal fun toCachedEntityForCategory(deal: DealItem, resolvedCategory: String?): DealEntity {
            val baseEntity = DealEntity.fromDealItem(deal)
            if (resolvedCategory.isNullOrBlank() || resolvedCategory == "전체" || resolvedCategory == "핫딜모음") {
                return baseEntity
            }
            val alreadyMatchedByLocalSql = CategoryCatalog.matchesDealCategory(
                title = baseEntity.title,
                category = baseEntity.category,
                siteName = baseEntity.siteName,
                siteNames = baseEntity.siteNames,
                honeyScore = baseEntity.honeyScore,
                targetCategory = resolvedCategory
            )
            return if (alreadyMatchedByLocalSql) {
                baseEntity
            } else if (!resolvedCategory.contains(",")) {
                baseEntity.copy(category = resolvedCategory)
            } else {
                baseEntity
            }
        }
    }

    private fun normalizeShopQuery(rawShop: String?): String? {
        return rawShop?.trim()?.takeIf { it.isNotEmpty() && it != "전체" }
    }

    private fun normalizePlatformQuery(rawPlatform: String?): String? {
        if (rawPlatform.isNullOrBlank() || rawPlatform == "전체") return null
        val expanded = rawPlatform.split(",")
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .flatMap { token ->
                when (token) {
                    "빠삭" -> listOf("빠삭국내", "빠삭해외", "빠삭육아")
                    else -> listOf(token)
                }
            }
            .distinct()
        return expanded.joinToString(",").ifEmpty { null }
    }

    private suspend fun getRemoteKeyForLastItem(state: PagingState<Int, DealEntity>): DealRemoteKeysEntity? {
        return state.pages.lastOrNull { it.data.isNotEmpty() }?.data?.lastOrNull()
            ?.let { deal ->
                dealRemoteKeysDao.getRemoteKeysForDealId(deal.id)
            }
    }
}

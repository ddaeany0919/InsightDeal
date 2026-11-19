package com.ddaeany0919.insightdeal.presentation.home

import androidx.paging.ExperimentalPagingApi
import androidx.paging.LoadType
import androidx.paging.PagingState
import androidx.paging.RemoteMediator
import androidx.room.withTransaction
import com.ddaeany0919.insightdeal.local.db.AppDatabase
import com.ddaeany0919.insightdeal.local.db.DealEntity
import com.ddaeany0919.insightdeal.local.db.DealRemoteKeysEntity
import com.ddaeany0919.insightdeal.models.CategoryCatalog
import com.ddaeany0919.insightdeal.models.DealItem
import com.ddaeany0919.insightdeal.network.ApiService
import kotlinx.coroutines.CancellationException
import retrofit2.HttpException
import java.io.IOException

/**
 * Paging 3 RemoteMediator
 * - 네트워크 API로부터 데이터를 불러와 로컬 Room DB에 오프라인 캐싱을 수행합니다.
 */
@OptIn(ExperimentalPagingApi::class)
class HotDealsRemoteMediator(
    private val database: AppDatabase,
    private val apiService: ApiService,
    private val category: String?,
    private val keyword: String?,
    private val platform: String?,
    private val sort: String = "latest"
) : RemoteMediator<Int, DealEntity>() {

    override suspend fun load(
        loadType: LoadType,
        state: PagingState<Int, DealEntity>
    ): MediatorResult {
        return try {
            val offset = when (loadType) {
                LoadType.REFRESH -> 0
                LoadType.PREPEND -> {
                    return MediatorResult.Success(endOfPaginationReached = true)
                }
                LoadType.APPEND -> {
                    val remoteKeys = getRemoteKeyForLastItem(state)
                    val nextKey = remoteKeys?.nextKey
                        ?: return MediatorResult.Success(endOfPaginationReached = remoteKeys != null)
                    nextKey
                }
            }

            val limit = state.config.pageSize
            val resolvedPlatform = normalizePlatformQuery(platform)
            val resolvedCategory = if (category.isNullOrBlank() || category == "전체") {
                null
            } else {
                CategoryCatalog.normalizeCategoryKey(category)
            }

            val response = apiService.getCommunityHotDeals(
                limit = limit,
                offset = offset,
                category = resolvedCategory,
                keyword = if (keyword.isNullOrBlank()) null else keyword,
                platform = resolvedPlatform,
                sort = sort
            )

            if (response.isSuccessful) {
                val responseBody = response.body()
                val deals = responseBody?.deals ?: emptyList()
                val hasMore = responseBody?.hasMore ?: false
                val endOfPaginationReached = deals.isEmpty() || !hasMore

                database.withTransaction {
                    if (loadType == LoadType.REFRESH) {
                        database.dealRemoteKeysDao().clearRemoteKeys()
                        database.dealDao().clearAllDeals()
                    }

                    val prevKey = if (offset <= 0) null else (offset - limit).coerceAtLeast(0)
                    val nextKey = if (endOfPaginationReached) null else offset + deals.size

                    val keys = deals.map { deal ->
                        DealRemoteKeysEntity(
                            dealId = deal.id,
                            prevKey = prevKey,
                            nextKey = nextKey
                        )
                    }

                    database.dealRemoteKeysDao().insertAll(keys)
                    database.dealDao().insertAll(deals.map { toCachedEntityForCategory(it, resolvedCategory) })
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
            } else {
                baseEntity.copy(category = resolvedCategory)
            }
        }
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
                database.dealRemoteKeysDao().getRemoteKeysForDealId(deal.id)
            }
    }
}

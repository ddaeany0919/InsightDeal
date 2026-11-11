package com.ddaeany0919.insightdeal.presentation.home

import androidx.paging.ExperimentalPagingApi
import androidx.paging.LoadType
import androidx.paging.PagingState
import androidx.paging.RemoteMediator
import androidx.room.withTransaction
import com.ddaeany0919.insightdeal.local.db.AppDatabase
import com.ddaeany0919.insightdeal.local.db.DealEntity
import com.ddaeany0919.insightdeal.local.db.DealRemoteKeysEntity
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

            val response = apiService.getCommunityHotDeals(
                limit = limit,
                offset = offset,
                category = if (category == "전체") null else category,
                keyword = if (keyword.isNullOrBlank()) null else keyword,
                platform = resolvedPlatform,
                sort = sort
            )

            if (response.isSuccessful) {
                val responseBody = response.body()
                val deals = responseBody?.deals ?: emptyList()
                val endOfPaginationReached = deals.isEmpty() || deals.size < limit

                database.withTransaction {
                    val prevKey = if (offset == 0) null else offset - limit
                    val nextKey = if (endOfPaginationReached) null else offset + deals.size

                    val keys = deals.map { deal ->
                        DealRemoteKeysEntity(
                            dealId = deal.id,
                            prevKey = prevKey,
                            nextKey = nextKey
                        )
                    }

                    database.dealRemoteKeysDao().insertAll(keys)
                    database.dealDao().insertAll(deals.map { DealEntity.fromDealItem(it) })
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

package com.ddaeany0919.insightdeal.presentation.home

import androidx.paging.ExperimentalPagingApi
import androidx.paging.LoadType
import androidx.paging.RemoteMediator.MediatorResult
import androidx.paging.RemoteMediator
import androidx.paging.PagingState
import androidx.room.withTransaction
import com.ddaeany0919.insightdeal.local.db.AppDatabase
import com.ddaeany0919.insightdeal.local.db.DealEntity
import com.ddaeany0919.insightdeal.local.db.DealRemoteKeysEntity
import com.ddaeany0919.insightdeal.network.ApiService
import java.io.IOException
import retrofit2.HttpException

/**
 * 🛰️ Paging 3 RemoteMediator
 * - 네트워크 API로부터 데이터를 불러와 로컬 Room DB에 오프라인 캐싱을 수행합니다.
 * - 오프라인 상태에서도 캐싱된 DB 데이터를 노출함으로써 중단 없는 UX를 보장합니다.
 */
@OptIn(ExperimentalPagingApi::class)
class HotDealsRemoteMediator(
    private val database: AppDatabase,
    private val apiService: ApiService,
    private val category: String?,
    private val keyword: String?,
    private val platform: String?
) : RemoteMediator<Int, DealEntity>() {

    override suspend fun load(
        loadType: LoadType,
        state: PagingState<Int, DealEntity>
    ): MediatorResult {
        return try {
            // 1. 현재 로드 타입에 따른 Offset(페이지 키) 계산
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

            android.util.Log.d("HotDealsRemoteMediator", "Loading data: offset=$offset, limit=$limit, category=$category, keyword=$keyword, platform=$platform")
            
            // 2. 네트워크 API 호출
            val response = apiService.getCommunityHotDeals(
                limit = limit,
                offset = offset,
                category = if (category == "전체") null else category,
                keyword = if (keyword.isNullOrBlank()) null else keyword,
                platform = if (platform == "전체") null else platform
            )

            if (response.isSuccessful) {
                val responseBody = response.body()
                val deals = responseBody?.deals ?: emptyList()
                val endOfPaginationReached = deals.isEmpty() || deals.size < limit

                // 3. 로컬 DB 트랜잭션 수행 (캐싱)
                database.withTransaction {
                    if (loadType == LoadType.REFRESH) {
                        database.dealRemoteKeysDao().clearRemoteKeys()
                        database.dealDao().clearAllDeals()
                    }

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
                android.util.Log.e("HotDealsRemoteMediator", "API Error: ${response.code()} ${response.message()}")
                MediatorResult.Error(HttpException(response))
            }
        } catch (e: IOException) {
            android.util.Log.e("HotDealsRemoteMediator", "Network IO Exception during mediation", e)
            MediatorResult.Error(e)
        } catch (e: Exception) {
            android.util.Log.e("HotDealsRemoteMediator", "Unexpected Exception during mediation", e)
            MediatorResult.Error(e)
        }
    }

    private suspend fun getRemoteKeyForLastItem(state: PagingState<Int, DealEntity>): DealRemoteKeysEntity? {
        return state.pages.lastOrNull { it.data.isNotEmpty() }?.data?.lastOrNull()
            ?.let { deal ->
                database.dealRemoteKeysDao().getRemoteKeysForDealId(deal.id)
            }
    }

    private suspend fun getRemoteKeyClosestToCurrentPosition(state: PagingState<Int, DealEntity>): DealRemoteKeysEntity? {
        return state.anchorPosition?.let { position ->
            state.closestItemToPosition(position)?.id?.let { id ->
                database.dealRemoteKeysDao().getRemoteKeysForDealId(id)
            }
        }
    }
}

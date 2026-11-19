package com.ddaeany0919.insightdeal.presentation.home

import androidx.paging.PagingSource
import androidx.paging.PagingState
import com.ddaeany0919.insightdeal.models.CategoryCatalog
import com.ddaeany0919.insightdeal.models.DealItem
import com.ddaeany0919.insightdeal.network.ApiService
import kotlinx.coroutines.CancellationException
import java.util.concurrent.ConcurrentHashMap

class HotDealsPagingSource(
    private val apiService: ApiService,
    private val category: String? = null,
    private val keyword: String? = null,
    private val platform: String? = null,
    private val sort: String = "latest",
    private val includeClosed: Boolean? = null,
    private val onlyHot: Boolean? = null,
    private val hideClosed: Boolean? = null
) : PagingSource<Int, DealItem>() {

    private val cursorMap = ConcurrentHashMap<Int, Pair<String, Int>>()

    override suspend fun load(params: LoadParams<Int>): LoadResult<Int, DealItem> {
        val currentKey = params.key ?: 0
        val limit = params.loadSize
        val cursor = if (currentKey == 0) {
            cursorMap.clear()
            null
        } else {
            cursorMap[currentKey]
        }
        val requestOffset = if (cursor != null) 0 else currentKey

        return try {
            val resolvedPlatform = when {
                platform.isNullOrBlank() || platform == "전체" -> null
                platform == "빠삭" -> "빠삭국내,빠삭해외,빠삭육아"
                else -> platform
            }
            val resolvedCategory = if (category.isNullOrBlank() || category == "전체") {
                null
            } else {
                CategoryCatalog.normalizeCategoryKey(category)
            }
            val response = apiService.getCommunityHotDeals(
                limit = limit,
                offset = requestOffset,
                category = resolvedCategory,
                keyword = keyword?.takeIf { it.isNotBlank() },
                platform = resolvedPlatform,
                sort = sort,
                cursorIndexedAt = cursor?.first,
                cursorId = cursor?.second,
                includeClosed = includeClosed,
                onlyHot = onlyHot,
                hideClosed = hideClosed
            )
            if (response.isSuccessful) {
                val body = response.body()
                val data = body?.deals ?: emptyList()
                val nextCursor = body?.nextCursor
                val hasValidCursor = nextCursor != null && nextCursor.indexedAt.isNotBlank() && nextCursor.id > 0
                val hasMore = body?.hasMore ?: false
                val endReached = data.isEmpty() || (!hasMore && !hasValidCursor)
                val nextKey = if (endReached) null else currentKey + data.size

                if (nextKey != null && hasValidCursor) {
                    cursorMap[nextKey] = nextCursor!!.indexedAt to nextCursor.id
                }

                LoadResult.Page(
                    data = data,
                    prevKey = if (currentKey <= 0) null else (currentKey - limit).coerceAtLeast(0),
                    nextKey = nextKey
                )
            } else {
                LoadResult.Error(Exception("API Error: ${response.code()}"))
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            LoadResult.Error(e)
        }
    }

    override fun getRefreshKey(state: PagingState<Int, DealItem>): Int? {
        return state.anchorPosition?.let { anchorPosition ->
            state.closestPageToPosition(anchorPosition)?.prevKey?.plus(state.config.pageSize)
                ?: state.closestPageToPosition(anchorPosition)?.nextKey?.minus(state.config.pageSize)
        }
    }
}

package com.ddaeany0919.insightdeal.presentation.home

import androidx.paging.PagingSource
import androidx.paging.PagingState
import com.ddaeany0919.insightdeal.models.CategoryCatalog
import com.ddaeany0919.insightdeal.models.DealItem
import com.ddaeany0919.insightdeal.network.ApiService
import kotlinx.coroutines.CancellationException

class HotDealsPagingSource(
    private val apiService: ApiService,
    private val category: String? = null,
    private val keyword: String? = null,
    private val platform: String? = null,
    private val sort: String = "latest"
) : PagingSource<Int, DealItem>() {

    override suspend fun load(params: LoadParams<Int>): LoadResult<Int, DealItem> {
        val offset = params.key ?: 0
        val limit = params.loadSize

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
                offset = offset,
                category = resolvedCategory,
                keyword = keyword,
                platform = resolvedPlatform,
                sort = sort
            )
            if (response.isSuccessful) {
                val body = response.body()
                val data = body?.deals ?: emptyList()
                val hasMore = body?.hasMore ?: false
                LoadResult.Page(
                    data = data,
                    prevKey = if (offset <= 0) null else (offset - limit).coerceAtLeast(0),
                    nextKey = if (data.isEmpty() || !hasMore) null else offset + data.size
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

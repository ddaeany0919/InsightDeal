package com.ddaeany0919.insightdeal.presentation.home

import androidx.paging.PagingSource
import androidx.paging.PagingState
import com.ddaeany0919.insightdeal.models.DealItem
import com.ddaeany0919.insightdeal.network.ApiService

class HotDealsPagingSource(
    private val apiService: ApiService,
    private val category: String? = null,
    private val keyword: String? = null,
    private val platform: String? = null
) : PagingSource<Int, DealItem>() {

    override suspend fun load(params: LoadParams<Int>): LoadResult<Int, DealItem> {
        val offset = params.key ?: 0
        val limit = params.loadSize
        
        return try {
            android.util.Log.d("HotDealsPagingSource", "Calling getCommunityHotDeals: limit=$limit, offset=$offset, category=$category, keyword=$keyword, platform=$platform")
            val response = apiService.getCommunityHotDeals(
                limit = limit, 
                offset = offset, 
                category = if (category == "전체") null else category,
                keyword = keyword,
                platform = if (platform == "전체") null else platform
            )
            if (response.isSuccessful) {
                val data = response.body()?.deals ?: emptyList()
                android.util.Log.d("HotDealsPagingSource", "getCommunityHotDeals success, count: ${data.size}")
                LoadResult.Page(
                    data = data,
                    prevKey = if (offset == 0) null else offset - limit,
                    nextKey = if (data.isEmpty() || data.size < limit) null else offset + limit
                )
            } else {
                android.util.Log.e("HotDealsPagingSource", "getCommunityHotDeals failed: ${response.code()} ${response.message()}")
                LoadResult.Error(Exception("API Error: ${response.code()}"))
            }
        } catch (e: Exception) {
            android.util.Log.e("HotDealsPagingSource", "Exception in load()", e)
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

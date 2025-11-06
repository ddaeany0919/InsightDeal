package com.ddaeany0919.insightdeal.presentation.wishlist

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException

class HttpWishlistRepository : WishlistRepository {
    private val client = OkHttpClient()
    private val baseUrl = "http://10.0.2.2:8000/api"  // Android 에뮬레이터
    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    override suspend fun getWishlist(userId: String): List<WishlistItem> = withContext(Dispatchers.IO) {
        try {
            val url = "$baseUrl/wishlist?user_id=$userId"
            val request = Request.Builder().url(url).get().build()
            val response = client.newCall(request).execute()
            if (response.isSuccessful) {
                // TODO: JSON 파싱
                emptyList()
            } else {
                throw IOException("목록 가져오기 실패: ${response.code}")
            }
        } catch (e: Exception) {
            Log.e("WishlistRepo", "getWishlist 실패: $e")
            throw e
        }
    }

    override suspend fun addItem(keyword: String, targetPrice: Int, userId: String): WishlistItem = withContext(Dispatchers.IO) {
        try {
            val json = JSONObject().apply {
                put("keyword", keyword)
                put("target_price", targetPrice)
                put("user_id", userId)
            }
            val body = json.toString().toRequestBody(jsonMediaType)
            val request = Request.Builder().url("$baseUrl/wishlist").post(body).build()
            val response = client.newCall(request).execute()
            if (response.isSuccessful) {
                // TODO: JSON 파싱
                WishlistItem(id = 1, keyword = keyword, targetPrice = targetPrice)
            } else {
                throw IOException("추가 실패: ${response.code}")
            }
        } catch (e: Exception) {
            Log.e("WishlistRepo", "addItem 실패: $e")
            throw e
        }
    }

    override suspend fun deleteItem(id: Int, userId: String) = withContext(Dispatchers.IO) {
        try {
            val url = "$baseUrl/wishlist/$id?user_id=$userId"
            val request = Request.Builder().url(url).delete().build()
            val response = client.newCall(request).execute()
            if (!response.isSuccessful) {
                throw IOException("삭제 실패: ${response.code}")
            }
        } catch (e: Exception) {
            Log.e("WishlistRepo", "deleteItem 실패: $e")
            throw e
        }
    }

    override suspend fun checkPrice(id: Int, userId: String): WishlistItem = withContext(Dispatchers.IO) {
        try {
            val json = JSONObject().apply { put("user_id", userId) }
            val body = json.toString().toRequestBody(jsonMediaType)
            val request = Request.Builder().url("$baseUrl/wishlist/$id/check-price").post(body).build()
            val response = client.newCall(request).execute()
            if (response.isSuccessful) {
                // TODO: JSON 파싱
                WishlistItem(id = id, keyword = "키워드", targetPrice = 10000, currentLowestPrice = 9000, currentLowestPlatform = "네이버쇼핑")
            } else {
                throw IOException("가격 체크 실패: ${response.code}")
            }
        } catch (e: Exception) {
            Log.e("WishlistRepo", "checkPrice 실패: $e")
            throw e
        }
    }

    override suspend fun analyzeLink(url: String) = withContext(Dispatchers.IO) {
        try {
            val json = JSONObject().apply {
                put("url", url)
                put("target_price", 50000)  // 임시
            }
            val body = json.toString().toRequestBody(jsonMediaType)
            val request = Request.Builder().url("$baseUrl/product/analyze-link").post(body).build()
            val response = client.newCall(request).execute()
            if (!response.isSuccessful) {
                throw IOException("링크 분석 실패: ${response.code}")
            }
        } catch (e: Exception) {
            Log.e("WishlistRepo", "analyzeLink 실패: $e")
            throw e
        }
    }

    override suspend fun addFromLink(url: String, targetPrice: Int, userId: String): WishlistItem = withContext(Dispatchers.IO) {
        try {
            val json = JSONObject().apply {
                put("url", url)
                put("target_price", targetPrice)
                put("user_id", userId)
                put("auto_track", true)
            }
            val body = json.toString().toRequestBody(jsonMediaType)
            val request = Request.Builder().url("$baseUrl/wishlist/add-from-link").post(body).build()
            val response = client.newCall(request).execute()
            if (response.isSuccessful) {
                // TODO: JSON 파싱
                WishlistItem(id = 2, keyword = "AI분석키워드", targetPrice = targetPrice)
            } else {
                throw IOException("링크 추가 실패: ${response.code}")
            }
        } catch (e: Exception) {
            Log.e("WishlistRepo", "addFromLink 실패: $e")
            throw e
        }
    }
}

package com.ddaeany0919.insightdeal.presentation.wishlist

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException
import java.time.LocalDateTime

class HttpWishlistHelper {
    private val client = OkHttpClient()
    private val baseUrl = "http://10.0.2.2:8000/api"
    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    suspend fun fetchWishlist(userId: String): List<WishlistItem> = withContext(Dispatchers.IO) {
        try {
            val url = "$baseUrl/wishlist?user_id=$userId"
            val request = Request.Builder().url(url).get().build()
            val response = client.newCall(request).execute()
            if (response.isSuccessful) {
                emptyList()
            } else {
                throw IOException("목록 가져오기 실패: ${response.code}")
            }
        } catch (e: Exception) {
            Log.e("WishlistHelper", "fetchWishlist 실패: $e")
            throw e
        }
    }

    suspend fun createWishlist(keyword: String, targetPrice: Int, userId: String): WishlistItem = withContext(Dispatchers.IO) {
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
                WishlistItem(
                    id = 1,
                    keyword = keyword,
                    targetPrice = targetPrice,
                    currentLowestPrice = null,
                    currentLowestPlatform = null,
                    lastChecked = null,
                    createdAt = LocalDateTime.now(),
                    updatedAt = LocalDateTime.now()
                )
            } else {
                throw IOException("추가 실패: ${response.code}")
            }
        } catch (e: Exception) {
            Log.e("WishlistHelper", "createWishlist 실패: $e")
            throw e
        }
    }

    suspend fun deleteWishlist(id: Int, userId: String): Unit = withContext(Dispatchers.IO) {
        try {
            val url = "$baseUrl/wishlist/$id?user_id=$userId"
            val request = Request.Builder().url(url).delete().build()
            val response = client.newCall(request).execute()
            if (!response.isSuccessful) {
                throw IOException("삭제 실패: ${response.code}")
            }
        } catch (e: Exception) {
            Log.e("WishlistHelper", "deleteWishlist 실패: $e")
            throw e
        }
    }

    suspend fun checkPrice(wishlistId: Int, userId: String): String = withContext(Dispatchers.IO) {
        try {
            val json = JSONObject().apply { put("user_id", userId) }
            val body = json.toString().toRequestBody(jsonMediaType)
            val request = Request.Builder().url("$baseUrl/wishlist/$wishlistId/check-price").post(body).build()
            val response = client.newCall(request).execute()
            if (response.isSuccessful) {
                "가격 체크 완료"
            } else {
                throw IOException("가격 체크 실패: ${response.code}")
            }
        } catch (e: Exception) {
            Log.e("WishlistHelper", "checkPrice 실패: $e")
            throw e
        }
    }

    suspend fun analyzeLink(url: String, targetPrice: Int?, userId: String): Unit = withContext(Dispatchers.IO) {
        try {
            val json = JSONObject().apply {
                put("url", url)
                if (targetPrice != null) put("target_price", targetPrice)
                put("user_id", userId)
            }
            val body = json.toString().toRequestBody(jsonMediaType)
            val request = Request.Builder().url("$baseUrl/product/analyze-link").post(body).build()
            val response = client.newCall(request).execute()
            if (!response.isSuccessful) {
                throw IOException("링크 분석 실패: ${response.code}")
            }
        } catch (e: Exception) {
            Log.e("WishlistHelper", "analyzeLink 실패: $e")
            throw e
        }
    }

    suspend fun addFromLink(url: String, targetPrice: Int, userId: String): WishlistItem = withContext(Dispatchers.IO) {
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
                WishlistItem(
                    id = 2,
                    keyword = "AI분석키워드",
                    targetPrice = targetPrice,
                    currentLowestPrice = null,
                    currentLowestPlatform = null,
                    lastChecked = null,
                    createdAt = LocalDateTime.now(),
                    updatedAt = LocalDateTime.now()
                )
            } else {
                throw IOException("링크 추가 실패: ${response.code}")
            }
        } catch (e: Exception) {
            Log.e("WishlistHelper", "addFromLink 실패: $e")
            throw e
        }
    }
}

package com.ddaeany0919.insightdeal.presentation.wishlist

import android.util.Log
import com.ddaeany0919.insightdeal.data.network.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import retrofit2.HttpException
import java.io.IOException
import java.net.SocketTimeoutException

class WishlistRepository {
    private val TAG = "WishlistRepo"
    private val MAX_RETRIES = 3
    private val RETRY_DELAY_MS = 1000L
    
    // Use stable network configuration
    private var apiService: WishlistApiService? = null
    
    private suspend fun getApiService(): WishlistApiService {
        if (apiService == null) {
            Log.d(TAG, "getApiService: 초기화 시작")
            apiService = WishlistApiService.createWithStableConfig()
            Log.d(TAG, "getApiService: 초기화 완료")
        }
        return apiService!!
    }

    /**
     * Get wishlist with automatic retry mechanism
     */
    suspend fun getWishlist(userId: String) = withContext(Dispatchers.IO) {
        Log.d(TAG, "getWishlist: API 호출 시작 - userId=$userId")
        
        executeWithRetry("getWishlist") {
            val service = getApiService()
            val response = service.getWishlist(userId)
            Log.d(TAG, "getWishlist: API 응답 성공 - userId=$userId, count=${response.size}")
            response.map { it.toWishlistItem() }
        }
    }

    /**
     * Create wishlist item with retry
     */
    suspend fun createWishlist(
        keyword: String, 
        targetPrice: Int, 
        userId: String
    ) = withContext(Dispatchers.IO) {
        Log.d(TAG, "createWishlist: API 호출 시작 - keyword=$keyword, targetPrice=$targetPrice, userId=$userId")
        
        executeWithRetry("createWishlist") {
            val service = getApiService()
            val request = WishlistCreateRequest(keyword, targetPrice, userId)
            val result = service.createWishlist(request).toWishlistItem()
            Log.d(TAG, "createWishlist: 성공 - keyword=$keyword")
            result
        }
    }

    /**
     * Add from link with improved network handling (Phase 1)
     */
    suspend fun addFromLink(
        url: String, 
        targetPrice: Int, 
        userId: String
    ) = withContext(Dispatchers.IO) {
        Log.d(TAG, "addFromLink: 시작 - url=$url, targetPrice=$targetPrice, userId=$userId")
        
        executeWithRetry("addFromLink") {
            val service = getApiService()
            
            try {
                // TODO: Phase 2 - Replace with actual API call
                // val request = LinkAddRequest(url, targetPrice, userId)
                // val result = service.addFromLink(request).toWishlistItem()
                
                // Temporary fallback - extract product name from URL
                val productName = extractProductNameFromUrl(url)
                Log.d(TAG, "addFromLink: 상품명 추출 완료 - '$productName'")
                
                val request = WishlistCreateRequest(productName, targetPrice, userId)
                val result = service.createWishlist(request).toWishlistItem()
                
                Log.d(TAG, "addFromLink: 위시리스트 추가 성공 - product: $productName")
                result
            } catch (e: IOException) {
                Log.e(TAG, "addFromLink: 네트워크 연결 실패 - $url", e)
                throw Exception("네트워크 연결에 실패했습니다. 서버 상태를 확인해 주세요.")
            } catch (e: SocketTimeoutException) {
                Log.e(TAG, "addFromLink: 연결 타임아웃 - $url", e)
                throw Exception("서버 연결이 너무 느립니다. 다시 시도해 주세요.")
            }
        }
    }

    /**
     * Analyze product link (Phase 1 preparation)
     */
    suspend fun analyzeLink(url: String, userId: String): ProductAnalysis = withContext(Dispatchers.IO) {
        Log.d(TAG, "analyzeLink: 시작 - url=$url, userId=$userId")
        
        executeWithRetry("analyzeLink") {
            // TODO: Phase 2 - Replace with actual AI analysis API
            // val service = getApiService()
            // val request = AnalyzeLinkRequest(url, userId)
            // val result = service.analyzeLink(request)
            
            // Temporary mock analysis for Phase 1
            delay(1500) // Simulate processing time
            val productName = extractProductNameFromUrl(url)
            val mockAnalysis = ProductAnalysis(
                productName = productName,
                brand = "추출된 브랜드",
                category = "기타",
                estimatedLowestPrice = null,
                confidence = 0.85f
            )
            
            Log.d(TAG, "analyzeLink: 목 분석 완료 - product: $productName")
            mockAnalysis
        }
    }

    /**
     * Delete wishlist with simplified single-endpoint approach (Phase 1)
     */
    suspend fun deleteWishlist(wishlistId: Int, userId: String): Boolean = withContext(Dispatchers.IO) {
        Log.d(TAG, "deleteWishlist: 시작 - id=$wishlistId, userId=$userId")
        
        executeWithRetry("deleteWishlist") {
            try {
                val service = getApiService()
                val response = service.deleteWithQuery(wishlistId, userId)
                
                if (response.isSuccessful) {
                    Log.d(TAG, "deleteWishlist: 성공 - id=$wishlistId, http=${response.code()}")
                    true
                } else {
                    Log.w(TAG, "deleteWishlist: HTTP 실패 - id=$wishlistId, code=${response.code()}, message=${response.message()}")
                    false
                }
            } catch (e: Exception) {
                Log.e(TAG, "deleteWishlist: 예외 발생 - id=$wishlistId", e)
                false
            }
        }
    }

    /**
     * Check price with retry
     */
    suspend fun checkPrice(wishlistId: Int, userId: String): String = withContext(Dispatchers.IO) {
        Log.d(TAG, "checkPrice: API 호출 시작 - id=$wishlistId, userId=$userId")
        
        executeWithRetry("checkPrice") {
            val service = getApiService()
            val response = service.checkWishlistPrice(wishlistId, userId)
            Log.d(TAG, "checkPrice: 완료 - id=$wishlistId, message=${response.message}")
            response.message
        }
    }

    /**
     * Execute network call with automatic retry mechanism
     */
    private suspend fun <T> executeWithRetry(
        operationName: String,
        operation: suspend () -> T
    ): T {
        var lastException: Exception? = null
        
        repeat(MAX_RETRIES) { attempt ->
            try {
                Log.d(TAG, "$operationName: 시도 ${attempt + 1}/$MAX_RETRIES")
                return operation()
            } catch (e: SocketTimeoutException) {
                Log.w(TAG, "$operationName: 연결 타임아웃 (시도 ${attempt + 1}) - ${e.message}")
                lastException = e
                if (attempt < MAX_RETRIES - 1) {
                    delay(RETRY_DELAY_MS * (attempt + 1)) // Exponential backoff
                }
            } catch (e: IOException) {
                Log.w(TAG, "$operationName: 네트워크 오류 (시도 ${attempt + 1}) - ${e.message}")
                lastException = e
                if (attempt < MAX_RETRIES - 1) {
                    delay(RETRY_DELAY_MS)
                }
            } catch (e: HttpException) {
                Log.e(TAG, "$operationName: HTTP 오류 - code=${e.code()}, message=${e.message()}")
                throw e // HTTP errors should not be retried
            } catch (e: Exception) {
                Log.e(TAG, "$operationName: 예상치 못한 오류", e)
                throw e
            }
        }
        
        Log.e(TAG, "$operationName: 최대 재시도 횟수 초과 ($MAX_RETRIES회)")
        throw lastException ?: Exception("$operationName 실행 실패")
    }

    /**
     * Extract product name from URL for temporary fallback
     * TODO: Replace with AI analysis in Phase 2
     */
    private fun extractProductNameFromUrl(url: String): String {
        return try {
            Log.d(TAG, "extractProductNameFromUrl: 시작 - $url")
            
            val productName = when {
                url.contains("coupang.com") -> {
                    // Extract from Coupang URL pattern
                    val match = Regex("/vp/products/(\d+)").find(url)
                    if (match != null) {
                        "쿠팡 상품 (${match.groupValues[1]})"
                    } else {
                        "쿠팡 상품"
                    }
                }
                url.contains("11st.co.kr") -> "11번가 상품"
                url.contains("shopping.naver.com") -> "네이버 쇼핑 상품"
                else -> "링크로 추가된 상품"
            }
            
            Log.d(TAG, "extractProductNameFromUrl: 추출 완료 - '$productName'")
            productName
        } catch (e: Exception) {
            Log.w(TAG, "URL에서 상품명 추출 실패: $url", e)
            "링크로 추가된 상품"
        }
    }

    /**
     * Data class for product analysis result
     */
    data class ProductAnalysis(
        val productName: String,
        val brand: String?,
        val category: String?,
        val estimatedLowestPrice: Int?,
        val confidence: Float
    )
}
package com.ddaeany0919.insightdeal.presentation.wishlist

import android.util.Log
import com.ddaeany0919.insightdeal.network.ApiService
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

private const val TAG_NET = "WishlistNet"

fun createWishlistApiLogging(baseUrl: String, level: HttpLoggingInterceptor.Level = HttpLoggingInterceptor.Level.BODY): ApiService {
    val httpLogger = HttpLoggingInterceptor { msg -> Log.d(TAG_NET, msg) }.apply { setLevel(level) }
    val client = OkHttpClient.Builder().addInterceptor(httpLogger).build()
    return Retrofit.Builder()
        .baseUrl(baseUrl)
        .client(client)
        .addConverterFactory(GsonConverterFactory.create())
        .build()
        .create(ApiService::class.java)
}

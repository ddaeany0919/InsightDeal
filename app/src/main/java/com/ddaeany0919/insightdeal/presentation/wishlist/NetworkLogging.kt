package com.ddaeany0919.insightdeal.presentation.wishlist

import android.util.Log
import com.ddaeany0919.insightdeal.data.network.WishlistApiService
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

private const val TAG_NET = "WishlistNet"

fun createWishlistApiLogging(baseUrl: String, level: HttpLoggingInterceptor.Level = HttpLoggingInterceptor.Level.BODY): WishlistApiService {
    val httpLogger = HttpLoggingInterceptor { msg -> Log.d(TAG_NET, msg) }.apply { setLevel(level) }
    val client = OkHttpClient.Builder().addInterceptor(httpLogger).build()
    return Retrofit.Builder()
        .baseUrl(baseUrl)
        .client(client)
        .addConverterFactory(GsonConverterFactory.create())
        .build()
        .create(WishlistApiService::class.java)
}

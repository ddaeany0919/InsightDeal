package com.ddaeany0919.insightdeal.network

import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

object DealsRetrofitClient {
    private const val BASE_URL = "http://10.0.2.2:8000/"

    private val retrofit = Retrofit.Builder()
        .baseUrl(BASE_URL)
        .addConverterFactory(GsonConverterFactory.create())
        .build()

    val dealsApiService: DealsApiService = retrofit.create(DealsApiService::class.java)
}

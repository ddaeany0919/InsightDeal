package com.ddaeany0919.insightdeal.presentation.wishlist

import android.content.Context
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

fun schedulePriceCheckWork(context: Context) {
    val workRequest = PeriodicWorkRequestBuilder<PriceCheckWorker>(1, TimeUnit.DAYS).build()
    WorkManager.getInstance(context).enqueueUniquePeriodicWork(
        "price_check",
        ExistingPeriodicWorkPolicy.KEEP,
        workRequest
    )
}
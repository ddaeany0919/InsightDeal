package com.insightdeal.worker

import android.content.Context
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

class PriceCheckScheduler(private val context: Context) {

    fun schedulePeriodicPriceCheck() {
        val workRequest = PeriodicWorkRequestBuilder<PriceCheckWorker>(15, TimeUnit.MINUTES)
            .build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            "PriceCheckWork",
            ExistingPeriodicWorkPolicy.REPLACE,
            workRequest
        )
    }
}

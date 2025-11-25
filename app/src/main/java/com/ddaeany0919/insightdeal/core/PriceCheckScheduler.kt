package com.ddaeany0919.insightdeal.core

import android.content.Context
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.Worker
import androidx.work.WorkerParameters
import java.util.concurrent.TimeUnit

class PriceCheckWorker(appContext: Context, workerParams: WorkerParameters) : Worker(appContext, workerParams) {
    override fun doWork(): Result {
        // 가격 체크 로직 구현
        return Result.success()
    }
}

object PriceCheckScheduler {
    private const val WORK_NAME = "PriceCheckWork"

    fun scheduleDailyWork(context: Context) {
        val workRequest = PeriodicWorkRequestBuilder<PriceCheckWorker>(1, TimeUnit.DAYS)
            .build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            workRequest
        )
    }
}

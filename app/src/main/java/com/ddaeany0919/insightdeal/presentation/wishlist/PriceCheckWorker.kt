package com.ddaeany0919.insightdeal.presentation.wishlist

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class PriceCheckWorker(appContext: Context, workerParams: WorkerParameters) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        return withContext(Dispatchers.IO) {
            try {
                // 가격 체크 작업 실행 로직
                Result.success()
            } catch (e: Exception) {
                Result.retry()
            }
        }
    }
}

import android.content.Context
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.Worker
import androidx.work.WorkerParameters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

// 기존 PriceCheckWorker 작업 클래스
class PriceCheckWorker(appContext: Context, workerParams: WorkerParameters) : Worker(appContext, workerParams) {
    override fun doWork(): Result {
        // 가격 체크 로직 구현
        checkPrice()
        return Result.success()
    }

    private fun checkPrice() {
        // 가격 체크 구현
        // 예: API 호출하여 가격 가져오기
    }
}

// 작업 예약을 관리하는 스케줄러
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

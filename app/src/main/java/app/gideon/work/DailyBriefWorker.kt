package app.gideon.work

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import app.gideon.GideonApp
import java.util.Calendar
import java.util.concurrent.TimeUnit

/** Refreshes the feeds every morning and posts the "brief du jour". */
class DailyBriefWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val app = applicationContext as GideonApp
        val cache = app.repository.refresh(app.store.disabledGroups) ?: return Result.retry()
        if (app.store.dailyBrief) {
            val headlines = cache.items.filter { !it.isVideo }.take(3)
            Notifications.showBrief(applicationContext, headlines, cache.items.size)
        }
        return Result.success()
    }

    companion object {
        private const val NAME = "daily_brief"

        fun schedule(context: Context) {
            val now = Calendar.getInstance()
            val next = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, 8)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                if (before(now)) add(Calendar.DAY_OF_YEAR, 1)
            }
            val request = PeriodicWorkRequestBuilder<DailyBriefWorker>(24, TimeUnit.HOURS)
                .setInitialDelay(next.timeInMillis - now.timeInMillis, TimeUnit.MILLISECONDS)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .build()
            WorkManager.getInstance(context)
                .enqueueUniquePeriodicWork(NAME, ExistingPeriodicWorkPolicy.UPDATE, request)
        }

        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(NAME)
        }
    }
}

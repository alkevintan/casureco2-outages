package com.casureco2.outages.worker

import android.content.Context
import androidx.work.*
import com.casureco2.outages.util.Config
import java.util.*
import java.util.concurrent.TimeUnit

object WorkScheduler {

    fun scheduleWeekly(context: Context) {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val delay = calculateInitialDelay()

        val weeklyWork = PeriodicWorkRequestBuilder<PipelineTriggerWorker>(7, TimeUnit.DAYS)
            .setInitialDelay(delay, TimeUnit.MILLISECONDS)
            .setConstraints(constraints)
            .build()

        WorkManager.getInstance(context)
            .enqueueUniquePeriodicWork(
                "weekly_scrape",
                ExistingPeriodicWorkPolicy.KEEP,
                weeklyWork
            )
    }

    fun runNow(context: Context) {
        val scrape = OneTimeWorkRequestBuilder<ScrapingWorker>().build()
        val parse = OneTimeWorkRequestBuilder<ParserWorker>().build()
        val ics = OneTimeWorkRequestBuilder<IcsWorker>().build()
        val sync = OneTimeWorkRequestBuilder<GitHubSyncWorker>().build()

        WorkManager.getInstance(context)
            .beginWith(scrape)
            .then(parse)
            .then(ics)
            .then(sync)
            .enqueue()
    }

    private fun calculateInitialDelay(): Long {
        val now = Calendar.getInstance(TimeZone.getTimeZone("Asia/Manila"))
        val target = Calendar.getInstance(TimeZone.getTimeZone("Asia/Manila")).apply {
            set(Calendar.DAY_OF_WEEK, Config.WORKER_SCHEDULE_DAY_OF_WEEK)
            set(Calendar.HOUR_OF_DAY, Config.WORKER_SCHEDULE_HOUR)
            set(Calendar.MINUTE, Config.WORKER_SCHEDULE_MINUTE)
            set(Calendar.SECOND, 0)
            if (before(now)) {
                add(Calendar.WEEK_OF_YEAR, 1)
            }
        }
        return target.timeInMillis - now.timeInMillis
    }
}

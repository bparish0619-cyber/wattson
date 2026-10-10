package com.example.wattson.data.poll

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.example.wattson.data.WattsonRepository
import com.example.wattson.model.PollingMode
import java.util.concurrent.TimeUnit

/**
 * Starts or stops background polling to match the account and the chosen mode.
 * The 15-minute job runs in both modes: in Battery saver it is the poller; in Continuous
 * it is a backstop that catches up if the service was stopped by the system.
 */
object PollScheduler {
    private const val WORK_NAME = "wattson-poll"

    fun apply(context: Context) {
        val app = context.applicationContext
        val repo = WattsonRepository.getInstance(app)
        val workManager = WorkManager.getInstance(app)
        if (!repo.isLinked) {
            workManager.cancelUniqueWork(WORK_NAME)
            app.stopService(Intent(app, PollingService::class.java))
            return
        }
        val request = PeriodicWorkRequestBuilder<PollWorker>(15, TimeUnit.MINUTES)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .build()
        workManager.enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, request)

        if (repo.settings.value.pollingMode == PollingMode.CONTINUOUS) {
            try {
                ContextCompat.startForegroundService(app, Intent(app, PollingService::class.java))
            } catch (e: IllegalStateException) {
                // Android 12+ refuses to start a foreground service from the background;
                // the periodic job keeps polling until the app is next opened.
            }
        } else {
            app.stopService(Intent(app, PollingService::class.java))
        }
    }
}

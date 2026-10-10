package com.example.wattson.data.poll

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.wattson.data.WattsonRepository
import com.example.wattson.model.PollingMode
import kotlinx.coroutines.delay

/**
 * Runs every 15 minutes. Battery saver: polls, and while a drive is in progress keeps
 * polling every 30 s for up to 9 minutes. Continuous: a backstop poll if the service
 * hasn't polled recently (e.g. the system stopped it).
 */
class PollWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val repo = WattsonRepository.getInstance(applicationContext)
        if (!repo.isLinked || repo.needsReauth.value) return Result.success()

        if (repo.settings.value.pollingMode == PollingMode.CONTINUOUS) {
            val age = System.currentTimeMillis() / 1000 - (repo.lastPollTs.value ?: 0L)
            if (age > 2 * repo.nextPollIntervalSeconds() + 60) repo.pollNow()
            return Result.success()
        }

        repo.pollNow()
        val deadline = System.currentTimeMillis() + PollPolicy.BURST_MAX_S * 1000
        while (repo.nextPollIntervalSeconds() <= PollPolicy.DRIVING_S && System.currentTimeMillis() < deadline) {
            delay(PollPolicy.DRIVING_S * 1000)
            repo.pollNow()
        }
        return Result.success()
    }
}

package com.example.wattson.data.poll

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.example.wattson.MainActivity
import com.example.wattson.R
import com.example.wattson.data.WattsonRepository
import com.example.wattson.model.PollingMode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Continuous mode: the adaptive poller from upstream as a foreground service.
 * 30 s while driving (with a CPU wake lock so drives are captured with the screen off),
 * 5 min parked, 30 min while the car sleeps.
 */
class PollingService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var loop: Job? = null
    private var wakeLock: PowerManager.WakeLock? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "Vehicle tracking", NotificationManager.IMPORTANCE_LOW).apply {
                description = "Shown while Wattson polls your Rivian in the background"
            }
        )
        // The specialUse type exists from Android 14; earlier versions take no type.
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE else 0
        ServiceCompat.startForeground(this, NOTIFICATION_ID, notification("Starting…"), type)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val repo = WattsonRepository.getInstance(this)
        if (!repo.isLinked || repo.settings.value.pollingMode != PollingMode.CONTINUOUS) {
            stopSelf()
            return START_NOT_STICKY
        }
        if (loop?.isActive != true) {
            loop = scope.launch {
                while (isActive) {
                    val ok = repo.pollNow()
                    val interval = repo.nextPollIntervalSeconds()
                    holdCpu(interval <= PollPolicy.DRIVING_S, interval)
                    updateNotification(
                        when {
                            repo.needsReauth.value -> "Rivian sign-in expired. Open Wattson to link again."
                            !ok -> "Couldn't reach Rivian; retrying"
                            interval <= PollPolicy.DRIVING_S -> "Recording a drive"
                            interval >= PollPolicy.ASLEEP_S -> "Vehicle asleep; checking every 30 min"
                            else -> "Parked; checking every 5 min"
                        }
                    )
                    if (repo.needsReauth.value) {
                        stopSelf()
                        break
                    }
                    delay(interval * 1000)
                }
            }
        }
        return START_STICKY
    }

    /** Keeps the CPU awake only between 30-second polls while driving. */
    private fun holdCpu(hold: Boolean, intervalS: Long) {
        if (hold) {
            val lock = wakeLock ?: getSystemService(PowerManager::class.java)
                .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "wattson:drive").also {
                    it.setReferenceCounted(false)
                    wakeLock = it
                }
            lock.acquire((intervalS + 30) * 1000)
        } else {
            wakeLock?.takeIf { it.isHeld }?.release()
        }
    }

    private fun updateNotification(text: String) {
        getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, notification(text))
    }

    private fun notification(text: String): Notification {
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_wattson)
            .setContentTitle("Wattson is tracking your Rivian")
            .setContentText(text)
            .setContentIntent(open)
            .setOngoing(true)
            .setSilent(true)
            .build()
    }

    override fun onDestroy() {
        wakeLock?.takeIf { it.isHeld }?.release()
        scope.cancel()
        super.onDestroy()
    }

    private companion object {
        const val CHANNEL_ID = "tracking"
        const val NOTIFICATION_ID = 1
    }
}

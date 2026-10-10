package com.example.wattson.data.poll

import com.example.wattson.data.analysis.Analyzer
import com.example.wattson.data.analysis.Snapshot

/**
 * Adaptive cadence from upstream poller.py: fast while driving (the car is awake anyway,
 * so polling costs it nothing), moderate while parked and awake, heartbeat only while asleep.
 * Reads hit Rivian's cloud cache, not the car.
 */
object PollPolicy {
    const val DRIVING_S = 30L
    const val PARKED_S = 5 * 60L
    const val ASLEEP_S = 30 * 60L

    /** Battery saver: a driving burst inside one background job stays under its ~10 min limit. */
    const val BURST_MAX_S = 9 * 60L

    private const val MOVE_GPS_MI = 0.06 // ~100 m; parked GPS jitter stays well under this
    private const val SPEED_DRIVE_EPS = 0.5

    fun isDriving(last: Snapshot?, prev: Snapshot?): Boolean {
        if (last == null) return false
        if ((last.gear ?: "").uppercase() in Analyzer.DRIVE_GEARS) return true
        if ((last.speed ?: 0.0) > SPEED_DRIVE_EPS) return true
        if (prev == null) return false
        // The R2's gear and power fields are often null: odometer and GPS movement are what we have.
        val odoNow = last.odometerM
        val odoPrev = prev.odometerM
        if (odoNow != null && odoPrev != null && odoNow > odoPrev) return true
        val la1 = last.lat
        val lo1 = last.lon
        val la0 = prev.lat
        val lo0 = prev.lon
        return la1 != null && lo1 != null && la0 != null && lo0 != null &&
            Analyzer.haversineMi(la0, lo0, la1, lo1) > MOVE_GPS_MI
    }

    /** Seconds until the next poll, from the two most recent snapshots. */
    fun intervalSeconds(last: Snapshot?, prev: Snapshot?): Long = when {
        isDriving(last, prev) -> DRIVING_S
        // cloudConnection.isOnline=false is the sleep signal (powerState is always null on the R2).
        last?.cloudOnline == false && last.chargerState != "charging_active" -> ASLEEP_S
        else -> PARKED_S
    }
}

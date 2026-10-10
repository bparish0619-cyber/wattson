package com.example.wattson.data

import com.example.wattson.data.analysis.Snapshot
import com.example.wattson.data.poll.PollPolicy
import org.junit.Assert.assertEquals
import org.junit.Test

class PollPolicyTest {
    private val parked = Snapshot(ts = 1000, gear = "P", lat = 34.0, lon = -118.0, odometerM = 5000.0, cloudOnline = true)

    @Test
    fun drivingWhenOdometerOrGpsMoves() {
        assertEquals(PollPolicy.DRIVING_S, PollPolicy.intervalSeconds(parked.copy(ts = 1300, odometerM = 5400.0), parked))
        assertEquals(PollPolicy.DRIVING_S, PollPolicy.intervalSeconds(parked.copy(ts = 1300, lat = 34.002), parked))
        assertEquals(PollPolicy.DRIVING_S, PollPolicy.intervalSeconds(parked.copy(gear = "D"), null))
    }

    @Test
    fun parkedAndAsleepTiers() {
        assertEquals(PollPolicy.PARKED_S, PollPolicy.intervalSeconds(parked.copy(ts = 1300), parked))
        assertEquals(PollPolicy.ASLEEP_S, PollPolicy.intervalSeconds(parked.copy(ts = 1300, cloudOnline = false), parked))
        // Charging keeps the parked cadence so the session's curve is captured.
        assertEquals(PollPolicy.PARKED_S, PollPolicy.intervalSeconds(parked.copy(ts = 1300, cloudOnline = false, chargerState = "charging_active"), parked))
    }
}

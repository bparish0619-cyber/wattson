package com.example.wattson.data

import com.example.wattson.data.analysis.Analyzer
import com.example.wattson.data.analysis.Snapshot
import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AnalyzerTest {
    private val zone = ZoneId.of("America/Los_Angeles")
    private val t0 = LocalDate.of(2026, 10, 1).atTime(9, 0).atZone(zone).toEpochSecond()
    private val analyzer = Analyzer(87.9, zone)

    private fun parked(ts: Long, soc: Double, odoM: Double, lat: Double = 34.0, lon: Double = -118.47, label: String? = "HOME", state: String? = null) =
        Snapshot(ts = ts, soc = soc, rangeMi = soc * 3.36, gear = "P", lat = lat, lon = lon, geoLabel = label, odometerM = odoM, chargerState = state)

    @Test
    fun observedDriveBecomesOneTripWithOdometerMiles() {
        val snaps = mutableListOf(parked(t0, 80.0, 1_000_000.0), parked(t0 + 600, 80.0, 1_000_000.0))
        // 10 polls 30 s apart while driving north ~0.5 mi per poll
        for (i in 1..10) {
            snaps += Snapshot(ts = t0 + 600 + i * 30L, soc = 80.0 - i * 0.2, gear = "D", lat = 34.0 + i * 0.007, lon = -118.47, odometerM = 1_000_000.0 + i * 800.0)
        }
        snaps += parked(t0 + 1500, 78.0, 1_008_000.0, lat = 34.07, label = null)
        snaps += parked(t0 + 2400, 78.0, 1_008_000.0, lat = 34.07, label = null)

        val trips = analyzer.derive(snaps).trips
        assertEquals(1, trips.size)
        val t = trips.single()
        assertEquals(8000 / 1609.344, t.miles, 0.01) // odometer, not GPS
        // The first moving poll already shows an odometer jump, so (as upstream) the drive is
        // measured from the parked bracket: 80.0% to 78.0%.
        assertEquals(2.0 / 100 * 87.9, t.kwhUsed, 0.01)
        assertEquals("HOME", t.startLabel) // departure from the bracketing parked snapshot
        assertTrue(t.path.size >= 10)
        assertFalse(t.bracketed)
    }

    @Test
    fun driveBetweenPollsIsBracketedFromOdometerJump() {
        val snaps = listOf(
            parked(t0, 80.0, 1_000_000.0),
            parked(t0 + 1800, 75.0, 1_020_000.0, lat = 34.1, label = "Work"),
            parked(t0 + 3600, 75.0, 1_020_000.0, lat = 34.1, label = "Work")
        )
        val t = analyzer.derive(snaps).trips.single()
        assertEquals(20_000 / 1609.344, t.miles, 0.01)
        assertEquals(t0, t.startTs) // bracket window is the measure
        assertEquals("HOME", t.startLabel)
        assertEquals("Work", t.endLabel)
    }

    @Test
    fun shortDriveRollsIntoErrands() {
        val snaps = listOf(
            parked(t0, 80.0, 1_000_000.0),
            Snapshot(ts = t0 + 60, soc = 79.9, gear = "D", lat = 34.005, lon = -118.47, odometerM = 1_001_500.0),
            parked(t0 + 120, 79.8, 1_001_500.0, lat = 34.006),
            parked(t0 + 1200, 79.8, 1_001_500.0, lat = 34.006)
        )
        val d = analyzer.derive(snaps)
        assertTrue(d.trips.isEmpty())
        assertEquals(1, d.errands.single().drives)
    }

    @Test
    fun chargeSessionsAreSplitAndClassified() {
        val snaps = mutableListOf<Snapshot>()
        var ts = t0
        // Home AC: 50% -> 60% over 48 min (11 kW), ending with charging_complete
        for (i in 0..8) snaps += parked(ts + i * 360L, 50.0 + i * 1.25, 1e6, state = if (i == 8) "charging_complete" else "charging_active")
        ts += 3 * 3600
        // Flat tail, then a DC session: 20% -> 70% over 30 min
        snaps += parked(ts, 60.0, 1e6)
        snaps += parked(ts + 600, 20.0, 1e6 + 5, label = "Rivian Adventure Network") // big drop: new segment
        for (i in 1..6) snaps += parked(ts + 600 + i * 300L, 20.0 + i * 8.33, 1e6 + 5, label = "Rivian Adventure Network")

        val charges = analyzer.derive(snaps).charges
        assertEquals(2, charges.size)
        val (home, dc) = charges
        assertFalse(home.isDc)
        assertEquals(10.0 / 100 * 87.9, home.kwhAdded, 0.01)
        assertEquals("HOME", home.label)
        assertTrue(dc.isDc)
        assertTrue(dc.avgKw!! > 20)
    }

    @Test
    fun overnightWindowBacksOutChargingAndCountsWakeUps() {
        val evening = LocalDate.of(2026, 10, 1).atTime(22, 0).atZone(zone).toEpochSecond()
        val snaps = mutableListOf<Snapshot>()
        var soc = 60.0
        for (i in 0..54) { // 22:00 -> 07:00 every 10 min
            val ts = evening + i * 600L
            if (i in 6..11) soc += 2.0 // a 12% charge around midnight
            soc -= 0.012
            if (i == 30 || i == 40) soc -= 0.3 // two wake-ups
            snaps += parked(ts, soc, 1e6, state = if (i == 11) "charging_complete" else if (i in 6..10) "charging_active" else null)
        }
        val night = analyzer.derive(snaps).nights.single()
        assertEquals(LocalDate.of(2026, 10, 1), night.day)
        assertEquals(9.0, night.hours, 0.01)
        // Idle drain only: 54 x 0.012% + 2 x 0.3% with the charge backed out. The charge segment
        // also absorbs the small drift before it (upstream behaviour), hence the tolerance.
        assertEquals((54 * 0.012 + 0.6) / 100 * 87.9, night.kwhLost, 0.15)
        assertEquals(2, night.wakeCount)
    }

    @Test
    fun liveChargeReportsPowerFromSocSlope() {
        val now = t0 + 1800
        val snaps = (0..6).map { i -> parked(t0 + 900 + i * 150L, 40.0 + i * 0.5, 1e6, state = "charging_active") }
        val live = analyzer.liveCharge(snaps, now)
        assertNotNull(live)
        // 0.5% per 150 s = 12%/h of 87.9 kWh ~ 10.5 kW
        assertEquals(10.5, live!!.kw!!, 0.2)
        assertNull(analyzer.liveCharge(snaps.map { it.copy(chargerState = "charging_complete") }, now))
    }

    @Test
    fun demoSimulationRunsThroughThePipeline() {
        val snaps = DemoDataProvider.snapshots(zone, LocalDate.of(2026, 10, 10))
        val d = Analyzer(DemoDataProvider.USABLE_KWH, zone, DemoDataProvider.labeler).derive(snaps)
        assertTrue(d.trips.size >= 20)
        assertEquals("Home", d.trips.first().startLabel)
        assertTrue(d.trips.any { it.endLabel == "Disneyland" })
        assertTrue(d.trips.all { it.miles > 0 && it.kwhUsed > 0 && it.path.size >= 2 })
        assertEquals(1, d.charges.count { it.isDc })
        assertTrue(d.charges.count { !it.isDc } >= 5)
        assertEquals(10, d.nights.size)
        assertEquals(1, d.nights.count { it.isAbnormal })
        assertTrue(d.nights.single { it.isAbnormal }.wakeCount > 0)
    }
}

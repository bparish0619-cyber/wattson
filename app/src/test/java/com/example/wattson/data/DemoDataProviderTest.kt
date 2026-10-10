package com.example.wattson.data

import com.example.wattson.model.AppSettings
import com.example.wattson.model.ChargerType
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DemoDataProviderTest {
    private val data = DemoDataProvider.generate(today = LocalDate.of(2026, 10, 10))

    @Test
    fun generatesTenDaysOfHistory() {
        assertTrue(data.trips.size >= 25)
        assertEquals(10, data.batteryTrend.size)
        assertEquals(10, data.packHealth.size)
        assertTrue(data.drains.isNotEmpty())
        assertTrue(data.charges.any { it.chargeType == ChargerType.DC_FAST && it.userCostPerKwh != null })
        assertTrue(data.charges.any { it.chargeType == ChargerType.HOME_AC })
    }

    @Test
    fun listsAreNewestFirst() {
        assertTrue(data.trips.zipWithNext().all { (a, b) -> a.startTs >= b.startTs })
        assertTrue(data.charges.zipWithNext().all { (a, b) -> a.startTs >= b.startTs })
        assertTrue(data.drains.zipWithNext().all { (a, b) -> a.sleepTs >= b.sleepTs })
    }

    @Test
    fun valuesArePhysicallyPlausible() {
        data.trips.forEach {
            assertTrue(it.miles > 0f && it.kwhUsed > 0f && it.endTs > it.startTs)
            assertTrue(it.miPerKwh in 2.5f..4.5f)
        }
        data.charges.forEach { assertTrue(it.kwhAdded > 0f && it.socEnd > it.socStart) }
        assertTrue(data.vehicle.soc in 10f..90f)
        assertTrue(data.batteryTrend.all { it.soc in 0f..100f })
    }

    @Test
    fun flagsExactlyTheProximityWakeNight() {
        val abnormal = data.drains.filter { it.isAbnormal }
        assertEquals(1, abnormal.size)
        assertTrue(abnormal.single().wakeCount > 0)
    }

    @Test
    fun fuelSavingsSkipsUnpricedSessionsAndSavesMoney() {
        val unpriced = data.charges.map { if (it.isDc) it.copy(userCostPerKwh = null) else it }
        val rows = FuelSavings.calculate(unpriced, AppSettings(), 3.1f)
        assertEquals(unpriced.count { it.isHome }, rows.size)
        assertTrue(rows.all { it.saved > 0f })
    }

    @Test
    fun fuelSavingsMath() {
        val session = data.charges.first { it.isHome }.copy(kwhAdded = 10f)
        val row = FuelSavings.calculate(listOf(session), AppSettings(homeKwhRate = 0.30f, gasMpg = 25f, gasPrice = 4.50f), 3f).single()
        assertEquals(3.0f, row.evCost, 0.001f)          // 10 kWh x $0.30
        assertEquals(5.4f, row.gasCost, 0.001f)         // 30 mi / 25 mpg x $4.50
        assertEquals(2.4f, row.saved, 0.001f)
    }
}

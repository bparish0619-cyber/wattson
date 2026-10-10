package com.example.wattson.data

import com.example.wattson.model.AppSettings
import com.example.wattson.model.ChargerType
import com.example.wattson.model.ChargingSession
import org.junit.Assert.assertEquals
import org.junit.Test

class FuelSavingsTest {
    private fun session(id: Long, type: ChargerType, cost: Float? = null) = ChargingSession(
        id = id, startTs = 1_791_600_000 + id, endTs = 1_791_603_600 + id, minutes = 60f, socStart = 50f, socEnd = 61.4f,
        kwhAdded = 10f, milesAdded = 38f, avgKw = 10f, chargeType = type, label = "x", userCostPerKwh = cost
    )

    @Test
    fun homeAndPricedAwaySessionsOnly() {
        val rows = FuelSavings.calculate(
            listOf(session(1, ChargerType.HOME_AC), session(2, ChargerType.DC_FAST), session(3, ChargerType.DC_FAST, 0.48f), session(4, ChargerType.PUBLIC_AC)),
            AppSettings(), 3f
        )
        assertEquals(listOf(1L, 3L), rows.map { it.id })
    }

    @Test
    fun savingsMathUsesThatDaysGasPrice() {
        val row = FuelSavings.calculate(listOf(session(1, ChargerType.HOME_AC)), AppSettings(homeKwhRate = 0.30f, gasMpg = 25f), 3f) { 5.0f }.single()
        assertEquals(3.0f, row.evCost, 0.001f) // 10 kWh x $0.30
        assertEquals(6.0f, row.gasCost, 0.001f) // 30 mi / 25 mpg x $5.00
        assertEquals(3.0f, row.saved, 0.001f)
        assertEquals(5.0f, row.gasPrice, 0.001f)
    }
}

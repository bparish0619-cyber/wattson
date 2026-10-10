package com.example.wattson.data

import com.example.wattson.model.AppSettings
import com.example.wattson.model.ChargerType
import com.example.wattson.model.ChargingSession
import com.example.wattson.model.FuelSavingRow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object FuelSavings {
    /**
     * One row per priced session, as upstream report.py: home sessions use the home rate;
     * away sessions only count once the user has entered what they paid. The gas side drives the same miles
     * ([miPerKwh] x kWh) in a [AppSettings.gasMpg] SUV at that day's gas price.
     */
    fun calculate(
        charges: List<ChargingSession>,
        settings: AppSettings,
        miPerKwh: Float,
        gasPriceFor: (startTs: Long) -> Float = { settings.gasPrice }
    ): List<FuelSavingRow> {
        val dateFmt = SimpleDateFormat("MMM d", Locale.US)
        return charges.mapNotNull { session ->
            val rate = when (session.chargeType) {
                ChargerType.HOME_AC -> settings.homeKwhRate
                ChargerType.DC_FAST, ChargerType.PUBLIC_AC -> session.userCostPerKwh ?: return@mapNotNull null
            }
            if (session.kwhAdded <= 0f) return@mapNotNull null
            val gasPrice = gasPriceFor(session.startTs)
            val evCost = session.kwhAdded * rate
            val gallons = if (settings.gasMpg > 0) session.kwhAdded * miPerKwh / settings.gasMpg else 0f
            val gasCost = gallons * gasPrice
            FuelSavingRow(
                id = session.id,
                date = dateFmt.format(Date(session.startTs * 1000)),
                isDc = session.isDc,
                kwh = session.kwhAdded,
                evCost = evCost,
                gasPrice = gasPrice,
                gasCost = gasCost,
                saved = gasCost - evCost,
                label = session.label
            )
        }
    }
}

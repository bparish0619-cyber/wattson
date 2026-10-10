package com.example.wattson.data

import com.example.wattson.model.AppSettings
import com.example.wattson.model.ChargingSession
import com.example.wattson.model.FuelSavingRow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object FuelSavings {
    /**
     * One row per priced session: home sessions use the home rate, other sessions
     * only count once the user has entered what they paid. The gas side is the
     * cost of driving the same miles ([miPerKwh] x kWh) in a [AppSettings.gasMpg] SUV.
     */
    fun calculate(
        charges: List<ChargingSession>,
        settings: AppSettings,
        miPerKwh: Float
    ): List<FuelSavingRow> {
        val dateFmt = SimpleDateFormat("MMM d", Locale.US)
        return charges.mapNotNull { session ->
            val rate = if (session.isHome) settings.homeKwhRate else session.userCostPerKwh ?: return@mapNotNull null
            val evCost = session.kwhAdded * rate
            val gallons = if (settings.gasMpg > 0) session.kwhAdded * miPerKwh / settings.gasMpg else 0f
            val gasCost = gallons * settings.gasPrice
            FuelSavingRow(
                id = session.id,
                date = dateFmt.format(Date(session.startTs * 1000)),
                isDc = session.isDc,
                kwh = session.kwhAdded,
                evCost = evCost,
                gasPrice = settings.gasPrice,
                gasCost = gasCost,
                saved = gasCost - evCost,
                label = session.label
            )
        }
    }
}

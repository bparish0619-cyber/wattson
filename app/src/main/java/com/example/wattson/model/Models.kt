package com.example.wattson.model

data class VehicleStatus(
    val name: String,
    val model: String,
    val soc: Float, // Battery percentage 0..100
    val targetSoc: Float = 90f,
    val capacityKwh: Float = 91.4f,
    val usableKwh: Float = 87.9f,
    val rangeMi: Float,
    val ratedRangeMi: Float = 336f,
    val odometerMi: Double,
    val location: String,
    val isCharging: Boolean = false,
    val powerKw: Float = 0f,
    val chargingSessionStartTs: Long? = null,
    val chargingSessionStartSoc: Float? = null,
    val lastSyncTs: Long = System.currentTimeMillis()
)

data class Trip(
    val id: Long,
    val startTs: Long,
    val endTs: Long,
    val miles: Float,
    val kwhUsed: Float,
    val whPerMi: Float,
    val avgMph: Float,
    val startLabel: String,
    val endLabel: String,
    val tempF: Float? = null,
    val bracketed: Boolean = false,
    val viaLabel: String? = null,
    /** GPS fixes along the drive, for the route map. */
    val path: List<LatLon> = emptyList()
) {
    val miPerKwh: Float
        get() = if (kwhUsed > 0.05f) miles / kwhUsed else 0f

    val durationMinutes: Long
        get() = (endTs - startTs) / 60
}

data class LatLon(val lat: Double, val lon: Double)

data class DayErrand(
    val day: String,
    val title: String,
    val drives: Int,
    val miles: Float,
    val kwhUsed: Float,
    val trips: List<Trip> = emptyList()
) {
    val miPerKwh: Float
        get() = if (kwhUsed > 0.05f) miles / kwhUsed else 0f
}

enum class ChargerType {
    HOME_AC,
    PUBLIC_AC,
    DC_FAST
}

data class ChargingSession(
    val id: Long,
    val startTs: Long,
    val endTs: Long,
    val minutes: Float,
    val socStart: Float,
    val socEnd: Float,
    val kwhAdded: Float,
    val milesAdded: Float,
    val avgKw: Float,
    val chargeType: ChargerType,
    val label: String,
    val userCostPerKwh: Float? = null // Entered by user for DC chargers, null if unpriced
) {
    val isHome: Boolean
        get() = chargeType == ChargerType.HOME_AC

    val isDc: Boolean
        get() = chargeType == ChargerType.DC_FAST
}

data class OvernightDrain(
    val id: Long,
    val day: String,
    val label: String,
    val sleepTs: Long,
    val wakeTs: Long,
    val hours: Float,
    val socStart: Float,
    val socEnd: Float,
    val kwhLost: Float,
    val wattsAvg: Float,
    /** Rated range lost overnight, net of any driving or charging in the window. */
    val milesLost: Float? = null,
    val wakeCount: Int = 0,
    val isAbnormal: Boolean = false
)

data class FuelSavingRow(
    val id: Long,
    val date: String,
    val isDc: Boolean,
    val kwh: Float,
    val evCost: Float,
    val gasPrice: Float,
    val gasCost: Float,
    val saved: Float,
    val label: String
)

data class BatteryTrendPoint(
    val dateLabel: String,
    val soc: Float
)

data class PackHealthPoint(
    val dateLabel: String,
    val capacityKwh: Float,
    val impliedRangeMi: Float
)

enum class PollingMode {
    /** Foreground service: 30 s while driving, 5 min parked, 30 min asleep. */
    CONTINUOUS,
    /** Background job every 15 min, bursting to 30 s for a few minutes while driving. */
    BATTERY_SAVER
}

data class AppSettings(
    val homeKwhRate: Float = 0.30f,
    val gasMpg: Float = 25.0f,
    /** Used when AAA can't be reached. */
    val gasPrice: Float = 4.50f,
    val vehicleName: String = "Demo R2",
    val vehicleModel: String = "Rivian R2",
    /** Usable pack energy for SoC to kWh math (Rivian's rated usable figure). */
    val usableKwh: Float = 87.9f,
    /** Home charging location; charges within 250 m (or labelled HOME by the car) cost the home rate. */
    val homeLat: Double? = null,
    val homeLon: Double? = null,
    /** AAA gas-price source: state page and metro-area row. */
    val gasState: String = "CA",
    val gasMetro: String = "Los Angeles-Long Beach",
    val pollingMode: PollingMode = PollingMode.CONTINUOUS
)

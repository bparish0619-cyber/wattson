package com.example.wattson.data

import com.example.wattson.model.BatteryTrendPoint
import com.example.wattson.model.ChargerType
import com.example.wattson.model.ChargingSession
import com.example.wattson.model.OvernightDrain
import com.example.wattson.model.PackHealthPoint
import com.example.wattson.model.Trip
import com.example.wattson.model.VehicleStatus
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

/**
 * Fictional 10-day driving history for a Demo R2 based in Venice, Los Angeles.
 * Ported from the original demo.py generator. Place names are real; the driver
 * and history are fictional. Output is deterministic for a given end date.
 */
object DemoDataProvider {
    const val VEHICLE_NAME = "Demo R2"
    const val VEHICLE_MODEL = "Rivian R2"
    const val USABLE_KWH = 87.9f
    const val RATED_RANGE_MI = 336f
    const val CHARGE_TARGET = 90f

    private const val WH_PER_MI = 320.0
    private const val HOME_KW = 11f
    private const val DC_AVG_KW = 110f
    private const val DC_COST_PER_KWH = 0.48f
    private const val START_ODOMETER_MI = 943.0
    private const val ROAD_FACTOR = 1.25 // straight-line to road miles
    private const val PACK_CAPACITY_KWH = 91.5f

    private class Place(val label: String, val lat: Double, val lon: Double)

    private val places = mapOf(
        "home" to Place("Home", 33.99202, -118.47159),
        "antico_vinaio" to Place("All'Antico Vinaio", 34.06365, -118.30879),
        "cafe_gratitude" to Place("Cafe Gratitude", 33.99801, -118.47378),
        "whole_foods_venice" to Place("Whole Foods Venice", 34.00124, -118.46974),
        "sm_pier" to Place("Santa Monica Pier", 34.00890, -118.49740),
        "lobster_sm" to Place("The Lobster", 34.00608, -118.49146),
        "urth_sm" to Place("Urth Caffe Santa Monica", 34.00473, -118.48631),
        "disneyland" to Place("Disneyland", 33.81365, -117.91974),
        "newport_beach" to Place("Balboa Pier", 33.60000, -117.90041),
        "fashion_island" to Place("Fashion Island", 33.61604, -117.87548),
        "ran_oc" to Place("Rivian Adventure Network", 33.64000, -117.86000),
        "griffith" to Place("Griffith Observatory", 34.11822, -118.30029),
        "kanomwaan" to Place("Kanomwaan", 34.10218, -118.30550),
        "getty" to Place("Getty Center", 34.07702, -118.47401),
        "shin_sushi" to Place("Shin Sushi", 34.15876, -118.49426),
        "ranch_99" to Place("99 Ranch Market", 34.05605, -118.44186),
        "lax" to Place("LAX", 33.94217, -118.42136),
        "century_city" to Place("Westfield Century City", 34.05891, -118.41935),
        "erewhon_bh" to Place("Erewhon Beverly Hills", 34.06911, -118.40136),
        "grove" to Place("The Grove", 34.07260, -118.35835),
        "antico_nuovo" to Place("Antico Nuovo", 34.07632, -118.31070),
        "lacma" to Place("LACMA", 34.06374, -118.35894),
        "pink_wall" to Place("Paul Smith Pink Wall", 34.08076, -118.38959),
        "lucky_tiki" to Place("The Lucky Tiki", 34.08984, -118.37545),
        "urth_melrose" to Place("Urth Caffe Melrose", 34.08207, -118.37885),
        "camarillo_outlets" to Place("Camarillo Premium Outlets", 34.21562, -119.06461),
        "institution_ale" to Place("Institution Ale Co.", 34.21645, -119.01936),
        "conejo_garden" to Place("Conejo Valley Botanic Garden", 34.19172, -118.88545)
    )

    // Each day is a list of (place key, dwell minutes). "ran_oc" is a DC fast-charge stop.
    private val itinerary = listOf(
        listOf("home" to 0, "antico_vinaio" to 70, "cafe_gratitude" to 35, "whole_foods_venice" to 50, "home" to 0),
        listOf("home" to 0, "sm_pier" to 90, "lobster_sm" to 70, "urth_sm" to 30, "home" to 0),
        listOf("home" to 0, "disneyland" to 480, "home" to 0),
        listOf("home" to 0, "newport_beach" to 120, "fashion_island" to 90, "ran_oc" to 0, "home" to 0),
        listOf("home" to 0, "griffith" to 150, "kanomwaan" to 30, "home" to 0),
        listOf("home" to 0, "getty" to 150, "shin_sushi" to 75, "ranch_99" to 40, "home" to 0),
        listOf("home" to 0, "lax" to 20, "century_city" to 120, "erewhon_bh" to 35, "home" to 0),
        listOf("home" to 0, "grove" to 120, "antico_nuovo" to 100, "home" to 0),
        listOf("home" to 0, "lacma" to 120, "pink_wall" to 20, "lucky_tiki" to 45, "urth_melrose" to 30, "home" to 0),
        listOf("home" to 0, "camarillo_outlets" to 150, "institution_ale" to 60, "conejo_garden" to 60, "home" to 0)
    )

    private val dayStartHour = mapOf(7 to 14) // Grove day starts at 2 PM for dinner
    private val skipHomeChargeNights = setOf(1, 4, 7, 9) // last night unplugged so the charge demo has room
    private const val PROXIMITY_WAKE_NIGHT = 5

    data class Dataset(
        val vehicle: VehicleStatus,
        val trips: List<Trip>,
        val charges: List<ChargingSession>,
        val drains: List<OvernightDrain>,
        val batteryTrend: List<BatteryTrendPoint>,
        val packHealth: List<PackHealthPoint>
    )

    /** Builds the dataset for the [days] nights ending this morning. Lists are newest first, except chart series. */
    fun generate(
        zone: ZoneId = ZoneId.of("America/Los_Angeles"),
        today: LocalDate = LocalDate.now(zone),
        days: Int = 10
    ): Dataset {
        val rng = Random(42)
        val dayFmt = DateTimeFormatter.ofPattern("MMM d", Locale.US)
        val weekdayFmt = DateTimeFormatter.ofPattern("EEE", Locale.US)
        val shortFmt = DateTimeFormatter.ofPattern("M/d", Locale.US)

        var soc = CHARGE_TARGET.toDouble()
        var odometer = START_ODOMETER_MI
        val trips = mutableListOf<Trip>()
        val charges = mutableListOf<ChargingSession>()
        val drains = mutableListOf<OvernightDrain>()
        val trend = mutableListOf<BatteryTrendPoint>()
        val pack = mutableListOf<PackHealthPoint>()

        val startDay = today.minusDays(days.toLong())
        for (d in 0 until days) {
            val date = startDay.plusDays(d.toLong())
            var ts = date.atTime(dayStartHour[d] ?: 9, 0).atZone(zone).toEpochSecond()
            var here = places.getValue("home")
            val baseTempF = 62 + rng.nextInt(0, 12)

            for ((key, dwellMin) in itinerary[d % itinerary.size]) {
                val dest = places.getValue(key)
                val crowMiles = haversineMi(here, dest)
                if (crowMiles > 0.05) {
                    val miles = crowMiles * ROAD_FACTOR
                    val mph = (if (miles > 15) 52.0 else 27.0) * rng.nextDouble(0.9, 1.1)
                    val whPerMi = WH_PER_MI * (if (mph > 45) 1.08 else 0.95) * rng.nextDouble(0.9, 1.1)
                    val kwh = miles * whPerMi / 1000
                    val durationS = (miles / mph * 3600).toLong()
                    trips += Trip(
                        id = (trips.size + 1).toLong(),
                        startTs = ts,
                        endTs = ts + durationS,
                        miles = round1(miles),
                        kwhUsed = round2(kwh),
                        whPerMi = whPerMi.toFloat(),
                        avgMph = round1(mph),
                        startLabel = here.label,
                        endLabel = dest.label,
                        tempF = (baseTempF + rng.nextInt(-3, 6)).toFloat()
                    )
                    ts += durationS
                    soc -= kwh / USABLE_KWH * 100
                    odometer += miles
                    here = dest
                }

                if (key == "ran_oc") {
                    val kwh = ((CHARGE_TARGET - soc) / 100 * USABLE_KWH).coerceAtLeast(0.0)
                    val minutes = (kwh / DC_AVG_KW * 60).toFloat()
                    charges += session(charges.size + 1L, ts, minutes, soc, CHARGE_TARGET.toDouble(), kwh, DC_AVG_KW, ChargerType.DC_FAST, dest.label, DC_COST_PER_KWH)
                    ts += (minutes * 60).toLong()
                    soc = CHARGE_TARGET.toDouble()
                } else if (dwellMin > 0) {
                    ts += dwellMin * 60L
                }
            }

            // Plug in at home after the last drive, except on nights kept for clean drain readings.
            if (d !in skipHomeChargeNights && soc < CHARGE_TARGET - 0.5) {
                val plugIn = ts + 15 * 60
                val kwh = (CHARGE_TARGET - soc) / 100 * USABLE_KWH
                val minutes = (kwh / HOME_KW * 60).toFloat()
                charges += session(charges.size + 1L, plugIn, minutes, soc, CHARGE_TARGET.toDouble(), kwh, HOME_KW, ChargerType.HOME_AC, "Home", null)
                ts = plugIn + (minutes * 60).toLong()
                soc = CHARGE_TARGET.toDouble()
            }

            trend += BatteryTrendPoint(date.format(shortFmt), round1(soc))
            val capacity = PACK_CAPACITY_KWH - d * 0.015f
            pack += PackHealthPoint(date.format(shortFmt), capacity, RATED_RANGE_MI * capacity / PACK_CAPACITY_KWH)

            // Overnight vampire drain over the clean 10 PM to 7 AM window.
            val sleepTs = date.atTime(22, 0).atZone(zone).toEpochSecond()
            val wakeTs = date.plusDays(1).atTime(7, 0).atZone(zone).toEpochSecond()
            val proximityWakes = d == PROXIMITY_WAKE_NIGHT
            val kwhLost = if (proximityWakes) rng.nextDouble(1.15, 1.35) else rng.nextDouble(0.48, 0.64)
            val socStart = soc
            soc -= kwhLost / USABLE_KWH * 100
            if (ts <= sleepTs) {
                drains += OvernightDrain(
                    id = (drains.size + 1).toLong(),
                    day = date.format(dayFmt),
                    label = date.format(weekdayFmt),
                    sleepTs = sleepTs,
                    wakeTs = wakeTs,
                    hours = 9f,
                    socStart = round1(socStart),
                    socEnd = round1(soc),
                    kwhLost = round2(kwhLost),
                    wattsAvg = (kwhLost * 1000 / 9).toFloat(),
                    rangeStart = (socStart / 100 * RATED_RANGE_MI).toFloat(),
                    rangeEnd = (soc / 100 * RATED_RANGE_MI).toFloat(),
                    wakeCount = if (proximityWakes) 6 else rng.nextInt(0, 2)
                )
            }
        }

        val median = drains.map { it.kwhLost }.sorted().let { if (it.isEmpty()) 0f else it[it.size / 2] }
        val flaggedDrains = drains.map { it.copy(isAbnormal = it.kwhLost > 1.6f * median) }

        val vehicle = VehicleStatus(
            name = VEHICLE_NAME,
            model = VEHICLE_MODEL,
            soc = round1(soc),
            usableKwh = USABLE_KWH,
            rangeMi = (soc / 100 * RATED_RANGE_MI).toFloat(),
            ratedRangeMi = RATED_RANGE_MI,
            odometerMi = odometer,
            location = "Home"
        )

        return Dataset(
            vehicle = vehicle,
            trips = trips.reversed(),
            charges = charges.reversed(),
            drains = flaggedDrains.reversed(),
            batteryTrend = trend,
            packHealth = pack
        )
    }

    private fun session(
        id: Long, startTs: Long, minutes: Float, socStart: Double, socEnd: Double,
        kwh: Double, avgKw: Float, type: ChargerType, label: String, costPerKwh: Float?
    ) = ChargingSession(
        id = id,
        startTs = startTs,
        endTs = startTs + (minutes * 60).toLong(),
        minutes = minutes,
        socStart = round1(socStart),
        socEnd = round1(socEnd),
        kwhAdded = round1(kwh),
        milesAdded = (kwh / USABLE_KWH * RATED_RANGE_MI).toFloat(),
        avgKw = avgKw,
        chargeType = type,
        label = label,
        userCostPerKwh = costPerKwh
    )

    private fun haversineMi(a: Place, b: Place): Double {
        val lat1 = Math.toRadians(a.lat)
        val lat2 = Math.toRadians(b.lat)
        val dLat = lat2 - lat1
        val dLon = Math.toRadians(b.lon - a.lon)
        val h = sin(dLat / 2) * sin(dLat / 2) + cos(lat1) * cos(lat2) * sin(dLon / 2) * sin(dLon / 2)
        return 2 * 3958.8 * asin(sqrt(h))
    }

    private fun round1(x: Double): Float = (x * 10).roundToInt() / 10f
    private fun round2(x: Double): Float = (x * 100).roundToInt() / 100f
}

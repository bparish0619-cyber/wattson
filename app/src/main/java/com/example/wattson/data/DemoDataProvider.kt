package com.example.wattson.data

import com.example.wattson.data.analysis.Analyzer
import com.example.wattson.data.analysis.Snapshot
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.PI
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToLong
import kotlin.math.sin
import kotlin.random.Random

/**
 * Fictional 10-day history for a Demo R2 based in Venice, Los Angeles: a port of the
 * upstream demo.py simulator. It emits raw snapshots, so the demo runs through the same
 * [Analyzer] pipeline as a linked account. Place names are real; the driver is not.
 */
object DemoDataProvider {
    const val VEHICLE_NAME = "Demo R2"
    const val VEHICLE_MODEL = "Rivian R2"
    const val USABLE_KWH = 87.9
    const val RATED_RANGE_MI = 336.0
    const val GAS_PRICE = 4.89f // pinned, like upstream's demo
    const val DC_COST_PER_KWH = 0.48f

    private const val WH_PER_MI = 320.0
    private const val HOME_KW = 11.0
    private const val CHARGE_TARGET = 90.0

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

    // Each day: (place key, dwell minutes). "ran_oc" is a DC fast-charge stop.
    private val itinerary = listOf(
        listOf("home" to 0, "antico_vinaio" to 70, "cafe_gratitude" to 35, "whole_foods_venice" to 50, "home" to 0),
        listOf("home" to 0, "sm_pier" to 90, "lobster_sm" to 70, "urth_sm" to 30, "home" to 0),
        listOf("home" to 0, "disneyland" to 480, "home" to 0),
        listOf("home" to 0, "newport_beach" to 120, "fashion_island" to 90, "ran_oc" to -1, "home" to 0),
        listOf("home" to 0, "griffith" to 150, "kanomwaan" to 30, "home" to 0),
        listOf("home" to 0, "getty" to 150, "shin_sushi" to 75, "ranch_99" to 40, "home" to 0),
        listOf("home" to 0, "lax" to 20, "century_city" to 120, "erewhon_bh" to 35, "home" to 0),
        listOf("home" to 0, "grove" to 120, "antico_nuovo" to 100, "home" to 0),
        listOf("home" to 0, "lacma" to 120, "pink_wall" to 20, "lucky_tiki" to 45, "urth_melrose" to 30, "home" to 0),
        listOf("home" to 0, "camarillo_outlets" to 150, "institution_ale" to 60, "conejo_garden" to 60, "home" to 0)
    )

    private val dayStartHour = mapOf(7 to 14) // Grove day starts at 2 PM for dinner
    private val skipChargeNights = setOf(1, 4, 7) // so overnight drain shows clean
    private const val PROXIMITY_WAKE_NIGHT = 5 // repeated wake-ups: an abnormal night

    /** Demo place names: the nearest itinerary stop to a GPS fix, else the car's own label. */
    val labeler: (Double?, Double?, String?) -> String? = { lat, lon, geo ->
        val near = if (lat != null && lon != null) {
            places.values.minByOrNull { Analyzer.haversineMi(lat, lon, it.lat, it.lon) }
                ?.takeIf { Analyzer.haversineMi(lat, lon, it.lat, it.lon) < 0.3 }?.label
        } else null
        near ?: Analyzer.usableGeoLabel(geo)
    }

    /** Snapshots for the [days] days ending at 7 AM [today], oldest first. */
    fun snapshots(
        zone: ZoneId = ZoneId.systemDefault(),
        today: LocalDate = LocalDate.now(zone),
        days: Int = 10
    ): List<Snapshot> {
        val sim = Sim(today.minusDays(days.toLong()).atTime(9, 0).atZone(zone).toEpochSecond().toDouble(), zone)
        sim.emit("P", geoLabel = "Home", cloudOnline = false) // parked at home before the first drive
        for (d in 0 until days) {
            sim.dayIndex = d
            val day = Instant.ofEpochSecond(sim.ts.toLong()).atZone(zone).toLocalDate()
            val morning = day.atTime(dayStartHour[d] ?: 9, 0).atZone(zone).toEpochSecond().toDouble()
            if (morning > sim.ts) {
                sim.ts = morning
                sim.emit("P", geoLabel = "Home", cloudOnline = false)
            }
            for ((key, dwell) in itinerary[d % itinerary.size]) {
                val place = places.getValue(key)
                if (key == "ran_oc") {
                    sim.driveTo(place)
                    sim.chargeDcfc()
                    continue
                }
                sim.driveTo(place)
                if (dwell > 0) sim.dwell(dwell, place.label)
            }
            sim.overnight(charge = d !in skipChargeNights, proximityWakes = d == PROXIMITY_WAKE_NIGHT)
        }
        return sim.snaps
    }

    /** Session start timestamps that get the demo's DC price, like upstream's demo. */
    fun dcCosts(charges: List<com.example.wattson.data.analysis.DerivedCharge>): Map<Long, Float> =
        charges.filter { it.isDc }.associate { it.startTs to DC_COST_PER_KWH }

    private class Sim(var ts: Double, private val zone: ZoneId) {
        val snaps = mutableListOf<Snapshot>()
        var dayIndex = 0
        private var soc = CHARGE_TARGET
        private var odoM = 943 * 1609.344
        private var lat = places.getValue("home").lat
        private var lon = places.getValue("home").lon
        private val rng = Random(42)

        // Slight pack degradation over the window, for the pack-health card.
        private fun capKwh() = 91.5 - dayIndex * 0.015

        fun emit(
            gear: String, speed: Double? = null, chargerState: String? = null,
            geoLabel: String? = null, cloudOnline: Boolean = true
        ) {
            snaps += Snapshot(
                ts = ts.roundToLong(),
                soc = round2(soc),
                capacityKwh = round2(capKwh()),
                rangeMi = Math.round(soc / 100 * RATED_RANGE_MI * 10) / 10.0,
                batteryLimit = CHARGE_TARGET,
                chargerState = chargerState,
                gear = gear,
                lat = lat,
                lon = lon,
                speed = speed,
                geoLabel = geoLabel,
                odometerM = odoM,
                cloudOnline = cloudOnline,
                cloudSyncTs = ts.roundToLong()
            )
        }

        fun driveTo(place: Place) {
            val dist = Analyzer.haversineMi(lat, lon, place.lat, place.lon)
            if (dist < 0.05) return
            val speed = if (dist > 15) 52.0 else 27.0
            val whPerMi = WH_PER_MI * rng.nextDouble(0.9, 1.12) * (if (speed > 45) 1.06 else 1.0)
            val dur = dist / speed * 3600
            val steps = max(2, (dur / 120).toInt())
            val fromLat = lat
            val fromLon = lon
            for (i in 1..steps) {
                val f = i.toDouble() / steps
                val wobble = sin(f * PI) * 0.004 // routes shouldn't look laser-straight
                lat = fromLat + (place.lat - fromLat) * f + wobble * rng.nextDouble(-1.0, 1.0)
                lon = fromLon + (place.lon - fromLon) * f + wobble * rng.nextDouble(-1.0, 1.0)
                val miStep = dist / steps
                odoM += miStep * 1609.344
                soc -= miStep * whPerMi / 1000 / USABLE_KWH * 100
                ts += dur / steps
                emit("D", speed = speed * rng.nextDouble(0.85, 1.1))
            }
            lat = place.lat
            lon = place.lon
            emit("P", geoLabel = place.label) // parked arrival
        }

        fun dwell(minutes: Int, label: String) {
            var elapsed = 0
            while (elapsed + 10 < minutes) {
                ts += 600
                elapsed += 10
                emit("P", geoLabel = label, cloudOnline = false) // asleep mid-dwell
            }
            ts += (minutes - elapsed) * 60
            emit("P", geoLabel = label) // awake at departure
        }

        private fun chargeHome() {
            val need = CHARGE_TARGET - soc
            if (need <= 0.5) return
            val durS = need / 100 * USABLE_KWH / HOME_KW * 3600
            val steps = max(2, (durS / 300).toInt())
            for (i in 1..steps) {
                soc = min(soc + need / steps, CHARGE_TARGET)
                ts += durS / steps
                val done = i == steps || soc >= CHARGE_TARGET
                emit("P", chargerState = if (done) "charging_complete" else "charging_active", geoLabel = "Home")
                if (done) break
            }
        }

        // Tapered DC fast charge: 150 kW falling to 60 kW, stopping at the cap.
        fun chargeDcfc(minutes: Int = 25, cap: Double = 85.0) {
            val totalS = minutes * 60.0
            val steps = max(2, (totalS / 120).toInt())
            for (i in 1..steps) {
                val kw = 150 - (150 - 60) * (i.toDouble() / steps)
                var kwhStep = kw * (totalS / steps) / 3600
                if (soc + kwhStep / USABLE_KWH * 100 >= cap) kwhStep = max(0.0, (cap - soc) / 100 * USABLE_KWH)
                soc = min(soc + kwhStep / USABLE_KWH * 100, cap)
                ts += totalS / steps
                val done = i == steps || soc >= cap
                emit(
                    "P", chargerState = if (done) "charging_complete" else "charging_active",
                    geoLabel = "Rivian Adventure Network"
                )
                if (done) break
            }
        }

        fun overnight(charge: Boolean, proximityWakes: Boolean) {
            // Park until 11 PM, optionally charge, then sleep until 7 AM.
            val now = Instant.ofEpochSecond(ts.toLong()).atZone(zone)
            var bed = now.withHour(23).withMinute(0).withSecond(0)
            if (bed.isBefore(now)) bed = bed.plusDays(1)
            val bedTs = bed.toEpochSecond().toDouble()
            var first = true
            while (ts < bedTs) {
                ts = min(ts + 600, bedTs)
                if (!first) soc -= 0.008
                emit("P", geoLabel = "Home", cloudOnline = first)
                first = false
            }
            if (charge) {
                // Pre-charge dip so the charge session splits from the parked tail.
                soc -= 0.08
                emit("P", geoLabel = "Home", cloudOnline = false)
                chargeHome()
            }
            // Vampire drain (~0.07%/h, ~1.5 mi/night); a few wake-ups on the abnormal night.
            val wakeTs = bed.plusHours(8).toEpochSecond().toDouble()
            var step = 0
            while (ts < wakeTs) {
                ts = min(ts + 600, wakeTs)
                soc -= 0.012
                if (proximityWakes && step % 9 == 4) soc -= 0.2
                step++
                emit("P", geoLabel = "Home", cloudOnline = false)
            }
        }

        private fun round2(x: Double) = Math.round(x * 100) / 100.0
    }
}

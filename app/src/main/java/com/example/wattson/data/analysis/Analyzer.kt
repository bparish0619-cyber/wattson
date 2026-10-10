package com.example.wattson.data.analysis

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/** One poll of Rivian's vehicleState, as stored. Odometer is in meters (the API's unit). */
data class Snapshot(
    val ts: Long,
    val soc: Double? = null,
    val capacityKwh: Double? = null,
    val rangeMi: Double? = null,
    val batteryLimit: Double? = null,
    val chargerState: String? = null,
    val chargePort: String? = null,
    val powerState: String? = null,
    val gear: String? = null,
    val lat: Double? = null,
    val lon: Double? = null,
    val speed: Double? = null,
    val geoLabel: String? = null,
    val odometerM: Double? = null,
    val cloudOnline: Boolean? = null,
    val cloudSyncTs: Long? = null
)

data class DerivedTrip(
    val startTs: Long,
    val endTs: Long,
    val miles: Double,
    val kwhUsed: Double,
    val whPerMi: Double?,
    val avgMph: Double?,
    val bracketed: Boolean,
    val startLat: Double?,
    val startLon: Double?,
    val endLat: Double?,
    val endLon: Double?,
    val startLabel: String?,
    val endLabel: String?,
    val viaLabel: String?,
    val path: List<Pair<Double, Double>>
)

data class DerivedCharge(
    val startTs: Long,
    val endTs: Long,
    val minutes: Double,
    val socStart: Double,
    val socEnd: Double,
    val kwhAdded: Double,
    val milesAdded: Double?,
    val avgKw: Double?,
    val isDc: Boolean,
    val lat: Double?,
    val lon: Double?,
    val label: String?
)

/** Drives shorter than [Analyzer.TRIP_MIN_SECONDS], rolled up per local day. */
data class Errand(val day: LocalDate, val drives: Int, val miles: Double, val kwhUsed: Double)

/** One clean 10 PM to 7 AM parked window. [day] is the evening's date. */
data class Night(
    val day: LocalDate,
    val sleepTs: Long,
    val wakeTs: Long,
    val hours: Double,
    val socStart: Double,
    val socEnd: Double,
    val kwhLost: Double,
    val watts: Double,
    val milesLost: Double?,
    val wakeCount: Int,
    val isAbnormal: Boolean
)

data class LiveCharge(val sessionStartTs: Long, val socStart: Double, val kw: Double?)

data class Derived(
    val trips: List<DerivedTrip>,
    val charges: List<DerivedCharge>,
    val errands: List<Errand>,
    val nights: List<Night>
)

/**
 * Port of the upstream Wattson analyzer (analyze.py plus the overnight-window logic
 * from report.py). Turns raw snapshots into trips, charging sessions, short-errand
 * rollups and overnight drain nights. Pure Kotlin: [labeler] supplies place names.
 */
class Analyzer(
    private val usableKwh: Double,
    private val zone: ZoneId,
    private val labeler: (lat: Double?, lon: Double?, geoLabel: String?) -> String? = { _, _, g -> usableGeoLabel(g) }
) {
    private class Pt(val s: Snapshot, val bracket: Boolean = false, val bracketKind: String? = null)

    private class ErrandAcc(var drives: Int = 0, var miles: Double = 0.0, var kwh: Double = 0.0)

    /** [snaps] must be sorted by timestamp. */
    fun derive(snaps: List<Snapshot>): Derived {
        val trips = mutableListOf<DerivedTrip>()
        val errands = sortedMapOf<LocalDate, ErrandAcc>()

        // ---- trips ----
        var cur = mutableListOf<Pt>()
        var curEnd = -1
        var prev: Snapshot? = null
        for ((idx, s) in snaps.withIndex()) {
            // Odometer fallback: a drive that happened entirely between polls still
            // shows up as an odometer jump; bracket it with the previous snapshot.
            val odoJump = odoJumpMi(s, prev) > ODO_JUMP_MIN_MI
            if (moving(s, prev) || odoJump) {
                if (cur.isNotEmpty() && s.ts - cur.last().s.ts > TRIP_END_GAP) {
                    closeTrip(cur, trips, errands, snaps, curEnd)
                    cur = mutableListOf()
                }
                if (cur.isEmpty() && prev != null) {
                    // The last pre-drive snapshot is the true departure point.
                    cur.add(Pt(prev, bracket = true, bracketKind = if (odoJump) "odo" else "seen"))
                }
                cur.add(Pt(s))
                curEnd = idx
            } else if (cur.isNotEmpty() && s.ts - cur.last().s.ts > TRIP_END_GAP) {
                closeTrip(cur, trips, errands, snaps, curEnd)
                cur = mutableListOf()
            }
            prev = s
        }
        if (cur.isNotEmpty()) closeTrip(cur, trips, errands, snaps, curEnd)

        // ---- parked runs (charges) ----
        val runs = mutableListOf<List<Snapshot>>()
        var run = mutableListOf<Snapshot>()
        prev = null
        for (s in snaps) {
            val parked = (s.gear ?: "").uppercase() in PARKED_GEARS
            if (parked && !moving(s, prev)) {
                if (run.isNotEmpty() && odoJumpMi(s, prev) > ODO_JUMP_MIN_MI) {
                    // Moved between polls without being seen driving: start a new run.
                    runs.add(run)
                    run = mutableListOf()
                }
                run.add(s)
            } else if (run.isNotEmpty()) {
                runs.add(run)
                run = mutableListOf()
            }
            prev = s
        }
        if (run.isNotEmpty()) runs.add(run)

        val charges = mutableListOf<DerivedCharge>()
        for (r in runs) chargesFromRun(r, charges)

        val errandRows = errands.filter { it.value.drives > 0 }
            .map { (day, e) -> Errand(day, e.drives, e.miles, e.kwh) }

        return Derived(trips, charges, errandRows, nights(snaps, trips, charges))
    }

    // ---------------------------------------------------------------- trips

    private fun closeTrip(
        cur: List<Pt>, trips: MutableList<DerivedTrip>, errands: MutableMap<LocalDate, ErrandAcc>,
        snaps: List<Snapshot>, endIdx: Int
    ) {
        val miles = tripMiles(cur)
        if (miles < GPS_MOVE_MI) return // didn't actually go anywhere
        // Sub-quantum micro-drive: only real if the car actually relocated.
        if (miles < 0.1 && !relocated(snaps, endIdx)) return

        val drive = cur.filter { !it.bracket }.ifEmpty { cur }
        // Odometer-jump trips happened between polls: the bracket window is the measure.
        // Observed drives are timed from the first in-motion snapshot.
        val first = if ((cur[0].bracketKind ?: "odo") == "odo") cur[0].s else drive[0].s
        val last = cur.last().s
        val duration = last.ts - first.ts
        val kwh = max(0.0, ((first.soc ?: 0.0) - (last.soc ?: 0.0)) / 100.0 * usableKwh)
        if (duration < TRIP_MIN_SECONDS) {
            val e = errands.getOrPut(localDate(first.ts)) { ErrandAcc() }
            e.drives += 1
            e.miles += miles
            e.kwh += kwh
            return
        }
        val bracketed = cur[0].bracket && (drive[0].s.ts - cur[0].s.ts) > 300
        val origin = cur[0].s
        trips += DerivedTrip(
            startTs = first.ts,
            endTs = last.ts,
            miles = miles,
            kwhUsed = kwh,
            whPerMi = if (miles > 0) kwh * 1000 / miles else null,
            // For bracketed trips the duration is an upper bound, so this is a lower bound.
            avgMph = if (duration > 0) miles / (duration / 3600.0) else null,
            bracketed = bracketed,
            startLat = origin.lat,
            startLon = origin.lon,
            endLat = last.lat,
            endLon = last.lon,
            startLabel = endpointLabel(cur, start = true),
            endLabel = endpointLabel(cur, start = false),
            viaLabel = viaLabel(cur, origin),
            path = cur.mapNotNull { p -> if (p.s.lat != null && p.s.lon != null) p.s.lat to p.s.lon else null }
        )
    }

    private fun tripMiles(cur: List<Pt>): Double {
        // Odometer is exact but only reports whole kilometers; at or below one
        // quantum the GPS path is the honest measure when it shows motion.
        val odo = cur.mapNotNull { it.s.odometerM }
        val odoM = if (odo.size >= 2 && odo.last() >= odo.first()) odo.last() - odo.first() else 0.0
        val gpsMi = gpsPathMi(cur.map { it.s })
        return when {
            odoM > ODO_QUANTUM_M -> odoM / METERS_PER_MILE
            gpsMi >= GPS_MOVE_MI -> gpsMi
            else -> odoM / METERS_PER_MILE
        }
    }

    private fun gpsPathMi(snaps: List<Snapshot>): Double =
        snaps.zipWithNext().sumOf { (a, b) ->
            if (b.ts - a.ts < 900 && hasFix(a) && hasFix(b)) haversineMi(a.lat!!, a.lon!!, b.lat!!, b.lon!!) else 0.0
        }

    /** True when the car genuinely relocated to snaps[i] (the new position persists), not a GPS blip. */
    private fun relocated(snaps: List<Snapshot>, i: Int, need: Int = 2, window: Int = 3): Boolean {
        if (i !in snaps.indices) return false
        val base = snaps[i]
        if (!hasFix(base)) return false
        val later = snaps.subList(i + 1, min(snaps.size, i + 1 + window))
        val hits = later.count { hasFix(it) && haversineMi(base.lat!!, base.lon!!, it.lat!!, it.lon!!) <= GPS_MOVE_MI }
        return hits >= min(need, later.size)
    }

    /** Walk inward from an endpoint to the nearest snapshot with a usable place. */
    private fun endpointLabel(cur: List<Pt>, start: Boolean): String? {
        val seq = if (start) cur else cur.asReversed()
        for (p in seq) {
            if (!start && p.bracket) continue // pre-drive snapshot is not the arrival point
            labeler(p.s.lat, p.s.lon, p.s.geoLabel)?.let { return it }
        }
        return null
    }

    /** For round trips, the farthest interior point from the origin (where the car actually went). */
    private fun viaLabel(cur: List<Pt>, origin: Snapshot): String? {
        val last = cur.last().s
        if (!hasFix(origin) || !hasFix(last) || cur.size < 3) return null
        if (haversineMi(origin.lat!!, origin.lon!!, last.lat!!, last.lon!!) >= 0.5) return null
        var best: Snapshot? = null
        var bestD = 0.3
        for (p in cur.subList(1, cur.size - 1)) {
            if (p.bracket || !hasFix(p.s)) continue
            val d = haversineMi(origin.lat!!, origin.lon!!, p.s.lat!!, p.s.lon!!)
            if (d > bestD) {
                best = p.s
                bestD = d
            }
        }
        return best?.let { labeler(it.lat, it.lon, null) }
    }

    // ---------------------------------------------------------------- charges

    private fun chargesFromRun(run: List<Snapshot>, charges: MutableList<DerivedCharge>) {
        // Monotonically rising SoC segments are sessions; charging_complete also ends one,
        // so the flat plugged-in tail at the target doesn't stretch the session.
        var seg = mutableListOf<Snapshot>()
        for (s in run) {
            val soc = s.soc ?: continue
            if (seg.isNotEmpty() && soc < seg.last().soc!! - 0.05) {
                closeCharge(seg, charges)
                seg = mutableListOf()
            }
            seg.add(s)
            if (s.chargerState == "charging_complete") {
                closeCharge(seg, charges)
                seg = mutableListOf()
            }
        }
        if (seg.isNotEmpty()) closeCharge(seg, charges)
    }

    private fun closeCharge(seg: List<Snapshot>, charges: MutableList<DerivedCharge>) {
        val a = seg.first()
        val b = seg.last()
        val gain = b.soc!! - a.soc!!
        if (gain < CHARGE_MIN_SOC_DELTA) return
        val hours = (b.ts - a.ts) / 3600.0
        val kwhAdded = gain / 100.0 * usableKwh
        // No live power field in the API: average power classifies DC vs AC.
        val avgKw = if (hours > 0) kwhAdded / hours else 0.0
        val r0 = a.rangeMi
        val r1 = b.rangeMi
        charges += DerivedCharge(
            startTs = a.ts,
            endTs = b.ts,
            minutes = (b.ts - a.ts) / 60.0,
            socStart = a.soc,
            socEnd = b.soc,
            kwhAdded = kwhAdded,
            milesAdded = if (r0 != null && r1 != null) r1 - r0 else null,
            avgKw = if (avgKw > 0) avgKw else null,
            isDc = avgKw >= DC_FAST_MIN_KW,
            lat = a.lat,
            lon = a.lon,
            label = endpointLabel(seg.map { Pt(it) }, start = true)
        )
    }

    // ---------------------------------------------------------------- overnight drain

    /**
     * Clean 10 PM to 7 AM windows: polls nearest 10:00 PM and the next 7:00 AM. Trips and
     * charges inside the window are backed out so only idle drain remains. Oldest first.
     */
    private fun nights(snaps: List<Snapshot>, trips: List<DerivedTrip>, charges: List<DerivedCharge>): List<Night> {
        val byHour = HashMap<Pair<LocalDate, Int>, Snapshot>()
        for (s in snaps) {
            if (s.soc == null) continue
            val lt = Instant.ofEpochSecond(s.ts).atZone(zone)
            if (lt.hour != 22 && lt.hour != 7) continue
            val key = lt.toLocalDate() to lt.hour
            val hourStart = lt.withMinute(0).withSecond(0).withNano(0).toEpochSecond()
            val existing = byHour[key]
            if (existing == null || kotlin.math.abs(s.ts - hourStart) < kotlin.math.abs(existing.ts - hourStart)) {
                byHour[key] = s
            }
        }
        val out = mutableListOf<Night>()
        for (day in byHour.keys.map { it.first }.distinct().sorted()) {
            val s1 = byHour[day to 22] ?: continue
            val s2 = byHour[day.plusDays(1) to 7] ?: continue
            if (s2.ts <= s1.ts) continue
            val inWindow = trips.filter { s1.ts <= it.startTs && it.endTs <= s2.ts }
            val tripKwh = inWindow.sumOf { it.kwhUsed }
            val tripMi = inWindow.sumOf { it.miles }
            val chargeKwh = charges.filter { it.startTs in s1.ts..s2.ts }.sumOf { it.kwhAdded }
            val kwh = max(0.0, (s1.soc!! - s2.soc!!) / 100.0 * usableKwh - tripKwh + chargeKwh)
            val hours = (s2.ts - s1.ts) / 3600.0
            val ra = s1.rangeMi
            val rb = s2.rangeMi
            out += Night(
                day = day,
                sleepTs = s1.ts,
                wakeTs = s2.ts,
                hours = hours,
                socStart = s1.soc,
                socEnd = s2.soc,
                kwhLost = kwh,
                watts = if (hours > 0) kwh * 1000 / hours else 0.0,
                milesLost = when {
                    ra == null || rb == null -> null
                    // A charge inside the window hides the range drop: convert kWh at the car's range per kWh.
                    chargeKwh > 0 && s1.soc > 0 -> kwh * ra / (s1.soc / 100.0 * usableKwh)
                    else -> max(0.0, (ra - rb) - tripMi)
                },
                wakeCount = wakeCount(snaps, s1.ts, s2.ts),
                isAbnormal = false
            )
        }
        // Abnormal: more than 1.6x the median once there are a few nights to compare.
        val median = out.map { it.kwhLost }.sorted().let { if (it.isEmpty()) 0.0 else it[it.size / 2] }
        return if (out.size >= 3 && median > 0) out.map { it.copy(isAbnormal = it.kwhLost > 1.6 * median) } else out
    }

    /** Wake-up episodes: runs of consecutive SoC drops (>= 0.1% per poll) totaling >= 0.15%. */
    private fun wakeCount(snaps: List<Snapshot>, t1: Long, t2: Long): Int {
        val pts = snaps.filter { it.ts in t1..t2 && it.soc != null }
        var wakes = 0
        var drop = 0.0
        var inEpisode = false
        for ((a, b) in pts.zipWithNext()) {
            val gapTooLong = b.ts - a.ts > 2700
            val falling = !gapTooLong && a.soc!! - b.soc!! >= 0.1
            if (falling) {
                inEpisode = true
                drop += a.soc!! - b.soc!!
            } else {
                if (inEpisode && drop >= 0.15) wakes++
                inEpisode = false
                drop = 0.0
            }
        }
        if (inEpisode && drop >= 0.15) wakes++
        return wakes
    }

    // ---------------------------------------------------------------- live charge

    /** Live stats while charger_state is charging_active; power from the SoC slope over ~15 min. */
    fun liveCharge(snaps: List<Snapshot>, now: Long): LiveCharge? {
        if (snaps.isEmpty() || snaps.last().chargerState != "charging_active") return null
        var startIdx = snaps.size - 1
        while (startIdx > 0 && snaps[startIdx - 1].chargerState == "charging_active") startIdx--
        val win = snaps.subList(startIdx, snaps.size).filter { it.soc != null }
        if (win.isEmpty()) return null
        val start = win.first()
        var recent = win.filter { it.ts >= max(now - 15 * 60, start.ts) }
        if (recent.size < 2) recent = win.takeLast(2)
        var kw: Double? = null
        if (recent.size >= 2) {
            val a = recent.first()
            val b = recent.last()
            val hours = (b.ts - a.ts) / 3600.0
            val dSoc = b.soc!! - a.soc!!
            if (hours >= 3.0 / 60.0 && dSoc > 0) kw = dSoc / 100.0 * usableKwh / hours
        }
        return LiveCharge(start.ts, start.soc!!, kw)
    }

    private fun localDate(ts: Long): LocalDate = Instant.ofEpochSecond(ts).atZone(zone).toLocalDate()

    companion object {
        val DRIVE_GEARS = setOf("D", "DRIVE", "R", "REVERSE")
        val PARKED_GEARS = setOf("P", "PARK", "N", "NEUTRAL", "")
        const val TRIP_END_GAP = 5 * 60L // stillness that closes a trip
        const val TRIP_MIN_SECONDS = 3 * 60L // shorter drives roll into errands
        const val CHARGE_MIN_SOC_DELTA = 0.3
        const val ODO_JUMP_MIN_MI = 0.1
        const val GPS_MOVE_MI = 0.02
        const val ODO_QUANTUM_M = 1000.0 // the odometer reports whole kilometers
        const val DC_FAST_MIN_KW = 20.0
        const val METERS_PER_MILE = 1609.344

        fun moving(s: Snapshot, prev: Snapshot?): Boolean {
            if ((s.gear ?: "").uppercase() in DRIVE_GEARS) return true
            if (prev != null && hasFix(s) && hasFix(prev)) {
                val dt = s.ts - prev.ts
                if (dt in 1..899 && haversineMi(prev.lat!!, prev.lon!!, s.lat!!, s.lon!!) > GPS_MOVE_MI) return true
            }
            return false
        }

        fun odoJumpMi(s: Snapshot, prev: Snapshot?): Double {
            val a = prev?.odometerM ?: return 0.0
            val b = s.odometerM ?: return 0.0
            return if (b < a) 0.0 else (b - a) / METERS_PER_MILE
        }

        fun usableGeoLabel(g: String?): String? = g?.takeUnless { it.isBlank() || it.equals("UNKNOWN", ignoreCase = true) }

        private fun hasFix(s: Snapshot) = s.lat != null && s.lon != null

        fun haversineMi(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
            val p1 = Math.toRadians(lat1)
            val p2 = Math.toRadians(lat2)
            val dp = p2 - p1
            val dl = Math.toRadians(lon2 - lon1)
            val h = sin(dp / 2) * sin(dp / 2) + cos(p1) * cos(p2) * sin(dl / 2) * sin(dl / 2)
            return 2 * 3958.8 * asin(sqrt(h))
        }
    }
}

package com.example.wattson.data

import android.content.Context
import android.content.SharedPreferences
import android.widget.Toast
import com.example.wattson.data.analysis.Analyzer
import com.example.wattson.data.analysis.Snapshot
import com.example.wattson.data.db.WattsonDb
import com.example.wattson.data.enrich.GasPriceService
import com.example.wattson.data.enrich.Geocoder
import com.example.wattson.data.enrich.WeatherService
import com.example.wattson.data.poll.PollPolicy
import com.example.wattson.data.poll.PollScheduler
import com.example.wattson.data.rivian.RivianApiClient
import com.example.wattson.data.rivian.RivianApiException
import com.example.wattson.data.rivian.RivianSession
import com.example.wattson.data.secure.TokenStore
import com.example.wattson.model.AppSettings
import com.example.wattson.model.BatteryTrendPoint
import com.example.wattson.model.ChargerType
import com.example.wattson.model.ChargingSession
import com.example.wattson.model.DayErrand
import com.example.wattson.model.FuelSavingRow
import com.example.wattson.model.LatLon
import com.example.wattson.model.OvernightDrain
import com.example.wattson.model.PackHealthPoint
import com.example.wattson.model.PollingMode
import com.example.wattson.model.Trip
import com.example.wattson.model.VehicleStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.cos
import kotlin.math.hypot

/**
 * Single source of app state.
 *
 * Linked account: every poll is stored in the on-device database and trips, charges,
 * overnight drain and savings are re-derived from those snapshots (the upstream Wattson
 * pipeline), with place names, weather and gas prices filled in and cached.
 * Demo mode (no account): the same pipeline runs over simulated snapshots, in memory.
 */
class WattsonRepository private constructor(private val context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("wattson", Context.MODE_PRIVATE)
    private val db = WattsonDb(context)
    private val tokenStore = TokenStore(prefs)
    private val zone: ZoneId = ZoneId.systemDefault()
    private val geocoder = Geocoder(db)
    private val weather = WeatherService(db, zone)
    private val gas = GasPriceService(db)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val rebuildMutex = Mutex()
    private val pollMutex = Mutex()

    val apiClient = RivianApiClient()

    private val _isOnboardingCompleted = MutableStateFlow(prefs.getBoolean(KEY_ONBOARDED, false))
    val isOnboardingCompleted: StateFlow<Boolean> = _isOnboardingCompleted.asStateFlow()

    private val _session = MutableStateFlow(loadSession())
    val session: StateFlow<RivianSession?> = _session.asStateFlow()

    private val _isSyncing = MutableStateFlow(false)
    val isSyncing: StateFlow<Boolean> = _isSyncing.asStateFlow()

    private val _needsReauth = MutableStateFlow(prefs.getBoolean(KEY_NEEDS_REAUTH, false))
    val needsReauth: StateFlow<Boolean> = _needsReauth.asStateFlow()

    private val _lastPollTs = MutableStateFlow(prefs.getLong(KEY_LAST_POLL, 0L).takeIf { it > 0 })
    val lastPollTs: StateFlow<Long?> = _lastPollTs.asStateFlow()

    private val _settings = MutableStateFlow(loadSettings())
    val settings: StateFlow<AppSettings> = _settings.asStateFlow()

    private val _vehicle = MutableStateFlow(unsyncedVehicle())
    val vehicle: StateFlow<VehicleStatus> = _vehicle.asStateFlow()

    private val _trips = MutableStateFlow<List<Trip>>(emptyList())
    val trips: StateFlow<List<Trip>> = _trips.asStateFlow()

    private val _errands = MutableStateFlow<List<DayErrand>>(emptyList())
    val errands: StateFlow<List<DayErrand>> = _errands.asStateFlow()

    private val _charges = MutableStateFlow<List<ChargingSession>>(emptyList())
    val charges: StateFlow<List<ChargingSession>> = _charges.asStateFlow()

    private val _savings = MutableStateFlow<List<FuelSavingRow>>(emptyList())
    val savings: StateFlow<List<FuelSavingRow>> = _savings.asStateFlow()

    private val _drains = MutableStateFlow<List<OvernightDrain>>(emptyList())
    val drains: StateFlow<List<OvernightDrain>> = _drains.asStateFlow()

    private val _packHealth = MutableStateFlow<List<PackHealthPoint>>(emptyList())
    val packHealth: StateFlow<List<PackHealthPoint>> = _packHealth.asStateFlow()

    private val _batteryTrend = MutableStateFlow<List<BatteryTrendPoint>>(emptyList())
    val batteryTrend: StateFlow<List<BatteryTrendPoint>> = _batteryTrend.asStateFlow()

    @Volatile var lastError: String? = null
        private set

    // Demo-only state, in memory.
    @Volatile private var demoSnaps: List<Snapshot> = emptyList()
    private val demoCostEdits = HashMap<Long, Float?>()
    private val demoTrips = mutableListOf<Trip>()
    private val demoCharges = mutableListOf<ChargingSession>()
    private var liveChargeJob: Job? = null

    val isLinked: Boolean get() = _session.value != null

    init {
        if (isLinked) refresh() else resetToDemoData()
    }

    // ---------------------------------------------------------------- account

    fun completeOnboardingAsDemo() {
        resetToDemoData()
        setOnboarded(true)
    }

    fun saveRivianLogin(
        email: String,
        userSessionToken: String,
        accessToken: String?,
        refreshToken: String?,
        vehicleId: String?,
        vehicleName: String?,
        vehicleVin: String?
    ) {
        stopLiveChargeSimulation()
        val session = RivianSession(email, userSessionToken, accessToken, refreshToken, vehicleId, vehicleName, vehicleVin)
        tokenStore.save(session)
        _session.value = session
        setNeedsReauth(false)
        val model = apiClient.lastVehicles.firstOrNull { it.id == vehicleId }?.model ?: _settings.value.vehicleModel
        persistSettings(_settings.value.copy(vehicleName = vehicleName ?: "My Rivian", vehicleModel = model))
        setOnboarded(true)
        PollScheduler.apply(context)
        scope.launch { if (!pollNow()) rebuild() }
    }

    /** Unlinks the account (the encrypted session is deleted) and returns to onboarding. Stored history is kept. */
    fun logout() {
        stopLiveChargeSimulation()
        tokenStore.clear()
        _session.value = null
        setNeedsReauth(false)
        setOnboarded(false)
        PollScheduler.apply(context)
        resetToDemoData()
    }

    // ---------------------------------------------------------------- polling

    /** Polls Rivian once, stores the snapshot and re-derives. Returns false on failure. */
    suspend fun pollNow(): Boolean = pollMutex.withLock {
        val session = _session.value ?: return false
        val vehicleId = session.vehicleId ?: session.vehicleVin
        if (vehicleId.isNullOrBlank()) return false
        _isSyncing.value = true
        try {
            val snap = apiClient.getVehicleState(session.userSessionToken, vehicleId)
            withContext(Dispatchers.IO) {
                db.insertSnapshot(snap)
                val s = _settings.value
                gas.refreshToday(LocalDate.now(zone), s.gasState, s.gasMetro, s.gasPrice)
            }
            prefs.edit().putLong(KEY_LAST_POLL, snap.ts).apply()
            _lastPollTs.value = snap.ts
            setNeedsReauth(false)
            rebuild()
            true
        } catch (e: RivianApiException) {
            if (e.isUnauthenticated) setNeedsReauth(true)
            lastError = e.message
            false
        } finally {
            _isSyncing.value = false
        }
    }

    /** Seconds until the next poll, from the two latest snapshots (upstream's adaptive tiers). */
    suspend fun nextPollIntervalSeconds(): Long = withContext(Dispatchers.IO) {
        val recent = db.lastSnapshots(2)
        PollPolicy.intervalSeconds(recent.lastOrNull(), recent.getOrNull(recent.size - 2))
    }

    /** Manual "sync now" from the UI. */
    fun refreshLiveTelemetry() {
        if (!isLinked) return
        scope.launch {
            if (!pollNow()) {
                val message = if (_needsReauth.value) "Rivian session expired. Unlink and sign in again." else "Sync failed: ${lastError ?: "unknown error"}"
                withContext(Dispatchers.Main) { Toast.makeText(context, message, Toast.LENGTH_LONG).show() }
            }
        }
    }

    // ---------------------------------------------------------------- settings

    fun updateSettings(newSettings: AppSettings) {
        val modeChanged = newSettings.pollingMode != _settings.value.pollingMode
        persistSettings(newSettings)
        if (modeChanged) PollScheduler.apply(context)
        refresh()
    }

    /** Sets home to the linked car's last reported GPS position. */
    fun setHomeToCurrentLocation(): Boolean {
        if (!isLinked) return false
        val last = db.lastSnapshots(50).lastOrNull { it.lat != null && it.lon != null }
        val lat = last?.lat ?: return false
        val lon = last?.lon ?: return false
        updateSettings(_settings.value.copy(homeLat = lat, homeLon = lon))
        return true
    }

    // ---------------------------------------------------------------- demo

    fun resetToDemoData() {
        stopLiveChargeSimulation()
        demoSnaps = DemoDataProvider.snapshots(zone)
        demoCostEdits.clear()
        demoTrips.clear()
        demoCharges.clear()
        refresh()
    }

    fun updateChargeCost(chargeId: Long, costPerKwh: Float?) {
        if (isLinked) {
            scope.launch(Dispatchers.IO) {
                db.setChargeCost(chargeId, costPerKwh)
                rebuild()
            }
        } else {
            demoCostEdits[chargeId] = costPerKwh
            refresh()
        }
    }

    /** Demo only: a linked account's drives come from the poller. */
    fun addTrip(start: String, end: String, miles: Float, avgMph: Float) {
        if (isLinked || miles <= 0f || avgMph <= 0f) return
        val whPerMi = 290f + (avgMph - 35f).coerceAtLeast(0f) * 3f
        val kwh = miles * whPerMi / 1000f
        val endTs = System.currentTimeMillis() / 1000
        demoTrips += Trip(
            id = endTs,
            startTs = endTs - (miles / avgMph * 3600).toLong(),
            endTs = endTs,
            miles = miles,
            kwhUsed = Math.round(kwh * 100) / 100f,
            whPerMi = whPerMi,
            avgMph = avgMph,
            startLabel = start.trim().ifEmpty { "Unknown" },
            endLabel = end.trim()
        )
        _vehicle.update { v ->
            val soc = (v.soc - kwh / v.usableKwh * 100f).coerceAtLeast(0f)
            v.copy(soc = soc, rangeMi = soc / 100f * v.ratedRangeMi, odometerMi = v.odometerMi + miles)
        }
        refresh(keepVehicle = true)
    }

    /** Demo only: a linked account's charges come from the poller. */
    fun addChargingSession(socStart: Float, socEnd: Float, kwhAdded: Float, type: ChargerType, label: String, costPerKwh: Float?) {
        if (isLinked || kwhAdded <= 0f) return
        val avgKw = if (type == ChargerType.DC_FAST) 110f else 11f
        val minutes = kwhAdded / avgKw * 60f
        val endTs = System.currentTimeMillis() / 1000
        val v = _vehicle.value
        demoCharges += ChargingSession(
            id = endTs,
            startTs = endTs - (minutes * 60).toLong(),
            endTs = endTs,
            minutes = minutes,
            socStart = socStart.coerceIn(0f, 100f),
            socEnd = socEnd.coerceIn(0f, 100f),
            kwhAdded = kwhAdded,
            milesAdded = kwhAdded / v.usableKwh * v.ratedRangeMi,
            avgKw = avgKw,
            chargeType = type,
            label = label.trim().ifEmpty { if (type == ChargerType.HOME_AC) "Home" else "Public charger" },
            userCostPerKwh = if (type == ChargerType.HOME_AC) null else costPerKwh
        )
        refresh(keepVehicle = true)
    }

    /** Demo only: simulated 11 kW home charging, accelerated so a session completes in about a minute. */
    fun toggleLiveCharge(start: Boolean) {
        if (isLinked) return
        if (start) {
            if (_vehicle.value.isCharging) return
            _vehicle.update {
                it.copy(
                    isCharging = true,
                    powerKw = 11f,
                    chargingSessionStartTs = System.currentTimeMillis() / 1000,
                    chargingSessionStartSoc = it.soc,
                    location = "Home"
                )
            }
            liveChargeJob = scope.launch {
                while (isActive) {
                    delay(1000)
                    val v = _vehicle.value
                    val soc = (v.soc + 1f).coerceAtMost(v.targetSoc)
                    _vehicle.value = v.copy(soc = soc, rangeMi = soc / 100f * v.ratedRangeMi)
                    if (soc >= v.targetSoc) {
                        finishLiveCharge()
                        break
                    }
                }
            }
        } else {
            liveChargeJob?.cancel()
            liveChargeJob = null
            finishLiveCharge()
        }
    }

    private fun finishLiveCharge() {
        val v = _vehicle.value
        if (!v.isCharging) return
        val startSoc = v.chargingSessionStartSoc ?: v.soc
        val kwh = (v.soc - startSoc) / 100f * v.usableKwh
        if (kwh >= 0.1f) {
            val endTs = System.currentTimeMillis() / 1000
            demoCharges += ChargingSession(
                id = endTs,
                startTs = v.chargingSessionStartTs ?: endTs,
                endTs = endTs,
                minutes = kwh / 11f * 60f, // real-world duration at 11 kW
                socStart = startSoc,
                socEnd = v.soc,
                kwhAdded = Math.round(kwh * 10) / 10f,
                milesAdded = kwh / v.usableKwh * v.ratedRangeMi,
                avgKw = 11f,
                chargeType = ChargerType.HOME_AC,
                label = "Home"
            )
        }
        _vehicle.value = v.copy(isCharging = false, powerKw = 0f, chargingSessionStartTs = null, chargingSessionStartSoc = null)
        refresh(keepVehicle = true)
    }

    private fun stopLiveChargeSimulation() {
        liveChargeJob?.cancel()
        liveChargeJob = null
        _vehicle.update { it.copy(isCharging = false, powerKw = 0f, chargingSessionStartTs = null, chargingSessionStartSoc = null) }
    }

    // ---------------------------------------------------------------- derivation

    private fun refresh(keepVehicle: Boolean = false) {
        scope.launch { rebuild(keepVehicle) }
    }

    /** Publishes from caches first (instant), then again with network lookups for new places and weather. */
    private suspend fun rebuild(keepVehicle: Boolean = false) = rebuildMutex.withLock {
        withContext(Dispatchers.IO) {
            publish(allowNetwork = false, keepVehicle = keepVehicle)
            publish(allowNetwork = true, keepVehicle = keepVehicle)
        }
    }

    private fun publish(allowNetwork: Boolean, keepVehicle: Boolean) {
        val s = _settings.value
        val linked = isLinked
        val now = System.currentTimeMillis() / 1000
        val snaps = if (linked) db.snapshots(sinceTs = now - HISTORY_DAYS * 86_400) else demoSnaps
        val labeler: (Double?, Double?, String?) -> String? =
            if (linked) { lat, lon, geo -> geocoder.label(lat, lon, geo, allowNetwork) } else DemoDataProvider.labeler
        val usable = s.usableKwh.toDouble()
        val analyzer = Analyzer(usable, zone, labeler)
        val derived = analyzer.derive(snaps)
        val ratedRange = impliedFullRange(snaps) ?: DemoDataProvider.RATED_RANGE_MI

        val trips = derived.trips.map { t ->
            Trip(
                id = t.startTs,
                startTs = t.startTs,
                endTs = t.endTs,
                miles = t.miles.toFloat(),
                kwhUsed = t.kwhUsed.toFloat(),
                whPerMi = (t.whPerMi ?: 0.0).toFloat(),
                avgMph = (t.avgMph ?: 0.0).toFloat(),
                startLabel = t.startLabel ?: "Unknown location",
                endLabel = t.endLabel ?: "Unknown location",
                tempF = weather.tempF(t.startTs, t.startLat, t.startLon, allowNetwork)?.toFloat(),
                bracketed = t.bracketed,
                viaLabel = t.viaLabel,
                path = t.path.map { (lat, lon) -> LatLon(lat, lon) }
            )
        }.let { if (linked) it else it + demoTrips }.sortedByDescending { it.startTs }

        val costs: Map<Long, Float?> = if (linked) {
            db.chargeCosts()
        } else {
            DemoDataProvider.dcCosts(derived.charges) + demoCostEdits
        }
        val charges = derived.charges.map { c ->
            ChargingSession(
                id = c.startTs,
                startTs = c.startTs,
                endTs = c.endTs,
                minutes = c.minutes.toFloat(),
                socStart = c.socStart.toFloat(),
                socEnd = c.socEnd.toFloat(),
                kwhAdded = c.kwhAdded.toFloat(),
                milesAdded = (c.milesAdded ?: (c.kwhAdded / usable * ratedRange)).toFloat(),
                avgKw = (c.avgKw ?: 0.0).toFloat(),
                chargeType = when {
                    c.isDc -> ChargerType.DC_FAST
                    isHome(c.label, c.lat, c.lon, s) -> ChargerType.HOME_AC
                    else -> ChargerType.PUBLIC_AC
                },
                label = c.label ?: "Unknown location",
                userCostPerKwh = costs[c.startTs]
            )
        }.let { if (linked) it else it + demoCharges.map { m -> if (m.id in demoCostEdits) m.copy(userCostPerKwh = demoCostEdits[m.id]) else m } }
            .sortedByDescending { it.startTs }

        val dayFmt = DateTimeFormatter.ofPattern("MMM d", Locale.US)
        val weekdayFmt = DateTimeFormatter.ofPattern("EEE", Locale.US)
        val drains = derived.nights.takeLast(14).reversed().map { n ->
            OvernightDrain(
                id = n.sleepTs,
                day = n.day.format(dayFmt),
                label = n.day.format(weekdayFmt),
                sleepTs = n.sleepTs,
                wakeTs = n.wakeTs,
                hours = n.hours.toFloat(),
                socStart = n.socStart.toFloat(),
                socEnd = n.socEnd.toFloat(),
                kwhLost = n.kwhLost.toFloat(),
                wattsAvg = n.watts.toFloat(),
                milesLost = n.milesLost?.toFloat(),
                wakeCount = n.wakeCount,
                isAbnormal = n.isAbnormal
            )
        }

        val errands = derived.errands.reversed().map { e ->
            DayErrand(e.day.format(dayFmt), "Short drives", e.drives, e.miles.toFloat(), e.kwhUsed.toFloat())
        }

        // Savings: lifetime efficiency converts kWh to miles; each charge day's AAA price.
        val totalMi = trips.sumOf { it.miles.toDouble() }
        val totalKwh = trips.sumOf { it.kwhUsed.toDouble() }
        val efficiency = if (totalKwh > 0) (totalMi / totalKwh).toFloat() else 3.0f
        val gasHistory = if (linked) db.gasPrices() else emptyMap()
        val savings = FuelSavings.calculate(charges, s, efficiency) { ts ->
            if (linked) GasPriceService.priceFor(localDate(ts), gasHistory, s.gasPrice) else s.gasPrice
        }

        _trips.value = trips
        _charges.value = charges
        _drains.value = drains
        _errands.value = errands
        _savings.value = savings
        _batteryTrend.value = batteryTrend(snaps)
        _packHealth.value = packHealth(snaps)
        if (!(keepVehicle || liveChargeJob?.isActive == true)) {
            _vehicle.value = vehicleFrom(snaps, analyzer, labeler, s, ratedRange, now)
        }
    }

    private fun vehicleFrom(
        snaps: List<Snapshot>, analyzer: Analyzer, labeler: (Double?, Double?, String?) -> String?,
        s: AppSettings, ratedRange: Double, now: Long
    ): VehicleStatus {
        val last = snaps.lastOrNull() ?: return unsyncedVehicle()
        val live = analyzer.liveCharge(snaps, if (isLinked) now else last.ts)
        val soc = last.soc ?: 0.0
        val odometerM = snaps.lastOrNull { it.odometerM != null }?.odometerM ?: 0.0
        return VehicleStatus(
            name = s.vehicleName,
            model = s.vehicleModel,
            soc = soc.toFloat(),
            targetSoc = (last.batteryLimit ?: 90.0).toFloat(),
            capacityKwh = (last.capacityKwh ?: 91.4).toFloat(),
            usableKwh = s.usableKwh,
            rangeMi = (last.rangeMi ?: (soc / 100 * ratedRange)).toFloat(),
            ratedRangeMi = ratedRange.toFloat(),
            odometerMi = odometerM / Analyzer.METERS_PER_MILE,
            location = labeler(last.lat, last.lon, last.geoLabel) ?: "last known location",
            isCharging = live != null,
            powerKw = live?.kw?.toFloat() ?: 0f,
            chargingSessionStartTs = live?.sessionStartTs,
            chargingSessionStartSoc = live?.socStart?.toFloat(),
            lastSyncTs = last.ts * 1000
        )
    }

    /** End-of-day SoC for the last 14 days. */
    private fun batteryTrend(snaps: List<Snapshot>): List<BatteryTrendPoint> {
        val fmt = DateTimeFormatter.ofPattern("M/d", Locale.US)
        return snaps.filter { it.soc != null }
            .groupBy { localDate(it.ts) }
            .toSortedMap()
            .map { (day, list) -> BatteryTrendPoint(day.format(fmt), list.last().soc!!.toFloat()) }
            .takeLast(14)
    }

    /** Daily BMS capacity and implied full-charge range, for the pack-health card. */
    private fun packHealth(snaps: List<Snapshot>): List<PackHealthPoint> {
        val fmt = DateTimeFormatter.ofPattern("M/d", Locale.US)
        return snaps.groupBy { localDate(it.ts) }.toSortedMap().mapNotNull { (day, list) ->
            val caps = list.mapNotNull { it.capacityKwh }
            if (caps.isEmpty()) return@mapNotNull null
            PackHealthPoint(day.format(fmt), caps.average().toFloat(), (impliedFullRange(list) ?: 0.0).toFloat())
        }.takeLast(30)
    }

    /** The car's own range estimate scaled to 100%: median over readings above 20% SoC. */
    private fun impliedFullRange(snaps: List<Snapshot>): Double? {
        val values = snaps.mapNotNull { s ->
            val soc = s.soc ?: return@mapNotNull null
            val range = s.rangeMi ?: return@mapNotNull null
            if (soc >= 20) range / soc * 100 else null
        }.sorted()
        return values.getOrNull(values.size / 2)
    }

    private fun localDate(ts: Long): LocalDate = Instant.ofEpochSecond(ts).atZone(zone).toLocalDate()

    private fun unsyncedVehicle() = VehicleStatus(
        name = _settings.value.vehicleName,
        model = _settings.value.vehicleModel,
        soc = 0f,
        rangeMi = 0f,
        odometerMi = 0.0,
        location = if (isLinked) "waiting for first sync" else "Home"
    )

    // ---------------------------------------------------------------- persistence

    private fun setOnboarded(value: Boolean) {
        prefs.edit().putBoolean(KEY_ONBOARDED, value).apply()
        _isOnboardingCompleted.value = value
    }

    private fun setNeedsReauth(value: Boolean) {
        if (_needsReauth.value == value) return
        prefs.edit().putBoolean(KEY_NEEDS_REAUTH, value).apply()
        _needsReauth.value = value
    }

    private fun persistSettings(s: AppSettings) {
        prefs.edit()
            .putFloat(KEY_HOME_RATE, s.homeKwhRate)
            .putFloat(KEY_GAS_MPG, s.gasMpg)
            .putFloat(KEY_GAS_PRICE, s.gasPrice)
            .putString(KEY_SETTINGS_VEHICLE_NAME, s.vehicleName)
            .putString(KEY_SETTINGS_VEHICLE_MODEL, s.vehicleModel)
            .putFloat(KEY_USABLE_KWH, s.usableKwh)
            .putString(KEY_HOME_LAT, s.homeLat?.toString())
            .putString(KEY_HOME_LON, s.homeLon?.toString())
            .putString(KEY_GAS_STATE, s.gasState)
            .putString(KEY_GAS_METRO, s.gasMetro)
            .putString(KEY_POLLING_MODE, s.pollingMode.name)
            .apply()
        _settings.value = s
    }

    private fun loadSettings(): AppSettings {
        val d = AppSettings()
        return d.copy(
            homeKwhRate = prefs.getFloat(KEY_HOME_RATE, d.homeKwhRate),
            gasMpg = prefs.getFloat(KEY_GAS_MPG, d.gasMpg),
            gasPrice = prefs.getFloat(KEY_GAS_PRICE, d.gasPrice),
            vehicleName = prefs.getString(KEY_SETTINGS_VEHICLE_NAME, null) ?: d.vehicleName,
            vehicleModel = prefs.getString(KEY_SETTINGS_VEHICLE_MODEL, null) ?: d.vehicleModel,
            usableKwh = prefs.getFloat(KEY_USABLE_KWH, d.usableKwh),
            homeLat = prefs.getString(KEY_HOME_LAT, null)?.toDoubleOrNull(),
            homeLon = prefs.getString(KEY_HOME_LON, null)?.toDoubleOrNull(),
            gasState = prefs.getString(KEY_GAS_STATE, null) ?: d.gasState,
            gasMetro = prefs.getString(KEY_GAS_METRO, null) ?: d.gasMetro,
            pollingMode = prefs.getString(KEY_POLLING_MODE, null)
                ?.let { name -> PollingMode.entries.firstOrNull { it.name == name } } ?: d.pollingMode
        )
    }

    /** Encrypted session; migrates a plaintext session left by version 1.0.3 or earlier. */
    private fun loadSession(): RivianSession? {
        tokenStore.load()?.let { return it }
        val email = prefs.getString(LEGACY_EMAIL, null) ?: return null
        val token = prefs.getString(LEGACY_USER_SESSION, null) ?: return null
        val session = RivianSession(
            email = email,
            userSessionToken = token,
            accessToken = prefs.getString(LEGACY_ACCESS, null),
            refreshToken = prefs.getString(LEGACY_REFRESH, null),
            vehicleId = prefs.getString(LEGACY_VEHICLE_ID, null),
            vehicleName = prefs.getString(LEGACY_VEHICLE_NAME, null),
            vehicleVin = prefs.getString(LEGACY_VIN, null)
        )
        tokenStore.save(session)
        prefs.edit()
            .remove(LEGACY_EMAIL).remove(LEGACY_USER_SESSION).remove(LEGACY_ACCESS).remove(LEGACY_REFRESH)
            .remove(LEGACY_VEHICLE_ID).remove(LEGACY_VEHICLE_NAME).remove(LEGACY_VIN)
            .apply()
        return session
    }

    companion object {
        private const val HISTORY_DAYS = 365L
        private const val HOME_RADIUS_M = 250.0

        private const val KEY_ONBOARDED = "onboarding_completed"
        private const val KEY_NEEDS_REAUTH = "needs_reauth"
        private const val KEY_LAST_POLL = "last_poll_ts"
        private const val KEY_HOME_RATE = "home_kwh_rate"
        private const val KEY_GAS_MPG = "gas_mpg"
        private const val KEY_GAS_PRICE = "gas_price"
        private const val KEY_SETTINGS_VEHICLE_NAME = "vehicle_name"
        private const val KEY_SETTINGS_VEHICLE_MODEL = "vehicle_model"
        private const val KEY_USABLE_KWH = "usable_kwh"
        private const val KEY_HOME_LAT = "home_lat"
        private const val KEY_HOME_LON = "home_lon"
        private const val KEY_GAS_STATE = "gas_state"
        private const val KEY_GAS_METRO = "gas_metro"
        private const val KEY_POLLING_MODE = "polling_mode"

        // Plaintext keys used by version 1.0.3; migrated into the encrypted store.
        private const val LEGACY_EMAIL = "rivian_email"
        private const val LEGACY_USER_SESSION = "rivian_user_session_token"
        private const val LEGACY_ACCESS = "rivian_access_token"
        private const val LEGACY_REFRESH = "rivian_refresh_token"
        private const val LEGACY_VEHICLE_ID = "rivian_vehicle_id"
        private const val LEGACY_VEHICLE_NAME = "rivian_vehicle_name"
        private const val LEGACY_VIN = "rivian_vehicle_vin"

        /** Home: the car's saved-location label says so, or within 250 m of the configured home (upstream). */
        fun isHome(label: String?, lat: Double?, lon: Double?, s: AppSettings): Boolean {
            if (label != null && label.trim().equals("home", ignoreCase = true)) return true
            val homeLat = s.homeLat ?: return false
            val homeLon = s.homeLon ?: return false
            if (lat == null || lon == null) return false
            val dx = (lon - homeLon) * 111_320.0 * cos(Math.toRadians(homeLat))
            val dy = (lat - homeLat) * 110_540.0
            return hypot(dx, dy) <= HOME_RADIUS_M
        }

        @Volatile
        private var instance: WattsonRepository? = null

        /** Process-wide instance shared by the UI, the polling service and the background worker. */
        fun getInstance(context: Context): WattsonRepository =
            instance ?: synchronized(this) {
                instance ?: WattsonRepository(context.applicationContext).also { instance = it }
            }
    }
}

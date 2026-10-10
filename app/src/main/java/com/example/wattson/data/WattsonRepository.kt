package com.example.wattson.data

import android.content.Context
import android.content.SharedPreferences
import android.widget.Toast
import com.example.wattson.data.rivian.RivianApiClient
import com.example.wattson.data.rivian.RivianApiException
import com.example.wattson.data.rivian.RivianSession
import com.example.wattson.model.AppSettings
import com.example.wattson.model.BatteryTrendPoint
import com.example.wattson.model.ChargerType
import com.example.wattson.model.ChargingSession
import com.example.wattson.model.FuelSavingRow
import com.example.wattson.model.OvernightDrain
import com.example.wattson.model.PackHealthPoint
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

/**
 * Single source of app state. Settings, the onboarding flag and the Rivian session
 * persist in private SharedPreferences; trip/charge/drain history is in memory.
 */
class WattsonRepository private constructor(private val context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("wattson", Context.MODE_PRIVATE)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var liveChargeJob: Job? = null

    val apiClient = RivianApiClient()

    private val _isOnboardingCompleted = MutableStateFlow(prefs.getBoolean(KEY_ONBOARDED, false))
    val isOnboardingCompleted: StateFlow<Boolean> = _isOnboardingCompleted.asStateFlow()

    private val _session = MutableStateFlow(loadSession())
    val session: StateFlow<RivianSession?> = _session.asStateFlow()

    private val _isSyncing = MutableStateFlow(false)
    val isSyncing: StateFlow<Boolean> = _isSyncing.asStateFlow()

    private val _settings = MutableStateFlow(loadSettings())
    val settings: StateFlow<AppSettings> = _settings.asStateFlow()

    private val _vehicle = MutableStateFlow(DemoDataProvider.generate().vehicle)
    val vehicle: StateFlow<VehicleStatus> = _vehicle.asStateFlow()

    private val _trips = MutableStateFlow<List<Trip>>(emptyList())
    val trips: StateFlow<List<Trip>> = _trips.asStateFlow()

    private val _charges = MutableStateFlow<List<ChargingSession>>(emptyList())
    val charges: StateFlow<List<ChargingSession>> = _charges.asStateFlow()

    private val _drains = MutableStateFlow<List<OvernightDrain>>(emptyList())
    val drains: StateFlow<List<OvernightDrain>> = _drains.asStateFlow()

    private val _packHealth = MutableStateFlow<List<PackHealthPoint>>(emptyList())
    val packHealth: StateFlow<List<PackHealthPoint>> = _packHealth.asStateFlow()

    private val _batteryTrend = MutableStateFlow<List<BatteryTrendPoint>>(emptyList())
    val batteryTrend: StateFlow<List<BatteryTrendPoint>> = _batteryTrend.asStateFlow()

    init {
        if (_session.value != null) {
            // Rivian exposes no trip/charge history; a linked account starts with an empty log.
            _vehicle.value = unsyncedVehicle()
            refreshLiveTelemetry()
        } else {
            resetToDemoData()
        }
    }

    // ---- Onboarding & account ----

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
        val session = RivianSession(email, userSessionToken, accessToken, refreshToken, vehicleId, vehicleName, vehicleVin)
        prefs.edit()
            .putString(KEY_EMAIL, email)
            .putString(KEY_USER_SESSION, userSessionToken)
            .putString(KEY_ACCESS, accessToken)
            .putString(KEY_REFRESH, refreshToken)
            .putString(KEY_VEHICLE_ID, vehicleId)
            .putString(KEY_VEHICLE_NAME, vehicleName)
            .putString(KEY_VIN, vehicleVin)
            .apply()
        _session.value = session

        val model = apiClient.lastVehicles.firstOrNull { it.id == vehicleId }?.model ?: _settings.value.vehicleModel
        updateSettings(_settings.value.copy(vehicleName = vehicleName ?: "My Rivian", vehicleModel = model))
        stopLiveChargeSimulation()
        _vehicle.value = unsyncedVehicle()
        _trips.value = emptyList()
        _charges.value = emptyList()
        _drains.value = emptyList()
        _batteryTrend.value = emptyList()
        _packHealth.value = emptyList()
        setOnboarded(true)
        refreshLiveTelemetry()
    }

    /** Unlinks the Rivian account and returns to onboarding. */
    fun logout() {
        stopLiveChargeSimulation()
        prefs.edit()
            .remove(KEY_EMAIL).remove(KEY_USER_SESSION).remove(KEY_ACCESS).remove(KEY_REFRESH)
            .remove(KEY_VEHICLE_ID).remove(KEY_VEHICLE_NAME).remove(KEY_VIN)
            .apply()
        _session.value = null
        setOnboarded(false)
    }

    fun refreshLiveTelemetry() {
        val session = _session.value ?: return
        if (_isSyncing.value) return
        val vehicleId = session.vehicleId ?: session.vehicleVin
        if (vehicleId.isNullOrBlank()) {
            toast("No vehicle selected for this Rivian account.")
            return
        }
        _isSyncing.value = true
        scope.launch {
            try {
                val t = apiClient.getVehicleState(session.userSessionToken, vehicleId)
                _vehicle.update { v ->
                    val soc = t.socPercent ?: v.soc
                    val charging = t.chargerState == "charging_active" || t.chargerState == "charging"
                    v.copy(
                        soc = soc,
                        targetSoc = t.batteryLimitPercent ?: v.targetSoc,
                        rangeMi = t.rangeMi ?: (soc / 100f * v.ratedRangeMi),
                        odometerMi = t.odometerMi ?: v.odometerMi,
                        location = "last known location",
                        isCharging = charging,
                        powerKw = 0f,
                        chargingSessionStartSoc = if (charging) v.chargingSessionStartSoc ?: soc else null,
                        lastSyncTs = System.currentTimeMillis()
                    )
                }
            } catch (e: RivianApiException) {
                toast(if (e.isUnauthenticated) "Rivian session expired. Unlink and sign in again." else "Sync failed: ${e.message}")
            } finally {
                _isSyncing.value = false
            }
        }
    }

    // ---- Settings ----

    fun updateSettings(newSettings: AppSettings) {
        prefs.edit()
            .putFloat(KEY_HOME_RATE, newSettings.homeKwhRate)
            .putFloat(KEY_GAS_MPG, newSettings.gasMpg)
            .putFloat(KEY_GAS_PRICE, newSettings.gasPrice)
            .putString(KEY_SETTINGS_VEHICLE_NAME, newSettings.vehicleName)
            .putString(KEY_SETTINGS_VEHICLE_MODEL, newSettings.vehicleModel)
            .apply()
        _settings.value = newSettings
        _vehicle.update { it.copy(name = newSettings.vehicleName, model = newSettings.vehicleModel) }
    }

    fun resetToDemoData() {
        stopLiveChargeSimulation()
        val demo = DemoDataProvider.generate()
        _trips.value = demo.trips
        _charges.value = demo.charges
        _drains.value = demo.drains
        _batteryTrend.value = demo.batteryTrend
        _packHealth.value = demo.packHealth
        if (_session.value == null) {
            _vehicle.value = demo.vehicle.copy(name = _settings.value.vehicleName, model = _settings.value.vehicleModel)
        }
    }

    fun calculateFuelSavings(charges: List<ChargingSession>, settings: AppSettings, miPerKwh: Float): List<FuelSavingRow> =
        FuelSavings.calculate(charges, settings, miPerKwh)

    // ---- Manual logging ----

    fun addTrip(start: String, end: String, miles: Float, avgMph: Float) {
        if (miles <= 0f || avgMph <= 0f) return
        val whPerMi = 290f + (avgMph - 35f).coerceAtLeast(0f) * 3f
        val kwh = miles * whPerMi / 1000f
        val endTs = System.currentTimeMillis() / 1000
        val trip = Trip(
            id = (_trips.value.maxOfOrNull { it.id } ?: 0L) + 1,
            startTs = endTs - (miles / avgMph * 3600).toLong(),
            endTs = endTs,
            miles = miles,
            kwhUsed = Math.round(kwh * 100) / 100f,
            whPerMi = whPerMi,
            avgMph = avgMph,
            startLabel = start.trim().ifEmpty { "Unknown" },
            endLabel = end.trim()
        )
        _trips.update { listOf(trip) + it }
        _vehicle.update { v ->
            val soc = (v.soc - kwh / v.usableKwh * 100f).coerceAtLeast(0f)
            v.copy(soc = soc, rangeMi = soc / 100f * v.ratedRangeMi, odometerMi = v.odometerMi + miles)
        }
    }

    fun addChargingSession(socStart: Float, socEnd: Float, kwhAdded: Float, type: ChargerType, label: String, costPerKwh: Float?) {
        if (kwhAdded <= 0f) return
        val avgKw = if (type == ChargerType.DC_FAST) 110f else 11f
        val minutes = kwhAdded / avgKw * 60f
        val endTs = System.currentTimeMillis() / 1000
        val v = _vehicle.value
        val session = ChargingSession(
            id = nextChargeId(),
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
        _charges.update { listOf(session) + it }
    }

    fun updateChargeCost(chargeId: Long, costPerKwh: Float?) {
        _charges.update { list -> list.map { if (it.id == chargeId) it.copy(userCostPerKwh = costPerKwh) else it } }
    }

    /** Simulated home charging at 11 kW, accelerated so a session completes in about a minute. */
    fun toggleLiveCharge(start: Boolean) {
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
        val startTs = v.chargingSessionStartTs ?: (System.currentTimeMillis() / 1000)
        val kwh = (v.soc - startSoc) / 100f * v.usableKwh
        if (kwh >= 0.1f) {
            val endTs = System.currentTimeMillis() / 1000
            val minutes = kwh / 11f * 60f // real-world duration at 11 kW
            _charges.update {
                listOf(
                    ChargingSession(
                        id = nextChargeId(),
                        startTs = startTs,
                        endTs = endTs,
                        minutes = minutes,
                        socStart = startSoc,
                        socEnd = v.soc,
                        kwhAdded = Math.round(kwh * 10) / 10f,
                        milesAdded = kwh / v.usableKwh * v.ratedRangeMi,
                        avgKw = 11f,
                        chargeType = ChargerType.HOME_AC,
                        label = "Home"
                    )
                ) + it
            }
        }
        _vehicle.value = v.copy(isCharging = false, powerKw = 0f, chargingSessionStartTs = null, chargingSessionStartSoc = null)
    }

    private fun stopLiveChargeSimulation() {
        liveChargeJob?.cancel()
        liveChargeJob = null
        _vehicle.update { it.copy(isCharging = false, powerKw = 0f, chargingSessionStartTs = null, chargingSessionStartSoc = null) }
    }

    // ---- Helpers ----

    private fun unsyncedVehicle() = VehicleStatus(
        name = _settings.value.vehicleName,
        model = _settings.value.vehicleModel,
        soc = 0f,
        rangeMi = 0f,
        odometerMi = 0.0,
        location = "not synced yet"
    )

    private fun nextChargeId(): Long = (_charges.value.maxOfOrNull { it.id } ?: 0L) + 1

    private fun setOnboarded(value: Boolean) {
        prefs.edit().putBoolean(KEY_ONBOARDED, value).apply()
        _isOnboardingCompleted.value = value
    }

    private fun toast(message: String) {
        Toast.makeText(context, message, Toast.LENGTH_LONG).show()
    }

    private fun loadSession(): RivianSession? {
        val email = prefs.getString(KEY_EMAIL, null) ?: return null
        val token = prefs.getString(KEY_USER_SESSION, null) ?: return null
        return RivianSession(
            email = email,
            userSessionToken = token,
            accessToken = prefs.getString(KEY_ACCESS, null),
            refreshToken = prefs.getString(KEY_REFRESH, null),
            vehicleId = prefs.getString(KEY_VEHICLE_ID, null),
            vehicleName = prefs.getString(KEY_VEHICLE_NAME, null),
            vehicleVin = prefs.getString(KEY_VIN, null)
        )
    }

    private fun loadSettings(): AppSettings {
        val defaults = AppSettings()
        return defaults.copy(
            homeKwhRate = prefs.getFloat(KEY_HOME_RATE, defaults.homeKwhRate),
            gasMpg = prefs.getFloat(KEY_GAS_MPG, defaults.gasMpg),
            gasPrice = prefs.getFloat(KEY_GAS_PRICE, defaults.gasPrice),
            vehicleName = prefs.getString(KEY_SETTINGS_VEHICLE_NAME, null) ?: defaults.vehicleName,
            vehicleModel = prefs.getString(KEY_SETTINGS_VEHICLE_MODEL, null) ?: defaults.vehicleModel
        )
    }

    companion object {
        private const val KEY_ONBOARDED = "onboarding_completed"
        private const val KEY_EMAIL = "rivian_email"
        private const val KEY_USER_SESSION = "rivian_user_session_token"
        private const val KEY_ACCESS = "rivian_access_token"
        private const val KEY_REFRESH = "rivian_refresh_token"
        private const val KEY_VEHICLE_ID = "rivian_vehicle_id"
        private const val KEY_VEHICLE_NAME = "rivian_vehicle_name"
        private const val KEY_VIN = "rivian_vehicle_vin"
        private const val KEY_HOME_RATE = "home_kwh_rate"
        private const val KEY_GAS_MPG = "gas_mpg"
        private const val KEY_GAS_PRICE = "gas_price"
        private const val KEY_SETTINGS_VEHICLE_NAME = "vehicle_name"
        private const val KEY_SETTINGS_VEHICLE_MODEL = "vehicle_model"

        @Volatile
        private var instance: WattsonRepository? = null

        /** Process-wide instance, so state survives activity recreation (e.g. rotation). */
        fun getInstance(context: Context): WattsonRepository =
            instance ?: synchronized(this) {
                instance ?: WattsonRepository(context.applicationContext).also { instance = it }
            }
    }
}

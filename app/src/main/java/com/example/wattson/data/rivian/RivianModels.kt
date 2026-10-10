package com.example.wattson.data.rivian

data class RivianSession(
    val email: String,
    val userSessionToken: String,
    val accessToken: String?,
    val refreshToken: String?,
    val vehicleId: String?,
    val vehicleName: String?,
    val vehicleVin: String?
)

data class RivianVehicle(
    val id: String,
    val name: String,
    val model: String,
    val vin: String
)

/** Subset of Rivian's vehicleState, already converted to app units. Null means not reported. */
data class RivianTelemetry(
    val socPercent: Float?,
    val batteryLimitPercent: Float?,
    val rangeMi: Float?,
    val odometerMi: Double?,
    val chargerState: String?
)

sealed class LoginResult {
    data class RequiresOtp(val otpToken: String) : LoginResult()
    data class Success(val userSessionToken: String, val accessToken: String?, val refreshToken: String?) : LoginResult()
    data class Error(val message: String) : LoginResult()
}

sealed class OtpResult {
    data class Success(val userSessionToken: String, val accessToken: String?, val refreshToken: String?) : OtpResult()
    data class Error(val message: String) : OtpResult()
}

class RivianApiException(message: String, val isUnauthenticated: Boolean = false) : Exception(message)

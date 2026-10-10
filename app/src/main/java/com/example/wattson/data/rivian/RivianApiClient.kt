package com.example.wattson.data.rivian

import com.example.wattson.data.analysis.Snapshot
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONException
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URI
import java.time.Instant
import java.util.UUID

/**
 * Minimal client for Rivian's (unofficial) consumer GraphQL gateway, mirroring the
 * operations of rivian-python-client 2.1.0 used by the original Wattson poller.
 * The password is only sent to Rivian during [login] and is never stored.
 */
class RivianApiClient {
    private var csrfToken: String? = null
    private var appSessionToken: String? = null

    /** Vehicles returned by the most recent [getVehicles] call. */
    var lastVehicles: List<RivianVehicle> = emptyList()
        private set

    suspend fun login(email: String, password: String): LoginResult = withContext(Dispatchers.IO) {
        try {
            createCsrfToken()
            val variables = JSONObject().put("email", email).put("password", password)
            val login = execute(LOGIN_QUERY, "Login", variables, withCsrf = true).getJSONObject("login")
            val otpToken = login.str("otpToken")
            if (otpToken != null) {
                LoginResult.RequiresOtp(otpToken)
            } else {
                LoginResult.Success(login.getString("userSessionToken"), login.str("accessToken"), login.str("refreshToken"))
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            LoginResult.Error(e.userMessage())
        }
    }

    suspend fun verifyOtp(email: String, otpToken: String, otpCode: String): OtpResult = withContext(Dispatchers.IO) {
        try {
            if (csrfToken == null) createCsrfToken()
            val variables = JSONObject().put("email", email).put("otpCode", otpCode.trim()).put("otpToken", otpToken)
            val login = execute(OTP_QUERY, "LoginWithOTP", variables, withCsrf = true).getJSONObject("loginWithOTP")
            OtpResult.Success(login.getString("userSessionToken"), login.str("accessToken"), login.str("refreshToken"))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            OtpResult.Error(e.userMessage())
        }
    }

    /** Vehicles on the account, or an empty list if the request fails. */
    suspend fun getVehicles(userSessionToken: String): List<RivianVehicle> = withContext(Dispatchers.IO) {
        try {
            if (appSessionToken == null) createCsrfToken()
            val user = execute(USER_INFO_QUERY, "getUserInfo", null, userSession = userSessionToken).getJSONObject("currentUser")
            val array = user.optJSONArray("vehicles")
            val vehicles = (0 until (array?.length() ?: 0)).map { i ->
                val v = array!!.getJSONObject(i)
                val model = v.optJSONObject("vehicle")?.str("model")
                RivianVehicle(
                    id = v.getString("id"),
                    name = v.str("name") ?: "My Rivian",
                    model = if (model != null) "Rivian $model" else "Rivian",
                    vin = v.str("vin") ?: ""
                )
            }
            lastVehicles = vehicles
            vehicles
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            emptyList()
        }
    }

    /** Polls current vehicle state as a raw snapshot. Throws [RivianApiException] on failure. */
    suspend fun getVehicleState(userSessionToken: String, vehicleId: String): Snapshot = withContext(Dispatchers.IO) {
        try {
            createCsrfToken()
            val state = execute(
                VEHICLE_STATE_QUERY, "GetVehicleState", JSONObject().put("vehicleID", vehicleId),
                userSession = userSessionToken
            ).optJSONObject("vehicleState") ?: throw RivianApiException("Rivian returned no state for this vehicle.")
            parseVehicleState(state, System.currentTimeMillis() / 1000)
        } catch (e: RivianApiException) {
            throw e
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            throw RivianApiException(e.userMessage())
        }
    }

    private fun createCsrfToken() {
        csrfToken = null
        appSessionToken = null
        val data = execute(CSRF_QUERY, "CreateCSRFToken", null).getJSONObject("createCsrfToken")
        csrfToken = data.getString("csrfToken")
        appSessionToken = data.getString("appSessionToken")
    }

    private fun execute(
        query: String,
        operationName: String,
        variables: JSONObject?,
        userSession: String? = null,
        withCsrf: Boolean = false
    ): JSONObject {
        val body = JSONObject()
            .put("operationName", operationName)
            .put("query", query)
            .put("variables", variables ?: JSONObject.NULL)

        val conn = (URI(GATEWAY_URL).toURL().openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = TIMEOUT_MS
            readTimeout = TIMEOUT_MS
            doOutput = true
            setRequestProperty("User-Agent", USER_AGENT)
            setRequestProperty("Accept", "application/json")
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("Apollographql-Client-Name", APOLLO_CLIENT_NAME)
            setRequestProperty("dc-cid", "m-ios-${UUID.randomUUID()}")
            if (withCsrf) csrfToken?.let { setRequestProperty("Csrf-Token", it) }
            appSessionToken?.let { setRequestProperty("A-Sess", it) }
            userSession?.let { setRequestProperty("U-Sess", it) }
        }

        try {
            conn.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
            val status = conn.responseCode
            val stream = if (status in 200..299) conn.inputStream else conn.errorStream
            val text = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
            val json = try {
                JSONObject(text)
            } catch (e: JSONException) {
                throw RivianApiException("Rivian returned an unexpected response (HTTP $status).")
            }

            val errors = json.optJSONArray("errors")
            if (errors != null && errors.length() > 0) {
                val error = errors.getJSONObject(0)
                val extensions = error.optJSONObject("extensions")
                val code = extensions?.str("code")
                val reason = extensions?.str("reason")
                val message = when {
                    reason == "INVALID_OTP" || reason == "OTP_TOKEN_EXPIRED" -> "Invalid or expired verification code."
                    code == "UNAUTHENTICATED" -> "Rivian rejected the sign-in. Check your email and password, or link your account again."
                    else -> error.str("message") ?: "Rivian API error (${code ?: "HTTP $status"})."
                }
                throw RivianApiException(message, isUnauthenticated = code == "UNAUTHENTICATED")
            }
            return json.optJSONObject("data") ?: throw RivianApiException("Rivian returned no data (HTTP $status).")
        } finally {
            conn.disconnect()
        }
    }

    private fun Exception.userMessage(): String = when (this) {
        is RivianApiException -> message ?: "Rivian API error."
        is IOException -> "Could not reach Rivian. Check your internet connection."
        else -> "Unexpected error: ${message ?: javaClass.simpleName}"
    }

    companion object {
        /** Normalizes a vehicleState payload, as upstream riv.py did (km and meters converted where noted). */
        fun parseVehicleState(state: JSONObject, ts: Long): Snapshot {
            // gnssLocation is not wrapped in {value, timeStamp}; handle both shapes.
            val locNode = state.optJSONObject("gnssLocation")
            val loc = locNode?.optJSONObject("value") ?: locNode
            val cloud = state.optJSONObject("cloudConnection")
            return Snapshot(
                ts = ts,
                soc = state.value("batteryLevel"),
                capacityKwh = state.value("batteryCapacity"),
                rangeMi = state.value("distanceToEmpty")?.let { it / KM_PER_MILE }, // reported in km
                batteryLimit = state.value("batteryLimit"),
                chargerState = state.text("chargerState"),
                chargePort = state.text("chargePortState"),
                powerState = state.text("powerState"),
                gear = state.text("gearStatus"),
                lat = loc?.number("latitude"),
                lon = loc?.number("longitude"),
                speed = state.value("gnssSpeed"),
                geoLabel = state.opt("geoLocation").let { if (it is JSONObject) it.str("value") else it as? String },
                odometerM = state.value("vehicleMileage"), // meters
                cloudOnline = cloud?.let { if (it.isNull("isOnline")) null else it.optBoolean("isOnline") },
                cloudSyncTs = cloud?.str("lastSync")?.let { runCatching { Instant.parse(it).epochSecond }.getOrNull() }
            )
        }

        private fun JSONObject.str(name: String): String? = if (isNull(name)) null else optString(name)
        private fun JSONObject.text(name: String): String? = optJSONObject(name)?.str("value")
        private fun JSONObject.number(name: String): Double? =
            if (isNull(name)) null else optDouble(name).takeUnless { it.isNaN() }
        private fun JSONObject.value(name: String): Double? = optJSONObject(name)?.number("value")

        const val GATEWAY_URL = "https://rivian.com/api/gql/gateway/graphql"
        const val USER_AGENT = "RivianApp/707 CFNetwork/1237 Darwin/20.4.0"
        const val APOLLO_CLIENT_NAME = "com.rivian.ios.consumer-apollo-ios"
        const val TIMEOUT_MS = 20_000
        const val KM_PER_MILE = 1.609344

        const val CSRF_QUERY =
            "mutation CreateCSRFToken { createCsrfToken { __typename csrfToken appSessionToken } }"
        const val LOGIN_QUERY =
            "mutation Login(\$email: String!, \$password: String!) { login(email: \$email, password: \$password) { __typename " +
                "... on MobileLoginResponse { __typename accessToken refreshToken userSessionToken } " +
                "... on MobileMFALoginResponse { __typename otpToken } } }"
        const val OTP_QUERY =
            "mutation LoginWithOTP(\$email: String!, \$otpCode: String!, \$otpToken: String!) { " +
                "loginWithOTP(email: \$email, otpCode: \$otpCode, otpToken: \$otpToken) { __typename " +
                "... on MobileLoginResponse { __typename accessToken refreshToken userSessionToken } } }"
        const val USER_INFO_QUERY =
            "query getUserInfo { currentUser { __typename id vehicles { id vin name vehicle { __typename id vin modelYear make model } } } }"
        // Same fields upstream polled (config.PROPS). gnssLocation omits isAuthorized, which the schema rejects.
        const val VEHICLE_STATE_QUERY =
            "query GetVehicleState(\$vehicleID: String!) { vehicleState(id: \$vehicleID) { " +
                "batteryLevel { timeStamp value } batteryLimit { timeStamp value } batteryCapacity { timeStamp value } " +
                "distanceToEmpty { timeStamp value } chargerState { timeStamp value } chargePortState { timeStamp value } " +
                "powerState { timeStamp value } gearStatus { timeStamp value } gnssLocation { latitude longitude timeStamp } " +
                "geoLocation { timeStamp value } gnssSpeed { timeStamp value } vehicleMileage { timeStamp value } " +
                "cloudConnection { lastSync isOnline } } }"
    }
}

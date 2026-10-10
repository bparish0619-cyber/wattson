package com.example.wattson.data.enrich

import com.example.wattson.data.analysis.Analyzer
import com.example.wattson.data.db.WattsonDb
import org.json.JSONObject
import java.net.URLEncoder
import kotlin.math.roundToInt

/**
 * Reverse geocoding for trip endpoints via OpenStreetMap Nominatim (port of upstream
 * geocode.py). Results are cached forever per ~100 m cell, so only new places hit the
 * network, throttled to Nominatim's 1 request per second.
 */
class Geocoder(private val db: WattsonDb) {
    private var lastCall = 0L

    /**
     * Best label for a point: the car's saved-location label (e.g. HOME) wins, else the street.
     * With [allowNetwork] false, only cached names are returned.
     */
    fun label(lat: Double?, lon: Double?, geoLabel: String?, allowNetwork: Boolean = true): String? {
        Analyzer.usableGeoLabel(geoLabel)?.let { return it }
        if (lat == null || lon == null) return null
        val latR = (lat * 1000).roundToInt()
        val lonR = (lon * 1000).roundToInt()
        db.place(latR, lonR)?.let { (cached, ts) ->
            if (cached != null || System.currentTimeMillis() / 1000 - ts < RETRY_FAILED_AFTER_S) return cached
        }
        if (!allowNetwork) return null
        val name = lookup(lat, lon)
        db.setPlace(latR, lonR, name, System.currentTimeMillis() / 1000)
        return name
    }

    @Synchronized
    private fun lookup(lat: Double, lon: Double): String? {
        val wait = lastCall + 1100 - System.currentTimeMillis()
        if (wait > 0) Thread.sleep(wait)
        lastCall = System.currentTimeMillis()
        return try {
            val url = "https://nominatim.openstreetmap.org/reverse?format=jsonv2&zoom=16" +
                "&lat=" + URLEncoder.encode(lat.toString(), "UTF-8") + "&lon=" + URLEncoder.encode(lon.toString(), "UTF-8")
            parse(String(Http.get(url), Charsets.UTF_8))
        } catch (e: Exception) {
            null
        }
    }

    companion object {
        private const val RETRY_FAILED_AFTER_S = 3600L

        /** "Road, Area" from a Nominatim jsonv2 response. */
        fun parse(json: String): String? {
            val data = JSONObject(json)
            val addr = data.optJSONObject("address")
            fun field(name: String) = addr?.optString(name)?.takeIf { it.isNotBlank() }
            val road = field("road")
            val area = field("suburb") ?: field("neighbourhood") ?: field("city") ?: field("town") ?: field("village")
            return when {
                road != null && area != null -> "$road, $area"
                else -> road ?: area ?: data.optString("display_name").takeIf { it.isNotBlank() }
            }
        }
    }
}

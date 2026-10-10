package com.example.wattson.data.enrich

import com.example.wattson.data.db.WattsonDb
import org.json.JSONObject
import java.time.Instant
import java.time.ZoneId
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.roundToInt

/**
 * Ambient temperature at a trip's start, from Open-Meteo (free, no key). Port of upstream
 * weather.py: one request fills a whole day for a ~11 km cell, cached in the database;
 * failed cells back off for an hour.
 */
class WeatherService(private val db: WattsonDb, private val zone: ZoneId) {
    private val failures = ConcurrentHashMap<String, Long>()

    /** Ambient °F at [ts] near the point, or null. With [allowNetwork] false, cache only. */
    fun tempF(ts: Long, lat: Double?, lon: Double?, allowNetwork: Boolean = true): Double? {
        if (lat == null || lon == null) return null
        val local = Instant.ofEpochSecond(ts).atZone(zone)
        val day = local.toLocalDate().toString()
        val latR = (lat * 10).roundToInt() / 10.0
        val lonR = (lon * 10).roundToInt() / 10.0
        db.temperature(day, local.hour, latR, lonR)?.let { return it }
        if (!allowNetwork) return null
        val key = "$day|$latR|$lonR"
        if ((failures[key] ?: 0L) > System.currentTimeMillis() - 3_600_000) return null
        val temps = try {
            fetchDay(day, latR, lonR)
        } catch (e: Exception) {
            emptyMap()
        }
        if (temps.isEmpty()) {
            failures[key] = System.currentTimeMillis()
            return null
        }
        db.putTemperatures(day, latR, lonR, temps)
        return temps[local.hour]
    }

    private fun fetchDay(day: String, lat: Double, lon: Double): Map<Int, Double> {
        val url = "https://api.open-meteo.com/v1/forecast?latitude=$lat&longitude=$lon&start_date=$day&end_date=$day" +
            "&hourly=temperature_2m&temperature_unit=fahrenheit&timezone=${zone.id}"
        return parse(String(Http.get(url, timeoutMs = 20_000), Charsets.UTF_8))
    }

    companion object {
        /** Hour of day to temperature, from an Open-Meteo hourly response. */
        fun parse(json: String): Map<Int, Double> {
            val hourly = JSONObject(json).optJSONObject("hourly") ?: return emptyMap()
            val times = hourly.optJSONArray("time") ?: return emptyMap()
            val temps = hourly.optJSONArray("temperature_2m") ?: return emptyMap()
            val out = HashMap<Int, Double>()
            for (i in 0 until minOf(times.length(), temps.length())) {
                if (temps.isNull(i)) continue
                val t = times.getString(i) // e.g. 2026-09-27T14:00
                out[t.substring(11, 13).toInt()] = temps.getDouble(i)
            }
            return out
        }
    }
}

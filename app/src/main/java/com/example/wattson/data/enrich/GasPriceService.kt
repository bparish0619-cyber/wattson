package com.example.wattson.data.enrich

import com.example.wattson.data.db.WattsonDb
import java.time.LocalDate

/**
 * Daily regular-gas price scraped from AAA's state page for one metro area (port of
 * upstream gasprice.py). AAA is hit at most once per day; a failed scrape caches the
 * fallback price so an outage costs one attempt per day.
 */
class GasPriceService(private val db: WattsonDb) {

    /** Makes sure today's price is cached. Network: at most one request per day. */
    fun refreshToday(today: LocalDate, state: String, metro: String, fallback: Float) {
        val key = today.toString()
        if (db.gasPrices().containsKey(key)) return
        val price = try {
            val url = "https://gasprices.aaa.com/?state=" + state.trim().uppercase()
            parse(String(Http.get(url, userAgent = BROWSER_UA, accept = "text/html"), Charsets.UTF_8), metro)
        } catch (e: Exception) {
            null
        }
        if (price != null) db.putGasPrice(key, price, "AAA $metro") else db.putGasPrice(key, fallback, "fallback")
    }

    companion object {
        private const val BROWSER_UA =
            "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"

        /** The metro's "Current Avg." Regular price from an AAA state page, or null. */
        fun parse(html: String, metro: String): Float? {
            val start = html.indexOf(metro)
            if (start < 0) return null
            val section = html.substring(start, minOf(html.length, start + 3000))
            val row = Regex("<tr>(.*?)</tr>", RegexOption.DOT_MATCHES_ALL).findAll(section)
                .map { it.groupValues[1] }.firstOrNull { "Current Avg" in it } ?: return null
            val cells = Regex("<td[^>]*>(.*?)</td>", RegexOption.DOT_MATCHES_ALL).findAll(row).map { it.groupValues[1] }.toList()
            if (cells.size < 2) return null
            val text = cells[1].replace(Regex("<[^>]+>"), "").trim()
            return Regex("\\$([0-9]+\\.[0-9]+)").find(text)?.groupValues?.get(1)?.toFloatOrNull()
        }

        /** Price for a charge day: that day's cached price, else the latest known, else the fallback. */
        fun priceFor(day: LocalDate, history: Map<String, Float>, fallback: Float): Float =
            history[day.toString()] ?: history.maxByOrNull { it.key }?.value ?: fallback
    }
}

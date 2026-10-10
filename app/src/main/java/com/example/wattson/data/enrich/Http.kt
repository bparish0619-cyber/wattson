package com.example.wattson.data.enrich

import java.net.HttpURLConnection
import java.net.URI

internal object Http {
    const val USER_AGENT = "Wattson-Android/1.0 (https://github.com/bparish0619-cyber/wattson)"

    /** Blocking GET; returns the body, or throws on network errors and non-2xx responses. */
    fun get(url: String, userAgent: String = USER_AGENT, accept: String = "application/json", timeoutMs: Int = 15_000): ByteArray {
        val conn = URI(url).toURL().openConnection() as HttpURLConnection
        try {
            conn.connectTimeout = timeoutMs
            conn.readTimeout = timeoutMs
            conn.setRequestProperty("User-Agent", userAgent)
            conn.setRequestProperty("Accept", accept)
            val status = conn.responseCode
            if (status !in 200..299) throw java.io.IOException("HTTP $status for $url")
            return conn.inputStream.use { it.readBytes() }
        } finally {
            conn.disconnect()
        }
    }
}

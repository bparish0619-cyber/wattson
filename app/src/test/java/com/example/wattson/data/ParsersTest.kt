package com.example.wattson.data

import com.example.wattson.data.enrich.GasPriceService
import com.example.wattson.data.enrich.Geocoder
import com.example.wattson.data.enrich.WeatherService
import com.example.wattson.data.rivian.RivianApiClient
import java.time.LocalDate
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ParsersTest {
    @Test
    fun aaaMetroCurrentAverage() {
        val html = """
            <h3>Los Angeles-Long Beach</h3><table><tbody>
            <tr><td>Current Avg.</td><td>${'$'}4.892</td><td>${'$'}5.10</td></tr>
            <tr><td>Yesterday Avg.</td><td>${'$'}4.90</td></tr></tbody></table>"""
        assertEquals(4.892f, GasPriceService.parse(html, "Los Angeles-Long Beach")!!, 0.0001f)
        assertNull(GasPriceService.parse(html, "San Diego"))
    }

    @Test
    fun gasPriceFallsBackToLatestThenSetting() {
        val history = mapOf("2026-10-01" to 4.5f, "2026-10-05" to 4.8f)
        assertEquals(4.5f, GasPriceService.priceFor(LocalDate.of(2026, 10, 1), history, 4.0f))
        assertEquals(4.8f, GasPriceService.priceFor(LocalDate.of(2026, 9, 1), history, 4.0f))
        assertEquals(4.0f, GasPriceService.priceFor(LocalDate.of(2026, 9, 1), emptyMap(), 4.0f))
    }

    @Test
    fun nominatimRoadAndArea() {
        assertEquals("Abbot Kinney Boulevard, Venice", Geocoder.parse("""{"address":{"road":"Abbot Kinney Boulevard","suburb":"Venice","city":"Los Angeles"}}"""))
        assertEquals("Los Angeles", Geocoder.parse("""{"address":{"city":"Los Angeles"}}"""))
    }

    @Test
    fun openMeteoHourly() {
        val temps = WeatherService.parse("""{"hourly":{"time":["2026-09-27T13:00","2026-09-27T14:00"],"temperature_2m":[71.2,null]}}""")
        assertEquals(mapOf(13 to 71.2), temps)
    }

    @Test
    fun rivianVehicleStateIsNormalized() {
        val state = JSONObject(
            """{"batteryLevel":{"timeStamp":"t","value":72.5},"batteryCapacity":{"value":91.4},
               "distanceToEmpty":{"value":402.3},"batteryLimit":{"value":85},
               "chargerState":{"value":"charging_active"},"gearStatus":{"value":"park"},
               "gnssLocation":{"latitude":34.01,"longitude":-118.49,"timeStamp":"t"},
               "geoLocation":{"value":"HOME"},"vehicleMileage":{"value":2123456},
               "gnssSpeed":{"value":null},"powerState":null,
               "cloudConnection":{"lastSync":"2026-10-10T06:00:00.000Z","isOnline":false}}"""
        )
        val s = RivianApiClient.parseVehicleState(state, 1_791_600_000)
        assertEquals(72.5, s.soc!!, 1e-9)
        assertEquals(402.3 / 1.609344, s.rangeMi!!, 1e-6) // km to miles
        assertEquals(85.0, s.batteryLimit!!, 1e-9)
        assertEquals("charging_active", s.chargerState)
        assertEquals(34.01, s.lat!!, 1e-9)
        assertEquals("HOME", s.geoLabel)
        assertEquals(2123456.0, s.odometerM!!, 1e-9) // meters, stored raw
        assertEquals(false, s.cloudOnline)
        assertEquals(1_791_612_000L, s.cloudSyncTs)
        assertNull(s.speed)
        assertNull(s.powerState)
    }
}

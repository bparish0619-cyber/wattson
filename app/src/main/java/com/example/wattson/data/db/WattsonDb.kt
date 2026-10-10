package com.example.wattson.data.db

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import com.example.wattson.data.analysis.Snapshot

/**
 * On-device storage, mirroring upstream db.py: every poll is kept as a raw snapshot
 * (trips, charges and drain are re-derived from them), plus caches for place names,
 * weather and gas prices, and the DC fast-charge prices the user enters.
 */
class WattsonDb(context: Context) : SQLiteOpenHelper(context, "wattson.db", null, 1) {

    override fun onConfigure(db: SQLiteDatabase) {
        db.enableWriteAheadLogging()
    }

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """CREATE TABLE snapshots(
                id INTEGER PRIMARY KEY,
                ts INTEGER NOT NULL,
                soc REAL, capacity_kwh REAL, range_mi REAL, battery_limit REAL,
                charger_state TEXT, charge_port TEXT, power_state TEXT, gear TEXT,
                lat REAL, lon REAL, speed REAL, geo_label TEXT, odometer REAL,
                cloud_online INTEGER, cloud_sync_ts INTEGER)"""
        )
        db.execSQL("CREATE INDEX idx_snap_ts ON snapshots(ts)")
        // Keyed by charge start time, which is stable across re-derives (as upstream).
        db.execSQL("CREATE TABLE charge_costs(start_ts INTEGER PRIMARY KEY, cost_per_kwh REAL NOT NULL)")
        db.execSQL("CREATE TABLE places(lat_r INTEGER NOT NULL, lon_r INTEGER NOT NULL, label TEXT, ts INTEGER, PRIMARY KEY(lat_r, lon_r))")
        db.execSQL("CREATE TABLE weather_cache(day TEXT NOT NULL, hour INTEGER NOT NULL, lat_r REAL NOT NULL, lon_r REAL NOT NULL, temp_f REAL, PRIMARY KEY(day, hour, lat_r, lon_r))")
        db.execSQL("CREATE TABLE gas_prices(day TEXT PRIMARY KEY, price REAL NOT NULL, source TEXT)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit

    // ---- snapshots ----

    fun insertSnapshot(s: Snapshot) {
        // The odometer is cached server-side and sometimes comes back null; carry the
        // last reading forward (it can't decrease while parked anyway).
        val odometer = s.odometerM ?: lastOdometer()
        writableDatabase.insert("snapshots", null, ContentValues().apply {
            put("ts", s.ts)
            put("soc", s.soc)
            put("capacity_kwh", s.capacityKwh)
            put("range_mi", s.rangeMi)
            put("battery_limit", s.batteryLimit)
            put("charger_state", s.chargerState)
            put("charge_port", s.chargePort)
            put("power_state", s.powerState)
            put("gear", s.gear)
            put("lat", s.lat)
            put("lon", s.lon)
            put("speed", s.speed)
            put("geo_label", s.geoLabel)
            put("odometer", odometer)
            put("cloud_online", s.cloudOnline?.let { if (it) 1 else 0 })
            put("cloud_sync_ts", s.cloudSyncTs)
        })
    }

    private fun lastOdometer(): Double? =
        readableDatabase.rawQuery("SELECT odometer FROM snapshots WHERE odometer IS NOT NULL ORDER BY ts DESC LIMIT 1", null)
            .use { c -> if (c.moveToFirst()) c.getDouble(0) else null }

    fun snapshots(sinceTs: Long = 0): List<Snapshot> =
        readableDatabase.rawQuery("SELECT * FROM snapshots WHERE ts >= ? ORDER BY ts, id", arrayOf(sinceTs.toString())).use { c ->
            val out = ArrayList<Snapshot>(c.count)
            while (c.moveToNext()) {
                out += Snapshot(
                    ts = c.long("ts")!!,
                    soc = c.double("soc"),
                    capacityKwh = c.double("capacity_kwh"),
                    rangeMi = c.double("range_mi"),
                    batteryLimit = c.double("battery_limit"),
                    chargerState = c.string("charger_state"),
                    chargePort = c.string("charge_port"),
                    powerState = c.string("power_state"),
                    gear = c.string("gear"),
                    lat = c.double("lat"),
                    lon = c.double("lon"),
                    speed = c.double("speed"),
                    geoLabel = c.string("geo_label"),
                    odometerM = c.double("odometer"),
                    cloudOnline = c.long("cloud_online")?.let { it == 1L },
                    cloudSyncTs = c.long("cloud_sync_ts")
                )
            }
            out
        }

    fun lastSnapshots(limit: Int): List<Snapshot> =
        snapshots(
            readableDatabase.rawQuery("SELECT ts FROM snapshots ORDER BY ts DESC LIMIT 1 OFFSET ?", arrayOf((limit - 1).toString()))
                .use { c -> if (c.moveToFirst()) c.getLong(0) else 0L }
        )

    fun snapshotCount(): Long =
        readableDatabase.rawQuery("SELECT COUNT(*) FROM snapshots", null).use { c -> if (c.moveToFirst()) c.getLong(0) else 0L }

    // ---- user-entered DC prices ----

    fun chargeCosts(): Map<Long, Float> =
        readableDatabase.rawQuery("SELECT start_ts, cost_per_kwh FROM charge_costs", null).use { c ->
            buildMap { while (c.moveToNext()) put(c.getLong(0), c.getFloat(1)) }
        }

    fun setChargeCost(startTs: Long, costPerKwh: Float?) {
        if (costPerKwh == null) {
            writableDatabase.delete("charge_costs", "start_ts = ?", arrayOf(startTs.toString()))
        } else {
            writableDatabase.insertWithOnConflict(
                "charge_costs", null,
                ContentValues().apply { put("start_ts", startTs); put("cost_per_kwh", costPerKwh) },
                SQLiteDatabase.CONFLICT_REPLACE
            )
        }
    }

    // ---- place-name cache ----

    /** (label, lookup time) if this ~100 m cell was ever looked up; the label may be null. */
    fun place(latR: Int, lonR: Int): Pair<String?, Long>? =
        readableDatabase.rawQuery("SELECT label, ts FROM places WHERE lat_r = ? AND lon_r = ?", arrayOf(latR.toString(), lonR.toString()))
            .use { c -> if (c.moveToFirst()) c.string("label") to (c.long("ts") ?: 0L) else null }

    fun setPlace(latR: Int, lonR: Int, label: String?, ts: Long) {
        writableDatabase.insertWithOnConflict("places", null, ContentValues().apply {
            put("lat_r", latR); put("lon_r", lonR); put("label", label); put("ts", ts)
        }, SQLiteDatabase.CONFLICT_REPLACE)
    }

    // ---- weather cache ----

    fun temperature(day: String, hour: Int, latR: Double, lonR: Double): Double? =
        readableDatabase.rawQuery(
            "SELECT temp_f FROM weather_cache WHERE day = ? AND hour = ? AND lat_r = ? AND lon_r = ?",
            arrayOf(day, hour.toString(), latR.toString(), lonR.toString())
        ).use { c -> if (c.moveToFirst()) c.double("temp_f") else null }

    fun putTemperatures(day: String, latR: Double, lonR: Double, byHour: Map<Int, Double>) {
        val db = writableDatabase
        db.beginTransaction()
        try {
            for ((hour, temp) in byHour) {
                db.insertWithOnConflict("weather_cache", null, ContentValues().apply {
                    put("day", day); put("hour", hour); put("lat_r", latR); put("lon_r", lonR); put("temp_f", temp)
                }, SQLiteDatabase.CONFLICT_REPLACE)
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    // ---- gas price history ----

    fun gasPrices(): Map<String, Float> =
        readableDatabase.rawQuery("SELECT day, price FROM gas_prices", null).use { c ->
            buildMap { while (c.moveToNext()) put(c.getString(0), c.getFloat(1)) }
        }

    fun putGasPrice(day: String, price: Float, source: String) {
        writableDatabase.insertWithOnConflict("gas_prices", null, ContentValues().apply {
            put("day", day); put("price", price); put("source", source)
        }, SQLiteDatabase.CONFLICT_REPLACE)
    }

    private fun Cursor.idx(name: String) = getColumnIndexOrThrow(name)
    private fun Cursor.double(name: String): Double? = idx(name).let { if (isNull(it)) null else getDouble(it) }
    private fun Cursor.long(name: String): Long? = idx(name).let { if (isNull(it)) null else getLong(it) }
    private fun Cursor.string(name: String): String? = idx(name).let { if (isNull(it)) null else getString(it) }
}

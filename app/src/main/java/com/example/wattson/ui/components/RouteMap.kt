package com.example.wattson.ui.components

import android.content.Context
import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.wattson.model.LatLon
import com.example.wattson.ui.theme.RivianAmber
import com.example.wattson.ui.theme.RivianBlack
import com.example.wattson.ui.theme.RivianGreen
import com.example.wattson.ui.theme.TextTertiary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URI
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.tan

/**
 * Trip route on Esri's World Dark Gray Canvas basemap (free, no key), as upstream's
 * dashboard maps. Tiles are cached on disk for a week; without network the route is
 * still drawn on the dark background.
 */
@Composable
fun RouteMap(path: List<LatLon>, modifier: Modifier = Modifier) {
    if (path.size < 2) return
    val context = LocalContext.current
    val density = LocalDensity.current.density
    BoxWithConstraints(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(RivianBlack)
    ) {
        // Draw map pixels at ~1.5x screen density so 256 px tiles stay legible.
        val k = max(1f, density / 1.5f)
        val w = constraints.maxWidth / k
        val h = constraints.maxHeight / k
        val view = remember(path, w, h) { MapView.fit(path, w.toDouble(), h.toDouble()) }
        val tiles = remember(view) { mutableStateMapOf<Triple<Int, Int, Int>, ImageBitmap>() }

        LaunchedEffect(view) {
            for (key in view.tiles()) {
                TileCache.load(context, key)?.let { tiles[key] = it }
            }
        }

        Canvas(modifier = Modifier.fillMaxSize()) {
            scale(k, pivot = Offset.Zero) {
                for ((key, bitmap) in tiles) {
                    val (_, tx, ty) = key
                    drawImage(
                        bitmap,
                        dstOffset = IntOffset((tx * 256 - view.ox).toInt(), (ty * 256 - view.oy).toInt()),
                        dstSize = IntSize(256, 256)
                    )
                }
                val points = path.map { view.toScreen(it) }
                val line = Path().apply {
                    moveTo(points.first().x, points.first().y)
                    points.drop(1).forEach { lineTo(it.x, it.y) }
                }
                drawPath(line, RivianGreen, style = Stroke(width = 3.5f, cap = StrokeCap.Round, join = StrokeJoin.Round))
                drawCircle(Color.White, radius = 5f, center = points.first())
                drawCircle(RivianGreen, radius = 3.5f, center = points.first())
                drawCircle(Color.White, radius = 5f, center = points.last())
                drawCircle(RivianAmber, radius = 3.5f, center = points.last())
            }
        }
        Text(
            text = "Esri, HERE, Garmin, © OpenStreetMap",
            color = TextTertiary,
            fontSize = 8.sp,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(4.dp)
        )
    }
}

/** Web-Mercator viewport: zoom chosen so the whole route fits with padding. */
private class MapView(val z: Int, val ox: Double, val oy: Double, val w: Double, val h: Double) {
    fun toScreen(p: LatLon): Offset {
        val (x, y) = merc(p.lon, p.lat, z)
        return Offset((x - ox).toFloat(), (y - oy).toFloat())
    }

    fun tiles(): List<Triple<Int, Int, Int>> {
        val n = 1 shl z
        val out = mutableListOf<Triple<Int, Int, Int>>()
        for (tx in floor(ox / 256).toInt()..floor((ox + w) / 256).toInt()) {
            for (ty in floor(oy / 256).toInt()..floor((oy + h) / 256).toInt()) {
                if (tx in 0 until n && ty in 0 until n) out += Triple(z, tx, ty)
            }
        }
        return out
    }

    companion object {
        fun fit(path: List<LatLon>, w: Double, h: Double): MapView {
            val lat0 = path.minOf { it.lat }
            val lat1 = path.maxOf { it.lat }
            val lon0 = path.minOf { it.lon }
            val lon1 = path.maxOf { it.lon }
            // Dark Gray Canvas tops out at z16.
            var z = 3
            for (cand in 16 downTo 3) {
                val (x0, y0) = merc(lon0, lat0, cand)
                val (x1, y1) = merc(lon1, lat1, cand)
                if (abs(x1 - x0) <= w - 48 && abs(y1 - y0) <= h - 48) {
                    z = cand
                    break
                }
            }
            val (x0, y0) = merc(lon0, lat0, z)
            val (x1, y1) = merc(lon1, lat1, z)
            return MapView(z, (x0 + x1) / 2 - w / 2, (y0 + y1) / 2 - h / 2, w, h)
        }

        fun merc(lon: Double, lat: Double, z: Int): Pair<Double, Double> {
            val n = (1 shl z).toDouble()
            val x = (lon + 180.0) / 360.0 * 256 * n
            val r = Math.toRadians(min(max(lat, -85.0511), 85.0511))
            val y = (1.0 - ln(tan(r) + 1.0 / cos(r)) / PI) / 2.0 * 256 * n
            return x to y
        }
    }
}

private object TileCache {
    private const val URL = "https://server.arcgisonline.com/ArcGIS/rest/services/Canvas/World_Dark_Gray_Base/MapServer/tile/%d/%d/%d"
    private const val TTL_MS = 7L * 24 * 3600 * 1000
    private val memory = LruCache<Triple<Int, Int, Int>, ImageBitmap>(64)

    suspend fun load(context: Context, key: Triple<Int, Int, Int>): ImageBitmap? = withContext(Dispatchers.IO) {
        memory.get(key)?.let { return@withContext it }
        val (z, x, y) = key
        val file = File(context.cacheDir, "tiles/$z/$x/$y.png")
        val bytes = if (file.exists() && System.currentTimeMillis() - file.lastModified() < TTL_MS) {
            file.readBytes()
        } else {
            download(String.format(java.util.Locale.US, URL, z, y, x))?.also { data ->
                file.parentFile?.mkdirs()
                file.writeBytes(data)
            } ?: file.takeIf { it.exists() }?.readBytes() // stale tile beats no tile
        } ?: return@withContext null
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()?.also { memory.put(key, it) }
    }

    private fun download(url: String): ByteArray? = try {
        val conn = URI(url).toURL().openConnection() as HttpURLConnection
        try {
            conn.connectTimeout = 15_000
            conn.readTimeout = 15_000
            conn.setRequestProperty("User-Agent", "Wattson-Android/1.0")
            if (conn.responseCode in 200..299) conn.inputStream.use { it.readBytes() } else null
        } finally {
            conn.disconnect()
        }
    } catch (e: Exception) {
        null
    }
}

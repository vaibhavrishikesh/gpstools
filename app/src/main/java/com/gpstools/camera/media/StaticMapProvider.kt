package com.gpstools.camera.media

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.Log
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.tan

/**
 * Fetches a static map thumbnail centered on a coordinate, for compositing into the
 * photo stamp (US-008). Implementations may hit the network, so callers MUST invoke
 * this off the main thread and treat a null return (offline / fetch error) as
 * "no map" and fall back gracefully. Kept behind an interface so the map source can
 * be swapped (OSM tiles today; a keyed Google/Mapbox static API later) without
 * touching the stamp/capture code — see the PRD Tech Considerations.
 */
interface StaticMapProvider {
    /**
     * Returns a square [sizePx]×[sizePx] map thumbnail centered on
     * (latitude, longitude) with a marker at the center, or null if it can't be
     * produced (e.g. offline or a fetch error).
     */
    fun fetchMapThumbnail(
        latitude: Double,
        longitude: Double,
        sizePx: Int = DEFAULT_SIZE_PX,
    ): Bitmap?

    companion object {
        const val DEFAULT_SIZE_PX = 256
    }
}

/**
 * [StaticMapProvider] backed by the OpenStreetMap raster tile server. Needs no API
 * key or billing (the PRD's no-per-call-cost option). Fetches the handful of 256px
 * web-mercator tiles covering a window centered on the point, stitches them, and
 * draws a marker at the center. OSM's tile usage policy requires a descriptive
 * User-Agent, which we set on every request.
 */
class OsmStaticMapProvider(
    private val zoom: Int = DEFAULT_ZOOM,
    private val userAgent: String = DEFAULT_USER_AGENT,
) : StaticMapProvider {

    override fun fetchMapThumbnail(latitude: Double, longitude: Double, sizePx: Int): Bitmap? {
        return try {
            val n = 1 shl zoom
            val latRad = Math.toRadians(latitude)
            // Web-mercator: fractional tile coordinates for the point, then global px.
            val xTile = (longitude + 180.0) / 360.0 * n
            val yTile = (1.0 - ln(tan(latRad) + 1.0 / cos(latRad)) / PI) / 2.0 * n
            val left = xTile * TILE_SIZE - sizePx / 2.0
            val top = yTile * TILE_SIZE - sizePx / 2.0

            val result = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(result)
            canvas.drawColor(EMPTY_TILE_COLOR) // shows through any tile we can't draw

            val txStart = floor(left / TILE_SIZE).toInt()
            val txEnd = floor((left + sizePx - 1) / TILE_SIZE).toInt()
            val tyStart = floor(top / TILE_SIZE).toInt()
            val tyEnd = floor((top + sizePx - 1) / TILE_SIZE).toInt()

            var drewAny = false
            for (tx in txStart..txEnd) {
                for (ty in tyStart..tyEnd) {
                    // Tiles outside the world bounds (near the poles) simply don't exist.
                    if (tx < 0 || ty < 0 || tx >= n || ty >= n) continue
                    val tile = fetchTile(tx, ty) ?: return null // any failed tile -> no map
                    val dx = (tx.toDouble() * TILE_SIZE - left).toFloat()
                    val dy = (ty.toDouble() * TILE_SIZE - top).toFloat()
                    canvas.drawBitmap(tile, dx, dy, null)
                    tile.recycle()
                    drewAny = true
                }
            }
            if (!drewAny) {
                result.recycle()
                return null
            }
            drawMarker(canvas, sizePx / 2f, sizePx / 2f)
            result
        } catch (e: Exception) {
            Log.w(TAG, "Failed to build OSM map thumbnail", e)
            null
        }
    }

    private fun fetchTile(x: Int, y: Int): Bitmap? {
        val url = URL("https://tile.openstreetmap.org/$zoom/$x/$y.png")
        val conn = (url.openConnection() as HttpURLConnection).apply {
            connectTimeout = TIMEOUT_MS
            readTimeout = TIMEOUT_MS
            setRequestProperty("User-Agent", userAgent)
        }
        return try {
            if (conn.responseCode != HttpURLConnection.HTTP_OK) {
                Log.w(TAG, "Tile $zoom/$x/$y returned HTTP ${conn.responseCode}")
                return null
            }
            conn.inputStream.use { BitmapFactory.decodeStream(it) }
        } catch (e: IOException) {
            Log.w(TAG, "Tile $zoom/$x/$y fetch failed", e)
            null
        } finally {
            conn.disconnect()
        }
    }

    /** Draws a simple red dot with a white ring at the photo's coordinate. */
    private fun drawMarker(canvas: Canvas, cx: Float, cy: Float) {
        val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }
        val dot = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(0xD3, 0x2F, 0x2F) }
        canvas.drawCircle(cx, cy, MARKER_RING_RADIUS, ring)
        canvas.drawCircle(cx, cy, MARKER_DOT_RADIUS, dot)
    }

    companion object {
        private const val TAG = "OsmStaticMap"
        private const val TILE_SIZE = 256
        const val DEFAULT_ZOOM = 15
        private const val TIMEOUT_MS = 8000
        private const val EMPTY_TILE_COLOR = 0xFFE8E8E8.toInt()
        private const val MARKER_RING_RADIUS = 9f
        private const val MARKER_DOT_RADIUS = 6f
        private const val DEFAULT_USER_AGENT = "gpstools/1.0 (Android; com.gpstools.camera)"
    }
}

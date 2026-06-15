package com.gpstools.camera.media

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * The information burned onto a captured photo as a location stamp (US-007).
 * [timestamp] is captured at shutter-press time. Location fields are nullable so a
 * photo taken before a fix is available still gets a date/time stamp.
 */
data class StampData(
    val timestamp: Date,
    val latitude: Double?,
    val longitude: Double?,
    val accuracyMeters: Float?,
    val address: String?,
)

/** Reference width the stamp's type sizes are tuned against; everything scales from it. */
private const val REFERENCE_WIDTH = 1080f
/** Side length of the square map thumbnail, in REFERENCE_WIDTH units (scaled at draw). */
private const val MAP_SIZE = 200f
private const val STAMP_DATE_PATTERN = "dd MMM yyyy  HH:mm:ss z"

/**
 * Draws [stamp] as a legible semi-transparent panel across the bottom of [source]
 * and returns the composited bitmap. Type sizes scale with the image width so the
 * stamp stays readable at any resolution, and the panel spans the full width so it
 * renders correctly for both portrait and landscape captures. [source] is assumed
 * to already be rotated upright (see PhotoStorage.toUprightBitmap).
 *
 * When [mapThumbnail] is non-null (US-008) a square map tile is composited on the
 * right of the panel and the text wraps in the remaining width; when it's null
 * (offline / no fix) the layout falls back to text spanning the full width.
 */
fun drawStamp(source: Bitmap, stamp: StampData, mapThumbnail: Bitmap? = null): Bitmap {
    val result = if (source.isMutable && source.config == Bitmap.Config.ARGB_8888) {
        source
    } else {
        source.copy(Bitmap.Config.ARGB_8888, true)
    }
    val canvas = Canvas(result)
    val width = result.width
    val height = result.height
    val scale = width / REFERENCE_WIDTH

    val pad = 22f * scale
    val lineSpacing = 10f * scale
    val shadow = 3f * scale

    val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = 34f * scale
        typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        setShadowLayer(shadow, 0f, 0f, Color.BLACK)
    }
    val bodyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = 28f * scale
        typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
        setShadowLayer(shadow, 0f, 0f, Color.BLACK)
    }
    val monoPaint = Paint(bodyPaint).apply {
        typeface = Typeface.create(Typeface.MONOSPACE, Typeface.NORMAL)
    }

    // Reserve a square on the right for the map thumbnail when present.
    val mapSize = if (mapThumbnail != null) MAP_SIZE * scale else 0f
    val mapGap = if (mapThumbnail != null) pad else 0f
    val maxTextWidth = width - 2 * pad - mapSize - mapGap

    // Build the ordered list of (text, paint) lines: address (wrapped), then the
    // coordinates line (when a fix exists), then the date/time line (always).
    val lines = mutableListOf<Pair<String, Paint>>()
    stamp.address?.takeIf { it.isNotBlank() }?.let { addr ->
        wrapText(addr, titlePaint, maxTextWidth).forEach { lines += it to titlePaint }
    }
    coordinatesLine(stamp)?.let { lines += it to monoPaint }
    lines += dateLine(stamp) to bodyPaint

    // Measure the panel height from the actual line metrics.
    var textHeight = 0f
    lines.forEachIndexed { index, (_, paint) ->
        textHeight += paint.descent() - paint.ascent()
        if (index < lines.size - 1) textHeight += lineSpacing
    }
    // The panel must be tall enough for whichever is bigger: the text or the map.
    val contentHeight = maxOf(textHeight, mapSize)
    val panelTop = height - (contentHeight + 2 * pad)

    val panelPaint = Paint().apply { color = Color.argb(150, 0, 0, 0) }
    canvas.drawRect(0f, panelTop, width.toFloat(), height.toFloat(), panelPaint)

    var top = panelTop + pad
    lines.forEach { (text, paint) ->
        canvas.drawText(text, pad, top - paint.ascent(), paint)
        top += (paint.descent() - paint.ascent()) + lineSpacing
    }

    if (mapThumbnail != null) {
        val mapLeft = width - pad - mapSize
        val mapTop = panelTop + (contentHeight + 2 * pad - mapSize) / 2f // centered vertically
        val dst = RectF(mapLeft, mapTop, mapLeft + mapSize, mapTop + mapSize)
        val mapPaint = Paint(Paint.FILTER_BITMAP_FLAG)
        canvas.drawBitmap(mapThumbnail, null, dst, mapPaint)
        val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 2f * scale
            color = Color.WHITE
        }
        canvas.drawRect(dst, borderPaint)
    }
    return result
}

private fun coordinatesLine(stamp: StampData): String? {
    val lat = stamp.latitude ?: return null
    val lon = stamp.longitude ?: return null
    val base = String.format(Locale.US, "%.6f, %.6f", lat, lon)
    val accuracy = stamp.accuracyMeters?.let { String.format(Locale.US, "  ±%d m", it.toInt()) } ?: ""
    return base + accuracy
}

private fun dateLine(stamp: StampData): String =
    SimpleDateFormat(STAMP_DATE_PATTERN, Locale.getDefault()).format(stamp.timestamp)

/** Greedily wraps [text] to fit [maxWidth] under [paint]; never loops on long words. */
private fun wrapText(text: String, paint: Paint, maxWidth: Float): List<String> {
    val words = text.split(" ").filter { it.isNotEmpty() }
    if (words.isEmpty()) return emptyList()
    val lines = mutableListOf<String>()
    var current = StringBuilder()
    for (word in words) {
        val candidate = if (current.isEmpty()) word else "$current $word"
        if (current.isEmpty() || paint.measureText(candidate) <= maxWidth) {
            current = StringBuilder(candidate)
        } else {
            lines += current.toString()
            current = StringBuilder(word)
        }
    }
    if (current.isNotEmpty()) lines += current.toString()
    return lines
}

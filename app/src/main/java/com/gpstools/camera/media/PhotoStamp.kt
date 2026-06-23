package com.gpstools.camera.media

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import com.gpstools.camera.settings.CoordinateFormat
import com.gpstools.camera.settings.LayoutPreset
import com.gpstools.camera.settings.StampPosition
import com.gpstools.camera.settings.TimeFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * The information burned onto a captured photo as a location stamp (US-007).
 * [timestamp] is captured at shutter-press time. Location fields are nullable so a
 * photo taken before a fix is available still gets a date/time stamp.
 *
 * [projectName] and [note] are the user's custom fields (US-010); blank/empty
 * values are simply not rendered. The optional logo image is passed separately to
 * [drawStamp] since it's a bitmap rather than a value.
 *
 * [coordinateFormat] and [timeFormat] (US-014) are snapshot from the user's
 * Settings at capture time so the stamp honours their preferred lat/long notation
 * and clock.
 */
data class StampData(
    val timestamp: Date,
    val latitude: Double?,
    val longitude: Double?,
    val accuracyMeters: Float?,
    val address: String?,
    val projectName: String? = null,
    val note: String? = null,
    /** Pre-formatted weather line (US-009), e.g. "28°C · Clear"; null when unavailable. */
    val weather: String? = null,
    /**
     * Pre-formatted altitude + compass facing line (P2-US-013), e.g.
     * "Altitude 342m · Facing NE"; null when neither the altitude nor a compass
     * bearing is available. Always rendered when present (not gated by the layout
     * preset), like the project/note/date-time fields.
     */
    val altitudeFacing: String? = null,
    /**
     * Raw GPS altitude in metres (P2-US-014); null when the fix has none. Carried
     * separately from the pre-formatted [altitudeFacing] line so it can be written as
     * machine-readable EXIF GPS metadata into the saved JPEG.
     */
    val altitudeMeters: Double? = null,
    val coordinateFormat: CoordinateFormat = CoordinateFormat.DEFAULT,
    val timeFormat: TimeFormat = TimeFormat.DEFAULT,
    /**
     * Which location fields render on the stamp (P2-US-010). Snapshot from the user's
     * Settings at capture time; gates the map / address / coordinates / weather lines
     * in every template. Project/site, note, logo and date-time ignore it.
     */
    val layoutPreset: LayoutPreset = LayoutPreset.DEFAULT,
    /**
     * Which edge the stamp panel is anchored to (P2-US-011, WYSIWYG). Snapshot from
     * the user's Settings at capture time; the live viewfinder overlay sits at the
     * same edge so the on-screen preview matches the burned photo.
     */
    val stampPosition: StampPosition = StampPosition.DEFAULT,
    /**
     * Whether the date/time line is rendered on the stamp (P2-US-017). Snapshot from
     * the user's Settings at capture time. Defaults to true (the historical
     * behaviour). When false the date/time line is omitted from every template.
     */
    val showDateTime: Boolean = true,
    /**
     * Custom watermark text (P2-US-017), e.g. a company name. When non-blank it's
     * drawn bottom-right of the photo over a subtle backing, independent of the
     * template — like the logo (which sits top-right).
     */
    val watermark: String? = null,
)

/** Reference width the stamp's type sizes are tuned against; everything scales from it. */
private const val REFERENCE_WIDTH = 1080f
/** Side length of the square map thumbnail, in REFERENCE_WIDTH units (scaled at draw). */
private const val MAP_SIZE = 200f
/** Larger map for the Field-Report template (drawn on the left). */
private const val FIELD_MAP_SIZE = 240f
/** Square box the logo is fitted into, in REFERENCE_WIDTH units (top-right corner). */
private const val LOGO_SIZE = 150f
/** Accent colour used for the Advance/Field-Report header band + row labels. */
private val FIELD_ACCENT = Color.rgb(255, 193, 7)
/** Green "WORK REPORT" header accent for the Reporting template. */
private val REPORT_GREEN = Color.rgb(46, 160, 67)
/** Blue header accent for the Custom template. */
private val REPORT_BLUE = Color.rgb(33, 118, 210)
/** Gold left accent bar for the Modern template. */
private val MODERN_ACCENT = Color.rgb(242, 169, 59)

/**
 * Draws [stamp] onto [source] in the style of [template] (US-009) and returns the
 * composited bitmap. Type sizes scale with the image width so the stamp stays
 * readable at any resolution, and layouts span the full width so they render
 * correctly for both portrait and landscape captures. [source] is assumed to
 * already be rotated upright (see PhotoStorage.toUprightBitmap).
 *
 * [mapThumbnail] is composited by templates whose [StampTemplate.usesMap] is true;
 * when it's null (offline / no fix) those layouts fall back to text spanning the
 * full width.
 *
 * [logo] (US-010), when present, is drawn into the top-right corner over a subtle
 * backing so it stays visible on any background, independent of the template.
 */
fun drawStamp(
    source: Bitmap,
    stamp: StampData,
    template: StampTemplate = StampTemplate.DEFAULT,
    mapThumbnail: Bitmap? = null,
    logo: Bitmap? = null,
): Bitmap {
    val result = if (source.isMutable && source.config == Bitmap.Config.ARGB_8888) {
        source
    } else {
        source.copy(Bitmap.Config.ARGB_8888, true)
    }
    val canvas = Canvas(result)
    // The layout preset (P2-US-010) can suppress the map even for a map-drawing template.
    val effectiveMap = if (stamp.layoutPreset.showMap) mapThumbnail else null
    // WYSIWYG (P2-US-011): anchor the panel to the top edge when the user picked TOP.
    val top = stamp.stampPosition == StampPosition.TOP
    when (template) {
        StampTemplate.CLASSIC -> drawClassic(canvas, result, stamp, effectiveMap, top)
        StampTemplate.MODERN -> drawModern(canvas, result, stamp, top)
        StampTemplate.REPORTING ->
            drawReport(canvas, result, stamp, effectiveMap, top, "", REPORT_GREEN, forceAllFields = false)
        StampTemplate.ADVANCE ->
            drawReport(canvas, result, stamp, effectiveMap, top, "", FIELD_ACCENT, forceAllFields = true)
        StampTemplate.CUSTOM ->
            drawReport(canvas, result, stamp, effectiveMap, top, "", REPORT_BLUE, forceAllFields = false)
    }
    if (logo != null) drawLogo(canvas, result, logo)
    // Custom watermark (P2-US-017) — bottom-right, independent of the template.
    stamp.watermark?.takeIf { it.isNotBlank() }?.let { drawWatermark(canvas, result, it) }
    return result
}

// --- Classic ---------------------------------------------------------------

/**
 * Full-width semi-transparent panel at the bottom: bold wrapped address, the
 * coordinates line, then date/time, with the map thumbnail (when present) on the
 * right and text wrapped in the remaining width.
 */
private fun drawClassic(canvas: Canvas, result: Bitmap, stamp: StampData, mapThumbnail: Bitmap?, top: Boolean) {
    val width = result.width
    val height = result.height
    val scale = width / REFERENCE_WIDTH

    val pad = 22f * scale
    val lineSpacing = 10f * scale
    val shadow = 3f * scale

    val titlePaint = paint(34f * scale, Typeface.SANS_SERIF, Typeface.BOLD, shadow)
    val bodyPaint = paint(28f * scale, Typeface.SANS_SERIF, Typeface.NORMAL, shadow)
    val monoPaint = paint(28f * scale, Typeface.MONOSPACE, Typeface.NORMAL, shadow)
    val notePaint = paint(26f * scale, Typeface.SANS_SERIF, Typeface.ITALIC, shadow)

    val mapSize = if (mapThumbnail != null) MAP_SIZE * scale else 0f
    val mapGap = if (mapThumbnail != null) pad else 0f
    val maxTextWidth = width - 2 * pad - mapSize - mapGap

    val lines = mutableListOf<Pair<String, Paint>>()
    stamp.projectName?.takeIf { it.isNotBlank() }?.let { name ->
        wrapText(name, titlePaint, maxTextWidth).forEach { lines += it to titlePaint }
    }
    if (stamp.layoutPreset.showAddress) {
        stamp.address?.takeIf { it.isNotBlank() }?.let { addr ->
            wrapText(addr, bodyPaint, maxTextWidth).forEach { lines += it to bodyPaint }
        }
    }
    if (stamp.layoutPreset.showCoords) coordinatesLine(stamp)?.let { lines += it to monoPaint }
    if (stamp.layoutPreset.showWeather) {
        stamp.weather?.takeIf { it.isNotBlank() }?.let { lines += it to bodyPaint }
    }
    // Altitude + compass facing (P2-US-013) — always rendered when present.
    stamp.altitudeFacing?.takeIf { it.isNotBlank() }?.let { lines += it to bodyPaint }
    // Date/time (P2-US-017) — only when the user kept it enabled.
    if (stamp.showDateTime) lines += dateLine(stamp) to bodyPaint
    stamp.note?.takeIf { it.isNotBlank() }?.let { note ->
        wrapText(note, notePaint, maxTextWidth).forEach { lines += it to notePaint }
    }

    if (lines.isEmpty() && mapThumbnail == null) return

    val textHeight = measureLines(lines, lineSpacing)
    val contentHeight = maxOf(textHeight, mapSize)
    val panelHeight = contentHeight + 2 * pad
    val panelTop = if (top) 0f else height - panelHeight

    canvas.drawRect(0f, panelTop, width.toFloat(), panelTop + panelHeight, panelFill(150))
    drawLines(canvas, lines, pad, panelTop + pad, lineSpacing)

    if (mapThumbnail != null) {
        val mapLeft = width - pad - mapSize
        val mapTop = panelTop + (panelHeight - mapSize) / 2f
        drawMap(canvas, mapThumbnail, RectF(mapLeft, mapTop, mapLeft + mapSize, mapTop + mapSize), scale)
    }
}

// --- Modern ----------------------------------------------------------------

/**
 * Clean no-map panel with a gold left accent bar: bold address, then coordinates,
 * weather, altitude/facing, date-time and note. A lighter, more contemporary take
 * than Classic — no map thumbnail.
 */
private fun drawModern(canvas: Canvas, result: Bitmap, stamp: StampData, top: Boolean) {
    val width = result.width
    val height = result.height
    val scale = width / REFERENCE_WIDTH

    val pad = 22f * scale
    val barWidth = 10f * scale
    val lineSpacing = 9f * scale
    val shadow = 3f * scale

    val titlePaint = paint(32f * scale, Typeface.SANS_SERIF, Typeface.BOLD, shadow)
    val bodyPaint = paint(27f * scale, Typeface.SANS_SERIF, Typeface.NORMAL, shadow)
    val monoPaint = paint(27f * scale, Typeface.MONOSPACE, Typeface.NORMAL, shadow)
    val notePaint = paint(25f * scale, Typeface.SANS_SERIF, Typeface.ITALIC, shadow)

    val textLeft = pad + barWidth + pad
    val maxTextWidth = width - textLeft - pad

    val lines = mutableListOf<Pair<String, Paint>>()
    stamp.projectName?.takeIf { it.isNotBlank() }?.let { name ->
        wrapText(name, titlePaint, maxTextWidth).forEach { lines += it to titlePaint }
    }
    if (stamp.layoutPreset.showAddress) {
        stamp.address?.takeIf { it.isNotBlank() }?.let { addr ->
            wrapText(addr, titlePaint, maxTextWidth).forEach { lines += it to titlePaint }
        }
    }
    if (stamp.layoutPreset.showCoords) coordinatesLine(stamp)?.let { lines += it to monoPaint }
    if (stamp.layoutPreset.showWeather) {
        stamp.weather?.takeIf { it.isNotBlank() }?.let { lines += it to bodyPaint }
    }
    stamp.altitudeFacing?.takeIf { it.isNotBlank() }?.let { lines += it to bodyPaint }
    if (stamp.showDateTime) lines += dateLine(stamp) to bodyPaint
    stamp.note?.takeIf { it.isNotBlank() }?.let { note ->
        wrapText(note, notePaint, maxTextWidth).forEach { lines += it to notePaint }
    }

    if (lines.isEmpty()) return

    val textHeight = measureLines(lines, lineSpacing)
    val panelHeight = textHeight + 2 * pad
    val panelTop = if (top) 0f else height - panelHeight

    canvas.drawRect(0f, panelTop, width.toFloat(), panelTop + panelHeight, panelFill(140))
    // Gold left accent bar.
    canvas.drawRect(pad, panelTop + pad, pad + barWidth, panelTop + panelHeight - pad,
        Paint().apply { color = MODERN_ACCENT })
    drawLines(canvas, lines, textLeft, panelTop + pad, lineSpacing)
}

// --- Report (Reporting / Advance / Custom) ---------------------------------

/**
 * Documentation-style layout shared by the Reporting, Advance and Custom templates:
 * an [accent]-coloured header band carrying [headerText], a larger map thumbnail on
 * the LEFT, and labelled rows on the right so it reads like a proof record. When
 * [forceAllFields] is true (Advance) the address / coordinates / weather rows are
 * always shown, ignoring the layout-preset suppression.
 */
private fun drawReport(
    canvas: Canvas,
    result: Bitmap,
    stamp: StampData,
    mapThumbnail: Bitmap?,
    top: Boolean,
    headerText: String,
    accent: Int,
    forceAllFields: Boolean,
) {
    val width = result.width
    val height = result.height
    val scale = width / REFERENCE_WIDTH

    val pad = 22f * scale
    val lineSpacing = 8f * scale
    val shadow = 3f * scale

    val headerPaint = paint(26f * scale, Typeface.SANS_SERIF, Typeface.BOLD, 0f).apply {
        color = Color.BLACK
        letterSpacing = 0.15f
    }
    val labelPaint = paint(20f * scale, Typeface.SANS_SERIF, Typeface.BOLD, shadow).apply {
        color = accent
        letterSpacing = 0.08f
    }
    val valuePaint = paint(26f * scale, Typeface.SANS_SERIF, Typeface.NORMAL, shadow)
    val monoPaint = paint(26f * scale, Typeface.MONOSPACE, Typeface.NORMAL, shadow)

    val hasHeader = headerText.isNotBlank()
    val headerHeight = if (hasHeader) headerPaint.descent() - headerPaint.ascent() + pad else 0f

    val mapSize = if (mapThumbnail != null) FIELD_MAP_SIZE * scale else 0f
    val mapGap = if (mapThumbnail != null) pad else 0f
    val textLeft = pad + mapSize + mapGap
    val maxTextWidth = width - textLeft - pad

    // Each row is an optional accent label followed by one or more value lines.
    val rows = mutableListOf<Pair<String, Paint>>()
    stamp.projectName?.takeIf { it.isNotBlank() }?.let { name ->
        rows += "PROJECT / SITE" to labelPaint
        wrapText(name, valuePaint, maxTextWidth).forEach { rows += it to valuePaint }
    }
    if (forceAllFields || stamp.layoutPreset.showAddress) {
        stamp.address?.takeIf { it.isNotBlank() }?.let { addr ->
            rows += "ADDRESS" to labelPaint
            wrapText(addr, valuePaint, maxTextWidth).forEach { rows += it to valuePaint }
        }
    }
    if (forceAllFields || stamp.layoutPreset.showCoords) {
        coordinatesLine(stamp)?.let {
            rows += "COORDINATES" to labelPaint
            rows += it to monoPaint
        }
    }
    if (forceAllFields || stamp.layoutPreset.showWeather) {
        stamp.weather?.takeIf { it.isNotBlank() }?.let {
            rows += "WEATHER" to labelPaint
            rows += it to valuePaint
        }
    }
    // Altitude + compass facing (P2-US-013) — always rendered when present.
    stamp.altitudeFacing?.takeIf { it.isNotBlank() }?.let {
        rows += "ALTITUDE / FACING" to labelPaint
        rows += it to valuePaint
    }
    // Date/time (P2-US-017) — only when the user kept it enabled.
    if (stamp.showDateTime) {
        rows += "DATE / TIME" to labelPaint
        rows += dateLine(stamp) to valuePaint
    }
    stamp.note?.takeIf { it.isNotBlank() }?.let { note ->
        rows += "NOTE" to labelPaint
        wrapText(note, valuePaint, maxTextWidth).forEach { rows += it to valuePaint }
    }

    val rowsHeight = measureLines(rows, lineSpacing)
    val bodyHeight = maxOf(rowsHeight, mapSize)
    val panelHeight = headerHeight + bodyHeight + 2 * pad
    val panelTop = if (top) 0f else height - panelHeight

    // Panel; coloured header band only when a header label is set (Work Report).
    canvas.drawRect(0f, panelTop, width.toFloat(), panelTop + panelHeight, panelFill(175))
    if (hasHeader) {
        canvas.drawRect(0f, panelTop, width.toFloat(), panelTop + headerHeight, Paint().apply { color = accent })
        canvas.drawText(headerText, pad, panelTop + pad / 2f - headerPaint.ascent(), headerPaint)
    }

    val bodyTop = panelTop + headerHeight + pad
    drawLines(canvas, rows, textLeft, bodyTop, lineSpacing)

    if (mapThumbnail != null) {
        val mapTop = bodyTop + (bodyHeight - mapSize) / 2f
        drawMap(canvas, mapThumbnail, RectF(pad, mapTop, pad + mapSize, mapTop + mapSize), scale)
    }
}

// --- Shared helpers --------------------------------------------------------

private fun paint(size: Float, family: Typeface, style: Int, shadow: Float): Paint =
    Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = size
        typeface = Typeface.create(family, style)
        if (shadow > 0f) setShadowLayer(shadow, 0f, 0f, Color.BLACK)
    }

private fun panelFill(alpha: Int): Paint = Paint().apply { color = Color.argb(alpha, 0, 0, 0) }

/** Total height of [lines] including [lineSpacing] between them. */
private fun measureLines(lines: List<Pair<String, Paint>>, lineSpacing: Float): Float {
    var h = 0f
    lines.forEachIndexed { index, (_, paint) ->
        h += paint.descent() - paint.ascent()
        if (index < lines.size - 1) h += lineSpacing
    }
    return h
}

/** Draws each (text, paint) line stacked from [top], left-aligned at [left]. */
private fun drawLines(canvas: Canvas, lines: List<Pair<String, Paint>>, left: Float, top: Float, lineSpacing: Float) {
    var y = top
    lines.forEach { (text, paint) ->
        canvas.drawText(text, left, y - paint.ascent(), paint)
        y += (paint.descent() - paint.ascent()) + lineSpacing
    }
}

/** Draws [map] into [dst] (FILTER_BITMAP) with a white border. */
private fun drawMap(canvas: Canvas, map: Bitmap, dst: RectF, scale: Float) {
    canvas.drawBitmap(map, null, dst, Paint(Paint.FILTER_BITMAP_FLAG))
    val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2f * scale
        color = Color.WHITE
    }
    canvas.drawRect(dst, borderPaint)
}

/**
 * Draws the user's [logo] (US-010) into the top-right corner, fitted into a
 * LOGO_SIZE square (preserving aspect ratio) over a subtle rounded backing so it
 * stays visible regardless of the underlying photo. Independent of the template.
 */
private fun drawLogo(canvas: Canvas, result: Bitmap, logo: Bitmap) {
    val width = result.width
    val scale = width / REFERENCE_WIDTH
    val pad = 22f * scale
    val box = LOGO_SIZE * scale

    // Fit within the square box, preserving aspect ratio.
    val aspect = logo.width.toFloat() / logo.height.toFloat()
    val drawW = if (aspect >= 1f) box else box * aspect
    val drawH = if (aspect >= 1f) box / aspect else box

    val right = width - pad
    val left = right - drawW
    val top = pad
    val dst = RectF(left, top, left + drawW, top + drawH)

    val bgPad = 8f * scale
    val radius = 10f * scale
    canvas.drawRoundRect(
        RectF(dst.left - bgPad, dst.top - bgPad, dst.right + bgPad, dst.bottom + bgPad),
        radius, radius, panelFill(120),
    )
    canvas.drawBitmap(logo, null, dst, Paint(Paint.FILTER_BITMAP_FLAG))
}

/**
 * Draws the user's custom [watermark] text (P2-US-017) into the bottom-right corner
 * over a subtle rounded backing so it stays visible regardless of the underlying
 * photo. Independent of the template (the burned stamp panel may sit at the bottom
 * too — the backing keeps the watermark legible over it).
 */
private fun drawWatermark(canvas: Canvas, result: Bitmap, watermark: String) {
    val width = result.width
    val height = result.height
    val scale = width / REFERENCE_WIDTH
    val pad = 16f * scale

    val textPaint = paint(24f * scale, Typeface.SANS_SERIF, Typeface.BOLD, 3f * scale).apply {
        color = Color.argb(235, 255, 255, 255)
    }
    // Trim to the widest the photo can show so it never runs off-screen.
    val maxWidth = width - 2 * pad
    val text = watermark.takeIf { textPaint.measureText(it) <= maxWidth }
        ?: ellipsize(watermark, textPaint, maxWidth)

    val textWidth = textPaint.measureText(text)
    val textHeight = textPaint.descent() - textPaint.ascent()
    val right = width - pad
    val left = right - textWidth
    val bottom = height - pad
    val top = bottom - textHeight

    val bgPad = 8f * scale
    val radius = 8f * scale
    canvas.drawRoundRect(
        RectF(left - bgPad, top - bgPad, right + bgPad, bottom + bgPad),
        radius, radius, panelFill(120),
    )
    canvas.drawText(text, left, top - textPaint.ascent(), textPaint)
}

/** Truncates [text] with an ellipsis so it fits [maxWidth] under [paint]. */
private fun ellipsize(text: String, paint: Paint, maxWidth: Float): String {
    var end = text.length
    while (end > 0 && paint.measureText(text.substring(0, end) + "…") > maxWidth) end--
    return if (end <= 0) "…" else text.substring(0, end) + "…"
}

private fun coordinatesLine(stamp: StampData): String? {
    val lat = stamp.latitude ?: return null
    val lon = stamp.longitude ?: return null
    val base = stamp.coordinateFormat.format(lat, lon)
    val accuracy = stamp.accuracyMeters?.let { String.format(Locale.US, "  ±%d m", it.toInt()) } ?: ""
    return base + accuracy
}

private fun dateLine(stamp: StampData): String =
    SimpleDateFormat(stamp.timeFormat.datePattern, Locale.getDefault()).format(stamp.timestamp)

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

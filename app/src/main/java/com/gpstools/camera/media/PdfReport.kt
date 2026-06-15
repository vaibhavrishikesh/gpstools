package com.gpstools.camera.media

import android.content.ActivityNotFoundException
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private const val TAG = "PdfReport"

/** Free-tier reports are capped to this many photos and watermarked (US-017). */
const val FREE_REPORT_MAX_PHOTOS = 3

private const val REPORT_RELATIVE_PATH = "Download/gpstools"
private const val REPORT_FILENAME_PREFIX = "gpstools_report_"
private const val REPORT_DATE_PATTERN = "yyyyMMdd_HHmmss"

// A4 at 72 dpi, in PostScript points.
private const val PAGE_WIDTH = 595
private const val PAGE_HEIGHT = 842
private const val MARGIN = 36f

/** Outcome of a report export. [limited] is true when the free-tier cap dropped photos. */
data class PdfReportResult(
    val uri: Uri,
    val displayName: String,
    val pageCount: Int,
    val limited: Boolean,
)

/**
 * Renders [photos] into a single multi-page PDF (one photo per page) using the
 * framework [PdfDocument] — no external dependency. Each page carries a header
 * (project/site name + date range), the photo with its burned-in stamp scaled to
 * fit, a caption (filename, capture date, coordinates if known) and a footer.
 *
 * Free users (`!isPremium`) get at most [FREE_REPORT_MAX_PHOTOS] photos and a
 * diagonal watermark on every page; premium users get the full, watermark-free set.
 *
 * Runs synchronously and touches the content resolver — call off the main thread.
 * Returns null on failure (never throws); returns null for an empty selection.
 */
fun generatePhotoReport(
    context: Context,
    photos: List<CapturedPhoto>,
    projectName: String,
    isPremium: Boolean,
): PdfReportResult? {
    if (photos.isEmpty()) return null

    // Oldest first reads like a chronological log; free tier keeps the first N.
    val ordered = photos.sortedBy { it.dateAddedMillis }
    val limited = !isPremium && ordered.size > FREE_REPORT_MAX_PHOTOS
    val included = if (isPremium) ordered else ordered.take(FREE_REPORT_MAX_PHOTOS)
    val geotags = runCatching { GeotagStore.loadAll(context) }.getOrDefault(emptyMap())

    val title = projectName.trim().ifEmpty { "Photo report" }
    val dateRange = formatDateRange(included)

    val document = PdfDocument()
    try {
        included.forEachIndexed { index, photo ->
            val pageInfo = PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, index + 1).create()
            val page = document.startPage(pageInfo)
            val canvas = page.canvas
            val bitmap = decodeScaled(context, photo.uri, PAGE_WIDTH * 2, PAGE_HEIGHT * 2)
            drawPage(
                canvas = canvas,
                title = title,
                dateRange = dateRange,
                photo = photo,
                bitmap = bitmap,
                geotag = geotags[photo.displayName],
                pageNumber = index + 1,
                pageCount = included.size,
                watermark = !isPremium,
            )
            bitmap?.recycle()
            document.finishPage(page)
        }

        val name = REPORT_FILENAME_PREFIX +
            SimpleDateFormat(REPORT_DATE_PATTERN, Locale.US).format(Date()) + ".pdf"
        val uri = writeReport(context, document, name) ?: return null
        return PdfReportResult(uri, name, included.size, limited)
    } catch (e: Exception) {
        Log.e(TAG, "Failed to generate report", e)
        return null
    } finally {
        document.close()
    }
}

/** Opens the generated report in a PDF viewer via a chooser. Returns false if none can. */
fun openReport(context: Context, uri: Uri, chooserTitle: String): Boolean {
    val view = Intent(Intent.ACTION_VIEW).apply {
        setDataAndType(uri, "application/pdf")
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    val chooser = Intent.createChooser(view, chooserTitle).apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    return try {
        context.startActivity(chooser)
        true
    } catch (e: ActivityNotFoundException) {
        Log.e(TAG, "No PDF viewer available", e)
        false
    }
}

private fun drawPage(
    canvas: Canvas,
    title: String,
    dateRange: String,
    photo: CapturedPhoto,
    bitmap: Bitmap?,
    geotag: PhotoGeotag?,
    pageNumber: Int,
    pageCount: Int,
    watermark: Boolean,
) {
    val left = MARGIN
    val right = PAGE_WIDTH - MARGIN

    val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.BLACK
        textSize = 18f
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    }
    val subPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.DKGRAY
        textSize = 10f
    }
    val captionPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.BLACK
        textSize = 9f
    }
    val linePaint = Paint().apply { color = Color.LTGRAY; strokeWidth = 1f }

    // Header.
    var y = MARGIN + 16f
    canvas.drawText(title, left, y, titlePaint)
    y += 14f
    canvas.drawText(dateRange, left, y, subPaint)
    y += 8f
    canvas.drawLine(left, y, right, y, linePaint)

    // Footer (drawn first so we know the photo's bottom bound).
    val footerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.GRAY
        textSize = 8f
    }
    val footer = "Generated by gpstools  ·  Page $pageNumber of $pageCount"
    canvas.drawText(footer, left, PAGE_HEIGHT - MARGIN + 8f, footerPaint)

    // Caption block, bottom-up, above the footer.
    val captionLines = buildList {
        add(photo.displayName)
        add("Captured ${formatDateTime(photo.dateAddedMillis)}")
        if (geotag != null) {
            add(String.format(Locale.US, "%.6f, %.6f", geotag.latitude, geotag.longitude))
        }
    }
    val captionLineHeight = 12f
    val captionTop = PAGE_HEIGHT - MARGIN - 6f - captionLines.size * captionLineHeight
    captionLines.forEachIndexed { i, line ->
        canvas.drawText(line, left, captionTop + (i + 1) * captionLineHeight, captionPaint)
    }

    // Photo, scaled to fit the area between the header and the caption.
    val areaTop = y + 12f
    val areaBottom = captionTop - 10f
    if (bitmap != null && areaBottom > areaTop) {
        val areaWidth = right - left
        val areaHeight = areaBottom - areaTop
        val scale = minOf(areaWidth / bitmap.width, areaHeight / bitmap.height)
        val drawW = bitmap.width * scale
        val drawH = bitmap.height * scale
        val dx = left + (areaWidth - drawW) / 2f
        val dy = areaTop + (areaHeight - drawH) / 2f
        val dest = Rect(dx.toInt(), dy.toInt(), (dx + drawW).toInt(), (dy + drawH).toInt())
        canvas.drawBitmap(bitmap, null, dest, Paint(Paint.FILTER_BITMAP_FLAG))
    }

    if (watermark) drawWatermark(canvas)
}

/** Diagonal translucent watermark across the page for free-tier reports. */
private fun drawWatermark(canvas: Canvas) {
    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(48, 0, 0, 0)
        textSize = 64f
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        textAlign = Paint.Align.CENTER
    }
    canvas.save()
    canvas.rotate(-30f, PAGE_WIDTH / 2f, PAGE_HEIGHT / 2f)
    canvas.drawText("gpstools", PAGE_WIDTH / 2f, PAGE_HEIGHT / 2f, paint)
    canvas.restore()
}

/**
 * Decodes [uri] downsampled so the result is roughly [maxW]×[maxH] — keeps a
 * full-page PDF from holding several full-resolution bitmaps at once. Returns null
 * if the image can't be read.
 */
private fun decodeScaled(context: Context, uri: Uri, maxW: Int, maxH: Int): Bitmap? = try {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    context.contentResolver.openInputStream(uri)?.use {
        BitmapFactory.decodeStream(it, null, bounds)
    }
    var sample = 1
    while (bounds.outWidth / sample > maxW || bounds.outHeight / sample > maxH) {
        sample *= 2
    }
    val opts = BitmapFactory.Options().apply { inSampleSize = sample }
    context.contentResolver.openInputStream(uri)?.use {
        BitmapFactory.decodeStream(it, null, opts)
    }
} catch (e: Exception) {
    Log.e(TAG, "Failed to decode $uri", e)
    null
}

private fun formatDateRange(photos: List<CapturedPhoto>): String {
    if (photos.isEmpty()) return ""
    val fmt = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())
    val first = fmt.format(Date(photos.minOf { it.dateAddedMillis }))
    val last = fmt.format(Date(photos.maxOf { it.dateAddedMillis }))
    return if (first == last) first else "$first – $last"
}

private fun formatDateTime(millis: Long): String =
    SimpleDateFormat("dd MMM yyyy HH:mm", Locale.getDefault()).format(Date(millis))

/**
 * Writes the document into the shared Downloads/gpstools collection. On API 29+ uses
 * MediaStore Downloads (RELATIVE_PATH + IS_PENDING, no permission); on API 24–28
 * inserts a MediaStore.Files row pointing at a public Downloads path (needs the
 * maxSdk=28 WRITE_EXTERNAL_STORAGE permission already declared). Either way the
 * returned content Uri is shareable/viewable. Returns null and rolls back on failure.
 */
private fun writeReport(context: Context, document: PdfDocument, name: String): Uri? {
    val resolver = context.contentResolver
    val values = ContentValues().apply {
        put(MediaStore.MediaColumns.DISPLAY_NAME, name)
        put(MediaStore.MediaColumns.MIME_TYPE, "application/pdf")
    }
    val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        values.put(MediaStore.MediaColumns.RELATIVE_PATH, REPORT_RELATIVE_PATH)
        values.put(MediaStore.MediaColumns.IS_PENDING, 1)
        MediaStore.Downloads.EXTERNAL_CONTENT_URI
    } else {
        val dir = File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
            "gpstools",
        )
        if (!dir.exists()) dir.mkdirs()
        @Suppress("DEPRECATION")
        values.put(MediaStore.MediaColumns.DATA, File(dir, name).absolutePath)
        MediaStore.Files.getContentUri("external")
    }

    val uri = resolver.insert(collection, values) ?: return null
    return try {
        resolver.openOutputStream(uri)?.use { out -> document.writeTo(out) }
            ?: error("openOutputStream returned null for $uri")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            values.clear()
            values.put(MediaStore.MediaColumns.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
        }
        Log.d(TAG, "Report saved to $uri")
        uri
    } catch (e: Exception) {
        Log.e(TAG, "Failed to write report; rolling back", e)
        runCatching { resolver.delete(uri, null, null) }
        null
    }
}

package com.gpstools.camera.media

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private const val TAG = "PhotoCollage"

/** A grid/collage combines this many photos (inclusive) into one image (US-016). */
const val COLLAGE_MIN_PHOTOS = 2
const val COLLAGE_MAX_PHOTOS = 4

private const val COLLAGE_FILENAME_PREFIX = "GPS_collage_"
private const val COLLAGE_DATE_PATTERN = "yyyyMMdd_HHmmss"

// Output canvas + layout metrics, in pixels.
private const val COLLAGE_WIDTH = 1200
private const val COLLAGE_GAP = 12
private const val COLLAGE_COLUMNS = 2
private val COLLAGE_BG = Color.WHITE

/**
 * Combines [photos] (2–4) into a single grid/collage image and saves it to the shared
 * MediaStore Pictures/gpstools collection (so it also appears in the in-app gallery).
 *
 * Layout: 2 columns, rows = ceil(n/2) — so 2 photos sit side-by-side and 4 photos form
 * a 2×2 grid (3 photos = 2 + 1). Each photo is drawn fit-inside its square cell (never
 * cropped) on a white background, so every photo's burned-in stamp is preserved.
 *
 * Runs synchronously, decodes bitmaps and touches the content resolver — call off the
 * main thread. Returns the saved content [Uri], or null on failure / a bad selection
 * size (never throws).
 */
fun createPhotoCollage(context: Context, photos: List<CapturedPhoto>): Uri? {
    if (photos.size < COLLAGE_MIN_PHOTOS || photos.size > COLLAGE_MAX_PHOTOS) return null

    val n = photos.size
    val cols = COLLAGE_COLUMNS
    val rows = (n + cols - 1) / cols
    val cell = (COLLAGE_WIDTH - COLLAGE_GAP * (cols + 1)) / cols
    val height = COLLAGE_GAP * (rows + 1) + cell * rows

    val output = Bitmap.createBitmap(COLLAGE_WIDTH, height, Bitmap.Config.ARGB_8888)
    return try {
        val canvas = Canvas(output)
        canvas.drawColor(COLLAGE_BG)
        val paint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)
        photos.forEachIndexed { i, photo ->
            val row = i / cols
            val col = i % cols
            val cellLeft = COLLAGE_GAP + col * (cell + COLLAGE_GAP)
            val cellTop = COLLAGE_GAP + row * (cell + COLLAGE_GAP)
            // Downsample to ~2× the cell so the collage isn't holding full-res bitmaps.
            val bmp = decodeScaledToMax(context, photo.uri, cell * 2) ?: return@forEachIndexed
            // Fit-inside (contain) the square cell so the stamp is never cropped off.
            val scale = minOf(cell.toFloat() / bmp.width, cell.toFloat() / bmp.height)
            val drawW = (bmp.width * scale).toInt()
            val drawH = (bmp.height * scale).toInt()
            val dx = cellLeft + (cell - drawW) / 2
            val dy = cellTop + (cell - drawH) / 2
            canvas.drawBitmap(bmp, null, Rect(dx, dy, dx + drawW, dy + drawH), paint)
            bmp.recycle()
        }
        val name = COLLAGE_FILENAME_PREFIX +
            SimpleDateFormat(COLLAGE_DATE_PATTERN, Locale.US).format(Date()) + ".jpg"
        saveCollage(context, output, name)
    } catch (e: Exception) {
        Log.e(TAG, "Failed to create collage", e)
        null
    } finally {
        output.recycle()
    }
}

/** Fires a system share sheet for a saved image [uri] (read permission granted). */
fun shareImage(context: Context, uri: Uri, chooserTitle: String) {
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "image/jpeg"
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    val chooser = Intent.createChooser(send, chooserTitle).apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    context.startActivity(chooser)
}

/**
 * Decodes [uri] downsampled so its largest dimension is roughly [maxDimension] px —
 * keeps the collage from holding several full-resolution bitmaps at once. Returns null
 * if the image can't be read.
 */
private fun decodeScaledToMax(context: Context, uri: Uri, maxDimension: Int): Bitmap? = try {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    context.contentResolver.openInputStream(uri)?.use {
        BitmapFactory.decodeStream(it, null, bounds)
    }
    var sample = 1
    while (bounds.outWidth / sample > maxDimension || bounds.outHeight / sample > maxDimension) {
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

/**
 * Writes the collage [bitmap] as a JPEG into the shared MediaStore Pictures/gpstools
 * collection (same scoped-storage pattern as [saveBitmap]). Keeps the GPS_ filename
 * prefix + folder so the in-app gallery query still finds it. Returns the content Uri,
 * or null (rolling back the row) on failure.
 */
private fun saveCollage(context: Context, bitmap: Bitmap, name: String): Uri? {
    val resolver = context.contentResolver
    val values = ContentValues().apply {
        put(MediaStore.MediaColumns.DISPLAY_NAME, name)
        put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            put(MediaStore.MediaColumns.RELATIVE_PATH, CAPTURE_RELATIVE_PATH)
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        } else {
            val dir = File(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES),
                "gpstools",
            )
            if (!dir.exists()) dir.mkdirs()
            @Suppress("DEPRECATION")
            put(MediaStore.MediaColumns.DATA, File(dir, name).absolutePath)
        }
    }

    val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values) ?: return null
    return try {
        resolver.openOutputStream(uri)?.use { out ->
            check(bitmap.compress(Bitmap.CompressFormat.JPEG, 95, out)) { "JPEG compression failed" }
        } ?: error("openOutputStream returned null for $uri")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            values.clear()
            values.put(MediaStore.MediaColumns.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
        }
        Log.d(TAG, "Collage saved to $uri")
        uri
    } catch (e: Exception) {
        Log.e(TAG, "Failed to write collage; rolling back", e)
        runCatching { resolver.delete(uri, null, null) }
        null
    }
}

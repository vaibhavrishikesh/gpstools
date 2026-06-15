package com.gpstools.camera.media

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.core.content.ContextCompat
import java.io.File
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.concurrent.Executors

private const val TAG = "PhotoStorage"

/** Sub-folder under the shared Pictures/ collection where captures are stored. */
const val CAPTURE_RELATIVE_PATH = "Pictures/gpstools"

/** Filename prefix + pattern for captured photos, e.g. GPS_20260615_103045123.jpg. */
private const val FILENAME_PREFIX = "GPS_"
private const val FILENAME_DATE_PATTERN = "yyyyMMdd_HHmmssSSS"
private const val JPEG_QUALITY = 95

/**
 * Single background thread for the decode → stamp → encode work so the main thread
 * never blocks on a full-resolution bitmap. CameraX delivers the capture callback
 * on this executor; results are posted back to the main thread for the caller.
 */
private val ioExecutor = Executors.newSingleThreadExecutor()

/**
 * Captures a full-resolution photo via CameraX [ImageCapture], burns the location
 * [stamp] onto it (US-007), and saves the result as a JPEG into the shared
 * MediaStore Pictures/gpstools collection. [onResult] is invoked on the main thread
 * with the saved content [Uri] on success, or null on failure (never throws).
 *
 * Unlike a direct file capture, we take the image in-memory so we can composite the
 * stamp before encoding: decode the JPEG, rotate it upright per the capture
 * metadata (so portrait and landscape shots both stamp correctly), draw the stamp,
 * then re-encode. EXIF from the original frame is not carried over (the proof data
 * is burned into the pixels instead).
 */
fun capturePhoto(
    context: Context,
    imageCapture: ImageCapture,
    stamp: StampData,
    template: StampTemplate = StampTemplate.DEFAULT,
    mapProvider: StaticMapProvider = OsmStaticMapProvider(),
    onResult: (Uri?) -> Unit,
) {
    val appContext = context.applicationContext
    imageCapture.takePicture(
        ioExecutor,
        object : ImageCapture.OnImageCapturedCallback() {
            override fun onCaptureSuccess(image: ImageProxy) {
                val uri = try {
                    val upright = image.toUprightBitmap()
                    // Fetch the map thumbnail on this background thread, but only for
                    // templates that render one; null (offline / no fix) just means the
                    // stamp falls back to text-only (US-008).
                    val mapThumbnail = if (template.usesMap &&
                        stamp.latitude != null && stamp.longitude != null
                    ) {
                        mapProvider.fetchMapThumbnail(stamp.latitude, stamp.longitude)
                    } else {
                        null
                    }
                    val stamped = drawStamp(upright, stamp, template, mapThumbnail)
                    mapThumbnail?.recycle()
                    saveBitmap(appContext, stamped, stamp)
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to stamp/save capture", e)
                    null
                } finally {
                    image.close()
                }
                postResult(appContext, uri, onResult)
            }

            override fun onError(exception: ImageCaptureException) {
                Log.e(TAG, "Capture failed", exception)
                postResult(appContext, null, onResult)
            }
        },
    )
}

private fun postResult(context: Context, uri: Uri?, onResult: (Uri?) -> Unit) {
    ContextCompat.getMainExecutor(context).execute { onResult(uri) }
}

/** Decodes the in-memory JPEG and rotates it upright per the capture metadata. */
private fun ImageProxy.toUprightBitmap(): Bitmap {
    val buffer = planes[0].buffer
    val bytes = ByteArray(buffer.remaining())
    buffer.get(bytes)
    val decoded = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        ?: throw IllegalStateException("Failed to decode captured JPEG")
    val rotation = imageInfo.rotationDegrees
    if (rotation == 0) return decoded
    val matrix = Matrix().apply { postRotate(rotation.toFloat()) }
    val rotated = Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, matrix, true)
    if (rotated != decoded) decoded.recycle()
    return rotated
}

/**
 * Writes [bitmap] as a JPEG into the shared MediaStore Pictures/gpstools collection.
 * On API 29+ it uses RELATIVE_PATH + IS_PENDING (scoped storage, no permission); on
 * API 24–28 it writes via an absolute DATA path under public Pictures (needs the
 * maxSdkVersion=28 WRITE_EXTERNAL_STORAGE permission declared in the manifest).
 * Keeps the GPS_ prefix + folder so the in-app gallery (US-011) can still find it.
 */
private fun saveBitmap(context: Context, bitmap: Bitmap, stamp: StampData): Uri? {
    val resolver = context.contentResolver
    val timestamp = SimpleDateFormat(FILENAME_DATE_PATTERN, Locale.US).format(stamp.timestamp)
    val name = "$FILENAME_PREFIX$timestamp.jpg"
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
            check(bitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, out)) {
                "JPEG compression failed"
            }
        } ?: error("openOutputStream returned null for $uri")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            values.clear()
            values.put(MediaStore.MediaColumns.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
        }
        Log.d(TAG, "Stamped capture saved to $uri")
        uri
    } catch (e: Exception) {
        Log.e(TAG, "Failed to write stamped capture; rolling back", e)
        runCatching { resolver.delete(uri, null, null) }
        null
    }
}

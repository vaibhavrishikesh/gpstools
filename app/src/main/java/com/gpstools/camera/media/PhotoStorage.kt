package com.gpstools.camera.media

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.util.Log
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.core.content.ContextCompat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private const val TAG = "PhotoStorage"

/** Sub-folder under the shared Pictures/ collection where captures are stored. */
const val CAPTURE_RELATIVE_PATH = "Pictures/gpstools"

/** Filename prefix + pattern for captured photos, e.g. GPS_20260615_103045123.jpg. */
private const val FILENAME_PREFIX = "GPS_"
private const val FILENAME_DATE_PATTERN = "yyyyMMdd_HHmmssSSS"

/**
 * Builds [ImageCapture.OutputFileOptions] that write a new JPEG into the shared
 * MediaStore Pictures/gpstools collection. On API 29+ the [MediaStore.Images.Media.RELATIVE_PATH]
 * places it in our sub-folder under scoped storage (no storage permission needed);
 * on API 24–28 it lands in the public Pictures collection (requires the
 * maxSdkVersion=28 WRITE_EXTERNAL_STORAGE permission declared in the manifest).
 * Registering through MediaStore means each capture is immediately visible to the
 * system gallery and queryable by our in-app gallery (US-011).
 */
private fun buildOutputOptions(context: Context): ImageCapture.OutputFileOptions {
    val timestamp = SimpleDateFormat(FILENAME_DATE_PATTERN, Locale.US).format(Date())
    val name = "$FILENAME_PREFIX$timestamp.jpg"
    val values = ContentValues().apply {
        put(MediaStore.MediaColumns.DISPLAY_NAME, name)
        put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            put(MediaStore.MediaColumns.RELATIVE_PATH, CAPTURE_RELATIVE_PATH)
        }
    }
    return ImageCapture.OutputFileOptions.Builder(
        context.contentResolver,
        MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
        values,
    ).build()
}

/**
 * Captures a full-resolution photo via CameraX [ImageCapture] and saves it to the
 * shared MediaStore collection. [onResult] is invoked on the main thread with the
 * saved content [Uri] on success, or null on failure (never throws to the caller).
 */
fun capturePhoto(
    context: Context,
    imageCapture: ImageCapture,
    onResult: (Uri?) -> Unit,
) {
    val outputOptions = buildOutputOptions(context)
    imageCapture.takePicture(
        outputOptions,
        ContextCompat.getMainExecutor(context),
        object : ImageCapture.OnImageSavedCallback {
            override fun onImageSaved(results: ImageCapture.OutputFileResults) {
                Log.d(TAG, "Capture saved to ${results.savedUri}")
                onResult(results.savedUri)
            }

            override fun onError(exception: ImageCaptureException) {
                Log.e(TAG, "Capture failed", exception)
                onResult(null)
            }
        },
    )
}

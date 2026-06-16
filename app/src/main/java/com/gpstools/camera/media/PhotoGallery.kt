package com.gpstools.camera.media

import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.util.Log

private const val TAG = "PhotoGallery"

/**
 * A single captured photo discovered in the shared MediaStore. The location/time
 * metadata is burned into the pixels at capture time (US-007), so the gallery only
 * carries the file-level metadata MediaStore exposes.
 */
data class CapturedPhoto(
    val uri: Uri,
    val displayName: String,
    /** Capture time in epoch millis (from MediaStore DATE_ADDED, stored in seconds). */
    val dateAddedMillis: Long,
    val sizeBytes: Long,
    val width: Int,
    val height: Int,
)

/**
 * Queries the captures this app wrote (GPS_*.jpg under Pictures/gpstools), newest
 * first. Run off the main thread — it touches the content resolver. Returns an empty
 * list (never throws) when nothing matches or the query fails.
 */
fun queryCapturedPhotos(context: Context): List<CapturedPhoto> {
    val collection = MediaStore.Images.Media.EXTERNAL_CONTENT_URI
    val projection = arrayOf(
        MediaStore.Images.Media._ID,
        MediaStore.Images.Media.DISPLAY_NAME,
        MediaStore.Images.Media.DATE_ADDED,
        MediaStore.Images.Media.SIZE,
        MediaStore.Images.Media.WIDTH,
        MediaStore.Images.Media.HEIGHT,
    )
    // Restrict to our captures: the GPS_ filename prefix, and on Q+ also our folder.
    val (selection, args) = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        "${MediaStore.Images.Media.RELATIVE_PATH} LIKE ? AND " +
            "${MediaStore.Images.Media.DISPLAY_NAME} LIKE ?" to
            arrayOf("$CAPTURE_RELATIVE_PATH%", "$CAPTURE_FILENAME_PREFIX%")
    } else {
        "${MediaStore.Images.Media.DISPLAY_NAME} LIKE ?" to
            arrayOf("$CAPTURE_FILENAME_PREFIX%")
    }
    val sortOrder = "${MediaStore.Images.Media.DATE_ADDED} DESC"

    val photos = mutableListOf<CapturedPhoto>()
    try {
        context.contentResolver.query(collection, projection, selection, args, sortOrder)
            ?.use { cursor ->
                val idCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
                val nameCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME)
                val dateCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_ADDED)
                val sizeCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.SIZE)
                val widthCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.WIDTH)
                val heightCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.HEIGHT)
                while (cursor.moveToNext()) {
                    val id = cursor.getLong(idCol)
                    photos += CapturedPhoto(
                        uri = ContentUris.withAppendedId(collection, id),
                        displayName = cursor.getString(nameCol) ?: "",
                        dateAddedMillis = cursor.getLong(dateCol) * 1000L,
                        sizeBytes = cursor.getLong(sizeCol),
                        width = cursor.getInt(widthCol),
                        height = cursor.getInt(heightCol),
                    )
                }
            }
    } catch (e: Exception) {
        Log.e(TAG, "Failed to query captured photos", e)
    }
    return photos
}

/**
 * Deletes a captured photo from MediaStore. The captures are owned by this app, so a
 * direct delete works without the scoped-storage delete-request flow. Returns true if
 * a row was removed.
 */
fun deleteCapturedPhoto(context: Context, uri: Uri): Boolean = try {
    context.contentResolver.delete(uri, null, null) > 0
} catch (e: Exception) {
    Log.e(TAG, "Failed to delete $uri", e)
    false
}

/**
 * Opens the most recently captured photo in a full-screen system image viewer
 * (P2-US-018 swipe-left "quick look" gesture). Queries newest-first off the caller's
 * thread (touches the content resolver — call from a background dispatcher), then fires
 * an ACTION_VIEW chooser. Returns false when there are no captures yet (caller can
 * toast) or the launch fails — never throws.
 */
fun openLastPhoto(context: Context): Boolean {
    val latest = queryCapturedPhotos(context).firstOrNull() ?: return false
    return try {
        val view = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(latest.uri, "image/jpeg")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(view)
        true
    } catch (e: Exception) {
        Log.e(TAG, "Failed to open last photo", e)
        false
    }
}

/** Fires a system share sheet for the given photo (read permission granted to the target). */
fun sharePhoto(context: Context, photo: CapturedPhoto, chooserTitle: String) {
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "image/jpeg"
        putExtra(Intent.EXTRA_STREAM, photo.uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    val chooser = Intent.createChooser(send, chooserTitle).apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    context.startActivity(chooser)
}

package com.gpstools.camera.media

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Log
import java.io.File

private const val TAG = "CustomFields"

/** Largest dimension a picked logo is downscaled to before it's stored. */
private const val LOGO_MAX_DIMENSION = 512

/**
 * User-supplied custom fields burned onto the stamp (US-010): a project/site name,
 * a free-text note, and whether a logo image has been picked. All persist between
 * captures via [CustomFieldsStore]. The logo bitmap itself lives in app storage
 * (see [CustomFieldsStore.logoFileOrNull]); [hasLogo] just reflects its presence.
 */
data class CustomFields(
    val projectName: String = "",
    val note: String = "",
    /**
     * Custom watermark text (e.g. a company name) burned bottom-right of the stamp
     * (P2-US-017). Blank = no watermark. Persisted between captures like the other
     * fields.
     */
    val watermark: String = "",
    val hasLogo: Boolean = false,
)

private const val PREFS_NAME = "custom_fields_prefs"
private const val KEY_PROJECT = "project_name"
private const val KEY_NOTE = "note"
private const val KEY_WATERMARK = "watermark"
private const val LOGO_FILENAME = "custom_logo.png"

/**
 * SharedPreferences-backed store for the stamp's custom fields (US-010). The
 * project name + note are plain strings; the logo is copied (downscaled) into app
 * internal storage so it survives across launches without holding a content-URI
 * permission. No new dependency required.
 */
class CustomFieldsStore(context: Context) {
    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val logoFile = File(appContext.filesDir, LOGO_FILENAME)

    fun load(): CustomFields = CustomFields(
        projectName = prefs.getString(KEY_PROJECT, "").orEmpty(),
        note = prefs.getString(KEY_NOTE, "").orEmpty(),
        watermark = prefs.getString(KEY_WATERMARK, "").orEmpty(),
        hasLogo = logoFile.exists(),
    )

    fun saveFields(projectName: String, note: String, watermark: String) {
        prefs.edit()
            .putString(KEY_PROJECT, projectName.trim())
            .putString(KEY_NOTE, note.trim())
            .putString(KEY_WATERMARK, watermark.trim())
            .apply()
    }

    /**
     * Copies the picked logo at [uri] into app storage, downscaling to
     * [LOGO_MAX_DIMENSION]. Returns true on success; on failure the previous logo
     * (if any) is left untouched and false is returned.
     */
    fun saveLogo(uri: Uri): Boolean = try {
        val bitmap = appContext.contentResolver.openInputStream(uri)?.use { input ->
            BitmapFactory.decodeStream(input)
        } ?: throw IllegalStateException("Could not open logo stream for $uri")
        val scaled = bitmap.scaledTo(LOGO_MAX_DIMENSION)
        logoFile.outputStream().use { out ->
            check(scaled.compress(Bitmap.CompressFormat.PNG, 100, out)) { "PNG compress failed" }
        }
        if (scaled != bitmap) scaled.recycle()
        bitmap.recycle()
        true
    } catch (e: Exception) {
        Log.e(TAG, "Failed to save picked logo", e)
        false
    }

    fun clearLogo() {
        if (logoFile.exists()) logoFile.delete()
    }

    /** The stored logo file, or null if no logo has been picked. */
    fun logoFileOrNull(): File? = logoFile.takeIf { it.exists() }
}

/** Returns a copy of this bitmap scaled so its largest side is at most [maxDimension]. */
private fun Bitmap.scaledTo(maxDimension: Int): Bitmap {
    val largest = maxOf(width, height)
    if (largest <= maxDimension) return this
    val ratio = maxDimension.toFloat() / largest
    return Bitmap.createScaledBitmap(this, (width * ratio).toInt(), (height * ratio).toInt(), true)
}

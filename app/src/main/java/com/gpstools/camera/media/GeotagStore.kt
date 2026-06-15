package com.gpstools.camera.media

import android.content.Context

/** A captured photo's recorded coordinates. */
data class PhotoGeotag(val latitude: Double, val longitude: Double)

/**
 * Persists each capture's coordinates, keyed by its MediaStore display name.
 *
 * The stamp burns lat/long into the pixels (US-007), and the decode → re-encode
 * needed to draw it drops the source EXIF — so the saved file carries no
 * machine-readable location. The map view (US-012) needs real coordinates to drop
 * pins, so we record them here at capture time. A plain SharedPreferences entry per
 * photo keeps this dependency-free and consistent with the other small stores in
 * this module (see [StampTemplateStore], [CustomFieldsStore]).
 */
object GeotagStore {
    private const val PREFS = "photo_geotags"

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** Records the coordinates for [displayName]. No-op if either value is null. */
    fun record(context: Context, displayName: String, latitude: Double?, longitude: Double?) {
        if (latitude == null || longitude == null) return
        prefs(context).edit().putString(displayName, "$latitude,$longitude").apply()
    }

    /** Returns all stored geotags as displayName -> [PhotoGeotag]. */
    fun loadAll(context: Context): Map<String, PhotoGeotag> {
        val out = HashMap<String, PhotoGeotag>()
        for ((key, value) in prefs(context).all) {
            val parts = (value as? String)?.split(",") ?: continue
            if (parts.size != 2) continue
            val lat = parts[0].toDoubleOrNull() ?: continue
            val lon = parts[1].toDoubleOrNull() ?: continue
            out[key] = PhotoGeotag(lat, lon)
        }
        return out
    }

    /**
     * Drops geotags whose photo no longer exists, so deleted captures don't linger
     * as phantom pins. [keep] is the set of display names still present in MediaStore.
     */
    fun retainOnly(context: Context, keep: Set<String>) {
        val p = prefs(context)
        val stale = p.all.keys.filterNot { it in keep }
        if (stale.isEmpty()) return
        p.edit().apply { stale.forEach { remove(it) } }.apply()
    }
}

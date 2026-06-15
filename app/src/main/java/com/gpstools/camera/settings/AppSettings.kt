package com.gpstools.camera.settings

import android.content.Context
import androidx.annotation.StringRes
import com.gpstools.camera.R
import java.util.Locale
import kotlin.math.abs

/**
 * How latitude/longitude is rendered on the stamp (US-014). [DECIMAL] keeps the
 * signed 6-decimal form; [DMS] converts to degrees/minutes/seconds with a
 * hemisphere letter. The chosen format is snapshot into [com.gpstools.camera.media.StampData]
 * at capture time so it's burned into the saved photo.
 */
enum class CoordinateFormat(@StringRes val labelRes: Int) {
    DECIMAL(R.string.settings_coord_decimal),
    DMS(R.string.settings_coord_dms);

    /** Formats [lat]/[lon] for the stamp (no accuracy — the caller appends that). */
    fun format(lat: Double, lon: Double): String = when (this) {
        DECIMAL -> String.format(Locale.US, "%.6f, %.6f", lat, lon)
        DMS -> "${dms(lat, isLatitude = true)}  ${dms(lon, isLatitude = false)}"
    }

    private fun dms(value: Double, isLatitude: Boolean): String {
        val hemisphere = when {
            isLatitude -> if (value >= 0) "N" else "S"
            else -> if (value >= 0) "E" else "W"
        }
        val absolute = abs(value)
        val degrees = absolute.toInt()
        val minutesFull = (absolute - degrees) * 60.0
        val minutes = minutesFull.toInt()
        val seconds = (minutesFull - minutes) * 60.0
        return String.format(Locale.US, "%d°%02d'%04.1f\"%s", degrees, minutes, seconds, hemisphere)
    }

    companion object {
        val DEFAULT = DECIMAL

        fun fromName(name: String?): CoordinateFormat =
            entries.firstOrNull { it.name == name } ?: DEFAULT
    }
}

/**
 * Whether the stamp's time component uses a 24-hour or 12-hour clock (US-014).
 * The date portion is unchanged; [datePattern] is the full SimpleDateFormat pattern.
 */
enum class TimeFormat(@StringRes val labelRes: Int, val datePattern: String) {
    HOUR_24(R.string.settings_time_24, "dd MMM yyyy  HH:mm:ss z"),
    HOUR_12(R.string.settings_time_12, "dd MMM yyyy  hh:mm:ss a z");

    companion object {
        val DEFAULT = HOUR_24

        fun fromName(name: String?): TimeFormat =
            entries.firstOrNull { it.name == name } ?: DEFAULT
    }
}

/**
 * Persists the formatting preferences (coordinate + time format) used by the
 * stamp. Same lightweight SharedPreferences pattern as [com.gpstools.camera.locale.LocaleStore]
 * and the other stores — no DataStore dependency.
 */
object AppSettingsStore {
    private const val PREFS = "app_settings"
    private const val KEY_COORD_FORMAT = "coordinate_format"
    private const val KEY_TIME_FORMAT = "time_format"

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun loadCoordinateFormat(context: Context): CoordinateFormat =
        CoordinateFormat.fromName(prefs(context).getString(KEY_COORD_FORMAT, null))

    fun saveCoordinateFormat(context: Context, format: CoordinateFormat) {
        prefs(context).edit().putString(KEY_COORD_FORMAT, format.name).apply()
    }

    fun loadTimeFormat(context: Context): TimeFormat =
        TimeFormat.fromName(prefs(context).getString(KEY_TIME_FORMAT, null))

    fun saveTimeFormat(context: Context, format: TimeFormat) {
        prefs(context).edit().putString(KEY_TIME_FORMAT, format.name).apply()
    }
}

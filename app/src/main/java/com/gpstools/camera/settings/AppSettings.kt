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
 * Which location fields the stamp renders (P2-US-010). Each preset is a combination
 * of map thumbnail / street address / raw lat-long / weather; the user picks one in
 * Settings and it's snapshot into [com.gpstools.camera.media.StampData] at capture
 * time so the chosen field set is burned into the saved photo. Project/site name,
 * note, logo and date-time are independent of the preset (they always render when
 * set). The map flag still composes with [com.gpstools.camera.media.StampTemplate.usesMap]
 * — a template that never draws a map (Minimal) won't grow one just because a preset
 * asks for it.
 */
enum class LayoutPreset(
    @StringRes val labelRes: Int,
    val showMap: Boolean,
    val showAddress: Boolean,
    val showCoords: Boolean,
    val showWeather: Boolean,
) {
    MAP_ADDRESS_WEATHER(R.string.preset_map_address_weather, showMap = true, showAddress = true, showCoords = false, showWeather = true),
    MAP_LATLNG_WEATHER(R.string.preset_map_latlng_weather, showMap = true, showAddress = false, showCoords = true, showWeather = true),
    MAP_ADDRESS(R.string.preset_map_address, showMap = true, showAddress = true, showCoords = false, showWeather = false),
    ADDRESS_WEATHER(R.string.preset_address_weather, showMap = false, showAddress = true, showCoords = false, showWeather = true),
    ADDRESS(R.string.preset_address, showMap = false, showAddress = true, showCoords = false, showWeather = false),
    LATLNG(R.string.preset_latlng, showMap = false, showAddress = false, showCoords = true, showWeather = false);

    companion object {
        val DEFAULT = MAP_ADDRESS_WEATHER

        fun fromName(name: String?): LayoutPreset =
            entries.firstOrNull { it.name == name } ?: DEFAULT
    }
}

/**
 * Where the stamp panel is burned onto the photo — and, for WYSIWYG (P2-US-011),
 * where the live GPS card sits on the viewfinder. The on-screen overlay aligns to
 * the SAME edge as this setting so the preview matches the saved photo. Snapshot
 * into [com.gpstools.camera.media.StampData] at capture time. [BOTTOM] is the
 * default (the historical stamp location).
 */
enum class StampPosition(@StringRes val labelRes: Int) {
    BOTTOM(R.string.position_bottom),
    TOP(R.string.position_top);

    companion object {
        val DEFAULT = BOTTOM

        fun fromName(name: String?): StampPosition =
            entries.firstOrNull { it.name == name } ?: DEFAULT
    }
}

/**
 * Persists the formatting preferences (coordinate + time format, layout preset,
 * stamp position) used by the stamp. Same lightweight SharedPreferences pattern as
 * [com.gpstools.camera.locale.LocaleStore] and the other stores — no DataStore dependency.
 */
object AppSettingsStore {
    private const val PREFS = "app_settings"
    private const val KEY_COORD_FORMAT = "coordinate_format"
    private const val KEY_TIME_FORMAT = "time_format"
    private const val KEY_LAYOUT_PRESET = "layout_preset"
    private const val KEY_STAMP_POSITION = "stamp_position"
    private const val KEY_SHOW_GRID = "show_grid"

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

    fun loadLayoutPreset(context: Context): LayoutPreset =
        LayoutPreset.fromName(prefs(context).getString(KEY_LAYOUT_PRESET, null))

    fun saveLayoutPreset(context: Context, preset: LayoutPreset) {
        prefs(context).edit().putString(KEY_LAYOUT_PRESET, preset.name).apply()
    }

    fun loadStampPosition(context: Context): StampPosition =
        StampPosition.fromName(prefs(context).getString(KEY_STAMP_POSITION, null))

    fun saveStampPosition(context: Context, position: StampPosition) {
        prefs(context).edit().putString(KEY_STAMP_POSITION, position.name).apply()
    }

    /** Whether the 3×3 rule-of-thirds framing grid is drawn on the viewfinder (P2-US-012). */
    fun loadShowGrid(context: Context): Boolean =
        prefs(context).getBoolean(KEY_SHOW_GRID, false)

    fun saveShowGrid(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_SHOW_GRID, enabled).apply()
    }
}

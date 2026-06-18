package com.gpstools.camera.media

import android.content.Context
import androidx.annotation.StringRes
import com.gpstools.camera.R

/**
 * The available visual styles for the location stamp burned onto a captured photo
 * (US-009). Each template lays out the same data (address, coordinates, accuracy,
 * date/time and — where appropriate — a map thumbnail) in a visually distinct way.
 *
 * [usesMap] lets the capture pipeline skip the (network) map fetch entirely for
 * templates that don't render one.
 *
 * [premium] templates are locked behind the one-time IAP (US-016) — gated in the
 * picker on [com.gpstools.camera.billing.Premium.isPremium].
 */
enum class StampTemplate(
    @StringRes val labelRes: Int,
    val usesMap: Boolean,
    val premium: Boolean = false,
) {
    /** Full-width panel: bold wrapped address, coords, date/time + map on the right. */
    CLASSIC(R.string.template_classic, usesMap = true),

    /** Clean no-map panel with a gold left accent bar — address + fields, modern look. */
    MODERN(R.string.template_modern, usesMap = false),

    /** Documentation layout with a green "WORK REPORT" header + map on the left. */
    REPORTING(R.string.template_reporting, usesMap = true),

    /** Most detailed: header band + labelled rows; forces every field on. Premium. */
    ADVANCE(R.string.template_advance, usesMap = true, premium = true),

    /** Report-style layout the user can later tailor field-by-field. Premium. */
    CUSTOM(R.string.template_custom, usesMap = true, premium = true);

    companion object {
        /** The template applied when the user hasn't picked one yet. */
        val DEFAULT = CLASSIC

        /**
         * Resolves a persisted [name] back to a template, falling back to [DEFAULT].
         * Old v2 names (MINIMAL → MODERN, FIELD_REPORT → REPORTING) are migrated so a
         * previously-saved choice still resolves to the nearest new template.
         */
        fun fromName(name: String?): StampTemplate = when (name) {
            "MINIMAL" -> MODERN
            "FIELD_REPORT" -> REPORTING
            else -> entries.firstOrNull { it.name == name } ?: DEFAULT
        }
    }
}

/** Localised display label for the template. */
fun StampTemplate.label(context: Context): String = context.getString(labelRes)

private const val PREFS_NAME = "stamp_prefs"
private const val KEY_TEMPLATE = "stamp_template"

/**
 * Tiny SharedPreferences-backed store for the selected [StampTemplate] (US-009).
 * Persists across captures and app launches; no new dependency required.
 */
class StampTemplateStore(context: Context) {
    private val prefs = context.applicationContext
        .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun load(): StampTemplate = StampTemplate.fromName(prefs.getString(KEY_TEMPLATE, null))

    fun save(template: StampTemplate) {
        prefs.edit().putString(KEY_TEMPLATE, template.name).apply()
    }
}

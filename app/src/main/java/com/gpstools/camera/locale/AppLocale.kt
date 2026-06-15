package com.gpstools.camera.locale

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.res.Configuration
import androidx.annotation.StringRes
import com.gpstools.camera.R
import java.util.Locale

/**
 * In-app language options. [SYSTEM] follows the device locale (the default);
 * the others force a specific app language regardless of the device setting.
 *
 * [tag] is an ISO-639 language code ("" for system default) persisted by
 * [LocaleStore]; resource resolution for "hi" is provided by res/values-hi.
 */
enum class AppLanguage(val tag: String, @StringRes val labelRes: Int) {
    SYSTEM("", R.string.settings_language_system),
    ENGLISH("en", R.string.settings_language_english),
    HINDI("hi", R.string.settings_language_hindi);

    companion object {
        val DEFAULT = SYSTEM

        fun fromTag(tag: String?): AppLanguage =
            entries.firstOrNull { it.tag == tag } ?: DEFAULT
    }
}

/**
 * Persists the chosen [AppLanguage] in SharedPreferences (same lightweight
 * store pattern used elsewhere in the app — no DataStore dependency).
 */
object LocaleStore {
    private const val PREFS = "app_locale"
    private const val KEY_TAG = "language_tag"

    fun load(context: Context): AppLanguage {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return AppLanguage.fromTag(prefs.getString(KEY_TAG, AppLanguage.DEFAULT.tag))
    }

    fun save(context: Context, language: AppLanguage) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_TAG, language.tag)
            .apply()
    }
}

/**
 * Returns a context whose resources resolve in the stored app language.
 * For [AppLanguage.SYSTEM] the base context is returned untouched so the app
 * follows the device locale. Call from [Activity.attachBaseContext].
 */
fun Context.wrapWithStoredLocale(): Context {
    val language = LocaleStore.load(this)
    if (language == AppLanguage.SYSTEM) return this

    val locale = Locale(language.tag)
    Locale.setDefault(locale)
    val config = Configuration(resources.configuration)
    config.setLocale(locale)
    return createConfigurationContext(config)
}

/** Walks the context wrapper chain to find the hosting [Activity], if any. */
fun Context.findActivity(): Activity? {
    var context: Context? = this
    while (context is ContextWrapper) {
        if (context is Activity) return context
        context = context.baseContext
    }
    return null
}

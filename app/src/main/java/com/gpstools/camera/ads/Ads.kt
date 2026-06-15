package com.gpstools.camera.ads

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.google.android.gms.ads.MobileAds

/**
 * AdMob configuration + the single global ads-enabled flag (US-015).
 *
 * Defaults to Google's official TEST ad unit ids so no real ad ever serves during
 * development; the [bannerUnitId]/[interstitialUnitId] are vars so they can be
 * swapped for production ids (or supplied from a remote config) without touching
 * call sites.
 *
 * EVERY ad placement is gated on [adsEnabled]. Keeping a single flag here means the
 * future one-time remove-ads IAP (US-016) can disable all ads app-wide by calling
 * [setEnabled]. The flag is backed by Compose state so banners react immediately,
 * and persisted to SharedPreferences so the choice survives restarts.
 */
object Ads {
    // Google's official test ad unit ids — always safe, never bill, never serve a
    // real ad. See https://developers.google.com/admob/android/test-ads
    const val TEST_BANNER_UNIT_ID = "ca-app-pub-3940256099942544/6300978111"
    const val TEST_INTERSTITIAL_UNIT_ID = "ca-app-pub-3940256099942544/1033173712"

    /** Show an interstitial after every Nth successful capture (non-intrusive). */
    const val CAPTURES_PER_INTERSTITIAL = 5

    // Configurable ad unit ids (default to the test ids).
    var bannerUnitId: String = TEST_BANNER_UNIT_ID
    var interstitialUnitId: String = TEST_INTERSTITIAL_UNIT_ID

    private const val PREFS = "ads_settings"
    private const val KEY_ADS_ENABLED = "ads_enabled"

    /** True while ads should show. Compose-observable so banners hide instantly. */
    var adsEnabled by mutableStateOf(true)
        private set

    private var initialized = false

    /**
     * Initialise the Mobile Ads SDK once and load the persisted [adsEnabled] flag.
     * Safe to call from [com.gpstools.camera.MainActivity.onCreate]; failures are
     * swallowed so a missing/blocked Play Services can never crash the app.
     */
    fun initialize(context: Context) {
        adsEnabled = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getBoolean(KEY_ADS_ENABLED, true)
        if (!initialized) {
            initialized = true
            runCatching { MobileAds.initialize(context.applicationContext) }
        }
    }

    /** Toggle ads app-wide and persist the choice (the US-016 IAP will call this). */
    fun setEnabled(context: Context, enabled: Boolean) {
        adsEnabled = enabled
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putBoolean(KEY_ADS_ENABLED, enabled).apply()
    }
}

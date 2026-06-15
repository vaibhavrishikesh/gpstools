package com.gpstools.camera.ads

import android.app.Activity
import android.content.Context
import android.util.Log
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback

private const val TAG = "InterstitialAd"

/**
 * Loads and shows a full-screen interstitial after every
 * [Ads.CAPTURES_PER_INTERSTITIAL] successful captures (US-015).
 *
 * The capture flow is NEVER blocked by ads: counting and showing happen in the
 * post-capture callback, an ad only shows if one is already preloaded, every SDK
 * call is guarded with [runCatching], and nothing happens at all when ads are
 * disabled. A miss (no ad ready) simply skips the show and warms one for next time.
 */
class InterstitialAdManager(private val context: Context) {
    private var interstitial: InterstitialAd? = null
    private var loading = false
    private var captureCount = 0

    /** Warm up one interstitial ahead of the next milestone. Idempotent. */
    fun preload() {
        if (!Ads.adsEnabled || loading || interstitial != null) return
        loading = true
        runCatching {
            InterstitialAd.load(
                context,
                Ads.interstitialUnitId,
                AdRequest.Builder().build(),
                object : InterstitialAdLoadCallback() {
                    override fun onAdLoaded(ad: InterstitialAd) {
                        interstitial = ad
                        loading = false
                    }

                    override fun onAdFailedToLoad(error: LoadAdError) {
                        interstitial = null
                        loading = false
                        Log.w(TAG, "Interstitial failed to load: ${error.message}")
                    }
                },
            )
        }.onFailure { loading = false }
    }

    /**
     * Call once after each successful capture. Shows an interstitial on every Nth
     * call when one is preloaded; otherwise it just keeps a fresh ad warming. Pass
     * the hosting [activity] (an interstitial needs an Activity to display).
     */
    fun onCaptureCompleted(activity: Activity?) {
        if (!Ads.adsEnabled) return
        captureCount++
        val milestone = captureCount % Ads.CAPTURES_PER_INTERSTITIAL == 0
        val ad = interstitial
        if (milestone && ad != null && activity != null) {
            interstitial = null
            runCatching { ad.show(activity) }
        }
        // Always keep one warming for the next milestone.
        preload()
    }
}

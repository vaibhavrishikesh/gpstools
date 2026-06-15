package com.gpstools.camera.billing

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.gpstools.camera.ads.Ads

/**
 * The single source of truth for the one-time "remove ads + premium templates"
 * entitlement (US-016).
 *
 * Mirrors the [Ads] object: a global, Compose-observable [isPremium] flag backed
 * by SharedPreferences so the entitlement survives restarts and the UI (template
 * picker, settings) reacts instantly. The actual Play Billing plumbing lives in
 * [BillingManager]; this object is the sink both the live purchase flow and the
 * launch-time "restore owned purchases" query funnel into via [grant].
 */
object Premium {
    /** The managed (non-consumable) one-time product id, configured in Play Console. */
    const val PRODUCT_ID = "remove_ads_premium"

    private const val PREFS = "premium_settings"
    private const val KEY_PREMIUM = "is_premium"

    /** True once the user owns [PRODUCT_ID]. Compose-observable. */
    var isPremium by mutableStateOf(false)
        private set

    /** Load the persisted entitlement; call once at app start (before any UI). */
    fun load(context: Context) {
        isPremium = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getBoolean(KEY_PREMIUM, false)
        // Keep ads in sync in case the flag was set in a previous run.
        if (isPremium) Ads.setEnabled(context, false)
    }

    /**
     * Grant the entitlement: persist it, expose it to the UI, and disable ads
     * app-wide. Idempotent — safe to call from every purchase/restore callback.
     */
    fun grant(context: Context) {
        isPremium = true
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putBoolean(KEY_PREMIUM, true).apply()
        Ads.setEnabled(context, false)
    }

    /**
     * Revoke the entitlement (re-enables ads). Only used by the DEBUG simulate
     * toggle for verification — a real refund would be reflected by the launch
     * restore query no longer returning the purchase, but we never auto-revoke
     * there to avoid yanking access on a transient offline launch.
     */
    fun revokeForDebug(context: Context) {
        isPremium = false
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putBoolean(KEY_PREMIUM, false).apply()
        Ads.setEnabled(context, true)
    }
}

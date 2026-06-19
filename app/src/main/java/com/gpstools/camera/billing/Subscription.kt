package com.gpstools.camera.billing

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * The single source of truth for the recurring "Pro" subscription entitlement
 * (US-018) that unlocks unlimited / watermark-free PDF reports + batch export.
 *
 * Mirrors [Premium]: a global, Compose-observable [isSubscribed] flag backed by
 * SharedPreferences so the entitlement survives restarts and the UI (gallery
 * export, paywall, settings) reacts instantly. Play Billing plumbing lives in
 * [BillingManager]; this object is the sink both the live subscribe flow and the
 * launch-time "restore owned purchases" query funnel into via [grant].
 *
 * Kept independent of [Premium] (the one-time remove-ads IAP) — either entitlement
 * unlocks unlimited reports (see [Entitlements.hasUnlimitedReports]), but a
 * subscription does not touch ads.
 */
object Subscription {
    /**
     * The single Pro subscription product configured in Play Console. It carries two
     * base plans (monthly + yearly) as offers — the modern Play Billing model — rather
     * than two separate subscription products.
     */
    const val PRODUCT_ID = "gpstools_pro"

    /** Monthly base-plan id (an offer within [PRODUCT_ID]). */
    const val BASE_PLAN_MONTHLY = "pro-monthly"

    /** Yearly base-plan id (an offer within [PRODUCT_ID]). */
    const val BASE_PLAN_YEARLY = "pro-yearly"

    private const val PREFS = "subscription_settings"
    private const val KEY_SUBSCRIBED = "is_subscribed"
    private const val KEY_PLAN = "active_plan"

    /** True while the user holds an active Pro subscription. Compose-observable. */
    var isSubscribed by mutableStateOf(false)
        private set

    /** The currently-active subscription product id (null when not subscribed). */
    var activePlan by mutableStateOf<String?>(null)
        private set

    /** Load the persisted entitlement; call once at app start (before any UI). */
    fun load(context: Context) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        isSubscribed = prefs.getBoolean(KEY_SUBSCRIBED, false)
        activePlan = if (isSubscribed) prefs.getString(KEY_PLAN, null) else null
    }

    /**
     * Grant the subscription: persist it and expose it to the UI. Idempotent — safe
     * to call from every purchase/restore callback.
     */
    fun grant(context: Context, productId: String) {
        isSubscribed = true
        activePlan = productId
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_SUBSCRIBED, true)
            .putString(KEY_PLAN, productId)
            .apply()
    }

    /**
     * Clear the subscription entitlement. Used by the launch restore when no active
     * subscription is owned anymore (e.g. lapsed/cancelled) and by the DEBUG toggle.
     */
    fun clear(context: Context) {
        isSubscribed = false
        activePlan = null
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_SUBSCRIBED, false)
            .remove(KEY_PLAN)
            .apply()
    }
}

/** Combined entitlement checks spanning the one-time IAP and the subscription. */
object Entitlements {
    /**
     * Whether premium PDF reports (unlimited photos, no watermark, batch export)
     * are unlocked — granted by EITHER the one-time [Premium] purchase (US-016) or
     * an active [Subscription] (US-018). Compose-observable via both flags.
     */
    val hasUnlimitedReports: Boolean
        get() = Premium.isPremium || Subscription.isSubscribed
}

package com.gpstools.camera.billing

import android.app.Activity
import android.content.Context
import android.util.Log
import com.android.billingclient.api.AcknowledgePurchaseParams
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.PendingPurchasesParams
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.PurchasesUpdatedListener
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams

private const val TAG = "BillingManager"

/**
 * Thin Google Play Billing wrapper for the one-time "remove ads + premium
 * templates" product (US-016) AND the recurring "Pro" subscriptions (US-018).
 *
 * Responsibilities:
 *  - connect to Play Billing and (on connect) query the user's owned purchases —
 *    both one-time (INAPP) and subscriptions (SUBS) — so entitlements are RESTORED
 *    automatically after a reinstall;
 *  - fetch [Premium.PRODUCT_ID] + [Subscription.PRODUCT_IDS] [ProductDetails] so the
 *    buy/subscribe buttons can launch a real flow and show live prices;
 *  - handle the purchase callback: acknowledge the purchase and funnel it into
 *    [Premium.grant] (INAPP) or [Subscription.grant] (SUBS).
 *
 * Every Play SDK call is wrapped in `runCatching`/guarded so a missing or blocked
 * Play Store can never crash the app — the buy button just reports unavailable.
 *
 * Create one instance where billing is needed (it holds its own connection),
 * call [start] to connect and [release] when done. The launch-time restore in
 * [com.gpstools.camera.MainActivity] uses a short-lived instance.
 */
class BillingManager(context: Context) {

    private val appContext = context.applicationContext

    /** Latest one-time product details, populated after a successful query (null until then). */
    var productDetails: ProductDetails? = null
        private set

    /** Latest subscription product details keyed by product id (empty until queried). */
    var subscriptionDetails: Map<String, ProductDetails> = emptyMap()
        private set

    private val purchasesListener = PurchasesUpdatedListener { result, purchases ->
        if (result.responseCode == BillingClient.BillingResponseCode.OK && purchases != null) {
            purchases.forEach { handlePurchase(it) }
        } else {
            Log.d(TAG, "Purchase update: code=${result.responseCode}")
        }
    }

    private val billingClient: BillingClient = BillingClient.newBuilder(appContext)
        .setListener(purchasesListener)
        .enablePendingPurchases(
            PendingPurchasesParams.newBuilder().enableOneTimeProducts().build(),
        )
        .build()

    private var connected = false

    /** Connect to Play Billing; on success restores owned purchases + loads details. */
    fun start() {
        runCatching {
            billingClient.startConnection(object : BillingClientStateListener {
                override fun onBillingSetupFinished(result: BillingResult) {
                    connected = result.responseCode == BillingClient.BillingResponseCode.OK
                    if (connected) {
                        queryOwnedPurchases()
                        queryProductDetails()
                        querySubscriptionDetails()
                    } else {
                        Log.d(TAG, "Billing setup failed: ${result.responseCode}")
                    }
                }

                override fun onBillingServiceDisconnected() {
                    connected = false
                }
            })
        }.onFailure { Log.w(TAG, "startConnection failed", it) }
    }

    /** Query the product so [productDetails] is ready before the user taps buy. */
    private fun queryProductDetails() {
        runCatching {
            val params = QueryProductDetailsParams.newBuilder()
                .setProductList(
                    listOf(
                        QueryProductDetailsParams.Product.newBuilder()
                            .setProductId(Premium.PRODUCT_ID)
                            .setProductType(BillingClient.ProductType.INAPP)
                            .build(),
                    ),
                )
                .build()
            billingClient.queryProductDetailsAsync(params) { result, details ->
                if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                    productDetails = details.firstOrNull()
                } else {
                    Log.d(TAG, "queryProductDetails: ${result.responseCode}")
                }
            }
        }.onFailure { Log.w(TAG, "queryProductDetails failed", it) }
    }

    /** Query the Pro subscriptions so prices/offer tokens are ready before subscribe. */
    private fun querySubscriptionDetails() {
        runCatching {
            val params = QueryProductDetailsParams.newBuilder()
                .setProductList(
                    Subscription.PRODUCT_IDS.map { id ->
                        QueryProductDetailsParams.Product.newBuilder()
                            .setProductId(id)
                            .setProductType(BillingClient.ProductType.SUBS)
                            .build()
                    },
                )
                .build()
            billingClient.queryProductDetailsAsync(params) { result, details ->
                if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                    subscriptionDetails = details.associateBy { it.productId }
                } else {
                    Log.d(TAG, "querySubscriptionDetails: ${result.responseCode}")
                }
            }
        }.onFailure { Log.w(TAG, "querySubscriptionDetails failed", it) }
    }

    /**
     * RESTORE: query purchases the account already owns (both one-time and
     * subscriptions) and grant the matching entitlement. Called automatically on
     * connect and from the manual "Restore purchases" button.
     */
    fun queryOwnedPurchases() {
        queryOwned(BillingClient.ProductType.INAPP)
        queryOwned(BillingClient.ProductType.SUBS)
    }

    private fun queryOwned(productType: String) {
        runCatching {
            val params = QueryPurchasesParams.newBuilder()
                .setProductType(productType)
                .build()
            billingClient.queryPurchasesAsync(params) { result, purchases ->
                if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                    purchases.forEach { handlePurchase(it) }
                }
            }
        }.onFailure { Log.w(TAG, "queryOwnedPurchases($productType) failed", it) }
    }

    /**
     * Launch the purchase flow for the product. Returns false (and does nothing)
     * if billing isn't connected or the product details haven't loaded yet — the
     * caller should surface an "unavailable, try later" message.
     */
    fun launchPurchase(activity: Activity): Boolean {
        val details = productDetails
        if (!connected || details == null) {
            Log.d(TAG, "launchPurchase unavailable (connected=$connected, details=${details != null})")
            return false
        }
        return runCatching {
            val params = BillingFlowParams.newBuilder()
                .setProductDetailsParamsList(
                    listOf(
                        BillingFlowParams.ProductDetailsParams.newBuilder()
                            .setProductDetails(details)
                            .build(),
                    ),
                )
                .build()
            val result = billingClient.launchBillingFlow(activity, params)
            result.responseCode == BillingClient.BillingResponseCode.OK
        }.getOrElse {
            Log.w(TAG, "launchBillingFlow failed", it)
            false
        }
    }

    /**
     * The formatted, locale-aware price of a Pro subscription (e.g. "₹149.00") once
     * its details have loaded; null until then. Reads the first pricing phase of the
     * first offer.
     */
    fun subscriptionPrice(productId: String): String? = runCatching {
        subscriptionDetails[productId]
            ?.subscriptionOfferDetails
            ?.firstOrNull()
            ?.pricingPhases
            ?.pricingPhaseList
            ?.firstOrNull()
            ?.formattedPrice
    }.getOrNull()

    /**
     * Launch the subscribe flow for a Pro subscription [productId]. Returns false
     * (and does nothing) if billing isn't connected, the details/offer aren't loaded
     * yet — the caller should surface an "unavailable, try later" message.
     */
    fun launchSubscriptionPurchase(activity: Activity, productId: String): Boolean {
        val details = subscriptionDetails[productId]
        val offerToken = details?.subscriptionOfferDetails?.firstOrNull()?.offerToken
        if (!connected || details == null || offerToken == null) {
            Log.d(TAG, "launchSubscription unavailable (connected=$connected, details=${details != null})")
            return false
        }
        return runCatching {
            val params = BillingFlowParams.newBuilder()
                .setProductDetailsParamsList(
                    listOf(
                        BillingFlowParams.ProductDetailsParams.newBuilder()
                            .setProductDetails(details)
                            .setOfferToken(offerToken)
                            .build(),
                    ),
                )
                .build()
            val result = billingClient.launchBillingFlow(activity, params)
            result.responseCode == BillingClient.BillingResponseCode.OK
        }.getOrElse {
            Log.w(TAG, "launchSubscription failed", it)
            false
        }
    }

    /** Grant + acknowledge a PURCHASED, matching purchase (one-time or subscription). Idempotent. */
    private fun handlePurchase(purchase: Purchase) {
        if (purchase.purchaseState != Purchase.PurchaseState.PURCHASED) return

        val subProduct = Subscription.PRODUCT_IDS.firstOrNull { it in purchase.products }
        when {
            Premium.PRODUCT_ID in purchase.products -> Premium.grant(appContext)
            subProduct != null -> Subscription.grant(appContext, subProduct)
            else -> return
        }

        if (!purchase.isAcknowledged) {
            runCatching {
                val params = AcknowledgePurchaseParams.newBuilder()
                    .setPurchaseToken(purchase.purchaseToken)
                    .build()
                billingClient.acknowledgePurchase(params) { result ->
                    Log.d(TAG, "acknowledge: ${result.responseCode}")
                }
            }.onFailure { Log.w(TAG, "acknowledge failed", it) }
        }
    }

    /** End the Play Billing connection. */
    fun release() {
        runCatching { billingClient.endConnection() }
    }
}

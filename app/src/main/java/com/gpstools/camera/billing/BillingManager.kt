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
 * templates" product (US-016).
 *
 * Responsibilities:
 *  - connect to Play Billing and (on connect) query the user's owned purchases so
 *    the entitlement is RESTORED automatically after a reinstall;
 *  - fetch [Premium.PRODUCT_ID]'s [ProductDetails] so the buy button can launch a
 *    real flow;
 *  - handle the purchase callback: acknowledge the purchase and funnel it into
 *    [Premium.grant].
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

    /** Latest product details, populated after a successful query (null until then). */
    var productDetails: ProductDetails? = null
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

    /**
     * RESTORE: query purchases the account already owns and grant the entitlement
     * for any that include our product. Called automatically on connect and from
     * the manual "Restore purchases" button.
     */
    fun queryOwnedPurchases() {
        runCatching {
            val params = QueryPurchasesParams.newBuilder()
                .setProductType(BillingClient.ProductType.INAPP)
                .build()
            billingClient.queryPurchasesAsync(params) { result, purchases ->
                if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                    purchases.forEach { handlePurchase(it) }
                }
            }
        }.onFailure { Log.w(TAG, "queryOwnedPurchases failed", it) }
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

    /** Grant + acknowledge a PURCHASED, matching purchase. Idempotent. */
    private fun handlePurchase(purchase: Purchase) {
        if (purchase.purchaseState != Purchase.PurchaseState.PURCHASED) return
        if (Premium.PRODUCT_ID !in purchase.products) return

        Premium.grant(appContext)

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

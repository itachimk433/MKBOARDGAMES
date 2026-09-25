package com.mkdev.mkboardgames

import android.app.Activity
import android.content.Context
import android.os.Handler
import android.os.Looper
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClient.BillingResponseCode
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.PendingPurchasesParams
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams

/**
 * Owns the one-time Play Billing purchase that disables advertising.
 *
 * The product must be created in Play Console as an INAPP product with the
 * id "remove_ads". Play supplies the local currency and final price to the UI.
 */
object RemoveAdsManager {
    const val PRODUCT_ID = "remove_ads"
    private const val PURCHASE_READY_TIMEOUT_MS = 15_000L
    private const val INTERNET_ERROR = "Please Check Your Internet Connection and try again."

    private var appContext: Context? = null
    private var billingClient: BillingClient? = null
    private var productDetails: ProductDetails? = null
    private var isConnecting = false
    private val readyCallbacks = mutableListOf<(Boolean) -> Unit>()
    private var purchaseCallback: ((String?) -> Unit)? = null
    private var purchaseLoadingCallback: ((Boolean) -> Unit)? = null
    private val mainHandler = Handler(Looper.getMainLooper())
    private var nextPurchaseRequestId = 0L
    private var activePurchaseRequestId: Long? = null
    private var purchaseReadyTimeout: Runnable? = null

    fun initialize(context: Context) {
        if (billingClient != null) return
        appContext = context.applicationContext
        billingClient = BillingClient.newBuilder(appContext!!)
            .setListener(::onPurchasesUpdated)
            .enablePendingPurchases(
                PendingPurchasesParams.newBuilder()
                    .enableOneTimeProducts()
                    .build(),
            )
            .build()
        connect()
    }

    fun prepare(context: Context, onPriceReady: (String?) -> Unit) {
        initialize(context)
        ensureReady { ready ->
            onPriceReady(
                if (ready) {
                    productDetails
                        ?.oneTimePurchaseOfferDetails
                        ?.formattedPrice
                } else {
                    null
                },
            )
        }
    }

    fun purchase(
        activity: Activity,
        onLoadingChanged: (Boolean) -> Unit,
        onResult: (String?) -> Unit,
    ) {
        if (SettingsManager.isAdsRemoved(activity)) {
            onLoadingChanged(false)
            onResult(null)
            return
        }
        val requestId = ++nextPurchaseRequestId
        activePurchaseRequestId = requestId
        purchaseCallback = onResult
        purchaseLoadingCallback = onLoadingChanged
        onLoadingChanged(true)
        purchaseReadyTimeout = Runnable {
            if (activePurchaseRequestId == requestId) finishPurchase(INTERNET_ERROR)
        }.also { mainHandler.postDelayed(it, PURCHASE_READY_TIMEOUT_MS) }
        initialize(activity)
        ensureReady readyCallback@{ ready ->
            if (activePurchaseRequestId != requestId) return@readyCallback
            cancelPurchaseReadyTimeout()
            val details = productDetails
            if (!ready || details == null) {
                finishPurchase(INTERNET_ERROR)
                return@readyCallback
            }
            purchaseLoadingCallback?.invoke(false)
            purchaseLoadingCallback = null
            val productDetailsParams = BillingFlowParams.ProductDetailsParams.newBuilder()
                .setProductDetails(details)
                .build()
            val flowParams = BillingFlowParams.newBuilder()
                .setProductDetailsParamsList(listOf(productDetailsParams))
                .build()
            val result = billingClient?.launchBillingFlow(activity, flowParams)
            if (result == null || result.responseCode != BillingResponseCode.OK) {
                val message = if (
                    result == null ||
                    result.responseCode == BillingResponseCode.NETWORK_ERROR ||
                    result.responseCode == BillingResponseCode.SERVICE_UNAVAILABLE
                ) {
                    INTERNET_ERROR
                } else {
                    result.debugMessage.ifBlank { "Google Play could not start the purchase." }
                }
                finishPurchase(message)
            }
        }
    }

    private fun connect() {
        val client = billingClient ?: return
        if (client.isReady || isConnecting) return
        isConnecting = true
        client.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(result: BillingResult) {
                isConnecting = false
                val ready = result.responseCode == BillingResponseCode.OK
                if (ready) {
                    queryProductDetails()
                    queryExistingPurchases()
                } else {
                    flushReadyCallbacks(false)
                }
            }

            override fun onBillingServiceDisconnected() {
                isConnecting = false
            }
        })
    }

    private fun ensureReady(callback: (Boolean) -> Unit) {
        val client = billingClient
        if (client?.isReady == true && productDetails != null) {
            callback(true)
            return
        }
        readyCallbacks += callback
        if (client?.isReady == true) {
            queryProductDetails()
        } else {
            connect()
        }
    }

    private fun queryProductDetails() {
        val client = billingClient ?: return
        client.queryProductDetailsAsync(
            QueryProductDetailsParams.newBuilder()
                .setProductList(
                    listOf(
                        QueryProductDetailsParams.Product.newBuilder()
                            .setProductId(PRODUCT_ID)
                            .setProductType(BillingClient.ProductType.INAPP)
                            .build(),
                    ),
                )
                .build(),
        ) { result, productDetailsResult ->
            if (result.responseCode == BillingResponseCode.OK) {
                productDetails = productDetailsResult.productDetailsList
                    .firstOrNull { it.productId == PRODUCT_ID }
                flushReadyCallbacks(productDetails != null)
            } else {
                flushReadyCallbacks(false)
            }
        }
    }

    private fun queryExistingPurchases() {
        val client = billingClient ?: return
        client.queryPurchasesAsync(
            QueryPurchasesParams.newBuilder()
                .setProductType(BillingClient.ProductType.INAPP)
                .build(),
        ) { result, purchases ->
            if (result.responseCode == BillingResponseCode.OK) {
                purchases.filter { it.products.contains(PRODUCT_ID) }
                    .forEach { processPurchase(it, null) }
            }
        }
    }

    private fun onPurchasesUpdated(result: BillingResult, purchases: MutableList<Purchase>?) {
        when (result.responseCode) {
            BillingResponseCode.OK -> purchases.orEmpty()
                .filter { it.products.contains(PRODUCT_ID) }
                .forEach { processPurchase(it, purchaseCallback) }
            BillingResponseCode.USER_CANCELED -> finishPurchase("Purchase canceled.")
            BillingResponseCode.ITEM_ALREADY_OWNED -> {
                queryExistingPurchases()
                finishPurchase(null)
            }
            else -> finishPurchase(
                if (result.responseCode == BillingResponseCode.NETWORK_ERROR ||
                    result.responseCode == BillingResponseCode.SERVICE_UNAVAILABLE
                ) {
                    INTERNET_ERROR
                } else {
                    result.debugMessage.ifBlank { "Google Play could not complete the purchase." }
                },
            )
        }
    }

    private fun processPurchase(purchase: Purchase, callback: ((String?) -> Unit)?) {
        if (purchase.purchaseState != Purchase.PurchaseState.PURCHASED) {
            if (callback != null) finishPurchase("Purchase is pending approval in Google Play.")
            return
        }
        val client = billingClient ?: return
        if (purchase.isAcknowledged) {
            appContext?.let { SettingsManager.setAdsRemoved(it) }
            if (callback != null) finishPurchase(null)
            return
        }
        client.acknowledgePurchase(
            com.android.billingclient.api.AcknowledgePurchaseParams.newBuilder()
                .setPurchaseToken(purchase.purchaseToken)
                .build(),
        ) { result ->
            if (result.responseCode == BillingResponseCode.OK) {
                appContext?.let { SettingsManager.setAdsRemoved(it) }
                if (callback != null) finishPurchase(null)
            } else {
                if (callback != null) {
                    finishPurchase("Purchase received, but Google Play has not confirmed it yet.")
                }
            }
        }
    }

    private fun flushReadyCallbacks(ready: Boolean) {
        if (readyCallbacks.isEmpty()) return
        val callbacks = readyCallbacks.toList()
        readyCallbacks.clear()
        callbacks.forEach { it(ready) }
    }

    private fun finishPurchase(message: String?) {
        val callback = purchaseCallback ?: return
        purchaseCallback = null
        activePurchaseRequestId = null
        cancelPurchaseReadyTimeout()
        purchaseLoadingCallback?.invoke(false)
        purchaseLoadingCallback = null
        callback(message)
    }

    private fun cancelPurchaseReadyTimeout() {
        purchaseReadyTimeout?.let(mainHandler::removeCallbacks)
        purchaseReadyTimeout = null
    }
}
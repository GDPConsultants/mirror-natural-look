package com.mirrornaturallook

import android.app.Activity
import android.content.Context
import android.util.Log
import com.android.billingclient.api.*
import kotlinx.coroutines.*

class BillingManager(
    private val context: Context,
    private val premiumManager: PremiumManager,
    private val onPurchaseSuccess: () -> Unit,
    private val onPurchaseFailed: (String) -> Unit
) {
    private var billingClient: BillingClient? = null
    private var productDetails: ProductDetails? = null

    private val purchasesUpdatedListener = PurchasesUpdatedListener { result, purchases ->
        when (result.responseCode) {
            BillingClient.BillingResponseCode.OK -> purchases?.forEach { handlePurchase(it) }
            BillingClient.BillingResponseCode.USER_CANCELED -> {}
            else -> onPurchaseFailed("Purchase failed: ${result.debugMessage}")
        }
    }

    fun init() {
        billingClient = BillingClient.newBuilder(context)
            .setListener(purchasesUpdatedListener)
            .enablePendingPurchases()
            .build()

        billingClient?.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(result: BillingResult) {
                if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                    CoroutineScope(Dispatchers.IO).launch {
                        queryProductDetails()
                        restorePurchases()
                    }
                }
            }
            override fun onBillingServiceDisconnected() {}
        })
    }

    private suspend fun queryProductDetails() {
        val params = QueryProductDetailsParams.newBuilder()
            .setProductList(listOf(
                QueryProductDetailsParams.Product.newBuilder()
                    .setProductId(PremiumManager.PRODUCT_ID_LIFETIME)
                    .setProductType(BillingClient.ProductType.INAPP)
                    .build()
            )).build()
        val result = billingClient?.queryProductDetails(params)
        if (result?.billingResult?.responseCode == BillingClient.BillingResponseCode.OK)
            productDetails = result.productDetailsList?.firstOrNull()
    }

    fun getPriceString(): String =
        productDetails?.oneTimePurchaseOfferDetails?.formattedPrice ?: "$4.99"

    fun launchPurchase(activity: Activity) {
        val details = productDetails ?: run { onPurchaseFailed("Store not available. Try again."); return }
        val params = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(listOf(
                BillingFlowParams.ProductDetailsParams.newBuilder().setProductDetails(details).build()
            )).build()
        billingClient?.launchBillingFlow(activity, params)
    }

    private fun handlePurchase(purchase: Purchase) {
        if (purchase.purchaseState == Purchase.PurchaseState.PURCHASED) {
            if (!purchase.isAcknowledged) {
                CoroutineScope(Dispatchers.IO).launch {
                    billingClient?.acknowledgePurchase(
                        AcknowledgePurchaseParams.newBuilder().setPurchaseToken(purchase.purchaseToken).build()
                    )
                }
            }
            premiumManager.activatePremium(purchase.purchaseToken)
            CoroutineScope(Dispatchers.Main).launch { onPurchaseSuccess() }
        }
    }

    suspend fun restorePurchases() {
        val result = billingClient?.queryPurchasesAsync(
            QueryPurchasesParams.newBuilder().setProductType(BillingClient.ProductType.INAPP).build()
        )
        result?.purchasesList?.forEach { purchase ->
            if (purchase.products.contains(PremiumManager.PRODUCT_ID_LIFETIME) &&
                purchase.purchaseState == Purchase.PurchaseState.PURCHASED) {
                premiumManager.activatePremium(purchase.purchaseToken)
                withContext(Dispatchers.Main) { onPurchaseSuccess() }
            }
        }
    }

    fun destroy() { billingClient?.endConnection() }
}

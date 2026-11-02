package com.scamshield.app.billing

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
import com.android.billingclient.api.acknowledgePurchase
import com.android.billingclient.api.queryProductDetails
import com.android.billingclient.api.queryPurchasesAsync
import com.scamshield.app.data.Entitlement
import com.scamshield.app.data.prefs.SettingsRepository
import com.scamshield.app.di.ApplicationScope
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Google Play Billing for the three subscription products. Create them in Play Console with
 * exactly these product IDs (base plans: monthly / yearly) and offer a 7-day free trial on Family.
 *
 * Security note: for a real launch, verify purchase tokens server-side (Play Developer API) in a
 * Cloud Function and store entitlement there too; the client-side state here is for UX.
 */
@Singleton
class BillingManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settings: SettingsRepository,
    @ApplicationScope private val scope: CoroutineScope
) : PurchasesUpdatedListener {

    companion object {
        const val PRO_MONTHLY = "scamshield_pro_monthly"
        const val FAMILY_MONTHLY = "scamshield_family_monthly"
        const val FAMILY_YEARLY = "scamshield_family_yearly"
        val ALL_PRODUCTS = listOf(PRO_MONTHLY, FAMILY_MONTHLY, FAMILY_YEARLY)
        private const val TAG = "Billing"
    }

    private val client: BillingClient = BillingClient.newBuilder(context)
        .setListener(this)
        .enablePendingPurchases(PendingPurchasesParams.newBuilder().enableOneTimeProducts().build())
        .build()

    private val _products = MutableStateFlow<List<ProductDetails>>(emptyList())
    val products: StateFlow<List<ProductDetails>> = _products

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message

    @Volatile
    private var connecting = false

    fun connect() {
        if (client.isReady || connecting) return
        connecting = true
        client.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(result: BillingResult) {
                connecting = false
                if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                    scope.launch {
                        queryProducts()
                        restorePurchases()
                    }
                } else {
                    _message.value = "Play Billing unavailable (${result.debugMessage})"
                }
            }

            override fun onBillingServiceDisconnected() {
                // Retried lazily on the next connect() call.
                connecting = false
            }
        })
    }

    suspend fun queryProducts() {
        val params = QueryProductDetailsParams.newBuilder()
            .setProductList(
                ALL_PRODUCTS.map {
                    QueryProductDetailsParams.Product.newBuilder()
                        .setProductId(it)
                        .setProductType(BillingClient.ProductType.SUBS)
                        .build()
                }
            )
            .build()
        val result = client.queryProductDetails(params)
        if (result.billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
            _products.value = result.productDetailsList.orEmpty()
        } else {
            Log.w(TAG, "queryProductDetails: ${result.billingResult.debugMessage}")
        }
    }

    /** Launches the Play purchase sheet for the product's first (cheapest/trial) offer. */
    fun purchase(activity: Activity, product: ProductDetails) {
        val offer = product.subscriptionOfferDetails
            ?.minByOrNull { o -> o.pricingPhases.pricingPhaseList.firstOrNull()?.priceAmountMicros ?: Long.MAX_VALUE }
            ?: run { _message.value = "No offer available for ${product.productId}"; return }
        val params = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(
                listOf(
                    BillingFlowParams.ProductDetailsParams.newBuilder()
                        .setProductDetails(product)
                        .setOfferToken(offer.offerToken)
                        .build()
                )
            )
            .build()
        val result = client.launchBillingFlow(activity, params)
        if (result.responseCode != BillingClient.BillingResponseCode.OK) {
            _message.value = "Could not start purchase: ${result.debugMessage}"
        }
    }

    override fun onPurchasesUpdated(result: BillingResult, purchases: MutableList<Purchase>?) {
        when (result.responseCode) {
            BillingClient.BillingResponseCode.OK -> purchases?.let { list -> scope.launch { applyPurchases(list) } }
            BillingClient.BillingResponseCode.USER_CANCELED -> Unit
            else -> _message.value = "Purchase failed: ${result.debugMessage}"
        }
    }

    suspend fun restorePurchases() {
        val result = client.queryPurchasesAsync(
            QueryPurchasesParams.newBuilder().setProductType(BillingClient.ProductType.SUBS).build()
        )
        if (result.billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
            applyPurchases(result.purchasesList)
        }
    }

    private suspend fun applyPurchases(purchases: List<Purchase>) {
        var entitlement = Entitlement.FREE
        for (purchase in purchases) {
            if (purchase.purchaseState != Purchase.PurchaseState.PURCHASED) continue
            if (!purchase.isAcknowledged) {
                client.acknowledgePurchase(
                    AcknowledgePurchaseParams.newBuilder().setPurchaseToken(purchase.purchaseToken).build()
                )
            }
            when {
                purchase.products.any { it == FAMILY_MONTHLY || it == FAMILY_YEARLY } -> entitlement = Entitlement.FAMILY
                purchase.products.contains(PRO_MONTHLY) && entitlement != Entitlement.FAMILY -> entitlement = Entitlement.PRO
            }
        }
        settings.setEntitlement(entitlement)
        if (entitlement.isPremium) _message.value = "Welcome to ScamShield ${entitlement.name.lowercase().replaceFirstChar { it.uppercase() }}!"
    }

    fun clearMessage() {
        _message.value = null
    }
}

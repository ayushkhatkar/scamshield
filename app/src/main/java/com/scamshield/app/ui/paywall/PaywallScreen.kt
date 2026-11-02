package com.scamshield.app.ui.paywall

import android.app.Activity
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.android.billingclient.api.ProductDetails
import com.scamshield.app.billing.BillingManager
import com.scamshield.app.data.Entitlement
import com.scamshield.app.data.ReportHelper
import com.scamshield.app.data.prefs.SettingsRepository
import com.scamshield.app.ui.components.SectionTitle
import com.scamshield.app.ui.theme.color
import com.scamshield.engine.RiskLevel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class PaywallViewModel @Inject constructor(
    val billing: BillingManager,
    settings: SettingsRepository
) : ViewModel() {
    val entitlement = settings.settings.map { it.entitlement }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), Entitlement.FREE)
    val products = billing.products
    val message = billing.message

    init {
        billing.connect()
    }

    fun buy(activity: Activity, productId: String) {
        val product = products.value.firstOrNull { it.productId == productId } ?: run {
            viewModelScope.launch { billing.queryProducts() }
            return
        }
        billing.purchase(activity, product)
    }

    fun restore() {
        viewModelScope.launch { billing.restorePurchases() }
    }

    fun priceOf(productId: String, fallback: String): String =
        products.value.firstOrNull { it.productId == productId }?.let { formatPrice(it) } ?: fallback

    private fun formatPrice(details: ProductDetails): String {
        val phase = details.subscriptionOfferDetails
            ?.flatMap { it.pricingPhases.pricingPhaseList }
            ?.filter { it.priceAmountMicros > 0 }
            ?.minByOrNull { it.priceAmountMicros } ?: return "See Play Store"
        val period = when (phase.billingPeriod) {
            "P1M" -> "/month"
            "P1Y" -> "/year"
            else -> ""
        }
        return phase.formattedPrice + period
    }
}

private data class Plan(
    val productId: String,
    val title: String,
    val fallbackPrice: String,
    val tagline: String,
    val features: List<String>,
    val highlight: Boolean
)

private val plans = listOf(
    Plan(
        BillingManager.PRO_MONTHLY, "Pro", "₹99/month", "For yourself",
        listOf(
            "Unlimited link, QR and message checks",
            "Cloud AI second opinion on messages",
            "Automatic daily app audit with alerts",
            "Breach monitor for your e-mail",
            "Priority scam-number database updates"
        ),
        highlight = false
    ),
    Plan(
        BillingManager.FAMILY_MONTHLY, "Family", "₹149/month", "Protect up to 5 phones",
        listOf(
            "Everything in Pro, on every linked phone",
            "Instant alerts when a parent gets a scam call or opens a bad link",
            "See blocked calls and flagged apps across the family",
            "One subscription, shared by the whole family",
            "7-day free trial"
        ),
        highlight = true
    ),
    Plan(
        BillingManager.FAMILY_YEARLY, "Family yearly", "₹999/year", "Save ~45%",
        listOf("Everything in Family", "Billed once a year"),
        highlight = false
    )
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PaywallScreen(onBack: () -> Unit, viewModel: PaywallViewModel = hiltViewModel()) {
    val context = LocalContext.current
    val entitlement by viewModel.entitlement.collectAsStateWithLifecycle()
    val products by viewModel.products.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Plans") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") } }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)
        ) {
            Text("Scams cost Indian families thousands of crores a year.", style = MaterialTheme.typography.headlineMedium)
            Text(
                "ScamShield free covers the basics. Pro and Family remove limits and watch over the people who need it most.",
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 6.dp, bottom = 8.dp)
            )
            if (entitlement.isPremium) {
                Text("Current plan: ${entitlement.name.lowercase().replaceFirstChar { it.uppercase() }}", color = RiskLevel.SAFE.color(), style = MaterialTheme.typography.titleMedium)
            }
            message?.let { Text(it, color = MaterialTheme.colorScheme.secondary, modifier = Modifier.padding(vertical = 6.dp)) }

            plans.forEach { plan ->
                val price = viewModel.priceOf(plan.productId, plan.fallbackPrice)
                Card(
                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (plan.highlight) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface
                    )
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(plan.title, style = MaterialTheme.typography.titleLarge)
                                Text(plan.tagline, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Text(price, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary)
                        }
                        Spacer(Modifier.height(8.dp))
                        plan.features.forEach { f ->
                            Row(verticalAlignment = Alignment.Top, modifier = Modifier.padding(vertical = 2.dp)) {
                                Icon(Icons.Default.CheckCircle, contentDescription = null, tint = RiskLevel.SAFE.color(), modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(8.dp))
                                Text(f, style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                        Spacer(Modifier.height(10.dp))
                        Button(
                            onClick = { (context as? Activity)?.let { viewModel.buy(it, plan.productId) } },
                            enabled = !entitlement.isPremium,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(if (products.isEmpty()) "Subscribe (opens Play Store)" else "Subscribe")
                        }
                    }
                }
            }

            TextButton(onClick = { viewModel.restore() }, modifier = Modifier.padding(top = 8.dp)) { Text("Restore purchases") }

            SectionTitle("For businesses")
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text("Verified Caller ID & SDK", style = MaterialTheme.typography.titleLarge)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Banks, NBFCs, hospitals and delivery companies: register your outgoing numbers so customers see a verified badge, " +
                            "and embed the ScamShield engine (link, QR, message and app checks) inside your own app.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(Modifier.height(10.dp))
                    OutlinedButton(onClick = { ReportHelper.openUrl(context, "https://example.com/scamshield-business") }) { Text("Contact sales") }
                }
            }
            Text(
                "Subscriptions renew automatically and can be cancelled any time in Google Play. Prices shown in the app are set in Play Console.",
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 16.dp)
            )
        }
    }
}

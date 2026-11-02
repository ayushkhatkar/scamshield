package com.scamshield.app.ui.scan

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.scamshield.app.callshield.CallShieldManager
import com.scamshield.app.data.ReportHelper
import com.scamshield.app.data.ScamNumberRepository
import com.scamshield.app.data.ScanRecord
import com.scamshield.app.data.ScanRepository
import com.scamshield.app.ui.components.ReasonCard
import com.scamshield.app.ui.components.RiskBadge
import com.scamshield.app.ui.components.ScoreRing
import com.scamshield.app.ui.components.SectionTitle
import com.scamshield.app.ui.theme.color
import com.scamshield.engine.ScanCategory
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ScanResultViewModel @Inject constructor(
    private val scans: ScanRepository,
    private val callShield: CallShieldManager
) : ViewModel() {
    val record = MutableStateFlow<ScanRecord?>(null)
    val loaded = MutableStateFlow(false)
    val reportedMessage = MutableStateFlow<String?>(null)

    fun load(id: Long) {
        viewModelScope.launch {
            record.value = scans.get(id)
            loaded.value = true
        }
    }

    fun reportNumber(number: String, label: String) {
        viewModelScope.launch {
            callShield.reportNumber(number, label)
            reportedMessage.value = "Reported $number. It will be blocked on this phone and shared with the community."
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScanResultScreen(scanId: Long, onBack: () -> Unit, viewModel: ScanResultViewModel = hiltViewModel()) {
    val context = LocalContext.current
    val record by viewModel.record.collectAsStateWithLifecycle()
    val loaded by viewModel.loaded.collectAsStateWithLifecycle()
    val reported by viewModel.reportedMessage.collectAsStateWithLifecycle()
    var reportDialogFor by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(scanId) { viewModel.load(scanId) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Result") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") } }
            )
        }
    ) { padding ->
        val r = record
        if (r == null) {
            Column(Modifier.fillMaxSize().padding(padding), horizontalAlignment = Alignment.CenterHorizontally) {
                Spacer(Modifier.height(48.dp))
                if (loaded) Text("This result is no longer available.") else CircularProgressIndicator()
            }
            return@Scaffold
        }
        val result = r.result
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
        ) {
            Card(
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                colors = CardDefaults.cardColors(containerColor = result.level.color().copy(alpha = 0.10f))
            ) {
                Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    ScoreRing(score = result.score, level = result.level, size = 92)
                    Spacer(Modifier.width(16.dp))
                    Column(Modifier.weight(1f)) {
                        RiskBadge(result.level)
                        Spacer(Modifier.height(6.dp))
                        Text(result.summary, style = MaterialTheme.typography.titleLarge)
                    }
                }
            }

            Card(modifier = Modifier.fillMaxWidth().padding(top = 12.dp)) {
                Column(Modifier.padding(14.dp)) {
                    Text("What to do", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(4.dp))
                    Text(result.recommendation, style = MaterialTheme.typography.bodyLarge)
                }
            }

            if (result.facts.isNotEmpty()) {
                SectionTitle("Details")
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(14.dp)) {
                        result.facts.filterKeys { it !in setOf("worstLinkScore") }.forEach { (k, v) ->
                            Row(modifier = Modifier.padding(vertical = 3.dp)) {
                                Text(factLabel(k), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.width(110.dp))
                                Text(v, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                            }
                        }
                    }
                }
            }

            SectionTitle("Why")
            if (result.reasons.isEmpty()) {
                Text("No warning signs were detected.", style = MaterialTheme.typography.bodyMedium)
            }
            result.reasons.forEach { ReasonCard(it) }

            SectionTitle("Checked")
            Card(modifier = Modifier.fillMaxWidth()) {
                Text(
                    result.subject,
                    style = MaterialTheme.typography.bodyMedium,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier.padding(14.dp)
                )
            }

            SectionTitle("Take action")
            val phone = when {
                result.category == ScanCategory.PHONE -> result.subject
                else -> result.facts["payeePhone"] ?: result.facts["phones"]?.substringBefore(",")?.trim()
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(onClick = { ReportHelper.shareWarning(context, result) }, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Default.Share, contentDescription = null); Spacer(Modifier.width(6.dp)); Text("Warn family")
                }
                if (phone != null) {
                    OutlinedButton(onClick = { reportDialogFor = phone }, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Default.Block, contentDescription = null); Spacer(Modifier.width(6.dp)); Text("Block number")
                    }
                }
            }
            if (result.level.isAlertWorthy) {
                Spacer(Modifier.height(10.dp))
                Button(onClick = { ReportHelper.dial1930(context) }, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Default.Call, contentDescription = null); Spacer(Modifier.width(6.dp)); Text("Lost money? Call 1930 now")
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(top = 6.dp)) {
                    TextButton(onClick = { ReportHelper.openUrl(context, ReportHelper.CYBERCRIME_PORTAL) }) { Text("cybercrime.gov.in") }
                    TextButton(onClick = { ReportHelper.openUrl(context, ReportHelper.SANCHAR_SAATHI) }) { Text("Report number (Chakshu)") }
                }
            }
            reported?.let {
                Spacer(Modifier.height(8.dp))
                Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.secondary)
            }
            Spacer(Modifier.height(32.dp))
        }
    }

    reportDialogFor?.let { number ->
        ReportNumberDialog(number = number, onDismiss = { reportDialogFor = null }) { label ->
            viewModel.reportNumber(number, label)
            reportDialogFor = null
        }
    }
}

@Composable
fun ReportNumberDialog(number: String, onDismiss: () -> Unit, onReport: (String) -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Report $number") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text("What kind of scam was it? The number is blocked on this phone immediately.", style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(8.dp))
                ScamNumberRepository.REPORT_LABELS.forEach { label ->
                    TextButton(onClick = { onReport(label) }, modifier = Modifier.fillMaxWidth()) { Text(label) }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

private fun factLabel(key: String): String = when (key) {
    "host" -> "Website"
    "domain" -> "Domain"
    "brand" -> "Claims to be"
    "official" -> "Official site"
    "payee" -> "Pays to (UPI ID)"
    "payeeName" -> "Payee name"
    "payeePhone" -> "Payee phone"
    "amount" -> "Amount"
    "note" -> "Note"
    "merchantCode" -> "Merchant code"
    "url" -> "Link"
    "links" -> "Links found"
    "phones" -> "Numbers found"
    "upiIds" -> "UPI IDs found"
    "worstLink" -> "Riskiest link"
    "number" -> "Number"
    "verifiedBusiness" -> "Verified business"
    "ssid" -> "Network"
    "security" -> "Security"
    "email" -> "E-mail"
    else -> key
}

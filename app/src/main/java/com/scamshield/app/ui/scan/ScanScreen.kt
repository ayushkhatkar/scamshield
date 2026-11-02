package com.scamshield.app.ui.scan

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.scamshield.app.data.ScanRecord
import com.scamshield.app.data.prefs.SettingsRepository
import com.scamshield.app.ui.components.EmptyState
import com.scamshield.app.ui.components.LevelDot
import com.scamshield.app.ui.components.RiskBadge
import com.scamshield.app.ui.components.SectionTitle
import com.scamshield.app.ui.components.formatTime
import com.scamshield.engine.ScanCategory

@Composable
fun ScanScreen(
    onResult: (Long) -> Unit,
    onPaywall: () -> Unit,
    viewModel: ScanViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val input by viewModel.input.collectAsStateWithLifecycle()
    val busy by viewModel.busy.collectAsStateWithLifecycle()
    val remaining by viewModel.remaining.collectAsStateWithLifecycle()
    val history by viewModel.history.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is ScanEvent.Done -> onResult(event.id)
                is ScanEvent.QuotaExceeded -> {
                    val r = snackbar.showSnackbar("Daily free limit reached. Upgrade for unlimited scans.", actionLabel = "Upgrade")
                    if (r == androidx.compose.material3.SnackbarResult.ActionPerformed) onPaywall()
                }
                is ScanEvent.Error -> snackbar.showSnackbar(event.message)
            }
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.weight(1f).padding(horizontal = 16.dp),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 24.dp)
        ) {
            item {
                Text("Check before you trust", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.padding(top = 12.dp))
                Text(
                    "Paste a link, a message, a phone number or a UPI ID. Or scan a QR code before paying.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp, bottom = 12.dp)
                )
                OutlinedTextField(
                    value = input,
                    onValueChange = { viewModel.input.value = it },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 120.dp),
                    placeholder = { Text("e.g. \"Dear customer your SBI account will be blocked, update KYC at http://…\"") },
                    minLines = 4,
                    maxLines = 10
                )
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button(
                        onClick = { viewModel.analyze() },
                        enabled = !busy && input.isNotBlank(),
                        modifier = Modifier.weight(1f)
                    ) {
                        if (busy) CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                        else Icon(Icons.Default.Search, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Analyse")
                    }
                    OutlinedButton(onClick = { viewModel.scanQr(context) }, enabled = !busy, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Default.QrCodeScanner, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Scan QR")
                    }
                }
                if (remaining != Int.MAX_VALUE) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 6.dp)) {
                        Text(
                            "$remaining of ${SettingsRepository.FREE_DAILY_SCANS} free scans left today",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.weight(1f)
                        )
                        TextButton(onClick = onPaywall) { Text("Go unlimited") }
                    }
                }
                Text(
                    "Tip: select any text in WhatsApp or SMS and tap \"Check with ScamShield\", or share a link to this app.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp)
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    SectionTitle("Recent checks", Modifier.weight(1f))
                    if (history.isNotEmpty()) TextButton(onClick = { viewModel.clearHistory() }) { Text("Clear") }
                }
            }
            if (history.isEmpty()) {
                item { EmptyState("Nothing checked yet. Your results will appear here.") }
            }
            items(history, key = { it.id }) { record ->
                HistoryRow(record) { onResult(record.id) }
            }
        }
        SnackbarHost(snackbar)
    }
}

@Composable
private fun HistoryRow(record: ScanRecord, onClick: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp).clickable(onClick = onClick)) {
        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            LevelDot(record.result.level)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    record.result.subject.replace('\n', ' '),
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    "${categoryLabel(record.result.category)} · ${formatTime(record.timestamp)}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(Modifier.width(8.dp))
            RiskBadge(record.result.level)
        }
    }
}

fun categoryLabel(category: ScanCategory): String = when (category) {
    ScanCategory.URL -> "Link"
    ScanCategory.UPI_QR -> "UPI payment"
    ScanCategory.WIFI_QR -> "Wi-Fi QR"
    ScanCategory.QR_TEXT -> "QR code"
    ScanCategory.MESSAGE -> "Message"
    ScanCategory.PHONE -> "Phone number"
    ScanCategory.APP -> "App"
}

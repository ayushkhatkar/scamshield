package com.scamshield.app.ui.apps

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.scamshield.app.data.AppAuditRepository
import com.scamshield.app.data.AppRisk
import com.scamshield.app.data.ReportHelper
import com.scamshield.app.data.prefs.SettingsRepository
import com.scamshield.app.ui.components.EmptyState
import com.scamshield.app.ui.components.InfoCard
import com.scamshield.app.ui.components.ReasonCard
import com.scamshield.app.ui.components.RiskBadge
import com.scamshield.app.ui.components.SectionTitle
import com.scamshield.app.ui.components.formatTime
import com.scamshield.app.ui.theme.color
import com.scamshield.engine.RiskLevel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AppsViewModel @Inject constructor(
    private val audit: AppAuditRepository,
    settings: SettingsRepository
) : ViewModel() {
    val apps = audit.risks.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val lastScan = audit.lastScanTime.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    val isPremium = settings.settings.map { it.entitlement.isPremium }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)
    val scanning = MutableStateFlow(false)
    val error = MutableStateFlow<String?>(null)

    fun scan() {
        if (scanning.value) return
        viewModelScope.launch {
            scanning.value = true
            try {
                audit.scanNow(notify = false)
            } catch (e: Exception) {
                error.value = e.message ?: "Scan failed"
            } finally {
                scanning.value = false
            }
        }
    }

    fun setIgnored(packageName: String, ignored: Boolean) {
        viewModelScope.launch { audit.setIgnored(packageName, ignored) }
    }
}

@Composable
fun AppsScreen(onPaywall: () -> Unit, viewModel: AppsViewModel = hiltViewModel()) {
    val context = LocalContext.current
    val apps by viewModel.apps.collectAsStateWithLifecycle()
    val lastScan by viewModel.lastScan.collectAsStateWithLifecycle()
    val scanning by viewModel.scanning.collectAsStateWithLifecycle()
    val isPremium by viewModel.isPremium.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()
    var selected by remember { mutableStateOf<AppRisk?>(null) }

    val risky = apps.filter { it.level.isAlertWorthy && !it.ignored }
    val caution = apps.filter { it.level == RiskLevel.CAUTION && !it.ignored }
    val ignored = apps.filter { it.ignored }
    val safeCount = apps.count { it.level == RiskLevel.SAFE }

    LazyColumn(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        item {
            Text("App audit", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.padding(top = 12.dp))
            Text(
                "Finds fake banking apps, predatory loan apps, remote-control tools and apps that can read your OTPs.",
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp)
            )
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 12.dp)) {
                Button(onClick = { viewModel.scan() }, enabled = !scanning) {
                    if (scanning) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(8.dp))
                    }
                    Text(if (apps.isEmpty()) "Scan my apps" else "Scan again")
                }
                Spacer(Modifier.width(12.dp))
                Text(
                    lastScan?.let { "Last scan ${formatTime(it)}" } ?: "Never scanned",
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            error?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 6.dp)) }
            if (!isPremium) {
                InfoCard(
                    title = "Automatic daily audits are a Pro feature",
                    body = "Pro scans every day and alerts you (and your family) the moment a dangerous app is installed, which is usually during a scam call.",
                    modifier = Modifier.padding(top = 12.dp)
                )
                TextButton(onClick = onPaywall) { Text("Upgrade") }
            }
            if (apps.isNotEmpty()) {
                Card(modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
                    Row(modifier = Modifier.padding(14.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
                        Count("${risky.size}", "risky", RiskLevel.DANGEROUS)
                        Count("${caution.size}", "caution", RiskLevel.CAUTION)
                        Count("$safeCount", "clean", RiskLevel.SAFE)
                    }
                }
            }
            SectionTitle("Needs attention")
            if (apps.isEmpty()) EmptyState("Run a scan to see results.") else if (risky.isEmpty()) EmptyState("No risky apps. Nice.")
        }
        items(risky, key = { it.packageName }) { AppRow(it) { selected = it } }
        if (caution.isNotEmpty()) {
            item { SectionTitle("Worth a look") }
            items(caution, key = { it.packageName }) { AppRow(it) { selected = it } }
        }
        if (ignored.isNotEmpty()) {
            item { SectionTitle("Ignored") }
            items(ignored, key = { it.packageName }) { AppRow(it) { selected = it } }
        }
        item { Spacer(Modifier.height(32.dp)) }
    }

    selected?.let { app ->
        AlertDialog(
            onDismissRequest = { selected = null },
            title = { Text(app.appName) },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RiskBadge(app.level)
                        Spacer(Modifier.width(8.dp))
                        Text("${app.score}/100", style = MaterialTheme.typography.bodyMedium)
                    }
                    Text(app.packageName, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("Installed via: ${app.installer ?: "sideloaded / unknown"}", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(8.dp))
                    if (app.reasons.isEmpty()) Text("No risky patterns found.")
                    app.reasons.forEach { ReasonCard(it) }
                }
            },
            confirmButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    OutlinedButton(onClick = { ReportHelper.launch(context, ReportHelper.appInfoIntent(app.packageName), "Could not open app settings.") }) { Text("App info") }
                    Button(onClick = { ReportHelper.launch(context, ReportHelper.uninstallIntent(app.packageName), "Could not start uninstall."); selected = null }) { Text("Uninstall") }
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.setIgnored(app.packageName, !app.ignored); selected = null }) {
                    Text(if (app.ignored) "Un-ignore" else "Ignore")
                }
            }
        )
    }
}

@Composable
private fun Count(value: String, label: String, level: RiskLevel) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = MaterialTheme.typography.headlineMedium, color = level.color())
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun AppRow(app: AppRisk, onClick: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp).clickable(onClick = onClick)) {
        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(app.appName, style = MaterialTheme.typography.titleMedium)
                Text(app.summary.substringAfter(": ", app.summary), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.width(8.dp))
            RiskBadge(app.level)
        }
    }
}

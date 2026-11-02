package com.scamshield.app.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
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
import com.scamshield.app.BuildConfig
import com.scamshield.app.callshield.CallShieldManager
import com.scamshield.app.data.ReportHelper
import com.scamshield.app.data.ScanRepository
import com.scamshield.app.data.prefs.Settings
import com.scamshield.app.data.prefs.SettingsRepository
import com.scamshield.app.ui.components.SectionTitle
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settings: SettingsRepository,
    private val scans: ScanRepository,
    private val callShield: CallShieldManager
) : ViewModel() {
    val prefs = settings.settings.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), Settings())

    fun setAppAuditAlerts(v: Boolean) {
        viewModelScope.launch { settings.setAppAuditAlerts(v) }
    }

    fun clearAllHistory() {
        viewModelScope.launch {
            scans.clearHistory()
            callShield.clearHistory()
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(onBack: () -> Unit, viewModel: SettingsViewModel = hiltViewModel()) {
    val context = LocalContext.current
    val prefs by viewModel.prefs.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings & about") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") } }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
            SectionTitle("Alerts")
            Card(modifier = Modifier.fillMaxWidth()) {
                Row(modifier = Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Dangerous app alerts", style = MaterialTheme.typography.titleMedium)
                        Text("Notify when a daily audit finds a dangerous app (Pro)", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Switch(checked = prefs.appAuditAlerts, onCheckedChange = viewModel::setAppAuditAlerts)
                }
            }

            SectionTitle("How ScamShield protects you")
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(14.dp)) {
                    Bullet("Call Shield uses Android's official Caller ID & spam role to check unknown callers before the phone rings. It never reads your SMS or call log.")
                    Bullet("Link, QR and message checks run on your phone. Only links are optionally checked against Google Safe Browsing.")
                    Bullet("App audit looks at permissions and installer information of the apps on this phone; nothing is uploaded.")
                    Bullet("Family mode shares only alert summaries (never message contents) with the guardians you choose.")
                    Bullet("Numbers you report are shared with the community blocklist without your identity.")
                }
            }

            SectionTitle("If you have been scammed")
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(14.dp)) {
                    Text("1. Call 1930 immediately (national cyber-fraud helpline). The first hour matters most.", style = MaterialTheme.typography.bodyMedium)
                    Text("2. Report at cybercrime.gov.in with screenshots and transaction IDs.", style = MaterialTheme.typography.bodyMedium)
                    Text("3. Call your bank to block cards / freeze UPI, and change passwords.", style = MaterialTheme.typography.bodyMedium)
                    Text("4. Report the number on Sanchar Saathi (Chakshu). Report illegal loan apps to RBI Sachet.", style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.height(8.dp))
                    Row {
                        OutlinedButton(onClick = { ReportHelper.dial1930(context) }) { Text("Call 1930") }
                        Spacer(Modifier.padding(4.dp))
                        TextButton(onClick = { ReportHelper.openUrl(context, ReportHelper.CYBERCRIME_PORTAL) }) { Text("cybercrime.gov.in") }
                    }
                    Row {
                        TextButton(onClick = { ReportHelper.openUrl(context, ReportHelper.SANCHAR_SAATHI) }) { Text("Sanchar Saathi") }
                        TextButton(onClick = { ReportHelper.openUrl(context, ReportHelper.RBI_SACHET) }) { Text("RBI Sachet") }
                    }
                }
            }

            SectionTitle("Your data")
            OutlinedButton(onClick = { viewModel.clearAllHistory() }) { Text("Clear scan and call history") }
            Text(
                "ScamShield ${BuildConfig.VERSION_NAME} · Detection engine ${com.scamshield.engine.ScamShieldEngine.VERSION}",
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 16.dp)
            )
            TextButton(onClick = { ReportHelper.openUrl(context, "https://example.com/scamshield/privacy") }) { Text("Privacy policy") }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun Bullet(text: String) {
    Row(modifier = Modifier.padding(vertical = 3.dp)) {
        Text("•  ", style = MaterialTheme.typography.bodyMedium)
        Text(text, style = MaterialTheme.typography.bodyMedium)
    }
}

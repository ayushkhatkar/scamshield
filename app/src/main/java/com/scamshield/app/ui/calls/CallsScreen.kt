package com.scamshield.app.ui.calls

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.scamshield.app.callshield.CallDecision
import com.scamshield.app.callshield.CallShieldManager
import com.scamshield.app.data.ScamNumberRepository
import com.scamshield.app.data.local.CallEventEntity
import com.scamshield.app.data.local.ScamNumberEntity
import com.scamshield.app.data.prefs.SettingsRepository
import com.scamshield.app.ui.components.EmptyState
import com.scamshield.app.ui.components.InfoCard
import com.scamshield.app.ui.components.ReasonCard
import com.scamshield.app.ui.components.RiskBadge
import com.scamshield.app.ui.components.SectionTitle
import com.scamshield.app.ui.components.formatTime
import com.scamshield.app.ui.scan.ReportNumberDialog
import com.scamshield.app.ui.theme.color
import com.scamshield.engine.RiskLevel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class CallsViewModel @Inject constructor(
    private val callShield: CallShieldManager,
    private val scamNumbers: ScamNumberRepository,
    private val settings: SettingsRepository
) : ViewModel() {
    val roleHeld = MutableStateFlow(callShield.isRoleHeld())
    val supported = callShield.isSupported
    val events = callShield.recentEvents.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val myReports = scamNumbers.localReports.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val blocklistSize = scamNumbers.blocklistSize.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)
    val prefs = settings.settings.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), com.scamshield.app.data.prefs.Settings())
    val checkInput = MutableStateFlow("")
    val checkResult = MutableStateFlow<CallDecision?>(null)

    fun refreshRole() {
        roleHeld.value = callShield.isRoleHeld()
    }

    fun roleIntent() = callShield.roleRequestIntent()

    fun checkNumber() {
        val n = checkInput.value.trim()
        if (n.isEmpty()) return
        viewModelScope.launch { checkResult.value = callShield.evaluate(n) }
    }

    fun report(number: String, label: String) {
        viewModelScope.launch {
            callShield.reportNumber(number, label)
            checkResult.value?.let { if (it.number == number) checkResult.value = callShield.evaluate(number) }
        }
    }

    fun removeReport(number: String) {
        viewModelScope.launch { scamNumbers.removeReport(number) }
    }

    fun setAutoBlock(v: Boolean) {
        viewModelScope.launch { settings.setAutoBlockDangerous(v) }
    }

    fun setBlockInternational(v: Boolean) {
        viewModelScope.launch { settings.setBlockHighRiskInternational(v) }
    }

    fun setBlockTelemarketing(v: Boolean) {
        viewModelScope.launch { settings.setBlockTelemarketing(v) }
    }

    fun setWarnSuspicious(v: Boolean) {
        viewModelScope.launch { settings.setWarnSuspicious(v) }
    }

    fun clearHistory() {
        viewModelScope.launch { callShield.clearHistory() }
    }
}

@Composable
fun CallsScreen(onPaywall: () -> Unit, viewModel: CallsViewModel = hiltViewModel()) {
    val roleHeld by viewModel.roleHeld.collectAsStateWithLifecycle()
    val events by viewModel.events.collectAsStateWithLifecycle()
    val reports by viewModel.myReports.collectAsStateWithLifecycle()
    val blocklistSize by viewModel.blocklistSize.collectAsStateWithLifecycle()
    val prefs by viewModel.prefs.collectAsStateWithLifecycle()
    val checkInput by viewModel.checkInput.collectAsStateWithLifecycle()
    val checkResult by viewModel.checkResult.collectAsStateWithLifecycle()
    var reportFor by remember { mutableStateOf<String?>(null) }

    val roleLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { viewModel.refreshRole() }
    val contactsLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    LifecycleResumeEffect(Unit) {
        viewModel.refreshRole()
        onPauseOrDispose { }
    }

    LazyColumn(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        item {
            Text("Call Shield", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.padding(top = 12.dp))
            Card(
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (roleHeld) RiskLevel.SAFE.color().copy(alpha = 0.12f) else MaterialTheme.colorScheme.primaryContainer
                )
            ) {
                Column(Modifier.padding(16.dp)) {
                    Text(if (roleHeld) "Active" else "Not active", style = MaterialTheme.typography.titleLarge)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        when {
                            !viewModel.supported -> "Automatic call screening needs Android 10 or newer. You can still check numbers manually below."
                            roleHeld -> "ScamShield is your Caller ID & spam app. Calls from $blocklistSize known scam numbers and high-risk countries are blocked before the phone rings."
                            else -> "Make ScamShield your \"Caller ID & spam\" app. Android will then send every unknown incoming call here for a 1-second check."
                        },
                        style = MaterialTheme.typography.bodyMedium
                    )
                    if (!roleHeld && viewModel.supported) {
                        Spacer(Modifier.height(10.dp))
                        Button(onClick = { viewModel.roleIntent()?.let { roleLauncher.launch(it) } }) { Text("Enable Call Shield") }
                    }
                }
            }

            SectionTitle("Blocking rules")
            ToggleRow("Block dangerous calls automatically", "Numbers reported as scams or scoring 70+", prefs.autoBlockDangerous, viewModel::setAutoBlock)
            ToggleRow("Block high-risk international numbers", "+92, +84, +62, +254 and other codes behind most WhatsApp scams", prefs.blockHighRiskInternational, viewModel::setBlockInternational)
            ToggleRow("Block telemarketing (140-series)", "Promotional calls allowed by TRAI but rarely wanted", prefs.blockTelemarketing, viewModel::setBlockTelemarketing)
            ToggleRow("Warn me about suspicious calls", "Show a notification instead of blocking when unsure", prefs.warnSuspicious, viewModel::setWarnSuspicious)

            SectionTitle("Check a number")
            OutlinedTextField(
                value = checkInput,
                onValueChange = { viewModel.checkInput.value = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("+91 98765 43210") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                singleLine = true
            )
            Row(modifier = Modifier.padding(top = 8.dp)) {
                Button(onClick = { viewModel.checkNumber() }, enabled = checkInput.isNotBlank()) { Text("Check") }
                Spacer(Modifier.width(8.dp))
                OutlinedButton(onClick = { contactsLauncher.launch(Manifest.permission.READ_CONTACTS) }) { Text("Allow contacts") }
            }
            checkResult?.let { decision ->
                Card(modifier = Modifier.fillMaxWidth().padding(top = 10.dp)) {
                    Column(Modifier.padding(14.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(decision.number, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                            RiskBadge(decision.result.level)
                        }
                        Spacer(Modifier.height(4.dp))
                        Text(
                            if (decision.block) "Would be blocked" else if (decision.warn) "Would ring with a warning" else "Would ring normally",
                            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        decision.result.reasons.take(3).forEach { ReasonCard(it) }
                        TextButton(onClick = { reportFor = decision.number }) { Text("Report & block this number") }
                    }
                }
            }

            InfoCard(
                title = "Verified Business Caller ID",
                body = "Run a business? Register your outgoing numbers so customers see a verified badge instead of a warning. (B2B plan)",
                modifier = Modifier.padding(top = 16.dp)
            )
            TextButton(onClick = onPaywall) { Text("Learn about business plans") }

            Row(verticalAlignment = Alignment.CenterVertically) {
                SectionTitle("Recent screened calls", Modifier.weight(1f))
                if (events.isNotEmpty()) TextButton(onClick = { viewModel.clearHistory() }) { Text("Clear") }
            }
            if (events.isEmpty()) EmptyState(if (roleHeld) "No unknown calls screened yet." else "Enable Call Shield to start screening.")
        }
        items(events, key = { it.id }) { CallEventRow(it) { reportFor = it.number } }

        item { SectionTitle("Numbers you reported") }
        if (reports.isEmpty()) item { EmptyState("Report a number from any result screen or the check box above.") }
        items(reports, key = { it.number }) { ReportRow(it) { viewModel.removeReport(it.number) } }
        item { Spacer(Modifier.height(32.dp)) }
    }

    reportFor?.let { number ->
        ReportNumberDialog(number = number, onDismiss = { reportFor = null }) { label ->
            viewModel.report(number, label)
            reportFor = null
        }
    }
}

@Composable
private fun ToggleRow(title: String, subtitle: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Row(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Switch(checked = checked, onCheckedChange = onChange)
        }
    }
}

@Composable
private fun CallEventRow(event: CallEventEntity, onReport: () -> Unit) {
    val level = runCatching { RiskLevel.valueOf(event.level) }.getOrDefault(RiskLevel.SAFE)
    Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(event.number, style = MaterialTheme.typography.titleMedium)
                Text("${event.action.lowercase().replaceFirstChar { it.uppercase() }} · ${event.reason}", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(formatTime(event.timestamp), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Column(horizontalAlignment = Alignment.End) {
                RiskBadge(level)
                TextButton(onClick = onReport) { Text("Report") }
            }
        }
    }
}

@Composable
private fun ReportRow(entity: ScamNumberEntity, onRemove: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Row(modifier = Modifier.padding(start = 12.dp, top = 4.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(entity.number, style = MaterialTheme.typography.titleMedium)
                Text(entity.label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            IconButton(onClick = onRemove) { Icon(Icons.Default.Delete, contentDescription = "Remove") }
        }
    }
}

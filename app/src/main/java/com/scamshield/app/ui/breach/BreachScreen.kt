package com.scamshield.app.ui.breach

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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.scamshield.app.data.BreachLookup
import com.scamshield.app.data.BreachRepository
import com.scamshield.app.data.Entitlement
import com.scamshield.app.data.prefs.SettingsRepository
import com.scamshield.app.ui.components.InfoCard
import com.scamshield.app.ui.components.SectionTitle
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
class BreachViewModel @Inject constructor(
    private val breaches: BreachRepository,
    settings: SettingsRepository
) : ViewModel() {
    val entitlement = settings.settings.map { it.entitlement }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), Entitlement.FREE)
    val email = MutableStateFlow("")
    val busy = MutableStateFlow(false)
    val result = MutableStateFlow<BreachLookup?>(null)
    val configured = breaches.isConfigured

    init {
        viewModelScope.launch { settings.settings.collect { s -> if (email.value.isEmpty() && s.breachEmail.isNotEmpty()) email.value = s.breachEmail } }
    }

    fun check() {
        if (busy.value) return
        viewModelScope.launch {
            busy.value = true
            result.value = breaches.check(email.value)
            busy.value = false
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BreachScreen(onBack: () -> Unit, onPaywall: () -> Unit, viewModel: BreachViewModel = hiltViewModel()) {
    val entitlement by viewModel.entitlement.collectAsStateWithLifecycle()
    val email by viewModel.email.collectAsStateWithLifecycle()
    val busy by viewModel.busy.collectAsStateWithLifecycle()
    val result by viewModel.result.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Breach monitor") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") } }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
            Text(
                "Scammers buy leaked e-mail/password lists and use them for 'your account was hacked' calls. Know what has leaked.",
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp, bottom = 12.dp)
            )
            if (!entitlement.isPremium) {
                InfoCard(title = "Pro feature", body = "Breach monitoring is included in Pro and Family plans.")
                TextButton(onClick = onPaywall) { Text("See plans") }
                return@Column
            }
            if (!viewModel.configured) {
                InfoCard(title = "Not configured", body = "Add HIBP_API_KEY to local.properties (haveibeenpwned.com/API/Key) to enable lookups.")
            }
            OutlinedTextField(
                value = email,
                onValueChange = { viewModel.email.value = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("you@example.com") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                singleLine = true
            )
            Spacer(Modifier.height(8.dp))
            Button(onClick = { viewModel.check() }, enabled = !busy && email.contains("@") && viewModel.configured) {
                if (busy) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(8.dp))
                }
                Text("Check")
            }
            when (val r = result) {
                null -> Unit
                BreachLookup.NotConfigured -> Unit
                BreachLookup.Clean -> {
                    SectionTitle("Result")
                    Text("Good news: no known breaches for this e-mail.", color = RiskLevel.SAFE.color(), style = MaterialTheme.typography.titleMedium)
                }
                is BreachLookup.Error -> {
                    SectionTitle("Result")
                    Text(r.message, color = MaterialTheme.colorScheme.error)
                }
                is BreachLookup.Found -> {
                    SectionTitle("Found in ${r.breaches.size} breach(es)")
                    Text(
                        "Change the password on these services and anywhere you reused it. Turn on 2-step verification.",
                        style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(bottom = 8.dp)
                    )
                    r.breaches.forEach { b ->
                        Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                            Column(Modifier.padding(12.dp)) {
                                Row {
                                    Text(b.title.ifBlank { b.name }, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                                    Text(b.breachDate, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                if (b.domain.isNotBlank()) Text(b.domain, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text("Leaked: ${b.dataClasses.joinToString()}", style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(32.dp))
        }
    }
}

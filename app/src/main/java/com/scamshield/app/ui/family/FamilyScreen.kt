package com.scamshield.app.ui.family

import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.scamshield.app.data.FamilyEvent
import com.scamshield.app.data.FamilyMember
import com.scamshield.app.data.FamilyRepository
import com.scamshield.app.data.FamilyRole
import com.scamshield.app.data.ReportHelper
import com.scamshield.app.data.prefs.Settings
import com.scamshield.app.data.prefs.SettingsRepository
import com.scamshield.app.ui.components.EmptyState
import com.scamshield.app.ui.components.InfoCard
import com.scamshield.app.ui.components.RiskBadge
import com.scamshield.app.ui.components.SectionTitle
import com.scamshield.app.ui.components.formatTime
import com.scamshield.app.ui.theme.color
import com.scamshield.engine.RiskLevel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class FamilyViewModel @Inject constructor(
    private val family: FamilyRepository,
    settings: SettingsRepository
) : ViewModel() {
    val available = family.isAvailable
    val prefs = settings.settings.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), Settings())
    val events = settings.settings.flatMapLatest { s ->
        s.familyCode?.let { family.observeEvents(it) } ?: flowOf(emptyList())
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val members = settings.settings.flatMapLatest { s ->
        s.familyCode?.let { family.observeMembers(it) } ?: flowOf(emptyList())
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val busy = MutableStateFlow(false)
    val message = MutableStateFlow<String?>(null)

    fun create(name: String) {
        viewModelScope.launch {
            busy.value = true
            runCatching { family.createFamily(name.ifBlank { "Guardian" }) }
                .onSuccess { message.value = "Family created. Share code $it with the person you want to protect." }
                .onFailure { message.value = it.message ?: "Could not create family" }
            busy.value = false
        }
    }

    fun join(code: String, name: String, role: FamilyRole) {
        viewModelScope.launch {
            busy.value = true
            runCatching { family.joinFamily(code, name.ifBlank { if (role == FamilyRole.PROTECTED) "Family member" else "Guardian" }, role) }
                .onSuccess { message.value = "Joined family $code." }
                .onFailure { message.value = it.message ?: "Could not join" }
            busy.value = false
        }
    }

    fun leave() {
        viewModelScope.launch { family.leaveFamily(); message.value = "Left the family." }
    }

    fun sendTest() {
        viewModelScope.launch { family.postTestEvent(); message.value = "Test alert sent to your guardians." }
    }
}

@Composable
fun FamilyScreen(onPaywall: () -> Unit, viewModel: FamilyViewModel = hiltViewModel()) {
    val context = LocalContext.current
    val prefs by viewModel.prefs.collectAsStateWithLifecycle()
    val events by viewModel.events.collectAsStateWithLifecycle()
    val members by viewModel.members.collectAsStateWithLifecycle()
    val busy by viewModel.busy.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    var name by remember { mutableStateOf("") }
    var code by remember { mutableStateOf("") }

    LazyColumn(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        item {
            Text("Family protection", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.padding(top = 12.dp))
            Text(
                "Link a parent's phone to yours. When their phone blocks a scam call, flags a link or finds a dangerous app, you get an alert and can call them before any money moves.",
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp, bottom = 12.dp)
            )

            if (!viewModel.available) {
                InfoCard(
                    title = "Backend not configured",
                    body = "Family mode needs Firebase. Add app/google-services.json, enable Anonymous Auth + Firestore and deploy the Cloud Function in /firebase (see README). Everything else in ScamShield works without it."
                )
                return@item
            }

            if (!prefs.entitlement.isFamily && prefs.familyCode == null) {
                InfoCard(
                    title = "Family plan feature",
                    body = "You can try it now; linking more than one protected phone and push alerts need the Family plan."
                )
                TextButton(onClick = onPaywall) { Text("See Family plan") }
            }

            message?.let {
                Text(it, color = MaterialTheme.colorScheme.secondary, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(vertical = 6.dp))
            }

            val familyCode = prefs.familyCode
            if (familyCode == null) {
                SectionTitle("Your name")
                OutlinedTextField(value = name, onValueChange = { name = it }, modifier = Modifier.fillMaxWidth(), placeholder = { Text("e.g. Ayush") }, singleLine = true)

                SectionTitle("I want to protect someone")
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(14.dp)) {
                        Text("Create a family and share the code with your parent. Install ScamShield on their phone and enter the code there as \"Protected\".", style = MaterialTheme.typography.bodyMedium)
                        Spacer(Modifier.height(8.dp))
                        Button(onClick = { viewModel.create(name) }, enabled = !busy) { Text("Create family (I'm the guardian)") }
                    }
                }

                SectionTitle("I have a code")
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(14.dp)) {
                        OutlinedTextField(
                            value = code,
                            onValueChange = { code = it.uppercase().take(6) },
                            modifier = Modifier.fillMaxWidth(),
                            placeholder = { Text("6-character code") },
                            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters),
                            singleLine = true
                        )
                        Spacer(Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(onClick = { viewModel.join(code, name, FamilyRole.PROTECTED) }, enabled = !busy && code.length == 6, modifier = Modifier.weight(1f)) {
                                Text("Join as protected")
                            }
                            OutlinedButton(onClick = { viewModel.join(code, name, FamilyRole.GUARDIAN) }, enabled = !busy && code.length == 6, modifier = Modifier.weight(1f)) {
                                Text("Join as guardian")
                            }
                        }
                    }
                }
            } else {
                Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
                    Column(Modifier.padding(16.dp)) {
                        Text(
                            if (prefs.familyRole == FamilyRole.GUARDIAN) "You are a guardian" else "This phone is protected",
                            style = MaterialTheme.typography.titleLarge
                        )
                        Spacer(Modifier.height(4.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Family code: ", style = MaterialTheme.typography.bodyLarge)
                            Text(familyCode, style = MaterialTheme.typography.headlineMedium, fontFamily = FontFamily.Monospace)
                        }
                        Spacer(Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            if (prefs.familyRole == FamilyRole.GUARDIAN) {
                                Button(onClick = {
                                    val send = Intent(Intent.ACTION_SEND).apply {
                                        type = "text/plain"
                                        putExtra(Intent.EXTRA_TEXT, "Install ScamShield from the Play Store, open Family and enter code $familyCode as \"Protected\". I'll get an alert if a scammer tries to reach you.")
                                    }
                                    ReportHelper.launch(context, Intent.createChooser(send, "Share family code"), "No app available to share with.")
                                }) { Text("Share code") }
                            } else {
                                Button(onClick = { viewModel.sendTest() }) { Text("Send test alert") }
                            }
                            OutlinedButton(onClick = { viewModel.leave() }) { Text("Leave") }
                        }
                    }
                }

                SectionTitle("Members")
                if (members.isEmpty()) EmptyState("Loading members…")
            }
        }
        if (prefs.familyCode != null) {
            items(members, key = { it.uid }) { MemberRow(it) }
            item { SectionTitle("Alerts") }
            if (events.isEmpty()) item { EmptyState("No alerts yet. That's good news.") }
            items(events, key = { it.id }) { EventRow(it) }
        }
        item { Spacer(Modifier.height(32.dp)) }
    }
}

@Composable
private fun MemberRow(member: FamilyMember) {
    Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(member.name, style = MaterialTheme.typography.titleMedium)
                Text(member.device, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(
                if (member.role == FamilyRole.GUARDIAN) "Guardian" else "Protected",
                style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary
            )
        }
    }
}

@Composable
private fun EventRow(event: FamilyEvent) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        colors = CardDefaults.cardColors(containerColor = if (event.level.isAlertWorthy) event.level.color().copy(alpha = 0.08f) else MaterialTheme.colorScheme.surface)
    ) {
        Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(event.memberName, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                if (event.level != RiskLevel.SAFE) RiskBadge(event.level)
            }
            Text(event.title, style = MaterialTheme.typography.bodyLarge)
            if (event.detail.isNotBlank()) Text(event.detail, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.width(4.dp))
            Text(formatTime(event.createdAt), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

package com.scamshield.app.ui.home

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Message
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.scamshield.app.data.SecurityCheck
import com.scamshield.app.data.prefs.SettingsRepository
import com.scamshield.app.ui.components.PremiumChip
import com.scamshield.app.ui.components.ScoreRing
import com.scamshield.app.ui.components.SectionTitle
import com.scamshield.app.ui.components.StatPill
import com.scamshield.app.ui.theme.color
import com.scamshield.engine.RiskLevel

private val tips = listOf(
    "A QR code can only take money OUT of your account. Nobody can pay you by making you scan.",
    "No bank, courier or police officer will ever ask for an OTP. The moment they do, hang up.",
    "'Digital arrest' is not a thing. Police do not investigate over video calls or ask for transfers.",
    "KYC is updated inside the official app or at the branch, never through a link in SMS or WhatsApp.",
    "'Earn daily by liking videos' jobs pay small amounts first so you deposit a big amount later.",
    "If a caller asks you to install AnyDesk, TeamViewer or any 'support' app, it is a scam.",
    "Lost money? Call 1930 within the first hour. Fast reporting is how money gets frozen and recovered."
)

@Composable
fun HomeScreen(
    onScan: () -> Unit,
    onCalls: () -> Unit,
    onApps: () -> Unit,
    onFamily: () -> Unit,
    onPaywall: () -> Unit,
    onSettings: () -> Unit,
    onBreach: () -> Unit,
    viewModel: HomeViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val roleLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { viewModel.refresh() }

    LifecycleResumeEffect(Unit) {
        viewModel.refresh()
        onPauseOrDispose { }
    }
    LaunchedEffect(Unit) { viewModel.refresh() }

    val safety = state.safety
    val scoreLevel = when {
        safety == null -> RiskLevel.CAUTION
        safety.score >= 80 -> RiskLevel.SAFE
        safety.score >= 55 -> RiskLevel.CAUTION
        safety.score >= 30 -> RiskLevel.SUSPICIOUS
        else -> RiskLevel.DANGEROUS
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 12.dp)) {
            Column(Modifier.weight(1f)) {
                Text("ScamShield", style = MaterialTheme.typography.headlineMedium)
                Text(
                    if (state.entitlement.isPremium) "${state.entitlement.name.lowercase().replaceFirstChar { it.uppercase() }} plan"
                    else "Free plan · ${state.remainingScans}/${SettingsRepository.FREE_DAILY_SCANS} scans left today",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (state.entitlement.isPremium) PremiumChip()
            IconButton(onClick = onSettings) { Icon(Icons.Default.Settings, contentDescription = "Settings") }
        }

        // Safety score
        Card(
            modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                ScoreRing(score = safety?.score ?: 0, level = scoreLevel, size = 104, label = "safety")
                Spacer(Modifier.width(16.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        when (scoreLevel) {
                            RiskLevel.SAFE -> "Well protected"
                            RiskLevel.CAUTION -> "Mostly protected"
                            RiskLevel.SUSPICIOUS -> "Needs attention"
                            RiskLevel.DANGEROUS -> "At risk"
                        },
                        style = MaterialTheme.typography.titleLarge, color = scoreLevel.color()
                    )
                    Spacer(Modifier.height(6.dp))
                    val failing = safety?.checks?.filter { !it.ok }.orEmpty()
                    if (failing.isEmpty()) {
                        Text("Every check passed. Keep scanning anything that looks odd.", style = MaterialTheme.typography.bodyMedium)
                    } else {
                        failing.take(3).forEach { CheckRow(it) }
                    }
                }
            }
        }

        // Stats
        Card(modifier = Modifier.fillMaxWidth().padding(top = 12.dp)) {
            Row(modifier = Modifier.padding(vertical = 14.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
                StatPill("${state.blockedCalls}", "calls blocked", Modifier.weight(1f))
                StatPill("${state.threatsFound}", "scams caught", Modifier.weight(1f))
                StatPill("${state.riskyApps}", "risky apps", Modifier.weight(1f))
            }
        }

        SectionTitle("Check something")
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            ActionTile("Link", Icons.Default.Link, Modifier.weight(1f), onScan)
            ActionTile("QR code", Icons.Default.QrCodeScanner, Modifier.weight(1f), onScan)
            ActionTile("Message", Icons.Default.Message, Modifier.weight(1f), onScan)
            ActionTile("Number", Icons.Default.Call, Modifier.weight(1f), onCalls)
        }

        SectionTitle("Protection")
        StatusRow(
            title = "Call Shield",
            subtitle = when {
                !state.callShieldSupported -> "Needs Android 10 or newer"
                state.callShieldOn -> "Screening scam calls automatically"
                else -> "Tap to enable: blocks scam calls before your phone rings"
            },
            icon = Icons.Default.Call,
            ok = state.callShieldOn,
            onClick = {
                if (state.callShieldOn || !state.callShieldSupported) onCalls()
                else viewModel.roleRequestIntent()?.let { roleLauncher.launch(it) } ?: onCalls()
            }
        )
        StatusRow(
            title = "App audit",
            subtitle = if (state.riskyApps == 0) "No risky apps found" else "${state.riskyApps} app(s) need attention",
            icon = Icons.Default.Apps,
            ok = state.riskyApps == 0,
            onClick = onApps
        )
        StatusRow(
            title = "Family protection",
            subtitle = if (state.familyLinked) "Linked: alerts go to your family" else "Protect a parent or get protected",
            icon = Icons.Default.Group,
            ok = state.familyLinked,
            onClick = onFamily
        )
        StatusRow(
            title = "Breach monitor",
            subtitle = if (state.entitlement.isPremium) "Check if your e-mail leaked in a data breach" else "Pro: know when your e-mail leaks",
            icon = Icons.Default.Email,
            ok = state.entitlement.isPremium,
            onClick = if (state.entitlement.isPremium) onBreach else onPaywall
        )

        if (!state.entitlement.isPremium) {
            Card(
                modifier = Modifier.fillMaxWidth().padding(top = 20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
            ) {
                Column(Modifier.padding(16.dp)) {
                    Text("Protect your parents too", style = MaterialTheme.typography.titleLarge)
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "Family plan: unlimited scans, automatic daily app audits, breach monitoring and instant alerts when someone you love gets a scam call.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(Modifier.height(10.dp))
                    Button(onClick = onPaywall) { Text("See plans") }
                }
            }
        }

        SectionTitle("Scam of the day")
        val tip = tips[(System.currentTimeMillis() / 86_400_000L % tips.size).toInt()]
        Card(modifier = Modifier.fillMaxWidth()) {
            Text(tip, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(16.dp))
        }
        TextButton(onClick = onSettings, modifier = Modifier.padding(top = 4.dp, bottom = 24.dp)) {
            Text("How ScamShield protects you")
        }
    }
}

@Composable
private fun CheckRow(check: SecurityCheck) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 2.dp)) {
        Icon(
            if (check.ok) Icons.Default.CheckCircle else Icons.Default.Warning,
            contentDescription = null,
            tint = if (check.ok) RiskLevel.SAFE.color() else RiskLevel.CAUTION.color(),
            modifier = Modifier.size(16.dp)
        )
        Spacer(Modifier.width(6.dp))
        Text(check.title, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun ActionTile(label: String, icon: ImageVector, modifier: Modifier, onClick: () -> Unit) {
    Card(modifier = modifier.clickable(onClick = onClick)) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(vertical = 14.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(icon, contentDescription = label, tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(6.dp))
            Text(label, style = MaterialTheme.typography.labelLarge)
        }
    }
}

@Composable
private fun StatusRow(title: String, subtitle: String, icon: ImageVector, ok: Boolean, onClick: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp).clickable(onClick = onClick)) {
        Row(modifier = Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Icon(
                if (ok) Icons.Default.CheckCircle else Icons.Default.Warning,
                contentDescription = null,
                tint = if (ok) RiskLevel.SAFE.color() else RiskLevel.CAUTION.color()
            )
        }
    }
}

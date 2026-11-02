package com.scamshield.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProgressIndicatorDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.scamshield.app.ui.theme.color
import com.scamshield.app.ui.theme.label
import com.scamshield.engine.RiskLevel
import com.scamshield.engine.RiskReason
import java.text.DateFormat
import java.util.Date

@Composable
fun RiskBadge(level: RiskLevel, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .background(level.color().copy(alpha = 0.15f), RoundedCornerShape(50))
            .padding(horizontal = 10.dp, vertical = 4.dp)
    ) {
        Text(level.label(), color = level.color(), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun ScoreRing(score: Int, level: RiskLevel, size: Int = 96, label: String = "risk") {
    Box(contentAlignment = Alignment.Center, modifier = Modifier.size(size.dp)) {
        CircularProgressIndicator(
            progress = { score / 100f },
            modifier = Modifier.size(size.dp),
            color = level.color(),
            strokeWidth = 8.dp,
            trackColor = level.color().copy(alpha = 0.15f),
            strokeCap = ProgressIndicatorDefaults.CircularDeterminateStrokeCap
        )
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("$score", style = MaterialTheme.typography.headlineMedium, color = level.color())
            Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier.padding(top = 20.dp, bottom = 8.dp)
    )
}

@Composable
fun ReasonCard(reason: RiskReason) {
    val positive = reason.weight <= 0
    val tint = when {
        positive -> RiskLevel.SAFE.color()
        reason.weight >= 45 -> RiskLevel.DANGEROUS.color()
        reason.weight >= 25 -> RiskLevel.SUSPICIOUS.color()
        else -> RiskLevel.CAUTION.color()
    }
    Card(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(modifier = Modifier.padding(14.dp), verticalAlignment = Alignment.Top) {
            Icon(
                imageVector = if (positive) Icons.Default.CheckCircle else Icons.Default.Warning,
                contentDescription = null,
                tint = tint,
                modifier = Modifier.size(22.dp).padding(top = 2.dp)
            )
            Spacer(Modifier.width(12.dp))
            Column {
                Text(reason.title, style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(4.dp))
                Text(reason.detail, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
fun InfoCard(title: String, body: String, modifier: Modifier = Modifier, tint: Color = MaterialTheme.colorScheme.secondary) {
    Card(modifier = modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = tint.copy(alpha = 0.12f))) {
        Row(modifier = Modifier.padding(14.dp), verticalAlignment = Alignment.Top) {
            Icon(Icons.Default.Info, contentDescription = null, tint = tint, modifier = Modifier.size(22.dp))
            Spacer(Modifier.width(12.dp))
            Column {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(4.dp))
                Text(body, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

@Composable
fun PremiumChip() {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .background(MaterialTheme.colorScheme.tertiary.copy(alpha = 0.15f), RoundedCornerShape(50))
            .padding(horizontal = 8.dp, vertical = 3.dp)
    ) {
        Icon(Icons.Default.Star, contentDescription = null, tint = MaterialTheme.colorScheme.tertiary, modifier = Modifier.size(14.dp))
        Spacer(Modifier.width(4.dp))
        Text("PRO", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.tertiary)
    }
}

@Composable
fun StatPill(value: String, label: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.primary)
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
fun LevelDot(level: RiskLevel) {
    Box(Modifier.size(12.dp).background(level.color(), CircleShape))
}

@Composable
fun EmptyState(text: String) {
    Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
        Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun SpacedRow(content: @Composable () -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) { content() }
}

fun formatTime(millis: Long): String =
    DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(millis))

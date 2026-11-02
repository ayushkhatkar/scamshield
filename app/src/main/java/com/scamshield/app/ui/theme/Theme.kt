package com.scamshield.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.scamshield.engine.RiskLevel

// Brand palette: trustworthy navy + a calm teal accent. Risk colours are semantic and fixed.
val Navy = Color(0xFF14306B)
val NavyDark = Color(0xFF0B1E47)
val Teal = Color(0xFF0E9A86)
val TealLight = Color(0xFF9FE1D4)
val Sand = Color(0xFFF6F3EC)
val Ink = Color(0xFF131A2B)

val SafeGreen = Color(0xFF2E7D32)
val CautionAmber = Color(0xFFB7791F)
val SuspiciousOrange = Color(0xFFD84315)
val DangerRed = Color(0xFFB3261E)

private val LightColors: ColorScheme = lightColorScheme(
    primary = Navy,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFDCE4FA),
    onPrimaryContainer = NavyDark,
    secondary = Teal,
    onSecondary = Color.White,
    secondaryContainer = TealLight,
    onSecondaryContainer = Color(0xFF00382F),
    tertiary = Color(0xFF7A5C00),
    background = Sand,
    onBackground = Ink,
    surface = Color.White,
    onSurface = Ink,
    surfaceVariant = Color(0xFFE8E6DF),
    onSurfaceVariant = Color(0xFF4A4C50),
    error = DangerRed,
    onError = Color.White,
    outline = Color(0xFFB8B6AF)
)

private val DarkColors: ColorScheme = darkColorScheme(
    primary = Color(0xFFB4C5FF),
    onPrimary = NavyDark,
    primaryContainer = Color(0xFF2B4A9A),
    onPrimaryContainer = Color(0xFFDCE4FA),
    secondary = Color(0xFF7FD6C4),
    onSecondary = Color(0xFF00382F),
    secondaryContainer = Color(0xFF0B5B4F),
    onSecondaryContainer = TealLight,
    background = Color(0xFF0E1322),
    onBackground = Color(0xFFE6E8F0),
    surface = Color(0xFF161C2E),
    onSurface = Color(0xFFE6E8F0),
    surfaceVariant = Color(0xFF2A3045),
    onSurfaceVariant = Color(0xFFC3C6D1),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    outline = Color(0xFF6C7085)
)

val AppTypography = Typography(
    headlineLarge = TextStyle(fontWeight = FontWeight.Bold, fontSize = 30.sp, lineHeight = 36.sp),
    headlineMedium = TextStyle(fontWeight = FontWeight.Bold, fontSize = 24.sp, lineHeight = 30.sp),
    titleLarge = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 20.sp, lineHeight = 26.sp),
    titleMedium = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 16.sp, lineHeight = 22.sp),
    bodyLarge = TextStyle(fontSize = 16.sp, lineHeight = 24.sp),
    bodyMedium = TextStyle(fontSize = 14.sp, lineHeight = 20.sp),
    labelLarge = TextStyle(fontWeight = FontWeight.Medium, fontSize = 14.sp)
)

@Composable
fun ScamShieldTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = AppTypography,
        content = content
    )
}

fun RiskLevel.color(): Color = when (this) {
    RiskLevel.SAFE -> SafeGreen
    RiskLevel.CAUTION -> CautionAmber
    RiskLevel.SUSPICIOUS -> SuspiciousOrange
    RiskLevel.DANGEROUS -> DangerRed
}

fun RiskLevel.label(): String = when (this) {
    RiskLevel.SAFE -> "Safe"
    RiskLevel.CAUTION -> "Caution"
    RiskLevel.SUSPICIOUS -> "Suspicious"
    RiskLevel.DANGEROUS -> "Dangerous"
}

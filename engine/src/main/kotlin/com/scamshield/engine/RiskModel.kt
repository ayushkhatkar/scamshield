package com.scamshield.engine

/**
 * Overall verdict for anything the engine analyses.
 * Thresholds live in [RiskScale] so every analyzer uses the same scale.
 */
enum class RiskLevel {
    SAFE,
    CAUTION,
    SUSPICIOUS,
    DANGEROUS;

    val isAlertWorthy: Boolean get() = this == SUSPICIOUS || this == DANGEROUS
}

/** What kind of thing was analysed. Drives UI copy and storage. */
enum class ScanCategory {
    URL,
    UPI_QR,
    WIFI_QR,
    QR_TEXT,
    MESSAGE,
    PHONE,
    APP
}

/**
 * One human-readable reason contributing to the score.
 *
 * @param code   stable machine identifier (used for analytics / translations)
 * @param title  short headline, e.g. "Pretends to be SBI"
 * @param detail one or two sentences a non-technical person understands
 * @param weight contribution to the 0..100 score (can be negative for positive signals)
 */
data class RiskReason(
    val code: String,
    val title: String,
    val detail: String,
    val weight: Int
)

/** Final result of an analysis. Immutable; use [adding] to combine with extra signals. */
data class RiskResult(
    val category: ScanCategory,
    val subject: String,
    val score: Int,
    val level: RiskLevel,
    val summary: String,
    val recommendation: String,
    val reasons: List<RiskReason>,
    /** Extra structured facts for the UI, e.g. UPI payee, amount, decoded Wi-Fi SSID. */
    val facts: Map<String, String> = emptyMap()
) {
    /** Returns a copy with [reason] added and the score / level recomputed. */
    fun adding(reason: RiskReason): RiskResult {
        val newReasons = reasons + reason
        val newScore = RiskScale.clamp(newReasons.sumOf { it.weight })
        val newLevel = RiskScale.levelFor(newScore)
        return copy(
            score = newScore,
            level = newLevel,
            reasons = newReasons,
            summary = if (newLevel != level) RiskScale.defaultSummary(category, newLevel) else summary,
            recommendation = if (newLevel != level) RiskScale.defaultRecommendation(category, newLevel) else recommendation
        )
    }
}

object RiskScale {
    const val CAUTION_AT = 20
    const val SUSPICIOUS_AT = 45
    const val DANGEROUS_AT = 70

    fun clamp(score: Int): Int = score.coerceIn(0, 100)

    fun levelFor(score: Int): RiskLevel = when {
        score >= DANGEROUS_AT -> RiskLevel.DANGEROUS
        score >= SUSPICIOUS_AT -> RiskLevel.SUSPICIOUS
        score >= CAUTION_AT -> RiskLevel.CAUTION
        else -> RiskLevel.SAFE
    }

    fun defaultSummary(category: ScanCategory, level: RiskLevel): String {
        val noun = when (category) {
            ScanCategory.URL -> "link"
            ScanCategory.UPI_QR -> "payment QR"
            ScanCategory.WIFI_QR -> "Wi-Fi QR"
            ScanCategory.QR_TEXT -> "QR code"
            ScanCategory.MESSAGE -> "message"
            ScanCategory.PHONE -> "number"
            ScanCategory.APP -> "app"
        }
        return when (level) {
            RiskLevel.SAFE -> "No scam signs found in this $noun"
            RiskLevel.CAUTION -> "This $noun has a few warning signs"
            RiskLevel.SUSPICIOUS -> "This $noun looks suspicious"
            RiskLevel.DANGEROUS -> "This $noun is very likely a scam"
        }
    }

    fun defaultRecommendation(category: ScanCategory, level: RiskLevel): String = when (level) {
        RiskLevel.SAFE -> when (category) {
            ScanCategory.PHONE -> "Looks normal. Still never share OTPs, PINs or passwords on a call."
            ScanCategory.APP -> "Nothing risky found. Keep it updated from the Play Store."
            else -> "Looks fine, but never enter passwords or OTPs unless you opened the site yourself."
        }
        RiskLevel.CAUTION -> "Be careful. Verify through an official app or number you already trust before acting."
        RiskLevel.SUSPICIOUS -> "Do not act on this. Confirm with the organisation using their official app or the number printed on your card."
        RiskLevel.DANGEROUS -> "Do not open, pay or reply. Block and report it. If you already shared details, call 1930 (India cyber-fraud helpline) immediately."
    }

    /** Builds a result from a list of reasons using the shared scale. */
    fun build(
        category: ScanCategory,
        subject: String,
        reasons: List<RiskReason>,
        facts: Map<String, String> = emptyMap(),
        summaryOverride: String? = null,
        recommendationOverride: String? = null
    ): RiskResult {
        val score = clamp(reasons.sumOf { it.weight })
        val level = levelFor(score)
        return RiskResult(
            category = category,
            subject = subject,
            score = score,
            level = level,
            summary = summaryOverride ?: defaultSummary(category, level),
            recommendation = recommendationOverride ?: defaultRecommendation(category, level),
            reasons = reasons.sortedByDescending { it.weight },
            facts = facts
        )
    }
}

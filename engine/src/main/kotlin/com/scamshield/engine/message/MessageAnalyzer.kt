package com.scamshield.engine.message

import com.scamshield.engine.RiskReason
import com.scamshield.engine.RiskResult
import com.scamshield.engine.RiskScale
import com.scamshield.engine.ScanCategory
import com.scamshield.engine.url.UrlAnalyzer

/**
 * Offline scam detection for SMS / WhatsApp / email text. Works on pasted text, shared text
 * (Android share sheet) and selected text (PROCESS_TEXT). Returns plain-language reasons so the
 * result can be read aloud to a parent.
 */
class MessageAnalyzer(private val urlAnalyzer: UrlAnalyzer = UrlAnalyzer()) {

    companion object {
        val URL_REGEX = Regex(
            """(?i)\b((?:https?://|www\.)[^\s<>"'()\[\]]+|(?:[a-z0-9][a-z0-9-]*\.)+(?:com|in|net|org|co|xyz|top|club|info|site|online|link|click|app|io|me|ly|gl|shop|store|live|icu|buzz|cam|cc|tk|ml|ga|cf|gq|work|zip|mov|pro|biz|vip|win|loan|money|cash|help|support|page|dev|sh|to|gd|id|ru|cn|su|pw)(?:/[^\s<>"'()\[\]]*)?)"""
        )
        val PHONE_REGEX = Regex("""(?:\+?91[\s-]?)?(?<!\d)[6-9]\d{9}(?!\d)""")
        val UPI_ID_REGEX = Regex("""(?i)\b[a-z0-9._-]{3,}@[a-z]{2,}\b""")

        /** Patterns that represent an "ask" — needed before brand mentions count heavily. */
        private val ACTION_CODES = setOf(
            "OTP_REQUEST", "KYC_BLOCK", "PAYMENT_ASK", "REMOTE_ACCESS", "URGENCY", "LOTTERY", "TASK_JOB",
            "INVESTMENT", "LOAN", "REWARD_POINTS", "DELIVERY_FEE", "ELECTRICITY", "DIGITAL_ARREST",
            "WRONG_TRANSFER", "FAMILY_EMERGENCY", "SEXTORTION", "CREDENTIAL_LINK_BAIT"
        )
    }

    data class Extracted(val urls: List<String>, val phones: List<String>, val upiIds: List<String>)

    fun extract(text: String): Extracted {
        val urls = URL_REGEX.findAll(text).map { it.value.trimEnd('.', ',', ';', ':', '!', '?') }
            .filter { !it.contains('@') || it.startsWith("http", ignoreCase = true) }
            .distinct().toList()
        val phones = PHONE_REGEX.findAll(text).map { it.value.filter { c -> c.isDigit() }.takeLast(10) }.distinct().toList()
        val upiIds = UPI_ID_REGEX.findAll(text).map { it.value }
            .filter { id -> !id.substringAfter('@').let { d -> d.contains('.') } } // exclude e-mails
            .distinct().toList()
        return Extracted(urls, phones, upiIds)
    }

    fun analyze(raw: String, senderHint: String? = null): RiskResult {
        val text = raw.trim()
        val normalized = " " + text.lowercase().replace(Regex("""\s+"""), " ") + " "
        val reasons = mutableListOf<RiskReason>()
        val facts = mutableMapOf<String, String>()

        if (text.isBlank()) {
            return RiskScale.build(ScanCategory.MESSAGE, text, emptyList(), summaryOverride = "Nothing to analyse")
        }

        val extracted = extract(text)
        if (extracted.urls.isNotEmpty()) facts["links"] = extracted.urls.joinToString("\n")
        if (extracted.phones.isNotEmpty()) facts["phones"] = extracted.phones.joinToString(", ")
        if (extracted.upiIds.isNotEmpty()) facts["upiIds"] = extracted.upiIds.joinToString(", ")

        val hitCodes = mutableSetOf<String>()
        val matchedPatterns = mutableListOf<Pair<ScamPattern, List<String>>>()
        for (pattern in ScamPatterns.ALL) {
            val hits = pattern.phrases.filter { normalized.contains(it) }
            if (hits.size >= pattern.minHits) {
                hitCodes += pattern.code
                matchedPatterns += pattern to hits
            }
        }
        val hasAction = hitCodes.any { it in ACTION_CODES } || extracted.urls.isNotEmpty()

        for ((pattern, hits) in matchedPatterns) {
            val bonus = ((hits.size - pattern.minHits) * 5).coerceIn(0, 15)
            val weight = when {
                pattern.code == "IMPERSONATION" && !hasAction -> 5
                pattern.code == "CREDENTIAL_LINK_BAIT" && !hasAction && extracted.urls.isEmpty() -> 5
                else -> pattern.weight + bonus
            }
            reasons += RiskReason(
                pattern.code,
                pattern.title,
                pattern.explanation + " Found: \"" + hits.take(3).joinToString("\", \"") + "\".",
                weight
            )
        }

        // Links inside the message are analysed individually.
        var worstLink: RiskResult? = null
        for (url in extracted.urls.take(5)) {
            val r = urlAnalyzer.analyze(url)
            if (worstLink == null || r.score > worstLink.score) worstLink = r
        }
        worstLink?.let { link ->
            val weight = (link.score * 0.6).toInt()
            if (weight > 0) {
                reasons += RiskReason(
                    "LINK_" + link.level.name,
                    "Contains a ${link.level.name.lowercase()} link",
                    link.summary + ": " + link.reasons.firstOrNull()?.title.orEmpty(),
                    weight
                )
            }
            facts["worstLink"] = link.subject
            facts["worstLinkScore"] = link.score.toString()
        }

        // Style signals.
        val letters = text.filter { it.isLetter() }
        if (letters.length > 40 && letters.count { it.isUpperCase() } > letters.length * 0.4) {
            reasons += RiskReason("SHOUTING", "Written in capitals", "Scam messages shout to create panic.", 5)
        }
        if (text.count { it == '!' } >= 3) {
            reasons += RiskReason("EXCLAMATION", "Lots of exclamation marks", "Emotional pressure is a scam tactic.", 5)
        }

        // Sender hint: an unknown international or personal number pretending to be an institution.
        senderHint?.let { sender ->
            val s = sender.trim()
            val international = s.startsWith("+") && !s.startsWith("+91")
            if (international && hitCodes.isNotEmpty()) {
                reasons += RiskReason(
                    "FOREIGN_SENDER", "Sent from a foreign number ($s)",
                    "Indian banks and government bodies do not message from international numbers.", 20
                )
            } else if (s.matches(Regex("""\+?91?[6-9]\d{9}""")) && "IMPERSONATION" in hitCodes) {
                reasons += RiskReason(
                    "PERSONAL_SENDER", "Official-sounding message from a personal mobile number",
                    "Real bank and government SMS come from 6-character sender IDs (e.g. VM-SBIINB), not 10-digit mobile numbers.", 20
                )
            }
        }

        val summary = when {
            reasons.isEmpty() -> "No scam signs found in this message"
            else -> {
                val top = reasons.maxByOrNull { it.weight }!!
                when (RiskScale.levelFor(RiskScale.clamp(reasons.sumOf { it.weight }))) {
                    com.scamshield.engine.RiskLevel.DANGEROUS -> "Very likely a scam: ${top.title}"
                    com.scamshield.engine.RiskLevel.SUSPICIOUS -> "Suspicious: ${top.title}"
                    com.scamshield.engine.RiskLevel.CAUTION -> "Be careful: ${top.title}"
                    com.scamshield.engine.RiskLevel.SAFE -> "Looks okay, minor flags: ${top.title}"
                }
            }
        }

        return RiskScale.build(ScanCategory.MESSAGE, text, reasons, facts, summaryOverride = summary)
    }
}

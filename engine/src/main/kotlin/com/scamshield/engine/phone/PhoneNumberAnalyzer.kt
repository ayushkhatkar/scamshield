package com.scamshield.engine.phone

import com.scamshield.engine.RiskReason
import com.scamshield.engine.RiskResult
import com.scamshield.engine.RiskScale
import com.scamshield.engine.ScanCategory

/**
 * Scores an incoming caller / sender number. Combines:
 *  - numbering-plan knowledge (TRAI 140/160 series, toll-free, international prefixes)
 *  - community / backend reports passed in via [PhoneContext]
 *  - whether the number is a saved contact
 */
class PhoneNumberAnalyzer {

    data class PhoneContext(
        val inContacts: Boolean = false,
        /** Number of community reports for this exact number (from your DB / backend). */
        val communityReports: Int = 0,
        /** Label from the scam database if present, e.g. "Loan harassment", "Digital arrest". */
        val reportLabel: String? = null,
        /** Verified business name if the number is in the paid "Verified Caller" registry. */
        val verifiedBusiness: String? = null,
        /** How many calls from this number in the last 24h (rapid redial pressure). */
        val recentCalls: Int = 0
    )

    companion object {
        /** Country codes seen in a very large share of WhatsApp/voice scams targeting India. */
        val HIGH_RISK_COUNTRY_CODES = mapOf(
            "92" to "Pakistan", "84" to "Vietnam", "62" to "Indonesia", "254" to "Kenya", "60" to "Malaysia",
            "212" to "Morocco", "63" to "Philippines", "880" to "Bangladesh", "234" to "Nigeria", "7" to "Russia/Kazakhstan",
            "855" to "Cambodia", "95" to "Myanmar", "856" to "Laos", "66" to "Thailand", "251" to "Ethiopia",
            "233" to "Ghana", "225" to "Ivory Coast", "243" to "DR Congo", "216" to "Tunisia", "20" to "Egypt"
        )

        /** Strips spaces, dashes, brackets. Keeps a leading '+'. */
        fun normalize(raw: String): String {
            val trimmed = raw.trim()
            val plus = trimmed.startsWith("+") || trimmed.startsWith("00")
            val digits = trimmed.filter { it.isDigit() }
            val body = if (trimmed.startsWith("00")) digits.drop(2) else digits
            return if (plus) "+$body" else body
        }

        /** Canonical E.164-ish key for storage: Indian 10-digit numbers become +91XXXXXXXXXX. */
        fun canonical(raw: String): String {
            val n = normalize(raw)
            return when {
                n.startsWith("+") -> n
                n.length == 10 && n[0] in '6'..'9' -> "+91$n"
                n.length == 11 && n.startsWith("0") && n[1] in '6'..'9' -> "+91${n.drop(1)}"
                n.length == 12 && n.startsWith("91") && n[2] in '6'..'9' -> "+$n"
                else -> n
            }
        }
    }

    fun analyze(rawNumber: String?, context: PhoneContext = PhoneContext()): RiskResult {
        val number = rawNumber?.let { normalize(it) }.orEmpty()
        val reasons = mutableListOf<RiskReason>()
        val facts = mutableMapOf<String, String>()

        if (number.isEmpty()) {
            reasons += RiskReason(
                "HIDDEN_NUMBER", "Caller ID hidden",
                "The caller deliberately hid their number. Legitimate businesses don't do this.", 35
            )
            return RiskScale.build(ScanCategory.PHONE, "Hidden number", reasons, facts)
        }
        facts["number"] = canonical(number)

        context.verifiedBusiness?.let {
            facts["verifiedBusiness"] = it
            reasons += RiskReason("VERIFIED_BUSINESS", "Verified business: $it", "This number is registered and verified in the ScamShield business directory.", -40)
        }
        if (context.inContacts) {
            reasons += RiskReason("IN_CONTACTS", "Saved contact", "This number is in your contacts.", -30)
        }
        if (context.communityReports > 0) {
            val label = context.reportLabel ?: "scam / spam"
            reasons += RiskReason(
                "COMMUNITY_REPORTED", "Reported ${context.communityReports}× as $label",
                "Other ScamShield users reported this number. Do not answer, and never call back.",
                (50 + context.communityReports * 5).coerceAtMost(80)
            )
        }

        // Indian numbering plan rules.
        val domestic = when {
            number.startsWith("+91") -> number.drop(3)
            number.startsWith("91") && number.length == 12 -> number.drop(2)
            number.startsWith("0") && number.length == 11 -> number.drop(1)
            !number.startsWith("+") -> number
            else -> null
        }
        if (domestic != null) {
            when {
                domestic.startsWith("140") -> reasons += RiskReason(
                    "TELEMARKETING_140", "Telemarketing number (140-series)",
                    "TRAI reserves the 140 prefix for promotional calls. Usually spam, not fraud.", 25
                )
                domestic.startsWith("160") -> reasons += RiskReason(
                    "SERVICE_160", "Transactional / service call (160-series)",
                    "TRAI's 160 prefix is allotted to banks, insurers and regulated companies for genuine service calls. Still, never share an OTP.", -10
                )
                domestic.startsWith("1800") || domestic.startsWith("1860") -> reasons += RiskReason(
                    "TOLL_FREE", "Toll-free business line", "Likely a company helpline.", -5
                )
                domestic.length == 10 && domestic[0] in '6'..'9' && !context.inContacts -> reasons += RiskReason(
                    "UNKNOWN_MOBILE", "Unknown personal mobile number",
                    "Banks, courier companies and government offices call from registered lines, not personal mobiles.", 10
                )
                domestic.length < 7 && domestic.all { it.isDigit() } -> reasons += RiskReason(
                    "SHORT_CODE", "Short code", "Operator or service short code.", 0
                )
            }
        } else if (number.startsWith("+")) {
            val cc = HIGH_RISK_COUNTRY_CODES.keys.sortedByDescending { it.length }.firstOrNull { number.startsWith("+$it") }
            if (cc != null && !context.inContacts) {
                reasons += RiskReason(
                    "HIGH_RISK_COUNTRY", "International number from ${HIGH_RISK_COUNTRY_CODES[cc]} (+$cc)",
                    "A very large share of WhatsApp and voice scams targeting India come from this country code. Do not answer or call back.", 45
                )
            } else if (!context.inContacts) {
                reasons += RiskReason(
                    "INTERNATIONAL", "International number",
                    "Unexpected international calls are often scams or expensive callback traps.", 20
                )
            }
        }

        if (context.recentCalls >= 3) {
            reasons += RiskReason(
                "RAPID_REDIAL", "Called ${context.recentCalls} times today",
                "Repeated calls are pressure. Genuine callers leave a message.", 10
            )
        }

        return RiskScale.build(ScanCategory.PHONE, canonical(number), reasons, facts)
    }
}

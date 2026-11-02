package com.scamshield.engine.url

import com.scamshield.engine.RiskReason
import com.scamshield.engine.RiskResult
import com.scamshield.engine.RiskScale
import com.scamshield.engine.ScanCategory
import java.net.URLDecoder

/**
 * Explains a UPI payment QR / deep link in plain language and scores it.
 *
 * The single most important fact we surface: scanning a QR can only ever take money OUT of your
 * account. "Scan this to receive your refund/prize" is always a scam.
 */
class UpiQrAnalyzer {

    companion object {
        private val UPI_SCHEMES = listOf("upi://", "paytmmp://", "phonepe://", "gpay://", "tez://", "bhim://")

        fun isUpiLink(text: String): Boolean {
            val t = text.trim().lowercase()
            return UPI_SCHEMES.any { t.startsWith(it) } || t.contains("upi://pay") || t.contains("upi://collect") || t.contains("upi://mandate")
        }

        /** Common UPI handles (PSP suffixes). Unknown handles are not necessarily bad, just unverified. */
        val KNOWN_HANDLES = setOf(
            "okaxis", "oksbi", "okhdfcbank", "okicici", "ybl", "ibl", "axl", "paytm", "apl", "upi", "sbi", "hdfcbank",
            "icici", "axisbank", "kotak", "jio", "airtel", "freecharge", "fbl", "yapl", "waaxis", "wahdfcbank",
            "waicici", "wasbi", "ptyes", "ptaxis", "pthdfc", "ptsbi", "axb", "naviaxis", "slice", "superyes",
            "tapicici", "yespop", "axisb", "barodampay", "cnrb", "pnb", "idfcbank", "indus", "kbl", "federal",
            "rbl", "dbs", "sc", "ikwik", "mahb", "boi", "cbin", "ucobank", "uboi", "allbank", "aubank", "idbi",
            "jupiteraxis", "timecosmos", "pingpay", "amazonpay", "apl", "okbizaxis", "yesbank", "icicibank",
            "hsbc", "citi", "bandhan", "csbpay", "dlb", "equitas", "esaf", "fino", "jkb", "karb", "kvb", "lvb",
            "nsdl", "psb", "sib", "tjsb", "uco", "utbi", "vijb", "yesb", "ybl", "axisgo", "postbank", "ippb"
        )

        private val INSTITUTION_WORDS = listOf(
            "refund", "cashback", "reward", "rewards", "lottery", "kbc", "lucky", "prize", "winner", "customer care",
            "customercare", "support", "helpdesk", "help desk", "police", "cbi", "rbi", "sbi", "hdfc", "icici",
            "axis", "kotak", "paytm", "phonepe", "gpay", "google pay", "bank", "govt", "government", "income tax",
            "electricity", "bijli", "verification", "kyc", "insurance", "loan", "job", "task", "telegram"
        )
        private val NOTE_WORDS = listOf(
            "refund", "cashback", "reward", "receive", "claim", "verification", "verify", "kyc", "unblock",
            "activate", "prize", "winner", "bonus", "return", "wrong transfer", "wrongly"
        )
    }

    fun analyze(raw: String): RiskResult {
        val text = raw.trim()
        val params = parseParams(text)
        val action = text.substringAfter("://", "").substringBefore("?").lowercase().trim('/')
        val payee = params["pa"].orEmpty()
        val name = params["pn"].orEmpty()
        val amount = params["am"]?.replace(",", "")?.toDoubleOrNull()
        val note = params["tn"].orEmpty()
        val merchantCode = params["mc"].orEmpty()
        val url = params["url"].orEmpty()

        val reasons = mutableListOf<RiskReason>()
        val facts = mutableMapOf<String, String>()
        if (payee.isNotEmpty()) facts["payee"] = payee
        if (name.isNotEmpty()) facts["payeeName"] = name
        if (amount != null) facts["amount"] = formatInr(amount)
        if (note.isNotEmpty()) facts["note"] = note
        if (merchantCode.isNotEmpty()) facts["merchantCode"] = merchantCode

        when {
            action.contains("collect") -> reasons += RiskReason(
                "UPI_COLLECT", "Money REQUEST, not a payment to you",
                "This is a UPI collect request. If you approve it, money leaves YOUR account. Nobody needs you to approve anything to send you money.", 45
            )
            action.contains("mandate") -> reasons += RiskReason(
                "UPI_MANDATE", "Sets up an AutoPay mandate",
                "Approving this lets the other party pull money from your account repeatedly. Only do this for subscriptions you chose yourself.", 35
            )
            else -> reasons += RiskReason(
                "UPI_PAYS_OUT", "Scanning this sends money FROM you",
                if (amount != null) "This QR will debit ${formatInr(amount)} from your account. A QR code can never give you money, so ignore anyone who says 'scan to receive'."
                else "Paying this QR debits your account. A QR code can never give you money, so ignore anyone who says 'scan to receive'.",
                if (amount != null) 10 else 5
            )
        }

        if (amount != null && amount >= 10_000) {
            reasons += RiskReason("LARGE_AMOUNT", "Large amount pre-filled", "${formatInr(amount)} is pre-filled. Double-check the payee before confirming.", 10)
        }

        val lowerName = name.lowercase()
        val nameHit = INSTITUTION_WORDS.firstOrNull { lowerName.contains(it) }
        if (nameHit != null) {
            reasons += RiskReason(
                "PAYEE_IMPERSONATION", "Payee name claims to be '$name'",
                "Real banks, government offices and companies never collect money through a personal UPI ID. The name is typed by whoever made the QR and can say anything.", 40
            )
        }
        val lowerNote = note.lowercase()
        val noteHit = NOTE_WORDS.firstOrNull { lowerNote.contains(it) }
        if (noteHit != null) {
            reasons += RiskReason(
                "NOTE_BAIT", "Payment note mentions '$noteHit'",
                "Words like refund, cashback, prize or KYC in a payment note are bait. Paying never 'unlocks' money for you.", 30
            )
        }

        if (payee.isNotEmpty()) {
            val handle = payee.substringAfterLast('@', "").lowercase()
            val user = payee.substringBeforeLast('@')
            if (handle.isEmpty()) {
                reasons += RiskReason("MALFORMED_VPA", "Malformed UPI ID", "The payee ID '$payee' is not a valid UPI address.", 30)
            } else {
                if (handle !in KNOWN_HANDLES) {
                    reasons += RiskReason("UNKNOWN_HANDLE", "Unrecognised UPI handle (@$handle)", "Could not verify this UPI provider. Confirm the payee before paying.", 10)
                }
                if (merchantCode.isEmpty() || merchantCode == "0000") {
                    reasons += RiskReason(
                        "PERSONAL_VPA", "Paying a person, not a registered merchant",
                        "No merchant code: this goes to an individual's account. Fine for a street vendor, a red flag for anything claiming to be a company or office.", 10
                    )
                }
                if (user.all { it.isDigit() } && user.length == 10) {
                    facts["payeePhone"] = user
                }
            }
        } else {
            reasons += RiskReason("NO_PAYEE", "No payee in the QR", "This payment code has no recipient address, which is not normal.", 25)
        }

        if (url.isNotEmpty()) {
            val urlResult = UrlAnalyzer().analyze(url)
            facts["url"] = url
            if (urlResult.level.isAlertWorthy) {
                reasons += RiskReason("EMBEDDED_URL", "Contains a suspicious web link", urlResult.summary, 25)
            }
        }

        val summary = buildString {
            append(if (action.contains("collect")) "This asks you to SEND " else "This QR will PAY ")
            append(amount?.let { formatInr(it) } ?: "an amount you enter")
            if (name.isNotEmpty()) append(" to \"$name\"")
            if (payee.isNotEmpty()) append(" ($payee)")
        }

        return RiskScale.build(ScanCategory.UPI_QR, text, reasons, facts, summaryOverride = summary)
    }

    private fun parseParams(text: String): Map<String, String> {
        val query = text.substringAfter('?', "")
        if (query.isEmpty()) return emptyMap()
        return query.split('&').mapNotNull { pair ->
            val k = pair.substringBefore('=').lowercase()
            val v = pair.substringAfter('=', "")
            if (k.isEmpty()) null else k to decode(v)
        }.toMap()
    }

    private fun decode(value: String): String = try {
        URLDecoder.decode(value, "UTF-8")
    } catch (e: Exception) {
        value
    }

    private fun formatInr(amount: Double): String {
        val whole = amount.toLong()
        val isWhole = amount == whole.toDouble()
        val s = if (isWhole) whole.toString() else String.format("%.2f", amount)
        // Indian digit grouping: 12,34,567
        val intPart = s.substringBefore('.')
        val frac = if (s.contains('.')) "." + s.substringAfter('.') else ""
        if (intPart.length <= 3) return "₹$intPart$frac"
        val last3 = intPart.takeLast(3)
        val rest = intPart.dropLast(3)
        val grouped = rest.reversed().chunked(2).joinToString(",").reversed()
        return "₹$grouped,$last3$frac"
    }
}

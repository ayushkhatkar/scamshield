package com.scamshield.engine

import com.scamshield.engine.app.AppRiskScorer
import com.scamshield.engine.app.InstalledAppInfo
import com.scamshield.engine.message.MessageAnalyzer
import com.scamshield.engine.phone.PhoneNumberAnalyzer
import com.scamshield.engine.url.UpiQrAnalyzer
import com.scamshield.engine.url.UrlAnalyzer

/**
 * Single entry point. This is the surface you would license to a bank or fintech app:
 *
 * ```
 * val engine = ScamShieldEngine()
 * engine.analyzeQr(rawQrValue)      // UPI / URL / Wi-Fi / text
 * engine.analyzeMessage(smsBody)    // SMS, WhatsApp, e-mail text
 * engine.analyzeUrl(link)
 * engine.analyzePhone("+92...", PhoneNumberAnalyzer.PhoneContext(inContacts = false))
 * engine.scoreApp(installedAppInfo)
 * ```
 */
class ScamShieldEngine(
    val urlAnalyzer: UrlAnalyzer = UrlAnalyzer(),
    val upiAnalyzer: UpiQrAnalyzer = UpiQrAnalyzer(),
    val messageAnalyzer: MessageAnalyzer = MessageAnalyzer(urlAnalyzer),
    val phoneAnalyzer: PhoneNumberAnalyzer = PhoneNumberAnalyzer(),
    val appScorer: AppRiskScorer = AppRiskScorer()
) {
    companion object {
        const val VERSION = "1.0.0"
    }

    fun analyzeUrl(url: String): RiskResult = urlAnalyzer.analyze(url)

    fun analyzeMessage(text: String, senderHint: String? = null): RiskResult = messageAnalyzer.analyze(text, senderHint)

    fun analyzePhone(number: String?, context: PhoneNumberAnalyzer.PhoneContext = PhoneNumberAnalyzer.PhoneContext()): RiskResult =
        phoneAnalyzer.analyze(number, context)

    fun scoreApp(app: InstalledAppInfo): RiskResult = appScorer.score(app)

    /**
     * Smart entry point for anything the user pastes or shares: decides whether it is a link,
     * a UPI deep link, a phone number or free text.
     */
    fun analyzeAny(input: String): RiskResult {
        val t = input.trim()
        return when {
            t.isEmpty() -> RiskScale.build(ScanCategory.MESSAGE, t, emptyList(), summaryOverride = "Nothing to analyse")
            UpiQrAnalyzer.isUpiLink(t) -> upiAnalyzer.analyze(t)
            isSingleUrl(t) -> urlAnalyzer.analyze(t)
            isPhoneNumber(t) -> phoneAnalyzer.analyze(t)
            else -> messageAnalyzer.analyze(t)
        }
    }

    /** Routes a raw QR payload to the right analyzer. */
    fun analyzeQr(rawValue: String): RiskResult {
        val t = rawValue.trim()
        val lower = t.lowercase()
        return when {
            UpiQrAnalyzer.isUpiLink(t) -> upiAnalyzer.analyze(t)
            lower.startsWith("wifi:") -> analyzeWifiQr(t)
            lower.startsWith("tel:") || lower.startsWith("sms:") || lower.startsWith("smsto:") -> {
                val number = t.substringAfter(':').substringBefore('?').substringBefore(':')
                val base = phoneAnalyzer.analyze(number)
                base.adding(
                    RiskReason(
                        "QR_DIALS", "QR code opens the phone dialler / SMS",
                        "Scanning this starts a call or SMS to $number. Scam posters use this to connect you to a fake helpline or a premium-rate number.", 20
                    )
                ).copy(category = ScanCategory.QR_TEXT, subject = t)
            }
            lower.startsWith("mailto:") -> RiskScale.build(
                ScanCategory.QR_TEXT, t,
                listOf(RiskReason("QR_EMAIL", "Opens an e-mail to ${t.substringAfter(':').substringBefore('?')}", "Only write if you expected this.", 5)),
                mapOf("email" to t.substringAfter(':').substringBefore('?'))
            )
            lower.startsWith("begin:vcard") || lower.startsWith("mecard:") -> RiskScale.build(
                ScanCategory.QR_TEXT, t, listOf(RiskReason("QR_CONTACT", "Contact card", "This QR adds a contact. Check the details before saving.", 0))
            )
            lower.startsWith("geo:") -> RiskScale.build(
                ScanCategory.QR_TEXT, t, listOf(RiskReason("QR_LOCATION", "Map location", "Opens a location in your maps app.", 0))
            )
            lower.startsWith("market://") || lower.contains("play.google.com/store") -> RiskScale.build(
                ScanCategory.QR_TEXT, t,
                listOf(RiskReason("QR_PLAY_STORE", "Opens a Play Store listing", "Installing from the Play Store is safe, but check the developer name matches the real company.", 5)),
                mapOf("url" to t)
            )
            isSingleUrl(t) -> urlAnalyzer.analyze(t)
            isPhoneNumber(t) -> phoneAnalyzer.analyze(t)
            else -> messageAnalyzer.analyze(t).copy(category = ScanCategory.QR_TEXT)
        }
    }

    private fun analyzeWifiQr(raw: String): RiskResult {
        // WIFI:T:WPA;S:MyNetwork;P:secret;H:false;;
        val body = raw.substring(5)
        val fields = body.split(';').mapNotNull {
            val k = it.substringBefore(':', "")
            val v = it.substringAfter(':', "")
            if (k.isEmpty()) null else k.uppercase() to v
        }.toMap()
        val ssid = fields["S"].orEmpty()
        val type = fields["T"].orEmpty().uppercase()
        val reasons = mutableListOf<RiskReason>()
        if (type.isEmpty() || type == "NOPASS") {
            reasons += RiskReason("OPEN_WIFI", "Open network without a password", "Anything you do on an open Wi-Fi can be read by others nearby. Avoid banking on it.", 30)
        } else if (type == "WEP") {
            reasons += RiskReason("WEAK_WIFI", "Uses outdated WEP security", "WEP can be cracked in minutes.", 25)
        }
        val lowerSsid = ssid.lowercase()
        if (listOf("free", "airport", "railway", "station", "bank", "govt", "wifi_free", "public").any { lowerSsid.contains(it) }) {
            reasons += RiskReason("LURE_SSID", "Network name designed to attract", "Fake hotspots named like public or official networks are used to intercept logins.", 15)
        }
        return RiskScale.build(
            ScanCategory.WIFI_QR, raw, reasons,
            mapOf("ssid" to ssid, "security" to type.ifEmpty { "none" }),
            summaryOverride = "Joins Wi-Fi network \"$ssid\" (${type.ifEmpty { "no password" }})"
        )
    }

    private fun isSingleUrl(t: String): Boolean {
        if (t.any { it.isWhitespace() }) return false
        val lower = t.lowercase()
        if (lower.startsWith("http://") || lower.startsWith("https://") || lower.startsWith("www.")) return true
        return MessageAnalyzer.URL_REGEX.matchEntire(t) != null
    }

    private fun isPhoneNumber(t: String): Boolean {
        val digits = t.filter { it.isDigit() }
        val other = t.filter { !it.isDigit() && !it.isWhitespace() && it != '+' && it != '-' && it != '(' && it != ')' }
        return other.isEmpty() && digits.length in 7..15
    }
}

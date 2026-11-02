package com.scamshield.engine.app

import com.scamshield.engine.RiskReason
import com.scamshield.engine.RiskResult
import com.scamshield.engine.RiskScale
import com.scamshield.engine.ScanCategory

/** Platform-independent description of an installed app (the Android layer fills this from PackageManager). */
data class InstalledAppInfo(
    val packageName: String,
    val appName: String,
    val requestedPermissions: List<String>,
    val grantedPermissions: Set<String>,
    /** Package name of the installer, e.g. "com.android.vending". Null = sideloaded / unknown. */
    val installerPackage: String?,
    val isSystemApp: Boolean,
    val targetSdk: Int,
    val firstInstallTimeMillis: Long,
    val declaresAccessibilityService: Boolean = false,
    val declaresDeviceAdmin: Boolean = false,
    /** Now, injected for testability. */
    val nowMillis: Long = System.currentTimeMillis()
)

/**
 * Flags fake banking apps, predatory loan apps, remote-control tools and permission abuse.
 * Pure Kotlin so it is unit-testable and reusable inside the paid SDK.
 */
class AppRiskScorer {

    companion object {
        const val PERM_READ_SMS = "android.permission.READ_SMS"
        const val PERM_RECEIVE_SMS = "android.permission.RECEIVE_SMS"
        const val PERM_READ_CONTACTS = "android.permission.READ_CONTACTS"
        const val PERM_READ_CALL_LOG = "android.permission.READ_CALL_LOG"
        const val PERM_FINE_LOCATION = "android.permission.ACCESS_FINE_LOCATION"
        const val PERM_OVERLAY = "android.permission.SYSTEM_ALERT_WINDOW"
        const val PERM_INSTALL = "android.permission.REQUEST_INSTALL_PACKAGES"
        const val PERM_ACCESSIBILITY = "android.permission.BIND_ACCESSIBILITY_SERVICE"
        const val PERM_DEVICE_ADMIN = "android.permission.BIND_DEVICE_ADMIN"
        const val PERM_CAMERA = "android.permission.CAMERA"
        const val PERM_RECORD_AUDIO = "android.permission.RECORD_AUDIO"
        const val PERM_READ_MEDIA = "android.permission.READ_EXTERNAL_STORAGE"

        /** App stores we trust as installers. Everything else counts as sideloaded. */
        val TRUSTED_INSTALLERS = setOf(
            "com.android.vending", "com.sec.android.app.samsungapps", "com.amazon.venezia", "com.xiaomi.mipicks",
            "com.huawei.appmarket", "com.oppo.market", "com.heytap.market", "com.vivo.appstore", "com.bbk.appstore",
            "com.oneplus.store", "com.realme.appstore", "com.miui.packageinstaller", "com.google.android.feedback"
        )

        /**
         * Brand name (lowercase, as it appears in the app label) -> substrings the real package name contains.
         * Verify and extend this list; it is the core of fake-banking-app detection.
         */
        val OFFICIAL_PACKAGE_HINTS: Map<String, List<String>> = mapOf(
            "phonepe" to listOf("com.phonepe"),
            "paytm" to listOf("net.one97.paytm", "com.paytm"),
            "google pay" to listOf("com.google.android.apps"),
            "gpay" to listOf("com.google.android.apps"),
            "bhim" to listOf("in.org.npci"),
            "yono" to listOf("com.sbi"),
            "sbi" to listOf("com.sbi", "sbicard", "com.sbi.lotusintouch"),
            "hdfc" to listOf("hdfc", "com.snapwork.hdfc"),
            "icici" to listOf("icici"),
            "imobile" to listOf("icici"),
            "axis" to listOf("com.axis", "axisbank"),
            "kotak" to listOf("kotak", "com.msf.kbank"),
            "bank of baroda" to listOf("baroda", "bob"),
            "amazon" to listOf("amazon"),
            "flipkart" to listOf("flipkart"),
            "whatsapp" to listOf("com.whatsapp"),
            "telegram" to listOf("org.telegram", "telegram", "org.thunderdog"),
            "instagram" to listOf("com.instagram"),
            "facebook" to listOf("com.facebook"),
            "cred" to listOf("com.dreamplug"),
            "groww" to listOf("groww"),
            "zerodha" to listOf("zerodha"),
            "kite" to listOf("zerodha"),
            "upstox" to listOf("upstox"),
            "myjio" to listOf("com.jio"),
            "airtel" to listOf("airtel"),
            "irctc" to listOf("cris.org.in", "irctc"),
            "truecaller" to listOf("com.truecaller"),
            "digilocker" to listOf("in.gov.digilocker", "digilocker"),
            "umang" to listOf("in.gov.umang"),
            "aadhaar" to listOf("in.gov.uidai", "uidai"),
            "maadhaar" to listOf("in.gov.uidai", "uidai"),
            "income tax" to listOf("in.gov.incometax", "incometax"),
            "netflix" to listOf("com.netflix"),
            "chrome" to listOf("com.android.chrome", "com.chrome"),
            "play store" to listOf("com.android.vending"),
            "google play" to listOf("com.android.vending")
        )

        val LOAN_WORDS = listOf(
            "loan", "credit", "cash", "rupee", "rupees", "paisa", "paise", "kredit", "udhar", "udhaar", "lend", "lending",
            "money", "finance", "fin", "emi", "advance", "instant", "wallet", "pay later", "paylater", "borrow", "lakh"
        )
        val FINANCE_WORDS = LOAN_WORDS + listOf("bank", "upi", "pay", "trading", "invest", "stock", "crypto", "bitcoin", "forex")
        val SUPPORT_WORDS = listOf("customer care", "customercare", "helpline", "support", "care", "service centre", "service center", "kyc", "verify", "verification")
        val REMOTE_CONTROL_PACKAGES = listOf(
            "com.anydesk", "com.teamviewer", "com.carriez.flutter_hbb", "com.sand.airdroid", "com.rustdesk",
            "com.splashtop", "com.google.android.apps.chromeremotedesktop", "com.alfredcamera", "com.microsoft.rdc",
            "com.realvnc", "com.aeroadmin", "com.apowersoft", "com.iiordanov.freebVNC", "com.remotepc", "com.zoho.assist"
        )
        /** Apps that legitimately need accessibility services (screen readers, password managers, automation). */
        val LEGIT_ACCESSIBILITY_HINTS = listOf(
            "com.google.android.marvin.talkback", "com.android.talkback", "com.samsung.accessibility", "bitwarden",
            "lastpass", "1password", "dashlane", "keepass", "com.google.android.apps.accessibility", "tasker",
            "macrodroid", "com.microsoft.launcher", "nova", "voice access", "com.google.android.apps.accessibility.voiceaccess"
        )
        private const val SEVEN_DAYS_MS = 7L * 24 * 60 * 60 * 1000
    }

    fun score(app: InstalledAppInfo): RiskResult {
        val reasons = mutableListOf<RiskReason>()
        val facts = mutableMapOf("package" to app.packageName, "installer" to (app.installerPackage ?: "sideloaded / unknown"))
        if (app.isSystemApp) {
            return RiskScale.build(ScanCategory.APP, app.packageName, emptyList(), facts, summaryOverride = "System app")
        }

        val name = app.appName.lowercase().trim()
        val pkg = app.packageName.lowercase()
        val sideloaded = app.installerPackage == null || app.installerPackage !in TRUSTED_INSTALLERS
        val requested = app.requestedPermissions.toSet()
        val granted = app.grantedPermissions
        val isFinanceLike = FINANCE_WORDS.any { name.contains(it) || pkg.contains(it) }
        val isNew = app.nowMillis - app.firstInstallTimeMillis < SEVEN_DAYS_MS

        // 1. Fake official app: label says PhonePe/SBI/... but the package is not theirs.
        OFFICIAL_PACKAGE_HINTS.entries.firstOrNull { (brand, _) -> name == brand || name.startsWith("$brand ") || name.endsWith(" $brand") || name.contains(" $brand ") }
            ?.let { (brand, hints) ->
                if (hints.none { pkg.contains(it) }) {
                    reasons += RiskReason(
                        "FAKE_OFFICIAL_APP", "Imitates ${brand.replaceFirstChar { it.uppercase() }} but is not the real app",
                        "The app is called \"${app.appName}\" but its package name ($pkg) does not belong to ${brand.replaceFirstChar { it.uppercase() }}. Fake banking/UPI apps steal logins and OTPs. Uninstall it and change your passwords.", 70
                    )
                }
            }

        // 2. Sideloaded.
        if (sideloaded) {
            reasons += RiskReason(
                "SIDELOADED", "Not installed from the Play Store",
                "Installed from ${app.installerPackage ?: "an APK file / unknown source"}. Apps sent over WhatsApp or downloaded from links bypass Google's malware checks.",
                if (isFinanceLike) 45 else 25
            )
        }

        // 3. Predatory loan app pattern.
        val isLoanLike = LOAN_WORDS.any { name.contains(it) || pkg.contains(it) }
        val invasive = listOf(PERM_READ_CONTACTS, PERM_READ_SMS, PERM_RECEIVE_SMS, PERM_READ_CALL_LOG, PERM_FINE_LOCATION, PERM_READ_MEDIA)
            .filter { it in requested }
        if (isLoanLike && invasive.isNotEmpty()) {
            reasons += RiskReason(
                "PREDATORY_LOAN", "Loan app that wants your contacts / SMS / location",
                "Illegal loan apps harvest contacts, photos and SMS to blackmail borrowers and their families. Requests: ${invasive.joinToString { it.substringAfterLast('.') }}. Only borrow from RBI-regulated lenders listed on rbi.org.in.",
                if (invasive.size >= 2) 55 else 40
            )
        }

        // 4. Can read OTP SMS.
        if ((PERM_READ_SMS in granted || PERM_RECEIVE_SMS in granted) && !name.contains("message") && !name.contains("sms") && !pkg.contains("messag") && !pkg.contains("sms")) {
            reasons += RiskReason(
                "SMS_ACCESS", "Can read your SMS (including OTPs)",
                "This app has permission to read incoming SMS. Combined with a fake login page, that is everything a scammer needs.", 30
            )
        }

        // 5. Accessibility service (banking-trojan favourite) unless it's a known legitimate category.
        if (app.declaresAccessibilityService || PERM_ACCESSIBILITY in requested) {
            val legit = LEGIT_ACCESSIBILITY_HINTS.any { pkg.contains(it) || name.contains(it) }
            if (!legit) {
                reasons += RiskReason(
                    "ACCESSIBILITY", "Uses an Accessibility service",
                    "Accessibility access lets an app read everything on screen and tap buttons for you, which is how banking trojans approve payments. Only screen readers and password managers need it.",
                    if (sideloaded) 35 else 20
                )
            }
        }

        // 6. Overlay + install-packages + device admin.
        if (PERM_OVERLAY in requested) {
            reasons += RiskReason(
                "OVERLAY", "Can draw over other apps",
                "Overlay permission lets an app show a fake login screen on top of your real banking app.",
                if (sideloaded) 25 else 8
            )
        }
        if (PERM_INSTALL in requested && sideloaded) {
            reasons += RiskReason("INSTALLS_APPS", "Can install other apps", "Malware droppers use this to install the real payload later.", 20)
        }
        if (app.declaresDeviceAdmin || PERM_DEVICE_ADMIN in requested) {
            reasons += RiskReason("DEVICE_ADMIN", "Device administrator", "Device-admin apps can lock the phone or resist uninstallation. Only use this for work-profile or anti-theft apps you chose.", 20)
        }

        // 7. Remote-control tools.
        if (REMOTE_CONTROL_PACKAGES.any { pkg.startsWith(it) } || name.contains("anydesk") || name.contains("teamviewer") || name.contains("rustdesk")) {
            reasons += RiskReason(
                "REMOTE_CONTROL", "Remote-control app",
                "Scammers posing as 'customer care' ask victims to install this so they can operate the phone. If you did not install it yourself for your own use, uninstall it now.",
                if (isNew) 40 else 20
            )
        }

        // 8. 'Customer care' / 'KYC' apps are almost never legitimate.
        if (SUPPORT_WORDS.any { name.contains(it) } && !pkg.startsWith("com.google") && !pkg.startsWith("com.android")) {
            reasons += RiskReason("SUPPORT_APP", "'Customer care' / 'KYC' style app", "Real companies put support inside their main app. Separate 'customer care' or 'KYC update' apps are scam tools.", 30)
        }

        // 9. Old targetSdk sidesteps modern permission protections.
        if (app.targetSdk in 1..22) {
            reasons += RiskReason("OLD_TARGET_SDK", "Built for very old Android", "Targets Android ${app.targetSdk}, so it gets all its permissions at install time without asking.", 15)
        }

        // 10. Camera + mic + location on a sideloaded app.
        if (sideloaded && listOf(PERM_CAMERA, PERM_RECORD_AUDIO, PERM_FINE_LOCATION).all { it in requested }) {
            reasons += RiskReason("SPYWARE_COMBO", "Camera + microphone + location together", "A sideloaded app with all three is consistent with stalkerware.", 25)
        }

        if (isNew && reasons.any { it.weight >= 20 }) {
            reasons += RiskReason("RECENTLY_INSTALLED", "Installed in the last 7 days", "Most scam installs happen during or right after a scam call.", 10)
        }

        val summary = when {
            reasons.isEmpty() -> "No risky permissions or patterns"
            else -> reasons.maxByOrNull { it.weight }!!.title
        }
        return RiskScale.build(ScanCategory.APP, app.packageName, reasons, facts, summaryOverride = "${app.appName}: $summary")
    }
}

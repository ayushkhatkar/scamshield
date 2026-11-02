package com.scamshield.engine

import com.scamshield.engine.app.AppRiskScorer
import com.scamshield.engine.app.InstalledAppInfo
import com.scamshield.engine.phone.PhoneNumberAnalyzer
import com.scamshield.engine.url.DomainUtils
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EngineTest {

    private val engine = ScamShieldEngine()

    // ---------- URLs ----------

    @Test
    fun officialBankUrlIsSafe() {
        val r = engine.analyzeUrl("https://www.onlinesbi.sbi/")
        assertEquals(RiskLevel.SAFE, r.level)
        val r2 = engine.analyzeUrl("https://netbanking.hdfcbank.com/netbanking/")
        assertEquals(RiskLevel.SAFE, r2.level)
        val r3 = engine.analyzeUrl("https://docs.google.com/document/d/abc")
        assertFalse(r3.level == RiskLevel.DANGEROUS)
    }

    @Test
    fun brandImpersonationOnFreeHostIsDangerous() {
        val r = engine.analyzeUrl("https://sbi-kyc-update.web.app/login")
        assertEquals(RiskLevel.DANGEROUS, r.level)
        assertTrue(r.reasons.any { it.code == "BRAND_IMPERSONATION" })
        assertTrue(r.reasons.any { it.code == "USER_CONTENT_HOST" })
    }

    @Test
    fun gluedBrandTokenIsCaught() {
        val r = engine.analyzeUrl("http://hdfcrewards.xyz/claim")
        assertTrue(r.reasons.any { it.code == "BRAND_IMPERSONATION" })
        assertTrue(r.level.isAlertWorthy)
    }

    @Test
    fun apkLinkIsDangerous() {
        val r = engine.analyzeUrl("https://cdn-files.top/PhonePe_update.apk")
        assertEquals(RiskLevel.DANGEROUS, r.level)
    }

    @Test
    fun shortenerIsCaution() {
        val r = engine.analyzeUrl("https://bit.ly/3xyzabc")
        assertTrue(r.level == RiskLevel.CAUTION || r.level == RiskLevel.SUSPICIOUS)
        assertTrue(r.reasons.any { it.code == "SHORTENER" })
    }

    @Test
    fun userInfoTrickIsCaught() {
        val r = engine.analyzeUrl("https://icicibank.com@198.51.100.7/login")
        assertTrue(r.reasons.any { it.code == "USERINFO" })
        assertEquals(RiskLevel.DANGEROUS, r.level)
    }

    @Test
    fun registrableDomainHandlesIndianSuffixes() {
        assertEquals("sbi.co.in", DomainUtils.registrableDomain("pay.sbi.co.in"))
        assertEquals("google.com", DomainUtils.registrableDomain("docs.google.com"))
        assertEquals("web.app", DomainUtils.registrableDomain("sbi-kyc.web.app"))
    }

    // ---------- UPI QR ----------

    @Test
    fun upiRefundQrIsFlagged() {
        val r = engine.analyzeQr("upi://pay?pa=9876543210@ybl&pn=SBI%20Refund%20Desk&am=4999&cu=INR&tn=refund%20processing")
        assertEquals(ScanCategory.UPI_QR, r.category)
        assertTrue(r.level.isAlertWorthy)
        assertTrue(r.reasons.any { it.code == "PAYEE_IMPERSONATION" })
        assertTrue(r.summary.contains("₹4,999"))
    }

    @Test
    fun plainShopQrIsLowRisk() {
        val r = engine.analyzeQr("upi://pay?pa=shop.rahul@okaxis&pn=Rahul%20Kirana&cu=INR&mc=5411")
        assertTrue(r.score < RiskScale.SUSPICIOUS_AT)
        assertTrue(r.summary.contains("PAY"))
    }

    @Test
    fun collectRequestIsFlagged() {
        val r = engine.analyzeQr("upi://collect?pa=abc@paytm&pn=Reward&am=1")
        assertTrue(r.reasons.any { it.code == "UPI_COLLECT" })
    }

    @Test
    fun wifiQrIsParsed() {
        val r = engine.analyzeQr("WIFI:T:nopass;S:Free Airport WiFi;;")
        assertEquals(ScanCategory.WIFI_QR, r.category)
        assertEquals("Free Airport WiFi", r.facts["ssid"])
        assertTrue(r.level.isAlertWorthy)
    }

    // ---------- Messages ----------

    @Test
    fun digitalArrestMessageIsDangerous() {
        val msg = "This is CBI officer. A parcel in your name contains drugs. You are under digital arrest. " +
            "Join video call immediately and transfer Rs 50000 for verification or arrest warrant will be issued."
        val r = engine.analyzeMessage(msg)
        assertEquals(RiskLevel.DANGEROUS, r.level)
        assertTrue(r.reasons.any { it.code == "DIGITAL_ARREST" })
    }

    @Test
    fun kycLinkSmsIsDangerous() {
        val r = engine.analyzeMessage("Dear customer your SBI account will be blocked today. Update KYC immediately: http://sbi-kyc-update.web.app/login")
        assertEquals(RiskLevel.DANGEROUS, r.level)
        assertTrue(r.facts["links"]!!.contains("sbi-kyc-update.web.app"))
    }

    @Test
    fun taskJobScamIsSuspicious() {
        val r = engine.analyzeMessage("Hi, we are hiring for part time job. Earn Rs 3000 daily by liking YouTube videos. Join telegram group now.")
        assertTrue(r.level.isAlertWorthy)
    }

    @Test
    fun genuineBankDebitSmsIsNotFlagged() {
        val r = engine.analyzeMessage("Dear Customer, Rs 450.00 debited from a/c XX1234 on 03-11-26 to VPA rahul@okaxis. Ref 6311. If not you, call 1800111109. -SBI")
        assertTrue("score was ${r.score}", r.score < RiskScale.SUSPICIOUS_AT)
    }

    @Test
    fun otpRequestIsDangerous() {
        val r = engine.analyzeMessage("Sir I am from Flipkart delivery, please share OTP received on your phone to confirm the address")
        assertTrue(r.reasons.any { it.code == "OTP_REQUEST" })
        assertTrue(r.level.isAlertWorthy)
    }

    @Test
    fun analyzeAnyRoutesCorrectly() {
        assertEquals(ScanCategory.URL, engine.analyzeAny("https://example.com").category)
        assertEquals(ScanCategory.PHONE, engine.analyzeAny("+92 300 1234567").category)
        assertEquals(ScanCategory.UPI_QR, engine.analyzeAny("upi://pay?pa=a@b").category)
        assertEquals(ScanCategory.MESSAGE, engine.analyzeAny("hello there, how are you").category)
    }

    // ---------- Phone numbers ----------

    @Test
    fun pakistaniNumberIsSuspicious() {
        val r = engine.analyzePhone("+92 300 1234567")
        assertTrue(r.level.isAlertWorthy)
        assertTrue(r.reasons.any { it.code == "HIGH_RISK_COUNTRY" })
    }

    @Test
    fun communityReportedNumberIsDangerous() {
        val r = engine.analyzePhone("9876543210", PhoneNumberAnalyzer.PhoneContext(communityReports = 12, reportLabel = "Loan harassment"))
        assertEquals(RiskLevel.DANGEROUS, r.level)
        assertEquals("+919876543210", r.subject)
    }

    @Test
    fun contactIsSafe() {
        val r = engine.analyzePhone("+919876543210", PhoneNumberAnalyzer.PhoneContext(inContacts = true))
        assertEquals(RiskLevel.SAFE, r.level)
    }

    @Test
    fun canonicalFormats() {
        assertEquals("+919876543210", PhoneNumberAnalyzer.canonical("98765 43210"))
        assertEquals("+919876543210", PhoneNumberAnalyzer.canonical("09876543210"))
        assertEquals("+919876543210", PhoneNumberAnalyzer.canonical("+91-98765-43210"))
        assertEquals("+923001234567", PhoneNumberAnalyzer.canonical("00923001234567"))
    }

    // ---------- Apps ----------

    private fun app(
        pkg: String, name: String, perms: List<String> = emptyList(), granted: Set<String> = emptySet(),
        installer: String? = "com.android.vending", targetSdk: Int = 34, accessibility: Boolean = false
    ) = InstalledAppInfo(
        packageName = pkg, appName = name, requestedPermissions = perms, grantedPermissions = granted,
        installerPackage = installer, isSystemApp = false, targetSdk = targetSdk,
        firstInstallTimeMillis = System.currentTimeMillis() - 1_000L, declaresAccessibilityService = accessibility
    )

    @Test
    fun fakePhonePeIsDangerous() {
        val r = engine.scoreApp(app("com.update.phnpe", "PhonePe", installer = null, perms = listOf(AppRiskScorer.PERM_READ_SMS)))
        assertEquals(RiskLevel.DANGEROUS, r.level)
        assertTrue(r.reasons.any { it.code == "FAKE_OFFICIAL_APP" })
    }

    @Test
    fun realPhonePeIsSafe() {
        val r = engine.scoreApp(app("com.phonepe.app", "PhonePe", perms = listOf("android.permission.CAMERA")))
        assertEquals(RiskLevel.SAFE, r.level)
    }

    @Test
    fun predatoryLoanAppIsFlagged() {
        val r = engine.scoreApp(
            app("com.fastcash.rupeeloan", "Rupee Cash Loan",
                perms = listOf(AppRiskScorer.PERM_READ_CONTACTS, AppRiskScorer.PERM_FINE_LOCATION, AppRiskScorer.PERM_READ_MEDIA))
        )
        assertTrue(r.level.isAlertWorthy)
        assertTrue(r.reasons.any { it.code == "PREDATORY_LOAN" })
    }

    @Test
    fun sideloadedRemoteControlIsFlagged() {
        val r = engine.scoreApp(app("com.anydesk.anydeskandroid", "AnyDesk Remote Desktop", installer = null, accessibility = true))
        assertTrue(r.level.isAlertWorthy)
        assertTrue(r.reasons.any { it.code == "REMOTE_CONTROL" })
    }

    @Test
    fun systemAppIsSkipped() {
        val r = engine.scoreApp(app("com.android.phone", "Phone").copy(isSystemApp = true))
        assertEquals(RiskLevel.SAFE, r.level)
        assertEquals("System app", r.summary)
    }
}

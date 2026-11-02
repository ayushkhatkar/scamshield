package com.scamshield.engine.url

/**
 * Brands that scammers impersonate most in India, mapped to the registrable domains
 * they really use. If a host *mentions* a brand but its registrable domain is not in
 * the brand's list, the URL is treated as brand impersonation.
 *
 * Extend this list from your backend over time; it is deliberately data, not code.
 */
object KnownDomains {

    data class Brand(
        val key: String,
        val displayName: String,
        /** Tokens that identify the brand inside a hostname. */
        val tokens: List<String>,
        /** Registrable domains (eTLD+1) the brand legitimately uses. */
        val officialDomains: List<String>
    )

    val BRANDS: List<Brand> = listOf(
        Brand("sbi", "State Bank of India", listOf("sbi", "onlinesbi", "yono", "sbicard"),
            listOf("sbi.co.in", "onlinesbi.sbi", "yonobusiness.sbi", "sbicard.com", "sbilife.co.in")),
        Brand("hdfc", "HDFC Bank", listOf("hdfc", "hdfcbank"),
            listOf("hdfcbank.com", "hdfc.com", "hdfcergo.com", "hdfclife.com", "hdfcsec.com")),
        Brand("icici", "ICICI Bank", listOf("icici", "icicibank", "imobile"),
            listOf("icicibank.com", "icicidirect.com", "icicilombard.com", "iciciprulife.com")),
        Brand("axis", "Axis Bank", listOf("axis", "axisbank"), listOf("axisbank.com")),
        Brand("kotak", "Kotak Mahindra Bank", listOf("kotak"), listOf("kotak.com")),
        Brand("pnb", "Punjab National Bank", listOf("pnb", "pnbindia"), listOf("pnbindia.in")),
        Brand("bob", "Bank of Baroda", listOf("bankofbaroda", "bobibanking"), listOf("bankofbaroda.in", "bankofbaroda.com")),
        Brand("paytm", "Paytm", listOf("paytm"), listOf("paytm.com", "paytmbank.com", "paytmmoney.com", "paytmpayments.com")),
        Brand("phonepe", "PhonePe", listOf("phonepe"), listOf("phonepe.com")),
        Brand("gpay", "Google Pay", listOf("gpay", "googlepay"), listOf("google.com", "google.co.in")),
        Brand("bhim", "BHIM / NPCI", listOf("bhim", "npci"), listOf("npci.org.in", "bhimupi.org.in")),
        Brand("airtel", "Airtel", listOf("airtel"), listOf("airtel.in", "airtel.com")),
        Brand("jio", "Jio", listOf("jio", "myjio"), listOf("jio.com")),
        Brand("vi", "Vodafone Idea", listOf("vodafone", "myvi"), listOf("myvi.in", "vodafoneidea.com")),
        Brand("bsnl", "BSNL", listOf("bsnl"), listOf("bsnl.co.in", "bsnl.in")),
        Brand("irctc", "IRCTC", listOf("irctc"), listOf("irctc.co.in")),
        Brand("incometax", "Income Tax Department", listOf("incometax", "efiling"),
            listOf("incometax.gov.in", "incometaxindia.gov.in")),
        Brand("uidai", "Aadhaar / UIDAI", listOf("uidai", "aadhaar", "aadhar"), listOf("uidai.gov.in")),
        Brand("epfo", "EPFO", listOf("epfo", "epfindia"), listOf("epfindia.gov.in")),
        Brand("rbi", "Reserve Bank of India", listOf("rbi"), listOf("rbi.org.in")),
        Brand("sebi", "SEBI", listOf("sebi"), listOf("sebi.gov.in")),
        Brand("indiapost", "India Post", listOf("indiapost", "postoffice", "speedpost"), listOf("indiapost.gov.in")),
        Brand("lic", "LIC", listOf("licindia", "licofindia"), listOf("licindia.in")),
        Brand("flipkart", "Flipkart", listOf("flipkart"), listOf("flipkart.com")),
        Brand("amazon", "Amazon", listOf("amazon"), listOf("amazon.in", "amazon.com", "primevideo.com", "amazonaws.com")),
        Brand("myntra", "Myntra", listOf("myntra"), listOf("myntra.com")),
        Brand("whatsapp", "WhatsApp", listOf("whatsapp"), listOf("whatsapp.com", "wa.me", "whatsapp.net")),
        Brand("meta", "Facebook / Instagram", listOf("facebook", "instagram"),
            listOf("facebook.com", "fb.com", "fb.me", "instagram.com", "meta.com")),
        Brand("netflix", "Netflix", listOf("netflix"), listOf("netflix.com")),
        Brand("microsoft", "Microsoft", listOf("microsoft", "outlook", "office365"),
            listOf("microsoft.com", "live.com", "outlook.com", "office.com", "microsoftonline.com")),
        Brand("apple", "Apple", listOf("apple", "icloud"), listOf("apple.com", "icloud.com")),
        Brand("google", "Google", listOf("google", "gmail"),
            listOf("google.com", "google.co.in", "gmail.com", "goo.gl", "youtube.com", "googleusercontent.com",
                "googleapis.com", "gstatic.com", "withgoogle.com", "android.com", "google.dev")),
        Brand("fedex", "FedEx", listOf("fedex"), listOf("fedex.com")),
        Brand("dhl", "DHL", listOf("dhl"), listOf("dhl.com")),
        Brand("bluedart", "Blue Dart", listOf("bluedart"), listOf("bluedart.com")),
        Brand("delhivery", "Delhivery", listOf("delhivery"), listOf("delhivery.com")),
        Brand("swiggy", "Swiggy", listOf("swiggy"), listOf("swiggy.com")),
        Brand("zomato", "Zomato", listOf("zomato"), listOf("zomato.com")),
        Brand("ola", "Ola", listOf("olacabs"), listOf("olacabs.com")),
        Brand("uber", "Uber", listOf("uber"), listOf("uber.com", "ubereats.com")),
        Brand("tata", "Tata", listOf("tataneu", "tatasky", "tataplay"), listOf("tataneu.com", "tataplay.com", "tata.com")),
        Brand("cred", "CRED", listOf("cred"), listOf("cred.club")),
        Brand("zerodha", "Zerodha", listOf("zerodha", "kite"), listOf("zerodha.com")),
        Brand("groww", "Groww", listOf("groww"), listOf("groww.in")),
        Brand("upstox", "Upstox", listOf("upstox"), listOf("upstox.com")),
        Brand("nse", "NSE / BSE", listOf("nseindia", "bseindia"), listOf("nseindia.com", "bseindia.com")),
        Brand("mygov", "MyGov / DigiLocker", listOf("mygov", "digilocker", "umang"),
            listOf("mygov.in", "digilocker.gov.in", "umang.gov.in")),
        Brand("sancharsaathi", "Sanchar Saathi / TRAI", listOf("sancharsaathi", "trai"),
            listOf("sancharsaathi.gov.in", "trai.gov.in")),
        Brand("cybercrime", "National Cyber Crime Portal", listOf("cybercrime"), listOf("cybercrime.gov.in"))
    )

    private val officialDomainSet: Set<String> = BRANDS.flatMap { it.officialDomains }.toSet()

    /** Brand-owned top-level domains (e.g. onlinesbi.sbi). Anything under them is official. */
    private val BRAND_TLDS: Set<String> = setOf("sbi")

    /** Registrable domains of large, trusted platforms that are not brands we track as impersonated. */
    val TRUSTED_PLATFORMS: Set<String> = setOf(
        "wikipedia.org", "github.com", "linkedin.com", "twitter.com", "x.com", "reddit.com",
        "ndtv.com", "thehindu.com", "indiatimes.com", "hindustantimes.com", "indianexpress.com",
        "who.int", "un.org"
    )

    /** Words scammers glue onto a short brand token: sbirewards.top, hdfckyc.xyz, axispoints.com. */
    private val GLUE_WORDS: Set<String> = setOf(
        "reward", "rewards", "points", "point", "kyc", "bank", "banking", "netbanking", "update", "login",
        "card", "cards", "offer", "offers", "loan", "loans", "credit", "verify", "verification", "secure",
        "online", "pay", "payment", "payments", "refund", "cashback", "bonus", "gift", "prize", "care",
        "support", "help", "alert", "alerts", "upi", "otp", "account", "accounts", "claim", "unblock",
        "app", "apps", "mobile", "service", "services", "customer", "india", "in", "net", "yono", "bonanza"
    )
    private val GLUE_PREFIXES: Set<String> = setOf("my", "the", "new", "online", "e", "i", "net", "m")

    fun isOfficial(registrableDomain: String, host: String = registrableDomain): Boolean {
        if (registrableDomain in officialDomainSet || registrableDomain in TRUSTED_PLATFORMS) return true
        if (host.endsWith(".gov.in") || host.endsWith(".nic.in") || host.endsWith(".ac.in")) return true
        val tld = host.substringAfterLast('.')
        return tld in BRAND_TLDS
    }

    /**
     * Returns the brand mentioned in [host] if any. Short tokens (<5 chars) must match a whole
     * host label (split on '.' and '-'), optionally glued to a typical scam word, to avoid false
     * positives like "public" containing "lic".
     */
    fun matchBrand(host: String): Brand? = matchBrands(host).firstOrNull()

    /** All brands mentioned in [host], in list order. */
    fun matchBrands(host: String): List<Brand> {
        val labels = host.split('.', '-').filter { it.isNotEmpty() }
        val compact = host.replace("-", "").replace(".", "")
        return BRANDS.filter { brand ->
            brand.tokens.any { token ->
                if (token.length >= 5) {
                    compact.contains(token)
                } else {
                    labels.any { label ->
                        label == token ||
                            (label.startsWith(token) && label.removePrefix(token) in GLUE_WORDS) ||
                            (label.endsWith(token) && label.removeSuffix(token) in GLUE_PREFIXES)
                    }
                }
            }
        }
    }
}

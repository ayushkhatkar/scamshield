package com.scamshield.engine.url

import com.scamshield.engine.RiskReason
import com.scamshield.engine.RiskResult
import com.scamshield.engine.RiskScale
import com.scamshield.engine.ScanCategory

/**
 * Heuristic, fully offline link analysis tuned for scams common in India.
 * The app layers Google Safe Browsing / your own blocklist on top of this via [RiskResult.adding].
 */
class UrlAnalyzer {

    companion object {
        val SHORTENERS = setOf(
            "bit.ly", "tinyurl.com", "t.co", "cutt.ly", "rb.gy", "is.gd", "tiny.cc", "shorturl.at", "rebrand.ly",
            "ow.ly", "buff.ly", "t.ly", "s.id", "v.gd", "clck.ru", "shorte.st", "adf.ly", "bl.ink", "lnkd.in",
            "tny.im", "u.to", "qr.ae", "short.io", "surl.li", "shrtco.de", "goo.su", "zpr.io", "kutt.it"
        )

        /** Hosts where anyone can publish a page. Not "bad", but banks and governments never use them. */
        val USER_CONTENT_HOSTS = setOf(
            "web.app", "firebaseapp.com", "github.io", "netlify.app", "vercel.app", "pages.dev", "glitch.me",
            "000webhostapp.com", "weebly.com", "wixsite.com", "wix.com", "blogspot.com", "sites.google.com",
            "docs.google.com", "forms.gle", "drive.google.com", "herokuapp.com", "r2.dev", "workers.dev",
            "repl.co", "replit.app", "surge.sh", "onrender.com", "ngrok-free.app", "ngrok.io", "amazonaws.com",
            "cloudfront.net", "godaddysites.com", "square.site", "carrd.co", "strikingly.com", "webflow.io",
            "notion.site", "linktr.ee", "typeform.com", "jotform.com", "wordpress.com", "tumblr.com",
            "telegra.ph", "pastebin.com", "mega.nz", "mediafire.com", "dropbox.com", "trycloudflare.com",
            "duckdns.org", "no-ip.org", "ddns.net", "hopto.org", "zapto.org", "myftp.org", "serveo.net"
        )

        /** TLDs disproportionately used for phishing (cheap or free registrations). */
        val SUSPICIOUS_TLDS = setOf(
            "xyz", "top", "tk", "ml", "ga", "cf", "gq", "buzz", "click", "link", "icu", "cam", "rest", "monster",
            "quest", "work", "zip", "mov", "live", "club", "site", "online", "website", "space", "fun", "cyou",
            "sbs", "bond", "lol", "pw", "cc", "su", "ru", "cn", "info", "biz", "loan", "loans", "money", "cash",
            "win", "bid", "stream", "download", "racing", "party", "review", "trade", "date", "men", "faith",
            "accountant", "science", "cfd", "shop", "store", "vip", "pro", "ws", "nu"
        )

        /** Pressure / credential words in the path or query. */
        val PHISHING_PATH_WORDS = listOf(
            "login", "signin", "verify", "verification", "kyc", "update", "secure", "account", "otp", "reward",
            "prize", "claim", "refund", "unblock", "suspend", "suspended", "blocked", "wallet", "password",
            "confirm", "bonus", "cashback", "lottery", "winner", "customercare", "support", "helpdesk", "pan",
            "aadhaar", "aadhar", "netbanking", "creditcard", "debitcard", "apk", "install", "urgent", "limited"
        )

        val WHATSAPP_HOSTS = setOf("wa.me", "api.whatsapp.com", "chat.whatsapp.com")
    }

    fun analyze(raw: String): RiskResult {
        val input = raw.trim()
        if (UpiQrAnalyzer.isUpiLink(input)) return UpiQrAnalyzer().analyze(input)

        val parsed = DomainUtils.parse(input) ?: return RiskScale.build(
            category = ScanCategory.URL,
            subject = input,
            reasons = listOf(
                RiskReason(
                    "UNPARSEABLE", "Could not read this as a link",
                    "This does not look like a valid web address. Be careful with anything you cannot read clearly.", 30
                )
            )
        )

        val host = parsed.host
        val registrable = DomainUtils.registrableDomain(host)
        val userContentHost = USER_CONTENT_HOSTS.firstOrNull { DomainUtils.hostMatches(host, it) }
        val isKnownGood = userContentHost == null && KnownDomains.isOfficial(registrable, host)
        // A brand mentioned in the host that does NOT own the registrable domain (docs.google.com is Google's own).
        val brand = KnownDomains.matchBrands(host).firstOrNull { registrable !in it.officialDomains }
        val reasons = mutableListOf<RiskReason>()
        val facts = mutableMapOf("host" to host, "domain" to registrable)

        // ---- Hard signals (apply even to otherwise-trusted hosts) ----
        if (parsed.userInfo != null) {
            reasons += RiskReason(
                "USERINFO", "Hidden real destination",
                "The address contains an '@'. Browsers ignore everything before it, so the site you actually open is not the one you see.", 60
            )
        }
        if (DomainUtils.isIpAddress(host)) {
            reasons += RiskReason(
                "IP_HOST", "Raw IP address instead of a name",
                "Real companies use a domain name. Links to a bare IP address are typical of scam servers.", 50
            )
        }
        if (DomainUtils.isPunycode(host)) {
            reasons += RiskReason(
                "PUNYCODE", "Look-alike characters in the address",
                "This address uses special characters that imitate normal letters (e.g. a Cyrillic 'а' instead of 'a').", 55
            )
        }
        val lowerPathQuery = (parsed.path + "?" + parsed.query).lowercase()
        if (Regex("""\.apk(\?|&|$|/)""").containsMatchIn(lowerPathQuery)) {
            reasons += RiskReason(
                "APK_DOWNLOAD", "Installs an app outside the Play Store",
                "This link downloads an APK file. Fake banking, loan and 'customer care' apps are spread exactly this way. Only install apps from the Play Store.", 70
            )
        }

        if (!isKnownGood) {
            if (brand != null) {
                reasons += RiskReason(
                    "BRAND_IMPERSONATION", "Pretends to be ${brand.displayName}",
                    "The address mentions ${brand.displayName} but is not one of its real websites (${brand.officialDomains.take(2).joinToString(", ")}).", 65
                )
                facts["brand"] = brand.displayName
            }
            if (userContentHost != null) {
                reasons += RiskReason(
                    "USER_CONTENT_HOST", "Hosted on a free / public platform",
                    "Anyone can publish a page on $userContentHost. Banks, government offices and courier companies never host login or payment pages there.",
                    if (brand != null) 25 else 15
                )
            }
            if (registrable in SHORTENERS) {
                reasons += RiskReason(
                    "SHORTENER", "Shortened link hides the destination",
                    "You cannot see where this link really goes. Scammers use shorteners to hide fake sites.", 25
                )
            }
            val tld = DomainUtils.tld(host)
            if (tld in SUSPICIOUS_TLDS) {
                reasons += RiskReason(
                    "RISKY_TLD", "Unusual website ending (.$tld)",
                    "Addresses ending in .$tld are cheap to register and heavily used for phishing. Indian banks and government sites end in .in, .co.in, .gov.in or .com.", 25
                )
            }
            val labelCount = host.split('.').size
            if (labelCount >= 4) {
                reasons += RiskReason(
                    "DEEP_SUBDOMAIN", "Many sub-parts in the address",
                    "Long addresses like this are often built so the real domain is hidden at the end where you don't look.", 15
                )
            }
            if (host.count { it == '-' } >= 2) {
                reasons += RiskReason(
                    "MANY_HYPHENS", "Hyphen-heavy address",
                    "Addresses stuffed with hyphens (e.g. secure-bank-login-update) are a classic phishing pattern.", 10
                )
            }
            if (host.length > 30) {
                reasons += RiskReason("LONG_HOST", "Very long address", "Legitimate sites usually have short, memorable names.", 10)
            }
            if (parsed.scheme == "http" && parsed.hasExplicitScheme) {
                reasons += RiskReason(
                    "NO_HTTPS", "Not a secure (https) connection",
                    "Anything you type on this page can be read by others. No bank or payment site uses plain http.", 15
                )
            }
            if (parsed.port != -1 && parsed.port != 80 && parsed.port != 443) {
                reasons += RiskReason("ODD_PORT", "Unusual port number", "Normal websites don't need a port number in the address.", 20)
            }
            val hits = PHISHING_PATH_WORDS.filter { lowerPathQuery.contains(it) }
            if (hits.isNotEmpty()) {
                reasons += RiskReason(
                    "PHISHING_WORDS", "Pressure or login words in the link",
                    "Found: ${hits.take(4).joinToString(", ")}. Scam pages are built around urgency, rewards and logins.",
                    (10 * hits.size).coerceAtMost(30)
                )
            }
            if (WHATSAPP_HOSTS.any { DomainUtils.hostMatches(host, it) }) {
                reasons += RiskReason(
                    "WHATSAPP_LINK", "Opens a WhatsApp chat",
                    "Many job, lottery and investment scams move victims to WhatsApp. Don't share personal details with an unknown chat.", 10
                )
            }
        } else {
            facts["official"] = "true"
            // Even official hosts can carry open redirects; only flag the strong signals above.
            if (reasons.isEmpty()) {
                reasons += RiskReason(
                    "KNOWN_OFFICIAL", "Recognised official website",
                    "$registrable is a known legitimate domain.", 0
                )
            }
        }

        if (input.length > 200) {
            reasons += RiskReason("LONG_URL", "Unusually long link", "Very long links often hide tracking or encoded redirect targets.", 5)
        }

        return RiskScale.build(ScanCategory.URL, input, reasons, facts)
    }
}

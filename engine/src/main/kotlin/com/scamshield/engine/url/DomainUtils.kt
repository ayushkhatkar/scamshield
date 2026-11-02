package com.scamshield.engine.url

/** Small, dependency-free URL helpers. Good enough for scoring; not a full RFC parser. */
object DomainUtils {

    data class ParsedUrl(
        val original: String,
        val scheme: String,
        val userInfo: String?,
        val host: String,
        val port: Int,
        val path: String,
        val query: String
    ) {
        val hasExplicitScheme: Boolean get() = original.contains("://")
    }

    private val URL_REGEX = Regex(
        """^(?:([a-zA-Z][a-zA-Z0-9+.\-]*)://)?(?:([^@/?#\s]*)@)?([^:/?#\s]+)(?::(\d{1,5}))?([^?#\s]*)(?:\?([^#\s]*))?(?:#.*)?$"""
    )

    private val IPV4_REGEX = Regex("""^(\d{1,3})\.(\d{1,3})\.(\d{1,3})\.(\d{1,3})$""")

    /** Multi-label public suffixes (subset of the Public Suffix List) so eTLD+1 is computed correctly. */
    private val MULTI_LABEL_SUFFIXES = setOf(
        "co.in", "gov.in", "nic.in", "ac.in", "org.in", "net.in", "firm.in", "ind.in", "gen.in", "edu.in", "res.in",
        "co.uk", "org.uk", "gov.uk", "ac.uk", "com.au", "net.au", "org.au", "co.jp", "co.nz", "com.br",
        "com.sg", "com.my", "co.za", "com.pk", "com.bd", "com.np", "com.lk", "gov.bd", "com.ng", "com.tr"
    )

    fun parse(raw: String): ParsedUrl? {
        val text = raw.trim()
        if (text.isEmpty()) return null
        val m = URL_REGEX.find(text) ?: return null
        val scheme = (m.groupValues[1].ifEmpty { "http" }).lowercase()
        val userInfo = m.groupValues[2].ifEmpty { null }
        val host = m.groupValues[3].lowercase().trimEnd('.')
        val port = m.groupValues[4].toIntOrNull() ?: -1
        val path = m.groupValues[5]
        val query = m.groupValues[6]
        if (!looksLikeHost(host)) return null
        return ParsedUrl(text, scheme, userInfo, host, port, path, query)
    }

    fun looksLikeHost(host: String): Boolean {
        if (host.isEmpty() || host.length > 253) return false
        if (isIpAddress(host)) return true
        if (!host.contains('.')) return host == "localhost"
        return host.all { it.isLetterOrDigit() || it == '.' || it == '-' } &&
            !host.startsWith(".") && !host.contains("..") && host.substringAfterLast('.').any { it.isLetter() }
    }

    fun isIpAddress(host: String): Boolean {
        val m = IPV4_REGEX.find(host)
            ?: return host.contains(':') && host.all { it.isLetterOrDigit() || it == ':' || it == '.' }
        return m.groupValues.drop(1).all { it.toInt() in 0..255 }
    }

    /** Best-effort eTLD+1. "pay.sbi.co.in" -> "sbi.co.in", "docs.google.com" -> "google.com". */
    fun registrableDomain(host: String): String {
        if (isIpAddress(host)) return host
        val labels = host.split('.')
        if (labels.size <= 2) return host
        val last2 = labels.takeLast(2).joinToString(".")
        return if (last2 in MULTI_LABEL_SUFFIXES) labels.takeLast(3).joinToString(".") else last2
    }

    /** Top-level domain, e.g. "xyz" for "a.b.xyz". */
    fun tld(host: String): String = host.substringAfterLast('.', "")

    fun isPunycode(host: String): Boolean = host.split('.').any { it.startsWith("xn--") }

    /** True when [host] equals [suffix] or is a subdomain of it. */
    fun hostMatches(host: String, suffix: String): Boolean = host == suffix || host.endsWith(".$suffix")
}

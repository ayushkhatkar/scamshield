package com.scamshield.app.data.remote

import com.scamshield.app.BuildConfig
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

// ---------------------------------------------------------------------------------------------
// Google Safe Browsing Lookup API v4 (https://developers.google.com/safe-browsing/v4/lookup-api)
// Free for non-commercial use; use the Web Risk API (same request shape) once you charge money.
// ---------------------------------------------------------------------------------------------

interface SafeBrowsingApi {
    @POST("v4/threatMatches:find")
    suspend fun find(@Query("key") apiKey: String, @Body body: SafeBrowsingRequest): SafeBrowsingResponse

    companion object {
        const val BASE_URL = "https://safebrowsing.googleapis.com/"
        val isConfigured: Boolean get() = BuildConfig.SAFE_BROWSING_API_KEY.isNotBlank()
    }
}

@Serializable
data class SafeBrowsingRequest(val client: Client, val threatInfo: ThreatInfo) {
    @Serializable
    data class Client(val clientId: String = "scamshield-android", val clientVersion: String = BuildConfig.VERSION_NAME)

    @Serializable
    data class ThreatInfo(
        val threatTypes: List<String> = listOf("MALWARE", "SOCIAL_ENGINEERING", "UNWANTED_SOFTWARE", "POTENTIALLY_HARMFUL_APPLICATION"),
        val platformTypes: List<String> = listOf("ANY_PLATFORM"),
        val threatEntryTypes: List<String> = listOf("URL"),
        val threatEntries: List<Entry>
    )

    @Serializable
    data class Entry(val url: String)

    companion object {
        fun forUrls(urls: List<String>) = SafeBrowsingRequest(Client(), ThreatInfo(threatEntries = urls.map { Entry(it) }))
    }
}

@Serializable
data class SafeBrowsingResponse(val matches: List<Match> = emptyList()) {
    @Serializable
    data class Match(val threatType: String = "", val platformType: String = "", val threat: SafeBrowsingRequest.Entry? = null)
}

// ---------------------------------------------------------------------------------------------
// Have I Been Pwned v3 (https://haveibeenpwned.com/API/v3) - paid key, powers the Pro breach monitor
// ---------------------------------------------------------------------------------------------

interface BreachApi {
    @GET("api/v3/breachedaccount/{account}?truncateResponse=false")
    suspend fun breaches(
        @Path("account") account: String,
        @Header("hibp-api-key") apiKey: String,
        @Header("user-agent") userAgent: String = "ScamShield-Android"
    ): Response<List<BreachDto>>

    companion object {
        const val BASE_URL = "https://haveibeenpwned.com/"
        val isConfigured: Boolean get() = BuildConfig.HIBP_API_KEY.isNotBlank()
    }
}

@Serializable
data class BreachDto(
    @SerialName("Name") val name: String = "",
    @SerialName("Title") val title: String = "",
    @SerialName("Domain") val domain: String = "",
    @SerialName("BreachDate") val breachDate: String = "",
    @SerialName("PwnCount") val pwnCount: Long = 0,
    @SerialName("DataClasses") val dataClasses: List<String> = emptyList(),
    @SerialName("IsVerified") val isVerified: Boolean = true
)

// ---------------------------------------------------------------------------------------------
// Your own backend (optional). Until deployed, BuildConfig.SCAMSHIELD_API_BASE_URL is a placeholder
// and every call is skipped. Suggested stack: Firebase Functions or a small FastAPI/Ktor service.
// ---------------------------------------------------------------------------------------------

interface ScamShieldApi {
    /** Curated + community blocklist, incremental by timestamp. */
    @GET("v1/scam-numbers")
    suspend fun scamNumbers(@Query("since") sinceMillis: Long): ScamNumberFeed

    /** Verified-business registry (the B2B revenue line): numbers that should show a green badge. */
    @GET("v1/verified-businesses")
    suspend fun verifiedBusinesses(@Query("since") sinceMillis: Long): ScamNumberFeed

    /** User-submitted report of a scam number. */
    @POST("v1/reports")
    suspend fun report(@Body body: NumberReport): ReportAck

    /** Cloud (LLM-based) analysis of a message - Pro feature. */
    @POST("v1/analyze")
    suspend fun analyze(@Body body: AnalyzeRequest): AnalyzeResponse

    companion object {
        val baseUrl: String get() = BuildConfig.SCAMSHIELD_API_BASE_URL
        val isConfigured: Boolean get() = baseUrl.isNotBlank() && !baseUrl.contains("example.com")
    }
}

@Serializable
data class ScamNumberFeed(val numbers: List<FeedNumber> = emptyList(), val serverTime: Long = 0)

@Serializable
data class FeedNumber(
    val number: String,
    val label: String = "Scam",
    val reports: Int = 1,
    val businessName: String? = null
)

@Serializable
data class NumberReport(
    val number: String,
    val label: String,
    val comment: String = "",
    val appVersion: String = BuildConfig.VERSION_NAME
)

@Serializable
data class ReportAck(val ok: Boolean = true, val totalReports: Int = 0)

@Serializable
data class AnalyzeRequest(val text: String, val locale: String = "en-IN")

@Serializable
data class AnalyzeResponse(
    val score: Int = 0,
    val summary: String = "",
    val reasons: List<CloudReason> = emptyList()
) {
    @Serializable
    data class CloudReason(val code: String = "CLOUD", val title: String = "", val detail: String = "", val weight: Int = 0)
}

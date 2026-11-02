package com.scamshield.app.data

import android.util.Log
import com.scamshield.app.BuildConfig
import com.scamshield.app.data.local.ScanDao
import com.scamshield.app.data.remote.AnalyzeRequest
import com.scamshield.app.data.remote.SafeBrowsingApi
import com.scamshield.app.data.remote.SafeBrowsingRequest
import com.scamshield.app.data.remote.ScamShieldApi
import com.scamshield.app.notifications.Notifier
import com.scamshield.engine.RiskReason
import com.scamshield.engine.RiskResult
import com.scamshield.engine.ScamShieldEngine
import com.scamshield.engine.ScanCategory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Runs the on-device engine, then layers optional cloud signals on top:
 *  - Google Safe Browsing for links (free tier, needs an API key)
 *  - Your own cloud analyzer for Pro users (LLM-based explanation)
 * and stores every result so it shows in history and the Safety Score.
 */
@Singleton
class ScanRepository @Inject constructor(
    private val engine: ScamShieldEngine,
    private val safeBrowsingApi: SafeBrowsingApi,
    private val cloudApi: ScamShieldApi,
    private val scanDao: ScanDao,
    private val mapper: ScanMapper,
    private val familyRepository: FamilyRepository,
    private val notifier: Notifier
) {
    companion object {
        private const val TAG = "ScanRepository"
    }

    val recent: Flow<List<ScanRecord>> = scanDao.recent(100).map { list -> list.map(mapper::toModel) }
    val threatCount: Flow<Int> = scanDao.threatCount()
    val totalCount: Flow<Int> = scanDao.totalCount()

    suspend fun get(id: Long): ScanRecord? = scanDao.byId(id)?.let(mapper::toModel)

    suspend fun clearHistory() = scanDao.clear()

    /** Analyses pasted / shared text, a link, a number or a UPI deep link. */
    suspend fun analyze(input: String, source: ScanSource, premium: Boolean): ScanRecord = withContext(Dispatchers.IO) {
        var result = engine.analyzeAny(input)
        result = enrichWithSafeBrowsing(result)
        if (premium && result.category == ScanCategory.MESSAGE) result = enrichWithCloud(result)
        persist(result, source)
    }

    /** Analyses a raw QR payload. */
    suspend fun analyzeQr(rawValue: String, premium: Boolean): ScanRecord = withContext(Dispatchers.IO) {
        var result = engine.analyzeQr(rawValue)
        result = enrichWithSafeBrowsing(result)
        if (premium && result.category == ScanCategory.QR_TEXT) result = enrichWithCloud(result)
        persist(result, ScanSource.QR)
    }

    private suspend fun persist(result: RiskResult, source: ScanSource): ScanRecord {
        val id = scanDao.insert(mapper.toEntity(result, source))
        if (result.level.isAlertWorthy) {
            familyRepository.postEventIfProtected(
                type = "SCAN_" + result.category.name,
                title = result.summary,
                detail = result.subject,
                score = result.score,
                level = result.level
            )
            if (source == ScanSource.SHARE || source == ScanSource.OPEN_LINK) {
                notifier.showScanAlert(id, result.summary)
            }
        }
        return ScanRecord(id, System.currentTimeMillis(), result, source)
    }

    /** Looks up the link (or the worst link inside a message) in Google Safe Browsing. */
    private suspend fun enrichWithSafeBrowsing(result: RiskResult): RiskResult {
        if (!SafeBrowsingApi.isConfigured) return result
        val url = when (result.category) {
            ScanCategory.URL -> result.subject
            ScanCategory.MESSAGE, ScanCategory.QR_TEXT -> result.facts["worstLink"]
            ScanCategory.UPI_QR -> result.facts["url"]
            else -> null
        } ?: return result
        val normalized = if (url.startsWith("http", ignoreCase = true)) url else "http://$url"
        val response = withTimeoutOrNull(6_000) {
            runCatching { safeBrowsingApi.find(BuildConfig.SAFE_BROWSING_API_KEY, SafeBrowsingRequest.forUrls(listOf(normalized))) }
                .onFailure { Log.w(TAG, "Safe Browsing lookup failed", it) }
                .getOrNull()
        } ?: return result
        val match = response.matches.firstOrNull() ?: return result.adding(
            RiskReason("SAFE_BROWSING_CLEAR", "Not on Google's blocklist", "Google Safe Browsing has no report for this link (new scam pages take a while to be listed).", 0)
        )
        val threat = match.threatType.replace('_', ' ').lowercase()
        return result.adding(
            RiskReason(
                "SAFE_BROWSING", "Flagged by Google Safe Browsing ($threat)",
                "Google has confirmed this page as $threat. Do not open it.", 80
            )
        )
    }

    /** Pro: ask your backend (LLM) for a second opinion and a richer explanation. */
    private suspend fun enrichWithCloud(result: RiskResult): RiskResult {
        if (!ScamShieldApi.isConfigured) return result
        val response = withTimeoutOrNull(8_000) {
            runCatching { cloudApi.analyze(AnalyzeRequest(text = result.subject)) }
                .onFailure { Log.w(TAG, "Cloud analysis failed", it) }
                .getOrNull()
        } ?: return result
        var merged = result
        response.reasons.forEach { r ->
            merged = merged.adding(RiskReason("CLOUD_" + r.code, r.title, r.detail, r.weight))
        }
        if (response.summary.isNotBlank() && merged.reasons.size > result.reasons.size) {
            merged = merged.copy(summary = response.summary)
        }
        return merged
    }
}

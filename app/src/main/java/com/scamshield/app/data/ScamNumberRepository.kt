package com.scamshield.app.data

import android.util.Log
import com.scamshield.app.data.local.ScamNumberDao
import com.scamshield.app.data.local.ScamNumberEntity
import com.scamshield.app.data.prefs.SettingsRepository
import com.scamshield.app.data.remote.NumberReport
import com.scamshield.app.data.remote.ScamShieldApi
import com.scamshield.engine.phone.PhoneNumberAnalyzer
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

/** Local blocklist + user reports + sync with the backend feed and Firestore community reports. */
@Singleton
class ScamNumberRepository @Inject constructor(
    private val dao: ScamNumberDao,
    private val api: ScamShieldApi,
    private val settings: SettingsRepository,
    private val familyRepository: FamilyRepository
) {
    companion object {
        private const val TAG = "ScamNumberRepo"
        val REPORT_LABELS = listOf(
            "Digital arrest / police threat", "Fake bank / KYC", "Loan harassment", "Job / task scam",
            "Investment / trading scam", "Lottery / prize", "Courier / parcel", "Electricity bill",
            "Customer-care impersonation", "Sextortion", "Other scam", "Spam / telemarketing"
        )
    }

    val localReports: Flow<List<ScamNumberEntity>> = dao.localReports()
    val blocklistSize: Flow<Int> = dao.blocklistSize()

    suspend fun lookup(rawNumber: String): ScamNumberEntity? = dao.get(PhoneNumberAnalyzer.canonical(rawNumber))

    /** User reports a number. Stored locally immediately, shared with the community when possible. */
    suspend fun report(rawNumber: String, label: String) {
        val number = PhoneNumberAnalyzer.canonical(rawNumber)
        val existing = dao.get(number)
        dao.upsert(
            ScamNumberEntity(
                number = number,
                label = label,
                reports = (existing?.reports ?: 0) + 1,
                source = "LOCAL",
                businessName = null,
                updatedAt = System.currentTimeMillis()
            )
        )
        familyRepository.submitCommunityReport(number, label)
        if (ScamShieldApi.isConfigured) {
            runCatching { api.report(NumberReport(number, label)) }.onFailure { Log.w(TAG, "report upload failed", it) }
        }
    }

    suspend fun removeReport(number: String) = dao.deleteLocal(number)

    /**
     * Pulls (a) the community blocklist aggregated in Firestore by the Cloud Function and
     * (b) the curated blocklist + verified-business registry from your backend, if configured.
     */
    suspend fun sync(): Boolean {
        val since = settings.current().lastDbSync
        val now = System.currentTimeMillis()
        var changed = false
        val community = familyRepository.fetchCommunityBlocklist(since)
        if (community.isNotEmpty()) {
            dao.upsertAll(community.map {
                ScamNumberEntity(PhoneNumberAnalyzer.canonical(it.number), it.label, it.reports, "COMMUNITY", null, it.updatedAt)
            })
            changed = true
        }
        val backendOk = syncFromBackend(since)
        if (changed || backendOk) settings.setLastDbSync(now)
        return changed || backendOk
    }

    private suspend fun syncFromBackend(since: Long): Boolean {
        if (!ScamShieldApi.isConfigured) return false
        return runCatching {
            val feed = api.scamNumbers(since)
            val now = System.currentTimeMillis()
            dao.upsertAll(feed.numbers.map {
                ScamNumberEntity(PhoneNumberAnalyzer.canonical(it.number), it.label, it.reports, "REMOTE", null, now)
            })
            val verified = api.verifiedBusinesses(since)
            dao.upsertAll(verified.numbers.map {
                ScamNumberEntity(PhoneNumberAnalyzer.canonical(it.number), "Verified business", 0, "VERIFIED", it.businessName, now)
            })
            true
        }.onFailure { Log.w(TAG, "sync failed", it) }.getOrDefault(false)
    }
}

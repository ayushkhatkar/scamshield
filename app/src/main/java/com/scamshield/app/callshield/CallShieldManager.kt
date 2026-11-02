package com.scamshield.app.callshield

import android.Manifest
import android.app.role.RoleManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.ContactsContract
import androidx.annotation.ChecksSdkIntAtLeast
import androidx.core.content.ContextCompat
import com.scamshield.app.data.FamilyRepository
import com.scamshield.app.data.ScamNumberRepository
import com.scamshield.app.data.local.CallEventDao
import com.scamshield.app.data.local.CallEventEntity
import com.scamshield.app.data.prefs.SettingsRepository
import com.scamshield.app.di.ApplicationScope
import com.scamshield.app.notifications.Notifier
import com.scamshield.engine.RiskLevel
import com.scamshield.engine.RiskResult
import com.scamshield.engine.ScamShieldEngine
import com.scamshield.engine.phone.PhoneNumberAnalyzer
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

data class CallDecision(
    val number: String,
    val result: RiskResult,
    val block: Boolean,
    val warn: Boolean
)

/**
 * Decides what to do with an incoming call. Used by [ScamCallScreeningService] and by the
 * "Check a number" UI. The decision policy comes from user settings.
 *
 * Play-policy note: this uses the CALL_SCREENING role (allowed) and never touches SMS or the call log.
 */
@Singleton
class CallShieldManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val engine: ScamShieldEngine,
    private val scamNumbers: ScamNumberRepository,
    private val callEventDao: CallEventDao,
    private val settings: SettingsRepository,
    private val notifier: Notifier,
    private val familyRepository: FamilyRepository,
    @ApplicationScope private val scope: CoroutineScope
) {
    val recentEvents: Flow<List<CallEventEntity>> = callEventDao.recent()
    val blockedCount: Flow<Int> = callEventDao.blockedCount()

    /** Call screening via the system role needs Android 10+. */
    @get:ChecksSdkIntAtLeast(api = Build.VERSION_CODES.Q)
    val isSupported: Boolean get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q

    fun isRoleHeld(): Boolean {
        if (!isSupported) return false
        val rm = context.getSystemService(RoleManager::class.java) ?: return false
        return rm.isRoleAvailable(RoleManager.ROLE_CALL_SCREENING) && rm.isRoleHeld(RoleManager.ROLE_CALL_SCREENING)
    }

    /** Intent to launch with an ActivityResultLauncher to ask the user to make ScamShield the call screener. */
    fun roleRequestIntent(): Intent? {
        if (!isSupported) return null
        val rm = context.getSystemService(RoleManager::class.java) ?: return null
        if (!rm.isRoleAvailable(RoleManager.ROLE_CALL_SCREENING)) return null
        return rm.createRequestRoleIntent(RoleManager.ROLE_CALL_SCREENING)
    }

    fun hasContactsPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED

    private fun isInContacts(number: String): Boolean {
        if (!hasContactsPermission()) return false
        return runCatching {
            val uri = Uri.withAppendedPath(ContactsContract.PhoneLookup.CONTENT_FILTER_URI, Uri.encode(number))
            context.contentResolver.query(uri, arrayOf(ContactsContract.PhoneLookup._ID), null, null, null)?.use { it.count > 0 } ?: false
        }.getOrDefault(false)
    }

    /** Full evaluation: engine heuristics + local/community/remote database + policy. */
    suspend fun evaluate(rawNumber: String?): CallDecision = withContext(Dispatchers.IO) {
        val number = rawNumber?.let { PhoneNumberAnalyzer.canonical(it) }.orEmpty()
        val dbEntry = if (number.isNotEmpty()) scamNumbers.lookup(number) else null
        val recent = if (number.isNotEmpty()) callEventDao.countFrom(number, System.currentTimeMillis() - 24 * 60 * 60 * 1000L) else 0
        val ctx = PhoneNumberAnalyzer.PhoneContext(
            inContacts = number.isNotEmpty() && isInContacts(number),
            communityReports = if (dbEntry != null && dbEntry.source != "VERIFIED") dbEntry.reports.coerceAtLeast(1) else 0,
            reportLabel = dbEntry?.takeIf { it.source != "VERIFIED" }?.label,
            verifiedBusiness = dbEntry?.takeIf { it.source == "VERIFIED" }?.businessName,
            recentCalls = recent
        )
        val result = engine.analyzePhone(rawNumber, ctx)
        val s = settings.current()
        val codes = result.reasons.map { it.code }.toSet()
        val block = when {
            "VERIFIED_BUSINESS" in codes || "IN_CONTACTS" in codes -> false
            s.autoBlockDangerous && result.level == RiskLevel.DANGEROUS -> true
            s.blockHighRiskInternational && "HIGH_RISK_COUNTRY" in codes -> true
            s.blockTelemarketing && "TELEMARKETING_140" in codes -> true
            "COMMUNITY_REPORTED" in codes && dbEntry?.source == "LOCAL" -> true // numbers the user reported are always blocked
            else -> false
        }
        val warn = !block && s.warnSuspicious && result.level.isAlertWorthy
        CallDecision(number.ifEmpty { "Hidden number" }, result, block, warn)
    }

    /** Records the outcome, notifies the user and the family. Fire-and-forget. */
    fun onCallScreened(decision: CallDecision) {
        scope.launch {
            val action = when {
                decision.block -> "BLOCKED"
                decision.warn -> "WARNED"
                else -> "ALLOWED"
            }
            val reason = decision.result.reasons.firstOrNull()?.title ?: decision.result.summary
            callEventDao.insert(
                CallEventEntity(
                    number = decision.number,
                    timestamp = System.currentTimeMillis(),
                    score = decision.result.score,
                    level = decision.result.level.name,
                    action = action,
                    reason = reason
                )
            )
            if (decision.block || decision.warn) {
                notifier.showCallAlert(decision.number, decision.result.level, decision.block, reason)
                familyRepository.postEventIfProtected(
                    type = if (decision.block) "CALL_BLOCKED" else "CALL_SUSPICIOUS",
                    title = (if (decision.block) "Blocked a scam call from " else "Suspicious call from ") + decision.number,
                    detail = reason,
                    score = decision.result.score,
                    level = decision.result.level
                )
            }
        }
    }

    suspend fun reportNumber(number: String, label: String) = scamNumbers.report(number, label)

    suspend fun clearHistory() = callEventDao.clear()
}

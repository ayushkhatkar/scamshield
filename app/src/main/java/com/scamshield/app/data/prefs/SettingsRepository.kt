package com.scamshield.app.data.prefs

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.scamshield.app.data.Entitlement
import com.scamshield.app.data.FamilyRole
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

data class Settings(
    val onboardingDone: Boolean = false,
    val displayName: String = "",
    val entitlement: Entitlement = Entitlement.FREE,
    // Call Shield policy
    val autoBlockDangerous: Boolean = true,
    val blockHighRiskInternational: Boolean = true,
    val blockTelemarketing: Boolean = false,
    val warnSuspicious: Boolean = true,
    // App audit
    val appAuditAlerts: Boolean = true,
    // Family
    val familyCode: String? = null,
    val familyRole: FamilyRole? = null,
    val familyLastSeenEvent: Long = 0L,
    val fcmToken: String? = null,
    // Quota (free tier)
    val scanQuotaDate: String = "",
    val scanQuotaUsed: Int = 0,
    val lastDbSync: Long = 0L,
    val breachEmail: String = ""
)

/**
 * Single source of truth for user preferences, entitlement and the free-tier quota.
 * Entitlement is also mirrored here so screens can gate features synchronously.
 */
@Singleton
class SettingsRepository @Inject constructor(
    private val dataStore: DataStore<Preferences>
) {
    private object Keys {
        val onboardingDone = booleanPreferencesKey("onboarding_done")
        val displayName = stringPreferencesKey("display_name")
        val entitlement = stringPreferencesKey("entitlement")
        val autoBlockDangerous = booleanPreferencesKey("auto_block_dangerous")
        val blockHighRiskInternational = booleanPreferencesKey("block_high_risk_international")
        val blockTelemarketing = booleanPreferencesKey("block_telemarketing")
        val warnSuspicious = booleanPreferencesKey("warn_suspicious")
        val appAuditAlerts = booleanPreferencesKey("app_audit_alerts")
        val familyCode = stringPreferencesKey("family_code")
        val familyRole = stringPreferencesKey("family_role")
        val familyLastSeenEvent = longPreferencesKey("family_last_seen_event")
        val fcmToken = stringPreferencesKey("fcm_token")
        val scanQuotaDate = stringPreferencesKey("scan_quota_date")
        val scanQuotaUsed = intPreferencesKey("scan_quota_used")
        val lastDbSync = longPreferencesKey("last_db_sync")
        val breachEmail = stringPreferencesKey("breach_email")
    }

    companion object {
        /** Free users get this many analyses per day. Shown on the paywall. */
        const val FREE_DAILY_SCANS = 10
    }

    val settings: Flow<Settings> = dataStore.data.map { p ->
        Settings(
            onboardingDone = p[Keys.onboardingDone] ?: false,
            displayName = p[Keys.displayName] ?: "",
            entitlement = p[Keys.entitlement]?.let { runCatching { Entitlement.valueOf(it) }.getOrNull() } ?: Entitlement.FREE,
            autoBlockDangerous = p[Keys.autoBlockDangerous] ?: true,
            blockHighRiskInternational = p[Keys.blockHighRiskInternational] ?: true,
            blockTelemarketing = p[Keys.blockTelemarketing] ?: false,
            warnSuspicious = p[Keys.warnSuspicious] ?: true,
            appAuditAlerts = p[Keys.appAuditAlerts] ?: true,
            familyCode = p[Keys.familyCode],
            familyRole = p[Keys.familyRole]?.let { runCatching { FamilyRole.valueOf(it) }.getOrNull() },
            familyLastSeenEvent = p[Keys.familyLastSeenEvent] ?: 0L,
            fcmToken = p[Keys.fcmToken],
            scanQuotaDate = p[Keys.scanQuotaDate] ?: "",
            scanQuotaUsed = p[Keys.scanQuotaUsed] ?: 0,
            lastDbSync = p[Keys.lastDbSync] ?: 0L,
            breachEmail = p[Keys.breachEmail] ?: ""
        )
    }

    suspend fun current(): Settings = settings.first()

    suspend fun setOnboardingDone(done: Boolean) = dataStore.edit { it[Keys.onboardingDone] = done }
    suspend fun setDisplayName(name: String) = dataStore.edit { it[Keys.displayName] = name }
    suspend fun setEntitlement(entitlement: Entitlement) = dataStore.edit { it[Keys.entitlement] = entitlement.name }
    suspend fun setAutoBlockDangerous(v: Boolean) = dataStore.edit { it[Keys.autoBlockDangerous] = v }
    suspend fun setBlockHighRiskInternational(v: Boolean) = dataStore.edit { it[Keys.blockHighRiskInternational] = v }
    suspend fun setBlockTelemarketing(v: Boolean) = dataStore.edit { it[Keys.blockTelemarketing] = v }
    suspend fun setWarnSuspicious(v: Boolean) = dataStore.edit { it[Keys.warnSuspicious] = v }
    suspend fun setAppAuditAlerts(v: Boolean) = dataStore.edit { it[Keys.appAuditAlerts] = v }
    suspend fun setFcmToken(token: String) = dataStore.edit { it[Keys.fcmToken] = token }
    suspend fun setLastDbSync(time: Long) = dataStore.edit { it[Keys.lastDbSync] = time }
    suspend fun setBreachEmail(email: String) = dataStore.edit { it[Keys.breachEmail] = email }
    suspend fun setFamilyLastSeenEvent(time: Long) = dataStore.edit { it[Keys.familyLastSeenEvent] = time }

    suspend fun setFamily(code: String?, role: FamilyRole?) = dataStore.edit {
        if (code == null) it.remove(Keys.familyCode) else it[Keys.familyCode] = code
        if (role == null) it.remove(Keys.familyRole) else it[Keys.familyRole] = role.name
        it[Keys.familyLastSeenEvent] = System.currentTimeMillis()
    }

    /** Remaining free analyses today; unlimited (Int.MAX_VALUE) for premium users. */
    suspend fun remainingScans(): Int {
        val s = current()
        if (s.entitlement.isPremium) return Int.MAX_VALUE
        val today = LocalDate.now().toString()
        return if (s.scanQuotaDate == today) (FREE_DAILY_SCANS - s.scanQuotaUsed).coerceAtLeast(0) else FREE_DAILY_SCANS
    }

    /** Consumes one free-tier scan. Returns false if the quota is exhausted (premium always true). */
    suspend fun tryConsumeScan(): Boolean {
        val s = current()
        if (s.entitlement.isPremium) return true
        val today = LocalDate.now().toString()
        val used = if (s.scanQuotaDate == today) s.scanQuotaUsed else 0
        if (used >= FREE_DAILY_SCANS) return false
        dataStore.edit {
            it[Keys.scanQuotaDate] = today
            it[Keys.scanQuotaUsed] = used + 1
        }
        return true
    }
}

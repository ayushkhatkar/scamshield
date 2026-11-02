package com.scamshield.app.work

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.scamshield.app.data.AppAuditRepository
import com.scamshield.app.data.FamilyRepository
import com.scamshield.app.data.FamilyRole
import com.scamshield.app.data.ScamNumberRepository
import com.scamshield.app.data.prefs.SettingsRepository
import com.scamshield.app.notifications.Notifier
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.util.concurrent.TimeUnit

/** Pulls the latest scam-number blocklist and verified-business registry. */
@HiltWorker
class ScamDbSyncWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val scamNumbers: ScamNumberRepository
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        scamNumbers.sync()
        return Result.success()
    }
}

/**
 * Daily app audit with alerts. Automatic audits are a Pro feature; free users run it manually
 * from the Apps tab (that difference is one of the reasons to upgrade).
 */
@HiltWorker
class AppAuditWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val appAudit: AppAuditRepository,
    private val settings: SettingsRepository
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        val s = settings.current()
        if (!s.entitlement.isPremium) return Result.success()
        runCatching { appAudit.scanNow(notify = s.appAuditAlerts) }
        return Result.success()
    }
}

/**
 * Guardian-side fallback: polls the family feed every 15 minutes in case the FCM Cloud Function
 * is not deployed. Shows a notification for each new event from a protected member.
 */
@HiltWorker
class FamilyPollWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val family: FamilyRepository,
    private val settings: SettingsRepository,
    private val notifier: Notifier
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        val s = settings.current()
        val code = s.familyCode ?: return Result.success()
        if (s.familyRole != FamilyRole.GUARDIAN) return Result.success()
        val events = family.fetchEventsSince(code, s.familyLastSeenEvent)
        events.filter { it.level.isAlertWorthy || it.type == "TEST" }.take(3).forEach {
            notifier.showFamilyAlert("${it.memberName}: ${it.title}", it.detail)
        }
        events.maxOfOrNull { it.createdAt }?.let { settings.setFamilyLastSeenEvent(it) }
        return Result.success()
    }
}

object WorkScheduler {
    private const val SYNC = "scam_db_sync"
    private const val AUDIT = "app_audit"
    private const val FAMILY = "family_poll"

    fun scheduleAll(context: Context) {
        val wm = WorkManager.getInstance(context)
        val network = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()

        wm.enqueueUniquePeriodicWork(
            SYNC, ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<ScamDbSyncWorker>(12, TimeUnit.HOURS).setConstraints(network).build()
        )
        wm.enqueueUniquePeriodicWork(
            AUDIT, ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<AppAuditWorker>(24, TimeUnit.HOURS).build()
        )
        wm.enqueueUniquePeriodicWork(
            FAMILY, ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<FamilyPollWorker>(15, TimeUnit.MINUTES).setConstraints(network).build()
        )
    }
}

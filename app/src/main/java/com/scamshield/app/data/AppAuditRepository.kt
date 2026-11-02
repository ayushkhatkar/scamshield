package com.scamshield.app.data

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import com.scamshield.app.data.local.AppRiskDao
import com.scamshield.app.data.local.AppRiskEntity
import com.scamshield.app.notifications.Notifier
import com.scamshield.engine.RiskLevel
import com.scamshield.engine.ScamShieldEngine
import com.scamshield.engine.app.AppRiskScorer
import com.scamshield.engine.app.InstalledAppInfo
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Reads installed apps through PackageManager and scores them with the engine.
 * Requires QUERY_ALL_PACKAGES (declared in the manifest; Play allows it for security apps -
 * fill in the permission declaration form in Play Console).
 */
@Singleton
class AppAuditRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val engine: ScamShieldEngine,
    private val dao: AppRiskDao,
    private val mapper: ScanMapper,
    private val notifier: Notifier,
    private val familyRepository: FamilyRepository
) {
    val risks: Flow<List<AppRisk>> = dao.all().map { list -> list.map(mapper::toAppRisk) }
    val riskyCount: Flow<Int> = dao.riskyCount()
    val lastScanTime: Flow<Long?> = dao.lastScanTime()

    suspend fun setIgnored(packageName: String, ignored: Boolean) = dao.setIgnored(packageName, ignored)

    /**
     * Scans every user-installed app. Returns apps that are newly SUSPICIOUS/DANGEROUS since the
     * previous scan so callers can decide whether to notify.
     */
    suspend fun scanNow(notify: Boolean): List<AppRisk> = withContext(Dispatchers.IO) {
        val pm = context.packageManager
        val now = System.currentTimeMillis()
        val packages = installedPackages(pm)
        val previous = mutableMapOf<String, AppRiskEntity>()
        val entities = packages.mapNotNull { info ->
            val app = toEngineInfo(pm, info, now) ?: return@mapNotNull null
            if (app.isSystemApp) return@mapNotNull null
            val result = engine.scoreApp(app)
            val old = dao.get(app.packageName)
            previous[app.packageName] = old ?: AppRiskEntity(app.packageName, app.appName, -1, "SAFE", "", "[]", null, 0L)
            AppRiskEntity(
                packageName = app.packageName,
                appName = app.appName,
                score = result.score,
                level = result.level.name,
                summary = result.summary,
                reasonsJson = mapper.reasonsToJson(result.reasons),
                installer = app.installerPackage,
                scannedAt = now,
                ignored = old?.ignored ?: false
            )
        }
        dao.upsertAll(entities)
        dao.deleteNotIn(entities.map { it.packageName }.ifEmpty { listOf("") })

        val newlyRisky = entities.filter { e ->
            val level = RiskLevel.valueOf(e.level)
            val old = previous[e.packageName]
            level.isAlertWorthy && !e.ignored && (old == null || old.score < 0 || !RiskLevel.valueOf(old.level).isAlertWorthy)
        }.map(mapper::toAppRisk)

        if (notify) {
            newlyRisky.firstOrNull { it.level == RiskLevel.DANGEROUS }?.let { worst ->
                notifier.showAppRiskAlert(worst.appName, worst.summary)
                familyRepository.postEventIfProtected("APP_RISK", "Dangerous app installed: ${worst.appName}", worst.summary, worst.score, worst.level)
            }
        }
        newlyRisky
    }

    @Suppress("DEPRECATION")
    private fun installedPackages(pm: PackageManager): List<PackageInfo> {
        val flags = PackageManager.GET_PERMISSIONS or PackageManager.GET_SERVICES or PackageManager.GET_RECEIVERS
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pm.getInstalledPackages(PackageManager.PackageInfoFlags.of(flags.toLong()))
        } else {
            pm.getInstalledPackages(flags)
        }
    }

    @Suppress("DEPRECATION")
    private fun installerOf(pm: PackageManager, packageName: String): String? = runCatching {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            pm.getInstallSourceInfo(packageName).installingPackageName
        } else {
            pm.getInstallerPackageName(packageName)
        }
    }.getOrNull()

    private fun toEngineInfo(pm: PackageManager, info: PackageInfo, now: Long): InstalledAppInfo? {
        val ai: ApplicationInfo = info.applicationInfo ?: return null
        if (info.packageName == context.packageName) return null
        val isSystem = (ai.flags and ApplicationInfo.FLAG_SYSTEM) != 0
        val requested = info.requestedPermissions?.toList().orEmpty()
        val flags = info.requestedPermissionsFlags
        val granted = requested.filterIndexed { i, _ ->
            flags != null && i < flags.size && (flags[i] and PackageInfo.REQUESTED_PERMISSION_GRANTED) != 0
        }.toSet()
        val accessibility = info.services?.any { it.permission == AppRiskScorer.PERM_ACCESSIBILITY } ?: false
        val deviceAdmin = info.receivers?.any { it.permission == AppRiskScorer.PERM_DEVICE_ADMIN } ?: false
        return InstalledAppInfo(
            packageName = info.packageName,
            appName = runCatching { pm.getApplicationLabel(ai).toString() }.getOrDefault(info.packageName),
            requestedPermissions = requested,
            grantedPermissions = granted,
            installerPackage = installerOf(pm, info.packageName),
            isSystemApp = isSystem,
            targetSdk = ai.targetSdkVersion,
            firstInstallTimeMillis = info.firstInstallTime,
            declaresAccessibilityService = accessibility,
            declaresDeviceAdmin = deviceAdmin,
            nowMillis = now
        )
    }
}

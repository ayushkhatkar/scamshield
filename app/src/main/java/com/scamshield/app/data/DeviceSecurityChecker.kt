package com.scamshield.app.data

import android.app.KeyguardManager
import android.content.Context
import android.provider.Settings
import androidx.core.app.NotificationManagerCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

data class SecurityCheck(val id: String, val title: String, val ok: Boolean, val detail: String, val points: Int)

data class SafetyScore(val score: Int, val checks: List<SecurityCheck>)

/**
 * Computes the "Safety Score" on the home screen from device state + app state.
 * Pure read-only checks; nothing here needs special permissions.
 */
@Singleton
class DeviceSecurityChecker @Inject constructor(@ApplicationContext private val context: Context) {

    fun compute(callShieldOn: Boolean, riskyApps: Int, familyLinked: Boolean): SafetyScore {
        val keyguard = context.getSystemService(KeyguardManager::class.java)
        val screenLock = keyguard?.isDeviceSecure == true
        val adb = Settings.Global.getInt(context.contentResolver, Settings.Global.ADB_ENABLED, 0) == 1
        val devOptions = Settings.Global.getInt(context.contentResolver, Settings.Global.DEVELOPMENT_SETTINGS_ENABLED, 0) == 1
        val notifications = NotificationManagerCompat.from(context).areNotificationsEnabled()

        val checks = listOf(
            SecurityCheck(
                "call_shield", "Call Shield active", callShieldOn,
                if (callShieldOn) "Scam calls are screened before your phone rings." else "Turn on Call Shield to block scam calls automatically.", 30
            ),
            SecurityCheck(
                "screen_lock", "Screen lock set", screenLock,
                if (screenLock) "Your phone needs a PIN, pattern or fingerprint." else "Without a screen lock, anyone who picks up your phone can open your UPI apps.", 15
            ),
            SecurityCheck(
                "risky_apps", "No risky apps", riskyApps == 0,
                if (riskyApps == 0) "No suspicious apps found in the last audit." else "$riskyApps app(s) need attention in the Apps tab.", 20
            ),
            SecurityCheck(
                "usb_debugging", "USB debugging off", !adb,
                if (!adb) "Developer access is disabled." else "USB debugging is on. Scammers who get physical access can control the phone.", 10
            ),
            SecurityCheck(
                "notifications", "Alerts enabled", notifications,
                if (notifications) "ScamShield can warn you in real time." else "Enable notifications so warnings reach you.", 10
            ),
            SecurityCheck(
                "family", "Family protection", familyLinked,
                if (familyLinked) "A family member will be alerted if something looks wrong." else "Link a family member so someone has your back.", 10
            ),
            SecurityCheck(
                "dev_options", "Developer options off", !devOptions,
                if (!devOptions) "Developer options are hidden." else "Developer options are enabled; keep them off on a daily-use phone.", 5
            )
        )
        val score = checks.filter { it.ok }.sumOf { it.points }.coerceIn(0, 100)
        return SafetyScore(score, checks)
    }
}

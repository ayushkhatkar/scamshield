package com.scamshield.app.notifications

import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.scamshield.app.MainActivity
import com.scamshield.app.R
import com.scamshield.engine.RiskLevel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class Notifier @Inject constructor(@ApplicationContext private val context: Context) {

    companion object {
        const val CHANNEL_ALERTS = "alerts"
        const val CHANNEL_FAMILY = "family"
        const val CHANNEL_AUDIT = "audit"
        const val EXTRA_ROUTE = "route"
        private const val ID_CALL = 1001
        private const val ID_FAMILY = 1002
        private const val ID_AUDIT = 1003
        private const val ID_SCAN = 1004
    }

    init {
        createChannels()
    }

    private fun createChannels() {
        val nm = context.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_ALERTS, context.getString(R.string.channel_alerts), NotificationManager.IMPORTANCE_HIGH)
                .apply { description = context.getString(R.string.channel_alerts_desc) }
        )
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_FAMILY, context.getString(R.string.channel_family), NotificationManager.IMPORTANCE_HIGH)
                .apply { description = context.getString(R.string.channel_family_desc) }
        )
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_AUDIT, context.getString(R.string.channel_audit), NotificationManager.IMPORTANCE_DEFAULT)
                .apply { description = context.getString(R.string.channel_audit_desc) }
        )
    }

    fun canNotify(): Boolean {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return false
        return NotificationManagerCompat.from(context).areNotificationsEnabled()
    }

    private fun openApp(route: String, requestCode: Int): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra(EXTRA_ROUTE, route)
        }
        return PendingIntent.getActivity(context, requestCode, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }

    @SuppressLint("MissingPermission") // canNotify() checks POST_NOTIFICATIONS right before notify()
    private fun post(id: Int, builder: NotificationCompat.Builder) {
        if (!canNotify()) return
        try {
            NotificationManagerCompat.from(context).notify(id, builder.build())
        } catch (_: SecurityException) {
            // Permission revoked between the check and the call; nothing to do.
        }
    }

    fun showCallAlert(number: String, level: RiskLevel, blocked: Boolean, reason: String) {
        val title = if (blocked) context.getString(R.string.notif_call_blocked, number)
        else context.getString(R.string.notif_call_warning, number)
        post(
            ID_CALL,
            NotificationCompat.Builder(context, CHANNEL_ALERTS)
                .setSmallIcon(R.drawable.ic_shield)
                .setContentTitle(title)
                .setContentText(reason)
                .setStyle(NotificationCompat.BigTextStyle().bigText(reason))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setCategory(NotificationCompat.CATEGORY_ALARM)
                .setAutoCancel(true)
                .setContentIntent(openApp("calls", 1))
        )
    }

    fun showFamilyAlert(title: String, body: String) {
        post(
            ID_FAMILY,
            NotificationCompat.Builder(context, CHANNEL_FAMILY)
                .setSmallIcon(R.drawable.ic_shield)
                .setContentTitle(title)
                .setContentText(body)
                .setStyle(NotificationCompat.BigTextStyle().bigText(body))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)
                .setContentIntent(openApp("family", 2))
        )
    }

    fun showAppRiskAlert(appName: String, summary: String) {
        post(
            ID_AUDIT,
            NotificationCompat.Builder(context, CHANNEL_AUDIT)
                .setSmallIcon(R.drawable.ic_shield)
                .setContentTitle(context.getString(R.string.notif_app_risk, appName))
                .setContentText(summary)
                .setStyle(NotificationCompat.BigTextStyle().bigText(summary))
                .setAutoCancel(true)
                .setContentIntent(openApp("apps", 3))
        )
    }

    fun showScanAlert(scanId: Long, summary: String) {
        post(
            ID_SCAN,
            NotificationCompat.Builder(context, CHANNEL_ALERTS)
                .setSmallIcon(R.drawable.ic_shield)
                .setContentTitle(context.getString(R.string.notif_scan_danger))
                .setContentText(summary)
                .setAutoCancel(true)
                .setContentIntent(openApp("result/$scanId", 4))
        )
    }
}

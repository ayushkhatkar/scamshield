package com.scamshield.app.data

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import com.scamshield.engine.RiskResult

/**
 * One-tap routes to the official Indian reporting channels. These are intents only; the user
 * completes the action in the dialler / browser, so nothing is sent on their behalf.
 */
object ReportHelper {
    const val CYBER_HELPLINE = "1930"
    const val CYBERCRIME_PORTAL = "https://cybercrime.gov.in"
    const val SANCHAR_SAATHI = "https://sancharsaathi.gov.in" // Chakshu: report suspected fraud numbers/SMS
    const val RBI_SACHET = "https://sachet.rbi.org.in"       // Report unauthorised lenders / loan apps

    /** Starts an activity, or shows a toast on devices with no handler (tablets, TVs, some emulators). */
    fun launch(context: Context, intent: Intent, fallbackMessage: String) {
        try {
            context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(context, fallbackMessage, Toast.LENGTH_LONG).show()
        } catch (e: SecurityException) {
            Toast.makeText(context, fallbackMessage, Toast.LENGTH_LONG).show()
        }
    }

    fun dial1930(context: Context) {
        launch(context, Intent(Intent.ACTION_DIAL, Uri.parse("tel:$CYBER_HELPLINE")), "No phone app found. Call $CYBER_HELPLINE from any phone.")
    }

    fun openUrl(context: Context, url: String) {
        launch(context, Intent(Intent.ACTION_VIEW, Uri.parse(url)), "No browser found. Open $url on any device.")
    }

    /** Shares a human-readable warning (e.g. to a family WhatsApp group). */
    fun shareWarning(context: Context, result: RiskResult) {
        val text = buildString {
            appendLine("⚠️ ScamShield warning (${result.level.name.lowercase()}, ${result.score}/100)")
            appendLine(result.summary)
            appendLine()
            result.reasons.take(4).forEach { appendLine("• ${it.title}") }
            appendLine()
            appendLine("What I checked: ${result.subject.take(200)}")
            appendLine()
            appendLine("If you lost money call 1930 or report at cybercrime.gov.in")
        }
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, text)
        }
        launch(context, Intent.createChooser(send, "Share warning"), "No app available to share with.")
    }

    fun uninstallIntent(packageName: String): Intent =
        Intent(Intent.ACTION_DELETE, Uri.parse("package:$packageName")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    fun appInfoIntent(packageName: String): Intent =
        Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
}

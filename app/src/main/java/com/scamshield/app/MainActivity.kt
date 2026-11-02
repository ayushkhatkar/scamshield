package com.scamshield.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.scamshield.app.billing.BillingManager
import com.scamshield.app.data.ScanSource
import com.scamshield.app.notifications.Notifier
import com.scamshield.app.share.SharedInputHolder
import com.scamshield.app.ui.ScamShieldRoot
import com.scamshield.app.ui.theme.ScamShieldTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var sharedInput: SharedInputHolder
    @Inject lateinit var billing: BillingManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        handleIntent(intent)
        billing.connect()
        setContent {
            ScamShieldTheme {
                ScamShieldRoot()
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    /**
     * Entry points other than the launcher:
     *  - ACTION_SEND text/plain      -> "Share to ScamShield" from any app
     *  - ACTION_PROCESS_TEXT          -> "Check with ScamShield" in the text-selection toolbar
     *  - ACTION_VIEW http/https       -> "Open with ScamShield" from a link
     *  - EXTRA_ROUTE                  -> notification taps
     */
    private fun handleIntent(intent: Intent?) {
        intent ?: return
        when (intent.action) {
            Intent.ACTION_SEND -> intent.getStringExtra(Intent.EXTRA_TEXT)?.let {
                sharedInput.offerInput(it, ScanSource.SHARE)
            }
            Intent.ACTION_PROCESS_TEXT -> intent.getCharSequenceExtra(Intent.EXTRA_PROCESS_TEXT)?.toString()?.let {
                sharedInput.offerInput(it, ScanSource.PROCESS_TEXT)
            }
            Intent.ACTION_VIEW -> intent.dataString?.let {
                sharedInput.offerInput(it, ScanSource.OPEN_LINK)
            }
        }
        intent.getStringExtra(Notifier.EXTRA_ROUTE)?.let { sharedInput.offerRoute(it) }
        // Clear so a configuration change doesn't replay the same input.
        intent.action = Intent.ACTION_MAIN
        intent.removeExtra(Notifier.EXTRA_ROUTE)
    }
}

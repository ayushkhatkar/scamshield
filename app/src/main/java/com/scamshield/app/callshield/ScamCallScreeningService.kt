package com.scamshield.app.callshield

import android.os.Build
import android.telecom.Call
import android.telecom.CallScreeningService
import android.util.Log
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject

/**
 * Invoked by the system for incoming calls once the user grants ScamShield the
 * "Caller ID & spam" (CALL_SCREENING) role. The system gives us ~5 seconds to respond.
 *
 * Note: Android only routes calls from numbers NOT in the user's contacts to this service.
 */
@AndroidEntryPoint
class ScamCallScreeningService : CallScreeningService() {

    @Inject
    lateinit var manager: CallShieldManager

    override fun onScreenCall(callDetails: Call.Details) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
            callDetails.callDirection != Call.Details.DIRECTION_INCOMING
        ) {
            respondToCall(callDetails, CallResponse.Builder().build())
            return
        }

        val number = callDetails.handle?.schemeSpecificPart
        val decision = runCatching {
            runBlocking { withTimeoutOrNull(3_000) { manager.evaluate(number) } }
        }.onFailure { Log.e("CallScreening", "evaluation failed", it) }.getOrNull()

        val block = decision?.block == true
        val response = CallResponse.Builder()
            .setDisallowCall(block)
            .setRejectCall(block)
            .setSkipCallLog(false)
            .setSkipNotification(block)
            .build()
        respondToCall(callDetails, response)

        decision?.let { manager.onCallScreened(it) }
    }
}

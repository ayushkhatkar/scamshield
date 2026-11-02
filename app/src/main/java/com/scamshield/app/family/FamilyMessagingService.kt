package com.scamshield.app.family

import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.scamshield.app.data.FamilyRepository
import com.scamshield.app.di.ApplicationScope
import com.scamshield.app.notifications.Notifier
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Receives guardian alerts pushed by the Cloud Function in /firebase/functions. */
@AndroidEntryPoint
class FamilyMessagingService : FirebaseMessagingService() {

    @Inject lateinit var notifier: Notifier
    @Inject lateinit var familyRepository: FamilyRepository
    @Inject @ApplicationScope lateinit var scope: CoroutineScope

    override fun onNewToken(token: String) {
        scope.launch { familyRepository.updateFcmToken(token) }
    }

    override fun onMessageReceived(message: RemoteMessage) {
        val title = message.notification?.title ?: message.data["title"] ?: "Family alert"
        val body = message.notification?.body ?: message.data["detail"] ?: ""
        notifier.showFamilyAlert(title, body)
    }
}

package com.scamshield.app.share

import com.scamshield.app.data.ScanSource
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject
import javax.inject.Singleton

data class SharedInput(val text: String, val source: ScanSource)

/**
 * Bridges incoming intents (share sheet, "Check with ScamShield" text action, opened links and
 * notification taps) to the Compose navigation graph and the Scan screen.
 */
@Singleton
class SharedInputHolder @Inject constructor() {
    private val _pendingInput = MutableStateFlow<SharedInput?>(null)
    val pendingInput: StateFlow<SharedInput?> = _pendingInput

    private val _pendingRoute = MutableStateFlow<String?>(null)
    val pendingRoute: StateFlow<String?> = _pendingRoute

    fun offerInput(text: String, source: ScanSource) {
        _pendingInput.value = SharedInput(text.trim(), source)
    }

    fun consumeInput(): SharedInput? = _pendingInput.value.also { _pendingInput.value = null }

    fun offerRoute(route: String) {
        _pendingRoute.value = route
    }

    fun consumeRoute(): String? = _pendingRoute.value.also { _pendingRoute.value = null }
}

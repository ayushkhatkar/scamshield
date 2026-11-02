package com.scamshield.app.ui.scan

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.codescanner.GmsBarcodeScannerOptions
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning
import com.scamshield.app.data.ScanRecord
import com.scamshield.app.data.ScanRepository
import com.scamshield.app.data.ScanSource
import com.scamshield.app.data.prefs.SettingsRepository
import com.scamshield.app.share.SharedInputHolder
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed class ScanEvent {
    data class Done(val id: Long) : ScanEvent()
    data object QuotaExceeded : ScanEvent()
    data class Error(val message: String) : ScanEvent()
}

@HiltViewModel
class ScanViewModel @Inject constructor(
    private val scans: ScanRepository,
    private val settings: SettingsRepository,
    private val sharedInput: SharedInputHolder
) : ViewModel() {

    val input = MutableStateFlow("")
    val busy = MutableStateFlow(false)
    val remaining = MutableStateFlow(SettingsRepository.FREE_DAILY_SCANS)
    val history: StateFlow<List<ScanRecord>> = scans.recent.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _events = MutableSharedFlow<ScanEvent>(extraBufferCapacity = 4)
    val events: SharedFlow<ScanEvent> = _events

    init {
        viewModelScope.launch { refreshQuota() }
        // Text shared from another app or selected via "Check with ScamShield".
        viewModelScope.launch {
            sharedInput.pendingInput.filterNotNull().collect {
                val pending = sharedInput.consumeInput() ?: return@collect
                input.value = pending.text
                analyze(pending.source)
            }
        }
    }

    private suspend fun refreshQuota() {
        remaining.value = settings.remainingScans()
    }

    fun analyze(source: ScanSource = ScanSource.MANUAL) {
        val text = input.value.trim()
        if (text.isEmpty() || busy.value) return
        viewModelScope.launch {
            if (!settings.tryConsumeScan()) {
                _events.tryEmit(ScanEvent.QuotaExceeded)
                return@launch
            }
            busy.value = true
            try {
                val premium = settings.current().entitlement.isPremium
                val record = scans.analyze(text, source, premium)
                _events.tryEmit(ScanEvent.Done(record.id))
            } catch (e: Exception) {
                _events.tryEmit(ScanEvent.Error(e.message ?: "Analysis failed"))
            } finally {
                busy.value = false
                refreshQuota()
            }
        }
    }

    /** Opens Google's full-screen code scanner (no camera permission needed). */
    fun scanQr(context: Context) {
        val options = GmsBarcodeScannerOptions.Builder()
            .setBarcodeFormats(Barcode.FORMAT_QR_CODE)
            .enableAutoZoom()
            .build()
        GmsBarcodeScanning.getClient(context, options).startScan()
            .addOnSuccessListener { barcode ->
                val raw = barcode.rawValue ?: return@addOnSuccessListener
                viewModelScope.launch {
                    if (!settings.tryConsumeScan()) {
                        _events.tryEmit(ScanEvent.QuotaExceeded)
                        return@launch
                    }
                    busy.value = true
                    try {
                        val premium = settings.current().entitlement.isPremium
                        val record = scans.analyzeQr(raw, premium)
                        _events.tryEmit(ScanEvent.Done(record.id))
                    } catch (e: Exception) {
                        _events.tryEmit(ScanEvent.Error(e.message ?: "Analysis failed"))
                    } finally {
                        busy.value = false
                        refreshQuota()
                    }
                }
            }
            .addOnFailureListener { e ->
                _events.tryEmit(ScanEvent.Error("Scanner unavailable: ${e.message}. Make sure Google Play services is up to date."))
            }
    }

    fun clearHistory() {
        viewModelScope.launch { scans.clearHistory() }
    }
}

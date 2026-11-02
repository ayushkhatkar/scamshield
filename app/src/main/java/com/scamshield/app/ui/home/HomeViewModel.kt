package com.scamshield.app.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.scamshield.app.callshield.CallShieldManager
import com.scamshield.app.data.AppAuditRepository
import com.scamshield.app.data.DeviceSecurityChecker
import com.scamshield.app.data.Entitlement
import com.scamshield.app.data.SafetyScore
import com.scamshield.app.data.ScanRepository
import com.scamshield.app.data.prefs.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

data class HomeUiState(
    val safety: SafetyScore? = null,
    val entitlement: Entitlement = Entitlement.FREE,
    val callShieldOn: Boolean = false,
    val callShieldSupported: Boolean = true,
    val blockedCalls: Int = 0,
    val threatsFound: Int = 0,
    val totalScans: Int = 0,
    val riskyApps: Int = 0,
    val familyLinked: Boolean = false,
    val remainingScans: Int = SettingsRepository.FREE_DAILY_SCANS
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val settings: SettingsRepository,
    private val callShield: CallShieldManager,
    private val appAudit: AppAuditRepository,
    private val scans: ScanRepository,
    private val security: DeviceSecurityChecker
) : ViewModel() {

    /** Bumped from the UI on resume so the role / device checks are re-read. */
    private val refreshTick = MutableStateFlow(0)

    val state: StateFlow<HomeUiState> = combine(
        settings.settings,
        callShield.blockedCount,
        appAudit.riskyCount,
        scans.threatCount,
        scans.totalCount,
        refreshTick
    ) { values ->
        @Suppress("UNCHECKED_CAST")
        val s = values[0] as com.scamshield.app.data.prefs.Settings
        val blocked = values[1] as Int
        val risky = values[2] as Int
        val threats = values[3] as Int
        val total = values[4] as Int
        val callShieldOn = callShield.isRoleHeld()
        val familyLinked = s.familyCode != null
        HomeUiState(
            safety = security.compute(callShieldOn, risky, familyLinked),
            entitlement = s.entitlement,
            callShieldOn = callShieldOn,
            callShieldSupported = callShield.isSupported,
            blockedCalls = blocked,
            threatsFound = threats,
            totalScans = total,
            riskyApps = risky,
            familyLinked = familyLinked,
            remainingScans = settings.remainingScans()
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState())

    fun refresh() {
        refreshTick.value = refreshTick.value + 1
    }

    fun roleRequestIntent() = callShield.roleRequestIntent()
}

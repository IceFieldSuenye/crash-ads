package hey.skipad.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import hey.skipad.data.AppSettings
import hey.skipad.data.LogStore
import hey.skipad.data.SettingsRepository
import hey.skipad.data.SkipLogEntry
import hey.skipad.data.SkipStats
import hey.skipad.data.StatsRepository
import hey.skipad.data.RewardMode
import hey.skipad.service.AdSkipService
import hey.skipad.util.AccessibilityUtils
import hey.skipad.util.NotificationHelper
import hey.skipad.util.OverlayUtils
import hey.skipad.util.PauseState
import hey.skipad.util.PowerUtils
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class MainViewModel(private val app: Application) : AndroidViewModel(app) {

    val settings: StateFlow<AppSettings> =
        SettingsRepository.settings.stateIn(viewModelScope, SharingStarted.Eagerly, AppSettings())

    val stats: StateFlow<SkipStats> =
        StatsRepository.stats.stateIn(viewModelScope, SharingStarted.Eagerly, SkipStats())

    val logs: StateFlow<List<SkipLogEntry>> = LogStore.entries

    val serviceRunning: StateFlow<Boolean> = AdSkipService.running

    private val _serviceEnabled = MutableStateFlow(
        AccessibilityUtils.isServiceEnabled(app, AdSkipService::class.java),
    )
    val serviceEnabled: StateFlow<Boolean> = _serviceEnabled.asStateFlow()

    private val _batteryIgnored = MutableStateFlow(PowerUtils.isIgnoringBatteryOptimizations(app))
    val batteryIgnored: StateFlow<Boolean> = _batteryIgnored.asStateFlow()

    private val _overlayGranted = MutableStateFlow(OverlayUtils.canDrawOverlays(app))
    val overlayGranted: StateFlow<Boolean> = _overlayGranted.asStateFlow()

    private val _paused = MutableStateFlow(PauseState.isPaused(app))
    val paused: StateFlow<Boolean> = _paused.asStateFlow()

    fun refreshSystemState() {
        _serviceEnabled.value = AccessibilityUtils.isServiceEnabled(app, AdSkipService::class.java)
        _batteryIgnored.value = PowerUtils.isIgnoringBatteryOptimizations(app)
        _overlayGranted.value = OverlayUtils.canDrawOverlays(app)
        _paused.value = PauseState.isPaused(app)
    }

    fun setPaused(value: Boolean) {
        PauseState.set(app, value)
        _paused.value = value
        NotificationHelper.notifyRunning(app)
    }

    fun setEnabled(value: Boolean) {
        update { it.copy(enabled = value) }
    }

    fun setCountdown(value: Boolean) {
        update { it.copy(matchCountdown = value) }
    }

    fun setViewId(value: Boolean) {
        update { it.copy(matchViewId = value) }
    }

    fun setContentDescription(value: Boolean) {
        update { it.copy(matchContentDescription = value) }
    }

    fun setGestureFallback(value: Boolean) {
        update { it.copy(gestureFallback = value) }
    }

    fun setAggressive(value: Boolean) {
        update { it.copy(aggressive = value) }
    }

    fun setShowToast(value: Boolean) {
        update { it.copy(showToast = value) }
    }

    fun setCooldown(value: Int) {
        update { it.copy(cooldownMs = value.coerceIn(400, 3000)) }
    }

    fun setRewardMode(mode: RewardMode) {
        update { it.copy(rewardMode = mode) }
    }

    fun setClosePopups(value: Boolean) {
        update { it.copy(closePopups = value) }
    }

    fun setNotifySkip(value: Boolean) {
        update { it.copy(notifySkip = value) }
    }

    fun setOcrEnabled(value: Boolean) {
        update { it.copy(ocrEnabled = value) }
    }

    fun setTapAdLabel(value: Boolean) {
        update { it.copy(tapAdLabel = value) }
    }

    fun setShowRunningNotification(value: Boolean) {
        update { it.copy(showRunningNotification = value) }
    }

    fun setAutoMuteVolume(value: Boolean) {
        update { it.copy(autoMuteVolume = value) }
    }

    fun setAutoSwipeFeed(value: Boolean) {
        update { it.copy(autoSwipeFeed = value) }
    }

    fun requestOverlayPermission() = OverlayUtils.requestOverlayPermission(app)

    fun addBlacklist(packageName: String) {
        val clean = packageName.trim()
        if (clean.isEmpty()) return
        update { it.copy(blacklist = it.blacklist + clean) }
    }

    fun removeBlacklist(packageName: String) {
        update { it.copy(blacklist = it.blacklist - packageName) }
    }

    fun clearLogs() = LogStore.clear()

    fun openAccessibilitySettings() = AccessibilityUtils.openAccessibilitySettings(app)

    fun requestBatteryWhitelist() = PowerUtils.requestIgnoreBatteryOptimizations(app)

    fun openOemBackgroundSettings() = PowerUtils.openOemBackgroundSettings(app)

    private fun update(transform: (AppSettings) -> AppSettings) {
        viewModelScope.launch { SettingsRepository.update(transform) }
    }

    companion object {
        fun factory(app: Application): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T = MainViewModel(app) as T
        }
    }
}

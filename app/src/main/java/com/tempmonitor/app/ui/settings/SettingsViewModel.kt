package com.tempmonitor.app.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tempmonitor.app.data.preferences.MonitorPreferences
import com.tempmonitor.app.data.preferences.MonitorSettings
import com.tempmonitor.app.session.ForegroundAppDetector
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val preferences: MonitorPreferences,
    private val foregroundAppDetector: ForegroundAppDetector
) : ViewModel() {

    val settings: StateFlow<MonitorSettings> = preferences.settings.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = MonitorSettings()
    )

    fun hasUsageStatsPermission(): Boolean = foregroundAppDetector.hasUsageStatsPermission()

    fun setInterval(seconds: Int) {
        viewModelScope.launch { preferences.setInterval(seconds) }
    }

    fun setThresholds(warm: Float, hot: Float) {
        viewModelScope.launch { preferences.setThresholds(warm, hot) }
    }

    fun setNotificationsEnabled(enabled: Boolean) {
        viewModelScope.launch { preferences.setNotificationsEnabled(enabled) }
    }

    fun setAutoStartOnBoot(enabled: Boolean) {
        viewModelScope.launch { preferences.setAutoStartOnBoot(enabled) }
    }

    fun setAutoSessionGaming(enabled: Boolean) {
        viewModelScope.launch { preferences.setAutoSessionGaming(enabled) }
    }

    fun setAlwaysMonitor(enabled: Boolean) {
        viewModelScope.launch { preferences.setAlwaysMonitor(enabled) }
    }
}

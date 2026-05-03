package com.tempmonitor.app.ui.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tempmonitor.app.data.TemperatureData
import com.tempmonitor.app.data.preferences.MonitorPreferences
import com.tempmonitor.app.data.preferences.MonitorSettings
import com.tempmonitor.app.service.TemperatureState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

data class DashboardUiState(
    val isRunning: Boolean = false,
    val latest: TemperatureData? = null,
    val settings: MonitorSettings = MonitorSettings()
)

@HiltViewModel
@OptIn(ExperimentalCoroutinesApi::class)
class DashboardViewModel @Inject constructor(
    preferences: MonitorPreferences
) : ViewModel() {

    val uiState: StateFlow<DashboardUiState> = combine(
        TemperatureState.isRunning,
        TemperatureState.latest,
        preferences.settings
    ) { running, latest, settings ->
        DashboardUiState(running, latest, settings)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = DashboardUiState()
    )
}

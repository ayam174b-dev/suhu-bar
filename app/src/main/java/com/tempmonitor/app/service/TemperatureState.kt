package com.tempmonitor.app.service

import com.tempmonitor.app.data.BatteryStats
import com.tempmonitor.app.data.TemperatureData
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Process-wide bus the foreground service writes to and the UI observes.
 * Kept as a singleton object instead of injecting through Hilt so the
 * notification can be updated without the UI being attached.
 */
object TemperatureState {
    private val _isRunning = MutableStateFlow(false)
    val isRunning: StateFlow<Boolean> = _isRunning

    private val _latest = MutableStateFlow<TemperatureData?>(null)
    val latest: StateFlow<TemperatureData?> = _latest

    private val _batteryStats = MutableStateFlow<BatteryStats?>(null)
    val batteryStats: StateFlow<BatteryStats?> = _batteryStats

    fun setRunning(running: Boolean) {
        _isRunning.value = running
    }

    fun publish(data: TemperatureData) {
        _latest.value = data
    }

    fun publishBattery(stats: BatteryStats) {
        _batteryStats.value = stats
    }
}

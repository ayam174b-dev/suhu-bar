package com.tempmonitor.app.data

/**
 * Snapshot of the live battery state.
 *
 * - [currentNowMa] / [currentAvgMa] follow the framework's sign convention:
 *   positive = current flowing INTO the battery (charging),
 *   negative = current flowing OUT of the battery (discharging).
 *   Some OEM kernels invert the sign; [BatteryStatsReader] normalises this when possible.
 *
 * - [chargeTimeRemainingMs] is the OS-estimated time until 100 % when [plugged]; null if the
 *   framework cannot compute it yet (e.g. the device just plugged in).
 */
data class BatteryStats(
    val levelPercent: Float,
    val currentNowMa: Float?,
    val currentAvgMa: Float?,
    val chargeCounterMah: Float?,
    val voltageMv: Float?,
    val plugged: Boolean,
    val pluggedSource: PluggedSource?,
    val status: ChargingStatus,
    val isFull: Boolean,
    val chargeTimeRemainingMs: Long?,
    val timestamp: Long
) {
    val direction: CurrentDirection
        get() = when {
            plugged && (currentNowMa ?: 0f) > 5f -> CurrentDirection.CHARGING
            !plugged && (currentNowMa ?: 0f) < -5f -> CurrentDirection.DISCHARGING
            plugged && isFull -> CurrentDirection.FULL
            plugged -> CurrentDirection.IDLE_PLUGGED
            else -> CurrentDirection.IDLE
        }
}

enum class PluggedSource { AC, USB, WIRELESS, DOCK, OTHER }

enum class ChargingStatus { UNKNOWN, CHARGING, DISCHARGING, NOT_CHARGING, FULL }

enum class CurrentDirection { CHARGING, DISCHARGING, FULL, IDLE_PLUGGED, IDLE }

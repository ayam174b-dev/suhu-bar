package com.tempmonitor.app.data

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Build
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs

/**
 * Reads the live battery snapshot used by the Dashboard and the full-charge alert.
 *
 * Pulls instantaneous current via [BatteryManager.BATTERY_PROPERTY_CURRENT_NOW] (microamps)
 * and average current via [BatteryManager.BATTERY_PROPERTY_CURRENT_AVERAGE]. Combined with
 * the sticky `ACTION_BATTERY_CHANGED` extras (level, voltage, plugged source), this gives a
 * complete picture of charge / discharge.
 */
@Singleton
class BatteryStatsReader @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val batteryManager: BatteryManager =
        context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager

    fun read(): BatteryStats {
        val intent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val level = intent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = intent?.getIntExtra(BatteryManager.EXTRA_SCALE, 100) ?: 100
        val pluggedRaw = intent?.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) ?: 0
        val statusRaw = intent?.getIntExtra(
            BatteryManager.EXTRA_STATUS,
            BatteryManager.BATTERY_STATUS_UNKNOWN
        ) ?: BatteryManager.BATTERY_STATUS_UNKNOWN
        val voltageMv = intent?.getIntExtra(BatteryManager.EXTRA_VOLTAGE, -1)?.takeIf { it > 0 }

        val percent: Float = when {
            level >= 0 && scale > 0 -> level * 100f / scale
            else -> {
                val capacityPct = runCatching {
                    batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
                }.getOrNull() ?: -1
                if (capacityPct in 0..100) capacityPct.toFloat() else 0f
            }
        }

        val currentNowMa: Float? = readPropertyAsLong(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW)
            ?.let { it / 1000f }
        val currentAvgMa: Float? = readPropertyAsLong(BatteryManager.BATTERY_PROPERTY_CURRENT_AVERAGE)
            ?.let { it / 1000f }
        val chargeCounterMah: Float? = readPropertyAsLong(BatteryManager.BATTERY_PROPERTY_CHARGE_COUNTER)
            ?.let { it / 1000f }

        val plugged = pluggedRaw != 0
        val pluggedSource = when (pluggedRaw) {
            BatteryManager.BATTERY_PLUGGED_AC -> PluggedSource.AC
            BatteryManager.BATTERY_PLUGGED_USB -> PluggedSource.USB
            BatteryManager.BATTERY_PLUGGED_WIRELESS -> PluggedSource.WIRELESS
            // BATTERY_PLUGGED_DOCK introduced in API 33
            8 -> PluggedSource.DOCK
            0 -> null
            else -> PluggedSource.OTHER
        }
        val status = when (statusRaw) {
            BatteryManager.BATTERY_STATUS_CHARGING -> ChargingStatus.CHARGING
            BatteryManager.BATTERY_STATUS_DISCHARGING -> ChargingStatus.DISCHARGING
            BatteryManager.BATTERY_STATUS_NOT_CHARGING -> ChargingStatus.NOT_CHARGING
            BatteryManager.BATTERY_STATUS_FULL -> ChargingStatus.FULL
            else -> ChargingStatus.UNKNOWN
        }

        // Normalise sign: when plugged, current_now should be positive on most kernels but a
        // few flip it. Use the average to disambiguate.
        val normalisedNow = normaliseSign(currentNowMa, currentAvgMa, plugged)
        val normalisedAvg = normaliseSign(currentAvgMa, currentAvgMa, plugged)

        // OS-level ETA (API 28+); falls back to manual estimate when null/zero.
        val etaMs: Long? = if (plugged) {
            val osEta = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                runCatching { batteryManager.computeChargeTimeRemaining() }
                    .getOrNull()?.takeIf { it > 0 }
            } else null
            osEta ?: estimateEta(percent, chargeCounterMah, normalisedAvg ?: normalisedNow)
        } else null

        // "Full" heuristic: explicit FULL status, or plugged with very high level and current
        // tapering to near-zero (battery management chip stopping the input).
        val isFull = status == ChargingStatus.FULL ||
            (plugged && percent >= 99f && (normalisedNow == null || abs(normalisedNow) <= 50f))

        return BatteryStats(
            levelPercent = percent,
            currentNowMa = normalisedNow,
            currentAvgMa = normalisedAvg,
            chargeCounterMah = chargeCounterMah,
            voltageMv = voltageMv?.toFloat(),
            plugged = plugged,
            pluggedSource = pluggedSource,
            status = status,
            isFull = isFull,
            chargeTimeRemainingMs = etaMs,
            timestamp = System.currentTimeMillis()
        )
    }

    private fun readPropertyAsLong(id: Int): Long? {
        // Many devices expose CURRENT_NOW only as Int; query both ways and return whichever
        // looks valid (Int.MIN_VALUE is the sentinel for "not implemented").
        val asLong = runCatching { batteryManager.getLongProperty(id) }.getOrNull()
        if (asLong != null && asLong != Long.MIN_VALUE && asLong != 0L) return asLong
        val asInt = runCatching { batteryManager.getIntProperty(id) }.getOrNull()
        if (asInt != null && asInt != Int.MIN_VALUE) return asInt.toLong()
        return asLong
    }

    private fun normaliseSign(value: Float?, hint: Float?, plugged: Boolean): Float? {
        if (value == null) return null
        // When plugged, current should be positive. If both value and hint look negative on a
        // plugged device, flip them — that kernel reports inverted signs.
        return if (plugged && value < 0f && (hint == null || hint < 0f)) -value else value
    }

    private fun estimateEta(
        percent: Float,
        chargeCounterMah: Float?,
        currentMa: Float?
    ): Long? {
        if (percent >= 100f) return 0L
        if (chargeCounterMah == null || currentMa == null || currentMa <= 50f) return null
        val totalCapacityMah = chargeCounterMah / (percent / 100f).coerceAtLeast(0.01f)
        val remainingMah = (totalCapacityMah - chargeCounterMah).coerceAtLeast(0f)
        val hours = remainingMah / currentMa
        return (hours * 60f * 60f * 1000f).toLong()
    }
}

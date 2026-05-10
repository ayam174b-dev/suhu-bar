package com.tempmonitor.app.data

import com.tempmonitor.app.service.TemperatureState
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs

/**
 * Running averages of charging / discharging current and power.
 *
 * Watt is computed per sample as `voltageV * |currentA|` so charging Watts (positive direction)
 * and discharging Watts (negative direction) are reported as positive magnitudes; the sign is
 * encoded in the field name. Each direction has its own running sum so the user can compare
 * "rata-rata masuk" vs "rata-rata keluar" without one drowning the other.
 *
 * The aggregator is a process-singleton so the Dashboard can keep accumulating across
 * configuration changes, while still being resettable by the user when a new charging cycle
 * begins.
 */
@Singleton
class BatteryStatsAggregator @Inject constructor() {

    private val state = AggregatorState()

    /** Latest snapshot pushed by the service loop. */
    fun feed(stats: BatteryStats) {
        synchronized(state) {
            if (state.startedAtMs == 0L) state.startedAtMs = stats.timestamp
            state.lastTimestampMs = stats.timestamp
            state.totalSamples += 1

            val voltageV = stats.voltageMv?.let { it / 1000f }
            val currentMa = stats.currentNowMa ?: return@synchronized
            // Watts = V * A. mV * mA => µW; divide by 1_000_000 to get W.
            val instantWatt: Float? = stats.voltageMv?.let { mv -> abs(mv * currentMa) / 1_000_000f }

            if (currentMa > 0f) {
                state.chargeSamples += 1
                state.chargeCurrentSumMa += currentMa
                if (instantWatt != null) state.chargeWattSum += instantWatt
            } else if (currentMa < 0f) {
                state.dischargeSamples += 1
                state.dischargeCurrentSumMa += -currentMa
                if (instantWatt != null) state.dischargeWattSum += instantWatt
            }
            voltageV?.let {
                state.voltageSum += it
                state.voltageSamples += 1
            }
        }
        TemperatureState.publishAggregate(snapshot())
    }

    fun reset() {
        synchronized(state) {
            state.reset()
        }
        TemperatureState.publishAggregate(snapshot())
    }

    fun snapshot(): BatteryAggregate = synchronized(state) {
        BatteryAggregate(
            startedAtMs = state.startedAtMs,
            lastTimestampMs = state.lastTimestampMs,
            totalSamples = state.totalSamples,
            chargeSamples = state.chargeSamples,
            avgChargeMa = state.chargeSamples.takeIf { it > 0 }
                ?.let { state.chargeCurrentSumMa / it },
            avgChargeWatt = state.chargeSamples.takeIf { it > 0 }
                ?.let { state.chargeWattSum / it },
            dischargeSamples = state.dischargeSamples,
            avgDischargeMa = state.dischargeSamples.takeIf { it > 0 }
                ?.let { state.dischargeCurrentSumMa / it },
            avgDischargeWatt = state.dischargeSamples.takeIf { it > 0 }
                ?.let { state.dischargeWattSum / it },
            avgVoltageV = state.voltageSamples.takeIf { it > 0 }
                ?.let { state.voltageSum / it }
        )
    }

    private class AggregatorState {
        var startedAtMs: Long = 0L
        var lastTimestampMs: Long = 0L
        var totalSamples: Int = 0
        var chargeSamples: Int = 0
        var chargeCurrentSumMa: Float = 0f
        var chargeWattSum: Float = 0f
        var dischargeSamples: Int = 0
        var dischargeCurrentSumMa: Float = 0f
        var dischargeWattSum: Float = 0f
        var voltageSum: Float = 0f
        var voltageSamples: Int = 0

        fun reset() {
            startedAtMs = 0L
            lastTimestampMs = 0L
            totalSamples = 0
            chargeSamples = 0
            chargeCurrentSumMa = 0f
            chargeWattSum = 0f
            dischargeSamples = 0
            dischargeCurrentSumMa = 0f
            dischargeWattSum = 0f
            voltageSum = 0f
            voltageSamples = 0
        }
    }
}

/**
 * Public snapshot of the running averages. All averages are null until at least one sample of
 * the relevant direction has been seen.
 */
data class BatteryAggregate(
    val startedAtMs: Long,
    val lastTimestampMs: Long,
    val totalSamples: Int,
    val chargeSamples: Int,
    val avgChargeMa: Float?,
    val avgChargeWatt: Float?,
    val dischargeSamples: Int,
    val avgDischargeMa: Float?,
    val avgDischargeWatt: Float?,
    val avgVoltageV: Float?
)

/** Convenience: Watt for the current instantaneous sample using V × A. */
fun BatteryStats.instantPowerWatt(): Float? {
    val mv = voltageMv ?: return null
    val ma = currentNowMa ?: return null
    return abs(mv * ma) / 1_000_000f
}

package com.tempmonitor.app.data

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Build
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * Reads battery and CPU temperatures from the most reliable source available.
 *
 * Battery temperature is gathered from a priority chain so that we always pick the
 * source that produces a *fresh* reading — many MIUI / HyperOS builds cache the
 * sticky `ACTION_BATTERY_CHANGED` extra and never refresh it, which is what causes
 * the well-known "stuck at the same value for hours" symptom.
 *
 *  1. Thermal zone whose `type` contains `batt` (`/sys/class/thermal/thermal_zone*`).
 *     Refreshes on every read on Qualcomm/MTK kernels.
 *  2. Power-supply sysfs nodes (`/sys/class/power_supply/.../temp`).
 *  3. An *active* [BroadcastReceiver] for [Intent.ACTION_BATTERY_CHANGED]. This wakes
 *     up whenever the system actually publishes a new battery snapshot, so values
 *     stay current even when the sticky cache does not.
 *
 * CPU temperature is best-effort from `/sys/class/thermal/thermal_zone*` matching
 * `cpu` / `soc` / `tsens` types.
 */
@Singleton
class TemperatureReader @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val broadcastTemp = MutableStateFlow<Float?>(null)
    @Volatile private var lastBatterySource: String = "—"
    @Volatile private var receiverRegistered = false

    private val batteryReceiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context?, intent: Intent?) {
            val raw = intent?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, Int.MIN_VALUE)
                ?: return
            if (raw == Int.MIN_VALUE) return
            val celsius = raw / 10f
            if (celsius.isValid()) {
                broadcastTemp.value = celsius
                Log.d(TAG, "Battery broadcast updated: $celsius°C")
            }
        }
    }

    private fun ensureReceiverRegistered() {
        if (receiverRegistered) return
        synchronized(this) {
            if (receiverRegistered) return
            val filter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
            val sticky = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.registerReceiver(batteryReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
            } else {
                context.registerReceiver(batteryReceiver, filter)
            }
            // Seed with whatever the sticky broadcast carries right now.
            val raw = sticky?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, Int.MIN_VALUE)
                ?: Int.MIN_VALUE
            if (raw != Int.MIN_VALUE) {
                val celsius = raw / 10f
                if (celsius.isValid()) broadcastTemp.value = celsius
            }
            receiverRegistered = true
        }
    }

    fun read(): TemperatureData? {
        ensureReceiverRegistered()

        val battery = readBatteryTempFromThermalZone()?.also { lastBatterySource = "thermal_zone(battery)" }
            ?: readBatteryTempFromSysfs()?.also { lastBatterySource = "power_supply sysfs" }
            ?: broadcastTemp.value?.also { lastBatterySource = "BroadcastReceiver" }
            ?: return null

        val cpu = readCpuTempFromThermalZone()
        return TemperatureData(
            batteryTemp = battery,
            cpuTemp = cpu,
            timestamp = System.currentTimeMillis()
        )
    }

    /** Returns a snapshot describing each accessible source — used by the Bantuan tab. */
    fun getDiagnostics(): Diagnostics {
        ensureReceiverRegistered()
        val zones = mutableListOf<ThermalZoneInfo>()
        val zoneDir = File("/sys/class/thermal")
        zoneDir.listFiles { f -> f.name.startsWith("thermal_zone") }?.sortedBy { it.name }?.forEach { zone ->
            val typeFile = File(zone, "type")
            val tempFile = File(zone, "temp")
            val type = runCatching { typeFile.readText().trim() }.getOrNull()
            val rawTemp = runCatching { tempFile.readText().trim().toLongOrNull() }.getOrNull()
            val celsius = rawTemp?.let { normalizeKernelTemp(it) }?.takeIf { it.isValid() }
            zones += ThermalZoneInfo(
                zoneName = zone.name,
                type = type,
                celsius = celsius,
                readable = tempFile.canRead()
            )
        }

        val sysfsBattery = BATTERY_SYSFS_PATHS.map { path ->
            val file = File(path)
            val raw = runCatching { file.readText().trim().toLongOrNull() }.getOrNull()
            val celsius = raw?.let { normalizeKernelTemp(it) }?.takeIf { it.isValid() }
            SysfsPathInfo(path, exists = file.exists(), readable = file.canRead(), celsius = celsius)
        }

        return Diagnostics(
            chosenSource = lastBatterySource,
            broadcastCelsius = broadcastTemp.value,
            sysfsBatteryPaths = sysfsBattery,
            thermalZones = zones
        )
    }

    private fun readBatteryTempFromThermalZone(): Float? {
        val zoneDir = File("/sys/class/thermal")
        val zones = zoneDir.listFiles { f -> f.name.startsWith("thermal_zone") } ?: return null
        var best: Float? = null
        for (zone in zones) {
            val tempFile = File(zone, "temp")
            val typeFile = File(zone, "type")
            if (!tempFile.exists() || !tempFile.canRead()) continue
            val type = runCatching { typeFile.readText().trim().lowercase() }.getOrNull().orEmpty()
            if (!type.contains("batt") && !type.contains("bms")) continue
            val raw = runCatching { tempFile.readText().trim().toLongOrNull() }.getOrNull() ?: continue
            val celsius = normalizeKernelTemp(raw)
            if (!celsius.isValid()) continue
            // Prefer zones explicitly named "battery" over generic "*-batt-therm".
            if (type == "battery") return celsius
            if (best == null) best = celsius
        }
        return best
    }

    private fun readBatteryTempFromSysfs(): Float? {
        for (path in BATTERY_SYSFS_PATHS) {
            val file = File(path)
            if (!file.exists() || !file.canRead()) continue
            val raw = runCatching { file.readText().trim().toLongOrNull() }.getOrNull() ?: continue
            val celsius = normalizeKernelTemp(raw)
            if (celsius.isValid()) {
                Log.d(TAG, "Battery temp via sysfs: $path -> $celsius°C")
                return celsius
            }
        }
        return null
    }

    private fun readCpuTempFromThermalZone(): Float? {
        val zoneDir = File("/sys/class/thermal")
        val zones = zoneDir.listFiles { f -> f.name.startsWith("thermal_zone") } ?: return null
        var best: Float? = null
        for (zone in zones) {
            val tempFile = File(zone, "temp")
            val typeFile = File(zone, "type")
            if (!tempFile.exists() || !tempFile.canRead()) continue
            val raw = runCatching { tempFile.readText().trim().toLongOrNull() }.getOrNull() ?: continue
            val celsius = normalizeKernelTemp(raw)
            if (!celsius.isValid()) continue
            val type = runCatching { typeFile.readText().trim().lowercase() }.getOrNull().orEmpty()
            val preferred = type.contains("cpu") || type.contains("soc") || type.contains("tsens")
            if (preferred) return celsius
            if (best == null || celsius > best) best = celsius
        }
        return best
    }

    private fun normalizeKernelTemp(raw: Long): Float = when {
        raw > 1_000 -> raw / 1000f
        raw > 200 -> raw / 10f
        else -> raw.toFloat()
    }

    private fun Float.isValid(): Boolean = this in 0f..99.9f

    data class Diagnostics(
        val chosenSource: String,
        val broadcastCelsius: Float?,
        val sysfsBatteryPaths: List<SysfsPathInfo>,
        val thermalZones: List<ThermalZoneInfo>
    )

    data class SysfsPathInfo(
        val path: String,
        val exists: Boolean,
        val readable: Boolean,
        val celsius: Float?
    )

    data class ThermalZoneInfo(
        val zoneName: String,
        val type: String?,
        val celsius: Float?,
        val readable: Boolean
    )

    companion object {
        private const val TAG = "TemperatureReader"

        /** Common sysfs locations exposing live battery temperature on Android kernels. */
        private val BATTERY_SYSFS_PATHS = listOf(
            "/sys/class/power_supply/battery/temp",
            "/sys/class/power_supply/battery/batt_temp",
            "/sys/class/power_supply/bms/temp",
            "/sys/class/power_supply/Battery/temp"
        )
    }
}

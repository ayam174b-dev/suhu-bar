package com.tempmonitor.app.data

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Reads battery temperature and best-effort CPU temperature.
 *
 * Battery temperature is fetched in this order:
 *  1. Direct kernel sysfs (`/sys/class/power_supply/battery/temp`, etc.) — always fresh.
 *  2. `BatteryManager` sticky `ACTION_BATTERY_CHANGED` broadcast — fallback for devices that
 *     restrict sysfs access. Note: the sticky broadcast is only re-published when other battery
 *     fields change, so the temperature value can stay constant for many seconds even when the
 *     real value drifts.
 *
 * CPU temperature is read best-effort from `/sys/class/thermal/thermal_zone*`.
 */
@Singleton
class TemperatureReader @Inject constructor(
    @ApplicationContext private val context: Context
) {
    fun read(): TemperatureData? {
        val battery = readBatteryTempFromSysfs() ?: readBatteryTempFromBroadcast() ?: return null
        val cpu = readCpuTemp()
        return TemperatureData(
            batteryTemp = battery,
            cpuTemp = cpu,
            timestamp = System.currentTimeMillis()
        )
    }

    private fun readBatteryTempFromSysfs(): Float? {
        for (path in BATTERY_SYSFS_PATHS) {
            val file = File(path)
            if (!file.exists() || !file.canRead()) continue
            val raw = runCatching { file.readText().trim().toLongOrNull() }.getOrNull() ?: continue
            // Most kernels report tenths of °C (e.g. 380 = 38.0); some report milli-°C.
            val celsius = when {
                raw > 1_000 -> raw / 1000f
                raw > 200 -> raw / 10f
                else -> raw.toFloat()
            }
            if (celsius.isValid()) {
                Log.d(TAG, "Battery temp via sysfs: $path -> $celsius°C")
                return celsius
            }
        }
        return null
    }

    private fun readBatteryTempFromBroadcast(): Float? {
        val intent: Intent = context.registerReceiver(
            null,
            IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        ) ?: return null
        // EXTRA_TEMPERATURE is reported in tenths of a degree Celsius.
        val raw = intent.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, Int.MIN_VALUE)
        if (raw == Int.MIN_VALUE) return null
        val celsius = raw / 10f
        return celsius.takeIf { it.isValid() }
    }

    private fun readCpuTemp(): Float? {
        val zoneDir = File("/sys/class/thermal")
        val zones = zoneDir.listFiles { f -> f.name.startsWith("thermal_zone") } ?: return null
        var best: Float? = null
        for (zone in zones) {
            val tempFile = File(zone, "temp")
            val typeFile = File(zone, "type")
            if (!tempFile.exists() || !tempFile.canRead()) continue
            val raw = runCatching { tempFile.readText().trim().toLongOrNull() }.getOrNull() ?: continue
            // Many kernels report milli-Celsius; some report tenths of degree.
            val celsius = when {
                raw > 1_000 -> raw / 1000f
                raw > 200 -> raw / 10f
                else -> raw.toFloat()
            }
            if (!celsius.isValid()) continue
            val type = runCatching { typeFile.readText().trim().lowercase() }.getOrNull().orEmpty()
            // Prefer CPU/SoC sensors when we can identify them; otherwise keep the highest.
            val preferred = type.contains("cpu") || type.contains("soc") || type.contains("tsens")
            if (preferred) {
                Log.d(TAG, "Selected thermal zone ${zone.name} (type=$type) -> $celsius°C")
                return celsius
            }
            if (best == null || celsius > best) best = celsius
        }
        return best
    }

    private fun Float.isValid(): Boolean = this in 0f..99.9f

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

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
 * Reads battery temperature via BatteryManager (sticky ACTION_BATTERY_CHANGED broadcast)
 * and best-effort CPU temperature from /sys/class/thermal/thermal_zone*.
 */
@Singleton
class TemperatureReader @Inject constructor(
    @ApplicationContext private val context: Context
) {
    fun read(): TemperatureData? {
        val battery = readBatteryTemp() ?: return null
        val cpu = readCpuTemp()
        return TemperatureData(
            batteryTemp = battery,
            cpuTemp = cpu,
            timestamp = System.currentTimeMillis()
        )
    }

    private fun readBatteryTemp(): Float? {
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
    }
}

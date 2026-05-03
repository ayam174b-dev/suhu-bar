package com.tempmonitor.app.data

/**
 * In-memory snapshot of a temperature reading.
 *
 * @param batteryTemp Battery temperature in degrees Celsius. Always present.
 * @param cpuTemp CPU thermal-zone temperature in degrees Celsius, when readable; null otherwise.
 * @param timestamp Wall-clock time of the reading, in epoch milliseconds.
 */
data class TemperatureData(
    val batteryTemp: Float,
    val cpuTemp: Float?,
    val timestamp: Long
)

enum class TemperatureStatus { NORMAL, WARM, HOT }

fun TemperatureData.status(warmThreshold: Float = 40f, hotThreshold: Float = 45f): TemperatureStatus {
    val ref = batteryTemp
    return when {
        ref >= hotThreshold -> TemperatureStatus.HOT
        ref >= warmThreshold -> TemperatureStatus.WARM
        else -> TemperatureStatus.NORMAL
    }
}

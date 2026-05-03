package com.tempmonitor.app.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "temperature_records")
data class TemperatureRecord(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val batteryTemp: Float,
    val cpuTemp: Float?,
    val timestamp: Long
)

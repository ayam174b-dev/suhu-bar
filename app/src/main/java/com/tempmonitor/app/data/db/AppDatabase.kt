package com.tempmonitor.app.data.db

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [TemperatureRecord::class],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun temperatureDao(): TemperatureDao
}

package com.tempmonitor.app.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [TemperatureRecord::class, GameSession::class],
    version = 2,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun temperatureDao(): TemperatureDao
    abstract fun gameSessionDao(): GameSessionDao

    companion object {
        val MIGRATION_1_2: Migration = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `game_sessions` (
                        `id` INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT,
                        `packageName` TEXT,
                        `label` TEXT NOT NULL,
                        `startTime` INTEGER NOT NULL,
                        `endTime` INTEGER,
                        `startTemp` REAL NOT NULL,
                        `endTemp` REAL,
                        `maxTemp` REAL NOT NULL,
                        `avgTemp` REAL NOT NULL,
                        `sampleCount` INTEGER NOT NULL,
                        `auto` INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
            }
        }
    }
}

package com.tempmonitor.app.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface TemperatureDao {

    @Insert
    suspend fun insert(record: TemperatureRecord): Long

    @Query("SELECT * FROM temperature_records WHERE timestamp BETWEEN :startMs AND :endMs ORDER BY timestamp ASC")
    suspend fun getInRange(startMs: Long, endMs: Long): List<TemperatureRecord>

    @Query("SELECT * FROM temperature_records WHERE timestamp BETWEEN :startMs AND :endMs ORDER BY timestamp ASC")
    fun observeInRange(startMs: Long, endMs: Long): Flow<List<TemperatureRecord>>

    @Query("SELECT * FROM temperature_records ORDER BY timestamp DESC LIMIT 1")
    suspend fun latest(): TemperatureRecord?

    @Query("DELETE FROM temperature_records WHERE timestamp < :cutoffMs")
    suspend fun deleteOlderThan(cutoffMs: Long): Int

    @Query("DELETE FROM temperature_records")
    suspend fun deleteAll()
}

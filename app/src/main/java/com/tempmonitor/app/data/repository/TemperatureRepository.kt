package com.tempmonitor.app.data.repository

import com.tempmonitor.app.data.TemperatureData
import com.tempmonitor.app.data.db.TemperatureDao
import com.tempmonitor.app.data.db.TemperatureRecord
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TemperatureRepository @Inject constructor(
    private val dao: TemperatureDao
) {
    suspend fun save(data: TemperatureData) {
        dao.insert(
            TemperatureRecord(
                batteryTemp = data.batteryTemp,
                cpuTemp = data.cpuTemp,
                timestamp = data.timestamp
            )
        )
    }

    suspend fun getInRange(startMs: Long, endMs: Long): List<TemperatureRecord> =
        dao.getInRange(startMs, endMs)

    fun observeInRange(startMs: Long, endMs: Long): Flow<List<TemperatureRecord>> =
        dao.observeInRange(startMs, endMs)

    suspend fun deleteOlderThan(cutoffMs: Long) = dao.deleteOlderThan(cutoffMs)

    suspend fun latest(): TemperatureRecord? = dao.latest()

    suspend fun clearAll() = dao.deleteAll()
}

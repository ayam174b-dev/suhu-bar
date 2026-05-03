package com.tempmonitor.app.data.repository

import com.tempmonitor.app.data.db.GameSession
import com.tempmonitor.app.data.db.GameSessionDao
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow

@Singleton
class GameSessionRepository @Inject constructor(
    private val dao: GameSessionDao
) {
    suspend fun insert(session: GameSession): Long = dao.insert(session)
    suspend fun update(session: GameSession) = dao.update(session)
    suspend fun get(id: Long): GameSession? = dao.get(id)
    suspend fun getActive(): GameSession? = dao.getActive()
    suspend fun delete(id: Long) = dao.delete(id)
    suspend fun deleteFinishedOlderThan(cutoffMs: Long) = dao.deleteFinishedOlderThan(cutoffMs)
    fun observeRecent(limit: Int = 100): Flow<List<GameSession>> = dao.observeRecent(limit)
}

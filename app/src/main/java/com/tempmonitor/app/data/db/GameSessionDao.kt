package com.tempmonitor.app.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface GameSessionDao {
    @Insert
    suspend fun insert(session: GameSession): Long

    @Update
    suspend fun update(session: GameSession)

    @Query("SELECT * FROM game_sessions WHERE id = :id")
    suspend fun get(id: Long): GameSession?

    @Query("SELECT * FROM game_sessions ORDER BY startTime DESC LIMIT :limit")
    fun observeRecent(limit: Int = 100): Flow<List<GameSession>>

    @Query("SELECT * FROM game_sessions WHERE endTime IS NULL ORDER BY startTime DESC LIMIT 1")
    suspend fun getActive(): GameSession?

    @Query("DELETE FROM game_sessions WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("DELETE FROM game_sessions WHERE endTime IS NOT NULL AND endTime < :cutoffMs")
    suspend fun deleteFinishedOlderThan(cutoffMs: Long): Int
}

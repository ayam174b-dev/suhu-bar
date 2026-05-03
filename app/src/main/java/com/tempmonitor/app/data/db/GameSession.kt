package com.tempmonitor.app.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * One gaming (or manual) usage session. A session that is currently in progress has
 * `endTime == null`; finished sessions carry the final stats.
 */
@Entity(tableName = "game_sessions")
data class GameSession(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    /** Foreground app package, or null when started manually. */
    val packageName: String?,
    /** Human-readable label (app name, "Manual", etc.). */
    val label: String,
    val startTime: Long,
    val endTime: Long?,
    val startTemp: Float,
    val endTemp: Float?,
    val maxTemp: Float,
    val avgTemp: Float,
    val sampleCount: Int,
    /** True when launched automatically by the foreground-app detector. */
    val auto: Boolean
) {
    val durationMs: Long
        get() = (endTime ?: System.currentTimeMillis()) - startTime
    val isActive: Boolean
        get() = endTime == null
}

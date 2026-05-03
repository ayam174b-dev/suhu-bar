package com.tempmonitor.app.session

import com.tempmonitor.app.data.TemperatureData
import com.tempmonitor.app.data.db.GameSession
import com.tempmonitor.app.data.repository.GameSessionRepository
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Tracks an in-progress gaming session.
 *
 * Sessions are created via [start] (manually from Dashboard or automatically when the
 * foreground-app detector sees a game). Each call to [feed] updates the rolling
 * statistics (sample count, sum/avg, max). Calling [end] finalises the session and
 * persists the final row to Room.
 *
 * The active session is also persisted on every call to [feed] so a service restart
 * does not lose progress.
 */
@Singleton
class SessionManager @Inject constructor(
    private val repository: GameSessionRepository
) {
    private val mutex = Mutex()
    private val _active = MutableStateFlow<GameSession?>(null)
    val active: StateFlow<GameSession?> = _active.asStateFlow()

    suspend fun restoreFromDb() {
        mutex.withLock {
            if (_active.value == null) {
                _active.value = repository.getActive()
            }
        }
    }

    suspend fun start(
        seedTemp: Float,
        packageName: String?,
        label: String,
        auto: Boolean
    ): GameSession {
        return mutex.withLock {
            // If a session is already active, return it (idempotent).
            _active.value?.let { return@withLock it }
            val now = System.currentTimeMillis()
            val draft = GameSession(
                packageName = packageName,
                label = label,
                startTime = now,
                endTime = null,
                startTemp = seedTemp,
                endTemp = null,
                maxTemp = seedTemp,
                avgTemp = seedTemp,
                sampleCount = 1,
                auto = auto
            )
            val id = repository.insert(draft)
            val saved = draft.copy(id = id)
            _active.value = saved
            saved
        }
    }

    /** Returns the updated session, or null if no session is active. */
    suspend fun feed(data: TemperatureData): GameSession? {
        return mutex.withLock {
            val current = _active.value ?: return@withLock null
            val newCount = current.sampleCount + 1
            val newAvg = ((current.avgTemp.toDouble() * current.sampleCount) + data.batteryTemp) / newCount
            val updated = current.copy(
                maxTemp = maxOf(current.maxTemp, data.batteryTemp),
                avgTemp = newAvg.toFloat(),
                sampleCount = newCount,
                endTemp = data.batteryTemp
            )
            repository.update(updated)
            _active.value = updated
            updated
        }
    }

    suspend fun end(finalTemp: Float?): GameSession? {
        return mutex.withLock {
            val current = _active.value ?: return@withLock null
            val finished = current.copy(
                endTime = System.currentTimeMillis(),
                endTemp = finalTemp ?: current.endTemp ?: current.startTemp
            )
            repository.update(finished)
            _active.value = null
            finished
        }
    }

    /** Returns true when an auto-launched session should end because the foreground app changed. */
    fun shouldAutoEnd(currentForegroundPackage: String?): Boolean {
        val active = _active.value ?: return false
        if (!active.auto) return false
        return currentForegroundPackage != active.packageName
    }
}

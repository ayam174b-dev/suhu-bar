package com.tempmonitor.app.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.monitorDataStore: DataStore<Preferences> by preferencesDataStore(name = "monitor_prefs")

data class MonitorSettings(
    val intervalSeconds: Int = 5,
    val warmThreshold: Float = 40f,
    val hotThreshold: Float = 45f,
    val notificationsEnabled: Boolean = true,
    val autoStartOnBoot: Boolean = false,
    /** When true and Usage Access has been granted, the service auto-starts a session every
     *  time the foreground app is identified as a game and ends it when the user leaves. */
    val autoSessionGaming: Boolean = false
)

@Singleton
class MonitorPreferences @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private object Keys {
        val INTERVAL = intPreferencesKey("interval_seconds")
        val WARM = floatPreferencesKey("warm_threshold")
        val HOT = floatPreferencesKey("hot_threshold")
        val NOTIF = booleanPreferencesKey("notifications_enabled")
        val BOOT = booleanPreferencesKey("auto_start_on_boot")
        val AUTO_SESSION = booleanPreferencesKey("auto_session_gaming")
    }

    val settings: Flow<MonitorSettings> = context.monitorDataStore.data.map { prefs ->
        MonitorSettings(
            intervalSeconds = prefs[Keys.INTERVAL] ?: 5,
            warmThreshold = prefs[Keys.WARM] ?: 40f,
            hotThreshold = prefs[Keys.HOT] ?: 45f,
            notificationsEnabled = prefs[Keys.NOTIF] ?: true,
            autoStartOnBoot = prefs[Keys.BOOT] ?: false,
            autoSessionGaming = prefs[Keys.AUTO_SESSION] ?: false
        )
    }

    suspend fun setInterval(seconds: Int) {
        context.monitorDataStore.edit { it[Keys.INTERVAL] = seconds.coerceIn(3, 60) }
    }

    suspend fun setThresholds(warm: Float, hot: Float) {
        context.monitorDataStore.edit {
            it[Keys.WARM] = warm
            it[Keys.HOT] = hot
        }
    }

    suspend fun setNotificationsEnabled(enabled: Boolean) {
        context.monitorDataStore.edit { it[Keys.NOTIF] = enabled }
    }

    suspend fun setAutoStartOnBoot(enabled: Boolean) {
        context.monitorDataStore.edit { it[Keys.BOOT] = enabled }
    }

    suspend fun setAutoSessionGaming(enabled: Boolean) {
        context.monitorDataStore.edit { it[Keys.AUTO_SESSION] = enabled }
    }
}

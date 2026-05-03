package com.tempmonitor.app.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationCompat
import com.tempmonitor.app.MainActivity
import com.tempmonitor.app.R
import com.tempmonitor.app.data.TemperatureData
import com.tempmonitor.app.data.TemperatureReader
import com.tempmonitor.app.data.TemperatureStatus
import com.tempmonitor.app.data.preferences.MonitorPreferences
import com.tempmonitor.app.data.preferences.MonitorSettings
import com.tempmonitor.app.data.repository.TemperatureRepository
import com.tempmonitor.app.data.status
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.math.roundToInt

@AndroidEntryPoint
class TemperatureMonitorService : Service() {

    @Inject lateinit var reader: TemperatureReader
    @Inject lateinit var repository: TemperatureRepository
    @Inject lateinit var preferences: MonitorPreferences

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var loopJob: Job? = null
    private val settingsFlow = MutableStateFlow(MonitorSettings())
    private var wakeLock: PowerManager.WakeLock? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        ensureChannel(this)
        scope.launch {
            preferences.settings.collect { settingsFlow.value = it }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopMonitoring()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
                return START_NOT_STICKY
            }
        }

        startInForeground(buildNotification(latest = null, settings = settingsFlow.value))
        if (loopJob == null || loopJob?.isActive != true) startMonitoring()
        return START_STICKY
    }

    private fun startInForeground(notification: Notification) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun startMonitoring() {
        TemperatureState.setRunning(true)
        acquireWakeLock()
        loopJob = scope.launch {
            var lastSavedAt = 0L
            while (true) {
                val settings = settingsFlow.value
                val data = reader.read()
                if (data != null) {
                    TemperatureState.publish(data)
                    updateNotification(data, settings)
                    val now = data.timestamp
                    if (now - lastSavedAt >= SAVE_INTERVAL_MS) {
                        runCatching { repository.save(data) }
                            .onFailure { Log.w(TAG, "Failed to save record", it) }
                        lastSavedAt = now
                    }
                } else {
                    Log.w(TAG, "Skipping invalid temperature reading")
                }
                delay(settings.intervalSeconds.coerceAtLeast(3) * 1000L)
            }
        }
    }

    private fun stopMonitoring() {
        TemperatureState.setRunning(false)
        loopJob?.cancel()
        loopJob = null
        releaseWakeLock()
    }

    private fun updateNotification(data: TemperatureData, settings: MonitorSettings) {
        val notification = buildNotification(data, settings)
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(NOTIFICATION_ID, notification)
    }

    private fun buildNotification(latest: TemperatureData?, settings: MonitorSettings): Notification {
        val contentIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val stopIntent = PendingIntent.getService(
            this,
            1,
            Intent(this, TemperatureMonitorService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val title: String
        val body: String
        if (latest == null) {
            title = getString(R.string.app_name)
            body = "Memulai monitoring suhu…"
        } else {
            val statusLabel = when (latest.status(settings.warmThreshold, settings.hotThreshold)) {
                TemperatureStatus.NORMAL -> "Normal"
                TemperatureStatus.WARM -> "Warm"
                TemperatureStatus.HOT -> "Hot"
            }
            val battery = "${latest.batteryTemp.roundToInt()}°C"
            title = if (latest.cpuTemp != null) {
                "🌡️ $battery | CPU ${latest.cpuTemp.roundToInt()}°C | $statusLabel"
            } else {
                "🌡️ $battery | $statusLabel"
            }
            body = "Battery ${"%.1f".format(latest.batteryTemp)}°C" +
                (latest.cpuTemp?.let { " · CPU ${"%.1f".format(it)}°C" } ?: "")
        }

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(body)
            .setSmallIcon(R.drawable.ic_thermometer)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setShowWhen(false)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setContentIntent(contentIntent)
            .addAction(
                NotificationCompat.Action.Builder(
                    R.drawable.ic_thermometer,
                    "Stop",
                    stopIntent
                ).build()
            )
            .build()
    }

    private fun acquireWakeLock() {
        if (wakeLock?.isHeld == true) return
        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "SuhuBar:MonitorWakeLock").apply {
            setReferenceCounted(false)
            acquire()
        }
    }

    private fun releaseWakeLock() {
        wakeLock?.let { if (it.isHeld) it.release() }
        wakeLock = null
    }

    override fun onDestroy() {
        stopMonitoring()
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        private const val TAG = "TempMonitorService"
        private const val CHANNEL_ID = "temperature_monitor_channel"
        private const val NOTIFICATION_ID = 4242
        private const val SAVE_INTERVAL_MS = 30_000L

        const val ACTION_START = "com.tempmonitor.app.action.START"
        const val ACTION_STOP = "com.tempmonitor.app.action.STOP"

        fun start(context: Context) {
            val intent = Intent(context, TemperatureMonitorService::class.java)
                .setAction(ACTION_START)
            context.startForegroundService(intent)
        }

        fun stop(context: Context) {
            val intent = Intent(context, TemperatureMonitorService::class.java)
                .setAction(ACTION_STOP)
            context.startService(intent)
        }

        fun ensureChannel(context: Context) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
            val nm = context.getSystemService(NotificationManager::class.java) ?: return
            if (nm.getNotificationChannel(CHANNEL_ID) != null) return
            val channel = NotificationChannel(
                CHANNEL_ID,
                context.getString(R.string.notif_channel_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = context.getString(R.string.notif_channel_desc)
                setShowBadge(false)
                enableLights(false)
                enableVibration(false)
            }
            nm.createNotificationChannel(channel)
        }
    }
}

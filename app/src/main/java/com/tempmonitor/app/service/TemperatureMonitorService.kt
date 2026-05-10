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
import com.tempmonitor.app.data.BatteryStats
import com.tempmonitor.app.data.BatteryStatsAggregator
import com.tempmonitor.app.data.BatteryStatsReader
import com.tempmonitor.app.data.CurrentDirection
import com.tempmonitor.app.data.TemperatureData
import com.tempmonitor.app.data.TemperatureReader
import com.tempmonitor.app.data.TemperatureStatus
import com.tempmonitor.app.data.preferences.MonitorPreferences
import com.tempmonitor.app.data.preferences.MonitorSettings
import com.tempmonitor.app.data.repository.TemperatureRepository
import com.tempmonitor.app.data.status
import com.tempmonitor.app.session.ForegroundAppDetector
import com.tempmonitor.app.session.SessionManager
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
    @Inject lateinit var batteryStatsReader: BatteryStatsReader
    @Inject lateinit var batteryStatsAggregator: BatteryStatsAggregator
    @Inject lateinit var repository: TemperatureRepository
    @Inject lateinit var preferences: MonitorPreferences
    @Inject lateinit var sessionManager: SessionManager
    @Inject lateinit var foregroundAppDetector: ForegroundAppDetector

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var loopJob: Job? = null
    private val settingsFlow = MutableStateFlow(MonitorSettings())
    private var wakeLock: PowerManager.WakeLock? = null
    /** Latest battery snapshot, included in the persistent notification. */
    @Volatile private var lastBatteryStats: BatteryStats? = null
    /** True after we have already alerted the user about a full battery for the current charge cycle. */
    @Volatile private var fullAlertSentForThisCharge: Boolean = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        ensureChannel(this)
        ensureBatteryFullChannel(this)
        scope.launch {
            preferences.settings.collect { settingsFlow.value = it }
        }
        scope.launch {
            runCatching { sessionManager.restoreFromDb() }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                scope.launch {
                    runCatching { sessionManager.end(finalTemp = null) }
                }
                stopMonitoring()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_START_MANUAL_SESSION -> {
                scope.launch {
                    val seed = reader.read()?.batteryTemp ?: 0f
                    sessionManager.start(
                        seedTemp = seed,
                        packageName = null,
                        label = "Manual",
                        auto = false
                    )
                }
            }
            ACTION_END_MANUAL_SESSION -> {
                scope.launch {
                    val finalTemp = reader.read()?.batteryTemp
                    sessionManager.end(finalTemp)
                }
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
                val battery = runCatching { batteryStatsReader.read() }
                    .onFailure { Log.w(TAG, "Battery stats read failed", it) }
                    .getOrNull()
                if (battery != null) {
                    lastBatteryStats = battery
                    TemperatureState.publishBattery(battery)
                    runCatching { batteryStatsAggregator.feed(battery) }
                        .onFailure { Log.w(TAG, "Aggregator feed failed", it) }
                    runCatching { maybeAlertBatteryFull(battery) }
                        .onFailure { Log.w(TAG, "Battery-full alert failed", it) }
                }
                val data = reader.read()
                if (data != null) {
                    TemperatureState.publish(data)
                    updateNotification(data, settings)
                    runCatching { handleSession(data, settings) }
                        .onFailure { Log.w(TAG, "Session bookkeeping failed", it) }
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

    /**
     * Posts a one-shot, high-priority alert when the battery first reaches the "full"
     * state while plugged in, so the user can unplug before the cell stays at 100 % under
     * load. Resets when the user unplugs (or the level drops below 99 %).
     */
    private fun maybeAlertBatteryFull(battery: BatteryStats) {
        if (!battery.plugged || battery.levelPercent < 99f) {
            // Reset latch on unplug or drop below 99 %.
            fullAlertSentForThisCharge = false
            return
        }
        if (!battery.isFull) return
        if (fullAlertSentForThisCharge) return
        val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        val contentIntent = PendingIntent.getActivity(
            this,
            2,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notif = NotificationCompat.Builder(this, BATTERY_FULL_CHANNEL_ID)
            .setContentTitle("Baterai sudah penuh \u2014 cabut charger")
            .setContentText(
                "Level ${battery.levelPercent.roundToInt()}% \u00b7 " +
                    "${battery.pluggedSource?.name ?: ""}. Lepas charger untuk mencegah baterai membengkak."
            )
            .setSmallIcon(R.drawable.ic_thermometer)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setAutoCancel(true)
            .setContentIntent(contentIntent)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .build()
        nm.notify(BATTERY_FULL_NOTIFICATION_ID, notif)
        fullAlertSentForThisCharge = true
    }

    private suspend fun handleSession(data: TemperatureData, settings: MonitorSettings) {
        // Always feed the active session so live stats keep updating regardless of source.
        sessionManager.feed(data)

        if (!settings.autoSessionGaming) return
        if (!foregroundAppDetector.hasUsageStatsPermission()) return

        val foreground = foregroundAppDetector.currentForegroundPackage()
        val isGame = foreground != null && foregroundAppDetector.isGame(foreground)
        val active = sessionManager.active.value

        when {
            active == null && isGame -> {
                sessionManager.start(
                    seedTemp = data.batteryTemp,
                    packageName = foreground,
                    label = foregroundAppDetector.appLabel(foreground!!),
                    auto = true
                )
            }
            active != null && active.auto && sessionManager.shouldAutoEnd(foreground) -> {
                sessionManager.end(data.batteryTemp)
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

        val battery = lastBatteryStats
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
            val batteryTempStr = "${latest.batteryTemp.roundToInt()}°C"
            title = if (latest.cpuTemp != null) {
                "🌡️ $batteryTempStr | CPU ${latest.cpuTemp.roundToInt()}°C | $statusLabel"
            } else {
                "🌡️ $batteryTempStr | $statusLabel"
            }
            val tempLine = "Battery ${"%.1f".format(latest.batteryTemp)}°C" +
                (latest.cpuTemp?.let { " · CPU ${"%.1f".format(it)}°C" } ?: "")
            body = if (battery != null) tempLine + "\n" + formatBatteryNotificationLine(battery) else tempLine
        }

        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
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

        // Status-bar small icon: render the current battery temperature as digits so the user
        // can read the value without opening the notification shade. Falls back to the static
        // thermometer drawable until the first reading arrives.
        if (latest != null) {
            builder.setSmallIcon(NotificationIconRenderer.render(this, latest.batteryTemp))
        } else {
            builder.setSmallIcon(R.drawable.ic_thermometer)
        }

        return builder.build()
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

    /**
     * Builds the second body line for the foreground notification: shows the live current
     * with sign and an ETA when charging, or the discharge rate when on battery.
     */
    private fun formatBatteryNotificationLine(battery: BatteryStats): String {
        val parts = mutableListOf<String>()
        parts += "Lvl ${battery.levelPercent.roundToInt()}%"
        battery.currentNowMa?.let { mA ->
            val sign = if (mA >= 0f) "+" else ""
            parts += "${sign}${mA.roundToInt()} mA"
        }
        when (battery.direction) {
            CurrentDirection.CHARGING -> battery.chargeTimeRemainingMs?.let {
                parts += "ETA ${formatDurationShort(it)}"
            }
            CurrentDirection.FULL -> parts += "penuh — cabut"
            CurrentDirection.IDLE_PLUGGED -> parts += "idle"
            else -> {}
        }
        return parts.joinToString(" · ")
    }

    private fun formatDurationShort(ms: Long): String {
        val totalMin = (ms / 60_000L).coerceAtLeast(0)
        val h = totalMin / 60
        val m = totalMin % 60
        return if (h > 0) "${h}j ${m}m" else "${m}m"
    }

    companion object {
        private const val TAG = "TempMonitorService"
        private const val CHANNEL_ID = "temperature_monitor_channel"
        private const val BATTERY_FULL_CHANNEL_ID = "battery_full_channel"
        private const val NOTIFICATION_ID = 4242
        private const val BATTERY_FULL_NOTIFICATION_ID = 4243
        private const val SAVE_INTERVAL_MS = 30_000L

        const val ACTION_START = "com.tempmonitor.app.action.START"
        const val ACTION_STOP = "com.tempmonitor.app.action.STOP"
        const val ACTION_START_MANUAL_SESSION = "com.tempmonitor.app.action.START_SESSION"
        const val ACTION_END_MANUAL_SESSION = "com.tempmonitor.app.action.END_SESSION"

        fun startManualSession(context: Context) {
            val intent = Intent(context, TemperatureMonitorService::class.java)
                .setAction(ACTION_START_MANUAL_SESSION)
            context.startForegroundService(intent)
        }

        fun endManualSession(context: Context) {
            val intent = Intent(context, TemperatureMonitorService::class.java)
                .setAction(ACTION_END_MANUAL_SESSION)
            context.startService(intent)
        }

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

        fun ensureBatteryFullChannel(context: Context) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
            val nm = context.getSystemService(NotificationManager::class.java) ?: return
            if (nm.getNotificationChannel(BATTERY_FULL_CHANNEL_ID) != null) return
            val channel = NotificationChannel(
                BATTERY_FULL_CHANNEL_ID,
                context.getString(R.string.notif_battery_full_channel_name),
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = context.getString(R.string.notif_battery_full_channel_desc)
                setShowBadge(true)
                enableLights(true)
                enableVibration(true)
            }
            nm.createNotificationChannel(channel)
        }
    }
}

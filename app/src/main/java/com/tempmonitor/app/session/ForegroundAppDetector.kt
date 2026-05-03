package com.tempmonitor.app.session

import android.app.AppOpsManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Build
import android.os.Process
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Wrapper around [UsageStatsManager] used to detect whether the user has launched a game.
 *
 * Requires the special "Usage access" permission, which the user must grant from
 * Settings → Apps → Special app access → Usage access. Without it, [hasUsageStatsPermission]
 * returns false and detection silently no-ops.
 */
@Singleton
class ForegroundAppDetector @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val usageStatsManager: UsageStatsManager? =
        context.getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager
    private val packageManager: PackageManager = context.packageManager
    private val appOpsManager: AppOpsManager =
        context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager

    fun hasUsageStatsPermission(): Boolean {
        val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            appOpsManager.unsafeCheckOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                Process.myUid(),
                context.packageName
            )
        } else {
            @Suppress("DEPRECATION")
            appOpsManager.checkOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                Process.myUid(),
                context.packageName
            )
        }
        return mode == AppOpsManager.MODE_ALLOWED
    }

    /**
     * Returns the package name of the most recent foreground app inside the last [windowMs]
     * milliseconds, or null if usage stats are unavailable.
     */
    @Suppress("DEPRECATION")
    fun currentForegroundPackage(windowMs: Long = 60_000L): String? {
        val usm = usageStatsManager ?: return null
        if (!hasUsageStatsPermission()) return null
        val now = System.currentTimeMillis()
        val events = runCatching { usm.queryEvents(now - windowMs, now) }.getOrNull() ?: return null
        var lastPackage: String? = null
        val ev = UsageEvents.Event()
        while (events.hasNextEvent()) {
            events.getNextEvent(ev)
            val type = ev.eventType
            val isMoveToForeground = type == UsageEvents.Event.MOVE_TO_FOREGROUND ||
                (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && type == UsageEvents.Event.ACTIVITY_RESUMED)
            if (isMoveToForeground) {
                lastPackage = ev.packageName
            }
        }
        return lastPackage
    }

    fun isGame(packageName: String): Boolean {
        val info = runCatching {
            packageManager.getApplicationInfo(packageName, 0)
        }.getOrNull() ?: return false
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if (info.category == ApplicationInfo.CATEGORY_GAME) return true
        }
        @Suppress("DEPRECATION")
        return (info.flags and ApplicationInfo.FLAG_IS_GAME) != 0
    }

    fun appLabel(packageName: String): String {
        val info = runCatching {
            packageManager.getApplicationInfo(packageName, 0)
        }.getOrNull() ?: return packageName
        return packageManager.getApplicationLabel(info).toString()
    }
}

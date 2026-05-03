package com.tempmonitor.app.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.tempmonitor.app.data.preferences.MonitorPreferences
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class BootReceiver : BroadcastReceiver() {

    @Inject lateinit var preferences: MonitorPreferences

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        if (action != Intent.ACTION_BOOT_COMPLETED && action != Intent.ACTION_LOCKED_BOOT_COMPLETED) {
            return
        }
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.Default).launch {
            try {
                val settings = preferences.settings.first()
                if (settings.autoStartOnBoot) {
                    TemperatureMonitorService.start(context)
                }
            } finally {
                pendingResult.finish()
            }
        }
    }
}

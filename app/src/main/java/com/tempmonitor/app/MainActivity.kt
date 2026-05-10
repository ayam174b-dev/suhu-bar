package com.tempmonitor.app

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.tempmonitor.app.data.preferences.MonitorPreferences
import com.tempmonitor.app.service.TemperatureMonitorService
import com.tempmonitor.app.service.TemperatureState
import com.tempmonitor.app.ui.navigation.AppNavigation
import com.tempmonitor.app.ui.theme.SuhuBarTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var preferences: MonitorPreferences

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        TemperatureMonitorService.ensureChannel(applicationContext)
        TemperatureMonitorService.ensureBatteryFullChannel(applicationContext)
        setContent {
            SuhuBarTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    AppNavigation()
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        // Auto-start the monitoring service whenever the app is opened *and* the user has
        // enabled "Selalu monitor". Skips if monitoring is already running or POST_NOTIFICATIONS
        // hasn't been granted yet on Android 13+ — in that case the user needs to start it
        // manually from the Dashboard, which surfaces the permission request.
        lifecycleScope.launch {
            val settings = preferences.settings.first()
            if (!settings.alwaysMonitor) return@launch
            if (TemperatureState.isRunning.value) return@launch
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                val granted = ContextCompat.checkSelfPermission(
                    this@MainActivity,
                    Manifest.permission.POST_NOTIFICATIONS
                ) == PackageManager.PERMISSION_GRANTED
                if (!granted) return@launch
            }
            TemperatureMonitorService.start(this@MainActivity)
        }
    }
}

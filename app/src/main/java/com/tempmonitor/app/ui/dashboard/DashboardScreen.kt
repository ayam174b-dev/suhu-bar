package com.tempmonitor.app.ui.dashboard

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tempmonitor.app.data.TemperatureData
import com.tempmonitor.app.data.TemperatureStatus
import com.tempmonitor.app.data.status
import com.tempmonitor.app.service.TemperatureMonitorService
import com.tempmonitor.app.ui.theme.HotRed
import com.tempmonitor.app.ui.theme.HotRedContainer
import com.tempmonitor.app.ui.theme.NormalGreen
import com.tempmonitor.app.ui.theme.NormalGreenContainer
import com.tempmonitor.app.ui.theme.WarmAmber
import com.tempmonitor.app.ui.theme.WarmAmberContainer
import kotlin.math.roundToInt

@Composable
fun DashboardScreen(viewModel: DashboardViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            TemperatureMonitorService.start(context)
        }
    }

    fun onStartClicked() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                return
            }
        }
        TemperatureMonitorService.start(context)
    }

    fun onStopClicked() {
        TemperatureMonitorService.stop(context)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "Monitor Suhu",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.SemiBold
        )

        TemperatureCard(
            data = state.latest,
            warmThreshold = state.settings.warmThreshold,
            hotThreshold = state.settings.hotThreshold
        )

        if (state.latest?.cpuTemp != null) {
            CpuCard(cpuTemp = state.latest!!.cpuTemp!!)
        } else if (state.isRunning) {
            CpuUnavailableCard()
        }

        StatusBanner(
            running = state.isRunning,
            data = state.latest,
            warmThreshold = state.settings.warmThreshold,
            hotThreshold = state.settings.hotThreshold
        )

        Spacer(modifier = Modifier.height(8.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Button(
                onClick = ::onStartClicked,
                enabled = !state.isRunning,
                modifier = Modifier.weight(1f)
            ) {
                Text("Start Monitoring")
            }
            OutlinedButton(
                onClick = ::onStopClicked,
                enabled = state.isRunning,
                modifier = Modifier.weight(1f)
            ) {
                Text("Stop Monitoring")
            }
        }
    }
}

@Composable
private fun TemperatureCard(
    data: TemperatureData?,
    warmThreshold: Float,
    hotThreshold: Float
) {
    val status = data?.status(warmThreshold, hotThreshold)
    val (containerColor, contentColor) = colorsFor(status)

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = containerColor)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp)
        ) {
            Text(
                text = "Suhu Baterai",
                style = MaterialTheme.typography.labelLarge,
                color = contentColor
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = if (data != null) "${"%.1f".format(data.batteryTemp)}°C" else "—",
                style = MaterialTheme.typography.displayLarge,
                color = contentColor,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun CpuCard(cpuTemp: Float) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Text("CPU", style = MaterialTheme.typography.labelLarge)
            Spacer(Modifier.height(4.dp))
            Text(
                text = "${cpuTemp.roundToInt()}°C",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

@Composable
private fun CpuUnavailableCard() {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Text(
                text = "CPU temperature tidak tersedia di perangkat ini",
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}

@Composable
private fun StatusBanner(
    running: Boolean,
    data: TemperatureData?,
    warmThreshold: Float,
    hotThreshold: Float
) {
    val status = data?.status(warmThreshold, hotThreshold) ?: TemperatureStatus.NORMAL
    val (color, label) = when (status) {
        TemperatureStatus.NORMAL -> NormalGreen to "Normal"
        TemperatureStatus.WARM -> WarmAmber to "Warm"
        TemperatureStatus.HOT -> HotRed to "Hot"
    }

    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(12.dp)
                .clip(CircleShape)
                .background(color)
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = if (running) "Monitoring aktif · Status: $label" else "Monitoring berhenti",
            style = MaterialTheme.typography.bodyMedium
        )
    }
}

private fun colorsFor(status: TemperatureStatus?): Pair<Color, Color> = when (status) {
    null -> Color(0xFFE3F2FD) to Color(0xFF0D47A1)
    TemperatureStatus.NORMAL -> NormalGreenContainer to NormalGreen
    TemperatureStatus.WARM -> WarmAmberContainer to WarmAmber
    TemperatureStatus.HOT -> HotRedContainer to HotRed
}

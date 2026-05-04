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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.BatteryFull
import androidx.compose.material.icons.filled.BatteryStd
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PowerOff
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material.icons.filled.Stop
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
import com.tempmonitor.app.data.BatteryStats
import com.tempmonitor.app.data.CurrentDirection
import com.tempmonitor.app.data.PluggedSource
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
            .verticalScroll(rememberScrollState())
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

        BatteryStatsCard(state.battery, state.isRunning)

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

        SessionInlineCard(
            isRunning = state.isRunning,
            activeSession = state.activeSession,
            onStart = { TemperatureMonitorService.startManualSession(context) },
            onStop = { TemperatureMonitorService.endManualSession(context) }
        )
    }
}

@Composable
private fun SessionInlineCard(
    isRunning: Boolean,
    activeSession: com.tempmonitor.app.data.db.GameSession?,
    onStart: () -> Unit,
    onStop: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.SportsEsports, contentDescription = null)
                Text(
                    "Sesi gaming",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(start = 8.dp)
                )
            }
            if (activeSession == null) {
                Text(
                    "Mulai sesi sebelum bermain untuk melacak suhu min/max/avg di tab Sesi.",
                    style = MaterialTheme.typography.bodySmall
                )
                AssistChip(
                    onClick = onStart,
                    enabled = isRunning,
                    label = { Text("Mulai Sesi") },
                    leadingIcon = { Icon(Icons.Filled.PlayArrow, null) },
                    colors = AssistChipDefaults.assistChipColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        labelColor = MaterialTheme.colorScheme.onPrimary,
                        leadingIconContentColor = MaterialTheme.colorScheme.onPrimary
                    )
                )
                if (!isRunning) {
                    Text(
                        "Aktifkan Start Monitoring lebih dulu.",
                        style = MaterialTheme.typography.labelSmall
                    )
                }
            } else {
                Text(
                    activeSession.label + (if (activeSession.auto) " (otomatis)" else ""),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    "Max %.1f°C · Avg %.1f°C · Sampel ${activeSession.sampleCount}".format(
                        activeSession.maxTemp,
                        activeSession.avgTemp
                    ),
                    style = MaterialTheme.typography.bodySmall
                )
                AssistChip(
                    onClick = onStop,
                    label = { Text("Akhiri Sesi") },
                    leadingIcon = { Icon(Icons.Filled.Stop, null) },
                    colors = AssistChipDefaults.assistChipColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        labelColor = MaterialTheme.colorScheme.onError,
                        leadingIconContentColor = MaterialTheme.colorScheme.onError
                    )
                )
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
private fun BatteryStatsCard(stats: BatteryStats?, running: Boolean) {
    val tone = when (stats?.direction) {
        CurrentDirection.CHARGING -> NormalGreenContainer
        CurrentDirection.FULL -> WarmAmberContainer
        CurrentDirection.DISCHARGING -> MaterialTheme.colorScheme.surfaceVariant
        CurrentDirection.IDLE_PLUGGED, CurrentDirection.IDLE -> MaterialTheme.colorScheme.surfaceVariant
        null -> MaterialTheme.colorScheme.surfaceVariant
    }
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = tone)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                val (icon, header) = when (stats?.direction) {
                    CurrentDirection.CHARGING -> Icons.Filled.BatteryChargingFull to "Mengisi"
                    CurrentDirection.FULL -> Icons.Filled.BatteryFull to "Penuh — cabut charger"
                    CurrentDirection.DISCHARGING -> Icons.Filled.ArrowDownward to "Memakai daya"
                    CurrentDirection.IDLE_PLUGGED -> Icons.Filled.BatteryStd to "Tercolok, idle"
                    CurrentDirection.IDLE -> Icons.Filled.BatteryStd to "Idle"
                    null -> Icons.Filled.PowerOff to "Arus & Baterai"
                }
                Icon(icon, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(
                    header,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
            }

            if (stats == null) {
                Text(
                    if (running) "Membaca arus baterai…"
                    else "Mulai monitoring untuk melihat arus, voltase, dan ETA full.",
                    style = MaterialTheme.typography.bodySmall
                )
                return@Column
            }

            // Big current readout — the value that answers "berapa arus masuk/keluar?"
            val currentLabel = stats.currentNowMa?.let { mA ->
                val sign = if (mA >= 0f) "+" else ""
                "$sign${mA.roundToInt()} mA"
            } ?: "— mA"
            Row(verticalAlignment = Alignment.Bottom) {
                val arrow = when {
                    stats.currentNowMa == null -> null
                    stats.currentNowMa > 5f -> Icons.Filled.ArrowUpward
                    stats.currentNowMa < -5f -> Icons.Filled.ArrowDownward
                    else -> null
                }
                if (arrow != null) {
                    Icon(arrow, contentDescription = null)
                    Spacer(Modifier.width(4.dp))
                }
                Text(
                    currentLabel,
                    style = MaterialTheme.typography.headlineLarge,
                    fontWeight = FontWeight.Bold
                )
            }
            stats.currentAvgMa?.let { avg ->
                Text(
                    "Rata-rata ${if (avg >= 0f) "+" else ""}${avg.roundToInt()} mA",
                    style = MaterialTheme.typography.bodySmall
                )
            }

            // Level + voltage line
            val levelStr = "${stats.levelPercent.roundToInt()}%"
            val voltageStr = stats.voltageMv?.let { "${"%.2f".format(it / 1000f)} V" }
            val sourceStr = stats.pluggedSource?.let { it.name.lowercase().replaceFirstChar { c -> c.uppercase() } }
            val parts = listOfNotNull(
                "Level $levelStr",
                voltageStr,
                sourceStr?.let { "Colokan $it" }
            )
            if (parts.isNotEmpty()) {
                Text(parts.joinToString(" · "), style = MaterialTheme.typography.bodyMedium)
            }

            // ETA / status hint
            when (stats.direction) {
                CurrentDirection.CHARGING -> {
                    val eta = stats.chargeTimeRemainingMs?.let { formatEta(it) }
                    Text(
                        "ETA penuh: ${eta ?: "menghitung…"}",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                CurrentDirection.FULL -> Text(
                    "Sudah penuh. Lepas charger untuk mencegah baterai membengkak.",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold
                )
                CurrentDirection.IDLE_PLUGGED -> Text(
                    "Charger tersambung tapi tidak ada arus masuk.",
                    style = MaterialTheme.typography.bodySmall
                )
                CurrentDirection.DISCHARGING, CurrentDirection.IDLE -> {}
            }
        }
    }
}

private fun formatEta(ms: Long): String {
    val totalMin = (ms / 60_000L).coerceAtLeast(0)
    val h = totalMin / 60
    val m = totalMin % 60
    return if (h > 0) "${h}j ${m}m" else "${m}m"
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

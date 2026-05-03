package com.tempmonitor.app.ui.session

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tempmonitor.app.data.db.GameSession
import com.tempmonitor.app.service.TemperatureMonitorService
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.delay

@Composable
fun SessionScreen(viewModel: SessionViewModel = hiltViewModel()) {
    val active by viewModel.active.collectAsStateWithLifecycle()
    val recent by viewModel.recent.collectAsStateWithLifecycle()
    val context = LocalContext.current

    // Tick every second so durations on the active card update.
    var nowMs by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(active?.id) {
        while (active != null) {
            nowMs = System.currentTimeMillis()
            delay(1_000L)
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text(
                "Sesi gaming",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                "Lacak suhu HP per sesi pemakaian. Mulai manual atau aktifkan auto-start gaming di Settings.",
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = 4.dp)
            )
        }

        item {
            ActiveSessionCard(
                session = active,
                nowMs = nowMs,
                onStart = { TemperatureMonitorService.startManualSession(context) },
                onStop = { TemperatureMonitorService.endManualSession(context) }
            )
        }

        item {
            Text(
                "Histori sesi",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(top = 4.dp)
            )
        }

        if (recent.isEmpty()) {
            item {
                Text(
                    "Belum ada sesi tersimpan.",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(vertical = 12.dp)
                )
            }
        } else {
            items(recent, key = { it.id }) { session ->
                SessionRow(
                    session = session,
                    isActive = session.id == active?.id,
                    onDelete = { viewModel.deleteSession(session.id) }
                )
            }
        }

        item { Spacer(Modifier.height(24.dp)) }
    }
}

@Composable
private fun ActiveSessionCard(
    session: GameSession?,
    nowMs: Long,
    onStart: () -> Unit,
    onStop: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (session == null) {
                Text(
                    "Tidak ada sesi aktif",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    "Tekan Mulai Sesi sebelum mulai bermain. Pastikan service monitoring sedang berjalan.",
                    style = MaterialTheme.typography.bodySmall
                )
                Spacer(Modifier.height(4.dp))
                Button(
                    onClick = onStart,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    )
                ) {
                    Icon(Icons.Filled.PlayArrow, contentDescription = null)
                    Spacer(Modifier.height(0.dp))
                    Text("Mulai Sesi", modifier = Modifier.padding(start = 6.dp))
                }
            } else {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            session.label,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            if (session.auto) "Otomatis (foreground)" else "Manual",
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                    Button(
                        onClick = onStop,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.error,
                            contentColor = MaterialTheme.colorScheme.onError
                        )
                    ) {
                        Icon(Icons.Filled.Stop, contentDescription = null)
                        Text("Stop", modifier = Modifier.padding(start = 6.dp))
                    }
                }
                HorizontalDivider()
                StatGrid(
                    duration = formatDuration(nowMs - session.startTime),
                    start = "%.1f°C".format(session.startTemp),
                    current = session.endTemp?.let { "%.1f°C".format(it) } ?: "—",
                    max = "%.1f°C".format(session.maxTemp),
                    avg = "%.1f°C".format(session.avgTemp),
                    samples = session.sampleCount.toString()
                )
            }
        }
    }
}

@Composable
private fun SessionRow(
    session: GameSession,
    isActive: Boolean,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (isActive) MaterialTheme.colorScheme.secondaryContainer
            else MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        session.label,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        formatDateTime(session.startTime),
                        style = MaterialTheme.typography.labelSmall
                    )
                }
                IconButton(onClick = onDelete) {
                    Icon(Icons.Filled.Delete, contentDescription = "Hapus")
                }
            }
            StatGrid(
                duration = formatDuration(
                    (session.endTime ?: System.currentTimeMillis()) - session.startTime
                ),
                start = "%.1f°C".format(session.startTemp),
                current = session.endTemp?.let { "%.1f°C".format(it) } ?: "—",
                max = "%.1f°C".format(session.maxTemp),
                avg = "%.1f°C".format(session.avgTemp),
                samples = session.sampleCount.toString()
            )
        }
    }
}

@Composable
private fun StatGrid(
    duration: String,
    start: String,
    current: String,
    max: String,
    avg: String,
    samples: String
) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Stat("Durasi", duration)
        Stat("Sampel", samples)
        Stat("Awal", start)
    }
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Stat("Akhir/Now", current)
        Stat("Max", max, highlight = true)
        Stat("Rata-rata", avg)
    }
}

@Composable
private fun Stat(label: String, value: String, highlight: Boolean = false) {
    Column {
        Text(label, style = MaterialTheme.typography.labelSmall)
        Text(
            value,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = if (highlight) FontWeight.Bold else FontWeight.SemiBold,
            color = if (highlight) MaterialTheme.colorScheme.error
            else MaterialTheme.colorScheme.onSurface
        )
    }
}

private fun formatDuration(ms: Long): String {
    val totalSec = (ms / 1000L).coerceAtLeast(0L)
    val h = totalSec / 3600L
    val m = (totalSec % 3600L) / 60L
    val s = totalSec % 60L
    return when {
        h > 0 -> "%dj %dm %ds".format(h, m, s)
        m > 0 -> "%dm %ds".format(m, s)
        else -> "%ds".format(s)
    }
}

private val DATE_FMT = SimpleDateFormat("dd MMM yyyy HH:mm", Locale.getDefault())
private fun formatDateTime(ms: Long): String = DATE_FMT.format(Date(ms))

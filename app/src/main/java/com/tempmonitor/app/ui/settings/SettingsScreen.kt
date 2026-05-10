package com.tempmonitor.app.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.delay

@Composable
fun SettingsScreen(viewModel: SettingsViewModel = hiltViewModel()) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var permissionTick by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) {
        while (true) {
            permissionTick++
            delay(1_500L)
        }
    }
    val hasUsageAccess = remember(permissionTick) { viewModel.hasUsageStatsPermission() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            "Pengaturan",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.SemiBold
        )

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "Interval monitoring",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(3, 5, 10).forEach { seconds ->
                        FilterChip(
                            selected = settings.intervalSeconds == seconds,
                            onClick = { viewModel.setInterval(seconds) },
                            label = { Text("${seconds}s") }
                        )
                    }
                }
                Text(
                    "Default: 5 detik. Hindari nilai terlalu kecil agar baterai tidak boros.",
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }

        ThresholdsCard(
            warm = settings.warmThreshold,
            hot = settings.hotThreshold,
            onUpdate = viewModel::setThresholds
        )

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Notifikasi", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        Text(
                            "Tampilkan suhu real-time di status bar",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    Switch(
                        checked = settings.notificationsEnabled,
                        onCheckedChange = viewModel::setNotificationsEnabled
                    )
                }
                HorizontalDivider()
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Auto-start saat boot", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        Text(
                            "Mulai monitoring otomatis setelah perangkat dinyalakan ulang",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    Switch(
                        checked = settings.autoStartOnBoot,
                        onCheckedChange = viewModel::setAutoStartOnBoot
                    )
                }
                HorizontalDivider()
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Selalu monitor", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        Text(
                            "Jalankan service tiap kali aplikasi dibuka dan biarkan OS me-restart kalau ke-kill di background. Tetap butuh whitelist autostart + no-restriction di MIUI.",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    Switch(
                        checked = settings.alwaysMonitor,
                        onCheckedChange = viewModel::setAlwaysMonitor
                    )
                }
            }
        }

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Sesi gaming otomatis", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        Text(
                            "Mulai dan akhiri sesi otomatis ketika game (kategori game) dibuka di foreground.",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    Switch(
                        checked = settings.autoSessionGaming,
                        onCheckedChange = viewModel::setAutoSessionGaming
                    )
                }
                if (settings.autoSessionGaming) {
                    Text(
                        if (hasUsageAccess) "Akses penggunaan: aktif. Detektor foreground siap."
                        else "Butuh izin Usage Access. Buka Settings sistem dan aktifkan untuk SuhuBar.",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (hasUsageAccess) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.error
                    )
                    OutlinedButton(onClick = {
                        val intent = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS).apply {
                            data = Uri.fromParts("package", context.packageName, null)
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                        runCatching { context.startActivity(intent) }
                            .onFailure {
                                context.startActivity(
                                    Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)
                                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                )
                            }
                    }) {
                        Text("Buka Usage Access settings")
                    }
                }
            }
        }

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Tentang", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(
                    "MIUI/HyperOS membatasi background service. Tambahkan SuhuBar ke daftar autostart dan no-restriction battery agar notifikasi suhu tidak terhenti.",
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }

        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun ThresholdsCard(
    warm: Float,
    hot: Float,
    onUpdate: (Float, Float) -> Unit
) {
    var warmText by remember(warm) { mutableStateOf(warm.toString()) }
    var hotText by remember(hot) { mutableStateOf(hot.toString()) }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                "Threshold suhu (°C)",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = warmText,
                    onValueChange = {
                        warmText = it
                        it.toFloatOrNull()?.let { value -> onUpdate(value, hotText.toFloatOrNull() ?: hot) }
                    },
                    label = { Text("Warm") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Number,
                        imeAction = ImeAction.Next
                    ),
                    modifier = Modifier.weight(1f)
                )
                OutlinedTextField(
                    value = hotText,
                    onValueChange = {
                        hotText = it
                        it.toFloatOrNull()?.let { value -> onUpdate(warmText.toFloatOrNull() ?: warm, value) }
                    },
                    label = { Text("Hot") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Number,
                        imeAction = ImeAction.Done
                    ),
                    modifier = Modifier.weight(1f)
                )
            }
            Text(
                "Default Warm 40 dan Hot 45.",
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}

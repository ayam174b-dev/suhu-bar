package com.tempmonitor.app.ui.info

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.background
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BatteryAlert
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Park
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Thermostat
import androidx.compose.material.icons.filled.Timeline
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
fun InfoScreen() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            "Cara pakai",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.SemiBold
        )
        Text(
            "Aplikasi ini memantau suhu HP secara real-time dan menampilkannya sebagai notifikasi tetap di status bar.",
            style = MaterialTheme.typography.bodyMedium
        )

        Spacer(Modifier.height(4.dp))

        Section(title = "Langkah cepat") {
            Step(
                number = "1",
                title = "Mulai monitoring",
                body = "Buka tab Dashboard dan tekan Start Monitoring. Saat pertama kali, izinkan notifikasi.",
                icon = Icons.Filled.Bolt
            )
            Step(
                number = "2",
                title = "Lihat suhu di status bar",
                body = "Notifikasi tetap akan muncul. Ikon kecil di status bar menampilkan angka suhu baterai (mis. 38) sehingga bisa dibaca tanpa membuka notifikasi.",
                icon = Icons.Filled.Notifications
            )
            Step(
                number = "3",
                title = "Cek histori",
                body = "Tab History menampilkan grafik suhu untuk 1 jam, 6 jam, atau 24 jam terakhir, lengkap dengan min/max/rata-rata.",
                icon = Icons.Filled.Timeline
            )
            Step(
                number = "4",
                title = "Ubah pengaturan",
                body = "Tab Settings: ubah interval (3/5/10 detik), threshold Warm/Hot, dan opsi auto-start setelah reboot.",
                icon = Icons.Filled.Settings
            )
            Step(
                number = "5",
                title = "Hentikan kapan saja",
                body = "Tekan Stop Monitoring di Dashboard atau aksi Stop pada notifikasi.",
                icon = Icons.Filled.BatteryAlert
            )
        }

        Section(title = "Sumber data suhu") {
            BulletItem(
                icon = Icons.Filled.Thermostat,
                title = "Suhu baterai (utama)",
                body = "Dibaca langsung dari kernel di /sys/class/power_supply/battery/temp; jika tidak tersedia, jatuh ke BatteryManager.ACTION_BATTERY_CHANGED."
            )
            BulletItem(
                icon = Icons.Filled.Memory,
                title = "Suhu CPU (opsional)",
                body = "Best-effort dari /sys/class/thermal/thermal_zone*. Pada beberapa Poco / MIUI HyperOS, akses sysfs CPU diblokir tanpa root → bagian CPU otomatis disembunyikan."
            )
        }

        Section(title = "Penting untuk MIUI / HyperOS") {
            BulletItem(
                icon = Icons.Filled.Park,
                title = "Autostart & no battery restriction",
                body = "Settings → Apps → SuhuBar → Battery saver: pilih No restrictions. Lalu Security app → Manage apps → SuhuBar → aktifkan Autostart. Tanpa langkah ini, foreground service bisa dihentikan oleh sistem."
            )
            BulletItem(
                icon = Icons.Filled.Notifications,
                title = "Notifikasi tetap aktif",
                body = "Channel notifikasi 'Temperature Monitor' diset prioritas Low (tidak berbunyi). Jangan dimute karena status bar membutuhkan channel aktif untuk menampilkan ikon suhu."
            )
        }

        Section(title = "Status warna") {
            BulletItem(
                color = Color1,
                title = "Normal",
                body = "Suhu di bawah threshold Warm (default < 40°C)."
            )
            BulletItem(
                color = Color2,
                title = "Warm",
                body = "Antara threshold Warm dan Hot (default 40–44°C). Hindari load berat sementara waktu."
            )
            BulletItem(
                color = Color3,
                title = "Hot",
                body = "Sama dengan / di atas threshold Hot (default ≥ 45°C). Stop game/app berat dan biarkan HP dingin sebelum dipakai lagi."
            )
        }

        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            content()
        }
    }
}

@Composable
private fun Step(
    number: String,
    title: String,
    body: String,
    icon: ImageVector
) {
    Row(verticalAlignment = Alignment.Top) {
        Column(
            modifier = Modifier
                .size(32.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                number,
                color = MaterialTheme.colorScheme.onPrimary,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold
            )
        }
        Spacer(Modifier.size(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(Modifier.size(8.dp))
                Text(
                    title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
            }
            Spacer(Modifier.height(2.dp))
            Text(body, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun BulletItem(
    icon: ImageVector? = null,
    color: androidx.compose.ui.graphics.Color? = null,
    title: String,
    body: String
) {
    Row(verticalAlignment = Alignment.Top) {
        when {
            icon != null -> Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
            color != null -> androidx.compose.foundation.layout.Box(
                modifier = Modifier
                    .size(20.dp)
                    .clip(CircleShape)
                    .background(color)
            )
        }
        Spacer(Modifier.size(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold
            )
            Text(body, style = MaterialTheme.typography.bodySmall)
        }
    }
}

private val Color1 = androidx.compose.ui.graphics.Color(0xFF2E7D32)
private val Color2 = androidx.compose.ui.graphics.Color(0xFFB58A00)
private val Color3 = androidx.compose.ui.graphics.Color(0xFFB23A3A)

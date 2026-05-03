# SuhuBar — Android Temperature Monitor

Aplikasi Android untuk monitoring suhu HP secara real-time dengan output utama berupa **notifikasi persisten** di status bar (tanpa overlay). Dirancang untuk perangkat **Poco X series (Android 13–15, MIUI/HyperOS)** namun berjalan di Android 8.0+ (`minSdk = 26`).

## Fitur

- Membaca **suhu baterai** (`BatteryManager` + `ACTION_BATTERY_CHANGED`) sebagai sumber utama.
- Membaca **suhu CPU** secara best-effort dari `/sys/class/thermal/thermal_zone*/temp` — ditampilkan jika tersedia, jika gagal akan disembunyikan.
- **Foreground Service** sebagai core engine untuk menjaga monitoring tetap aktif di background.
- **Notifikasi persisten** dengan format:
  - `🌡️ 38°C | CPU 65°C | Normal` — bila CPU temperature tersedia
  - `🌡️ 38°C | Normal` — bila CPU tidak tersedia
- Penyimpanan histori menggunakan **Room** (data tersimpan setiap 30 detik).
- **History screen** dengan grafik **Vico** dan filter rentang 1 jam / 6 jam / 24 jam beserta statistik min/max/avg.
- **Settings**: pilih interval (3/5/10 detik), threshold Warm/Hot kustom, toggle notifikasi, opsi auto-start setelah boot.
- **BootReceiver** untuk auto-start setelah perangkat dinyalakan ulang (opsional, dimatikan secara default).

## Status warna suhu

| Rentang        | Status |
|----------------|--------|
| `< warmThreshold` (default `< 40°C`) | Normal (hijau) |
| `warmThreshold ≤ T < hotThreshold` (default `40–44°C`) | Warm (kuning) |
| `≥ hotThreshold` (default `≥ 45°C`) | Hot (merah) |

## Stack

- Kotlin 2.0.21, Jetpack Compose (BOM 2024.10.01), Material 3
- Hilt 2.52 untuk dependency injection
- Room 2.6.1 + KSP
- Vico 2.1.3 untuk grafik
- Navigation Compose 2.8.4
- DataStore (preferences) untuk pengaturan
- Min SDK 26, Target SDK 35, Build SDK 35
- Gradle Kotlin DSL + version catalog (`gradle/libs.versions.toml`)

## Build

```bash
# Pastikan Android SDK Platform 35 + Build-Tools 35 sudah terinstal.
# Set sdk.dir di local.properties (otomatis dibuat oleh Android Studio).

./gradlew assembleDebug
# Output: app/build/outputs/apk/debug/app-debug.apk
```

Atau buka folder ini di Android Studio (Hedgehog atau lebih baru) dan klik **Run**.

## Permissions

Manifest hanya menggunakan permission yang valid:

- `FOREGROUND_SERVICE` — mendeklarasikan foreground service
- `FOREGROUND_SERVICE_DATA_SYNC` — wajib pada Android 14+ untuk `foregroundServiceType="dataSync"`
- `POST_NOTIFICATIONS` — agar notifikasi persisten dapat tampil di Android 13+
- `RECEIVE_BOOT_COMPLETED` — auto-start setelah reboot (opsional, hanya berjalan jika user mengaktifkan toggle)
- `WAKE_LOCK` — menjaga loop monitoring tetap berjalan saat layar mati

`FOREGROUND_SERVICE_SPECIAL_USE` **tidak digunakan**.

## Catatan MIUI / HyperOS

- Tambahkan SuhuBar ke daftar **autostart** dan **no battery restriction** agar foreground service tidak dihentikan oleh sistem.
- Beberapa perangkat tidak mengekspos thermal zone CPU yang dapat dibaca tanpa root → field `cpuTemp` akan `null` dan notifikasi otomatis menyembunyikan bagian CPU.
- Tidak memungkinkan menampilkan angka suhu langsung di **ikon** status bar (seperti ikon sinyal). Suhu ditampilkan pada teks notifikasi.

## Validasi data

- Nilai `< 0°C` atau `≥ 100°C` diabaikan.
- `batteryTemp` adalah sumber utama; bila tidak tersedia, snapshot dilewati dan notifikasi tidak diperbarui.
- `cpuTemp` bersifat opsional dan otomatis jatuh ke fallback jika gagal dibaca.

## Struktur folder

```
com.tempmonitor.app
├── MainActivity.kt
├── TempMonitorApp.kt
├── di/AppModule.kt
├── data/
│   ├── TemperatureData.kt
│   ├── TemperatureReader.kt
│   ├── db/{TemperatureRecord, TemperatureDao, AppDatabase}.kt
│   ├── preferences/MonitorPreferences.kt
│   └── repository/TemperatureRepository.kt
├── service/
│   ├── TemperatureMonitorService.kt
│   ├── TemperatureState.kt
│   └── BootReceiver.kt
└── ui/
    ├── dashboard/{DashboardScreen, DashboardViewModel}.kt
    ├── history/{HistoryScreen, HistoryViewModel}.kt
    ├── settings/{SettingsScreen, SettingsViewModel}.kt
    ├── navigation/AppNavigation.kt
    └── theme/{Color, Theme, Type}.kt
```

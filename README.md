# Cuacaku 🌤️

Aplikasi prakiraan cuaca real-time untuk Android dengan sumber data open source.

---

## ✨ Fitur

### Cuaca Real-Time
- ☀️ Kondisi cuaca saat ini (suhu, kelembaban, angin, tekanan)
- 📊 Prakiraan per jam (24 jam ke depan)
- 📅 Prakiraan 7 hari
- 🌅 Waktu sunrise & sunset
- 📍 Deteksi lokasi otomatis
- 🔍 Pencarian lokasi manual

### Sistem Notifikasi Lengkap
- 📢 Notifikasi prakiraan harian (pagi)
- ⚠️ Peringatan cuaca ekstrem (badai, hujan lebat)
- 🌧️ Peringatan kemungkinan hujan
- 🌡️ Peringatan suhu ekstrem
- 🔔 Background updates setiap 1 jam


## 🌐 Sumber Data

### 1. Open-Meteo API (Cuaca)

### 2. Nominatim API (Geocoding)


---


## 🚀 Setup & Instalasi

### Prerequisites
- Android Studio Hedgehog (2023.1.1) atau lebih baru
- JDK 17
- Android SDK 34
- Gradle 8.5

---

## 🔨 Build & Run

### Debug Build
```bash
# Via terminal
./gradlew assembleDebug

# APK location: app/build/outputs/apk/debug/app-debug.apk
```

### Download Release APK

Release APK tersedia di [GitHub Releases](https://github.com/eltfd/Cuacaku/releases).

```bash
# Download via CLI
gh release download v1.0.0 -p "app-release.apk"
```

---

## 🔔 Sistem Notifikasi

### Notification Channels

| Channel | Priority | Deskripsi |
|---------|----------|-----------|
| `weather_alerts` | HIGH | Peringatan cuaca ekstrem |
| `daily_forecast` | DEFAULT | Prakiraan harian |

### Jenis Notifikasi

1. **Daily Forecast** (Pagi hari)
   - Cuaca hari ini
   - Suhu max/min
   - Kemungkinan hujan

2. **Severe Weather Alert**
   - Badai petir
   - Hujan lebat
   - Weather code: 65, 67, 75, 77, 82, 86, 95, 96, 99

3. **Rain Alert**
   - Trigger: Precipitation probability ≥ 60%
   - Waktu perkiraan hujan

4. **Temperature Alert**
   - Suhu tinggi: ≥ 35°C
   - Suhu rendah: ≤ 10°C


---

### WMO Weather Codes

| Code | Condition |
|------|-----------|
| 0 | Cerah |
| 1-3 | Berawan sebagian/mendung |
| 45, 48 | Berkabut |
| 51, 53, 55 | Gerimis |
| 61, 63, 65 | Hujan |
| 71, 73, 75 | Salju |
| 95 | Badai petir |
| 96, 99 | Badai dengan hujan es |

---

## 👨‍💻 Panduan Pengembangan

### Menambah Fitur Notifikasi Baru

1. Tambahkan ID di `NotificationIds`:
```kotlin
object NotificationIds {
    const val NEW_ALERT = 1005
}
```

2. Implementasi di `WeatherNotificationManager`:
```kotlin
fun sendNewAlert(...) {
    val notification = createNotification(...)
    notificationManager.notify(NotificationIds.NEW_ALERT, notification)
}
```

3. Trigger dari `WeatherUpdateWorker`:
```kotlin
if (shouldSendNewAlert) {
    notificationManager.sendNewAlert(...)
}
```

### Menambah Data Cuaca

1. Update model di `WeatherResponse.kt`
2. Tambahkan field di `WeatherData.kt` (UI model)
3. Transform di `WeatherRepository.kt`
4. Display di UI screen

### Testing

```bash
# Run unit tests
./gradlew test

# Run instrumented tests
./gradlew connectedAndroidTest
```

---

## 📄 License

MIT License - Free to use and modify.

---

## 📖 Referensi

| Resource | Link |
|----------|------|
| Repository | https://github.com/eltfd/Cuacaku |
| Releases | https://github.com/eltfd/Cuacaku/releases |
| CI Runs | https://github.com/eltfd/Cuacaku/actions |
| Open-Meteo API | https://open-meteo.com/en/docs |
| Nominatim API | https://nominatim.org/release-docs/develop/api/Overview/ |
| Compose BOM | https://developer.android.com/develop/ui/compose/bom |
| Material3 | https://m3.material.io |
| Changelog | [CHANGELOG.md](CHANGELOG.md) |
| Contributing | [CONTRIBUTING.md](CONTRIBUTING.md) |
| Troubleshooting | [TROUBLESHOOTING.md](TROUBLESHOOTING.md) |

---

## �🙏 Credits

- **Weather Data**: [Open-Meteo](https://open-meteo.com)
- **Geocoding**: [Nominatim/OpenStreetMap](https://nominatim.org)
- **Icons**: Material Design Icons

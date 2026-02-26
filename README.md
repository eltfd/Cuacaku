# Weather Forecast App 🌤️

Aplikasi prakiraan cuaca real-time untuk Android dengan sumber data **100% gratis dan open source**. Tidak memerlukan API key atau berlangganan apapun.

## 📋 Daftar Isi

- [Fitur](#-fitur)
- [Arsitektur](#-arsitektur)
- [Sumber Data](#-sumber-data)
- [Struktur Proyek](#-struktur-proyek)
- [Setup & Instalasi](#-setup--instalasi)
- [Build & Run](#-build--run)
- [Sistem Notifikasi](#-sistem-notifikasi)
- [API Documentation](#-api-documentation)
- [Panduan Pengembangan](#-panduan-pengembangan)

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

### UI Modern
- 🎨 Material Design 3 dengan Jetpack Compose
- 🌙 Support dark mode
- 🌈 Dynamic color (Android 12+)
- 🔄 Pull-to-refresh
- 📱 Edge-to-edge display

---

## 🏗️ Arsitektur

Aplikasi menggunakan **MVVM (Model-View-ViewModel)** pattern:

```
┌─────────────────────────────────────────────────────────┐
│                        UI Layer                          │
│  ┌─────────────────┐  ┌─────────────────┐               │
│  │   HomeScreen    │  │  SettingsScreen │               │
│  └────────┬────────┘  └────────┬────────┘               │
│           │                    │                         │
│           └──────────┬─────────┘                         │
│                      ▼                                   │
│           ┌─────────────────────┐                        │
│           │   WeatherViewModel  │                        │
│           └──────────┬──────────┘                        │
└──────────────────────┼───────────────────────────────────┘
                       │
┌──────────────────────┼───────────────────────────────────┐
│                Data Layer                                │
│                      ▼                                   │
│           ┌─────────────────────┐                        │
│           │  WeatherRepository  │                        │
│           └──────────┬──────────┘                        │
│                      │                                   │
│      ┌───────────────┼───────────────┐                   │
│      ▼               ▼               ▼                   │
│  ┌────────┐   ┌────────────┐   ┌───────────┐            │
│  │ Weather│   │  Geocoding │   │Preferences│            │
│  │   API  │   │    API     │   │  Manager  │            │
│  └────────┘   └────────────┘   └───────────┘            │
└──────────────────────────────────────────────────────────┘
```

---

## 🌐 Sumber Data

### 1. Open-Meteo API (Cuaca)
- **URL**: https://open-meteo.com
- **Biaya**: GRATIS
- **API Key**: TIDAK DIPERLUKAN
- **Update**: Setiap 15 menit
- **Coverage**: Global

### 2. Nominatim API (Geocoding)
- **URL**: https://nominatim.openstreetmap.org
- **Biaya**: GRATIS (OpenStreetMap)
- **API Key**: TIDAK DIPERLUKAN
- **Fitur**: Reverse geocoding & search

---

## 📁 Struktur Proyek

```
app/src/main/java/com/weather/forecast/
├── WeatherApplication.kt      # Application class
├── MainActivity.kt            # Entry point
│
├── data/
│   ├── api/
│   │   ├── WeatherApiService.kt    # Open-Meteo API interface
│   │   ├── GeocodingApiService.kt  # Nominatim API interface
│   │   └── RetrofitClient.kt       # Retrofit configuration
│   │
│   ├── model/
│   │   ├── WeatherResponse.kt      # API response models
│   │   ├── WeatherData.kt          # UI-ready models
│   │   ├── WeatherCondition.kt     # WMO weather codes
│   │   └── GeocodingResponse.kt    # Geocoding models
│   │
│   ├── repository/
│   │   └── WeatherRepository.kt    # Data source abstraction
│   │
│   └── preferences/
│       └── PreferencesManager.kt   # DataStore preferences
│
├── location/
│   └── LocationManager.kt          # GPS location handling
│
├── notification/
│   ├── NotificationChannels.kt     # Channel definitions
│   └── WeatherNotificationManager.kt # Notification logic
│
├── service/
│   └── WeatherUpdateService.kt     # Foreground service
│
├── worker/
│   └── WeatherUpdateWorker.kt      # Background updates
│
├── receiver/
│   └── BootReceiver.kt             # Boot completed receiver
│
└── ui/
    ├── theme/
    │   ├── Color.kt
    │   ├── Theme.kt
    │   ├── Type.kt
    │   └── Shape.kt
    │
    ├── components/
    │   └── WeatherIcon.kt          # Weather icon component
    │
    ├── screens/
    │   ├── HomeScreen.kt           # Main weather screen
    │   └── SettingsScreen.kt       # Settings screen
    │
    ├── viewmodel/
    │   └── WeatherViewModel.kt     # UI state management
    │
    └── navigation/
        └── Navigation.kt           # Navigation setup
```

---

## 🚀 Setup & Instalasi

### Prerequisites
- Android Studio Hedgehog (2023.1.1) atau lebih baru
- JDK 17
- Android SDK 34
- Gradle 8.5

### Clone & Open
```bash
# Clone repository
git clone <repository-url>
cd weather

# Open dengan Android Studio
# File > Open > pilih folder weather
```

### Sync Dependencies
Android Studio akan otomatis sync dependencies. Jika tidak:
```bash
./gradlew build
```

### Developer Setup (Detailed)

These are the exact commands and environment steps used to build and run the project on macOS. Run the commands in a terminal and adjust paths if needed.

```bash
# 1) Install OpenJDK 17 (Homebrew)
brew install openjdk@17

# Add to your shell (recommended in ~/.zshrc or ~/.bash_profile)
export JAVA_HOME="/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home"
export PATH="$JAVA_HOME/bin:$PATH"

# 2) Install Android command-line tools and platform-tools
brew install --cask android-platform-tools android-commandlinetools

# 3) Create Android SDK root and install platform packages (SDK 34)
mkdir -p "$HOME/Library/Android/sdk"
export ANDROID_SDK_ROOT="$HOME/Library/Android/sdk"

# Use sdkmanager (linked by Homebrew) to install required packages
/opt/homebrew/bin/sdkmanager --sdk_root="$ANDROID_SDK_ROOT" "platform-tools" "platforms;android-34" "build-tools;34.0.0"
yes | /opt/homebrew/bin/sdkmanager --sdk_root="$ANDROID_SDK_ROOT" --licenses

# 4) Create local.properties pointing to SDK (project root)
echo "sdk.dir=$HOME/Library/Android/sdk" > local.properties

# 5) Ensure a valid Gradle wrapper (regenerate if wrapper JAR is broken)
# If you have a system gradle installed, run:
# gradle wrapper --gradle-version 8.5
# or use the included wrapper once valid:
./gradlew wrapper --gradle-version 8.5

# 6) Build debug APK
./gradlew assembleDebug

# 7) Install to a connected device
adb install -r app/build/outputs/apk/debug/app-debug.apk

# Helpful adb commands while testing
adb devices
adb logcat -d | tail -n 200

```

Note: On this machine I used OpenJDK 17 (OpenJDK 17.0.18), Gradle wrapper 8.5, Compose BOM 2023.10.01 and `com.google.accompanist:accompanist-permissions:0.32.0` to avoid runtime animation library mismatches.

See `TROUBLESHOOTING.md` for common errors and fixes, and `CHANGELOG.md` for recent changes made while building this project.


---

## 🔨 Build & Run

### Debug Build
```bash
# Via terminal
./gradlew assembleDebug

# APK location: app/build/outputs/apk/debug/app-debug.apk
```

### Release Build
```bash
# Generate release APK
./gradlew assembleRelease

# Pastikan sudah setup signing config di build.gradle.kts
```

### Run di Emulator/Device
```bash
# Install dan run
./gradlew installDebug

# Atau gunakan Android Studio: Run > Run 'app'
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

### Background Worker

```kotlin
// Update setiap 1 jam via WorkManager
WeatherWorkerScheduler.schedulePeriodicWeatherUpdate(context)

// One-time update
WeatherWorkerScheduler.scheduleOneTimeUpdate(context)

// Cancel updates
WeatherWorkerScheduler.cancelWeatherUpdates(context)
```

---

## 📚 API Documentation

### Open-Meteo Weather API

#### Endpoint
```
GET https://api.open-meteo.com/v1/forecast
```

#### Parameters
| Parameter | Type | Description |
|-----------|------|-------------|
| latitude | Double | Lokasi latitude |
| longitude | Double | Lokasi longitude |
| current | String | Parameter cuaca saat ini |
| hourly | String | Parameter prakiraan per jam |
| daily | String | Parameter prakiraan harian |
| timezone | String | Timezone (auto = lokasi) |
| forecast_days | Int | Jumlah hari (1-16) |

#### Example Request
```kotlin
weatherApi.getWeather(
    latitude = -6.2088,
    longitude = 106.8456,
    current = "temperature_2m,weather_code",
    hourly = "temperature_2m,precipitation_probability",
    daily = "temperature_2m_max,temperature_2m_min",
    timezone = "auto",
    forecastDays = 7
)
```

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

## 🙏 Credits

- **Weather Data**: [Open-Meteo](https://open-meteo.com)
- **Geocoding**: [Nominatim/OpenStreetMap](https://nominatim.org)
- **Icons**: Material Design Icons

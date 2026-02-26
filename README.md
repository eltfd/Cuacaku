# Cuacaku 🌤️

[![Android CI](https://github.com/eltfd/Cuacaku/actions/workflows/android-ci.yml/badge.svg)](https://github.com/eltfd/Cuacaku/actions/workflows/android-ci.yml)
[![Release](https://github.com/eltfd/Cuacaku/actions/workflows/release-sign.yml/badge.svg)](https://github.com/eltfd/Cuacaku/actions/workflows/release-sign.yml)
[![Latest Release](https://img.shields.io/github/v/release/eltfd/Cuacaku)](https://github.com/eltfd/Cuacaku/releases/latest)

Aplikasi prakiraan cuaca real-time untuk Android dengan sumber data **100% gratis dan open source**. Tidak memerlukan API key atau berlangganan apapun.

## 📋 Daftar Isi

- [Fitur](#-fitur)
- [Tech Stack](#-tech-stack)
- [Arsitektur](#-arsitektur)
- [Sumber Data](#-sumber-data)
- [Struktur Proyek](#-struktur-proyek)
- [Setup & Instalasi](#-setup--instalasi)
- [Build & Run](#-build--run)
- [CI/CD Pipeline](#-cicd-pipeline)
- [Release & Signing](#-release--signing)
- [Sistem Notifikasi](#-sistem-notifikasi)
- [API Documentation](#-api-documentation)
- [Panduan Pengembangan](#-panduan-pengembangan)
- [Troubleshooting](#-troubleshooting)
- [Referensi](#-referensi)

---

## ✨ Fitur

### Cuaca Real-Time
- ☀️ Kondisi cuaca saat ini (suhu, kelembaban, angin, tekanan)
- 📊 Prakiraan per jam (24 jam ke depan)
- 📅 Prakiraan 7 hari dengan detail per jam (tap untuk expand)
- 🕐 Prakiraan cuaca per jam untuk setiap hari (interval 1 jam)
- 🌅 Waktu sunrise & sunset
- 📍 Deteksi lokasi otomatis
- 🔍 Pencarian lokasi manual

### Sistem Notifikasi Lengkap
- 📢 Notifikasi prakiraan harian (pagi)
- ⚠️ Peringatan cuaca ekstrem (badai, hujan lebat)
- 🌧️ Peringatan kemungkinan hujan
- 🌡️ Peringatan suhu ekstrem
- 🔔 Background updates setiap 1 jam

### Auto Update
- 🔄 Pengecekan update otomatis saat aplikasi dibuka
- 📦 Download & install/update APK langsung dari GitHub Releases
- 🔀 Android otomatis mendeteksi: UPDATE jika sudah terinstall, INSTALL jika belum
- 📋 Dialog info versi terbaru, ukuran file, dan catatan rilis

### UI Modern
- 🎨 Material Design 3 dengan Jetpack Compose
- 🌙 Support dark mode
- 🌈 Dynamic color (Android 12+)
- 🔄 Pull-to-refresh
- 📱 Edge-to-edge display

---

## 🛠️ Tech Stack

| Category | Technology | Version |
|----------|-----------|---------|
| Language | Kotlin | 1.9.21 |
| UI | Jetpack Compose (BOM) | 2023.10.01 |
| Design | Material Design 3 | via BOM |
| Architecture | MVVM | — |
| Networking | Retrofit + OkHttp | 2.9.0 / 4.12.0 |
| JSON | Gson | 2.10.1 |
| Database | Room | 2.6.1 |
| Preferences | DataStore | 1.0.0 |
| Background | WorkManager | 2.9.0 |
| Location | Play Services Location | 21.1.0 |
| Permissions | Accompanist Permissions | 0.32.0 |
| Build | Gradle (Kotlin DSL) | 8.5 |
| Java | JDK | 17 |
| Min SDK | Android 8.0 | API 26 |
| Target SDK | Android 14 | API 34 |
| CI/CD | GitHub Actions | — |
| Signing | Keystore (base64 secret) | — |

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
│   │   ├── GitHubApiService.kt     # GitHub Releases API (auto-update)
│   │   └── RetrofitClient.kt       # Retrofit configuration
│   │
│   ├── model/
│   │   ├── WeatherResponse.kt      # API response models
│   │   ├── WeatherData.kt          # UI-ready models (termasuk hourly per day)
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
├── update/
│   └── AppUpdateManager.kt         # Auto-update via GitHub Releases
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
    │   ├── WeatherIcon.kt          # Weather icon component
    │   └── UpdateDialog.kt         # Update available dialog
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

## 🔄 CI/CD Pipeline

Project ini menggunakan **GitHub Actions** untuk Continuous Integration dan Continuous Delivery.

### Workflows

| Workflow | File | Trigger | Tujuan |
|----------|------|---------|--------|
| Android CI | `.github/workflows/android-ci.yml` | Push/PR ke `main` | Build debug APK, upload artifact |
| Release (signed) | `.github/workflows/release-sign.yml` | Manual dispatch | Build signed release APK, buat GitHub Release |

### 1. Android CI (`android-ci.yml`)

Berjalan otomatis setiap push atau pull request ke branch `main`.

**Steps:**
1. Checkout code
2. Setup JDK 17 (Temurin) dengan Gradle cache
3. Build debug APK (`./gradlew assembleDebug`)
4. Upload `app-debug.apk` sebagai artifact

**Status badge:**
```
[![Android CI](https://github.com/eltfd/weather-forecast-app/actions/workflows/android-ci.yml/badge.svg)](https://github.com/eltfd/weather-forecast-app/actions/workflows/android-ci.yml)
```

### 2. Release Signing (`release-sign.yml`)

Dispatch manual dari tab Actions → "Release (signed)" → "Run workflow".

**Input:**
- `tag` — Tag name untuk release (contoh: `v1.0.0`, `v1.1.0`)

**Steps:**
1. Checkout code
2. Setup JDK 17 (Temurin) dengan Gradle cache
3. Decode keystore dari secret `SIGNING_KEY` (base64) → `app/keystore.jks`
4. Build release APK (`./gradlew assembleRelease`)
5. Buat GitHub Release dengan tag dan upload `app-release.apk`

**Dispatch via CLI:**
```bash
gh workflow run release-sign.yml -f tag=v1.1.0
```

---

## 🔐 Release & Signing

### Repository Secrets

Signing dikonfigurasi menggunakan GitHub repository secrets:

| Secret | Deskripsi |
|--------|-----------|
| `SIGNING_KEY` | Base64-encoded keystore (`.jks`) file |
| `RELEASE_KEY_ALIAS` | Alias key di dalam keystore |
| `RELEASE_KEY_PASSWORD` | Password untuk key |
| `RELEASE_STORE_PASSWORD` | Password untuk keystore |

### Signing Config (`app/build.gradle.kts`)

```kotlin
signingConfigs {
    create("release") {
        val keystoreFile = System.getenv("RELEASE_KEYSTORE_FILE") ?: "keystore.jks"
        storeFile = file(keystoreFile)
        storePassword = System.getenv("RELEASE_STORE_PASSWORD") ?: ""
        keyAlias = System.getenv("RELEASE_KEY_ALIAS") ?: ""
        keyPassword = System.getenv("RELEASE_KEY_PASSWORD") ?: ""
    }
}
```

### Membuat Keystore Baru

Jika perlu regenerate keystore:

```bash
# Generate keystore
keytool -genkeypair -v \
  -keystore release-keystore.jks \
  -keyalg RSA -keysize 2048 -validity 10000 \
  -alias your-alias \
  -dname "CN=Your Name, OU=Dev, O=Org, L=City, ST=State, C=ID"

# Encode ke base64
base64 -i release-keystore.jks | tr -d '\n'

# Set sebagai secret di GitHub
gh secret set SIGNING_KEY < <(base64 -i release-keystore.jks)
gh secret set RELEASE_KEY_ALIAS -b "your-alias"
gh secret set RELEASE_KEY_PASSWORD -b "your-password"
gh secret set RELEASE_STORE_PASSWORD -b "your-password"
```

### Download Release APK

Release APK tersedia di [GitHub Releases](https://github.com/eltfd/Cuacaku/releases).

```bash
# Download via CLI
gh release download v1.1.0 -p "app-release.apk"
```

---

## 🔄 Auto Update (In-App)

Aplikasi memiliki sistem update otomatis yang memeriksa versi terbaru di GitHub Releases setiap kali dibuka.

### Cara Kerja

```
App Launch → GitHub API (releases/latest)
  → Bandingkan tag_name vs BuildConfig.VERSION_NAME
  → Jika lebih baru → Tampilkan dialog update
  → User klik "Update Sekarang"
  → DownloadManager unduh APK
  → PackageInstaller install/update
```

### Install vs Update (Otomatis)

Android **secara otomatis** mendeteksi:

| Kondisi | Hasil |
|---------|-------|
| `applicationId` sama + signing key cocok + sudah terinstall | **UPDATE** (data pengguna tetap ada) |
| Belum pernah diinstall | **INSTALL baru** |
| Signing key berbeda | **Ditolak** (keamanan Android) |

### Komponen

| File | Fungsi |
|------|--------|
| `GitHubApiService.kt` | API interface ke GitHub Releases |
| `AppUpdateManager.kt` | Logic cek versi, download, install |
| `UpdateDialog.kt` | UI dialog (Available/Downloading/Ready/Error) |
| `file_paths.xml` | FileProvider config untuk URI APK |

### Permissions

```xml
<uses-permission android:name="android.permission.REQUEST_INSTALL_PACKAGES" />
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

## � Troubleshooting

Lihat [TROUBLESHOOTING.md](TROUBLESHOOTING.md) untuk solusi masalah umum, termasuk:
- SDK location not found
- Gradle wrapper JAR missing
- Compose runtime crash (version mismatch)
- CI/CD signing errors (keystore path, permissions)
- ADB device visibility

---

## 📖 Referensi

| Resource | Link |
|----------|------|
| Repository | https://github.com/eltfd/Cuacaku |
| Releases | https://github.com/eltfd/Cuacaku/releases |
| CI Runs | https://github.com/eltfd/Cuacaku/actions |
| Open-Meteo API | https://open-meteo.com/en/docs |
| GitHub Releases API | https://docs.github.com/en/rest/releases |
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

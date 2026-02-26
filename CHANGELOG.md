# Changelog

All notable changes to this project are documented in this file.  
Format: [Keep a Changelog](https://keepachangelog.com/en/1.1.0/) + [Semantic Versioning](https://semver.org/).

---

## [1.2.1] - 2026-02-26

**Tag:** `v1.2.1`  
**Release:** https://github.com/eltfd/Cuacaku/releases/tag/v1.2.1  
**APK:** `app-release.apk` (signed)

### Added — Potensi Cuaca Ekstrem
- `WeatherPotential` model — analisis risiko badai petir, hujan lebat, hujan es, angin kencang, puting beliung
- `RiskLevel` enum — 4 level: Rendah, Sedang, Tinggi, Ekstrem
- `AlertType` enum — 7 tipe peringatan: Thunderstorm, Heavy Rain, Hail, Strong Wind, Tornado, Snowstorm, Freezing Rain
- Kalkulasi risiko berdasarkan CAPE (Convective Available Potential Energy), freezing level height, wind shear
- Potensi cuaca dihitung per hari dan untuk kondisi saat ini (24 jam ke depan)

### Added — Data Cuaca Tambahan
- API: `cape`, `dew_point_2m`, `freezing_level_height`, `wind_gusts_10m`, `surface_pressure`, `pressure_msl` (hourly)
- API: `precipitation_hours` (daily)
- Model: `rain`, `showers`, `snowfall`, `dewPoint`, `cape` di CurrentWeatherData
- Model: `windDirection`, `windGusts`, `dewPoint`, `cape`, `freezingLevelHeight`, `rain`, `showers`, `snowfall`, `pressure` di HourlyWeatherData
- Model: `windDirectionDominant`, `rainSum`, `showersSum`, `snowfallSum`, `precipitationHours`, `weatherPotential` di DailyWeatherData

### Added — UI Baru di HomeScreen
- **WindInfoCard**: Kompas arah angin real-time (Canvas), kecepatan, hembusan, arah lengkap
- **WeatherPotentialCard**: Grid risiko cuaca (badai, hujan lebat, hujan es, angin, puting beliung, CAPE)
- **PrecipitationDetailCard**: Detail presipitasi (total, hujan, hujan deras, salju)
- **WeatherDetailsCard**: Diperluas 2 baris (+ titik embun, hembusan angin, tutupan awan)
- Peringatan aktif dengan badge risiko berwarna
- Hourly forecast item: panah arah angin + kecepatan
- Daily expanded detail: arah & hembusan angin, badge potensi cuaca per hari, kelembaban per jam

### Changed
- `WeatherApiService.kt` — parameter API diperluas (CAPE, dew point, freezing level, dll)
- `WeatherResponse.kt` — field baru di CurrentWeather, HourlyForecast, DailyForecast
- `WeatherData.kt` — field baru + model WeatherPotential, RiskLevel, AlertType, helper functions
- `WeatherRepository.kt` — kalkulasi weather potential, transform data diperkaya
- `HomeScreen.kt` — UI diperkaya dengan semua section baru

---

## [1.2.0] - 2026-02-27

**Tag:** `v1.2.0`  
**Release:** https://github.com/eltfd/Cuacaku/releases/tag/v1.2.0  
**APK:** `app-release.apk` (signed)

### Added — Pemantauan Kualitas Udara
- `AirQualityApiService.kt` — interface ke Open-Meteo Air Quality API (CAMS data)
- `AirQualityResponse.kt` — model respons API: AQI, PM2.5, PM10, O₃, CO, NO₂, SO₂, UV Index, Dust, Ammonia
- `AirQualityData.kt` — model UI: `CurrentAirQualityData`, `HourlyAirQualityData`, `DailyAirQualityData`
- `AqiLevel` enum — 6 level: Good, Moderate, Unhealthy for Sensitive, Unhealthy, Very Unhealthy, Hazardous
- `AirQualityRepository.kt` — fetch AQ + weather wind data secara paralel, enrichment data angin ke prakiraan AQ
- `AirQualityScreen.kt` — UI lengkap: AQI summary circle, detail polutan, prakiraan per jam, prakiraan harian (expandable), rekomendasi kesehatan

### Added — Pemantauan Kualitas Air
- `MarineApiService.kt` — interface ke Open-Meteo Marine API (gelombang, swell)
- `FloodApiService.kt` — interface ke Open-Meteo Flood API (GloFAS/ECMWF)
- `MarineResponse.kt` — model respons marine: wave height/direction/period, swell data
- `FloodResponse.kt` — model respons flood: river discharge (mean, median, max, min)
- `WaterQualityData.kt` — model UI: `MarineData`, `FloodData`, `SeaCondition` (Douglas Scale), `FloodRisk` (4 level)
- `WaterQualityRepository.kt` — fetch marine + flood secara paralel, error satu tidak mempengaruhi yang lain
- `WaterQualityScreen.kt` — UI lengkap: kondisi laut saat ini, prakiraan harian (expandable), data debit sungai

### Added — Bottom Navigation
- `Navigation.kt` — `Scaffold` + `NavigationBar` dengan 3 tab: Cuaca, Udara, Air
- State preservation saat berganti tab (saveState/restoreState)
- `BottomNavItem` enum dengan filled/outlined icon variants
- `EnvironmentViewModel.kt` — shared ViewModel untuk AirQuality & WaterQuality screens

### Changed
- `versionCode` → 3
- `versionName` → "1.2.0"
- `RetrofitClient.kt` — ditambahkan 3 instance API baru: `airQualityApi`, `marineApi`, `floodApi`
- `Navigation.kt` — dari simple NavHost menjadi bottom nav tiga tab

### Documentation
- README.md diperbarui: fitur baru, sumber data baru, arsitektur, struktur proyek
- CHANGELOG.md diperbarui: entry v1.2.0
- copilot-instructions.md diperbarui: sumber data, fitur, versi

---

## [1.1.0] - 2026-02-26

**Tag:** `v1.1.0`  
**Release:** https://github.com/eltfd/Cuacaku/releases/tag/v1.1.0  
**APK:** `app-release.apk` (signed)

### Added — Prakiraan Per Jam dalam Harian
- `DailyWeatherData.hourlyForecasts` — setiap item harian sekarang berisi daftar 24 data per jam
- `WeatherRepository.transformAllHourlyDataByDate()` — mengelompokkan semua data hourly berdasarkan tanggal
- UI: Section "Prakiraan 7 Hari" sekarang bisa di-expand per hari
- Tap hari untuk melihat prakiraan per jam (interval 1 jam): icon, suhu, probabilitas hujan, kecepatan angin
- Info ringkas sunrise, sunset, dan angin max ditampilkan di panel expand
- Animasi expand/collapse menggunakan `AnimatedVisibility`

### Added — Auto Update via GitHub Releases
- `GitHubApiService.kt` — API interface untuk mengambil release terbaru dari GitHub
- `AppUpdateManager.kt` — mengelola pengecekan versi, download APK, dan trigger instalasi
- `UpdateDialog.kt` — dialog Compose untuk state: Available, Downloading, ReadyToInstall, Error
- Pengecekan otomatis saat app dibuka via `LaunchedEffect` di `MainActivity`
- Download APK via Android `DownloadManager` dengan notifikasi progress
- Install/update otomatis via `PackageInstaller` — Android mendeteksi apakah UPDATE atau INSTALL baru
- `FileProvider` dikonfigurasi untuk sharing URI APK (Android 7.0+)
- Permission `REQUEST_INSTALL_PACKAGES` ditambahkan di manifest

### Changed
- `versionCode` → 2
- `versionName` → "1.1.0"
- `MainActivity.kt` — ditambahkan integrasi `AppUpdateManager` dan `UpdateDialog`
- `AndroidManifest.xml` — ditambahkan `FileProvider` dan permission baru

### Documentation
- README.md diperbarui: fitur baru, struktur proyek, section Auto Update
- CHANGELOG.md diperbarui: entry v1.1.0
- copilot-instructions.md diperbarui: folder dan fitur baru

---

## [1.0.0] - 2026-02-26

**Tag:** `v1.0.0`  
**Release:** https://github.com/eltfd/Cuacaku/releases/tag/v1.0.0  
**APK:** `app-release.apk` (~2.0 MB, signed)

### Added — App Core
- Native Android weather app (Kotlin + Jetpack Compose, Material3)
- MVVM architecture with `WeatherViewModel`, `WeatherRepository`
- Open-Meteo API integration (current weather, hourly, 7-day forecast)
- Nominatim reverse geocoding and location search
- Retrofit + OkHttp networking layer with Gson converter
- GPS location detection via Fused Location Provider
- Pull-to-refresh and edge-to-edge UI
- Dark mode & dynamic color support (Android 12+)
- DataStore preferences for user settings
- Room database for offline weather caching

### Added — Notifications
- `NotificationChannels`: `weather_alerts` (HIGH), `daily_forecast` (DEFAULT)
- Daily forecast notification (morning)
- Severe weather alerts (WMO codes 65, 67, 75, 77, 82, 86, 95, 96, 99)
- Rain probability alert (≥ 60%)
- Temperature extreme alerts (≥ 35°C / ≤ 10°C)
- `WeatherUpdateWorker` — periodic 1-hour background update via WorkManager
- `WeatherUpdateService` — foreground service for reliable updates
- `BootReceiver` — restarts worker after device reboot

### Added — Build & CI/CD
- Gradle Kotlin DSL (`build.gradle.kts`), Gradle 8.5, JDK 17
- Compose BOM `2023.10.01` + Accompanist Permissions `0.32.0` (pinned for compatibility)
- `android-ci.yml` — CI workflow: build debug APK on every push/PR to `main`, upload as artifact
- `release-sign.yml` — Release workflow: manual dispatch with tag, decode base64 keystore from secret, build signed APK, create GitHub Release with artifact
- Signing config reads env vars: `RELEASE_KEYSTORE_FILE`, `RELEASE_STORE_PASSWORD`, `RELEASE_KEY_ALIAS`, `RELEASE_KEY_PASSWORD`
- Repository secrets: `SIGNING_KEY`, `RELEASE_KEY_ALIAS`, `RELEASE_KEY_PASSWORD`, `RELEASE_STORE_PASSWORD`

### Added — Documentation
- `README.md` — comprehensive project docs (features, arch, CI/CD, release, API, dev guide)
- `CHANGELOG.md` — this file
- `CONTRIBUTING.md` — contribution guidelines, branching, commit conventions, release process
- `TROUBLESHOOTING.md` — common errors and fixes (SDK, Gradle, Compose, CI/CD)
- `.github/copilot-instructions.md` — project context for AI-assisted development

### Added — Project Config
- `.gitignore` — Android/Kotlin tailored (build outputs, IDE files, local.properties)
- `gradle/wrapper/gradle-wrapper.jar` — committed to repo for CI runner compatibility
- Adaptive launcher icons (foreground + background vectors)
- Material3 theme (`Color.kt`, `Theme.kt`, `Type.kt`, `Shape.kt`)
- ProGuard rules for release builds

---

## Build & CI Fix History (for traceability)

These are the incremental CI/CD fixes applied during initial setup. They are part of v1.0.0.

| # | Commit Message | Problem | Fix |
|---|---------------|---------|-----|
| 1 | `chore: add gradle-wrapper.jar to repo` | Runner: "Could not find or load main class GradleWrapperMain" | Force-committed `gradle/wrapper/gradle-wrapper.jar` (was in `.gitignore`) |
| 2 | `ci: decode keystore into app/keystore.jks` | Keystore file not found at root path | Changed decode target to `app/keystore.jks` |
| 3 | `ci: use absolute keystore path for release signing` | Gradle resolved relative path under module dir → `app/app/keystore.jks` (double) | Set `RELEASE_KEYSTORE_FILE: ${{ github.workspace }}/app/keystore.jks` |
| 4 | `ci: pass dispatched tag to gh-release action` | `softprops/action-gh-release` error: "GitHub Releases requires a tag" | Added `tag_name: ${{ github.event.inputs.tag }}` |
| 5 | `ci: grant contents:write so gh-release can create tags` | Release action 403 Forbidden | Added `permissions: contents: write` to workflow |

---

## Version History

| Version | Date | Tag | Notes |
|---------|------|-----|-------|
| 1.1.0 | 2026-02-26 | `v1.1.0` | Hourly forecast per day + auto-update system |
| 1.0.0 | 2026-02-26 | `v1.0.0` | Initial release — full weather app with CI/CD |

---

## Future Updates (Planned)

- [ ] Widget (home screen widget for quick weather view)
- [ ] Multi-location support
- [ ] Hourly precipitation chart
- [ ] Air quality index (AQI) from Open-Meteo
- [x] ~~Hourly forecast detail per day~~ (done in v1.1.0)
- [x] ~~In-app auto-update~~ (done in v1.1.0)
- [ ] Localization (English / Bahasa Indonesia toggle)
- [ ] Unit tests for Repository and ViewModel
- [ ] Instrumented UI tests with Compose Testing

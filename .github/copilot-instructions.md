# Cuacaku - Development Guidelines

## Project Overview
- **Type**: Native Android (Kotlin)
- **UI**: Jetpack Compose + Material Design 3
- **Architecture**: MVVM
- **Data Sources**: Open-Meteo API (weather), Nominatim (geocoding), GitHub Releases API (auto-update)
- **Notifications**: WorkManager + NotificationManager
- **Auto Update**: GitHub Releases + DownloadManager + PackageInstaller

## Key Features
- Real-time weather display
- Hourly & 7-day forecast
- Expandable daily forecast with per-hour detail (24h interval)
- Location detection & search
- Comprehensive notification system:
  - Daily forecast
  - Severe weather alerts
  - Rain alerts
  - Temperature alerts
- In-app auto-update from GitHub Releases
  - Version comparison (semantic versioning)
  - APK download via DownloadManager
  - Automatic install/update detection by Android

## Project Structure
```
app/src/main/java/com/weather/forecast/
├── data/           # API, models, repository
│   ├── api/        # WeatherApi, GeocodingApi, GitHubApi
│   ├── model/      # Response & UI-ready models
│   ├── repository/ # Data source abstraction
│   └── preferences/ # DataStore
├── location/       # GPS handling
├── notification/   # Notification management
├── service/        # Foreground service
├── update/         # Auto-update (AppUpdateManager)
├── worker/         # Background tasks
├── receiver/       # Boot receiver
└── ui/             # Compose screens & components
    ├── components/ # WeatherIcon, UpdateDialog
    ├── screens/    # HomeScreen, SettingsScreen
    ├── viewmodel/  # WeatherViewModel
    ├── navigation/ # Navigation
    └── theme/      # Color, Theme, Type, Shape
```

## Build Instructions
1. Open project in Android Studio
2. Sync Gradle dependencies
3. Run on emulator or device

## API Reference
- Weather: https://api.open-meteo.com/v1/forecast
- Geocoding: https://nominatim.openstreetmap.org/
- GitHub Releases: https://api.github.com/repos/eltfd/Cuacaku/releases/latest

## Development Notes
- Min SDK: 26 (Android 8.0)
- Target SDK: 34 (Android 14)
- No API keys required
- Version: 1.1.0 (versionCode 2)

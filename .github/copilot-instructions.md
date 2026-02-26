# Weather Forecast App - Development Guidelines

## Project Overview
- **Type**: Native Android (Kotlin)
- **UI**: Jetpack Compose + Material Design 3
- **Architecture**: MVVM
- **Data Sources**: Open-Meteo API (weather), Nominatim (geocoding)
- **Notifications**: WorkManager + NotificationManager

## Key Features
- Real-time weather display
- Hourly & 7-day forecast
- Location detection & search
- Comprehensive notification system:
  - Daily forecast
  - Severe weather alerts
  - Rain alerts
  - Temperature alerts

## Project Structure
```
app/src/main/java/com/weather/forecast/
├── data/           # API, models, repository
├── location/       # GPS handling
├── notification/   # Notification management
├── service/        # Foreground service
├── worker/         # Background tasks
└── ui/             # Compose screens & components
```

## Build Instructions
1. Open project in Android Studio
2. Sync Gradle dependencies
3. Run on emulator or device

## API Reference
- Weather: https://api.open-meteo.com/v1/forecast
- Geocoding: https://nominatim.openstreetmap.org/

## Development Notes
- Min SDK: 26 (Android 8.0)
- Target SDK: 34 (Android 14)
- No API keys required

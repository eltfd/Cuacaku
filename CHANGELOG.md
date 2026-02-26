# Changelog

All notable changes to this project are documented in this file.

## [Unreleased] - 2026-02-26
- Scaffolded full Android app (Kotlin + Jetpack Compose)
- Added Open-Meteo and Nominatim API clients
- Implemented WorkManager, Foreground Service, and Notification system
- Initial build attempts and environment fixes

## 2026-02-26
- Installed OpenJDK 17 and Android command-line tools on development machine
- Fixed `gradle-wrapper.jar` regeneration and validated Gradle wrapper (8.5)
- Built debug APK: `app/build/outputs/apk/debug/app-debug.apk`
- Fixed runtime crash due to Compose library mismatch by pinning Compose BOM and accompanist:
  - Compose BOM: `androidx.compose:compose-bom:2023.10.01`
  - `accompanist-permissions` pinned to `0.32.0`
- Replaced unavailable icons and fixed Compose UI issues (`clickable`, `Divider` usage)
- Added proper adaptive launcher icons and theme adjustments

## Notes
- If you modify Compose libraries or accompanist, verify runtime compatibility between `material3`, `animation-core`, and accompanist versions.

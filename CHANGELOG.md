# Changelog

All notable changes to this project are documented in this file.  
Format: [Keep a Changelog](https://keepachangelog.com/en/1.1.0/) + [Semantic Versioning](https://semver.org/).

---

## [1.0.0] - 2026-02-26

**Tag:** `v1.0.0`  
**Release:** https://github.com/eltfd/weather-forecast-app/releases/tag/v1.0.0  
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
| 1.0.0 | 2026-02-26 | `v1.0.0` | Initial release — full weather app with CI/CD |

---

## Future Updates (Planned)

- [ ] Widget (home screen widget for quick weather view)
- [ ] Multi-location support
- [ ] Hourly precipitation chart
- [ ] Air quality index (AQI) from Open-Meteo
- [ ] Localization (English / Bahasa Indonesia toggle)
- [ ] Unit tests for Repository and ViewModel
- [ ] Instrumented UI tests with Compose Testing

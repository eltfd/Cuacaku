package com.weather.forecast.worker

import android.content.Context
import androidx.work.*
import com.weather.forecast.data.model.RiskLevel
import com.weather.forecast.data.model.WeatherCondition
import com.weather.forecast.data.preferences.PreferencesManager
import com.weather.forecast.data.repository.DisasterRepository
import com.weather.forecast.data.repository.SeismicRepository
import com.weather.forecast.data.repository.WeatherRepository
import com.weather.forecast.location.LocationManager
import com.weather.forecast.notification.WeatherNotificationManager
import com.weather.forecast.service.SOSManager
import kotlinx.coroutines.flow.first
import java.util.concurrent.TimeUnit

/**
 * Weather Update Worker
 * 
 * WorkManager worker untuk update cuaca di background.
 * Berjalan secara periodik untuk check:
 * - Severe weather alerts
 * - Rain probability alerts
 * - Temperature alerts
 * - **Weather risk alerts (HIGH → vibration, EXTREME → emergency SOS vibration)**
 * - **Disaster risk alerts (AI-based: flood, landslide, cyclone, thunderstorm, subsidence)**
 */
class WeatherUpdateWorker(
    context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    private val weatherRepository = WeatherRepository()
    private val disasterRepository = DisasterRepository(context)
    private val seismicRepository = SeismicRepository(context)
    private val locationManager = LocationManager(context)
    private val sosManager = SOSManager(context)
    private val notificationManager = WeatherNotificationManager(context)
    private val preferencesManager = PreferencesManager(context)

    override suspend fun doWork(): Result {
        return try {
            val preferences = preferencesManager.userPreferences.first()
            
            if (!preferences.notificationsEnabled) {
                return Result.success()
            }

            // Get location
            val location = when {
                locationManager.hasLocationPermission() -> {
                    locationManager.getLocationWithFallback()
                }
                preferences.lastLatitude != null && preferences.lastLongitude != null -> {
                    android.location.Location("").apply {
                        latitude = preferences.lastLatitude
                        longitude = preferences.lastLongitude
                    }
                }
                else -> null
            }

            if (location == null) {
                return Result.retry()
            }

            // Record location for SOS movement tracking
            sosManager.recordLocation(location.latitude, location.longitude)

            // Fetch weather
            val result = weatherRepository.getWeatherData(location.latitude, location.longitude)
            
            result.onSuccess { weatherData ->
                // Severe weather alert (WMO code based)
                if (preferences.severeWeatherAlert &&
                    WeatherCondition.isSevereWeather(weatherData.current.weatherCode)) {
                    notificationManager.sendSevereWeatherAlert(
                        locationName = weatherData.location.name,
                        weatherCode = weatherData.current.weatherCode,
                        description = weatherData.current.weatherCondition.descriptionId
                    )
                }

                // ── Weather Potential Risk Alert ─────────────────────
                // Hitung potensi cuaca dari data per jam 24 jam ke depan.
                // Jika ada risiko HIGH → notif + getar.
                // Jika ada risiko EXTREME → notif darurat + getar SOS.
                if (preferences.severeWeatherAlert) {
                    val potential = weatherData.currentPotential
                        ?: weatherRepository.calculateCurrentPotential(
                            weatherData.hourly,
                            weatherData.current.weatherCode,
                            weatherData.current.windGusts
                        )

                    val maxRisk = listOf(
                        potential.stormRisk,
                        potential.heavyRainRisk,
                        potential.hailRisk,
                        potential.strongWindRisk,
                        potential.tornadoRisk
                    ).maxByOrNull { it.ordinal } ?: RiskLevel.LOW

                    if (maxRisk == RiskLevel.HIGH || maxRisk == RiskLevel.EXTREME) {
                        notificationManager.sendWeatherRiskAlert(
                            locationName = weatherData.location.name,
                            potential = potential
                        )
                    }
                }

                // Rain alert
                if (preferences.rainAlert) {
                    weatherData.hourly.firstOrNull { it.precipitationProbability >= 60 }?.let {
                        notificationManager.sendRainAlert(
                            locationName = weatherData.location.name,
                            precipitationProbability = it.precipitationProbability,
                            expectedTime = it.hour
                        )
                    }
                }

                // Temperature alert
                if (preferences.temperatureAlert) {
                    val temp = weatherData.current.temperature
                    when {
                        temp >= 35 -> notificationManager.sendTemperatureAlert(
                            weatherData.location.name, temp, isHigh = true
                        )
                        temp <= 10 -> notificationManager.sendTemperatureAlert(
                            weatherData.location.name, temp, isHigh = false
                        )
                    }
                }

                // ── Disaster Prediction Alert ────────────────────────
                // Jalankan analisis AI untuk 6 jenis bencana.
                // Kirim notifikasi jika ada risiko HIGH atau EXTREME.
                // EXTREME → getaran SOS + bypass DND.
                if (preferences.disasterAlert) {
                    try {
                        val disasterResult = disasterRepository.getDisasterForecast(
                            location.latitude,
                            location.longitude
                        )

                        disasterResult.onSuccess { forecast ->
                            val dangerousPredictions = forecast.todayPredictions.filter {
                                it.riskLevel == RiskLevel.HIGH || it.riskLevel == RiskLevel.EXTREME
                            }

                            if (dangerousPredictions.isNotEmpty()) {
                                notificationManager.sendDisasterRiskAlert(
                                    locationName = weatherData.location.name,
                                    predictions = dangerousPredictions
                                )
                            }
                        }
                    } catch (_: Exception) {
                        // Silently fail — disaster analysis is best-effort
                    }
                }

                // ── Seismic Monitoring Alert ─────────────────────────
                // Check for significant earthquakes, tsunami risk,
                // volcanic activity, and high wave warnings.
                try {
                    val seismicResult = seismicRepository.getSeismicMonitorData(
                        location.latitude,
                        location.longitude
                    )

                    seismicResult.onSuccess { seismicData ->
                        val locationName = weatherData.location.name

                        // Earthquake alerts — nearby M4.0+
                        seismicData.nearbyEarthquakes
                            .filter { it.magnitude >= 4.0 }
                            .maxByOrNull { it.magnitude }
                            ?.let { eq ->
                                notificationManager.sendEarthquakeAlert(locationName, eq)
                            }

                        // Tsunami alerts
                        if (seismicData.tsunamiRisk.isAtRisk) {
                            notificationManager.sendTsunamiAlert(
                                locationName, seismicData.tsunamiRisk
                            )
                        }

                        // Volcano alerts — nearby with ADVISORY+
                        seismicData.volcanicActivity
                            .filter { it.isNearby && it.alertLevel.level >= 1 }
                            .maxByOrNull { it.alertLevel.level }
                            ?.let { volcano ->
                                notificationManager.sendVolcanoAlert(locationName, volcano)
                            }

                        // High wave warnings — no vibration
                        seismicData.highWaveWarning?.let { wave ->
                            if (wave.warningLevel.level >= 2) {
                                notificationManager.sendHighWaveAlert(locationName, wave)
                            }
                        }
                    }
                } catch (_: Exception) {
                    // Silently fail — seismic monitoring is best-effort
                }
            }

            Result.success()
        } catch (e: Exception) {
            Result.retry()
        }
    }

    companion object {
        const val WORK_NAME = "weather_update_worker"
    }
}

/**
 * Scheduler untuk WeatherUpdateWorker
 */
object WeatherWorkerScheduler {

    /**
     * Schedule periodic weather update (setiap 1 jam)
     */
    fun schedulePeriodicWeatherUpdate(context: Context) {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val workRequest = PeriodicWorkRequestBuilder<WeatherUpdateWorker>(
            repeatInterval = 1,
            repeatIntervalTimeUnit = TimeUnit.HOURS
        )
            .setConstraints(constraints)
            .setBackoffCriteria(
                BackoffPolicy.EXPONENTIAL,
                WorkRequest.MIN_BACKOFF_MILLIS,
                TimeUnit.MILLISECONDS
            )
            .build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            WeatherUpdateWorker.WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            workRequest
        )
    }

    /**
     * Cancel scheduled weather updates
     */
    fun cancelWeatherUpdates(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork(WeatherUpdateWorker.WORK_NAME)
    }

    /**
     * Schedule one-time weather update
     */
    fun scheduleOneTimeUpdate(context: Context) {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val workRequest = OneTimeWorkRequestBuilder<WeatherUpdateWorker>()
            .setConstraints(constraints)
            .build()

        WorkManager.getInstance(context).enqueue(workRequest)
    }

    // ════════════════════════════════════════════════
    //  AI Learning Worker — Silent Background Learning
    // ════════════════════════════════════════════════

    /**
     * Schedule periodic AI learning (setiap 12 jam).
     *
     * Constraints lebih ketat dari weather update karena learning
     * tidak urgent — user tidak perlu hasilnya segera:
     * - Requires network (untuk fetch cuaca terbaru)
     * - Requires battery not low (hemat daya)
     *
     * Interval 12 jam dipilih karena:
     * - Cuaca berubah signifikan dalam 12 jam (pagi ↔ malam)
     * - Cukup sering untuk maintain akurasi
     * - Cukup jarang untuk tidak boros resource
     */
    fun scheduleAiLearning(context: Context) {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .setRequiresBatteryNotLow(true)
            .build()

        val workRequest = PeriodicWorkRequestBuilder<AiLearningWorker>(
            repeatInterval = 12,
            repeatIntervalTimeUnit = TimeUnit.HOURS
        )
            .setConstraints(constraints)
            .setBackoffCriteria(
                BackoffPolicy.EXPONENTIAL,
                WorkRequest.MIN_BACKOFF_MILLIS,
                TimeUnit.MILLISECONDS
            )
            .build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            AiLearningWorker.WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            workRequest
        )
    }

    /**
     * Cancel scheduled AI learning
     */
    fun cancelAiLearning(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork(AiLearningWorker.WORK_NAME)
    }
}

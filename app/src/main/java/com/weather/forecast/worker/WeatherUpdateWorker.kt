package com.weather.forecast.worker

import android.content.Context
import androidx.work.*
import com.weather.forecast.data.model.WeatherCondition
import com.weather.forecast.data.preferences.PreferencesManager
import com.weather.forecast.data.repository.WeatherRepository
import com.weather.forecast.location.LocationManager
import com.weather.forecast.notification.WeatherNotificationManager
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
 */
class WeatherUpdateWorker(
    context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    private val weatherRepository = WeatherRepository()
    private val locationManager = LocationManager(context)
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
                        latitude = preferences.lastLatitude!!
                        longitude = preferences.lastLongitude!!
                    }
                }
                else -> null
            }

            if (location == null) {
                return Result.retry()
            }

            // Fetch weather
            val result = weatherRepository.getWeatherData(location.latitude, location.longitude)
            
            result.onSuccess { weatherData ->
                // Severe weather alert
                if (preferences.severeWeatherAlert &&
                    WeatherCondition.isSevereWeather(weatherData.current.weatherCode)) {
                    notificationManager.sendSevereWeatherAlert(
                        locationName = weatherData.location.name,
                        weatherCode = weatherData.current.weatherCode,
                        description = weatherData.current.weatherCondition.descriptionId
                    )
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
}

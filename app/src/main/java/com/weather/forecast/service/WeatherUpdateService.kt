package com.weather.forecast.service

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.weather.forecast.MainActivity
import com.weather.forecast.R
import com.weather.forecast.data.locale.AppLocaleManager
import com.weather.forecast.data.model.WeatherCondition
import com.weather.forecast.data.preferences.PreferencesManager
import com.weather.forecast.data.repository.WeatherRepository
import com.weather.forecast.location.LocationManager
import com.weather.forecast.notification.NotificationChannels
import com.weather.forecast.notification.NotificationIds
import com.weather.forecast.notification.WeatherNotificationManager
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first

/**
 * Weather Update Foreground Service
 * 
 * Service ini berjalan di background untuk:
 * - Update data cuaca secara periodik
 * - Mengirim notifikasi peringatan cuaca
 * - Monitoring severe weather conditions
 */
class WeatherUpdateService : Service() {

    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private lateinit var weatherRepository: WeatherRepository
    private lateinit var locationManager: LocationManager
    private lateinit var notificationManager: WeatherNotificationManager
    private lateinit var preferencesManager: PreferencesManager

    override fun onCreate() {
        super.onCreate()
        weatherRepository = WeatherRepository()
        locationManager = LocationManager(this)
        notificationManager = WeatherNotificationManager(this)
        preferencesManager = PreferencesManager(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(NotificationIds.WEATHER_UPDATE_SERVICE, createForegroundNotification())
        
        serviceScope.launch {
            updateWeatherAndNotify()
            stopSelf()
        }

        return START_NOT_STICKY
    }

    /**
     * Update weather data dan kirim notifikasi jika diperlukan
     */
    private suspend fun updateWeatherAndNotify() {
        try {
            val preferences = preferencesManager.userPreferences.first()
            
            // Get location
            val location = if (locationManager.hasLocationPermission()) {
                locationManager.getLocationWithFallback()
            } else {
                // Use last known location from preferences
                if (preferences.lastLatitude != null && preferences.lastLongitude != null) {
                    android.location.Location("").apply {
                        latitude = preferences.lastLatitude
                        longitude = preferences.lastLongitude
                    }
                } else {
                    null
                }
            }

            if (location == null) return

            // Fetch weather data
            val result = weatherRepository.getWeatherData(location.latitude, location.longitude)
            
            result.onSuccess { weatherData ->
                // Check for severe weather
                if (preferences.severeWeatherAlert && 
                    WeatherCondition.isSevereWeather(weatherData.current.weatherCode)) {
                    notificationManager.sendSevereWeatherAlert(
                        locationName = weatherData.location.name,
                        weatherCode = weatherData.current.weatherCode,
                        description = AppLocaleManager.strings.localized(
                            weatherData.current.weatherCondition.description,
                            weatherData.current.weatherCondition.descriptionId
                        )
                    )
                }

                // Check for rain
                if (preferences.rainAlert) {
                    val upcomingRain = weatherData.hourly.firstOrNull { 
                        it.precipitationProbability >= 50 
                    }
                    upcomingRain?.let {
                        notificationManager.sendRainAlert(
                            locationName = weatherData.location.name,
                            precipitationProbability = it.precipitationProbability,
                            expectedTime = it.hour
                        )
                    }
                }

                // Check for extreme temperature
                if (preferences.temperatureAlert) {
                    val temp = weatherData.current.temperature
                    when {
                        temp >= 35 -> notificationManager.sendTemperatureAlert(
                            locationName = weatherData.location.name,
                            temperature = temp,
                            isHigh = true
                        )
                        temp <= 10 -> notificationManager.sendTemperatureAlert(
                            locationName = weatherData.location.name,
                            temperature = temp,
                            isHigh = false
                        )
                    }
                }

                // Daily forecast notification
                if (preferences.dailyNotificationEnabled) {
                    notificationManager.sendDailyForecastNotification(
                        locationName = weatherData.location.name,
                        current = weatherData.current,
                        today = weatherData.daily.firstOrNull()
                    )
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * Create foreground notification untuk service
     */
    private fun createForegroundNotification(): Notification {
        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, NotificationChannels.DAILY_FORECAST)
            .setSmallIcon(R.drawable.ic_weather_splash)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(AppLocaleManager.strings.notifUpdatingWeather)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
    }
}

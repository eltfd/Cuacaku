package com.weather.forecast

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import com.weather.forecast.notification.NotificationChannels

/**
 * Application class untuk Weather Forecast App
 * 
 * Responsibilities:
 * - Initialize notification channels
 * - Setup dependency injection (manual for simplicity)
 * - Configure WorkManager for background tasks
 */
class WeatherApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        instance = this
        createNotificationChannels()
    }

    /**
     * Create notification channels untuk Android 8.0+
     * Channels:
     * - Weather Alerts: High priority untuk cuaca ekstrem
     * - Daily Forecast: Default priority untuk notifikasi harian
     */
    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager = getSystemService(NotificationManager::class.java)

            // Weather Alerts Channel - High Priority
            val alertChannel = NotificationChannel(
                NotificationChannels.WEATHER_ALERTS,
                getString(R.string.channel_weather_alerts),
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = getString(R.string.channel_weather_alerts_desc)
                enableVibration(true)
                enableLights(true)
            }

            // Daily Forecast Channel - Default Priority
            val dailyChannel = NotificationChannel(
                NotificationChannels.DAILY_FORECAST,
                getString(R.string.channel_daily_forecast),
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = getString(R.string.channel_daily_forecast_desc)
            }

            notificationManager.createNotificationChannels(listOf(alertChannel, dailyChannel))
        }
    }

    companion object {
        lateinit var instance: WeatherApplication
            private set
    }
}

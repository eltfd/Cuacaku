package com.weather.forecast

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.media.AudioAttributes
import android.os.Build
import com.weather.forecast.data.locale.AppLocaleManager
import com.weather.forecast.notification.NotificationChannels
import com.weather.forecast.worker.WeatherWorkerScheduler

/**
 * Application class untuk Cuacaku App
 * 
 * Responsibilities:
 * - Initialize notification channels
 * - Setup dependency injection (manual for simplicity)
 * - Configure WorkManager for background tasks
 * - Schedule AI learning worker (silent, invisible to user)
 */
class WeatherApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        instance = this
        AppLocaleManager.init(this)
        createNotificationChannels()
        scheduleBackgroundTasks()
    }

    /**
     * Schedule background workers.
     * AI Learning berjalan setiap 12 jam secara silent — user tidak tahu.
     */
    private fun scheduleBackgroundTasks() {
        WeatherWorkerScheduler.scheduleAiLearning(this)
    }

    /**
     * Create notification channels untuk Android 8.0+
     * Channels:
     * - Weather Alerts: High priority untuk cuaca ekstrem
     * - Daily Forecast: Default priority untuk notifikasi harian
     * - Extreme Weather Emergency: Max priority, bypass DND, aggressive vibration
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

            // Extreme Weather Emergency Channel — Max priority, aggressive vibration
            // Pola getaran SOS-like: jeda-getar-jeda-getar-jeda-getar…
            val extremeVibrationPattern = longArrayOf(
                0, 500, 200, 500, 200, 500,   // 3× getar pendek (S)
                400, 1000, 200, 1000, 200, 1000, // 3× getar panjang (O)
                400, 500, 200, 500, 200, 500   // 3× getar pendek (S)
            )

            val emergencyChannel = NotificationChannel(
                NotificationChannels.EXTREME_WEATHER_EMERGENCY,
                getString(R.string.channel_extreme_emergency),
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = getString(R.string.channel_extreme_emergency_desc)
                enableVibration(true)
                vibrationPattern = extremeVibrationPattern
                enableLights(true)
                lightColor = android.graphics.Color.RED
                setBypassDnd(true)
                lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
                setSound(
                    android.provider.Settings.System.DEFAULT_ALARM_ALERT_URI,
                    AudioAttributes.Builder()
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .build()
                )
            }

            notificationManager.createNotificationChannels(
                listOf(alertChannel, dailyChannel, emergencyChannel)
            )
        }
    }

    companion object {
        lateinit var instance: WeatherApplication
            private set
    }
}

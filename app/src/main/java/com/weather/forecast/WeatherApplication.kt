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

            // Disaster Alerts Channel — AI-based disaster predictions (HIGH risk)
            val disasterAlertChannel = NotificationChannel(
                NotificationChannels.DISASTER_ALERTS,
                "Peringatan Bencana / Disaster Alerts",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Peringatan potensi bencana dari analisis AI (longsor, banjir, siklon, dll)"
                enableVibration(true)
                enableLights(true)
                lightColor = android.graphics.Color.RED
            }

            // Disaster Emergency Channel — EXTREME risk, aggressive SOS vibration
            val disasterEmergencyChannel = NotificationChannel(
                NotificationChannels.DISASTER_EMERGENCY,
                "Darurat Bencana / Disaster Emergency",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Peringatan darurat bencana level EKSTREM — getaran SOS agresif"
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

            // Earthquake Alerts Channel — seismic activity alerts
            val earthquakeAlertChannel = NotificationChannel(
                NotificationChannels.EARTHQUAKE_ALERTS,
                "Peringatan Gempa / Earthquake Alerts",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Peringatan gempa bumi di sekitar lokasi Anda"
                enableVibration(true)
                enableLights(true)
                lightColor = android.graphics.Color.RED
            }

            // Tsunami Emergency Channel — bypass DND, SOS vibration
            val tsunamiEmergencyChannel = NotificationChannel(
                NotificationChannels.TSUNAMI_EMERGENCY,
                "Darurat Tsunami / Tsunami Emergency",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Peringatan darurat tsunami — getaran SOS agresif, bypass DND"
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

            // Volcano Alerts Channel
            val volcanoAlertChannel = NotificationChannel(
                NotificationChannels.VOLCANO_ALERTS,
                "Peringatan Gunung Api / Volcano Alerts",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Peringatan aktivitas gunung berapi di sekitar lokasi Anda"
                enableVibration(true)
                enableLights(true)
                lightColor = android.graphics.Color.RED
            }

            // High Wave Alerts Channel — NO vibration (maritime safety, not disaster)
            val highWaveAlertChannel = NotificationChannel(
                NotificationChannels.HIGH_WAVE_ALERTS,
                "Peringatan Gelombang Tinggi / High Wave Alerts",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Peringatan gelombang tinggi untuk keselamatan pelaut dan nelayan"
                enableVibration(false)
                enableLights(true)
                lightColor = android.graphics.Color.BLUE
            }

            notificationManager.createNotificationChannels(
                listOf(
                    alertChannel, dailyChannel, emergencyChannel,
                    disasterAlertChannel, disasterEmergencyChannel,
                    earthquakeAlertChannel, tsunamiEmergencyChannel,
                    volcanoAlertChannel, highWaveAlertChannel
                )
            )
        }
    }

    companion object {
        lateinit var instance: WeatherApplication
            private set
    }
}

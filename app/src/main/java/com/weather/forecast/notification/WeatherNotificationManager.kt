package com.weather.forecast.notification

import android.Manifest
import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.weather.forecast.MainActivity
import com.weather.forecast.R
import com.weather.forecast.data.model.CurrentWeatherData
import com.weather.forecast.data.model.DailyWeatherData
import com.weather.forecast.data.model.WeatherCondition

/**
 * Weather Notification Manager
 * 
 * Mengelola semua notifikasi cuaca:
 * - Daily forecast notification
 * - Severe weather alerts
 * - Rain alerts
 * - Temperature alerts
 */
class WeatherNotificationManager(private val context: Context) {

    private val notificationManager = NotificationManagerCompat.from(context)

    /**
     * Check if notification permission granted (Android 13+)
     */
    fun hasNotificationPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    /**
     * Send daily forecast notification
     */
    fun sendDailyForecastNotification(
        locationName: String,
        current: CurrentWeatherData,
        today: DailyWeatherData?
    ) {
        if (!hasNotificationPermission()) return

        val title = "Prakiraan Cuaca Hari Ini"
        val content = buildString {
            append("$locationName: ${current.temperatureFormatted}")
            append(" • ${current.weatherCondition.descriptionId}")
            today?.let {
                append("\nMaks: ${it.temperatureMaxFormatted} / Min: ${it.temperatureMinFormatted}")
                if (it.precipitationProbabilityMax > 30) {
                    append(" • Hujan: ${it.precipitationProbabilityFormatted}")
                }
            }
        }

        val notification = createNotification(
            channelId = NotificationChannels.DAILY_FORECAST,
            title = title,
            content = content,
            priority = NotificationCompat.PRIORITY_DEFAULT
        )

        notificationManager.notify(NotificationIds.DAILY_FORECAST, notification)
    }

    /**
     * Send severe weather alert notification
     */
    fun sendSevereWeatherAlert(
        locationName: String,
        weatherCode: Int,
        description: String
    ) {
        if (!hasNotificationPermission()) return
        if (!WeatherCondition.isSevereWeather(weatherCode)) return

        val title = "⚠️ Peringatan Cuaca Ekstrem"
        val content = "$locationName: $description. Harap berhati-hati!"

        val notification = createNotification(
            channelId = NotificationChannels.WEATHER_ALERTS,
            title = title,
            content = content,
            priority = NotificationCompat.PRIORITY_HIGH
        )

        notificationManager.notify(NotificationIds.SEVERE_WEATHER, notification)
    }

    /**
     * Send rain alert notification
     */
    fun sendRainAlert(
        locationName: String,
        precipitationProbability: Int,
        expectedTime: String
    ) {
        if (!hasNotificationPermission()) return
        if (precipitationProbability < 50) return

        val title = "🌧️ Peringatan Hujan"
        val content = "$locationName: Kemungkinan hujan $precipitationProbability% sekitar $expectedTime"

        val notification = createNotification(
            channelId = NotificationChannels.WEATHER_ALERTS,
            title = title,
            content = content,
            priority = NotificationCompat.PRIORITY_DEFAULT
        )

        notificationManager.notify(NotificationIds.RAIN_ALERT, notification)
    }

    /**
     * Send temperature alert notification
     */
    fun sendTemperatureAlert(
        locationName: String,
        temperature: Double,
        isHigh: Boolean
    ) {
        if (!hasNotificationPermission()) return

        val title = if (isHigh) "🌡️ Suhu Tinggi" else "❄️ Suhu Rendah"
        val content = "$locationName: Suhu saat ini ${temperature.toInt()}°C. " +
            if (isHigh) "Hindari paparan sinar matahari langsung." else "Gunakan pakaian hangat."

        val notification = createNotification(
            channelId = NotificationChannels.WEATHER_ALERTS,
            title = title,
            content = content,
            priority = NotificationCompat.PRIORITY_DEFAULT
        )

        notificationManager.notify(NotificationIds.TEMPERATURE_ALERT, notification)
    }

    /**
     * Create notification with common settings
     */
    private fun createNotification(
        channelId: String,
        title: String,
        content: String,
        priority: Int
    ): Notification {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        
        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.ic_weather_splash)
            .setContentTitle(title)
            .setContentText(content)
            .setStyle(NotificationCompat.BigTextStyle().bigText(content))
            .setPriority(priority)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()
    }

    /**
     * Cancel specific notification
     */
    fun cancelNotification(notificationId: Int) {
        notificationManager.cancel(notificationId)
    }

    /**
     * Cancel all weather notifications
     */
    fun cancelAllNotifications() {
        notificationManager.cancelAll()
    }
}

package com.weather.forecast.notification

/**
 * Notification Channel IDs
 * 
 * Digunakan untuk mengelompokkan notifikasi berdasarkan tipe:
 * - WEATHER_ALERTS: Peringatan cuaca ekstrem (high priority)
 * - DAILY_FORECAST: Prakiraan cuaca harian (default priority)
 */
object NotificationChannels {
    const val WEATHER_ALERTS = "weather_alerts"
    const val DAILY_FORECAST = "daily_forecast"
}

/**
 * Notification IDs
 * 
 * Unique ID untuk setiap jenis notifikasi agar bisa di-update atau cancel
 */
object NotificationIds {
    const val DAILY_FORECAST = 1001
    const val RAIN_ALERT = 1002
    const val SEVERE_WEATHER = 1003
    const val TEMPERATURE_ALERT = 1004
    const val WEATHER_UPDATE_SERVICE = 2001
}

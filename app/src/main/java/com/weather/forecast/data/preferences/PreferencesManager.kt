package com.weather.forecast.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException

/**
 * User Preferences Manager
 * 
 * Mengelola pengaturan user menggunakan DataStore.
 * 
 * Settings yang disimpan:
 * - Temperature unit (Celsius/Fahrenheit)
 * - Wind speed unit (km/h / mph)
 * - Notification preferences
 * - Last known location
 */

// Extension untuk DataStore
private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "weather_preferences")

class PreferencesManager(private val context: Context) {

    // Preference Keys
    private object Keys {
        val TEMPERATURE_UNIT = stringPreferencesKey("temperature_unit")
        val WIND_SPEED_UNIT = stringPreferencesKey("wind_speed_unit")
        val NOTIFICATIONS_ENABLED = booleanPreferencesKey("notifications_enabled")
        val DAILY_NOTIFICATION_ENABLED = booleanPreferencesKey("daily_notification_enabled")
        val DAILY_NOTIFICATION_HOUR = intPreferencesKey("daily_notification_hour")
        val DAILY_NOTIFICATION_MINUTE = intPreferencesKey("daily_notification_minute")
        val SEVERE_WEATHER_ALERT = booleanPreferencesKey("severe_weather_alert")
        val RAIN_ALERT = booleanPreferencesKey("rain_alert")
        val TEMPERATURE_ALERT = booleanPreferencesKey("temperature_alert")
        val LAST_LATITUDE = doublePreferencesKey("last_latitude")
        val LAST_LONGITUDE = doublePreferencesKey("last_longitude")
        val LAST_LOCATION_NAME = stringPreferencesKey("last_location_name")
    }

    /**
     * User preferences sebagai Flow
     */
    val userPreferences: Flow<UserPreferences> = context.dataStore.data
        .catch { exception ->
            if (exception is IOException) {
                emit(emptyPreferences())
            } else {
                throw exception
            }
        }
        .map { preferences ->
            UserPreferences(
                temperatureUnit = TemperatureUnit.valueOf(
                    preferences[Keys.TEMPERATURE_UNIT] ?: TemperatureUnit.CELSIUS.name
                ),
                windSpeedUnit = WindSpeedUnit.valueOf(
                    preferences[Keys.WIND_SPEED_UNIT] ?: WindSpeedUnit.KMH.name
                ),
                notificationsEnabled = preferences[Keys.NOTIFICATIONS_ENABLED] ?: true,
                dailyNotificationEnabled = preferences[Keys.DAILY_NOTIFICATION_ENABLED] ?: false,
                dailyNotificationHour = preferences[Keys.DAILY_NOTIFICATION_HOUR] ?: 7,
                dailyNotificationMinute = preferences[Keys.DAILY_NOTIFICATION_MINUTE] ?: 0,
                severeWeatherAlert = preferences[Keys.SEVERE_WEATHER_ALERT] ?: true,
                rainAlert = preferences[Keys.RAIN_ALERT] ?: true,
                temperatureAlert = preferences[Keys.TEMPERATURE_ALERT] ?: false,
                lastLatitude = preferences[Keys.LAST_LATITUDE],
                lastLongitude = preferences[Keys.LAST_LONGITUDE],
                lastLocationName = preferences[Keys.LAST_LOCATION_NAME]
            )
        }

    /**
     * Update temperature unit
     */
    suspend fun updateTemperatureUnit(unit: TemperatureUnit) {
        context.dataStore.edit { preferences ->
            preferences[Keys.TEMPERATURE_UNIT] = unit.name
        }
    }

    /**
     * Update wind speed unit
     */
    suspend fun updateWindSpeedUnit(unit: WindSpeedUnit) {
        context.dataStore.edit { preferences ->
            preferences[Keys.WIND_SPEED_UNIT] = unit.name
        }
    }

    /**
     * Update notification settings
     */
    suspend fun updateNotificationSettings(
        enabled: Boolean? = null,
        dailyEnabled: Boolean? = null,
        dailyHour: Int? = null,
        dailyMinute: Int? = null,
        severeWeatherAlert: Boolean? = null,
        rainAlert: Boolean? = null,
        temperatureAlert: Boolean? = null
    ) {
        context.dataStore.edit { preferences ->
            enabled?.let { preferences[Keys.NOTIFICATIONS_ENABLED] = it }
            dailyEnabled?.let { preferences[Keys.DAILY_NOTIFICATION_ENABLED] = it }
            dailyHour?.let { preferences[Keys.DAILY_NOTIFICATION_HOUR] = it }
            dailyMinute?.let { preferences[Keys.DAILY_NOTIFICATION_MINUTE] = it }
            severeWeatherAlert?.let { preferences[Keys.SEVERE_WEATHER_ALERT] = it }
            rainAlert?.let { preferences[Keys.RAIN_ALERT] = it }
            temperatureAlert?.let { preferences[Keys.TEMPERATURE_ALERT] = it }
        }
    }

    /**
     * Save last known location
     */
    suspend fun saveLastLocation(latitude: Double, longitude: Double, name: String) {
        context.dataStore.edit { preferences ->
            preferences[Keys.LAST_LATITUDE] = latitude
            preferences[Keys.LAST_LONGITUDE] = longitude
            preferences[Keys.LAST_LOCATION_NAME] = name
        }
    }
}

/**
 * User preferences data class
 */
data class UserPreferences(
    val temperatureUnit: TemperatureUnit = TemperatureUnit.CELSIUS,
    val windSpeedUnit: WindSpeedUnit = WindSpeedUnit.KMH,
    val notificationsEnabled: Boolean = true,
    val dailyNotificationEnabled: Boolean = false,
    val dailyNotificationHour: Int = 7,
    val dailyNotificationMinute: Int = 0,
    val severeWeatherAlert: Boolean = true,
    val rainAlert: Boolean = true,
    val temperatureAlert: Boolean = false,
    val lastLatitude: Double? = null,
    val lastLongitude: Double? = null,
    val lastLocationName: String? = null
)

enum class TemperatureUnit {
    CELSIUS, FAHRENHEIT
}

enum class WindSpeedUnit {
    KMH, MPH
}

/**
 * Extension functions untuk konversi unit
 */
fun Double.toCelsius(): Double = this
fun Double.toFahrenheit(): Double = (this * 9 / 5) + 32
fun Double.toKmh(): Double = this
fun Double.toMph(): Double = this * 0.621371

fun Double.formatTemperature(unit: TemperatureUnit): String {
    val value = when (unit) {
        TemperatureUnit.CELSIUS -> this.toCelsius()
        TemperatureUnit.FAHRENHEIT -> this.toFahrenheit()
    }
    val symbol = when (unit) {
        TemperatureUnit.CELSIUS -> "°C"
        TemperatureUnit.FAHRENHEIT -> "°F"
    }
    return "${value.toInt()}$symbol"
}

fun Double.formatWindSpeed(unit: WindSpeedUnit): String {
    val value = when (unit) {
        WindSpeedUnit.KMH -> this.toKmh()
        WindSpeedUnit.MPH -> this.toMph()
    }
    val symbol = when (unit) {
        WindSpeedUnit.KMH -> "km/h"
        WindSpeedUnit.MPH -> "mph"
    }
    return "${value.toInt()} $symbol"
}

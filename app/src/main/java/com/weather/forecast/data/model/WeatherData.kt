package com.weather.forecast.data.model

/**
 * UI-friendly weather data models
 * 
 * Models ini digunakan untuk menampilkan data di UI,
 * sudah diproses dari API response.
 */

/**
 * Data cuaca lengkap untuk satu lokasi
 */
data class WeatherData(
    val location: LocationInfo,
    val current: CurrentWeatherData,
    val hourly: List<HourlyWeatherData>,
    val daily: List<DailyWeatherData>,
    val lastUpdated: Long = System.currentTimeMillis()
)

/**
 * Info lokasi
 */
data class LocationInfo(
    val latitude: Double,
    val longitude: Double,
    val name: String,
    val fullName: String,
    val timezone: String
)

/**
 * Data cuaca saat ini (UI-ready)
 */
data class CurrentWeatherData(
    val temperature: Double,
    val apparentTemperature: Double,
    val humidity: Int,
    val weatherCode: Int,
    val weatherCondition: WeatherCondition,
    val windSpeed: Double,
    val windDirection: Int,
    val windGusts: Double,
    val pressure: Double,
    val cloudCover: Int,
    val precipitation: Double,
    val isDay: Boolean
) {
    val temperatureFormatted: String
        get() = "${temperature.toInt()}°"
    
    val apparentTemperatureFormatted: String
        get() = "${apparentTemperature.toInt()}°"
    
    val humidityFormatted: String
        get() = "$humidity%"
    
    val windSpeedFormatted: String
        get() = "${windSpeed.toInt()} km/h"
    
    val pressureFormatted: String
        get() = "${pressure.toInt()} hPa"
    
    val windDirectionText: String
        get() = when {
            windDirection in 0..22 || windDirection in 338..360 -> "U"
            windDirection in 23..67 -> "TL"
            windDirection in 68..112 -> "T"
            windDirection in 113..157 -> "TG"
            windDirection in 158..202 -> "S"
            windDirection in 203..247 -> "BD"
            windDirection in 248..292 -> "B"
            windDirection in 293..337 -> "BL"
            else -> "-"
        }
}

/**
 * Data prakiraan per jam (UI-ready)
 */
data class HourlyWeatherData(
    val time: String,
    val hour: String,
    val temperature: Double,
    val apparentTemperature: Double,
    val humidity: Int,
    val weatherCode: Int,
    val weatherCondition: WeatherCondition,
    val precipitationProbability: Int,
    val precipitation: Double,
    val windSpeed: Double,
    val uvIndex: Double,
    val isDay: Boolean,
    val visibility: Double
) {
    val temperatureFormatted: String
        get() = "${temperature.toInt()}°"
    
    val precipitationProbabilityFormatted: String
        get() = "$precipitationProbability%"
}

/**
 * Data prakiraan harian (UI-ready)
 */
data class DailyWeatherData(
    val date: String,
    val dayName: String,
    val temperatureMax: Double,
    val temperatureMin: Double,
    val apparentTemperatureMax: Double,
    val apparentTemperatureMin: Double,
    val weatherCode: Int,
    val weatherCondition: WeatherCondition,
    val sunrise: String,
    val sunset: String,
    val uvIndexMax: Double,
    val precipitationSum: Double,
    val precipitationProbabilityMax: Int,
    val windSpeedMax: Double,
    val windGustsMax: Double
) {
    val temperatureMaxFormatted: String
        get() = "${temperatureMax.toInt()}°"
    
    val temperatureMinFormatted: String
        get() = "${temperatureMin.toInt()}°"
    
    val precipitationProbabilityFormatted: String
        get() = "$precipitationProbabilityMax%"
}

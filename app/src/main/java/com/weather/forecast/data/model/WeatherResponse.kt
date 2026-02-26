package com.weather.forecast.data.model

import com.google.gson.annotations.SerializedName

/**
 * Open-Meteo API Response Models
 * 
 * API Documentation: https://open-meteo.com/en/docs
 * 
 * Open-Meteo adalah API cuaca gratis yang tidak memerlukan API key.
 * Data bersumber dari berbagai layanan meteorologi nasional.
 */

/**
 * Response utama dari Open-Meteo API
 */
data class WeatherResponse(
    val latitude: Double,
    val longitude: Double,
    val timezone: String,
    @SerializedName("timezone_abbreviation")
    val timezoneAbbreviation: String,
    val elevation: Double,
    @SerializedName("current")
    val currentWeather: CurrentWeather?,
    @SerializedName("hourly")
    val hourlyForecast: HourlyForecast?,
    @SerializedName("daily")
    val dailyForecast: DailyForecast?
)

/**
 * Data cuaca saat ini
 */
data class CurrentWeather(
    val time: String,
    val interval: Int,
    @SerializedName("temperature_2m")
    val temperature: Double,
    @SerializedName("relative_humidity_2m")
    val humidity: Int,
    @SerializedName("apparent_temperature")
    val apparentTemperature: Double,
    @SerializedName("is_day")
    val isDay: Int,
    val precipitation: Double,
    val rain: Double,
    val showers: Double,
    val snowfall: Double,
    @SerializedName("weather_code")
    val weatherCode: Int,
    @SerializedName("cloud_cover")
    val cloudCover: Int,
    @SerializedName("pressure_msl")
    val pressure: Double,
    @SerializedName("surface_pressure")
    val surfacePressure: Double,
    @SerializedName("wind_speed_10m")
    val windSpeed: Double,
    @SerializedName("wind_direction_10m")
    val windDirection: Int,
    @SerializedName("wind_gusts_10m")
    val windGusts: Double
)

/**
 * Data prakiraan per jam
 */
data class HourlyForecast(
    val time: List<String>,
    @SerializedName("temperature_2m")
    val temperature: List<Double>,
    @SerializedName("relative_humidity_2m")
    val humidity: List<Int>,
    @SerializedName("apparent_temperature")
    val apparentTemperature: List<Double>,
    @SerializedName("precipitation_probability")
    val precipitationProbability: List<Int>,
    val precipitation: List<Double>,
    val rain: List<Double>,
    val showers: List<Double>,
    val snowfall: List<Double>,
    @SerializedName("weather_code")
    val weatherCode: List<Int>,
    @SerializedName("cloud_cover")
    val cloudCover: List<Int>,
    val visibility: List<Double>,
    @SerializedName("wind_speed_10m")
    val windSpeed: List<Double>,
    @SerializedName("wind_direction_10m")
    val windDirection: List<Int>,
    @SerializedName("uv_index")
    val uvIndex: List<Double>,
    @SerializedName("is_day")
    val isDay: List<Int>
)

/**
 * Data prakiraan harian
 */
data class DailyForecast(
    val time: List<String>,
    @SerializedName("weather_code")
    val weatherCode: List<Int>,
    @SerializedName("temperature_2m_max")
    val temperatureMax: List<Double>,
    @SerializedName("temperature_2m_min")
    val temperatureMin: List<Double>,
    @SerializedName("apparent_temperature_max")
    val apparentTemperatureMax: List<Double>,
    @SerializedName("apparent_temperature_min")
    val apparentTemperatureMin: List<Double>,
    val sunrise: List<String>,
    val sunset: List<String>,
    @SerializedName("uv_index_max")
    val uvIndexMax: List<Double>,
    @SerializedName("precipitation_sum")
    val precipitationSum: List<Double>,
    @SerializedName("rain_sum")
    val rainSum: List<Double>,
    @SerializedName("showers_sum")
    val showersSum: List<Double>,
    @SerializedName("snowfall_sum")
    val snowfallSum: List<Double>,
    @SerializedName("precipitation_probability_max")
    val precipitationProbabilityMax: List<Int>,
    @SerializedName("wind_speed_10m_max")
    val windSpeedMax: List<Double>,
    @SerializedName("wind_gusts_10m_max")
    val windGustsMax: List<Double>,
    @SerializedName("wind_direction_10m_dominant")
    val windDirectionDominant: List<Int>
)

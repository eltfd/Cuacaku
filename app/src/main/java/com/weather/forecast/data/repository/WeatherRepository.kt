package com.weather.forecast.data.repository

import com.weather.forecast.data.api.RetrofitClient
import com.weather.forecast.data.model.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

/**
 * Weather Repository
 * 
 * Single source of truth untuk data cuaca.
 * Menggabungkan data dari Open-Meteo API dan Nominatim Geocoding.
 * 
 * Responsibilities:
 * - Fetch weather data dari API
 * - Fetch location name dari coordinates
 * - Transform API response ke UI-ready models
 * - Handle errors
 */
class WeatherRepository {

    private val weatherApi = RetrofitClient.weatherApi
    private val geocodingApi = RetrofitClient.geocodingApi

    /**
     * Get complete weather data untuk lokasi
     * 
     * @param latitude Location latitude
     * @param longitude Location longitude
     * @return Result<WeatherData> - Success dengan data atau Failure dengan exception
     */
    suspend fun getWeatherData(latitude: Double, longitude: Double): Result<WeatherData> {
        return withContext(Dispatchers.IO) {
            try {
                // Fetch weather dan location name secara parallel
                val weatherResponse = weatherApi.getWeather(latitude, longitude)
                val locationName = getLocationName(latitude, longitude)

                // Transform ke UI model
                val weatherData = transformToWeatherData(weatherResponse, locationName)
                Result.success(weatherData)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    /**
     * Get location name dari coordinates menggunakan Nominatim
     */
    private suspend fun getLocationName(latitude: Double, longitude: Double): Pair<String, String> {
        return try {
            val response = geocodingApi.reverseGeocode(latitude, longitude)
            val name = response.address?.getLocationName() ?: "Unknown"
            val fullName = response.address?.getFullLocation() ?: response.displayName
            Pair(name, fullName)
        } catch (e: Exception) {
            Pair("Lat: $latitude", "Lon: $longitude")
        }
    }

    /**
     * Search lokasi berdasarkan nama
     */
    suspend fun searchLocation(query: String): Result<List<SearchResult>> {
        return withContext(Dispatchers.IO) {
            try {
                val results = geocodingApi.searchLocation(query)
                Result.success(results)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    /**
     * Transform API response ke UI-ready WeatherData
     */
    private fun transformToWeatherData(
        response: WeatherResponse,
        locationName: Pair<String, String>
    ): WeatherData {
        val current = response.currentWeather?.let { c ->
            CurrentWeatherData(
                temperature = c.temperature,
                apparentTemperature = c.apparentTemperature,
                humidity = c.humidity,
                weatherCode = c.weatherCode,
                weatherCondition = WeatherCondition.fromCode(c.weatherCode),
                windSpeed = c.windSpeed,
                windDirection = c.windDirection,
                windGusts = c.windGusts,
                pressure = c.pressure,
                cloudCover = c.cloudCover,
                precipitation = c.precipitation,
                isDay = c.isDay == 1
            )
        } ?: createDefaultCurrentWeather()

        val hourly = transformHourlyData(response.hourlyForecast)
        val daily = transformDailyData(response.dailyForecast)

        return WeatherData(
            location = LocationInfo(
                latitude = response.latitude,
                longitude = response.longitude,
                name = locationName.first,
                fullName = locationName.second,
                timezone = response.timezone
            ),
            current = current,
            hourly = hourly,
            daily = daily
        )
    }

    /**
     * Transform hourly forecast data
     */
    private fun transformHourlyData(hourly: HourlyForecast?): List<HourlyWeatherData> {
        if (hourly == null) return emptyList()

        val formatter = DateTimeFormatter.ISO_DATE_TIME
        val hourFormatter = DateTimeFormatter.ofPattern("HH:mm")
        val now = LocalDateTime.now()

        return hourly.time.mapIndexedNotNull { index, timeStr ->
            try {
                val time = LocalDateTime.parse(timeStr, formatter)
                
                // Hanya ambil data dari jam sekarang sampai 24 jam ke depan
                if (time.isBefore(now.minusHours(1)) || time.isAfter(now.plusHours(24))) {
                    return@mapIndexedNotNull null
                }

                HourlyWeatherData(
                    time = timeStr,
                    hour = time.format(hourFormatter),
                    temperature = hourly.temperature.getOrNull(index) ?: 0.0,
                    apparentTemperature = hourly.apparentTemperature.getOrNull(index) ?: 0.0,
                    humidity = hourly.humidity.getOrNull(index) ?: 0,
                    weatherCode = hourly.weatherCode.getOrNull(index) ?: 0,
                    weatherCondition = WeatherCondition.fromCode(hourly.weatherCode.getOrNull(index) ?: 0),
                    precipitationProbability = hourly.precipitationProbability.getOrNull(index) ?: 0,
                    precipitation = hourly.precipitation.getOrNull(index) ?: 0.0,
                    windSpeed = hourly.windSpeed.getOrNull(index) ?: 0.0,
                    uvIndex = hourly.uvIndex.getOrNull(index) ?: 0.0,
                    isDay = (hourly.isDay.getOrNull(index) ?: 1) == 1,
                    visibility = hourly.visibility.getOrNull(index) ?: 0.0
                )
            } catch (e: Exception) {
                null
            }
        }
    }

    /**
     * Transform daily forecast data
     */
    private fun transformDailyData(daily: DailyForecast?): List<DailyWeatherData> {
        if (daily == null) return emptyList()

        val dateFormatter = DateTimeFormatter.ISO_DATE
        val locale = Locale("id", "ID")

        return daily.time.mapIndexedNotNull { index, dateStr ->
            try {
                val date = java.time.LocalDate.parse(dateStr, dateFormatter)
                val dayName = date.dayOfWeek.getDisplayName(TextStyle.FULL, locale)

                DailyWeatherData(
                    date = dateStr,
                    dayName = dayName,
                    temperatureMax = daily.temperatureMax.getOrNull(index) ?: 0.0,
                    temperatureMin = daily.temperatureMin.getOrNull(index) ?: 0.0,
                    apparentTemperatureMax = daily.apparentTemperatureMax.getOrNull(index) ?: 0.0,
                    apparentTemperatureMin = daily.apparentTemperatureMin.getOrNull(index) ?: 0.0,
                    weatherCode = daily.weatherCode.getOrNull(index) ?: 0,
                    weatherCondition = WeatherCondition.fromCode(daily.weatherCode.getOrNull(index) ?: 0),
                    sunrise = extractTime(daily.sunrise.getOrNull(index) ?: ""),
                    sunset = extractTime(daily.sunset.getOrNull(index) ?: ""),
                    uvIndexMax = daily.uvIndexMax.getOrNull(index) ?: 0.0,
                    precipitationSum = daily.precipitationSum.getOrNull(index) ?: 0.0,
                    precipitationProbabilityMax = daily.precipitationProbabilityMax.getOrNull(index) ?: 0,
                    windSpeedMax = daily.windSpeedMax.getOrNull(index) ?: 0.0,
                    windGustsMax = daily.windGustsMax.getOrNull(index) ?: 0.0
                )
            } catch (e: Exception) {
                null
            }
        }
    }

    /**
     * Extract time dari ISO datetime string
     */
    private fun extractTime(isoDateTime: String): String {
        return try {
            val dateTime = LocalDateTime.parse(isoDateTime, DateTimeFormatter.ISO_DATE_TIME)
            dateTime.format(DateTimeFormatter.ofPattern("HH:mm"))
        } catch (e: Exception) {
            isoDateTime.substringAfter("T").take(5)
        }
    }

    /**
     * Create default current weather jika API tidak mengembalikan data
     */
    private fun createDefaultCurrentWeather(): CurrentWeatherData {
        return CurrentWeatherData(
            temperature = 0.0,
            apparentTemperature = 0.0,
            humidity = 0,
            weatherCode = 0,
            weatherCondition = WeatherCondition.CLEAR,
            windSpeed = 0.0,
            windDirection = 0,
            windGusts = 0.0,
            pressure = 0.0,
            cloudCover = 0,
            precipitation = 0.0,
            isDay = true
        )
    }
}

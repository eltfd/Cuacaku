package com.weather.forecast.data.repository

import com.weather.forecast.data.api.RetrofitClient
import com.weather.forecast.data.model.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

/**
 * Air Quality Repository
 *
 * Mengelola data kualitas udara dari Open-Meteo Air Quality API.
 * Mengintegrasikan data angin dan cuaca dari Weather API untuk korelasi.
 *
 * Responsibilities:
 * - Fetch air quality data
 * - Fetch weather data (angin, suhu, kelembaban) secara paralel
 * - Transform ke UI-ready models
 * - Mengelompokkan data per jam menjadi prakiraan harian
 */
class AirQualityRepository {

    private val airQualityApi = RetrofitClient.airQualityApi
    private val weatherApi = RetrofitClient.weatherApi

    /**
     * Get complete air quality data untuk lokasi
     * Termasuk data angin dari Weather API
     */
    suspend fun getAirQualityData(latitude: Double, longitude: Double): Result<AirQualityData> {
        return withContext(Dispatchers.IO) {
            try {
                // Fetch air quality dan weather secara paralel
                val aqDeferred = async { airQualityApi.getAirQuality(latitude, longitude) }
                val weatherDeferred = async {
                    try {
                        weatherApi.getWeather(
                            latitude = latitude,
                            longitude = longitude,
                            current = "wind_speed_10m,wind_direction_10m,temperature_2m,relative_humidity_2m",
                            hourly = "wind_speed_10m,wind_direction_10m,temperature_2m,relative_humidity_2m",
                            daily = "wind_speed_10m_max,wind_direction_10m_dominant",
                            forecastDays = 5
                        )
                    } catch (e: Exception) {
                        null // Weather data is optional enhancement
                    }
                }

                val aqResponse = aqDeferred.await()
                val weatherResponse = weatherDeferred.await()

                val data = transformToAirQualityData(aqResponse, weatherResponse)
                Result.success(data)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    /**
     * Transform API responses ke UI-ready AirQualityData
     */
    private fun transformToAirQualityData(
        aqResponse: AirQualityResponse,
        weatherResponse: WeatherResponse?
    ): AirQualityData {
        val current = transformCurrentAirQuality(aqResponse.current)
        val hourlyList = transformHourlyAirQuality(aqResponse.hourly, weatherResponse)
        val dailyList = aggregateDailyAirQuality(hourlyList)

        return AirQualityData(
            current = current,
            hourlyForecast = hourlyList,
            dailyForecast = dailyList
        )
    }

    /**
     * Transform current air quality data
     */
    private fun transformCurrentAirQuality(current: CurrentAirQuality?): CurrentAirQualityData {
        if (current == null) return createDefaultCurrentAirQuality()

        val aqi = current.usAqi ?: current.europeanAqi ?: 0
        return CurrentAirQualityData(
            aqi = aqi,
            aqiLevel = AqiLevel.fromAqi(aqi),
            pm25 = current.pm25 ?: 0.0,
            pm10 = current.pm10 ?: 0.0,
            co = current.carbonMonoxide ?: 0.0,
            no2 = current.nitrogenDioxide ?: 0.0,
            so2 = current.sulphurDioxide ?: 0.0,
            o3 = current.ozone ?: 0.0,
            uvIndex = current.uvIndex ?: 0.0,
            dust = current.dust ?: 0.0,
            ammonia = current.ammonia ?: 0.0
        )
    }

    /**
     * Transform hourly air quality data, enriched with weather wind data
     */
    private fun transformHourlyAirQuality(
        hourly: HourlyAirQuality?,
        weatherResponse: WeatherResponse?
    ): List<HourlyAirQualityData> {
        if (hourly == null) return emptyList()

        val formatter = DateTimeFormatter.ISO_DATE_TIME
        val hourFormatter = DateTimeFormatter.ofPattern("HH:mm")

        // Build weather hourly lookup by time string
        val weatherHourlyMap = buildWeatherHourlyMap(weatherResponse)

        return hourly.time.mapIndexedNotNull { index, timeStr ->
            try {
                val time = LocalDateTime.parse(timeStr, formatter)
                val aqi = hourly.usAqi?.getOrNull(index) ?: hourly.europeanAqi?.getOrNull(index) ?: 0

                // Get matching weather data
                val weatherData = weatherHourlyMap[timeStr]

                HourlyAirQualityData(
                    time = timeStr,
                    hour = time.format(hourFormatter),
                    aqi = aqi,
                    aqiLevel = AqiLevel.fromAqi(aqi),
                    pm25 = hourly.pm25?.getOrNull(index) ?: 0.0,
                    pm10 = hourly.pm10?.getOrNull(index) ?: 0.0,
                    co = hourly.carbonMonoxide?.getOrNull(index) ?: 0.0,
                    no2 = hourly.nitrogenDioxide?.getOrNull(index) ?: 0.0,
                    so2 = hourly.sulphurDioxide?.getOrNull(index) ?: 0.0,
                    o3 = hourly.ozone?.getOrNull(index) ?: 0.0,
                    uvIndex = hourly.uvIndex?.getOrNull(index) ?: 0.0,
                    windSpeed = weatherData?.windSpeed ?: 0.0,
                    windDirection = weatherData?.windDirection ?: 0,
                    temperature = weatherData?.temperature ?: 0.0,
                    humidity = weatherData?.humidity ?: 0
                )
            } catch (e: Exception) {
                null
            }
        }
    }

    /**
     * Build map dari weather hourly data indexed by time string
     */
    private fun buildWeatherHourlyMap(weatherResponse: WeatherResponse?): Map<String, WeatherHourlySlice> {
        val hourly = weatherResponse?.hourlyForecast ?: return emptyMap()
        val map = mutableMapOf<String, WeatherHourlySlice>()

        for (i in hourly.time.indices) {
            map[hourly.time[i]] = WeatherHourlySlice(
                windSpeed = hourly.windSpeed.getOrNull(i) ?: 0.0,
                windDirection = hourly.windDirection.getOrNull(i) ?: 0,
                temperature = hourly.temperature.getOrNull(i) ?: 0.0,
                humidity = hourly.humidity.getOrNull(i) ?: 0
            )
        }

        return map
    }

    /**
     * Aggregate hourly data into daily summaries
     */
    private fun aggregateDailyAirQuality(hourlyList: List<HourlyAirQualityData>): List<DailyAirQualityData> {
        val formatter = DateTimeFormatter.ISO_DATE_TIME
        val dateFormatter = DateTimeFormatter.ISO_DATE
        val locale = Locale("id", "ID")

        val grouped = hourlyList.groupBy { item ->
            try {
                val time = LocalDateTime.parse(item.time, formatter)
                time.toLocalDate().format(dateFormatter)
            } catch (e: Exception) {
                ""
            }
        }.filterKeys { it.isNotEmpty() }

        return grouped.map { (dateStr, items) ->
            val date = LocalDate.parse(dateStr, dateFormatter)
            val dayName = date.dayOfWeek.getDisplayName(TextStyle.FULL, locale)
            val avgAqi = items.map { it.aqi }.average().toInt()

            DailyAirQualityData(
                date = dateStr,
                dayName = dayName,
                avgAqi = avgAqi,
                maxAqi = items.maxOf { it.aqi },
                minAqi = items.minOf { it.aqi },
                aqiLevel = AqiLevel.fromAqi(avgAqi),
                avgPm25 = items.map { it.pm25 }.average(),
                avgPm10 = items.map { it.pm10 }.average(),
                maxUvIndex = items.maxOf { it.uvIndex },
                avgWindSpeed = items.map { it.windSpeed }.average(),
                dominantWindDirection = items.map { it.windDirection }
                    .groupingBy { it }.eachCount()
                    .maxByOrNull { it.value }?.key ?: 0,
                hourlyForecasts = items
            )
        }
    }

    private fun createDefaultCurrentAirQuality(): CurrentAirQualityData {
        return CurrentAirQualityData(
            aqi = 0,
            aqiLevel = AqiLevel.GOOD,
            pm25 = 0.0,
            pm10 = 0.0,
            co = 0.0,
            no2 = 0.0,
            so2 = 0.0,
            o3 = 0.0,
            uvIndex = 0.0,
            dust = 0.0,
            ammonia = 0.0
        )
    }

    /**
     * Helper data class untuk weather data per jam
     */
    private data class WeatherHourlySlice(
        val windSpeed: Double,
        val windDirection: Int,
        val temperature: Double,
        val humidity: Int
    )
}

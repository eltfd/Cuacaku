package com.weather.forecast.data.api

import com.weather.forecast.data.model.WeatherResponse
import retrofit2.http.GET
import retrofit2.http.Query

/**
 * Open-Meteo Weather API Interface
 * 
 * Base URL: https://api.open-meteo.com/v1/
 * Documentation: https://open-meteo.com/en/docs
 * 
 * Features:
 * - Free & Open Source
 * - No API key required
 * - High resolution weather data
 * - Global coverage
 * - Updates every 15 minutes
 */
interface WeatherApiService {

    /**
     * Get weather forecast
     * 
     * @param latitude Location latitude
     * @param longitude Location longitude
     * @param current Current weather parameters to include
     * @param hourly Hourly forecast parameters to include
     * @param daily Daily forecast parameters to include
     * @param timezone Timezone for time values (auto = use location timezone)
     * @param forecastDays Number of forecast days (1-16)
     * 
     * Example call:
     * ```
     * api.getWeather(
     *     latitude = -6.2088,
     *     longitude = 106.8456,
     *     current = "temperature_2m,relative_humidity_2m,weather_code",
     *     hourly = "temperature_2m,precipitation_probability",
     *     daily = "temperature_2m_max,temperature_2m_min",
     *     timezone = "auto",
     *     forecastDays = 7
     * )
     * ```
     */
    @GET("forecast")
    suspend fun getWeather(
        @Query("latitude") latitude: Double,
        @Query("longitude") longitude: Double,
        @Query("current") current: String = CURRENT_PARAMS,
        @Query("hourly") hourly: String = HOURLY_PARAMS,
        @Query("daily") daily: String = DAILY_PARAMS,
        @Query("timezone") timezone: String = "auto",
        @Query("forecast_days") forecastDays: Int = 7
    ): WeatherResponse

    companion object {
        const val BASE_URL = "https://api.open-meteo.com/v1/"

        // Current weather parameters
        const val CURRENT_PARAMS = "temperature_2m,relative_humidity_2m,apparent_temperature," +
                "is_day,precipitation,rain,showers,snowfall,weather_code,cloud_cover," +
                "pressure_msl,surface_pressure,wind_speed_10m,wind_direction_10m,wind_gusts_10m," +
                "dew_point_2m,cape"

        // Hourly forecast parameters
        const val HOURLY_PARAMS = "temperature_2m,relative_humidity_2m,apparent_temperature," +
                "precipitation_probability,precipitation,rain,showers,snowfall,weather_code," +
                "cloud_cover,visibility,wind_speed_10m,wind_direction_10m,wind_gusts_10m," +
                "uv_index,is_day,dew_point_2m,cape,freezing_level_height," +
                "surface_pressure,pressure_msl," +
                "soil_moisture_0_to_7cm,soil_moisture_7_to_28cm,soil_moisture_28_to_100cm," +
                "soil_temperature_0cm"

        // Daily forecast parameters
        const val DAILY_PARAMS = "weather_code,temperature_2m_max,temperature_2m_min," +
                "apparent_temperature_max,apparent_temperature_min,sunrise,sunset," +
                "uv_index_max,precipitation_sum,rain_sum,showers_sum,snowfall_sum," +
                "precipitation_probability_max,wind_speed_10m_max,wind_gusts_10m_max," +
                "wind_direction_10m_dominant,precipitation_hours"
    }
}

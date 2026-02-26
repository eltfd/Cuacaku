package com.weather.forecast.data.api

import com.weather.forecast.data.model.MarineResponse
import retrofit2.http.GET
import retrofit2.http.Query

/**
 * Open-Meteo Marine API Interface
 *
 * Base URL: https://marine-api.open-meteo.com/v1/
 * Documentation: https://open-meteo.com/en/docs/marine-weather-api
 *
 * Features:
 * - Free & Open Source
 * - No API key required
 * - Wave height, direction, period
 * - Swell wave data
 * - Global ocean coverage
 */
interface MarineApiService {

    /**
     * Get marine/sea conditions
     */
    @GET("marine")
    suspend fun getMarineData(
        @Query("latitude") latitude: Double,
        @Query("longitude") longitude: Double,
        @Query("current") current: String = CURRENT_PARAMS,
        @Query("hourly") hourly: String = HOURLY_PARAMS,
        @Query("daily") daily: String = DAILY_PARAMS,
        @Query("timezone") timezone: String = "auto",
        @Query("forecast_days") forecastDays: Int = 7
    ): MarineResponse

    companion object {
        const val BASE_URL = "https://marine-api.open-meteo.com/v1/"

        const val CURRENT_PARAMS = "wave_height,wave_direction,wave_period," +
                "swell_wave_height,swell_wave_direction,swell_wave_period"

        const val HOURLY_PARAMS = "wave_height,wave_direction,wave_period," +
                "swell_wave_height,swell_wave_direction,swell_wave_period"

        const val DAILY_PARAMS = "wave_height_max,wave_direction_dominant,wave_period_max," +
                "swell_wave_height_max,swell_wave_direction_dominant,swell_wave_period_max"
    }
}

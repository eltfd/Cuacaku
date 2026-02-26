package com.weather.forecast.data.api

import com.weather.forecast.data.model.FloodResponse
import retrofit2.http.GET
import retrofit2.http.Query

/**
 * Open-Meteo Flood API Interface
 *
 * Base URL: https://flood-api.open-meteo.com/v1/
 * Documentation: https://open-meteo.com/en/docs/flood-api
 *
 * Features:
 * - Free & Open Source
 * - No API key required
 * - Data dari GloFAS (Global Flood Awareness System) by ECMWF
 * - River discharge (m³/s)
 * - Forecast hingga 3 bulan
 */
interface FloodApiService {

    /**
     * Get flood/river discharge data
     *
     * @param latitude Location latitude
     * @param longitude Location longitude
     * @param daily Daily parameters
     * @param forecastDays Number of forecast days
     */
    @GET("flood")
    suspend fun getFloodData(
        @Query("latitude") latitude: Double,
        @Query("longitude") longitude: Double,
        @Query("daily") daily: String = DAILY_PARAMS,
        @Query("forecast_days") forecastDays: Int = 7
    ): FloodResponse

    companion object {
        const val BASE_URL = "https://flood-api.open-meteo.com/v1/"

        const val DAILY_PARAMS = "river_discharge,river_discharge_mean," +
                "river_discharge_median,river_discharge_max,river_discharge_min"
    }
}

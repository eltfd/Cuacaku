package com.weather.forecast.data.api

import com.weather.forecast.data.model.AirQualityResponse
import retrofit2.http.GET
import retrofit2.http.Query

/**
 * Open-Meteo Air Quality API Interface
 *
 * Base URL: https://air-quality-api.open-meteo.com/v1/
 * Documentation: https://open-meteo.com/en/docs/air-quality-api
 *
 * Features:
 * - Free & Open Source (data dari CAMS / Copernicus)
 * - No API key required
 * - AQI (European & US), PM2.5, PM10, CO, NO₂, SO₂, O₃
 * - Prakiraan per jam hingga 5 hari
 * - UV Index, Dust, Ammonia
 */
interface AirQualityApiService {

    /**
     * Get air quality data
     *
     * @param latitude Location latitude
     * @param longitude Location longitude
     * @param current Current parameters
     * @param hourly Hourly forecast parameters
     * @param timezone Timezone
     * @param forecastDays Number of forecast days (1-5)
     */
    @GET("air-quality")
    suspend fun getAirQuality(
        @Query("latitude") latitude: Double,
        @Query("longitude") longitude: Double,
        @Query("current") current: String = CURRENT_PARAMS,
        @Query("hourly") hourly: String = HOURLY_PARAMS,
        @Query("timezone") timezone: String = "auto",
        @Query("forecast_days") forecastDays: Int = 5
    ): AirQualityResponse

    companion object {
        const val BASE_URL = "https://air-quality-api.open-meteo.com/v1/"

        const val CURRENT_PARAMS = "european_aqi,us_aqi,pm10,pm2_5," +
                "carbon_monoxide,nitrogen_dioxide,sulphur_dioxide,ozone," +
                "uv_index,dust,ammonia"

        const val HOURLY_PARAMS = "european_aqi,us_aqi,pm10,pm2_5," +
                "carbon_monoxide,nitrogen_dioxide,sulphur_dioxide,ozone," +
                "uv_index,dust,ammonia"
    }
}

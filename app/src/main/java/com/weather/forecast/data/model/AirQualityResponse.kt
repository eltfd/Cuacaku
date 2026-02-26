package com.weather.forecast.data.model

import com.google.gson.annotations.SerializedName

/**
 * Open-Meteo Air Quality API Response Models
 *
 * API: https://air-quality-api.open-meteo.com/v1/air-quality
 * Documentation: https://open-meteo.com/en/docs/air-quality-api
 *
 * Data bersumber dari CAMS (Copernicus Atmosphere Monitoring Service).
 * Gratis, tanpa API key.
 */

/**
 * Response utama dari Air Quality API
 */
data class AirQualityResponse(
    val latitude: Double,
    val longitude: Double,
    val timezone: String,
    @SerializedName("timezone_abbreviation")
    val timezoneAbbreviation: String,
    val elevation: Double?,
    @SerializedName("current")
    val current: CurrentAirQuality?,
    @SerializedName("hourly")
    val hourly: HourlyAirQuality?
)

/**
 * Data kualitas udara saat ini
 */
data class CurrentAirQuality(
    val time: String,
    val interval: Int?,
    @SerializedName("european_aqi")
    val europeanAqi: Int?,
    @SerializedName("us_aqi")
    val usAqi: Int?,
    @SerializedName("pm10")
    val pm10: Double?,
    @SerializedName("pm2_5")
    val pm25: Double?,
    @SerializedName("carbon_monoxide")
    val carbonMonoxide: Double?,
    @SerializedName("nitrogen_dioxide")
    val nitrogenDioxide: Double?,
    @SerializedName("sulphur_dioxide")
    val sulphurDioxide: Double?,
    @SerializedName("ozone")
    val ozone: Double?,
    @SerializedName("uv_index")
    val uvIndex: Double?,
    @SerializedName("dust")
    val dust: Double?,
    @SerializedName("ammonia")
    val ammonia: Double?
)

/**
 * Data prakiraan kualitas udara per jam
 */
data class HourlyAirQuality(
    val time: List<String>,
    @SerializedName("european_aqi")
    val europeanAqi: List<Int?>?,
    @SerializedName("us_aqi")
    val usAqi: List<Int?>?,
    @SerializedName("pm10")
    val pm10: List<Double?>?,
    @SerializedName("pm2_5")
    val pm25: List<Double?>?,
    @SerializedName("carbon_monoxide")
    val carbonMonoxide: List<Double?>?,
    @SerializedName("nitrogen_dioxide")
    val nitrogenDioxide: List<Double?>?,
    @SerializedName("sulphur_dioxide")
    val sulphurDioxide: List<Double?>?,
    @SerializedName("ozone")
    val ozone: List<Double?>?,
    @SerializedName("uv_index")
    val uvIndex: List<Double?>?,
    @SerializedName("dust")
    val dust: List<Double?>?,
    @SerializedName("ammonia")
    val ammonia: List<Double?>?
)

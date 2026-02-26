package com.weather.forecast.data.model

import com.google.gson.annotations.SerializedName

/**
 * Open-Meteo Marine API Response Models
 *
 * API: https://marine-api.open-meteo.com/v1/marine
 * Documentation: https://open-meteo.com/en/docs/marine-weather-api
 *
 * Data kondisi laut: gelombang, arus, suhu permukaan.
 * Gratis, tanpa API key.
 */
data class MarineResponse(
    val latitude: Double,
    val longitude: Double,
    val timezone: String,
    @SerializedName("timezone_abbreviation")
    val timezoneAbbreviation: String,
    @SerializedName("current")
    val current: CurrentMarine?,
    @SerializedName("hourly")
    val hourly: HourlyMarine?,
    @SerializedName("daily")
    val daily: DailyMarine?
)

data class CurrentMarine(
    val time: String,
    val interval: Int?,
    @SerializedName("wave_height")
    val waveHeight: Double?,
    @SerializedName("wave_direction")
    val waveDirection: Int?,
    @SerializedName("wave_period")
    val wavePeriod: Double?,
    @SerializedName("swell_wave_height")
    val swellWaveHeight: Double?,
    @SerializedName("swell_wave_direction")
    val swellWaveDirection: Int?,
    @SerializedName("swell_wave_period")
    val swellWavePeriod: Double?
)

data class HourlyMarine(
    val time: List<String>,
    @SerializedName("wave_height")
    val waveHeight: List<Double?>?,
    @SerializedName("wave_direction")
    val waveDirection: List<Int?>?,
    @SerializedName("wave_period")
    val wavePeriod: List<Double?>?,
    @SerializedName("swell_wave_height")
    val swellWaveHeight: List<Double?>?,
    @SerializedName("swell_wave_direction")
    val swellWaveDirection: List<Int?>?,
    @SerializedName("swell_wave_period")
    val swellWavePeriod: List<Double?>?
)

data class DailyMarine(
    val time: List<String>,
    @SerializedName("wave_height_max")
    val waveHeightMax: List<Double?>?,
    @SerializedName("wave_direction_dominant")
    val waveDirectionDominant: List<Int?>?,
    @SerializedName("wave_period_max")
    val wavePeriodMax: List<Double?>?,
    @SerializedName("swell_wave_height_max")
    val swellWaveHeightMax: List<Double?>?,
    @SerializedName("swell_wave_direction_dominant")
    val swellWaveDirectionDominant: List<Int?>?,
    @SerializedName("swell_wave_period_max")
    val swellWavePeriodMax: List<Double?>?
)

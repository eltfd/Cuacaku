package com.weather.forecast.data.model

import com.google.gson.annotations.SerializedName

/**
 * Open-Meteo Flood API Response Models
 *
 * API: https://flood-api.open-meteo.com/v1/flood
 * Documentation: https://open-meteo.com/en/docs/flood-api
 *
 * Data debit sungai dari GloFAS (Global Flood Awareness System).
 * Gratis, tanpa API key.
 */
data class FloodResponse(
    val latitude: Double,
    val longitude: Double,
    @SerializedName("daily")
    val daily: DailyFlood?
)

data class DailyFlood(
    val time: List<String>,
    @SerializedName("river_discharge")
    val riverDischarge: List<Double?>?,
    @SerializedName("river_discharge_mean")
    val riverDischargeMean: List<Double?>?,
    @SerializedName("river_discharge_median")
    val riverDischargeMedian: List<Double?>?,
    @SerializedName("river_discharge_max")
    val riverDischargeMax: List<Double?>?,
    @SerializedName("river_discharge_min")
    val riverDischargeMin: List<Double?>?
)

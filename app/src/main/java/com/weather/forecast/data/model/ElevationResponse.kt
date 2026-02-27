package com.weather.forecast.data.model

import com.google.gson.annotations.SerializedName

/**
 * Open-Elevation API Response
 *
 * Returns elevation data for queried points.
 * Uses SRTM 30m resolution dataset.
 */
data class ElevationResponse(
    val results: List<ElevationResult>?
)

data class ElevationResult(
    val latitude: Double,
    val longitude: Double,
    /** Elevation in meters above sea level */
    val elevation: Double
)

package com.weather.forecast.data.model

import com.google.gson.annotations.SerializedName

/**
 * NASA EONET API v3 Response Models
 *
 * Earth Observatory Natural Event Tracker
 * https://eonet.gsfc.nasa.gov/docs/v3
 */
data class EonetResponse(
    val title: String?,
    val description: String?,
    val events: List<EonetEvent>?
)

data class EonetEvent(
    val id: String,
    val title: String?,
    val description: String?,
    val link: String?,
    val closed: String?,  // null if event is still open/ongoing
    val categories: List<EonetCategory>?,
    val sources: List<EonetSource>?,
    val geometry: List<EonetGeometry>?
)

data class EonetCategory(
    val id: String,
    val title: String?
)

data class EonetSource(
    val id: String,
    val url: String?
)

/**
 * Geometry represents a point in time and space for the event.
 * Events can have multiple geometry entries (tracking movement).
 *
 * Note: EONET v3 geometry uses GeoJSON-like format:
 * - Point: coordinates = [longitude, latitude]
 */
data class EonetGeometry(
    val magnitudeValue: Double?,
    val magnitudeUnit: String?,
    val date: String?,
    val type: String?,  // "Point"
    val coordinates: List<Double>?  // [longitude, latitude]
) {
    /** Extract latitude from coordinates (index 1) */
    val latitude: Double?
        get() = coordinates?.getOrNull(1)

    /** Extract longitude from coordinates (index 0) */
    val longitude: Double?
        get() = coordinates?.getOrNull(0)
}

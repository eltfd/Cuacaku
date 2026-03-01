package com.weather.forecast.data.model

import com.google.gson.annotations.SerializedName

/**
 * Nominatim Reverse Geocoding Response
 * 
 * API Documentation: https://nominatim.org/release-docs/latest/api/Reverse/
 * 
 * Nominatim adalah layanan geocoding gratis dari OpenStreetMap.
 * Digunakan untuk mendapatkan nama lokasi dari koordinat GPS.
 */
data class GeocodingResponse(
    @SerializedName("place_id")
    val placeId: Long,
    val licence: String,
    @SerializedName("osm_type")
    val osmType: String,
    @SerializedName("osm_id")
    val osmId: Long,
    val lat: String,
    val lon: String,
    @SerializedName("display_name")
    val displayName: String,
    val address: Address?,
    val boundingbox: List<String>
)

data class Address(
    val village: String?,
    val town: String?,
    val city: String?,
    val municipality: String?,
    val county: String?,
    val state: String?,
    val region: String?,
    val country: String?,
    @SerializedName("country_code")
    val countryCode: String?,
    val postcode: String?
) {
    /**
     * Get display name for location
     * Priority: city > town > village > municipality > county
     */
    fun getLocationName(): String {
        return city ?: town ?: village ?: municipality ?: county ?: state ?: "Unknown"
    }
    
    /**
     * Get full location string
     */
    fun getFullLocation(): String {
        val parts = listOfNotNull(
            city ?: town ?: village ?: municipality,
            state ?: region,
            country
        )
        return parts.joinToString(", ")
    }
}

/**
 * Search result dari Nominatim Search API
 */
data class SearchResult(
    @SerializedName("place_id")
    val placeId: Long,
    val licence: String,
    @SerializedName("osm_type")
    val osmType: String,
    @SerializedName("osm_id")
    val osmId: Long,
    val lat: String,
    val lon: String,
    /** Feature name (e.g. "Sungai Cisadane", "Laut Jawa") */
    val name: String? = null,
    @SerializedName("display_name")
    val displayName: String,
    /** OSM class — e.g. "waterway", "place", "natural" */
    @SerializedName("class")
    val osmClass: String? = null,
    /** OSM type — e.g. "river", "sea", "water" */
    val type: String,
    @SerializedName("addresstype")
    val addressType: String? = null,
    val importance: Double,
    val address: Address?
)

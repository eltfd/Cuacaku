package com.weather.forecast.data.api

import com.google.gson.annotations.SerializedName
import retrofit2.http.GET
import retrofit2.http.Query

/**
 * USGS Earthquake Hazards Program API
 *
 * Provides real-time earthquake data worldwide.
 * Free, no API key required.
 *
 * Documentation: https://earthquake.usgs.gov/fdsnws/event/1/
 */
interface USGSEarthquakeApiService {

    /**
     * Query earthquakes within geographic bounds and time range.
     */
    @GET("query")
    suspend fun getEarthquakes(
        @Query("format") format: String = "geojson",
        @Query("minmagnitude") minMagnitude: Double = 2.5,
        @Query("orderby") orderBy: String = "time",
        @Query("limit") limit: Int = 100,
        @Query("latitude") latitude: Double? = null,
        @Query("longitude") longitude: Double? = null,
        @Query("maxradiuskm") maxRadiusKm: Double? = null,
        @Query("starttime") startTime: String? = null,
        @Query("endtime") endTime: String? = null
    ): USGSEarthquakeResponse

    /**
     * Query significant earthquakes (curated by USGS).
     */
    @GET("query")
    suspend fun getSignificantEarthquakes(
        @Query("format") format: String = "geojson",
        @Query("orderby") orderBy: String = "time",
        @Query("limit") limit: Int = 20,
        @Query("alertlevel") alertLevel: String? = null,
        @Query("minmagnitude") minMagnitude: Double = 5.0,
        @Query("starttime") startTime: String? = null
    ): USGSEarthquakeResponse

    companion object {
        const val BASE_URL = "https://earthquake.usgs.gov/fdsnws/event/1/"
    }
}

// ═══════════════════════════════════════════════════
//  USGS GeoJSON Response Models
// ═══════════════════════════════════════════════════

data class USGSEarthquakeResponse(
    val type: String?,
    val metadata: USGSMetadata?,
    val features: List<USGSFeature>?
)

data class USGSMetadata(
    val generated: Long?,
    val url: String?,
    val title: String?,
    val count: Int?
)

data class USGSFeature(
    val type: String?,
    val properties: USGSProperties?,
    val geometry: USGSGeometry?,
    val id: String?
)

data class USGSProperties(
    val mag: Double?,
    val place: String?,
    val time: Long?,
    val updated: Long?,
    val tz: Int?,
    val url: String?,
    val detail: String?,
    val felt: Int?,
    val cdi: Double?,
    val mmi: Double?,
    val alert: String?,         // green, yellow, orange, red
    val status: String?,        // automatic, reviewed
    val tsunami: Int?,          // 0 or 1
    val sig: Int?,              // significance 0-1000
    val net: String?,           // network code
    val code: String?,
    val ids: String?,
    val sources: String?,
    val types: String?,
    val nst: Int?,
    val dmin: Double?,
    val rms: Double?,
    val gap: Double?,
    val magType: String?,       // ml, mb, mw, etc.
    val type: String?,          // earthquake, quarry blast, etc.
    val title: String?
)

data class USGSGeometry(
    val type: String?,
    val coordinates: List<Double>?     // [longitude, latitude, depth_km]
) {
    val longitude: Double get() = coordinates?.getOrNull(0) ?: 0.0
    val latitude: Double get() = coordinates?.getOrNull(1) ?: 0.0
    val depthKm: Double get() = coordinates?.getOrNull(2) ?: 0.0
}

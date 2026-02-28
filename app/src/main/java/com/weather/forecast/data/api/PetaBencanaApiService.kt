package com.weather.forecast.data.api

import com.google.gson.annotations.SerializedName
import retrofit2.http.GET
import retrofit2.http.Query

/**
 * PetaBencana.id API Service
 *
 * Crowdsourced disaster reports for Indonesia.
 * - Real-time, free, no API key required
 * - Requires User-Agent header (already set in RetrofitClient)
 * - Returns Topology/GeoJSON format
 *
 * Supported disaster types:
 * - flood (Banjir)
 * - earthquake (Gempabumi)
 * - wind (Angin Kencang)
 * - haze (Kabut Asap)
 * - fire (Kebakaran Hutan)
 * - volcano (Gunung Api)
 *
 * Documentation: https://docs.petabencana.id/
 * Base URL: https://api.petabencana.id/
 */
interface PetaBencanaApiService {

    /**
     * Get crowdsourced disaster reports.
     *
     * @param timeperiod Seconds from now (default 3600 = 1 hour, max 604800 = 7 days)
     * @param disaster Filter by disaster type: flood, earthquake, wind, haze, fire, volcano
     * @param admin Filter by province code (e.g., "ID-JK" for Jakarta)
     */
    @GET("reports")
    suspend fun getReports(
        @Query("timeperiod") timeperiod: Int = 604800,
        @Query("disaster") disaster: String? = null,
        @Query("admin") admin: String? = null
    ): PetaBencanaResponse

    companion object {
        const val BASE_URL = "https://api.petabencana.id/"
    }
}

// ═══════════════════════════════════════════════════
//  PetaBencana Topology/GeoJSON Response Models
// ═══════════════════════════════════════════════════

/**
 * Top-level response wrapper
 */
data class PetaBencanaResponse(
    val statusCode: Int?,
    val result: PetaBencanaResult?
)

data class PetaBencanaResult(
    val type: String?,              // "Topology"
    val objects: PetaBencanaObjects?,
    val arcs: List<Any>?,
    val bbox: List<Double>?         // [minLon, minLat, maxLon, maxLat]
)

data class PetaBencanaObjects(
    val output: PetaBencanaOutput?
)

data class PetaBencanaOutput(
    val type: String?,              // "GeometryCollection"
    val geometries: List<PetaBencanaGeometry>?
)

data class PetaBencanaGeometry(
    val type: String?,              // "Point"
    val properties: PetaBencanaProperties?,
    val coordinates: List<Double>?  // [longitude, latitude]
) {
    val longitude: Double get() = coordinates?.getOrNull(0) ?: 0.0
    val latitude: Double get() = coordinates?.getOrNull(1) ?: 0.0
}

data class PetaBencanaProperties(
    val pkey: String?,
    val created_at: String?,        // ISO 8601
    val source: String?,            // "grasp", "qlue", etc.
    val status: String?,            // "confirmed"
    val url: String?,               // UUID for detail page
    val image_url: String?,
    val disaster_type: String?,     // "flood", "earthquake", "wind", "haze", "fire", "volcano"
    val is_training: Boolean?,
    val report_data: PetaBencanaReportData?,
    val tags: PetaBencanaTags?,
    val title: String?,
    val text: String?,              // User-submitted description
    val partner_code: String?,
    val partner_icon: String?
) {
    /** Parse created_at to epoch millis */
    val parsedTimeMillis: Long get() {
        return try {
            java.time.Instant.parse(created_at).toEpochMilli()
        } catch (_: Exception) { System.currentTimeMillis() }
    }

    /** Get localized disaster type label */
    val disasterTypeLabel: String get() = when (disaster_type) {
        "flood" -> "Banjir"
        "earthquake" -> "Gempa Bumi"
        "wind" -> "Angin Kencang"
        "haze" -> "Kabut Asap"
        "fire" -> "Kebakaran Hutan"
        "volcano" -> "Gunung Api"
        else -> disaster_type ?: "Unknown"
    }

    /** Get disaster emoji */
    val disasterEmoji: String get() = when (disaster_type) {
        "flood" -> "🌊"
        "earthquake" -> "🌍"
        "wind" -> "💨"
        "haze" -> "🌫️"
        "fire" -> "🔥"
        "volcano" -> "🌋"
        else -> "⚠️"
    }
}

data class PetaBencanaReportData(
    val report_type: String?,           // "flood", "structure", "road", "wind", "volcano", "fire"
    val flood_depth: Int?,              // cm (for flood reports)
    val impact: Int?,                   // 0-1 scale (for wind reports)
    @SerializedName("structureFailure")
    val structureFailure: Int?,         // 0-4 scale (for earthquake reports)
    @SerializedName("accessabilityFailure")
    val accessabilityFailure: Int?,     // Road accessibility (for earthquake reports)
    val condition: Int?,                // Road condition (for earthquake reports)
    @SerializedName("volcanicSigns")
    val volcanicSigns: List<Int>?,      // Volcanic signs observed
    @SerializedName("evacuationNumber")
    val evacuationNumber: Int?,         // # of evacuees
    @SerializedName("evacuationArea")
    val evacuationArea: Boolean?,       // Has evacuation zone
    val points: Int?                    // Severity points
)

data class PetaBencanaTags(
    val city: String?,
    val district_id: String?,
    val region_code: String?,
    val local_area_id: String?,
    val instance_region_code: String?   // "ID-JK", "ID-JB", etc.
)

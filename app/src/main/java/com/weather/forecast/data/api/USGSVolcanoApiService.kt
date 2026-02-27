package com.weather.forecast.data.api

import com.google.gson.annotations.SerializedName
import retrofit2.http.GET
import retrofit2.http.Query

/**
 * USGS Volcano Hazards Program API
 *
 * Provides volcanic activity alert data from USGS.
 * Endpoint returns JSON array of volcano alert notices.
 *
 * Also uses NASA EONET (already integrated) for volcanic events.
 */
interface USGSVolcanoApiService {

    /**
     * Get current volcano alerts/notices.
     */
    @GET("volcanoAlerts")
    suspend fun getVolcanoAlerts(): VolcanoAlertResponse

    companion object {
        const val BASE_URL = "https://volcanoes.usgs.gov/vsc/api/volcanoApi/"
    }
}

// ═══════════════════════════════════════════════════
//  Volcano Response Models
// ═══════════════════════════════════════════════════

/**
 * USGS Volcano API response wrapper.
 * May return as object with "features" or direct list depending on endpoint.
 */
data class VolcanoAlertResponse(
    val type: String? = null,
    val features: List<VolcanoAlertFeature>? = null
)

data class VolcanoAlertFeature(
    val type: String? = null,
    val properties: VolcanoAlertData? = null,
    val geometry: VolcanoGeometry? = null
)

data class VolcanoGeometry(
    val type: String? = null,
    val coordinates: List<Double>? = null  // [lon, lat]
)

data class VolcanoAlertData(
    @SerializedName("vnum")
    val vnum: String? = null,
    @SerializedName("volcano_name")
    val volcanoName: String? = null,
    @SerializedName("alert_level")
    val alertLevel: String? = null,       // NORMAL, ADVISORY, WATCH, WARNING
    @SerializedName("color_code")
    val colorCode: String? = null,        // GREEN, YELLOW, ORANGE, RED
    @SerializedName("observatory_code")
    val observatoryCode: String? = null,
    @SerializedName("date")
    val date: String? = null,
    @SerializedName("message")
    val message: String? = null
)

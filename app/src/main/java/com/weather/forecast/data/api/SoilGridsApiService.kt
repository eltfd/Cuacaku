package com.weather.forecast.data.api

import com.google.gson.annotations.SerializedName
import retrofit2.http.GET
import retrofit2.http.Query

/**
 * SoilGrids ISRIC API Service — Global Soil Information
 *
 * Base URL: https://rest.isric.org/soilgrids/v2.0/
 * Documentation: https://www.isric.org/explore/soilgrids
 *
 * Features:
 * - Free & Open Source (Creative Commons BY 4.0)
 * - No API key required
 * - Global coverage — resolusi 250m
 * - Data tanah berdasarkan machine learning dari 230.000+ profil tanah dunia
 *
 * Provides:
 * - Clay content (g/kg) — kadar lempung
 * - Sand content (g/kg) — kadar pasir
 * - Silt content (g/kg) — kadar debu
 * - SOC (Soil Organic Carbon, dg/kg) — karbon organik tanah
 *
 * Used for:
 * - Soil type classification → landslide/subsidence susceptibility
 * - Clay-rich soil retains water → higher landslide risk on slopes
 * - Sandy/loose soil → higher ground subsidence risk with water saturation
 *
 * Referensi:
 * - Hengl et al. (2017): SoilGrids250m — Global gridded soil information
 *   based on machine learning. PLOS ONE 12(2): e0169748
 * - PVMBG (2019): Korelasi tipe tanah–longsor di Indonesia
 */
interface SoilGridsApiService {

    /**
     * Get soil properties at a specific location.
     *
     * @param lat Latitude (-90 to 90)
     * @param lon Longitude (-180 to 180)
     * @param property Soil property to query (comma-separated):
     *   - "clay" = Clay content (g/kg)
     *   - "sand" = Sand content (g/kg)
     *   - "silt" = Silt content (g/kg)
     *   - "soc"  = Soil Organic Carbon (dg/kg)
     * @param depth Depth interval (e.g. "0-5cm", "5-15cm", "15-30cm")
     * @param value Statistic to return: "mean", "Q0.05", "Q0.5", "Q0.95", "uncertainty"
     * @return SoilGridsResponse with soil property values
     */
    @GET("properties/query")
    suspend fun getSoilProperties(
        @Query("lat") lat: Double,
        @Query("lon") lon: Double,
        @Query("property") property: String = "clay,sand,silt,soc",
        @Query("depth") depth: String = "0-5cm,5-15cm,15-30cm",
        @Query("value") value: String = "mean"
    ): SoilGridsResponse

    companion object {
        const val BASE_URL = "https://rest.isric.org/soilgrids/v2.0/"
    }
}

// ══════════════════════════════════════════════════
//  Response Models
// ══════════════════════════════════════════════════

/**
 * Root response from SoilGrids API
 *
 * Response structure example:
 * ```json
 * {
 *   "type": "Point",
 *   "geometry": { "type": "Point", "coordinates": [lon, lat] },
 *   "properties": {
 *     "layers": [
 *       {
 *         "name": "clay",
 *         "unit_measure": { "mapped_units": "g/kg" },
 *         "depths": [
 *           { "label": "0-5cm", "range": {...}, "values": { "mean": 250 } }
 *         ]
 *       }
 *     ]
 *   }
 * }
 * ```
 */
data class SoilGridsResponse(
    val type: String?,
    val geometry: SoilGridsGeometry?,
    val properties: SoilGridsProperties?
)

data class SoilGridsGeometry(
    val type: String?,
    val coordinates: List<Double>?
)

data class SoilGridsProperties(
    val layers: List<SoilGridsLayer>?
)

data class SoilGridsLayer(
    val name: String?,
    @SerializedName("unit_measure")
    val unitMeasure: SoilGridsUnit?,
    val depths: List<SoilGridsDepth>?
)

data class SoilGridsUnit(
    @SerializedName("mapped_units")
    val mappedUnits: String?,
    @SerializedName("target_units")
    val targetUnits: String?,
    @SerializedName("conversion_factor")
    val conversionFactor: Double?
)

data class SoilGridsDepth(
    val label: String?,
    val range: SoilGridsRange?,
    val values: SoilGridsValues?
)

data class SoilGridsRange(
    @SerializedName("top_depth")
    val topDepth: Int?,
    @SerializedName("bottom_depth")
    val bottomDepth: Int?,
    @SerializedName("unit_depth")
    val unitDepth: String?
)

data class SoilGridsValues(
    val mean: Int?,     // Mean value at this depth
    @SerializedName("Q0.05")
    val q005: Int?,     // 5th percentile
    @SerializedName("Q0.5")
    val q050: Int?,     // Median
    @SerializedName("Q0.95")
    val q095: Int?,     // 95th percentile
    val uncertainty: Int?
)

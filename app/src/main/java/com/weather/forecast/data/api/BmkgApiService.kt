package com.weather.forecast.data.api

import retrofit2.http.GET

/**
 * BMKG (Badan Meteorologi, Klimatologi, dan Geofisika) API Service
 *
 * Menyediakan data gempa bumi resmi dari BMKG Indonesia.
 * - Real-time, gratis, tanpa API key
 * - Data khusus wilayah Indonesia dan sekitarnya
 * - Lebih akurat untuk gempa Indonesia dibanding USGS
 *
 * Endpoints:
 * - autogempa.json → Gempa terbaru (1 event)
 * - gempaterkini.json → 15 gempa terkini M ≥ 5.0
 *
 * Base URL: https://data.bmkg.go.id/DataMKG/TEWS/
 */
interface BmkgApiService {

    /**
     * Get the latest earthquake detected by BMKG.
     * Returns single event with full detail including felt reports & shakemap.
     */
    @GET("autogempa.json")
    suspend fun getAutoGempa(): BmkgAutoGempaResponse

    /**
     * Get 15 most recent earthquakes with M ≥ 5.0.
     * Updated every few minutes.
     */
    @GET("gempaterkini.json")
    suspend fun getGempaTerkini(): BmkgGempaTerkiniResponse

    companion object {
        const val BASE_URL = "https://data.bmkg.go.id/DataMKG/TEWS/"
    }
}

// ═══════════════════════════════════════════════════
//  BMKG Response Models
// ═══════════════════════════════════════════════════

/**
 * Response for autogempa.json (single latest earthquake)
 */
data class BmkgAutoGempaResponse(
    val Infogempa: BmkgInfogempaAuto?
)

data class BmkgInfogempaAuto(
    val gempa: BmkgGempaDetail?
)

/**
 * Response for gempaterkini.json (array of recent M5.0+ earthquakes)
 */
data class BmkgGempaTerkiniResponse(
    val Infogempa: BmkgInfogempaList?
)

data class BmkgInfogempaList(
    val gempa: List<BmkgGempaDetail>?
)

/**
 * Individual earthquake detail from BMKG.
 *
 * Field examples:
 * - Tanggal: "27 Feb 2026"
 * - Jam: "03:12:27 WIB"
 * - DateTime: "2026-02-26T20:12:27+00:00"
 * - Coordinates: "-3.76,133.52"
 * - Lintang: "3.76 LS"
 * - Bujur: "133.52 BT"
 * - Magnitude: "3.8"
 * - Kedalaman: "31 km"
 * - Wilayah: "Pusat gempa berada di darat 35 km BaratLaut Kaimana"
 * - Potensi: "Gempa ini dirasakan untuk diteruskan ke masyarakat"
 * - Dirasakan: "II-III Kaimana" (only in autogempa)
 * - Shakemap: "20260226201227.mmi.jpg" (only in autogempa)
 */
data class BmkgGempaDetail(
    val Tanggal: String?,
    val Jam: String?,
    val DateTime: String?,
    val Coordinates: String?,    // "-3.76,133.52"
    val Lintang: String?,        // "3.76 LS"
    val Bujur: String?,          // "133.52 BT"
    val Magnitude: String?,      // "3.8"
    val Kedalaman: String?,      // "31 km"
    val Wilayah: String?,
    val Potensi: String?,
    val Dirasakan: String?,      // Only in autogempa
    val Shakemap: String?        // Only in autogempa
) {
    /** Parse latitude from Coordinates field */
    val parsedLatitude: Double get() {
        return try {
            Coordinates?.split(",")?.getOrNull(0)?.trim()?.toDouble() ?: 0.0
        } catch (_: Exception) { 0.0 }
    }

    /** Parse longitude from Coordinates field */
    val parsedLongitude: Double get() {
        return try {
            Coordinates?.split(",")?.getOrNull(1)?.trim()?.toDouble() ?: 0.0
        } catch (_: Exception) { 0.0 }
    }

    /** Parse magnitude as Double */
    val parsedMagnitude: Double get() {
        return try {
            Magnitude?.toDouble() ?: 0.0
        } catch (_: Exception) { 0.0 }
    }

    /** Parse depth in km from "31 km" format */
    val parsedDepthKm: Double get() {
        return try {
            Kedalaman?.replace("km", "")?.trim()?.toDouble() ?: 0.0
        } catch (_: Exception) { 0.0 }
    }

    /** Parse ISO DateTime to epoch millis */
    val parsedTimeMillis: Long get() {
        return try {
            java.time.OffsetDateTime.parse(DateTime).toInstant().toEpochMilli()
        } catch (_: Exception) { System.currentTimeMillis() }
    }

    /** Shakemap image URL */
    val shakemapUrl: String? get() {
        return Shakemap?.let { "https://data.bmkg.go.id/DataMKG/TEWS/$it" }
    }
}

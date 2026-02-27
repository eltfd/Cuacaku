package com.weather.forecast.data.api

import com.weather.forecast.data.model.ElevationResponse
import retrofit2.http.GET
import retrofit2.http.Query

/**
 * Open-Elevation API Service — Terrain Elevation Data
 *
 * Base URL: https://api.open-elevation.com/api/v1/
 * Documentation: https://open-elevation.com/
 *
 * Features:
 * - Free & Open Source
 * - No API key required
 * - Global coverage (SRTM 30m resolution)
 * - Returns elevation in meters above sea level
 *
 * Used for:
 * - Calculating terrain slope gradient for landslide risk
 * - Elevation difference analysis between nearby points
 * - Terrain steepness estimation
 */
interface ElevationApiService {

    /**
     * Get elevation for multiple locations.
     *
     * @param locations Pipe-separated lat,lon pairs (e.g. "41.161758,-8.583933|40.1,-7.5")
     * @return ElevationResponse with elevation for each point
     */
    @GET("lookup")
    suspend fun getElevation(
        @Query("locations") locations: String
    ): ElevationResponse

    companion object {
        const val BASE_URL = "https://api.open-elevation.com/api/v1/"

        /**
         * Build locations query string from a list of lat/lon pairs.
         * Open-Elevation expects "lat1,lon1|lat2,lon2|..." format.
         */
        fun buildLocationsQuery(points: List<Pair<Double, Double>>): String {
            return points.joinToString("|") { "${it.first},${it.second}" }
        }

        /**
         * Generate grid of points around center for slope calculation.
         * Creates a small grid (~500m spacing) to determine terrain gradient.
         *
         * @param centerLat Center latitude
         * @param centerLon Center longitude
         * @param deltaKm Distance offset in km (default 0.5 km)
         * @return List of 5 points: center + N/S/E/W offsets
         */
        fun generateSlopeGrid(
            centerLat: Double,
            centerLon: Double,
            deltaKm: Double = 0.5
        ): List<Pair<Double, Double>> {
            // Approximate degree offset for given km
            val latDelta = deltaKm / 111.32  // 1° latitude ≈ 111.32 km
            val lonDelta = deltaKm / (111.32 * kotlin.math.cos(Math.toRadians(centerLat)))

            return listOf(
                Pair(centerLat, centerLon),                          // Center
                Pair(centerLat + latDelta, centerLon),               // North
                Pair(centerLat - latDelta, centerLon),               // South
                Pair(centerLat, centerLon + lonDelta),               // East
                Pair(centerLat, centerLon - lonDelta)                // West
            )
        }
    }
}

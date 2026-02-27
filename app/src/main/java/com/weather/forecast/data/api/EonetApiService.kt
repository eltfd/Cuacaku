package com.weather.forecast.data.api

import com.weather.forecast.data.model.EonetResponse
import retrofit2.http.GET
import retrofit2.http.Query

/**
 * NASA EONET API Service — Earth Observatory Natural Event Tracker
 *
 * Base URL: https://eonet.gsfc.nasa.gov/api/v3/
 * Documentation: https://eonet.gsfc.nasa.gov/docs/v3
 *
 * Features:
 * - Free & Open Source (NASA)
 * - No API key required
 * - Global coverage of natural events
 * - Real-time tracking of ongoing events
 * - Historical event data
 *
 * Categories relevant to landslides:
 * - landslides (category ID: "landslides")
 * - also related: severeStorms, floods, volcanoes
 *
 * Used for:
 * - Real-time monitoring of active landslide events worldwide
 * - Tracking affected areas and timeline
 * - Enriching disaster monitor with NASA satellite-detected events
 */
interface EonetApiService {

    /**
     * Get recent natural events filtered by category.
     *
     * @param category Event category (e.g., "landslides", "floods")
     * @param days Number of days to look back
     * @param status Event status ("open" = ongoing, "closed" = ended)
     * @param limit Maximum number of events
     */
    @GET("events")
    suspend fun getEvents(
        @Query("category") category: String = "landslides",
        @Query("days") days: Int = 90,
        @Query("status") status: String = "open",
        @Query("limit") limit: Int = 50
    ): EonetResponse

    /**
     * Get all event categories available.
     * Useful for discovering category IDs.
     */
    @GET("events")
    suspend fun getAllRecentEvents(
        @Query("days") days: Int = 30,
        @Query("status") status: String = "open",
        @Query("limit") limit: Int = 100
    ): EonetResponse

    companion object {
        const val BASE_URL = "https://eonet.gsfc.nasa.gov/api/v3/"

        // Category IDs
        const val CATEGORY_LANDSLIDES = "landslides"
        const val CATEGORY_FLOODS = "floods"
        const val CATEGORY_SEVERE_STORMS = "severeStorms"
        const val CATEGORY_VOLCANOES = "volcanoes"
    }
}

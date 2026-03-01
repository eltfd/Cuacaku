package com.weather.forecast.data.api

import com.weather.forecast.data.model.GeocodingResponse
import com.weather.forecast.data.model.SearchResult
import retrofit2.http.GET
import retrofit2.http.Query

/**
 * Nominatim Geocoding API Interface
 * 
 * Base URL: https://nominatim.openstreetmap.org/
 * Documentation: https://nominatim.org/release-docs/latest/api/Overview/
 * 
 * Features:
 * - Free & Open Source (OpenStreetMap)
 * - Reverse geocoding (coordinates to address)
 * - Forward geocoding (search location by name)
 * 
 * Usage Policy:
 * - Max 1 request per second
 * - Provide User-Agent header
 * - Cache results when possible
 */
interface GeocodingApiService {

    /**
     * Reverse geocoding - get location name from coordinates
     * 
     * @param latitude Location latitude
     * @param longitude Location longitude
     * @param format Response format (json)
     * @param addressdetails Include address breakdown
     * @param zoom Level of detail (18 = building, 10 = city)
     * 
     * Example:
     * ```
     * api.reverseGeocode(-6.2088, 106.8456)
     * // Returns: "Jakarta, DKI Jakarta, Indonesia"
     * ```
     */
    @GET("reverse")
    suspend fun reverseGeocode(
        @Query("lat") latitude: Double,
        @Query("lon") longitude: Double,
        @Query("format") format: String = "json",
        @Query("addressdetails") addressDetails: Int = 1,
        @Query("zoom") zoom: Int = 14
    ): GeocodingResponse

    /**
     * Search location by name
     * 
     * @param query Search query (city name, address, etc)
     * @param format Response format
     * @param addressdetails Include address breakdown
     * @param limit Max results to return
     * 
     * Example:
     * ```
     * api.searchLocation("Jakarta")
     * // Returns list of matching locations
     * ```
     */
    @GET("search")
    suspend fun searchLocation(
        @Query("q") query: String,
        @Query("format") format: String = "json",
        @Query("addressdetails") addressDetails: Int = 1,
        @Query("limit") limit: Int = 10
    ): List<SearchResult>

    /**
     * Search for nearby features within a bounding box
     *
     * @param query Search query (e.g., "river", "sungai")
     * @param viewbox Bounding box: "lon1,lat1,lon2,lat2" (corners)
     * @param bounded 1 = restrict to viewbox, 0 = prefer viewbox
     * @param format Response format
     * @param limit Max results
     *
     * Example:
     * ```
     * api.searchBounded("river", "106.5,-6.3,107.0,-6.7", bounded = 1)
     * // Returns nearby river features within the bounding box
     * ```
     */
    @GET("search")
    suspend fun searchBounded(
        @Query("q") query: String,
        @Query("viewbox") viewbox: String,
        @Query("bounded") bounded: Int = 1,
        @Query("format") format: String = "json",
        @Query("addressdetails") addressDetails: Int = 0,
        @Query("limit") limit: Int = 5
    ): List<SearchResult>

    companion object {
        const val BASE_URL = "https://nominatim.openstreetmap.org/"
    }
}

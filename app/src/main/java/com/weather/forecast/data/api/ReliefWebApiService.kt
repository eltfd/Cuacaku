package com.weather.forecast.data.api

import com.weather.forecast.data.model.ReliefWebDisasterResponse
import com.weather.forecast.data.model.ReliefWebReportResponse
import retrofit2.http.*

/**
 * ReliefWeb API Service — UN OCHA Disaster Data
 *
 * Base URL: https://api.reliefweb.int/v1/
 * Dokumentasi: https://apidoc.rwlabs.org/
 *
 * Features:
 * - Free & Open Source (UN-hosted)
 * - Tidak perlu API key
 * - Data bencana global (worldwide coverage)
 * - Filter per negara, jenis bencana, dan status
 * - Format: JSON
 *
 * Digunakan untuk:
 * - Memantau bencana aktif yang sudah terjadi (banjir, longsor, dll.)
 * - Mendapatkan update terkini tentang recovery
 * - Menampilkan area yang terdampak
 * - Mendukung seluruh negara di dunia (dinamis berdasarkan lokasi user)
 */
interface ReliefWebApiService {

    /**
     * Get active disasters.
     *
     * Filter bencana berdasarkan negara dan status.
     * ReliefWeb menggunakan status: "alert", "ongoing", "past"
     *
     * @param appName App identifier (required by ReliefWeb ToS)
     * @param fields Fields to return
     * @param filterField Filter field name
     * @param filterValue Filter value
     * @param filterField2 Second filter field (country)
     * @param filterValue2 Country name
     * @param sort Sort field
     * @param limit Max results
     */
    @GET("disasters")
    suspend fun getDisasters(
        @Query("appname") appName: String = APP_NAME,
        @Query("fields[include][]") fields: List<String> = DISASTER_FIELDS,
        @Query("filter[field]") filterField: String = "status",
        @Query("filter[value]") filterValue: String = "ongoing",
        @Query("sort[]") sort: String = "date.event:desc",
        @Query("limit") limit: Int = 30
    ): ReliefWebDisasterResponse

    /**
     * Get disasters for a specific country with combined filters.
     *
     * ReliefWeb uses a query body format for complex filters,
     * but we can also use nested query params for simpler cases.
     *
     * @param filterValue1 Country ISO3 code (e.g. "IDN", "USA", "JPN", "DEU")
     *                     — dynamic, based on user's location
     */
    @GET("disasters")
    suspend fun getDisastersByCountry(
        @Query("appname") appName: String = APP_NAME,
        @Query("fields[include][]") fields: List<String> = DISASTER_FIELDS,
        @Query("filter[operator]") filterOperator: String = "AND",
        @Query("filter[conditions][0][field]") filterField1: String = "country.iso3",
        @Query("filter[conditions][0][value]") filterValue1: String,  // Dynamic: user's country ISO3
        @Query("filter[conditions][1][field]") filterField2: String = "status",
        @Query("filter[conditions][1][value]") filterValue2: String = "ongoing",
        @Query("sort[]") sort: String = "date.event:desc",
        @Query("limit") limit: Int = 20
    ): ReliefWebDisasterResponse

    /**
     * Get recent reports untuk disaster tertentu (detail / update).
     *
     * @param countryIso3 Country ISO3 code — dynamic, based on user's location
     */
    @GET("reports")
    suspend fun getDisasterReports(
        @Query("appname") appName: String = APP_NAME,
        @Query("fields[include][]") fields: List<String> = REPORT_FIELDS,
        @Query("filter[operator]") filterOperator: String = "AND",
        @Query("filter[conditions][0][field]") filterField1: String = "disaster.id",
        @Query("filter[conditions][0][value]") disasterId: Int,
        @Query("filter[conditions][1][field]") filterField2: String = "primary_country.iso3",
        @Query("filter[conditions][1][value]") countryIso3: String,  // Dynamic: user's country ISO3
        @Query("sort[]") sort: String = "date.created:desc",
        @Query("limit") limit: Int = 5
    ): ReliefWebReportResponse

    /**
     * Get all recent disasters (global, untuk area terdekat user).
     */
    @GET("disasters")
    suspend fun getRecentDisasters(
        @Query("appname") appName: String = APP_NAME,
        @Query("fields[include][]") fields: List<String> = DISASTER_FIELDS,
        @Query("filter[field]") filterField: String = "date.event",
        @Query("filter[value][from]") dateFrom: String,
        @Query("sort[]") sort: String = "date.event:desc",
        @Query("limit") limit: Int = 50
    ): ReliefWebDisasterResponse

    companion object {
        const val BASE_URL = "https://api.reliefweb.int/v1/"
        const val APP_NAME = "cuacaku-weather-app"

        val DISASTER_FIELDS = listOf(
            "name", "description", "status", "glide",
            "date", "country", "type", "primary_type",
            "url", "url_alias"
        )

        val REPORT_FIELDS = listOf(
            "title", "body", "date", "origin",
            "primary_country", "disaster", "url"
        )
    }
}

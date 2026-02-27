package com.weather.forecast.data.model

import com.google.gson.annotations.SerializedName

/**
 * ReliefWeb API Response Models
 *
 * ReliefWeb (https://reliefweb.int) adalah platform UN OCHA
 * yang mengumpulkan informasi bencana global.
 *
 * API: https://api.reliefweb.int/v1/
 * Dokumentasi: https://apidoc.rwlabs.org/
 * Gratis, tanpa API key.
 */

/**
 * Response dari ReliefWeb /disasters endpoint
 */
data class ReliefWebDisasterResponse(
    @SerializedName("totalCount") val totalCount: Int = 0,
    @SerializedName("count") val count: Int = 0,
    @SerializedName("data") val data: List<ReliefWebDisasterItem> = emptyList()
)

data class ReliefWebDisasterItem(
    @SerializedName("id") val id: String = "",
    @SerializedName("score") val score: Double? = null,
    @SerializedName("fields") val fields: ReliefWebDisasterFields? = null
)

data class ReliefWebDisasterFields(
    @SerializedName("id") val id: Int = 0,
    @SerializedName("name") val name: String = "",
    @SerializedName("description") val description: String? = null,
    @SerializedName("status") val status: String? = null,
    @SerializedName("glide") val glide: String? = null,
    @SerializedName("date") val date: ReliefWebDate? = null,
    @SerializedName("country") val country: List<ReliefWebCountry>? = null,
    @SerializedName("type") val type: List<ReliefWebType>? = null,
    @SerializedName("primary_type") val primaryType: ReliefWebType? = null,
    @SerializedName("url") val url: String? = null,
    @SerializedName("url_alias") val urlAlias: String? = null
)

data class ReliefWebDate(
    @SerializedName("created") val created: String? = null,
    @SerializedName("changed") val changed: String? = null,
    @SerializedName("event") val event: String? = null
)

data class ReliefWebCountry(
    @SerializedName("id") val id: Int = 0,
    @SerializedName("name") val name: String = "",
    @SerializedName("iso3") val iso3: String? = null,
    @SerializedName("location") val location: ReliefWebLocation? = null
)

data class ReliefWebLocation(
    @SerializedName("lat") val lat: Double? = null,
    @SerializedName("lon") val lon: Double? = null
)

data class ReliefWebType(
    @SerializedName("id") val id: Int = 0,
    @SerializedName("name") val name: String = "",
    @SerializedName("code") val code: String? = null
)

/**
 * Response dari ReliefWeb /reports endpoint (untuk detail bencana)
 */
data class ReliefWebReportResponse(
    @SerializedName("totalCount") val totalCount: Int = 0,
    @SerializedName("count") val count: Int = 0,
    @SerializedName("data") val data: List<ReliefWebReportItem> = emptyList()
)

data class ReliefWebReportItem(
    @SerializedName("id") val id: String = "",
    @SerializedName("fields") val fields: ReliefWebReportFields? = null
)

data class ReliefWebReportFields(
    @SerializedName("id") val id: Int = 0,
    @SerializedName("title") val title: String = "",
    @SerializedName("body") val body: String? = null,
    @SerializedName("date") val date: ReliefWebDate? = null,
    @SerializedName("origin") val origin: String? = null,
    @SerializedName("primary_country") val primaryCountry: ReliefWebCountry? = null,
    @SerializedName("disaster") val disaster: List<ReliefWebDisasterRef>? = null,
    @SerializedName("url") val url: String? = null
)

data class ReliefWebDisasterRef(
    @SerializedName("id") val id: Int = 0,
    @SerializedName("name") val name: String = "",
    @SerializedName("glide") val glide: String? = null
)

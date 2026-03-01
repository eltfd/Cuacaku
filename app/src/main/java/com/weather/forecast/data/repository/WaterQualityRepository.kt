package com.weather.forecast.data.repository

import com.weather.forecast.data.api.RetrofitClient
import com.weather.forecast.data.locale.AppLocaleManager
import com.weather.forecast.data.model.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

/**
 * Water Quality Repository
 *
 * Mengelola data kualitas air dari:
 * - Open-Meteo Marine API (kondisi laut)
 * - Open-Meteo Flood API (debit sungai, GloFAS)
 *
 * Responsibilities:
 * - Fetch marine & flood data secara paralel
 * - Transform ke UI-ready models
 * - **Hitung tren tinggi muka air** (naik/turun/stabil)
 * - Bangun ringkasan (WaterLevelSummary) untuk overview card
 * - Handle errors gracefully (satu API gagal tidak mempengaruhi lainnya)
 */
class WaterQualityRepository {

    private val marineApi = RetrofitClient.marineApi
    private val floodApi = RetrofitClient.floodApi
    private val geocodingApi = RetrofitClient.geocodingApi

    /**
     * Get complete water quality data
     * Marine dan Flood di-fetch paralel, error di satu tidak mempengaruhi yang lain
     */
    suspend fun getWaterQualityData(latitude: Double, longitude: Double): Result<WaterQualityData> {
        return withContext(Dispatchers.IO) {
            try {
                val marineDeferred = async {
                    try {
                        val response = marineApi.getMarineData(latitude, longitude)
                        transformMarineData(response)
                    } catch (e: Exception) {
                        null // Marine data might not be available for inland locations
                    }
                }

                val floodDeferred = async {
                    try {
                        val response = floodApi.getFloodData(latitude, longitude)
                        transformFloodData(response)
                    } catch (e: Exception) {
                        null // Flood data might not be available for all locations
                    }
                }

                // Lookup nearest river name in parallel
                val riverNameDeferred = async {
                    findNearbyRiverName(latitude, longitude)
                }

                val marine = marineDeferred.await()
                val flood = floodDeferred.await()
                val nearbyRiverName = riverNameDeferred.await()

                // Bangun ringkasan tren tinggi muka air
                val summary = buildWaterLevelSummary(marine, flood)

                // Resolve sea name only if marine data is available (user near coast)
                val hasValidMarineData = marine != null &&
                    marine.dailyForecast.any { it.waveHeightMax > 0.0 }
                val locale = if (AppLocaleManager.locale == com.weather.forecast.data.locale.AppLocale.ID) "id" else "en"
                val nearbySeaName = if (hasValidMarineData) {
                    NearbySeaResolver.findNearestSea(latitude, longitude, locale)
                } else null

                Result.success(
                    WaterQualityData(
                        marine = marine,
                        flood = flood,
                        waterLevelSummary = summary,
                        nearbySeaName = nearbySeaName,
                        nearbyRiverName = nearbyRiverName
                    )
                )
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    // ===================== NEARBY WATER BODY LOOKUP =====================

    /**
     * Cari nama sungai terdekat dari Nominatim (OpenStreetMap).
     * Menggunakan bounded viewbox dalam radius ~2 km dari posisi user.
     * Data & lokasi user di-update berkala → sungai terdekat mengikuti perpindahan user.
     */
    private suspend fun findNearbyRiverName(latitude: Double, longitude: Double): String? {
        return try {
            val delta = 0.018 // ~2 km search radius (1° ≈ 111 km)
            val viewbox = "${longitude - delta},${latitude - delta},${longitude + delta},${latitude + delta}"

            val results = geocodingApi.searchBounded(
                query = "river",
                viewbox = viewbox,
                bounded = 1,
                limit = 5
            )

            // Filter only actual waterway/river results
            val rivers = results.filter { result ->
                result.type == "river" || result.type == "stream" ||
                result.addressType == "river" ||
                (result.osmClass == "waterway" && result.type in listOf("river", "canal"))
            }

            // Pick the most important (largest) river
            val bestRiver = rivers.maxByOrNull { it.importance }
                ?: results.firstOrNull { it.type == "river" }

            bestRiver?.name?.let { name ->
                // Clean up: some OSM entries have "Sungai X" in name
                // Return as-is — it's already properly named
                name.ifBlank { null }
            }
        } catch (e: Exception) {
            null // Fallback gracefully
        }
    }

    // ===================== WATER LEVEL SUMMARY =====================

    /**
     * Bangun ringkasan tinggi muka air dari data marine & flood.
     *
     * Tren dihitung dengan membandingkan data hari ini vs besok.
     * Jika data besok tidak tersedia, bandingkan hari ini vs rata-rata 7 hari.
     */
    private fun buildWaterLevelSummary(marine: MarineData?, flood: FloodData?): WaterLevelSummary {
        var seaWaveTrend = WaterLevelTrend.STABLE
        var currentWave = 0.0
        var tomorrowWave = 0.0
        var waveChangePct = 0.0
        val hasMarineData = marine != null && marine.dailyForecast.isNotEmpty()

        if (hasMarineData) {
            val dailyMarine = marine!!.dailyForecast
            currentWave = dailyMarine.firstOrNull()?.waveHeightMax ?: 0.0
            tomorrowWave = dailyMarine.getOrNull(1)?.waveHeightMax ?: currentWave

            waveChangePct = if (currentWave > 0) {
                ((tomorrowWave - currentWave) / currentWave) * 100.0
            } else 0.0

            // Tren keseluruhan: bandingkan rata-rata 3 hari pertama vs 3 hari terakhir
            seaWaveTrend = calculateOverallTrend(dailyMarine.map { it.waveHeightMax })
        }

        var riverTrend = WaterLevelTrend.STABLE
        var currentDischarge = 0.0
        var tomorrowDischarge = 0.0
        var dischargeChangePct = 0.0
        val hasFloodData = flood != null && flood.dailyForecast.isNotEmpty()

        if (hasFloodData) {
            val dailyFlood = flood!!.dailyForecast
            currentDischarge = dailyFlood.firstOrNull()?.riverDischarge ?: 0.0
            tomorrowDischarge = dailyFlood.getOrNull(1)?.riverDischarge ?: currentDischarge

            dischargeChangePct = if (currentDischarge > 0) {
                ((tomorrowDischarge - currentDischarge) / currentDischarge) * 100.0
            } else 0.0

            riverTrend = calculateOverallTrend(dailyFlood.map { it.riverDischarge })
        }

        return WaterLevelSummary(
            seaWaveTrend = seaWaveTrend,
            currentWaveHeight = currentWave,
            tomorrowWaveHeight = tomorrowWave,
            waveChangePercent = waveChangePct,
            riverDischargeTrend = riverTrend,
            currentDischarge = currentDischarge,
            tomorrowDischarge = tomorrowDischarge,
            dischargeChangePercent = dischargeChangePct,
            hasMarineData = hasMarineData,
            hasFloodData = hasFloodData
        )
    }

    /**
     * Hitung tren keseluruhan dari deret data harian.
     * Membandingkan rata-rata paruh pertama vs paruh kedua.
     */
    private fun calculateOverallTrend(values: List<Double>): WaterLevelTrend {
        if (values.size < 2) return WaterLevelTrend.STABLE

        val half = values.size / 2
        val firstHalf = values.take(half).average()
        val secondHalf = values.takeLast(half).average()

        val changePct = if (firstHalf > 0) {
            ((secondHalf - firstHalf) / firstHalf) * 100.0
        } else 0.0

        return WaterLevelTrend.fromChangePercent(changePct)
    }

    /**
     * Hitung persentase perubahan antara dua nilai
     */
    private fun changePercent(previous: Double, current: Double): Double {
        return if (previous > 0) ((current - previous) / previous) * 100.0 else 0.0
    }

    // ===================== MARINE TRANSFORM =====================

    private fun transformMarineData(response: MarineResponse): MarineData {
        val current = response.current?.let { c ->
            CurrentMarineData(
                waveHeight = c.waveHeight ?: 0.0,
                waveDirection = c.waveDirection ?: 0,
                wavePeriod = c.wavePeriod ?: 0.0,
                swellWaveHeight = c.swellWaveHeight ?: 0.0,
                swellWaveDirection = c.swellWaveDirection ?: 0,
                swellWavePeriod = c.swellWavePeriod ?: 0.0,
                seaCondition = SeaCondition.fromWaveHeight(c.waveHeight ?: 0.0)
            )
        }

        val hourlyList = transformHourlyMarine(response.hourly)
        val hourlyByDate = groupMarineHourlyByDate(hourlyList)
        val dailyList = transformDailyMarine(response.daily, hourlyByDate)

        return MarineData(
            current = current,
            hourlyForecast = hourlyList,
            dailyForecast = dailyList
        )
    }

    private fun transformHourlyMarine(hourly: HourlyMarine?): List<HourlyMarineData> {
        if (hourly == null) return emptyList()

        val formatter = DateTimeFormatter.ISO_DATE_TIME
        val hourFormatter = DateTimeFormatter.ofPattern("HH:mm")

        return hourly.time.mapIndexedNotNull { index, timeStr ->
            try {
                val time = LocalDateTime.parse(timeStr, formatter)
                val waveHeight = hourly.waveHeight?.getOrNull(index) ?: 0.0

                HourlyMarineData(
                    time = timeStr,
                    hour = time.format(hourFormatter),
                    waveHeight = waveHeight,
                    waveDirection = hourly.waveDirection?.getOrNull(index) ?: 0,
                    wavePeriod = hourly.wavePeriod?.getOrNull(index) ?: 0.0,
                    swellWaveHeight = hourly.swellWaveHeight?.getOrNull(index) ?: 0.0,
                    seaCondition = SeaCondition.fromWaveHeight(waveHeight)
                )
            } catch (e: Exception) {
                null
            }
        }
    }

    private fun groupMarineHourlyByDate(hourlyList: List<HourlyMarineData>): Map<String, List<HourlyMarineData>> {
        val formatter = DateTimeFormatter.ISO_DATE_TIME
        val dateFormatter = DateTimeFormatter.ISO_DATE

        return hourlyList.groupBy { item ->
            try {
                val time = LocalDateTime.parse(item.time, formatter)
                time.toLocalDate().format(dateFormatter)
            } catch (e: Exception) {
                ""
            }
        }.filterKeys { it.isNotEmpty() }
    }

    private fun transformDailyMarine(
        daily: DailyMarine?,
        hourlyByDate: Map<String, List<HourlyMarineData>>
    ): List<DailyMarineData> {
        if (daily == null) return emptyList()

        val dateFormatter = DateTimeFormatter.ISO_DATE
        val locale = Locale("id", "ID")

        // Buat list terlebih dahulu tanpa tren
        val rawList = daily.time.mapIndexedNotNull { index, dateStr ->
            try {
                val date = LocalDate.parse(dateStr, dateFormatter)
                val dayName = date.dayOfWeek.getDisplayName(TextStyle.FULL, locale)
                val waveMax = daily.waveHeightMax?.getOrNull(index) ?: 0.0

                DailyMarineData(
                    date = dateStr,
                    dayName = dayName,
                    waveHeightMax = waveMax,
                    waveDirectionDominant = daily.waveDirectionDominant?.getOrNull(index) ?: 0,
                    wavePeriodMax = daily.wavePeriodMax?.getOrNull(index) ?: 0.0,
                    swellWaveHeightMax = daily.swellWaveHeightMax?.getOrNull(index) ?: 0.0,
                    seaCondition = SeaCondition.fromWaveHeight(waveMax),
                    hourlyForecasts = hourlyByDate[dateStr] ?: emptyList()
                )
            } catch (e: Exception) {
                null
            }
        }

        // Tambahkan tren: bandingkan tiap hari dengan hari sebelumnya
        return rawList.mapIndexed { index, data ->
            if (index == 0) {
                data // hari pertama tanpa tren
            } else {
                val prevWave = rawList[index - 1].waveHeightMax
                val pct = changePercent(prevWave, data.waveHeightMax)
                data.copy(
                    waveTrend = WaterLevelTrend.fromChangePercent(pct),
                    waveChangePercent = pct
                )
            }
        }
    }

    // ===================== FLOOD TRANSFORM =====================

    private fun transformFloodData(response: FloodResponse): FloodData {
        val daily = response.daily ?: return FloodData(dailyForecast = emptyList())

        val dateFormatter = DateTimeFormatter.ISO_DATE
        val locale = Locale("id", "ID")

        // Buat raw list tanpa tren
        val rawList = daily.time.mapIndexedNotNull { index, dateStr ->
            try {
                val date = LocalDate.parse(dateStr, dateFormatter)
                val dayName = date.dayOfWeek.getDisplayName(TextStyle.FULL, locale)

                val discharge = daily.riverDischarge?.getOrNull(index) ?: 0.0
                val mean = daily.riverDischargeMean?.getOrNull(index) ?: 0.0
                val max = daily.riverDischargeMax?.getOrNull(index) ?: 0.0
                val min = daily.riverDischargeMin?.getOrNull(index) ?: 0.0

                DailyFloodData(
                    date = dateStr,
                    dayName = dayName,
                    riverDischarge = discharge,
                    dischargeMean = mean,
                    dischargeMax = max,
                    dischargeMin = min,
                    floodRisk = FloodRisk.fromDischargeRatio(discharge, mean)
                )
            } catch (e: Exception) {
                null
            }
        }

        // Tambahkan tren: bandingkan tiap hari dengan hari sebelumnya
        val withTrend = rawList.mapIndexed { index, data ->
            if (index == 0) {
                data
            } else {
                val prevDischarge = rawList[index - 1].riverDischarge
                val pct = changePercent(prevDischarge, data.riverDischarge)
                data.copy(
                    waterLevelTrend = WaterLevelTrend.fromChangePercent(pct),
                    dischargeChangePercent = pct
                )
            }
        }

        // Hitung tren keseluruhan
        val overallTrend = calculateOverallTrend(rawList.map { it.riverDischarge })

        return FloodData(
            dailyForecast = withTrend,
            overallTrend = overallTrend
        )
    }
}

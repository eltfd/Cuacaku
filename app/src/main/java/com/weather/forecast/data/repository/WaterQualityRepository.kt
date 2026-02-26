package com.weather.forecast.data.repository

import com.weather.forecast.data.api.RetrofitClient
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
 * - Handle errors gracefully (satu API gagal tidak mempengaruhi lainnya)
 */
class WaterQualityRepository {

    private val marineApi = RetrofitClient.marineApi
    private val floodApi = RetrofitClient.floodApi

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

                val marine = marineDeferred.await()
                val flood = floodDeferred.await()

                Result.success(
                    WaterQualityData(
                        marine = marine,
                        flood = flood
                    )
                )
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
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

        return daily.time.mapIndexedNotNull { index, dateStr ->
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
    }

    // ===================== FLOOD TRANSFORM =====================

    private fun transformFloodData(response: FloodResponse): FloodData {
        val daily = response.daily ?: return FloodData(dailyForecast = emptyList())

        val dateFormatter = DateTimeFormatter.ISO_DATE
        val locale = Locale("id", "ID")

        val dailyList = daily.time.mapIndexedNotNull { index, dateStr ->
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

        return FloodData(dailyForecast = dailyList)
    }
}

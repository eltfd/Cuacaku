package com.weather.forecast.data.repository

import com.weather.forecast.data.ai.DisasterNeuralNetwork
import com.weather.forecast.data.ai.WeatherFeatureExtractor
import com.weather.forecast.data.api.RetrofitClient
import com.weather.forecast.data.model.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

/**
 * Disaster Repository
 *
 * Mengumpulkan data dari semua API sumber (cuaca, laut, sungai) lalu
 * meneruskan ke DisasterAnalysisEngine untuk analisis potensi bencana.
 *
 * Data paralel:
 * - Weather API → cuaca, CAPE, angin, hujan, tekanan
 * - Marine API → gelombang, swell → banjir rob
 * - Flood API → debit sungai → banjir
 */
class DisasterRepository {

    private val weatherApi = RetrofitClient.weatherApi
    private val marineApi = RetrofitClient.marineApi
    private val floodApi = RetrofitClient.floodApi

    private val waterQualityRepository = WaterQualityRepository()
    private val weatherRepository = WeatherRepository()

    /**
     * Ambil + analisis prakiraan bencana
     */
    suspend fun getDisasterForecast(
        latitude: Double,
        longitude: Double
    ): Result<DisasterForecast> {
        return withContext(Dispatchers.IO) {
            try {
                // Fetch semua data secara paralel
                val weatherDeferred = async {
                    weatherRepository.getWeatherData(latitude, longitude)
                }
                val waterDeferred = async {
                    waterQualityRepository.getWaterQualityData(latitude, longitude)
                }

                val weatherResult = weatherDeferred.await()
                val waterResult = waterDeferred.await()

                val weather = weatherResult.getOrNull()
                val water = waterResult.getOrNull()

                if (weather == null) {
                    return@withContext Result.failure(
                        Exception("Gagal memuat data cuaca untuk analisis bencana")
                    )
                }

                // ═══ Analisis hari ini ═══
                val todayPredictions = DisasterAnalysisEngine.analyzeToday(
                    weather = weather,
                    marine = water,
                    flood = water
                )

                // ═══ Analisis 7 hari ke depan ═══
                val weeklyPredictions = weather.daily.mapIndexed { index, daily ->
                    val hourlyForDay = daily.hourlyForecasts.ifEmpty {
                        // Ambil 24 hourly data untuk hari ke-index
                        weather.hourly.drop(index * 24).take(24)
                    }

                    val marineDaily = water?.marine?.dailyForecast?.getOrNull(index)
                    val floodDaily = water?.flood?.dailyForecast?.getOrNull(index)

                    val dayPredictions = DisasterAnalysisEngine.analyzeDaily(
                        dayIndex = index,
                        daily = daily,
                        hourlyForDay = hourlyForDay,
                        marineDaily = marineDaily,
                        floodDaily = floodDaily,
                        allDaily = weather.daily
                    )

                    DailyDisasterSummary(
                        date = daily.date,
                        dayName = daily.dayName,
                        predictions = dayPredictions,
                        highestRisk = dayPredictions.maxOfOrNull { it.riskLevel }
                            ?: RiskLevel.LOW
                    )
                }

                // ═══ Summary ═══
                val highestAlert = todayPredictions
                    .filter { it.riskLevel >= RiskLevel.MODERATE }
                    .maxByOrNull { it.riskScore }
                    ?.type

                val overallRisk = todayPredictions
                    .maxOfOrNull { it.riskLevel }
                    ?: RiskLevel.LOW

                val aiSummary = DisasterAnalysisEngine.generateSummary(todayPredictions)

                // ═══ AI Model Info ═══
                val todayFeatures = WeatherFeatureExtractor.extractForToday(weather, water)

                Result.success(
                    DisasterForecast(
                        todayPredictions = todayPredictions,
                        weeklyPredictions = weeklyPredictions,
                        highestAlert = highestAlert,
                        overallRiskLevel = overallRisk,
                        aiSummary = aiSummary,
                        aiModelVersion = DisasterNeuralNetwork.MODEL_VERSION,
                        aiFeatureCount = WeatherFeatureExtractor.FEATURE_COUNT,
                        aiDataCompleteness = todayFeatures.dataCompleteness
                    )
                )
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }
}

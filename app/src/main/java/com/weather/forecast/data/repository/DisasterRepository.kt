package com.weather.forecast.data.repository

import android.content.Context
import android.content.SharedPreferences
import com.weather.forecast.data.ai.DataRetentionManager
import com.weather.forecast.data.ai.DisasterNeuralNetwork
import com.weather.forecast.data.ai.IncrementalLearningEngine
import com.weather.forecast.data.ai.WeatherFeatureExtractor
import com.weather.forecast.data.api.RetrofitClient
import com.weather.forecast.data.locale.AppLocaleManager
import com.weather.forecast.data.model.*
import kotlinx.coroutines.*
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
 * Juga mengelola incremental learning: merekam prediksi, membandingkan
 * dengan observasi aktual, dan mengupdate bobot NN secara otomatis.
 *
 * ── Background Learning ──
 * Learning berjalan di background (fire-and-forget) sehingga:
 * - User langsung melihat forecast tanpa menunggu learning selesai
 * - Learning di-throttle maks 1× per 6 jam untuk hemat resource
 * - Semua I/O (SharedPreferences, Gson) terjadi di Dispatchers.IO
 *
 * Data paralel:
 * - Weather API → cuaca, CAPE, angin, hujan, tekanan
 * - Marine API → gelombang, swell → banjir rob
 * - Flood API → debit sungai → banjir
 */
class DisasterRepository(context: Context) {

    private val weatherApi = RetrofitClient.weatherApi
    private val marineApi = RetrofitClient.marineApi
    private val floodApi = RetrofitClient.floodApi

    private val waterQualityRepository = WaterQualityRepository()
    private val weatherRepository = WeatherRepository()

    // ── AI Learning ──
    private val learningEngine = IncrementalLearningEngine(context)
    private val retentionManager = DataRetentionManager(context)

    // Background scope untuk learning — SupervisorJob agar error learning
    // tidak mempengaruhi forecast. Single thread agar ringan.
    private val learningScope = CoroutineScope(
        SupervisorJob() + Dispatchers.IO
    )

    // Throttle learning: maks 1× per 6 jam
    private val learningPrefs: SharedPreferences =
        context.getSharedPreferences("ai_learning_throttle", Context.MODE_PRIVATE)

    companion object {
        /** Interval minimum antar learning steps (6 jam) */
        private const val LEARNING_THROTTLE_MS = 6 * 3600 * 1000L
        private const val KEY_LAST_LEARN_TIME = "last_learn_time"
    }

    /**
     * Ambil + analisis prakiraan bencana.
     *
     * Learning berjalan di background (fire-and-forget) sehingga:
     * - Forecast langsung dikembalikan ke UI tanpa menunggu learning
     * - Learning di-throttle agar tidak boros resource
     * - Error learning tidak mempengaruhi tampilan forecast
     */
    suspend fun getDisasterForecast(
        latitude: Double,
        longitude: Double
    ): Result<DisasterForecast> {
        return withContext(Dispatchers.IO) {
            try {
                // Ambil learned weight deltas (ringan, dari memory)
                val deltas = learningEngine.getWeightDeltas()

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
                        Exception(AppLocaleManager.strings.failedLoadDisasterForecast)
                    )
                }

                // ═══ Analisis hari ini (dengan learned weights) ═══
                val todayPredictions = DisasterAnalysisEngine.analyzeToday(
                    weather = weather,
                    marine = water,
                    flood = water,
                    weightDeltas = deltas
                )

                // ═══ Background Learning (fire-and-forget) ═══
                // Berjalan di background scope terpisah, tidak blocking UI
                val todayFeatures = WeatherFeatureExtractor.extractForToday(weather, water)
                val ruleScores = todayPredictions.map { it.riskScore.toFloat() }.toFloatArray()

                runLearningInBackground(
                    features = todayFeatures.features,
                    ruleScores = ruleScores,
                    deltas = deltas
                )

                // ═══ Analisis 7 hari ke depan ═══
                val weeklyPredictions = weather.daily.mapIndexed { index, daily ->
                    val hourlyForDay = daily.hourlyForecasts.ifEmpty {
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
                        allDaily = weather.daily,
                        weightDeltas = deltas
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
                val stats = learningEngine.getStats()
                val storageUsage = retentionManager.getStorageUsage()

                Result.success(
                    DisasterForecast(
                        todayPredictions = todayPredictions,
                        weeklyPredictions = weeklyPredictions,
                        highestAlert = highestAlert,
                        overallRiskLevel = overallRisk,
                        aiSummary = aiSummary,
                        aiModelVersion = DisasterNeuralNetwork.MODEL_VERSION,
                        aiFeatureCount = WeatherFeatureExtractor.FEATURE_COUNT,
                        aiDataCompleteness = todayFeatures.dataCompleteness,
                        learningSteps = stats.totalLearningSteps,
                        learningSamples = stats.totalSamples,
                        storageUsed = storageUsage.formattedTotal
                    )
                )
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    // ════════════════════════════════════════════════
    //  Background Learning (Silent, Non-blocking)
    // ════════════════════════════════════════════════

    /**
     * Jalankan learning di background tanpa mengganggu UI.
     *
     * Semua operasi (record, learn, cleanup) berjalan di coroutine
     * terpisah dengan SupervisorJob sehingga:
     * - Error di learning TIDAK propagate ke forecast
     * - User TIDAK perlu menunggu learning selesai
     * - Resource minimal: 1 thread, throttled per 6 jam
     */
    private fun runLearningInBackground(
        features: FloatArray,
        ruleScores: FloatArray,
        deltas: com.weather.forecast.data.ai.WeightDeltas
    ) {
        learningScope.launch {
            try {
                // Auto-cleanup data kadaluarsa (ringan, max 1x/hari)
                retentionManager.performAutoCleanup()

                // Prediksi NN untuk recording
                val nnScores = DisasterNeuralNetwork.predict(features, deltas)

                // Rekam prediksi hari ini (selalu, untuk data tracking)
                learningEngine.recordPrediction(
                    features = features,
                    predictions = nnScores,
                    ruleScores = ruleScores
                )

                // Throttle: hanya learn jika sudah > 6 jam sejak terakhir
                if (!shouldLearn()) return@launch

                // Learn: cuaca hari ini = outcome dari prediksi kemarin
                val actualOutcome = learningEngine.computeActualOutcome(ruleScores)
                learningEngine.learnFromOutcome(actualOutcome)

                // Catat waktu learning terakhir
                markLearned()
            } catch (_: Exception) {
                // Silent fail — learning error tidak boleh ganggu user
            }
        }
    }

    /**
     * Cek apakah sudah waktunya untuk learning step berikutnya.
     * Throttle: maks 1× per 6 jam untuk hemat CPU + battery.
     */
    private fun shouldLearn(): Boolean {
        val lastLearn = learningPrefs.getLong(KEY_LAST_LEARN_TIME, 0L)
        return System.currentTimeMillis() - lastLearn >= LEARNING_THROTTLE_MS
    }

    private fun markLearned() {
        learningPrefs.edit()
            .putLong(KEY_LAST_LEARN_TIME, System.currentTimeMillis())
            .apply()
    }
}
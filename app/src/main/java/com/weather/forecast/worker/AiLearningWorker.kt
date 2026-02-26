package com.weather.forecast.worker

import android.content.Context
import androidx.work.*
import com.weather.forecast.data.ai.DataRetentionManager
import com.weather.forecast.data.ai.DisasterNeuralNetwork
import com.weather.forecast.data.ai.IncrementalLearningEngine
import com.weather.forecast.data.ai.WeatherFeatureExtractor
import com.weather.forecast.data.repository.DisasterAnalysisEngine
import com.weather.forecast.data.repository.WaterQualityRepository
import com.weather.forecast.data.repository.WeatherRepository
import com.weather.forecast.location.LocationManager
import com.weather.forecast.data.preferences.PreferencesManager
import kotlinx.coroutines.flow.first
import java.util.concurrent.TimeUnit

/**
 * AI Learning Worker — Background Incremental Learning
 *
 * WorkManager worker yang menjalankan incremental learning secara berkala
 * di background, **tanpa sepengetahuan user**.
 *
 * ── Prinsip Desain ──
 * 1. **Invisible**: Tidak ada notifikasi, UI, atau indikasi apapun
 * 2. **Ringan**: ~15ms komputasi (102 parameter), ~15 KB I/O
 * 3. **Hemat battery**: Constraints ketat (network + not low battery)
 * 4. **Fail-safe**: Error tidak mempengaruhi app, retry otomatis
 *
 * ── Flow ──
 * 1. Ambil lokasi terakhir dari preferences (tanpa GPS access)
 * 2. Fetch cuaca terkini via API (ringan, 1 request)
 * 3. Jalankan rule-based analysis → target label
 * 4. Update NN weights (output layer only, ~0.1ms)
 * 5. Simpan weights ke SharedPreferences (~15 KB)
 *
 * ── Scheduling ──
 * - Interval: setiap 12 jam (2× per hari)
 * - Constraints: network connected + battery not low
 * - Policy: KEEP (tidak duplikasi)
 * - Backoff: exponential (min 10 menit)
 */
class AiLearningWorker(
    context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    private val weatherRepository = WeatherRepository()
    private val waterQualityRepository = WaterQualityRepository()
    private val learningEngine = IncrementalLearningEngine(context)
    private val retentionManager = DataRetentionManager(context)
    private val preferencesManager = PreferencesManager(context)

    override suspend fun doWork(): Result {
        return try {
            // 1. Ambil lokasi terakhir dari preferences (tanpa GPS)
            val preferences = preferencesManager.userPreferences.first()
            val latitude = preferences.lastLatitude ?: return Result.success()
            val longitude = preferences.lastLongitude ?: return Result.success()

            // 2. Fetch data cuaca (ringan, via existing API)
            val weatherResult = weatherRepository.getWeatherData(latitude, longitude)
            val weather = weatherResult.getOrNull() ?: return Result.retry()

            // Fetch water quality data (opsional, untuk fitur lengkap)
            val water = try {
                waterQualityRepository.getWaterQualityData(latitude, longitude).getOrNull()
            } catch (_: Exception) {
                null
            }

            // 3. Extract features + rule-based analysis
            val todayFeatures = WeatherFeatureExtractor.extractForToday(weather, water)
            val deltas = learningEngine.getWeightDeltas()

            val todayPredictions = DisasterAnalysisEngine.analyzeToday(
                weather = weather,
                marine = water,
                flood = water,
                weightDeltas = deltas
            )

            val ruleScores = todayPredictions.map { pred -> pred.riskScore.toFloat() }.toFloatArray()
            val nnScores = DisasterNeuralNetwork.predict(todayFeatures.features, deltas)

            // 4. Record prediction
            learningEngine.recordPrediction(
                features = todayFeatures.features,
                predictions = nnScores,
                ruleScores = ruleScores
            )

            // 5. Learn from outcome (weak supervision)
            val actualOutcome = learningEngine.computeActualOutcome(ruleScores)
            learningEngine.learnFromOutcome(actualOutcome)

            // 6. Auto-cleanup expired data
            retentionManager.performAutoCleanup()

            Result.success()
        } catch (_: Exception) {
            // Silent fail — retry nanti
            Result.retry()
        }
    }

    companion object {
        const val WORK_NAME = "ai_learning_worker"
    }
}

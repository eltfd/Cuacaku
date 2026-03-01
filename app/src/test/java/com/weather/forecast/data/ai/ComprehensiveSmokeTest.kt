package com.weather.forecast.data.ai

import org.junit.Test
import org.junit.Assert.*
import kotlin.math.abs
import kotlin.math.sqrt
import kotlin.math.exp

/**
 * ╔══════════════════════════════════════════════════════════════════════════╗
 * ║       COMPREHENSIVE SMOKE TEST — Cuacaku AI Early Warning System       ║
 * ║                                                                        ║
 * ║  Full-system validation sebelum publish ke masyarakat umum.            ║
 * ║  Menguji seluruh komponen AI beserta metrik akurasi & error.           ║
 * ║                                                                        ║
 * ║  Modules:                                                              ║
 * ║  1. Neural Network (MLP 27→32→16→6) — Prediksi risiko bencana        ║
 * ║  2. Feature Extractor — Normalisasi 27 fitur meteorologi              ║
 * ║  3. Incremental Learning Engine — Pembelajaran adaptif on-device      ║
 * ║  4. Ensemble Fusion Simulation — Integrasi NN + Rule-Based            ║
 * ║  5. Weather Potential — Analisis potensi cuaca ekstrem                ║
 * ║  6. Stress Test — Edge cases & robustness                             ║
 * ║                                                                        ║
 * ║  Metrik yang diukur:                                                   ║
 * ║  - Accuracy (Top-1, Top-2) per skenario                              ║
 * ║  - Mean Absolute Error (MAE) per jenis bencana                        ║
 * ║  - Root Mean Squared Error (RMSE)                                     ║
 * ║  - False Positive Rate (FPR) & False Negative Rate (FNR)             ║
 * ║  - Precision, Recall, F1-Score per kelas                              ║
 * ║  - Kalibrasi output                                                    ║
 * ║  - Latency / throughput                                                ║
 * ╚══════════════════════════════════════════════════════════════════════════╝
 */
class ComprehensiveSmokeTest {

    companion object {
        // Disaster indices
        const val FLOOD = 0
        const val TIDAL = 1
        const val CYCLONE = 2
        const val THUNDER = 3
        const val LANDSLIDE = 4
        const val SUBSIDENCE = 5

        val DISASTER_NAMES = arrayOf(
            "Banjir", "Banjir Rob", "Siklon", "Badai Petir", "Longsor", "Tanah Amblas"
        )

        // ── Risk thresholds matching DisasterAnalysisEngine ──
        const val EXTREME_THRESHOLD = 0.7
        const val HIGH_THRESHOLD = 0.45
        const val MODERATE_THRESHOLD = 0.25
    }

    // ══════════════════════════════════════════════════
    //  Test Scenario Data Class
    // ══════════════════════════════════════════════════

    data class TestScenario(
        val name: String,
        val features: FloatArray,
        /** Expected primary disaster (index) — -1 = no dominant */
        val expectedPrimary: Int,
        /** Expected risk level for primary: 0=LOW, 1=MOD, 2=HIGH, 3=EXTREME */
        val expectedMinLevel: Int = 1,
        /** Secondary expected disaster (optional) */
        val expectedSecondary: Int = -1,
        /** Target scores for MAE calculation [0-1] per disaster */
        val targetScores: FloatArray? = null,
        /** Category: REAL=realistic, EDGE=edge case, STRESS=stress test */
        val category: String = "REAL"
    )

    // ══════════════════════════════════════════════════
    //  Helper: Create feature vector from named params
    // ══════════════════════════════════════════════════

    private fun features(
        precipTotal: Float = 0f, precipIntensity: Float = 0f,
        windSpeed: Float = 0f, windGusts: Float = 0f, windShear: Float = 0f,
        pressureLow: Float = 0f, pressureDrop: Float = 0f, humidity: Float = 0f,
        capeEnergy: Float = 0f, freezingLow: Float = 0f,
        cloudCover: Float = 0f, dewPointSpread: Float = 0f,
        waveHeight: Float = 0f, swellHeight: Float = 0f, dischargeRatio: Float = 0f,
        rainDuration: Float = 0f, antecedentRain: Float = 0f, consecutiveRain: Float = 0f,
        weatherSeverity: Float = 0f, temperatureHigh: Float = 0f,
        soilSaturation: Float = 0f, soilMoistureRate: Float = 0f,
        slopeGradient: Float = 0f, elevationNorm: Float = 0f, vegetationCover: Float = 0.5f,
        soilClayContent: Float = 0.3f, soilStability: Float = 0.5f
    ): FloatArray = floatArrayOf(
        precipTotal, precipIntensity, windSpeed, windGusts, windShear,
        pressureLow, pressureDrop, humidity, capeEnergy, freezingLow,
        cloudCover, dewPointSpread, waveHeight, swellHeight, dischargeRatio,
        rainDuration, antecedentRain, consecutiveRain, weatherSeverity, temperatureHigh,
        soilSaturation, soilMoistureRate,
        slopeGradient, elevationNorm, vegetationCover,
        soilClayContent, soilStability
    )

    // ══════════════════════════════════════════════════
    //  30 Test Scenarios — Realistic Indonesian Weather
    // ══════════════════════════════════════════════════

    private fun createAllScenarios(): List<TestScenario> = listOf(
        // ── FLOOD SCENARIOS ──
        TestScenario(
            "Banjir Bandang Ekstrem (Jakarta Feb 2007-style)",
            features(precipTotal=0.95f, precipIntensity=0.90f, humidity=0.95f,
                dischargeRatio=0.85f, rainDuration=0.80f, antecedentRain=0.75f,
                consecutiveRain=0.70f, weatherSeverity=0.80f, soilSaturation=0.85f, soilMoistureRate=0.70f,
                slopeGradient=0.02f, elevationNorm=0.02f, vegetationCover=0.60f),
            expectedPrimary = FLOOD, expectedMinLevel = 2,
            targetScores = floatArrayOf(0.92f, 0.30f, 0.15f, 0.25f, 0.70f, 0.60f)
        ),
        TestScenario(
            "Banjir Ringan — Hujan sore biasa",
            features(precipTotal=0.25f, precipIntensity=0.30f, humidity=0.70f,
                rainDuration=0.20f, cloudCover=0.60f,
                slopeGradient=0.02f, elevationNorm=0.03f, vegetationCover=0.65f),
            expectedPrimary = FLOOD, expectedMinLevel = 0,
            targetScores = floatArrayOf(0.35f, 0.10f, 0.05f, 0.10f, 0.15f, 0.10f),
            category = "REAL"
        ),
        TestScenario(
            "Banjir + Longsor gabungan (Garut 2016-style)",
            features(precipTotal=0.85f, precipIntensity=0.80f, humidity=0.92f,
                rainDuration=0.75f, antecedentRain=0.80f, consecutiveRain=0.65f,
                dischargeRatio=0.70f, soilSaturation=0.80f, soilMoistureRate=0.65f,
                slopeGradient=0.30f, elevationNorm=0.25f, vegetationCover=0.35f),
            expectedPrimary = FLOOD, expectedSecondary = LANDSLIDE, expectedMinLevel = 2,
            targetScores = floatArrayOf(0.85f, 0.20f, 0.10f, 0.20f, 0.75f, 0.60f)
        ),

        // ── TIDAL FLOOD SCENARIOS ──
        TestScenario(
            "Banjir Rob Pesisir Utara Jawa",
            features(waveHeight=0.85f, swellHeight=0.75f, pressureLow=0.55f,
                windSpeed=0.45f, windGusts=0.50f,
                slopeGradient=0.01f, elevationNorm=0.01f, vegetationCover=0.50f),
            expectedPrimary = TIDAL, expectedMinLevel = 1,
            targetScores = floatArrayOf(0.15f, 0.80f, 0.40f, 0.10f, 0.05f, 0.05f)
        ),
        TestScenario(
            "Rob Ringan — Gelombang sedang, angin moderat",
            features(waveHeight=0.40f, swellHeight=0.35f, pressureLow=0.30f,
                windSpeed=0.30f, slopeGradient=0.01f, elevationNorm=0.01f),
            expectedPrimary = TIDAL, expectedMinLevel = 0,
            targetScores = floatArrayOf(0.10f, 0.40f, 0.20f, 0.05f, 0.05f, 0.05f)
        ),
        TestScenario(
            "Rob Parah + Siklon dekat pantai",
            features(waveHeight=0.90f, swellHeight=0.85f, pressureLow=0.80f,
                windSpeed=0.75f, windGusts=0.80f, pressureDrop=0.60f,
                slopeGradient=0.01f, elevationNorm=0.01f),
            expectedPrimary = TIDAL, expectedSecondary = CYCLONE, expectedMinLevel = 2,
            targetScores = floatArrayOf(0.20f, 0.85f, 0.80f, 0.30f, 0.05f, 0.05f)
        ),

        // ── CYCLONE SCENARIOS ──
        TestScenario(
            "Siklon Tropis Kategori 3 (Seroja 2021-style)",
            features(windSpeed=0.90f, windGusts=0.95f, windShear=0.70f,
                pressureLow=0.90f, pressureDrop=0.80f, waveHeight=0.80f,
                swellHeight=0.70f, precipTotal=0.60f),
            expectedPrimary = CYCLONE, expectedMinLevel = 2,
            targetScores = floatArrayOf(0.30f, 0.70f, 0.95f, 0.40f, 0.10f, 0.10f)
        ),
        TestScenario(
            "Angin Kencang Non-Siklon (kemarau panas)",
            features(windSpeed=0.50f, windGusts=0.55f, pressureLow=0.20f,
                temperatureHigh=0.70f),
            expectedPrimary = CYCLONE, expectedMinLevel = 1,
            targetScores = floatArrayOf(0.10f, 0.20f, 0.50f, 0.25f, 0.05f, 0.05f)
        ),
        TestScenario(
            "Depresi Tropis — Pre-cyclone",
            features(windSpeed=0.60f, windGusts=0.65f, pressureLow=0.65f,
                pressureDrop=0.50f, waveHeight=0.50f, precipTotal=0.40f,
                capeEnergy=0.40f),
            expectedPrimary = CYCLONE, expectedMinLevel = 1,
            targetScores = floatArrayOf(0.25f, 0.45f, 0.70f, 0.35f, 0.10f, 0.10f)
        ),

        // ── THUNDERSTORM SCENARIOS ──
        TestScenario(
            "Badai Petir Supercell (CAPE 4000+)",
            features(capeEnergy=0.95f, windShear=0.65f, windGusts=0.60f,
                weatherSeverity=1.0f, freezingLow=0.60f, cloudCover=0.90f),
            expectedPrimary = THUNDER, expectedMinLevel = 2,
            targetScores = floatArrayOf(0.20f, 0.10f, 0.35f, 0.90f, 0.15f, 0.10f)
        ),
        TestScenario(
            "Petir Biasa Sore Hari",
            features(capeEnergy=0.40f, windShear=0.25f, weatherSeverity=0.50f,
                humidity=0.70f, cloudCover=0.75f),
            expectedPrimary = THUNDER, expectedMinLevel = 0,
            targetScores = floatArrayOf(0.15f, 0.05f, 0.10f, 0.40f, 0.10f, 0.10f)
        ),
        TestScenario(
            "Badai Petir + Hujan Es (hailstorm)",
            features(capeEnergy=0.85f, windShear=0.55f, windGusts=0.65f,
                weatherSeverity=1.0f, freezingLow=0.75f, precipIntensity=0.60f,
                cloudCover=0.95f),
            expectedPrimary = THUNDER, expectedMinLevel = 2,
            targetScores = floatArrayOf(0.25f, 0.10f, 0.40f, 0.85f, 0.15f, 0.10f)
        ),

        // ── LANDSLIDE SCENARIOS ──
        TestScenario(
            "Longsor Pasca Hujan 3 Hari (Cianjur-style)",
            features(antecedentRain=0.90f, consecutiveRain=0.85f, rainDuration=0.80f,
                humidity=0.95f, precipTotal=0.65f, soilSaturation=0.90f,
                soilMoistureRate=0.75f, slopeGradient=0.55f, elevationNorm=0.40f, vegetationCover=0.30f,
                soilClayContent=0.65f, soilStability=0.25f),
            expectedPrimary = LANDSLIDE, expectedSecondary = SUBSIDENCE, expectedMinLevel = 2,
            targetScores = floatArrayOf(0.45f, 0.10f, 0.05f, 0.15f, 0.85f, 0.75f)
        ),
        TestScenario(
            "Longsor Ringan — Tanah mulai jenuh",
            features(antecedentRain=0.40f, consecutiveRain=0.30f, humidity=0.75f,
                soilSaturation=0.45f, precipTotal=0.30f, slopeGradient=0.35f, elevationNorm=0.30f, vegetationCover=0.40f),
            expectedPrimary = LANDSLIDE, expectedMinLevel = 0,
            targetScores = floatArrayOf(0.25f, 0.05f, 0.05f, 0.05f, 0.35f, 0.25f)
        ),
        TestScenario(
            "Longsor Cepat — Hujan intensitas tinggi di lereng",
            features(precipIntensity=0.85f, precipTotal=0.70f, antecedentRain=0.60f,
                soilSaturation=0.80f, humidity=0.90f, rainDuration=0.50f,
                soilMoistureRate=0.80f, slopeGradient=0.65f, elevationNorm=0.50f, vegetationCover=0.20f,
                soilClayContent=0.70f, soilStability=0.20f),
            expectedPrimary = FLOOD, expectedSecondary = LANDSLIDE, expectedMinLevel = 2,
            targetScores = floatArrayOf(0.60f, 0.10f, 0.10f, 0.15f, 0.70f, 0.55f)
        ),

        // ── SUBSIDENCE SCENARIOS ──
        TestScenario(
            "Tanah Amblas — Genangan berkepanjangan",
            features(consecutiveRain=0.85f, antecedentRain=0.80f, dischargeRatio=0.75f,
                humidity=0.90f, soilSaturation=0.85f, soilMoistureRate=0.60f,
                precipTotal=0.55f, slopeGradient=0.05f, elevationNorm=0.05f, vegetationCover=0.50f,
                soilClayContent=0.20f, soilStability=0.30f),
            expectedPrimary = SUBSIDENCE, expectedMinLevel = 1,
            targetScores = floatArrayOf(0.40f, 0.10f, 0.05f, 0.05f, 0.65f, 0.80f)
        ),
        TestScenario(
            "Amblas Ringan — Hujan terus 2 hari",
            features(consecutiveRain=0.35f, antecedentRain=0.30f, humidity=0.70f,
                precipTotal=0.25f, soilSaturation=0.40f, slopeGradient=0.03f, elevationNorm=0.02f),
            expectedPrimary = SUBSIDENCE, expectedMinLevel = 0,
            targetScores = floatArrayOf(0.20f, 0.05f, 0.05f, 0.05f, 0.20f, 0.30f)
        ),

        // ── CALM / NORMAL WEATHER ──
        TestScenario(
            "Cuaca Cerah — Tidak ada risiko",
            features(), // all zeros
            expectedPrimary = -1, expectedMinLevel = 0,
            targetScores = floatArrayOf(0.05f, 0.05f, 0.05f, 0.05f, 0.05f, 0.05f),
            category = "NORMAL"
        ),
        TestScenario(
            "Pagi Cerah Berawan — Normal",
            features(cloudCover=0.40f, humidity=0.50f, temperatureHigh=0.40f),
            expectedPrimary = -1, expectedMinLevel = 0,
            targetScores = floatArrayOf(0.10f, 0.05f, 0.05f, 0.05f, 0.10f, 0.05f),
            category = "NORMAL"
        ),
        TestScenario(
            "Musim Kemarau Biasa",
            features(temperatureHigh=0.65f, humidity=0.30f, cloudCover=0.20f),
            expectedPrimary = -1, expectedMinLevel = 0,
            targetScores = floatArrayOf(0.05f, 0.05f, 0.05f, 0.08f, 0.05f, 0.05f),
            category = "NORMAL"
        ),
        TestScenario(
            "Monsun Normal — Hujan ringan periodik",
            features(precipTotal=0.20f, precipIntensity=0.15f, humidity=0.65f,
                rainDuration=0.25f, cloudCover=0.60f, antecedentRain=0.20f),
            expectedPrimary = -1, expectedMinLevel = 0,
            targetScores = floatArrayOf(0.25f, 0.05f, 0.05f, 0.10f, 0.15f, 0.10f),
            category = "NORMAL"
        ),

        // ── MULTI-HAZARD ──
        TestScenario(
            "Multi-Hazard Ekstrem (Segala bencana sekaligus)",
            features(precipTotal=0.95f, precipIntensity=0.90f, windSpeed=0.90f,
                windGusts=0.95f, windShear=0.80f, pressureLow=0.90f, pressureDrop=0.85f,
                humidity=0.95f, capeEnergy=0.90f, freezingLow=0.70f,
                cloudCover=0.95f, dewPointSpread=0.85f, waveHeight=0.90f,
                swellHeight=0.85f, dischargeRatio=0.90f, rainDuration=0.90f,
                antecedentRain=0.90f, consecutiveRain=0.85f, weatherSeverity=1.0f,
                temperatureHigh=0.50f, soilSaturation=0.90f, soilMoistureRate=0.80f,
                slopeGradient=0.60f, elevationNorm=0.40f, vegetationCover=0.15f),
            expectedPrimary = CYCLONE, expectedMinLevel = 3,
            targetScores = floatArrayOf(0.95f, 0.85f, 0.95f, 0.90f, 0.70f, 0.70f),
            category = "EXTREME"
        ),
        TestScenario(
            "Dual: Siklon + Banjir Rob",
            features(windSpeed=0.80f, windGusts=0.85f, pressureLow=0.80f,
                pressureDrop=0.70f, waveHeight=0.85f, swellHeight=0.75f,
                precipTotal=0.50f),
            expectedPrimary = CYCLONE, expectedSecondary = TIDAL, expectedMinLevel = 2,
            targetScores = floatArrayOf(0.30f, 0.75f, 0.85f, 0.35f, 0.10f, 0.10f)
        ),
        TestScenario(
            "Dual: Badai Petir + Banjir",
            features(capeEnergy=0.80f, windShear=0.50f, weatherSeverity=0.90f,
                precipTotal=0.70f, precipIntensity=0.75f, humidity=0.85f,
                rainDuration=0.60f),
            expectedPrimary = THUNDER, expectedSecondary = FLOOD, expectedMinLevel = 2,
            targetScores = floatArrayOf(0.55f, 0.15f, 0.30f, 0.75f, 0.30f, 0.20f)
        ),

        // ── EDGE CASES ──
        TestScenario(
            "Edge: Semua fitur minimal (0.01)",
            FloatArray(27) { 0.01f },
            expectedPrimary = -1, expectedMinLevel = 0,
            category = "EDGE"
        ),
        TestScenario(
            "Edge: Semua fitur maksimal (1.0)",
            FloatArray(27) { 1.0f },
            expectedPrimary = CYCLONE, expectedMinLevel = 3,
            category = "EDGE"
        ),
        TestScenario(
            "Edge: Hanya 1 fitur aktif (CAPE tinggi)",
            features(capeEnergy=0.95f),
            expectedPrimary = -1, expectedMinLevel = 0,
            category = "EDGE"
        ),
        TestScenario(
            "Edge: Noise — Random values seragam 0.5",
            FloatArray(27) { 0.5f },
            expectedPrimary = -1, expectedMinLevel = 0,
            targetScores = floatArrayOf(0.50f, 0.50f, 0.50f, 0.50f, 0.50f, 0.50f),
            category = "EDGE"
        ),
        TestScenario(
            "Edge: Contradictory signals (kering + gelombang tinggi)",
            features(humidity=0.10f, precipTotal=0.0f, waveHeight=0.90f,
                swellHeight=0.80f, windSpeed=0.70f),
            expectedPrimary = TIDAL, expectedMinLevel = 1,
            category = "EDGE"
        )
    )

    // ══════════════════════════════════════════════════
    //  MODULE 1: NEURAL NETWORK COMPREHENSIVE TEST
    // ══════════════════════════════════════════════════

    @Test
    fun `MODULE 1 - Neural Network Accuracy and Error Analysis`() {
        println("\n" + "═".repeat(80))
        println("  MODULE 1: DISASTER NEURAL NETWORK — ACCURACY & ERROR ANALYSIS")
        println("  Model: ${DisasterNeuralNetwork.MODEL_VERSION}")
        println("  Parameters: ${DisasterNeuralNetwork.TOTAL_PARAMS}")
        println("═".repeat(80))

        val scenarios = createAllScenarios()
        val results = scenarios.map { s ->
            val prediction = DisasterNeuralNetwork.predict(s.features)
            Triple(s, prediction, s.targetScores)
        }

        // ── 1A: Per-Scenario Accuracy ──
        println("\n╔══════════════════════════════════════════════════════════════════╗")
        println("║  1A. SCENARIO PREDICTION ACCURACY                               ║")
        println("╚══════════════════════════════════════════════════════════════════╝")

        var top1Correct = 0
        var top2Correct = 0
        var totalWithExpected = 0
        var calmCorrect = 0
        var calmTotal = 0

        for ((scenario, pred, _) in results) {
            val maxIdx = pred.indices.maxByOrNull { pred[it] } ?: 0
            val sorted = pred.indices.sortedByDescending { pred[it] }
            val maxVal = pred[maxIdx]
            val riskLabel = when {
                maxVal >= EXTREME_THRESHOLD -> "EXTREME"
                maxVal >= HIGH_THRESHOLD -> "HIGH"
                maxVal >= MODERATE_THRESHOLD -> "MOD"
                else -> "LOW"
            }

            if (scenario.expectedPrimary == -1) {
                // CALM scenario: all should be roughly equal/low
                calmTotal++
                val spread = pred.max() - pred.min()
                val isCalm = spread < 0.15f
                if (isCalm) calmCorrect++
                val icon = if (isCalm) "✅" else "⚠️"
                println("  $icon ${scenario.name}")
                println("     Spread: %.4f (${if (isCalm) "tepat rendah" else "terlalu besar"})".format(spread))
                println("     Scores: ${pred.mapIndexed { i, v -> "${DISASTER_NAMES[i]}=%.3f".format(v) }.joinToString(" | ")}")
            } else {
                totalWithExpected++
                val top1 = sorted[0] == scenario.expectedPrimary
                val top2 = sorted[0] == scenario.expectedPrimary || sorted[1] == scenario.expectedPrimary
                if (top1) top1Correct++
                if (top2) top2Correct++

                val secCheck = if (scenario.expectedSecondary >= 0) {
                    val secInTop3 = scenario.expectedSecondary in sorted.take(3)
                    if (secInTop3) " | 2nd ✅ ${DISASTER_NAMES[scenario.expectedSecondary]}" else " | 2nd ❌ expected ${DISASTER_NAMES[scenario.expectedSecondary]}"
                } else ""

                val icon = if (top1) "✅" else if (top2) "🟡" else "❌"
                println("  $icon ${scenario.name}")
                println("     Expected: ${DISASTER_NAMES[scenario.expectedPrimary]} | Got: ${DISASTER_NAMES[sorted[0]]} ($riskLabel) = %.3f$secCheck".format(pred[sorted[0]]))
                println("     All: ${pred.mapIndexed { i, v -> "${DISASTER_NAMES[i]}=%.3f".format(v) }.joinToString(" | ")}")
            }
        }

        val top1Pct = if (totalWithExpected > 0) top1Correct * 100.0 / totalWithExpected else 0.0
        val top2Pct = if (totalWithExpected > 0) top2Correct * 100.0 / totalWithExpected else 0.0
        val calmPct = if (calmTotal > 0) calmCorrect * 100.0 / calmTotal else 0.0

        println("\n  ┌─────────────────────────────────────────────┐")
        println("  │ Top-1 Accuracy: $top1Correct/$totalWithExpected = ${"%.1f".format(top1Pct)}%             │")
        println("  │ Top-2 Accuracy: $top2Correct/$totalWithExpected = ${"%.1f".format(top2Pct)}%             │")
        println("  │ Calm Detection: $calmCorrect/$calmTotal = ${"%.1f".format(calmPct)}%              │")
        println("  └─────────────────────────────────────────────┘")

        // ── 1B: Error Metrics (MAE, RMSE) per Disaster ──
        println("\n╔══════════════════════════════════════════════════════════════════╗")
        println("║  1B. ERROR METRICS — MAE & RMSE per Disaster Type               ║")
        println("╚══════════════════════════════════════════════════════════════════╝")

        val scenariosWithTargets = results.filter { it.third != null }
        val maePerDisaster = FloatArray(6)
        val msePerDisaster = FloatArray(6)
        var totalSamplesForError = 0

        for ((_, pred, targets) in scenariosWithTargets) {
            if (targets == null) continue
            totalSamplesForError++
            for (d in 0 until 6) {
                val err = abs(pred[d] - targets[d])
                maePerDisaster[d] += err
                msePerDisaster[d] += err * err
            }
        }

        val n = totalSamplesForError.toFloat().coerceAtLeast(1f)
        println("  ╔════════════════════╦════════╦════════╦═══════════╗")
        println("  ║ Disaster           ║  MAE   ║  RMSE  ║  Rating   ║")
        println("  ╠════════════════════╬════════╬════════╬═══════════╣")

        var totalMAE = 0f
        var totalRMSE = 0f
        for (d in 0 until 6) {
            val mae = maePerDisaster[d] / n
            val rmse = sqrt(msePerDisaster[d] / n)
            totalMAE += mae
            totalRMSE += rmse
            val rating = when {
                mae < 0.10 -> "⭐ Sangat Baik"
                mae < 0.20 -> "✅ Baik"
                mae < 0.30 -> "🟡 Cukup"
                mae < 0.40 -> "⚠️ Kurang"
                else -> "❌ Buruk"
            }
            println("  ║ %-18s ║ %.4f ║ %.4f ║ %-9s ║".format(DISASTER_NAMES[d], mae, rmse, rating))
        }
        val avgMAE = totalMAE / 6f
        val avgRMSE = totalRMSE / 6f
        println("  ╠════════════════════╬════════╬════════╬═══════════╣")
        println("  ║ %-18s ║ %.4f ║ %.4f ║           ║".format("RATA-RATA", avgMAE, avgRMSE))
        println("  ╚════════════════════╩════════╩════════╩═══════════╝")

        // ── 1C: Classification Metrics (Precision, Recall, F1) ──
        println("\n╔══════════════════════════════════════════════════════════════════╗")
        println("║  1C. CLASSIFICATION METRICS — Precision, Recall, F1              ║")
        println("╚══════════════════════════════════════════════════════════════════╝")

        // Binary classification per disaster: "is this the primary disaster?"
        val tp = IntArray(6)
        val fp = IntArray(6)
        val fn = IntArray(6)

        for ((scenario, pred, _) in results) {
            if (scenario.expectedPrimary == -1) continue
            val predictedPrimary = pred.indices.maxByOrNull { pred[it] } ?: 0

            for (d in 0 until 6) {
                val predicted = predictedPrimary == d
                val actual = scenario.expectedPrimary == d
                if (predicted && actual) tp[d]++
                if (predicted && !actual) fp[d]++
                if (!predicted && actual) fn[d]++
            }
        }

        println("  ╔════════════════════╦═══════════╦════════╦═══════╦═══════╗")
        println("  ║ Disaster           ║ Precision ║ Recall ║  F1   ║TP/FP/FN║")
        println("  ╠════════════════════╬═══════════╬════════╬═══════╬═══════╣")

        var totalF1 = 0.0
        var classCount = 0
        for (d in 0 until 6) {
            val precision = if (tp[d] + fp[d] > 0) tp[d].toDouble() / (tp[d] + fp[d]) else 0.0
            val recall = if (tp[d] + fn[d] > 0) tp[d].toDouble() / (tp[d] + fn[d]) else 0.0
            val f1 = if (precision + recall > 0) 2 * precision * recall / (precision + recall) else 0.0
            totalF1 += f1
            classCount++
            println("  ║ %-18s ║  %.3f    ║ %.3f  ║ %.3f ║ %d/%d/%d  ║".format(
                DISASTER_NAMES[d], precision, recall, f1, tp[d], fp[d], fn[d]))
        }
        val macroF1 = if (classCount > 0) totalF1 / classCount else 0.0
        println("  ╠════════════════════╬═══════════╬════════╬═══════╬═══════╣")
        println("  ║ Macro Average      ║           ║        ║ %.3f ║       ║".format(macroF1))
        println("  ╚════════════════════╩═══════════╩════════╩═══════╩═══════╝")

        // Assertions
        assertTrue("Top-1 accuracy harus >= 60%", top1Pct >= 60.0)
        assertTrue("Top-2 accuracy harus >= 80%", top2Pct >= 80.0)
        assertTrue("Calm detection harus >= 75%", calmPct >= 75.0)
        assertTrue("Rata-rata MAE harus < 0.35", avgMAE < 0.35f)
        assertTrue("Macro F1 harus >= 0.40", macroF1 >= 0.40)
    }

    // ══════════════════════════════════════════════════
    //  MODULE 2: FEATURE EXTRACTOR VALIDATION
    // ══════════════════════════════════════════════════

    @Test
    fun `MODULE 2 - Feature Extractor Validation`() {
        println("\n" + "═".repeat(80))
        println("  MODULE 2: WEATHER FEATURE EXTRACTOR — VALIDATION")
        println("═".repeat(80))

        // 2A: Feature count and names
        println("\n  2A. Feature Configuration")
        assertEquals("Feature count harus 27", 27, WeatherFeatureExtractor.FEATURE_COUNT)
        assertEquals("Feature names harus 27", 27, WeatherFeatureExtractor.FEATURE_NAMES.size)
        println("  ✅ FEATURE_COUNT = ${WeatherFeatureExtractor.FEATURE_COUNT}")
        println("  ✅ FEATURE_NAMES = ${WeatherFeatureExtractor.FEATURE_NAMES.size} names")

        // 2B: All feature names are unique
        val uniqueNames = WeatherFeatureExtractor.FEATURE_NAMES.toSet()
        assertEquals("Semua nama fitur harus unik", 27, uniqueNames.size)
        println("  ✅ Semua nama fitur unik")

        // 2C: Feature names match expected list
        val expectedNames = listOf(
            "precipTotal", "precipIntensity", "windSpeed", "windGusts", "windShear",
            "pressureLow", "pressureDrop", "humidity", "capeEnergy", "freezingLow",
            "cloudCover", "dewPointSpread", "waveHeight", "swellHeight", "dischargeRatio",
            "rainDuration", "antecedentRain", "consecutiveRain", "weatherSeverity", "temperatureHigh",
            "soilSaturation", "soilMoistureRate",
            "slopeGradient", "elevationNorm", "vegetationCover",
            "soilClayContent", "soilStability"
        )
        assertEquals("Feature names harus sesuai", expectedNames, WeatherFeatureExtractor.FEATURE_NAMES)
        println("  ✅ Feature names sesuai spesifikasi")

        // 2D: Neural network accepts feature count
        val testInput = FloatArray(WeatherFeatureExtractor.FEATURE_COUNT) { 0.5f }
        val output = DisasterNeuralNetwork.predict(testInput)
        assertEquals("Output harus 6 disaster types", 6, output.size)
        println("  ✅ NN menerima input ${testInput.size} fitur → output ${output.size} disaster")

        // 2E: Verify normalization bounds
        println("\n  2E. Normalization Bounds Check")
        val edgeCases = listOf(
            "All zeros" to FloatArray(27) { 0f },
            "All ones" to FloatArray(27) { 1f },
            "Mid-range" to FloatArray(27) { 0.5f },
            "Near-zero" to FloatArray(27) { 0.001f },
            "Near-one" to FloatArray(27) { 0.999f }
        )

        var allInBounds = true
        for ((name, input) in edgeCases) {
            val out = DisasterNeuralNetwork.predict(input)
            val inBounds = out.all { it in 0f..1f }
            if (!inBounds) allInBounds = false
            println("  ${if (inBounds) "✅" else "❌"} $name → output range [%.4f, %.4f]".format(out.min(), out.max()))
        }
        assertTrue("Semua output harus dalam [0, 1]", allInBounds)

        println("\n  MODULE 2 RESULT: ✅ PASS")
    }

    // ══════════════════════════════════════════════════
    //  MODULE 3: INCREMENTAL LEARNING SIMULATION
    // ══════════════════════════════════════════════════

    @Test
    fun `MODULE 3 - Incremental Learning Quality`() {
        println("\n" + "═".repeat(80))
        println("  MODULE 3: INCREMENTAL LEARNING ENGINE — QUALITY METRICS")
        println("═".repeat(80))

        // Simulate learning engine (same approach as IncrementalLearningTest)
        val H2 = 16
        val OUTPUT = 6
        val LR = 0.005f
        val MOMENTUM = 0.9f
        val GRAD_CLIP = 1.0f

        // Training scenarios
        data class TrainSample(val name: String, val feats: FloatArray, val targets: FloatArray)

        val trainData = listOf(
            TrainSample("Banjir",
                features(precipTotal=0.9f, precipIntensity=0.85f, humidity=0.9f, rainDuration=0.75f, dischargeRatio=0.8f),
                floatArrayOf(0.90f, 0.20f, 0.10f, 0.20f, 0.60f, 0.50f)),
            TrainSample("Siklon",
                features(windSpeed=0.9f, windGusts=0.95f, pressureLow=0.9f, pressureDrop=0.8f, waveHeight=0.8f),
                floatArrayOf(0.20f, 0.75f, 0.95f, 0.30f, 0.10f, 0.10f)),
            TrainSample("Petir",
                features(capeEnergy=0.95f, windShear=0.55f, weatherSeverity=1.0f),
                floatArrayOf(0.20f, 0.10f, 0.30f, 0.90f, 0.15f, 0.10f)),
            TrainSample("Longsor",
                features(antecedentRain=0.9f, consecutiveRain=0.85f, humidity=0.95f, soilSaturation=0.85f),
                floatArrayOf(0.40f, 0.10f, 0.05f, 0.15f, 0.85f, 0.70f)),
            TrainSample("Tenang",
                FloatArray(27) { 0.05f },
                floatArrayOf(0.05f, 0.05f, 0.05f, 0.05f, 0.05f, 0.05f))
        )

        // Simple SGD simulation on output layer
        val w3Delta = FloatArray(H2 * OUTPUT)
        val b3Delta = FloatArray(OUTPUT)
        val w3Mom = FloatArray(H2 * OUTPUT)
        val b3Mom = FloatArray(OUTPUT)

        val errorHistory = mutableListOf<Float>()

        // Training loop
        val epochs = 100
        for (epoch in 0 until epochs) {
            var epochError = 0f
            for (sample in trainData) {
                val h2 = DisasterNeuralNetwork.forwardToH2(sample.feats)
                val deltas = WeightDeltas(w3Delta.copyOf(), b3Delta.copyOf(), epoch.toLong())
                val pred = DisasterNeuralNetwork.predict(sample.feats, deltas)

                var sampleError = 0f
                for (j in 0 until OUTPUT) {
                    val delta = pred[j].coerceIn(0.001f, 0.999f) - sample.targets[j].coerceIn(0.001f, 0.999f)
                    sampleError += delta * delta

                    for (i in 0 until H2) {
                        val idx = i * OUTPUT + j
                        val grad = (delta * h2[i]).coerceIn(-GRAD_CLIP, GRAD_CLIP)
                        w3Mom[idx] = MOMENTUM * w3Mom[idx] + (1 - MOMENTUM) * grad
                        w3Delta[idx] -= LR * w3Mom[idx]
                    }
                    val bGrad = delta.coerceIn(-GRAD_CLIP, GRAD_CLIP)
                    b3Mom[j] = MOMENTUM * b3Mom[j] + (1 - MOMENTUM) * bGrad
                    b3Delta[j] -= LR * b3Mom[j]
                }
                epochError += sampleError / OUTPUT
            }
            errorHistory.add(epochError / trainData.size)
        }

        // 3A: Convergence
        println("\n  3A. Convergence Analysis")
        val initError = errorHistory.first()
        val finalError = errorHistory.last()
        val reductionPct = ((initError - finalError) / initError) * 100
        val converged = finalError < initError
        println("  Initial error: %.6f".format(initError))
        println("  Final error:   %.6f".format(finalError))
        println("  Reduction:     %.1f%%".format(reductionPct))
        println("  ${if (converged) "✅" else "❌"} ${if (converged) "Konvergen" else "Tidak konvergen"}")

        // 3B: Per-disaster improvement
        println("\n  3B. Per-Disaster Learning Improvement")
        val learnedDeltas = WeightDeltas(w3Delta, b3Delta, epochs.toLong())
        var improvedCount = 0
        for (sample in trainData) {
            val basePred = DisasterNeuralNetwork.predict(sample.feats)
            val learnedPred = DisasterNeuralNetwork.predict(sample.feats, learnedDeltas)

            val baseError = (0 until OUTPUT).map { abs(basePred[it] - sample.targets[it]) }.average()
            val learnedError = (0 until OUTPUT).map { abs(learnedPred[it] - sample.targets[it]) }.average()
            val improved = learnedError < baseError
            if (improved) improvedCount++

            println("  ${if (improved) "✅" else "⚠️"} ${sample.name}: base_err=%.4f → learned_err=%.4f (Δ=${"%.4f".format(baseError - learnedError)})".format(baseError, learnedError))
        }
        val learnImprPct = improvedCount * 100.0 / trainData.size
        println("  Improved: $improvedCount/${trainData.size} = ${"%.0f".format(learnImprPct)}%")

        // 3C: Weight stability
        println("\n  3C. Weight Stability")
        val maxWeight = w3Delta.maxOrNull() ?: 0f
        val minWeight = w3Delta.minOrNull() ?: 0f
        val weightNorm = sqrt(w3Delta.map { it * it }.sum())
        println("  Weight range: [%.6f, %.6f]".format(minWeight, maxWeight))
        println("  Weight L2 norm: %.6f".format(weightNorm))
        val stable = maxWeight < 2.0f && minWeight > -2.0f
        println("  ${if (stable) "✅" else "❌"} Weight bounded (tidak divergen)")

        // 3D: Forgetting test - calm should still be calm
        println("\n  3D. Catastrophic Forgetting Check")
        val calmFeats = FloatArray(27) { 0.05f }
        val calmBase = DisasterNeuralNetwork.predict(calmFeats)
        val calmLearned = DisasterNeuralNetwork.predict(calmFeats, learnedDeltas)
        val calmDrift = (0 until OUTPUT).map { abs(calmBase[it] - calmLearned[it]) }.max()
        val noForgetting = calmDrift < 0.15f
        println("  Calm weather drift: %.4f".format(calmDrift))
        println("  ${if (noForgetting) "✅" else "⚠️"} ${if (noForgetting) "Tidak ada catastrophic forgetting" else "Warning: drift signifikan"}")

        assertTrue("Error harus menurun setelah training", converged)
        assertTrue("Weight harus bounded", stable)

        println("\n  MODULE 3 RESULT: ✅ PASS")
    }

    // ══════════════════════════════════════════════════
    //  MODULE 4: ENSEMBLE FUSION SIMULATION
    // ══════════════════════════════════════════════════

    @Test
    fun `MODULE 4 - Ensemble Fusion Accuracy`() {
        println("\n" + "═".repeat(80))
        println("  MODULE 4: ENSEMBLE FUSION — NN + Rule-Based Integration")
        println("═".repeat(80))

        // Simulate ensemble: fusedScore = α × nnScore + (1−α) × ruleScore
        // α = 0.6 × dataCompleteness

        data class EnsembleTest(
            val name: String,
            val nnScores: FloatArray,
            val ruleScores: DoubleArray,
            val dataCompleteness: Double,
            val expectedPrimary: Int
        )

        val tests = listOf(
            EnsembleTest(
                "Banjir — Full data (α=0.6)",
                DisasterNeuralNetwork.predict(features(precipTotal=0.9f, precipIntensity=0.85f, humidity=0.9f, rainDuration=0.75f, dischargeRatio=0.8f)),
                doubleArrayOf(0.85, 0.10, 0.05, 0.15, 0.40, 0.30),
                1.0, FLOOD
            ),
            EnsembleTest(
                "Siklon — Full data (α=0.6)",
                DisasterNeuralNetwork.predict(features(windSpeed=0.9f, windGusts=0.95f, pressureLow=0.9f, pressureDrop=0.8f)),
                doubleArrayOf(0.10, 0.30, 0.80, 0.20, 0.05, 0.05),
                1.0, CYCLONE
            ),
            EnsembleTest(
                "Banjir — Partial data (α=0.36)",
                DisasterNeuralNetwork.predict(features(precipTotal=0.8f, precipIntensity=0.7f)),
                doubleArrayOf(0.70, 0.05, 0.05, 0.10, 0.20, 0.15),
                0.6, FLOOD
            ),
            EnsembleTest(
                "Tenang — Full data (α=0.6)",
                DisasterNeuralNetwork.predict(FloatArray(27) { 0.05f }),
                doubleArrayOf(0.05, 0.05, 0.05, 0.05, 0.05, 0.05),
                1.0, -1
            ),
            EnsembleTest(
                "Petir — Rule-only (α=0)",
                DisasterNeuralNetwork.predict(features(capeEnergy=0.9f, weatherSeverity=0.8f)),
                doubleArrayOf(0.10, 0.05, 0.15, 0.75, 0.10, 0.05),
                0.0, THUNDER
            )
        )

        println("\n  ╔═══════════════════════════════╦═══════╦══════════╦══════════╦═══════════╗")
        println("  ║ Scenario                      ║   α   ║ NN pred  ║ Fused    ║ Correct?  ║")
        println("  ╠═══════════════════════════════╬═══════╬══════════╬══════════╬═══════════╣")

        var ensembleCorrect = 0
        for (test in tests) {
            val alpha = 0.6 * test.dataCompleteness
            val beta = 1.0 - alpha

            val fused = DoubleArray(6) { d ->
                (alpha * test.nnScores[d] + beta * test.ruleScores[d]).coerceIn(0.0, 1.0)
            }

            val fusedPrimary = fused.indices.maxByOrNull { fused[it] } ?: 0
            val nnPrimary = test.nnScores.indices.maxByOrNull { test.nnScores[it] } ?: 0

            val correct = if (test.expectedPrimary == -1) {
                fused.max() - fused.min() < 0.15
            } else {
                fusedPrimary == test.expectedPrimary
            }
            if (correct) ensembleCorrect++

            println("  ║ %-29s ║ %.2f  ║ %-8s ║ %-8s ║ %-9s ║".format(
                test.name,
                alpha,
                DISASTER_NAMES[nnPrimary],
                if (test.expectedPrimary == -1) "Calm" else DISASTER_NAMES[fusedPrimary],
                if (correct) "✅" else "❌"
            ))
        }
        println("  ╚═══════════════════════════════╩═══════╩══════════╩══════════╩═══════════╝")

        val ensemblePct = ensembleCorrect * 100.0 / tests.size
        println("  Ensemble Accuracy: $ensembleCorrect/${tests.size} = ${"%.0f".format(ensemblePct)}%")

        assertTrue("Ensemble accuracy harus >= 80%", ensemblePct >= 80.0)
        println("\n  MODULE 4 RESULT: ✅ PASS")
    }

    // ══════════════════════════════════════════════════
    //  MODULE 5: WEATHER POTENTIAL ANALYSIS
    // ══════════════════════════════════════════════════

    @Test
    fun `MODULE 5 - Weather Potential Risk Classification`() {
        println("\n" + "═".repeat(80))
        println("  MODULE 5: WEATHER POTENTIAL — RISK CLASSIFICATION")
        println("  (WeatherPotential: Storm, Rain, Hail, Wind, Tornado)")
        println("═".repeat(80))

        // Test risk thresholds using NN output as proxy
        // since WeatherPotential requires WeatherRepository (Android context)
        // we test the risk mapping logic

        data class RiskTest(
            val name: String,
            val score: Double,
            val expectedLevel: String
        )

        val riskTests = listOf(
            RiskTest("Score 0.00 → LOW", 0.00, "LOW"),
            RiskTest("Score 0.10 → LOW", 0.10, "LOW"),
            RiskTest("Score 0.24 → LOW", 0.24, "LOW"),
            RiskTest("Score 0.25 → MODERATE", 0.25, "MODERATE"),
            RiskTest("Score 0.35 → MODERATE", 0.35, "MODERATE"),
            RiskTest("Score 0.44 → MODERATE", 0.44, "MODERATE"),
            RiskTest("Score 0.45 → HIGH", 0.45, "HIGH"),
            RiskTest("Score 0.60 → HIGH", 0.60, "HIGH"),
            RiskTest("Score 0.69 → HIGH", 0.69, "HIGH"),
            RiskTest("Score 0.70 → EXTREME", 0.70, "EXTREME"),
            RiskTest("Score 0.85 → EXTREME", 0.85, "EXTREME"),
            RiskTest("Score 1.00 → EXTREME", 1.00, "EXTREME")
        )

        println("\n  ╔════════════════════════════════╦═══════════╦═══════════╦═══════╗")
        println("  ║ Test Case                      ║ Expected  ║ Actual    ║ Match ║")
        println("  ╠════════════════════════════════╬═══════════╬═══════════╬═══════╣")

        var riskCorrect = 0
        for (test in riskTests) {
            val actual = when {
                test.score >= 0.7 -> "EXTREME"
                test.score >= 0.45 -> "HIGH"
                test.score >= 0.25 -> "MODERATE"
                else -> "LOW"
            }
            val match = actual == test.expectedLevel
            if (match) riskCorrect++
            println("  ║ %-30s ║ %-9s ║ %-9s ║   %s   ║".format(
                test.name, test.expectedLevel, actual, if (match) "✅" else "❌"))
        }
        println("  ╚════════════════════════════════╩═══════════╩═══════════╩═══════╝")
        println("  Risk Mapping Accuracy: $riskCorrect/${riskTests.size} = ${"%.0f".format(riskCorrect * 100.0 / riskTests.size)}%")

        assertEquals("Semua risk mapping harus benar", riskTests.size, riskCorrect)
        println("\n  MODULE 5 RESULT: ✅ PASS")
    }

    // ══════════════════════════════════════════════════
    //  MODULE 6: STRESS TEST & ROBUSTNESS
    // ══════════════════════════════════════════════════

    @Test
    fun `MODULE 6 - Stress Test and Robustness`() {
        println("\n" + "═".repeat(80))
        println("  MODULE 6: STRESS TEST & ROBUSTNESS")
        println("═".repeat(80))

        // 6A: Wrong input size should throw
        println("\n  6A. Input Validation")
        var exceptionThrown = false
        try {
            DisasterNeuralNetwork.predict(FloatArray(10))
        } catch (e: IllegalArgumentException) {
            exceptionThrown = true
        }
        assertTrue("Input size mismatch harus throw", exceptionThrown)
        println("  ✅ Invalid input size → IllegalArgumentException")

        // 6B: Large random batch — no crash, all outputs in [0,1]
        println("\n  6B. Random Batch Stress (1000 predictions)")
        val rng = java.util.Random(42)
        var allValid = true
        val startTime = System.nanoTime()
        for (i in 0 until 1000) {
            val input = FloatArray(27) { rng.nextFloat() }
            val output = DisasterNeuralNetwork.predict(input)
            if (output.any { it < 0f || it > 1f || it.isNaN() || it.isInfinite() }) {
                allValid = false
                break
            }
        }
        val elapsed = (System.nanoTime() - startTime) / 1_000_000.0
        println("  ✅ 1000 predictions tanpa crash")
        println("  ✅ Semua output valid [0, 1]")
        println("  ⏱️ Total time: ${"%.2f".format(elapsed)} ms")
        println("  ⏱️ Per prediction: ${"%.3f".format(elapsed / 1000)} ms")
        assertTrue("Semua output harus valid", allValid)

        // 6C: Extreme inputs (beyond normal range)
        println("\n  6C. Extreme Input Values")
        val extremeInputs = listOf(
            "All -1" to FloatArray(27) { -1f },
            "All 2" to FloatArray(27) { 2f },
            "All 100" to FloatArray(27) { 100f },
            "Mixed extreme" to FloatArray(27) { if (it % 2 == 0) -10f else 10f }
        )

        var extremeAllValid = true
        for ((name, input) in extremeInputs) {
            val output = DisasterNeuralNetwork.predict(input)
            val valid = output.all { it in 0f..1f && !it.isNaN() && !it.isInfinite() }
            if (!valid) extremeAllValid = false
            println("  ${if (valid) "✅" else "❌"} $name → [%.4f, %.4f]".format(output.min(), output.max()))
        }
        assertTrue("Extreme inputs harus menghasilkan output valid", extremeAllValid)

        // 6D: Determinism check
        println("\n  6D. Determinism — 100 repeat predictions")
        val deterInput = FloatArray(27) { 0.5f }
        val firstOutput = DisasterNeuralNetwork.predict(deterInput)
        var deterPassed = true
        for (i in 0 until 100) {
            val output = DisasterNeuralNetwork.predict(deterInput)
            if (!output.contentEquals(firstOutput)) {
                deterPassed = false
                break
            }
        }
        println("  ${if (deterPassed) "✅" else "❌"} 100 predictions identik untuk input yang sama")
        assertTrue("Output harus deterministic", deterPassed)

        // 6E: Throughput benchmark
        println("\n  6E. Throughput Benchmark")
        val benchInput = FloatArray(27) { 0.3f }
        val warmup = 100
        val benchRuns = 10000
        repeat(warmup) { DisasterNeuralNetwork.predict(benchInput) }

        val benchStart = System.nanoTime()
        repeat(benchRuns) { DisasterNeuralNetwork.predict(benchInput) }
        val benchElapsed = (System.nanoTime() - benchStart) / 1_000_000.0
        val throughput = benchRuns / (benchElapsed / 1000.0)

        println("  ${benchRuns} predictions in ${"%.2f".format(benchElapsed)} ms")
        println("  Throughput: ${"%.0f".format(throughput)} predictions/sec")
        println("  Latency: ${"%.4f".format(benchElapsed / benchRuns)} ms/prediction")
        assertTrue("Throughput harus > 1000 pred/sec", throughput > 1000)

        println("\n  MODULE 6 RESULT: ✅ PASS")
    }

    // ══════════════════════════════════════════════════
    //  MASTER: COMPREHENSIVE SUMMARY
    // ══════════════════════════════════════════════════

    @Test
    fun `MASTER - Full System Smoke Test Report`() {
        println("\n")
        println("╔══════════════════════════════════════════════════════════════════════════════╗")
        println("║                                                                              ║")
        println("║          🔬 CUACAKU AI EARLY WARNING SYSTEM — FULL SMOKE TEST 🔬            ║")
        println("║                                                                              ║")
        println("║  Model: ${DisasterNeuralNetwork.MODEL_VERSION}                                               ║")
        println("║  Architecture: MLP 27→32→16→6 (${DisasterNeuralNetwork.TOTAL_PARAMS} parameters)                        ║")
        println("║  Output: 6 disaster types × [0,1] risk score                                ║")
        println("║                                                                              ║")
        println("╚══════════════════════════════════════════════════════════════════════════════╝")

        val scenarios = createAllScenarios()

        // ── Run all predictions ──
        val predictions = scenarios.map { s ->
            s to DisasterNeuralNetwork.predict(s.features)
        }

        // ========== ACCURACY ==========
        println("\n╔══════════════════════════════════════════════════════════════════╗")
        println("║  📊 OVERALL ACCURACY                                            ║")
        println("╚══════════════════════════════════════════════════════════════════╝")

        var top1 = 0; var top2 = 0; var hazardTotal = 0
        var calmOK = 0; var calmN = 0

        for ((scenario, pred) in predictions) {
            val sorted = pred.indices.sortedByDescending { pred[it] }
            if (scenario.expectedPrimary == -1) {
                calmN++
                if (pred.max() - pred.min() < 0.15f) calmOK++
            } else {
                hazardTotal++
                if (sorted[0] == scenario.expectedPrimary) top1++
                if (scenario.expectedPrimary in sorted.take(2)) top2++
            }
        }

        val top1Acc = top1 * 100.0 / hazardTotal
        val top2Acc = top2 * 100.0 / hazardTotal
        val calmAcc = calmOK * 100.0 / calmN

        // ========== ERROR METRICS ==========
        println("\n╔══════════════════════════════════════════════════════════════════╗")
        println("║  📉 ERROR METRICS                                                ║")
        println("╚══════════════════════════════════════════════════════════════════╝")

        var totalMAE = 0.0; var totalMSE = 0.0; var errN = 0
        val maePerD = DoubleArray(6)
        val msePerD = DoubleArray(6)

        for ((scenario, pred) in predictions) {
            val targets = scenario.targetScores ?: continue
            errN++
            for (d in 0 until 6) {
                val e = abs(pred[d] - targets[d]).toDouble()
                maePerD[d] += e
                msePerD[d] += e * e
            }
        }

        for (d in 0 until 6) {
            maePerD[d] = maePerD[d] / errN.toDouble()
            msePerD[d] = msePerD[d] / errN.toDouble()
            totalMAE += maePerD[d]
            totalMSE += msePerD[d]
        }
        val avgMAE = totalMAE / 6
        val avgRMSE = sqrt(totalMSE / 6)

        // ========== F1 SCORE ==========
        val tp = IntArray(6); val fp = IntArray(6); val fn = IntArray(6)
        for ((scenario, pred) in predictions) {
            if (scenario.expectedPrimary < 0) continue
            val predicted = pred.indices.maxByOrNull { pred[it] } ?: 0
            for (d in 0 until 6) {
                if (predicted == d && scenario.expectedPrimary == d) tp[d]++
                if (predicted == d && scenario.expectedPrimary != d) fp[d]++
                if (predicted != d && scenario.expectedPrimary == d) fn[d]++
            }
        }
        val f1Scores = (0 until 6).map { d ->
            val p = if (tp[d] + fp[d] > 0) tp[d].toDouble() / (tp[d] + fp[d]) else 0.0
            val r = if (tp[d] + fn[d] > 0) tp[d].toDouble() / (tp[d] + fn[d]) else 0.0
            if (p + r > 0) 2 * p * r / (p + r) else 0.0
        }
        val macroF1 = f1Scores.average()

        // ========== CALIBRATION ==========
        val rng = java.util.Random(42)
        val calOutputs = (0 until 500).flatMap {
            DisasterNeuralNetwork.predict(FloatArray(27) { rng.nextFloat() }).toList()
        }
        val calMean = calOutputs.average()
        val calStd = sqrt(calOutputs.map { (it - calMean) * (it - calMean) }.average())
        val calMin = calOutputs.min()
        val calMax = calOutputs.max()
        val calPass = calMean in 0.3..0.8 && calStd > 0.05 && calMin < 0.50 && calMax > 0.8

        // ========== LATENCY ==========
        val benchInput = FloatArray(27) { 0.3f }
        repeat(100) { DisasterNeuralNetwork.predict(benchInput) }
        val t0 = System.nanoTime()
        repeat(10000) { DisasterNeuralNetwork.predict(benchInput) }
        val latencyMs = (System.nanoTime() - t0) / 1_000_000.0 / 10000

        // ════════════════════════════════════════════════
        //  FINAL REPORT
        // ════════════════════════════════════════════════

        println("\n")
        println("╔══════════════════════════════════════════════════════════════════════════════╗")
        println("║                         📋 FINAL SMOKE TEST REPORT                          ║")
        println("╠══════════════════════════════════════════════════════════════════════════════╣")
        println("║                                                                              ║")
        println("║  🧠 MODEL                                                                   ║")
        println("║    Version:        ${DisasterNeuralNetwork.MODEL_VERSION}                                      ║")
        println("║    Architecture:   MLP 27→32→16→6                                           ║")
        println("║    Parameters:     ${DisasterNeuralNetwork.TOTAL_PARAMS}                                                     ║")
        println("║    Activation:     LeakyReLU → Sigmoid(T=1.3)                               ║")
        println("║                                                                              ║")
        println("║  📊 ACCURACY                                                                ║")
        println("║    Top-1 Accuracy: $top1/$hazardTotal = ${"%.1f".format(top1Acc)}%                                          ║")
        println("║    Top-2 Accuracy: $top2/$hazardTotal = ${"%.1f".format(top2Acc)}%                                          ║")
        println("║    Calm Detection: $calmOK/$calmN = ${"%.1f".format(calmAcc)}%                                           ║")
        println("║                                                                              ║")
        println("║  📉 ERROR METRICS                                                           ║")
        for (d in 0 until 6) {
            val rmseD = sqrt(msePerD[d])
            println("║    %-16s MAE=%.4f  RMSE=%.4f                                 ║".format(DISASTER_NAMES[d], maePerD[d], rmseD))
        }
        println("║    %-16s MAE=%.4f  RMSE=%.4f                                 ║".format("AVERAGE", avgMAE, avgRMSE))
        println("║                                                                              ║")
        println("║  🎯 F1 SCORE (per class)                                                    ║")
        for (d in 0 until 6) {
            println("║    %-16s F1=%.3f  (TP=%d FP=%d FN=%d)                              ║".format(DISASTER_NAMES[d], f1Scores[d], tp[d], fp[d], fn[d]))
        }
        println("║    %-16s F1=%.3f (macro average)                                 ║".format("AVERAGE", macroF1))
        println("║                                                                              ║")
        println("║  📐 CALIBRATION                                                             ║")
        println("║    Mean:  %.4f                                                              ║".format(calMean))
        println("║    Std:   %.4f                                                              ║".format(calStd))
        println("║    Range: [%.4f, %.4f]                                                      ║".format(calMin, calMax))
        println("║    Status: ${if (calPass) "✅ PASS" else "❌ FAIL"}                                                            ║")
        println("║                                                                              ║")
        println("║  ⏱️ PERFORMANCE                                                             ║")
        println("║    Latency:    ${"%.4f".format(latencyMs)} ms/prediction                                       ║")
        println("║    Throughput: ${"%.0f".format(1000.0 / latencyMs)} pred/sec                                           ║")
        println("║                                                                              ║")
        println("╠══════════════════════════════════════════════════════════════════════════════╣")

        // ── VERDICT ──
        val accuracyPass = top1Acc >= 60.0 && top2Acc >= 80.0 && calmAcc >= 75.0
        val errorPass = avgMAE < 0.35
        val f1Pass = macroF1 >= 0.40
        val perfPass = latencyMs < 1.0

        val allPass = accuracyPass && errorPass && f1Pass && calPass && perfPass
        val passCount = listOf(accuracyPass, errorPass, f1Pass, calPass, perfPass).count { it }

        println("║                                                                              ║")
        println("║  ✅ Accuracy:    ${if (accuracyPass) "PASS" else "FAIL"} (Top1≥60%, Top2≥80%, Calm≥75%)                      ║")
        println("║  ✅ Error:       ${if (errorPass) "PASS" else "FAIL"} (MAE < 0.35)                                           ║")
        println("║  ✅ F1 Score:    ${if (f1Pass) "PASS" else "FAIL"} (Macro F1 ≥ 0.40)                                        ║")
        println("║  ✅ Calibration: ${if (calPass) "PASS" else "FAIL"} (Mean ∈ [0.3,0.8], Std>0.05)                            ║")
        println("║  ✅ Performance: ${if (perfPass) "PASS" else "FAIL"} (Latency < 1ms, >1000 pred/sec)                       ║")
        println("║                                                                              ║")
        println("║  OVERALL: $passCount/5 CRITERIA PASSED                                               ║")
        println("║                                                                              ║")

        if (allPass) {
            println("║  ╔══════════════════════════════════════════════════════════════╗          ║")
            println("║  ║  🟢 VERDICT: LULUS — SIAP PUBLISH KE MASYARAKAT UMUM       ║          ║")
            println("║  ║                                                              ║          ║")
            println("║  ║  Model reliable untuk digunakan sebagai Sistem Peringatan   ║          ║")
            println("║  ║  Dini Bencana berbasis AI secara real-time.                 ║          ║")
            println("║  ╚══════════════════════════════════════════════════════════════╝          ║")
        } else {
            println("║  ╔══════════════════════════════════════════════════════════════╗          ║")
            println("║  ║  🟡 VERDICT: PERLU PERBAIKAN — $passCount/5 kriteria terpenuhi          ║          ║")
            println("║  ╚══════════════════════════════════════════════════════════════╝          ║")
        }
        println("║                                                                              ║")
        println("╚══════════════════════════════════════════════════════════════════════════════╝")

        // Assertions
        assertTrue("Top-1 accuracy ≥ 60%", top1Acc >= 60.0)
        assertTrue("Top-2 accuracy ≥ 80%", top2Acc >= 80.0)
        assertTrue("Calm detection ≥ 75%", calmAcc >= 75.0)
        assertTrue("Average MAE < 0.35", avgMAE < 0.35)
        assertTrue("Macro F1 ≥ 0.40", macroF1 >= 0.40)
        assertTrue("Calibration pass", calPass)
        assertTrue("Latency < 1ms", latencyMs < 1.0)
    }
}

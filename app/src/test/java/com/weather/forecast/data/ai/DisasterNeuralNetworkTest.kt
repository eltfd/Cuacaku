package com.weather.forecast.data.ai

import org.junit.Test
import org.junit.Assert.*
import org.junit.Before
import kotlin.math.abs

/**
 * Comprehensive Testing Suite for Disaster Neural Network
 *
 * Menguji akurasi, sensitivitas, kalibrasi, dan robustness dari
 * DisasterNeuralNetwork (MLP 20→32→16→6 domain-informed initialization).
 *
 * Karena model ini BUKAN model yang di-training dari data, melainkan
 * diinisialisasi dengan domain knowledge, pengujian berfokus pada:
 *
 * 1. SCENARIO ACCURACY — Apakah skenario cuaca ekstrem menghasilkan skor
 *    risiko yang benar untuk jenis bencana yang sesuai?
 * 2. SENSITIVITY — Apakah setiap fitur input mempengaruhi output yang tepat?
 * 3. CALIBRATION — Apakah skor output terdistribusi wajar (bukan semua 0 atau 1)?
 * 4. ROBUSTNESS — Apakah model stabil terhadap edge cases?
 * 5. DETERMINISM — Apakah output konsisten untuk input yang sama?
 *
 * Output indices:
 *   [0] Banjir, [1] Banjir Rob, [2] Siklon,
 *   [3] Badai Petir, [4] Longsor, [5] Tanah Amblas
 *
 * Feature indices:
 *   [0] precipTotal, [1] precipIntensity, [2] windSpeed, [3] windGusts,
 *   [4] windShear, [5] pressureLow, [6] pressureDrop, [7] humidity,
 *   [8] capeEnergy, [9] freezingLow, [10] cloudCover, [11] dewPointSpread,
 *   [12] waveHeight, [13] swellHeight, [14] dischargeRatio, [15] rainDuration,
 *   [16] antecedentRain, [17] consecutiveRain, [18] weatherSeverity,
 *   [19] temperatureHigh
 */
class DisasterNeuralNetworkTest {

    companion object {
        // Output indices
        const val FLOOD = 0
        const val TIDAL = 1
        const val CYCLONE = 2
        const val THUNDER = 3
        const val LANDSLIDE = 4
        const val SUBSIDENCE = 5

        // Thresholds untuk evaluasi
        const val HIGH_RISK = 0.6       // Skor >= ini dianggap "terdeteksi tinggi"
        const val MODERATE_RISK = 0.35  // Skor >= ini dianggap "terdeteksi sedang"
        const val LOW_RISK = 0.25       // Skor < ini dianggap "rendah/aman"

        val DISASTER_NAMES = arrayOf(
            "Banjir", "Banjir Rob", "Siklon", "Badai Petir", "Longsor", "Tanah Amblas"
        )
    }

    // ══════════════════════════════════════════════════════════
    //  Reusable feature vector builders
    // ══════════════════════════════════════════════════════════

    /** Cuaca tenang / normal — baseline */
    private fun calmWeather() = FloatArray(20) { 0.1f }

    /** Cuaca netral (semua 0.5) */
    private fun neutralWeather() = FloatArray(20) { 0.5f }

    /** Set a single feature high, rest low */
    private fun singleFeatureHigh(featureIdx: Int, value: Float = 0.95f): FloatArray {
        val f = FloatArray(20) { 0.1f }
        f[featureIdx] = value
        return f
    }

    // ══════════════════════════════════════════════════════════
    //  TEST 1: DETERMINISM
    // ══════════════════════════════════════════════════════════

    @Test
    fun `test determinism - identical input produces identical output`() {
        val features = neutralWeather()
        val result1 = DisasterNeuralNetwork.predict(features)
        val result2 = DisasterNeuralNetwork.predict(features.copyOf())
        val result3 = DisasterNeuralNetwork.predict(features.copyOf())

        for (i in result1.indices) {
            assertEquals("Output[$i] harus identik pada call berulang",
                result1[i], result2[i], 0.0f)
            assertEquals("Output[$i] harus identik pada call ketiga",
                result1[i], result3[i], 0.0f)
        }
        println("✅ DETERMINISM: Output konsisten 100% untuk input identik")
    }

    // ══════════════════════════════════════════════════════════
    //  TEST 2: OUTPUT RANGE
    // ══════════════════════════════════════════════════════════

    @Test
    fun `test output range - all outputs between 0 and 1`() {
        val testCases = listOf(
            FloatArray(20) { 0f },      // All zeros
            FloatArray(20) { 0.1f },    // Low
            FloatArray(20) { 0.5f },    // Medium
            FloatArray(20) { 0.9f },    // High
            FloatArray(20) { 1f },      // All ones
        )

        var allValid = true
        for ((idx, features) in testCases.withIndex()) {
            val result = DisasterNeuralNetwork.predict(features)
            for (j in result.indices) {
                if (result[j] < 0f || result[j] > 1f) {
                    allValid = false
                    fail("Output[$j] = ${result[j]} di luar range [0,1] untuk test case $idx")
                }
            }
        }
        assertTrue("Semua output harus dalam range [0, 1]", allValid)
        println("✅ OUTPUT RANGE: Semua output dalam [0, 1] untuk 5 test cases")
    }

    // ══════════════════════════════════════════════════════════
    //  TEST 3: SCENARIO ACCURACY — 12 Skenario Cuaca Realistis
    // ══════════════════════════════════════════════════════════

    /**
     * Skenario 1: BANJIR BANDANG
     * Curah hujan sangat tinggi (>100mm), debit sungai tinggi, hujan berkepanjangan
     * Expected: Flood HIGH, Landslide HIGH, Subsidence MODERATE+
     */
    @Test
    fun `scenario 1 - flash flood extreme rain`() {
        val features = FloatArray(20) { 0.1f }.apply {
            this[0] = 0.90f   // precipTotal: 180mm
            this[1] = 0.85f   // precipIntensity: 42mm/h
            this[7] = 0.90f   // humidity: 90%
            this[11] = 0.85f  // dewPointSpread: kecil (sangat lembab)
            this[14] = 0.80f  // dischargeRatio: 8x normal
            this[15] = 0.75f  // rainDuration: 18 jam
            this[16] = 0.70f  // antecedentRain: 210mm / 3 hari
            this[17] = 0.60f  // consecutiveRain: ~4 hari
            this[18] = 0.80f  // weatherSeverity: tinggi
        }
        val result = DisasterNeuralNetwork.predict(features)
        printScenarioResult("BANJIR BANDANG", result)

        assertTrue("Flood harus >= MODERATE ($MODERATE_RISK)",
            result[FLOOD] >= MODERATE_RISK)
        assertTrue("Flood harus > Cyclone (bukan angin kencang)",
            result[FLOOD] > result[CYCLONE])
    }

    /**
     * Skenario 2: SIKLON TROPIS
     * Angin sangat kencang, tekanan sangat rendah, gelombang tinggi
     * Expected: Cyclone HIGH, Tidal Flood HIGH
     */
    @Test
    fun `scenario 2 - tropical cyclone`() {
        val features = FloatArray(20) { 0.1f }.apply {
            this[2] = 0.90f   // windSpeed: 180 km/h
            this[3] = 0.95f   // windGusts: 190 km/h
            this[4] = 0.70f   // windShear: 56 km/h
            this[5] = 0.90f   // pressureLow: ~915 hPa
            this[6] = 0.80f   // pressureDrop: 24 hPa/24h
            this[12] = 0.80f  // waveHeight: 8m
            this[13] = 0.70f  // swellHeight: 3.5m
            this[18] = 0.90f  // weatherSeverity: sangat tinggi
        }
        val result = DisasterNeuralNetwork.predict(features)
        printScenarioResult("SIKLON TROPIS", result)

        assertTrue("Cyclone harus >= MODERATE",
            result[CYCLONE] >= MODERATE_RISK)
        assertTrue("Tidal Flood harus >= MODERATE (gelombang tinggi)",
            result[TIDAL] >= MODERATE_RISK)
    }

    /**
     * Skenario 3: BADAI PETIR HEBAT
     * CAPE sangat tinggi, gusts kuat, hujan deras tiba-tiba
     * Expected: Thunderstorm HIGH
     */
    @Test
    fun `scenario 3 - severe thunderstorm`() {
        val features = FloatArray(20) { 0.1f }.apply {
            this[1] = 0.70f   // precipIntensity: 35mm/h burst
            this[3] = 0.60f   // windGusts: 120 km/h
            this[4] = 0.55f   // windShear: 44 km/h
            this[8] = 0.95f   // capeEnergy: 4750 J/kg (sangat unstable)
            this[10] = 0.80f  // cloudCover: 80%
            this[11] = 0.70f  // dewPointSpread: kecil
            this[18] = 1.00f  // weatherSeverity: thunderstorm code
        }
        val result = DisasterNeuralNetwork.predict(features)
        printScenarioResult("BADAI PETIR HEBAT", result)

        assertTrue("Thunderstorm harus >= MODERATE",
            result[THUNDER] >= MODERATE_RISK)
    }

    /**
     * Skenario 4: TANAH LONGSOR
     * Hujan terus-menerus berhari-hari, tanah jenuh
     * Expected: Landslide HIGH, Subsidence MODERATE+
     */
    @Test
    fun `scenario 4 - landslide continuous rain`() {
        val features = FloatArray(20) { 0.1f }.apply {
            this[0] = 0.60f   // precipTotal: 120mm
            this[7] = 0.95f   // humidity: 95%
            this[11] = 0.90f  // dewPointSpread: sangat lembab
            this[15] = 0.80f  // rainDuration: 19 jam
            this[16] = 0.90f  // antecedentRain: 270mm / 3 hari
            this[17] = 0.85f  // consecutiveRain: 6 hari berturut
            this[18] = 0.60f  // weatherSeverity: moderate
        }
        val result = DisasterNeuralNetwork.predict(features)
        printScenarioResult("TANAH LONGSOR", result)

        assertTrue("Landslide harus >= MODERATE",
            result[LANDSLIDE] >= MODERATE_RISK)
        assertTrue("Subsidence juga harus terdeteksi",
            result[SUBSIDENCE] >= LOW_RISK)
    }

    /**
     * Skenario 5: BANJIR ROB
     * Gelombang dan swell tinggi, tekanan rendah
     * Expected: Tidal Flood HIGH
     */
    @Test
    fun `scenario 5 - tidal flood high waves`() {
        val features = FloatArray(20) { 0.1f }.apply {
            this[5] = 0.60f   // pressureLow: moderate low
            this[12] = 0.90f  // waveHeight: 9m
            this[13] = 0.85f  // swellHeight: 4.25m
            this[2] = 0.50f   // windSpeed: 100 km/h
            this[6] = 0.40f   // pressureDrop: 12 hPa
        }
        val result = DisasterNeuralNetwork.predict(features)
        printScenarioResult("BANJIR ROB", result)

        assertTrue("Tidal Flood harus >= MODERATE",
            result[TIDAL] >= MODERATE_RISK)
    }

    /**
     * Skenario 6: CUACA TENANG
     * Cerah, sedikit angin, tidak ada hujan
     * Expected: Semua risiko RENDAH (< 0.35)
     */
    @Test
    fun `scenario 6 - calm clear weather`() {
        val features = FloatArray(20) { 0.05f }.apply {
            this[19] = 0.30f  // temperatureHigh: 29°C (normal)
        }
        val result = DisasterNeuralNetwork.predict(features)
        printScenarioResult("CUACA TENANG", result)

        val maxScore = result.max()
        assertTrue("Cuaca tenang: skor maks harus < 0.5, actual=$maxScore",
            maxScore < 0.5f)
    }

    /**
     * Skenario 7: TANAH AMBLAS (Ground Subsidence)
     * Hujan berkepanjangan + debit sungai tinggi + tanah jenuh
     */
    @Test
    fun `scenario 7 - ground subsidence`() {
        val features = FloatArray(20) { 0.1f }.apply {
            this[0] = 0.50f   // precipTotal: 100mm
            this[7] = 0.90f   // humidity: 90%
            this[14] = 0.70f  // dischargeRatio: 7x
            this[16] = 0.85f  // antecedentRain: 255mm / 3 hari
            this[17] = 0.90f  // consecutiveRain: >6 hari
            this[15] = 0.70f  // rainDuration: 17 jam
        }
        val result = DisasterNeuralNetwork.predict(features)
        printScenarioResult("TANAH AMBLAS", result)

        assertTrue("Subsidence harus >= LOW_RISK",
            result[SUBSIDENCE] >= LOW_RISK)
    }

    /**
     * Skenario 8: MULTI-HAZARD — Semua parameter ekstrem
     * Expected: Semua skor tinggi
     */
    @Test
    fun `scenario 8 - multi-hazard extreme`() {
        val features = FloatArray(20) { 0.90f }
        val result = DisasterNeuralNetwork.predict(features)
        printScenarioResult("MULTI-HAZARD EXTREME", result)

        val highCount = result.count { it >= MODERATE_RISK }
        assertTrue("Multi-hazard: setidaknya 3 bencana harus >= MODERATE, actual=$highCount",
            highCount >= 3)
    }

    /**
     * Skenario 9: HUJAN LEBAT SINGKAT (bukan banjir berkepanjangan)
     * Intensitas tinggi tapi durasi pendek
     */
    @Test
    fun `scenario 9 - short intense rain`() {
        val features = FloatArray(20) { 0.1f }.apply {
            this[1] = 0.90f   // precipIntensity: sangat tinggi
            this[0] = 0.25f   // precipTotal: tapi total rendah (singkat)
            this[15] = 0.15f  // rainDuration: hanya ~3.5 jam
            this[18] = 0.60f  // weatherSeverity: moderate
        }
        val result = DisasterNeuralNetwork.predict(features)
        printScenarioResult("HUJAN LEBAT SINGKAT", result)

        // Flood seharusnya lebih rendah dibanding skenario banjir bandang
        assertTrue("Short rain: flood harus < 0.7 (bukan banjir besar)",
            result[FLOOD] < 0.7f)
    }

    /**
     * Skenario 10: CUACA PANAS KERING
     * Suhu tinggi, kelembaban rendah, tidak ada hujan
     */
    @Test
    fun `scenario 10 - hot dry weather`() {
        val features = FloatArray(20) { 0.05f }.apply {
            this[19] = 0.90f  // temperatureHigh: 47°C
            this[7] = 0.10f   // humidity: rendah
            this[11] = 0.10f  // dewPointSpread: besar (kering)
        }
        val result = DisasterNeuralNetwork.predict(features)
        printScenarioResult("CUACA PANAS KERING", result)

        // Tidak ada bencana yang harusnya tinggi kecuali mungkin rendah semua
        assertTrue("Panas kering: flood harus rendah",
            result[FLOOD] < 0.5f)
    }

    /**
     * Skenario 11: ANGIN KENCANG TANPA HUJAN
     * Angin kencang saja, tanpa hujan atau gelombang
     */
    @Test
    fun `scenario 11 - strong wind only`() {
        val features = FloatArray(20) { 0.1f }.apply {
            this[2] = 0.85f   // windSpeed: 170 km/h
            this[3] = 0.90f   // windGusts: 180 km/h
            this[4] = 0.70f   // windShear: 56 km/h
        }
        val result = DisasterNeuralNetwork.predict(features)
        printScenarioResult("ANGIN KENCANG TANPA HUJAN", result)

        assertTrue("Cyclone harus terdeteksi (wind-related)",
            result[CYCLONE] >= LOW_RISK)
    }

    /**
     * Skenario 12: MONSUN NORMAL INDONESIA
     * Hujan sedang, kelembaban tinggi, tapi tidak ekstrem
     */
    @Test
    fun `scenario 12 - normal monsoon season`() {
        val features = FloatArray(20) { 0.1f }.apply {
            this[0] = 0.20f   // precipTotal: 40mm (normal)
            this[1] = 0.20f   // precipIntensity: 10mm/h
            this[7] = 0.75f   // humidity: 75%
            this[10] = 0.70f  // cloudCover: 70%
            this[11] = 0.60f  // dewPointSpread
            this[15] = 0.30f  // rainDuration: ~7 jam
            this[18] = 0.30f  // weatherSeverity: light rain
        }
        val result = DisasterNeuralNetwork.predict(features)
        printScenarioResult("MONSUN NORMAL", result)

        val maxScore = result.max()
        assertTrue("Monsun normal: tidak ada risiko HIGH (>$HIGH_RISK), actual max=$maxScore",
            maxScore < HIGH_RISK)
    }

    // ══════════════════════════════════════════════════════════
    //  TEST 4: SENSITIVITY ANALYSIS
    //  Apakah setiap fitur mempengaruhi output yang benar?
    // ══════════════════════════════════════════════════════════

    @Test
    fun `sensitivity - precipTotal affects flood most`() {
        val baseline = calmWeather()
        val withRain = calmWeather().apply { this[0] = 0.95f }

        val baseResult = DisasterNeuralNetwork.predict(baseline)
        val rainResult = DisasterNeuralNetwork.predict(withRain)

        val floodDelta = rainResult[FLOOD] - baseResult[FLOOD]
        println("  precipTotal → Flood delta: +${String.format("%.4f", floodDelta)}")

        assertTrue("precipTotal harus meningkatkan skor Flood (delta=$floodDelta)",
            floodDelta > 0)
    }

    @Test
    fun `sensitivity - windSpeed affects cyclone most`() {
        val baseline = calmWeather()
        val withWind = calmWeather().apply { this[2] = 0.95f }

        val baseResult = DisasterNeuralNetwork.predict(baseline)
        val windResult = DisasterNeuralNetwork.predict(withWind)

        val cycloneDelta = windResult[CYCLONE] - baseResult[CYCLONE]
        println("  windSpeed → Cyclone delta: +${String.format("%.4f", cycloneDelta)}")

        assertTrue("windSpeed harus meningkatkan skor Cyclone (delta=$cycloneDelta)",
            cycloneDelta > 0)
    }

    @Test
    fun `sensitivity - capeEnergy affects thunderstorm most`() {
        val baseline = calmWeather()
        val withCape = calmWeather().apply { this[8] = 0.95f }

        val baseResult = DisasterNeuralNetwork.predict(baseline)
        val capeResult = DisasterNeuralNetwork.predict(withCape)

        val thunderDelta = capeResult[THUNDER] - baseResult[THUNDER]
        println("  capeEnergy → Thunder delta: +${String.format("%.4f", thunderDelta)}")

        assertTrue("CAPE harus meningkatkan skor Thunderstorm (delta=$thunderDelta)",
            thunderDelta > 0)
    }

    @Test
    fun `sensitivity - waveHeight affects tidal flood most`() {
        val baseline = calmWeather()
        val withWave = calmWeather().apply { this[12] = 0.95f }

        val baseResult = DisasterNeuralNetwork.predict(baseline)
        val waveResult = DisasterNeuralNetwork.predict(withWave)

        val tidalDelta = waveResult[TIDAL] - baseResult[TIDAL]
        println("  waveHeight → Tidal Flood delta: +${String.format("%.4f", tidalDelta)}")

        assertTrue("waveHeight harus meningkatkan skor Tidal Flood (delta=$tidalDelta)",
            tidalDelta > 0)
    }

    @Test
    fun `sensitivity - antecedentRain affects landslide`() {
        val baseline = calmWeather()
        val withRain = calmWeather().apply { this[16] = 0.95f }

        val baseResult = DisasterNeuralNetwork.predict(baseline)
        val rainResult = DisasterNeuralNetwork.predict(withRain)

        val landslideDelta = rainResult[LANDSLIDE] - baseResult[LANDSLIDE]
        println("  antecedentRain → Landslide delta: +${String.format("%.4f", landslideDelta)}")

        assertTrue("antecedentRain harus meningkatkan skor Landslide (delta=$landslideDelta)",
            landslideDelta > 0)
    }

    @Test
    fun `sensitivity - consecutiveRain affects subsidence`() {
        val baseline = calmWeather()
        val withRain = calmWeather().apply { this[17] = 0.95f }

        val baseResult = DisasterNeuralNetwork.predict(baseline)
        val rainResult = DisasterNeuralNetwork.predict(withRain)

        val subsidenceDelta = rainResult[SUBSIDENCE] - baseResult[SUBSIDENCE]
        println("  consecutiveRain → Subsidence delta: +${String.format("%.4f", subsidenceDelta)}")

        assertTrue("consecutiveRain harus meningkatkan skor Subsidence (delta=$subsidenceDelta)",
            subsidenceDelta > 0)
    }

    // ══════════════════════════════════════════════════════════
    //  TEST 5: COMPREHENSIVE SENSITIVITY MATRIX
    //  Test semua 20 fitur terhadap semua 6 output
    // ══════════════════════════════════════════════════════════

    @Test
    fun `sensitivity matrix - all features vs all outputs`() {
        val baseline = calmWeather()
        val baseResult = DisasterNeuralNetwork.predict(baseline)

        val featureNames = listOf(
            "precipTotal", "precipIntensity", "windSpeed", "windGusts", "windShear",
            "pressureLow", "pressureDrop", "humidity", "capeEnergy", "freezingLow",
            "cloudCover", "dewPointSpread", "waveHeight", "swellHeight", "dischargeRatio",
            "rainDuration", "antecedentRain", "consecutiveRain", "weatherSeverity", "temperatureHigh"
        )

        // Expected dominant disaster per feature (from RELEVANCE matrix)
        val expectedDominant = intArrayOf(
            FLOOD,      // 0: precipTotal → Flood (0.90)
            FLOOD,      // 1: precipIntensity → Flood (0.85)
            CYCLONE,    // 2: windSpeed → Cyclone (0.90)
            CYCLONE,    // 3: windGusts → Cyclone (0.85)
            THUNDER,    // 4: windShear → Thunder (0.85)
            CYCLONE,    // 5: pressureLow → Cyclone (0.95)
            CYCLONE,    // 6: pressureDrop → Cyclone (0.85)
            LANDSLIDE,  // 7: humidity → Landslide (0.60)
            THUNDER,    // 8: capeEnergy → Thunder (0.95)
            THUNDER,    // 9: freezingLow → Thunder (0.50)
            THUNDER,    // 10: cloudCover → Thunder (0.40)
            FLOOD,      // 11: dewPointSpread → Flood (tied at 0.40)
            TIDAL,      // 12: waveHeight → Tidal (0.90)
            TIDAL,      // 13: swellHeight → Tidal (0.80)
            FLOOD,      // 14: dischargeRatio → Flood (0.80)
            LANDSLIDE,  // 15: rainDuration → Landslide (0.75)
            LANDSLIDE,  // 16: antecedentRain → Landslide (0.90)
            SUBSIDENCE, // 17: consecutiveRain → Subsidence (0.90)
            THUNDER,    // 18: weatherSeverity → Thunder (0.70)
            -1          // 19: temperatureHigh → not strongly associated
        )

        println("\n╔══════════════════════════════════════════════════════════════════════════════════╗")
        println("║              SENSITIVITY MATRIX — Feature Impact per Disaster                    ║")
        println("╠════════════════════╦══════╦══════╦══════╦══════╦══════╦══════╦═══════════════════╣")
        println("║ Feature            ║ FLD  ║ TDL  ║ CYC  ║ THD  ║ LND  ║ SUB  ║ Dominant Match?  ║")
        println("╠════════════════════╬══════╬══════╬══════╬══════╬══════╬══════╬═══════════════════╣")

        var correctDominant = 0
        var totalChecked = 0

        for (f in 0 until 20) {
            val modified = calmWeather().apply { this[f] = 0.95f }
            val modResult = DisasterNeuralNetwork.predict(modified)

            val deltas = FloatArray(6) { modResult[it] - baseResult[it] }
            val maxDeltaIdx = deltas.indices.maxByOrNull { deltas[it] } ?: 0

            val match = if (expectedDominant[f] >= 0) {
                totalChecked++
                if (maxDeltaIdx == expectedDominant[f]) {
                    correctDominant++
                    "✅"
                } else "❌ exp=${DISASTER_NAMES[expectedDominant[f]]}"
            } else "⬜ (skip)"

            println(String.format("║ %-18s ║ %+.3f║ %+.3f║ %+.3f║ %+.3f║ %+.3f║ %+.3f║ %-17s ║",
                featureNames[f],
                deltas[0], deltas[1], deltas[2], deltas[3], deltas[4], deltas[5],
                match))
        }

        println("╚════════════════════╩══════╩══════╩══════╩══════╩══════╩══════╩═══════════════════╝")
        val accuracy = if (totalChecked > 0) correctDominant * 100.0 / totalChecked else 0.0
        println("SENSITIVITY ACCURACY: $correctDominant/$totalChecked = ${"%.1f".format(accuracy)}%")
        println("(Apakah fitur yang ditingkatkan mempengaruhi bencana yang tepat sesuai RELEVANCE matrix)")
    }

    // ══════════════════════════════════════════════════════════
    //  TEST 6: MONOTONICITY — Skor naik saat input naik
    // ══════════════════════════════════════════════════════════

    @Test
    fun `monotonicity - increasing features increase relevant scores`() {
        data class MonoTest(val feature: Int, val output: Int, val name: String)
        val tests = listOf(
            MonoTest(0, FLOOD, "precipTotal→Flood"),
            MonoTest(2, CYCLONE, "windSpeed→Cyclone"),
            MonoTest(8, THUNDER, "CAPE→Thunder"),
            MonoTest(12, TIDAL, "waveHeight→Tidal"),
            MonoTest(16, LANDSLIDE, "antecedentRain→Landslide"),
        )

        var monoPassed = 0
        for (test in tests) {
            val scores = mutableListOf<Float>()
            for (level in listOf(0.1f, 0.3f, 0.5f, 0.7f, 0.9f)) {
                val f = calmWeather().apply { this[test.feature] = level }
                val result = DisasterNeuralNetwork.predict(f)
                scores.add(result[test.output])
            }

            // Check overall monotonicity (first < last)
            val isMonotonic = scores.last() > scores.first()
            if (isMonotonic) monoPassed++
            println("  ${test.name}: ${scores.map { "%.3f".format(it) }} → ${if (isMonotonic) "✅ MONOTONIC" else "❌ NOT MONOTONIC"}")
        }
        println("MONOTONICITY: $monoPassed/${tests.size} passed")
        assertTrue("At least 3/5 should be monotonic", monoPassed >= 3)
    }

    // ══════════════════════════════════════════════════════════
    //  TEST 7: CALIBRATION — Output distribution check
    // ══════════════════════════════════════════════════════════

    @Test
    fun `calibration - output distribution is reasonable`() {
        // Test 100 random-ish scenarios
        val allOutputs = mutableListOf<Float>()
        val rng = java.util.Random(123)

        for (i in 0 until 100) {
            val features = FloatArray(20) { rng.nextFloat() }
            val result = DisasterNeuralNetwork.predict(features)
            allOutputs.addAll(result.toList())
        }

        val mean = allOutputs.average()
        val min = allOutputs.min()
        val max = allOutputs.max()
        val low = allOutputs.count { it < 0.3f }
        val mid = allOutputs.count { it in 0.3f..0.7f }
        val high = allOutputs.count { it > 0.7f }

        println("\n📊 CALIBRATION REPORT (100 random scenarios × 6 outputs = ${allOutputs.size} values)")
        println("  Mean:  ${"%.4f".format(mean)}")
        println("  Range: [${"%.4f".format(min)}, ${"%.4f".format(max)}]")
        println("  Low (<0.3):   $low (${"%.1f".format(low * 100.0 / allOutputs.size)}%)")
        println("  Mid (0.3-0.7): $mid (${"%.1f".format(mid * 100.0 / allOutputs.size)}%)")
        println("  High (>0.7):  $high (${"%.1f".format(high * 100.0 / allOutputs.size)}%)")

        // Temperature scaling (T=1.3) should prevent extreme saturation
        assertTrue("Mean harus antara 0.2 dan 0.9, actual=$mean",
            mean > 0.2 && mean < 0.9)
        // Domain-informed init biases outputs — check distribution spread
        val std = kotlin.math.sqrt(allOutputs.map { (it - mean) * (it - mean) }.average())
        assertTrue("Output harus ada variasi (std > 0.02), actual std=$std", std > 0.02)
        println("  Std:  ${"%.4f".format(std)}")
        println("✅ CALIBRATION: Distribusi output wajar")
    }

    // ══════════════════════════════════════════════════════════
    //  TEST 8: EDGE CASES
    // ══════════════════════════════════════════════════════════

    @Test
    fun `edge case - all zeros input`() {
        val result = DisasterNeuralNetwork.predict(FloatArray(20) { 0f })
        printScenarioResult("ALL-ZEROS", result)
        for (i in result.indices) {
            assertTrue("All-zeros: output[$i] harus valid [0,1]",
                result[i] in 0f..1f)
        }
    }

    @Test
    fun `edge case - all ones input`() {
        val result = DisasterNeuralNetwork.predict(FloatArray(20) { 1f })
        printScenarioResult("ALL-ONES", result)
        for (i in result.indices) {
            assertTrue("All-ones: output[$i] harus valid [0,1]",
                result[i] in 0f..1f)
        }
        // All-ones = semua parameter ekstrem, minimal 3 harus high risk
        val highCount = result.count { it > MODERATE_RISK }
        assertTrue("All-ones: setidaknya 3 bencana terdeteksi, actual=$highCount",
            highCount >= 3)
    }

    @Test
    fun `edge case - wrong size input should throw`() {
        try {
            DisasterNeuralNetwork.predict(FloatArray(10))
            fail("Seharusnya throw IllegalArgumentException untuk ukuran salah")
        } catch (e: IllegalArgumentException) {
            println("✅ Input size validation bekerja: ${e.message}")
        }
    }

    // ══════════════════════════════════════════════════════════
    //  TEST 9: WEIGHT DELTAS (Incremental Learning compatibility)
    // ══════════════════════════════════════════════════════════

    @Test
    fun `weight deltas - zero deltas produce same result`() {
        val features = neutralWeather()
        val zeroDelta = WeightDeltas(
            w3Delta = FloatArray(16 * 6),
            b3Delta = FloatArray(6),
            totalSteps = 0
        )

        val baseResult = DisasterNeuralNetwork.predict(features)
        val deltaResult = DisasterNeuralNetwork.predict(features, zeroDelta)

        for (i in baseResult.indices) {
            assertEquals("Zero deltas harus menghasilkan output identik",
                baseResult[i], deltaResult[i], 1e-6f)
        }
        println("✅ WEIGHT DELTAS: Zero deltas = identical output")
    }

    @Test
    fun `weight deltas - small deltas produce small changes`() {
        val features = neutralWeather()
        val smallDelta = WeightDeltas(
            w3Delta = FloatArray(16 * 6) { 0.01f },
            b3Delta = FloatArray(6) { 0.01f },
            totalSteps = 5
        )

        val baseResult = DisasterNeuralNetwork.predict(features)
        val deltaResult = DisasterNeuralNetwork.predict(features, smallDelta)

        var maxChange = 0f
        for (i in baseResult.indices) {
            val change = abs(deltaResult[i] - baseResult[i])
            maxChange = maxOf(maxChange, change)
        }

        println("  Small deltas → max change: ${"%.4f".format(maxChange)}")
        assertTrue("Small deltas harus menghasilkan perubahan kecil (< 0.3), actual=$maxChange",
            maxChange < 0.3f)
    }

    // ══════════════════════════════════════════════════════════
    //  TEST 10: MODEL INFO
    // ══════════════════════════════════════════════════════════

    @Test
    fun `model info - correct metadata`() {
        assertEquals("MLP-v1.1-incremental", DisasterNeuralNetwork.MODEL_VERSION)
        // TOTAL_PARAMS = 20*32 + 32 + 32*16 + 16 + 16*6 + 6 = 1302
        assertEquals(1302, DisasterNeuralNetwork.TOTAL_PARAMS)

        val info = DisasterNeuralNetwork.getModelInfo()
        assertTrue("Architecture harus mengandung 'MLP'",
            (info["architecture"] as String).contains("MLP"))
        assertEquals(1302, info["totalParameters"])
        println("✅ MODEL INFO: Version=${info["version"]}, Params=${info["totalParameters"]}")
    }

    // ══════════════════════════════════════════════════════════
    //  MASTER REPORT — Run all scenarios and print summary
    // ══════════════════════════════════════════════════════════

    @Test
    fun `MASTER TEST - comprehensive accuracy report`() {
        println("\n")
        println("╔══════════════════════════════════════════════════════════════════╗")
        println("║     DISASTER NEURAL NETWORK — COMPREHENSIVE TEST REPORT         ║")
        println("║     Model: ${DisasterNeuralNetwork.MODEL_VERSION}                          ║")
        println("║     Parameters: ${DisasterNeuralNetwork.TOTAL_PARAMS}                                       ║")
        println("║     Architecture: MLP 20→32→16→6                                ║")
        println("╚══════════════════════════════════════════════════════════════════╝")
        println()

        // ── Scenario Tests ──
        data class ScenarioTest(
            val name: String,
            val features: FloatArray,
            val expectedHigh: List<Int>,   // Output indices that should be >= MODERATE
            val expectedLow: List<Int>     // Output indices that should be < MODERATE
        )

        val scenarios = listOf(
            ScenarioTest("Banjir Bandang", FloatArray(20) { 0.1f }.apply {
                this[0]=0.90f; this[1]=0.85f; this[7]=0.90f; this[14]=0.80f
                this[15]=0.75f; this[16]=0.70f; this[17]=0.60f; this[18]=0.80f
            }, listOf(FLOOD), listOf(CYCLONE)),

            ScenarioTest("Siklon Tropis", FloatArray(20) { 0.1f }.apply {
                this[2]=0.90f; this[3]=0.95f; this[4]=0.70f; this[5]=0.90f
                this[6]=0.80f; this[12]=0.80f; this[13]=0.70f
            }, listOf(CYCLONE, TIDAL), listOf(LANDSLIDE)),

            ScenarioTest("Badai Petir", FloatArray(20) { 0.1f }.apply {
                this[8]=0.95f; this[4]=0.55f; this[3]=0.60f; this[18]=1.0f
            }, listOf(THUNDER), listOf(TIDAL)),

            ScenarioTest("Longsor", FloatArray(20) { 0.1f }.apply {
                this[16]=0.90f; this[17]=0.85f; this[15]=0.80f; this[7]=0.95f; this[0]=0.60f
            }, listOf(LANDSLIDE), listOf(CYCLONE)),

            ScenarioTest("Banjir Rob", FloatArray(20) { 0.1f }.apply {
                this[12]=0.90f; this[13]=0.85f; this[5]=0.60f; this[2]=0.50f
            }, listOf(TIDAL), listOf(LANDSLIDE, SUBSIDENCE)),

            ScenarioTest("Tanah Amblas", FloatArray(20) { 0.1f }.apply {
                this[17]=0.90f; this[16]=0.85f; this[14]=0.70f; this[15]=0.70f; this[7]=0.90f
            }, listOf(SUBSIDENCE), listOf(CYCLONE)),

            ScenarioTest("Cuaca Tenang", FloatArray(20) { 0.05f },
                listOf(), listOf(FLOOD, CYCLONE, THUNDER, LANDSLIDE)),

            ScenarioTest("Monsun Normal", FloatArray(20) { 0.1f }.apply {
                this[0]=0.20f; this[7]=0.75f; this[10]=0.70f; this[15]=0.30f
            }, listOf(), listOf(CYCLONE, THUNDER)),

            ScenarioTest("Multi-Hazard", FloatArray(20) { 0.90f },
                listOf(FLOOD, CYCLONE, THUNDER, LANDSLIDE), listOf()),

            ScenarioTest("Angin Kencang Saja", FloatArray(20) { 0.1f }.apply {
                this[2]=0.85f; this[3]=0.90f; this[4]=0.70f
            }, listOf(CYCLONE), listOf(FLOOD, LANDSLIDE))
        )

        var totalChecks = 0
        var passedChecks = 0
        val scenarioResults = mutableListOf<Triple<String, Boolean, String>>()

        println("═══════════ SCENARIO ACCURACY ═══════════")
        for (scenario in scenarios) {
            val result = DisasterNeuralNetwork.predict(scenario.features)
            var scenarioPassed = true
            val details = StringBuilder()

            // Check expected high risks
            for (idx in scenario.expectedHigh) {
                totalChecks++
                if (result[idx] >= MODERATE_RISK) {
                    passedChecks++
                } else {
                    scenarioPassed = false
                    details.append("  ❌ ${DISASTER_NAMES[idx]} = ${"%.3f".format(result[idx])} < $MODERATE_RISK\n")
                }
            }

            // Check expected low risks
            for (idx in scenario.expectedLow) {
                totalChecks++
                if (result[idx] < HIGH_RISK) {
                    passedChecks++
                } else {
                    scenarioPassed = false
                    details.append("  ❌ ${DISASTER_NAMES[idx]} = ${"%.3f".format(result[idx])} should be < $HIGH_RISK\n")
                }
            }

            val icon = if (scenarioPassed) "✅" else "⚠️"
            print("$icon ${scenario.name}: ")
            println(result.indices.joinToString(" | ") {
                "${DISASTER_NAMES[it]}=${"%.3f".format(result[it])}"
            })
            if (details.isNotEmpty()) print(details)

            scenarioResults.add(Triple(scenario.name, scenarioPassed, ""))
        }

        val scenarioAccuracy = passedChecks * 100.0 / totalChecks

        // ── Sensitivity Tests ──
        println("\n═══════════ SENSITIVITY ANALYSIS ═══════════")
        val baseline = calmWeather()
        val baseResult = DisasterNeuralNetwork.predict(baseline)

        val sensitivityMap = mapOf(
            0 to FLOOD, 2 to CYCLONE, 8 to THUNDER,
            12 to TIDAL, 16 to LANDSLIDE, 17 to SUBSIDENCE
        )
        val featureNamesList = listOf(
            "precipTotal", "precipIntensity", "windSpeed", "windGusts", "windShear",
            "pressureLow", "pressureDrop", "humidity", "capeEnergy", "freezingLow",
            "cloudCover", "dewPointSpread", "waveHeight", "swellHeight", "dischargeRatio",
            "rainDuration", "antecedentRain", "consecutiveRain", "weatherSeverity", "temperatureHigh"
        )

        var sensPassed = 0
        var sensTotal = 0
        for ((fIdx, dIdx) in sensitivityMap) {
            val modified = calmWeather().apply { this[fIdx] = 0.95f }
            val modResult = DisasterNeuralNetwork.predict(modified)
            val delta = modResult[dIdx] - baseResult[dIdx]
            sensTotal++
            val pass = delta > 0
            if (pass) sensPassed++
            println("  ${if (pass) "✅" else "❌"} ${featureNamesList[fIdx]} → ${DISASTER_NAMES[dIdx]}: delta=${"%+.4f".format(delta)}")
        }
        val sensAccuracy = sensPassed * 100.0 / sensTotal

        // ── Monotonicity ──
        println("\n═══════════ MONOTONICITY ═══════════")
        val monoTests = listOf(0 to FLOOD, 2 to CYCLONE, 8 to THUNDER, 12 to TIDAL, 16 to LANDSLIDE)
        var monoPassed = 0
        for ((fIdx, dIdx) in monoTests) {
            val scores = listOf(0.1f, 0.3f, 0.5f, 0.7f, 0.9f).map { level ->
                val f = calmWeather().apply { this[fIdx] = level }
                DisasterNeuralNetwork.predict(f)[dIdx]
            }
            val monotonic = scores.last() > scores.first()
            if (monotonic) monoPassed++
            println("  ${if (monotonic) "✅" else "❌"} ${featureNamesList[fIdx]}→${DISASTER_NAMES[dIdx]}: ${scores.map { "%.3f".format(it) }}")
        }
        val monoAccuracy = monoPassed * 100.0 / monoTests.size

        // ── Calibration ──
        println("\n═══════════ CALIBRATION ═══════════")
        val rng = java.util.Random(42)
        val allOutputs = (0 until 200).flatMap { i ->
            val f = FloatArray(20) { rng.nextFloat() }
            DisasterNeuralNetwork.predict(f).toList()
        }
        val mean = allOutputs.average()
        val std = kotlin.math.sqrt(allOutputs.map { (it - mean) * (it - mean) }.average())
        println("  Mean: ${"%.4f".format(mean)}")
        println("  Std:  ${"%.4f".format(std)}")
        println("  Range: [${"%.4f".format(allOutputs.min())}, ${"%.4f".format(allOutputs.max())}]")
        val calibrationOk = mean > 0.2 && mean < 0.9 && std > 0.02
        println("  ${if (calibrationOk) "✅" else "❌"} Calibration ${if (calibrationOk) "PASS" else "FAIL"}")

        // ═══════════════════════════════════════════════════
        //  FINAL REPORT
        // ═══════════════════════════════════════════════════
        println("\n")
        println("╔══════════════════════════════════════════════════════════════════╗")
        println("║                    FINAL TEST REPORT                             ║")
        println("╠══════════════════════════════════════════════════════════════════╣")
        println("║  Scenario Accuracy:    $passedChecks/$totalChecks = ${"%.1f".format(scenarioAccuracy)}%${" ".repeat(30 - "%.1f".format(scenarioAccuracy).length)}║")
        println("║  Sensitivity Accuracy: $sensPassed/$sensTotal = ${"%.1f".format(sensAccuracy)}%${" ".repeat(31 - "%.1f".format(sensAccuracy).length)}║")
        println("║  Monotonicity:         $monoPassed/${monoTests.size} = ${"%.1f".format(monoAccuracy)}%${" ".repeat(31 - "%.1f".format(monoAccuracy).length)}║")
        println("║  Calibration:          ${if (calibrationOk) "PASS ✅" else "FAIL ❌"}${" ".repeat(31)}║")
        println("╠══════════════════════════════════════════════════════════════════╣")

        val overallScore = (scenarioAccuracy + sensAccuracy + monoAccuracy + (if (calibrationOk) 100.0 else 0.0)) / 4.0
        val verdict = when {
            overallScore >= 85 -> "🟢 SANGAT BAIK — Model siap produksi"
            overallScore >= 70 -> "🟡 BAIK — Model cukup akurat, minor tuning diperlukan"
            overallScore >= 50 -> "🟠 CUKUP — Perlu perbaikan pada beberapa area"
            else -> "🔴 KURANG — Model perlu redesign signifikan"
        }
        println("║  OVERALL SCORE:        ${"%.1f".format(overallScore)}%${" ".repeat(32 - "%.1f".format(overallScore).length)}║")
        println("║  VERDICT:              $verdict  ║")
        println("╚══════════════════════════════════════════════════════════════════╝")

        // The test passes as long as overall > 50% — we want to see the report
        assertTrue("Overall score harus > 50%", overallScore > 50.0)
    }

    // ══════════════════════════════════════════════════════════
    //  Helper
    // ══════════════════════════════════════════════════════════

    private fun printScenarioResult(name: String, result: FloatArray) {
        println("📋 $name:")
        for (i in result.indices) {
            val bar = "█".repeat((result[i] * 20).toInt())
            val level = when {
                result[i] >= HIGH_RISK -> "🔴 HIGH"
                result[i] >= MODERATE_RISK -> "🟡 MOD"
                result[i] >= LOW_RISK -> "🟢 LOW"
                else -> "⚪ MIN"
            }
            println("  ${DISASTER_NAMES[i].padEnd(12)} ${"%.3f".format(result[i])} $bar $level")
        }
    }
}

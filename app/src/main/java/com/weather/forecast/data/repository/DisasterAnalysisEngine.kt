package com.weather.forecast.data.repository

import com.weather.forecast.data.ai.DisasterNeuralNetwork
import com.weather.forecast.data.ai.WeatherFeatureExtractor
import com.weather.forecast.data.ai.WeatherFeatures
import com.weather.forecast.data.ai.WeightDeltas
import com.weather.forecast.data.model.*

/**
 * Disaster Analysis Engine — Hybrid AI + Rule-Based
 *
 * Mesin analisis bencana yang menggabungkan:
 * 1. Neural Network (MLP 20→32→16→6) untuk deteksi pola non-linear
 * 2. Rule-based fuzzy-logic scoring untuk domain constraints
 * 3. Ensemble fusion dengan adaptive weighting
 *
 * Referensi:
 * - Gorishniy et al. (NeurIPS 2021) — MLP untuk data tabular
 * - Guo et al. (ICML 2017) — Temperature scaling calibration
 * - WMO Multi-Hazard Guidelines (2023)
 *
 * Data input:
 * - Cuaca: CAPE, angin (kecepatan/gust/arah), curah hujan, tekanan udara,
 *   kelembaban, dew point, kode cuaca WMO, freezing level
 * - Laut: tinggi gelombang, swell, periode gelombang
 * - Sungai: debit sungai, rasio vs rata-rata
 *
 * Output: skor risiko 0–1 per jenis bencana + narasi deskriptif
 */
object DisasterAnalysisEngine {

    // ══════════════════════════════════════════════════
    //  PUBLIC — Analisis hari ini
    // ══════════════════════════════════════════════════

    /**
     * Analisis seluruh potensi bencana berdasarkan data yang tersedia.
     *
     * @param weather Data cuaca (hourly + daily + current)
     * @param marine Data laut (nullable — hanya tersedia di daerah pesisir)
     * @param flood Data sungai (nullable)
     */
    fun analyzeToday(
        weather: WeatherData?,
        marine: WaterQualityData?,
        flood: WaterQualityData?,
        weightDeltas: WeightDeltas? = null
    ): List<DisasterPrediction> {
        val predictions = mutableListOf<DisasterPrediction>()

        // Extract relevant data
        val hourly = weather?.hourly?.take(24) ?: emptyList() // 24 jam ke depan
        val current = weather?.current
        val dailyToday = weather?.daily?.firstOrNull()
        val marineData = marine?.marine
        val floodData = flood?.flood // sama dengan marine param, tapi ambil flood

        predictions.add(analyzeFlood(hourly, dailyToday, floodData))
        predictions.add(analyzeTidalFlood(hourly, marineData, current))
        predictions.add(analyzeCyclone(hourly, current, dailyToday))
        predictions.add(analyzeThunderstorm(hourly, current, dailyToday))
        predictions.add(analyzeLandslide(hourly, dailyToday, weather?.daily))
        predictions.add(analyzeGroundSubsidence(hourly, dailyToday, weather?.daily, floodData))

        // ═══ Phase 2: Neural Network + Ensemble Fusion ═══
        val features = WeatherFeatureExtractor.extractForToday(weather, marine ?: flood)
        val nnScores = DisasterNeuralNetwork.predict(features.features, weightDeltas)
        return ensembleFuse(predictions, nnScores, features).sortedByDescending { it.riskScore }
    }

    /**
     * Analisis per hari untuk prakiraan 7 hari
     */
    fun analyzeDaily(
        dayIndex: Int,
        daily: DailyWeatherData,
        hourlyForDay: List<HourlyWeatherData>,
        marineDaily: DailyMarineData?,
        floodDaily: DailyFloodData?,
        allDaily: List<DailyWeatherData>,
        weightDeltas: WeightDeltas? = null
    ): List<DisasterPrediction> {
        val predictions = mutableListOf<DisasterPrediction>()

        predictions.add(analyzeDailyFlood(daily, hourlyForDay, floodDaily))
        predictions.add(analyzeDailyTidalFlood(daily, hourlyForDay, marineDaily))
        predictions.add(analyzeDailyCyclone(daily, hourlyForDay))
        predictions.add(analyzeDailyThunderstorm(daily, hourlyForDay))
        predictions.add(analyzeDailyLandslide(daily, hourlyForDay, allDaily, dayIndex))
        predictions.add(analyzeDailyGroundSubsidence(daily, hourlyForDay, allDaily, dayIndex, floodDaily))

        // ═══ Neural Network + Ensemble Fusion ═══
        val features = WeatherFeatureExtractor.extractForDay(
            dayIndex, daily, hourlyForDay, marineDaily, floodDaily, allDaily
        )
        val nnScores = DisasterNeuralNetwork.predict(features.features, weightDeltas)
        return ensembleFuse(predictions, nnScores, features).sortedByDescending { it.riskScore }
    }

    /**
     * Buat ringkasan narasi AI
     */
    fun generateSummary(predictions: List<DisasterPrediction>): String {
        val highRisks = predictions.filter { it.riskLevel >= RiskLevel.HIGH }
        val moderateRisks = predictions.filter { it.riskLevel == RiskLevel.MODERATE }

        return buildString {
            if (highRisks.isEmpty() && moderateRisks.isEmpty()) {
                append("✅ Kondisi aman — tidak terdeteksi potensi bencana signifikan dalam 24 jam ke depan. ")
                append("Tetap pantau pembaruan cuaca secara berkala.")
            } else {
                if (highRisks.isNotEmpty()) {
                    append("⚠️ PERINGATAN: Terdeteksi potensi ")
                    append(highRisks.joinToString(", ") { it.type.labelId })
                    append(" dengan risiko ${highRisks.first().riskLevel.labelId}. ")
                }
                if (moderateRisks.isNotEmpty()) {
                    append("Perhatikan juga potensi ")
                    append(moderateRisks.joinToString(", ") { it.type.labelId })
                    append(" (risiko sedang). ")
                }
                append("Harap waspada dan ikuti arahan pihak berwenang.")
            }
        }
    }

    // ══════════════════════════════════════════════════
    //  ENSEMBLE FUSION — Neural Network + Rule-Based
    // ══════════════════════════════════════════════════

    /**
     * Menggabungkan skor rule-based dengan output Neural Network.
     *
     * Formula: finalScore = α × nnScore + (1 − α) × ruleScore
     * dimana α = 0.6 × dataCompleteness
     *
     * NN mendapat bobot lebih besar (maks 60%) karena mampu menangkap
     * interaksi non-linear antar fitur meteorologi yang tidak bisa
     * ditangkap oleh aturan if-else.
     *
     * Saat data tidak lengkap (misalnya tidak ada data laut), bobot NN
     * dikurangi secara otomatis dan rule-based mengambil alih.
     */
    private fun ensembleFuse(
        rulePredictions: List<DisasterPrediction>,
        nnScores: FloatArray,
        features: WeatherFeatures
    ): List<DisasterPrediction> {
        val alpha = 0.6 * features.dataCompleteness  // Adaptive NN weight
        val beta = 1.0 - alpha                         // Rule-based weight

        return rulePredictions.map { pred ->
            val idx = DisasterType.entries.indexOf(pred.type)
            if (idx < 0 || idx >= nnScores.size) return@map pred

            val nnScore = nnScores[idx].toDouble().coerceIn(0.0, 1.0)
            val ruleScore = pred.riskScore
            val fusedScore = (alpha * nnScore + beta * ruleScore).coerceIn(0.0, 1.0)

            pred.copy(
                riskScore = fusedScore,
                riskLevel = scoreToRiskLevel(fusedScore),
                confidence = maxOf(pred.confidence, features.dataCompleteness),
                aiRawScore = nnScore,
                ensembleWeight = alpha
            )
        }
    }

    // ══════════════════════════════════════════════════
    //  BANJIR — Flood Analysis
    // ══════════════════════════════════════════════════

    private fun analyzeFlood(
        hourly: List<HourlyWeatherData>,
        daily: DailyWeatherData?,
        flood: FloodData?
    ): DisasterPrediction {
        val factors = mutableListOf<ContributingFactor>()
        var score = 0.0
        var dataPoints = 0

        // Faktor 1: Curah hujan kumulatif (bobot 0.35)
        val totalPrecip = daily?.precipitationSum ?: hourly.sumOf { it.precipitation }
        val precipScore = when {
            totalPrecip > 100 -> 1.0   // >100mm/hari = sangat bahaya
            totalPrecip > 50 -> 0.8
            totalPrecip > 20 -> 0.5
            totalPrecip > 10 -> 0.3
            totalPrecip > 5 -> 0.15
            else -> 0.0
        }
        score += precipScore * 0.35
        dataPoints++
        factors.add(ContributingFactor(
            name = "Curah Hujan",
            value = "%.1f mm".format(totalPrecip),
            contribution = precipScore,
            isElevating = precipScore > 0.3
        ))

        // Faktor 2: Intensitas hujan per jam tertinggi (bobot 0.25)
        val maxRainHourly = hourly.maxOfOrNull { it.rain + it.showers } ?: 0.0
        val intensityScore = when {
            maxRainHourly > 50 -> 1.0   // >50mm/jam = sangat lebat
            maxRainHourly > 20 -> 0.8
            maxRainHourly > 10 -> 0.5
            maxRainHourly > 5 -> 0.3
            else -> 0.0
        }
        score += intensityScore * 0.25
        dataPoints++
        factors.add(ContributingFactor(
            name = "Intensitas Hujan Maks",
            value = "%.1f mm/jam".format(maxRainHourly),
            contribution = intensityScore,
            isElevating = intensityScore > 0.3
        ))

        // Faktor 3: Debit sungai vs rata-rata (bobot 0.25)
        val floodDaily = flood?.dailyForecast?.firstOrNull()
        if (floodDaily != null && floodDaily.dischargeMean > 0) {
            val ratio = floodDaily.riverDischarge / floodDaily.dischargeMean
            val dischargeScore = when {
                ratio > 5.0 -> 1.0
                ratio > 3.0 -> 0.8
                ratio > 2.0 -> 0.5
                ratio > 1.5 -> 0.3
                else -> 0.0
            }
            score += dischargeScore * 0.25
            dataPoints++
            factors.add(ContributingFactor(
                name = "Debit Sungai",
                value = "%.1f m³/s (%.1fx rata-rata)".format(floodDaily.riverDischarge, ratio),
                contribution = dischargeScore,
                isElevating = dischargeScore > 0.3
            ))
        }

        // Faktor 4: Kelembaban tinggi & tekanan rendah (bobot 0.15)
        val avgHumidity = hourly.map { it.humidity }.average().takeIf { !it.isNaN() } ?: 50.0
        val avgPressure = hourly.map { it.pressure }.average().takeIf { !it.isNaN() } ?: 1013.0
        val atmosphereScore = when {
            avgHumidity > 90 && avgPressure < 1005 -> 0.8
            avgHumidity > 85 && avgPressure < 1008 -> 0.5
            avgHumidity > 80 -> 0.3
            else -> 0.0
        }
        score += atmosphereScore * 0.15
        dataPoints++
        factors.add(ContributingFactor(
            name = "Atmosfer",
            value = "Kelembaban ${avgHumidity.toInt()}%, Tekanan ${avgPressure.toInt()} hPa",
            contribution = atmosphereScore,
            isElevating = atmosphereScore > 0.3
        ))

        val confidence = (dataPoints.toDouble() / 4.0).coerceIn(0.5, 1.0)
        val riskLevel = scoreToRiskLevel(score)

        return DisasterPrediction(
            type = DisasterType.FLOOD,
            riskScore = score,
            riskLevel = riskLevel,
            confidence = confidence,
            factors = factors,
            description = buildFloodDescription(riskLevel, totalPrecip, maxRainHourly, floodDaily),
            recommendation = buildFloodRecommendation(riskLevel)
        )
    }

    // ══════════════════════════════════════════════════
    //  BANJIR ROB — Tidal Flood Analysis
    // ══════════════════════════════════════════════════

    private fun analyzeTidalFlood(
        hourly: List<HourlyWeatherData>,
        marine: MarineData?,
        current: CurrentWeatherData?
    ): DisasterPrediction {
        val factors = mutableListOf<ContributingFactor>()
        var score = 0.0
        var dataPoints = 0
        val hasMarineData = marine != null

        // Faktor 1: Tinggi gelombang (bobot 0.40)
        val waveHeight = marine?.current?.waveHeight ?: 0.0
        val waveDailyMax = marine?.dailyForecast?.firstOrNull()?.waveHeightMax ?: waveHeight
        val maxWave = maxOf(waveHeight, waveDailyMax)

        val waveScore = when {
            maxWave > 4.0 -> 1.0   // >4m = sangat berbahaya
            maxWave > 2.5 -> 0.8
            maxWave > 1.5 -> 0.5
            maxWave > 1.0 -> 0.3
            else -> 0.0
        }
        score += waveScore * 0.40
        dataPoints++
        factors.add(ContributingFactor(
            name = "Tinggi Gelombang",
            value = "%.1f m".format(maxWave),
            contribution = waveScore,
            isElevating = waveScore > 0.3
        ))

        // Faktor 2: Swell wave (bobot 0.20) — gelombang panjang dari laut lepas
        val swellHeight = marine?.current?.swellWaveHeight ?: 0.0
        val swellScore = when {
            swellHeight > 3.0 -> 1.0
            swellHeight > 2.0 -> 0.7
            swellHeight > 1.0 -> 0.4
            else -> 0.0
        }
        score += swellScore * 0.20
        if (swellHeight > 0) dataPoints++
        factors.add(ContributingFactor(
            name = "Swell (Gelombang Laut Lepas)",
            value = "%.1f m".format(swellHeight),
            contribution = swellScore,
            isElevating = swellScore > 0.3
        ))

        // Faktor 3: Angin kencang dari laut (bobot 0.20)
        val windSpeed = current?.windSpeed ?: hourly.firstOrNull()?.windSpeed ?: 0.0
        val windGusts = current?.windGusts ?: hourly.maxOfOrNull { it.windGusts } ?: 0.0
        val windScore = when {
            windGusts > 70 -> 1.0
            windGusts > 50 -> 0.7
            windGusts > 35 -> 0.4
            windGusts > 20 -> 0.2
            else -> 0.0
        }
        score += windScore * 0.20
        dataPoints++
        factors.add(ContributingFactor(
            name = "Angin & Gust",
            value = "%.0f / %.0f km/h".format(windSpeed, windGusts),
            contribution = windScore,
            isElevating = windScore > 0.3
        ))

        // Faktor 4: Tekanan rendah (bobot 0.20) — indikator cuaca buruk di pesisir
        val pressure = current?.pressure ?: hourly.firstOrNull()?.pressure ?: 1013.0
        val pressureScore = when {
            pressure < 995 -> 1.0
            pressure < 1000 -> 0.7
            pressure < 1005 -> 0.4
            pressure < 1010 -> 0.2
            else -> 0.0
        }
        score += pressureScore * 0.20
        dataPoints++
        factors.add(ContributingFactor(
            name = "Tekanan Udara",
            value = "${pressure.toInt()} hPa",
            contribution = pressureScore,
            isElevating = pressureScore > 0.3
        ))

        // Jika tidak ada data laut, kurangi confidence
        val confidence = if (hasMarineData) {
            (dataPoints.toDouble() / 4.0).coerceIn(0.5, 1.0)
        } else {
            0.3 // Low confidence tanpa data laut
        }

        val riskLevel = scoreToRiskLevel(score)

        return DisasterPrediction(
            type = DisasterType.TIDAL_FLOOD,
            riskScore = score,
            riskLevel = riskLevel,
            confidence = confidence,
            factors = factors,
            description = buildTidalFloodDescription(riskLevel, maxWave, swellHeight, hasMarineData),
            recommendation = buildTidalFloodRecommendation(riskLevel, hasMarineData)
        )
    }

    // ══════════════════════════════════════════════════
    //  SIKLON / ANGIN TOPAN — Cyclone Analysis
    // ══════════════════════════════════════════════════

    private fun analyzeCyclone(
        hourly: List<HourlyWeatherData>,
        current: CurrentWeatherData?,
        daily: DailyWeatherData?
    ): DisasterPrediction {
        val factors = mutableListOf<ContributingFactor>()
        var score = 0.0

        // Faktor 1: Tekanan sangat rendah (bobot 0.30)
        val pressure = current?.pressure ?: hourly.map { it.pressure }.minOrNull() ?: 1013.0
        val minPressure = hourly.map { it.pressure }.filter { it > 0 }.minOrNull() ?: pressure
        val effectivePressure = minOf(pressure, minPressure)
        val pressureScore = when {
            effectivePressure < 980 -> 1.0   // Siklon kuat
            effectivePressure < 990 -> 0.8
            effectivePressure < 1000 -> 0.5
            effectivePressure < 1005 -> 0.2
            else -> 0.0
        }
        score += pressureScore * 0.30
        factors.add(ContributingFactor(
            name = "Tekanan Minimum",
            value = "${effectivePressure.toInt()} hPa",
            contribution = pressureScore,
            isElevating = pressureScore > 0.3
        ))

        // Faktor 2: Angin sustained + gusts (bobot 0.35)
        val maxWindSpeed = maxOf(
            current?.windSpeed ?: 0.0,
            hourly.maxOfOrNull { it.windSpeed } ?: 0.0
        )
        val maxGusts = maxOf(
            current?.windGusts ?: 0.0,
            daily?.windGustsMax ?: 0.0,
            hourly.maxOfOrNull { it.windGusts } ?: 0.0
        )
        // Skala Beaufort/Saffir-Simpson sederhana
        val windCycloneScore = when {
            maxWindSpeed > 119 -> 1.0   // Badai tropis (>119 km/h)
            maxWindSpeed > 89 -> 0.8    // Depresi tropis kuat
            maxWindSpeed > 63 -> 0.6    // Depresi tropis
            maxGusts > 90 -> 0.5
            maxGusts > 70 -> 0.3
            maxGusts > 50 -> 0.15
            else -> 0.0
        }
        score += windCycloneScore * 0.35
        factors.add(ContributingFactor(
            name = "Kecepatan Angin",
            value = "%.0f km/h (gust %.0f)".format(maxWindSpeed, maxGusts),
            contribution = windCycloneScore,
            isElevating = windCycloneScore > 0.3
        ))

        // Faktor 3: CAPE tinggi (bobot 0.15) — energi konvektif
        val maxCape = maxOf(
            current?.cape ?: 0.0,
            hourly.maxOfOrNull { it.cape } ?: 0.0
        )
        val capeScore = when {
            maxCape > 3500 -> 1.0
            maxCape > 2500 -> 0.7
            maxCape > 1500 -> 0.4
            maxCape > 1000 -> 0.2
            else -> 0.0
        }
        score += capeScore * 0.15
        factors.add(ContributingFactor(
            name = "CAPE",
            value = "${maxCape.toInt()} J/kg",
            contribution = capeScore,
            isElevating = capeScore > 0.3
        ))

        // Faktor 4: Curah hujan masif (bobot 0.20)
        val totalPrecip = daily?.precipitationSum ?: hourly.sumOf { it.precipitation }
        val precipCycloneScore = when {
            totalPrecip > 100 -> 1.0
            totalPrecip > 50 -> 0.7
            totalPrecip > 30 -> 0.4
            else -> 0.0
        }
        score += precipCycloneScore * 0.20
        factors.add(ContributingFactor(
            name = "Curah Hujan",
            value = "%.1f mm".format(totalPrecip),
            contribution = precipCycloneScore,
            isElevating = precipCycloneScore > 0.3
        ))

        val riskLevel = scoreToRiskLevel(score)
        return DisasterPrediction(
            type = DisasterType.CYCLONE,
            riskScore = score,
            riskLevel = riskLevel,
            confidence = 0.8,
            factors = factors,
            description = buildCycloneDescription(riskLevel, effectivePressure, maxWindSpeed, maxGusts),
            recommendation = buildCycloneRecommendation(riskLevel)
        )
    }

    // ══════════════════════════════════════════════════
    //  BADAI PETIR HEBAT — Thunderstorm Analysis
    // ══════════════════════════════════════════════════

    @Suppress("UNUSED_PARAMETER")
    private fun analyzeThunderstorm(
        hourly: List<HourlyWeatherData>,
        current: CurrentWeatherData?,
        daily: DailyWeatherData?
    ): DisasterPrediction {
        val factors = mutableListOf<ContributingFactor>()
        var score = 0.0

        // Faktor 1: CAPE (bobot 0.35)
        val maxCape = maxOf(
            current?.cape ?: 0.0,
            hourly.maxOfOrNull { it.cape } ?: 0.0
        )
        val capeScore = when {
            maxCape > 3500 -> 1.0
            maxCape > 2500 -> 0.8
            maxCape > 1500 -> 0.5
            maxCape > 500 -> 0.2
            else -> 0.0
        }
        score += capeScore * 0.35
        factors.add(ContributingFactor(
            name = "CAPE",
            value = "${maxCape.toInt()} J/kg",
            contribution = capeScore,
            isElevating = capeScore > 0.3
        ))

        // Faktor 2: Kode cuaca WMO (bobot 0.25)
        val hasThunderstormCode = hourly.any { it.weatherCode in listOf(95, 96, 99) } ||
            (current?.weatherCode in listOf(95, 96, 99))
        val hasHailCode = hourly.any { it.weatherCode in listOf(96, 99) }
        val wmoScore = when {
            hasHailCode -> 1.0
            hasThunderstormCode -> 0.7
            hourly.any { it.weatherCode in listOf(61, 63, 65, 80, 81, 82) } -> 0.3
            else -> 0.0
        }
        score += wmoScore * 0.25
        factors.add(ContributingFactor(
            name = "Indikator WMO",
            value = if (hasThunderstormCode) "Badai petir terdeteksi" else "Tidak ada indikasi",
            contribution = wmoScore,
            isElevating = wmoScore > 0.3
        ))

        // Faktor 3: Wind shear (bobot 0.20)
        val maxGusts = maxOf(
            current?.windGusts ?: 0.0,
            hourly.maxOfOrNull { it.windGusts } ?: 0.0
        )
        val avgWind = hourly.map { it.windSpeed }.average().takeIf { !it.isNaN() } ?: 0.0
        val windShear = maxGusts - avgWind
        val shearScore = when {
            windShear > 40 -> 1.0
            windShear > 30 -> 0.7
            windShear > 20 -> 0.4
            windShear > 10 -> 0.2
            else -> 0.0
        }
        score += shearScore * 0.20
        factors.add(ContributingFactor(
            name = "Wind Shear",
            value = "%.0f km/h".format(windShear),
            contribution = shearScore,
            isElevating = shearScore > 0.3
        ))

        // Faktor 4: Freezing level rendah + CAPE → hujan es (bobot 0.20)
        val minFreezing = hourly.filter { it.freezingLevelHeight > 0 }
            .minOfOrNull { it.freezingLevelHeight } ?: 5000.0
        val hailScore = when {
            maxCape > 2000 && minFreezing < 2500 -> 1.0
            maxCape > 1500 && minFreezing < 3000 -> 0.6
            maxCape > 1000 && minFreezing < 3500 -> 0.3
            else -> 0.0
        }
        score += hailScore * 0.20
        factors.add(ContributingFactor(
            name = "Potensi Hujan Es",
            value = "Freezing ${(minFreezing / 1000).toInt()} km, CAPE ${maxCape.toInt()}",
            contribution = hailScore,
            isElevating = hailScore > 0.3
        ))

        val riskLevel = scoreToRiskLevel(score)
        return DisasterPrediction(
            type = DisasterType.THUNDERSTORM,
            riskScore = score,
            riskLevel = riskLevel,
            confidence = 0.85,
            factors = factors,
            description = buildThunderstormDescription(riskLevel, maxCape, maxGusts, hasThunderstormCode),
            recommendation = buildThunderstormRecommendation(riskLevel)
        )
    }

    // ══════════════════════════════════════════════════
    //  TANAH LONGSOR — Landslide Analysis
    // ══════════════════════════════════════════════════

    private fun analyzeLandslide(
        hourly: List<HourlyWeatherData>,
        daily: DailyWeatherData?,
        allDaily: List<DailyWeatherData>?
    ): DisasterPrediction {
        val factors = mutableListOf<ContributingFactor>()
        var score = 0.0

        // Faktor 1: Curah hujan kumulatif hari ini (bobot 0.30)
        val todayPrecip = daily?.precipitationSum ?: hourly.sumOf { it.precipitation }
        val precipScore = when {
            todayPrecip > 100 -> 1.0
            todayPrecip > 50 -> 0.7
            todayPrecip > 20 -> 0.4
            todayPrecip > 10 -> 0.2
            else -> 0.0
        }
        score += precipScore * 0.30
        factors.add(ContributingFactor(
            name = "Curah Hujan Hari Ini",
            value = "%.1f mm".format(todayPrecip),
            contribution = precipScore,
            isElevating = precipScore > 0.3
        ))

        // Faktor 2: Durasi hujan (jam hujan > 0) (bobot 0.25)
        val rainHours = hourly.count { it.precipitation > 0.5 }
        val durationScore = when {
            rainHours > 18 -> 1.0   // Hampir seharian hujan
            rainHours > 12 -> 0.7
            rainHours > 8 -> 0.4
            rainHours > 4 -> 0.2
            else -> 0.0
        }
        score += durationScore * 0.25
        factors.add(ContributingFactor(
            name = "Durasi Hujan",
            value = "$rainHours jam",
            contribution = durationScore,
            isElevating = durationScore > 0.3
        ))

        // Faktor 3: Hujan kumulatif 3 hari sebelumnya → tanah jenuh (bobot 0.30)
        // Simulasi antecedent rainfall dari data daily
        val recentDays = allDaily?.take(3) ?: emptyList()
        val antecedentRain = recentDays.sumOf { it.precipitationSum }
        val antecedentScore = when {
            antecedentRain > 150 -> 1.0  // Tanah sangat jenuh
            antecedentRain > 100 -> 0.8
            antecedentRain > 50 -> 0.5
            antecedentRain > 20 -> 0.2
            else -> 0.0
        }
        score += antecedentScore * 0.30
        factors.add(ContributingFactor(
            name = "Akumulasi Hujan 3 Hari",
            value = "%.0f mm".format(antecedentRain),
            contribution = antecedentScore,
            isElevating = antecedentScore > 0.3
        ))

        // Faktor 4: Kelembaban tinggi (bobot 0.15) — tanah basah
        val avgHumidity = hourly.map { it.humidity }.average().takeIf { !it.isNaN() } ?: 50.0
        val humidityScore = when {
            avgHumidity > 95 -> 0.8
            avgHumidity > 90 -> 0.5
            avgHumidity > 85 -> 0.3
            else -> 0.0
        }
        score += humidityScore * 0.15
        factors.add(ContributingFactor(
            name = "Kelembaban Rata-rata",
            value = "${avgHumidity.toInt()}%",
            contribution = humidityScore,
            isElevating = humidityScore > 0.3
        ))

        val riskLevel = scoreToRiskLevel(score)
        return DisasterPrediction(
            type = DisasterType.LANDSLIDE,
            riskScore = score,
            riskLevel = riskLevel,
            confidence = 0.7, // Tanpa data topografi, confidence rendah
            factors = factors,
            description = buildLandslideDescription(riskLevel, todayPrecip, antecedentRain, rainHours),
            recommendation = buildLandslideRecommendation(riskLevel)
        )
    }

    // ══════════════════════════════════════════════════
    //  TANAH AMBLAS — Ground Subsidence Analysis
    // ══════════════════════════════════════════════════

    @Suppress("UNUSED_PARAMETER")
    private fun analyzeGroundSubsidence(
        hourly: List<HourlyWeatherData>,
        daily: DailyWeatherData?,
        allDaily: List<DailyWeatherData>?,
        flood: FloodData?
    ): DisasterPrediction {
        val factors = mutableListOf<ContributingFactor>()
        var score = 0.0

        // Faktor 1: Akumulasi hujan berkepanjangan (bobot 0.35)
        val weeklyPrecip = allDaily?.sumOf { it.precipitationSum } ?: 0.0
        val prolongedScore = when {
            weeklyPrecip > 300 -> 1.0
            weeklyPrecip > 200 -> 0.7
            weeklyPrecip > 100 -> 0.4
            weeklyPrecip > 50 -> 0.2
            else -> 0.0
        }
        score += prolongedScore * 0.35
        factors.add(ContributingFactor(
            name = "Akumulasi Hujan 7 Hari",
            value = "%.0f mm".format(weeklyPrecip),
            contribution = prolongedScore,
            isElevating = prolongedScore > 0.3
        ))

        // Faktor 2: Debit sungai tinggi berkepanjangan (bobot 0.25)
        val avgDischargeRatio = flood?.dailyForecast?.let { days ->
            val ratios = days.mapNotNull { d ->
                if (d.dischargeMean > 0) d.riverDischarge / d.dischargeMean else null
            }
            ratios.average().takeIf { !it.isNaN() }
        } ?: 1.0
        val dischargeScore = when {
            avgDischargeRatio > 4.0 -> 1.0
            avgDischargeRatio > 2.5 -> 0.6
            avgDischargeRatio > 1.5 -> 0.3
            else -> 0.0
        }
        score += dischargeScore * 0.25
        factors.add(ContributingFactor(
            name = "Rasio Debit Sungai",
            value = "%.1fx rata-rata".format(avgDischargeRatio),
            contribution = dischargeScore,
            isElevating = dischargeScore > 0.3
        ))

        // Faktor 3: Durasi genangan (jam hujan terus-menerus) (bobot 0.25)
        val consecutiveRainDays = allDaily?.takeWhile { it.precipitationSum > 5 }?.size ?: 0
        val durationDayScore = when {
            consecutiveRainDays >= 5 -> 1.0
            consecutiveRainDays >= 3 -> 0.6
            consecutiveRainDays >= 2 -> 0.3
            else -> 0.0
        }
        score += durationDayScore * 0.25
        factors.add(ContributingFactor(
            name = "Hari Hujan Berturut",
            value = "$consecutiveRainDays hari",
            contribution = durationDayScore,
            isElevating = durationDayScore > 0.3
        ))

        // Faktor 4: Kelembaban tinggi berkepanjangan (bobot 0.15)
        val avgHumidity = hourly.map { it.humidity }.average().takeIf { !it.isNaN() } ?: 50.0
        val humidScore = when {
            avgHumidity > 95 -> 0.8
            avgHumidity > 90 -> 0.5
            avgHumidity > 85 -> 0.2
            else -> 0.0
        }
        score += humidScore * 0.15
        factors.add(ContributingFactor(
            name = "Kelembaban",
            value = "${avgHumidity.toInt()}%",
            contribution = humidScore,
            isElevating = humidScore > 0.3
        ))

        val riskLevel = scoreToRiskLevel(score)
        return DisasterPrediction(
            type = DisasterType.GROUND_SUBSIDENCE,
            riskScore = score,
            riskLevel = riskLevel,
            confidence = 0.6, // Tanpa data geologi, confidence rendah
            factors = factors,
            description = buildSubsidenceDescription(riskLevel, weeklyPrecip, consecutiveRainDays),
            recommendation = buildSubsidenceRecommendation(riskLevel)
        )
    }

    // ══════════════════════════════════════════════════
    //  DAILY ANALYSIS (7-day forecast)
    //  Simplified versions using daily + hourly data
    // ══════════════════════════════════════════════════

    private fun analyzeDailyFlood(
        daily: DailyWeatherData,
        hourly: List<HourlyWeatherData>,
        flood: DailyFloodData?
    ): DisasterPrediction {
        var score = 0.0
        val totalPrecip = daily.precipitationSum
        val maxRain = hourly.maxOfOrNull { it.rain + it.showers } ?: 0.0

        score += (when {
            totalPrecip > 100 -> 1.0; totalPrecip > 50 -> 0.8; totalPrecip > 20 -> 0.5
            totalPrecip > 10 -> 0.3; else -> 0.0
        }) * 0.40

        score += (when {
            maxRain > 50 -> 1.0; maxRain > 20 -> 0.8; maxRain > 10 -> 0.5; else -> 0.0
        }) * 0.30

        if (flood != null && flood.dischargeMean > 0) {
            val ratio = flood.riverDischarge / flood.dischargeMean
            score += (when {
                ratio > 5 -> 1.0; ratio > 3 -> 0.7; ratio > 2 -> 0.4; else -> 0.0
            }) * 0.30
        }

        val riskLevel = scoreToRiskLevel(score)
        return DisasterPrediction(
            type = DisasterType.FLOOD, riskScore = score, riskLevel = riskLevel,
            confidence = 0.7, factors = emptyList(),
            description = "Curah hujan %.0f mm".format(totalPrecip),
            recommendation = ""
        )
    }

    @Suppress("UNUSED_PARAMETER")
    private fun analyzeDailyTidalFlood(
        daily: DailyWeatherData,
        hourly: List<HourlyWeatherData>,
        marine: DailyMarineData?
    ): DisasterPrediction {
        var score = 0.0
        val wave = marine?.waveHeightMax ?: 0.0
        val swell = marine?.swellWaveHeightMax ?: 0.0
        val gusts = daily.windGustsMax

        score += (when {
            wave > 4 -> 1.0; wave > 2.5 -> 0.8; wave > 1.5 -> 0.5; wave > 1.0 -> 0.3; else -> 0.0
        }) * 0.40
        score += (when {
            swell > 3 -> 1.0; swell > 2 -> 0.7; swell > 1 -> 0.4; else -> 0.0
        }) * 0.25
        score += (when {
            gusts > 70 -> 1.0; gusts > 50 -> 0.6; gusts > 35 -> 0.3; else -> 0.0
        }) * 0.35

        val riskLevel = scoreToRiskLevel(score)
        return DisasterPrediction(
            type = DisasterType.TIDAL_FLOOD, riskScore = score, riskLevel = riskLevel,
            confidence = if (marine != null) 0.7 else 0.3, factors = emptyList(),
            description = "Gelombang %.1f m, swell %.1f m".format(wave, swell),
            recommendation = ""
        )
    }

    private fun analyzeDailyCyclone(
        daily: DailyWeatherData,
        hourly: List<HourlyWeatherData>
    ): DisasterPrediction {
        var score = 0.0
        val pressure = hourly.map { it.pressure }.filter { it > 0 }.minOrNull() ?: 1013.0
        val maxWind = daily.windSpeedMax
        val maxGusts = daily.windGustsMax
        val maxCape = hourly.maxOfOrNull { it.cape } ?: 0.0

        score += (when {
            pressure < 980 -> 1.0; pressure < 990 -> 0.8; pressure < 1000 -> 0.5; else -> 0.0
        }) * 0.30
        score += (when {
            maxWind > 119 -> 1.0; maxWind > 89 -> 0.8; maxWind > 63 -> 0.6
            maxGusts > 90 -> 0.5; maxGusts > 70 -> 0.3; else -> 0.0
        }) * 0.40
        score += (when {
            maxCape > 3500 -> 1.0; maxCape > 2500 -> 0.6; maxCape > 1500 -> 0.3; else -> 0.0
        }) * 0.15
        score += (when {
            daily.precipitationSum > 100 -> 1.0; daily.precipitationSum > 50 -> 0.5; else -> 0.0
        }) * 0.15

        val riskLevel = scoreToRiskLevel(score)
        return DisasterPrediction(
            type = DisasterType.CYCLONE, riskScore = score, riskLevel = riskLevel,
            confidence = 0.7, factors = emptyList(),
            description = "Angin %.0f km/h, tekanan %d hPa".format(maxWind, pressure.toInt()),
            recommendation = ""
        )
    }

    private fun analyzeDailyThunderstorm(
        daily: DailyWeatherData,
        hourly: List<HourlyWeatherData>
    ): DisasterPrediction {
        var score = 0.0
        val maxCape = hourly.maxOfOrNull { it.cape } ?: 0.0
        val hasTs = hourly.any { it.weatherCode in listOf(95, 96, 99) } || daily.weatherCode in listOf(95, 96, 99)
        val maxGusts = daily.windGustsMax
        val avgWind = hourly.map { it.windSpeed }.average().takeIf { !it.isNaN() } ?: 0.0

        score += (when {
            maxCape > 3500 -> 1.0; maxCape > 2500 -> 0.8; maxCape > 1500 -> 0.5; else -> 0.0
        }) * 0.35
        score += (if (hasTs) 0.8 else 0.0) * 0.30
        score += (when {
            maxGusts - avgWind > 40 -> 1.0; maxGusts - avgWind > 25 -> 0.5; else -> 0.0
        }) * 0.20
        val minFreeze = hourly.filter { it.freezingLevelHeight > 0 }.minOfOrNull { it.freezingLevelHeight } ?: 5000.0
        score += (when {
            maxCape > 2000 && minFreeze < 2500 -> 1.0; maxCape > 1500 && minFreeze < 3000 -> 0.5; else -> 0.0
        }) * 0.15

        val riskLevel = scoreToRiskLevel(score)
        return DisasterPrediction(
            type = DisasterType.THUNDERSTORM, riskScore = score, riskLevel = riskLevel,
            confidence = 0.8, factors = emptyList(),
            description = "CAPE ${maxCape.toInt()} J/kg" + if (hasTs) " ⛈ Badai petir" else "",
            recommendation = ""
        )
    }

    private fun analyzeDailyLandslide(
        daily: DailyWeatherData,
        hourly: List<HourlyWeatherData>,
        allDaily: List<DailyWeatherData>,
        dayIndex: Int
    ): DisasterPrediction {
        var score = 0.0
        val todayPrecip = daily.precipitationSum
        val rainHours = hourly.count { it.precipitation > 0.5 }
        val antecedentRain = allDaily.take(dayIndex + 1).sumOf { it.precipitationSum }

        score += (when {
            todayPrecip > 100 -> 1.0; todayPrecip > 50 -> 0.7; todayPrecip > 20 -> 0.4; else -> 0.0
        }) * 0.30
        score += (when {
            rainHours > 18 -> 1.0; rainHours > 12 -> 0.6; rainHours > 6 -> 0.3; else -> 0.0
        }) * 0.25
        score += (when {
            antecedentRain > 150 -> 1.0; antecedentRain > 100 -> 0.7; antecedentRain > 50 -> 0.4; else -> 0.0
        }) * 0.35
        val humid = hourly.map { it.humidity }.average().takeIf { !it.isNaN() } ?: 50.0
        score += (when { humid > 95 -> 0.8; humid > 90 -> 0.4; else -> 0.0 }) * 0.10

        val riskLevel = scoreToRiskLevel(score)
        return DisasterPrediction(
            type = DisasterType.LANDSLIDE, riskScore = score, riskLevel = riskLevel,
            confidence = 0.6, factors = emptyList(),
            description = "Hujan %.0f mm, akumulasi %.0f mm".format(todayPrecip, antecedentRain),
            recommendation = ""
        )
    }

    @Suppress("UNUSED_PARAMETER")
    private fun analyzeDailyGroundSubsidence(
        daily: DailyWeatherData,
        hourly: List<HourlyWeatherData>,
        allDaily: List<DailyWeatherData>,
        dayIndex: Int,
        flood: DailyFloodData?
    ): DisasterPrediction {
        var score = 0.0
        val cumulPrecip = allDaily.take(dayIndex + 1).sumOf { it.precipitationSum }
        val rDays = allDaily.take(dayIndex + 1).count { it.precipitationSum > 5 }

        score += (when {
            cumulPrecip > 300 -> 1.0; cumulPrecip > 200 -> 0.7; cumulPrecip > 100 -> 0.4; else -> 0.0
        }) * 0.40
        score += (when {
            rDays >= 5 -> 1.0; rDays >= 3 -> 0.5; else -> 0.0
        }) * 0.30
        if (flood != null && flood.dischargeMean > 0) {
            val ratio = flood.riverDischarge / flood.dischargeMean
            score += (when { ratio > 4 -> 1.0; ratio > 2.5 -> 0.5; else -> 0.0 }) * 0.30
        }

        val riskLevel = scoreToRiskLevel(score)
        return DisasterPrediction(
            type = DisasterType.GROUND_SUBSIDENCE, riskScore = score, riskLevel = riskLevel,
            confidence = 0.5, factors = emptyList(),
            description = "Akumulasi %.0f mm / %d hari hujan".format(cumulPrecip, rDays),
            recommendation = ""
        )
    }

    // ══════════════════════════════════════════════════
    //  HELPERS
    // ══════════════════════════════════════════════════

    private fun scoreToRiskLevel(score: Double): RiskLevel {
        return when {
            score >= 0.7 -> RiskLevel.EXTREME
            score >= 0.45 -> RiskLevel.HIGH
            score >= 0.25 -> RiskLevel.MODERATE
            else -> RiskLevel.LOW
        }
    }

    // ── Description Builders ──

    private fun buildFloodDescription(risk: RiskLevel, precip: Double, maxRain: Double, flood: DailyFloodData?): String {
        return buildString {
            when (risk) {
                RiskLevel.EXTREME -> append("🔴 BAHAYA BANJIR — ")
                RiskLevel.HIGH -> append("🟠 Waspada banjir — ")
                RiskLevel.MODERATE -> append("🟡 Potensi banjir — ")
                RiskLevel.LOW -> append("🟢 Risiko banjir rendah — ")
            }
            append("Curah hujan prakiraan %.0f mm dengan intensitas hingga %.0f mm/jam. ".format(precip, maxRain))
            if (flood != null && flood.dischargeMean > 0) {
                val ratio = flood.riverDischarge / flood.dischargeMean
                append("Debit sungai %.1fx dari rata-rata normal.".format(ratio))
            }
        }
    }

    private fun buildFloodRecommendation(risk: RiskLevel): String {
        return when (risk) {
            RiskLevel.EXTREME -> "Segera evakuasi jika berada di daerah rawan banjir. Hindari aliran sungai dan daerah rendah."
            RiskLevel.HIGH -> "Siapkan tas darurat dan jalur evakuasi. Pantau ketinggian air secara berkala."
            RiskLevel.MODERATE -> "Waspadai genangan air. Hindari berkendara saat hujan lebat di daerah rendah."
            RiskLevel.LOW -> "Kondisi normal. Tetap waspada jika hujan turun terus-menerus."
        }
    }

    private fun buildTidalFloodDescription(risk: RiskLevel, wave: Double, swell: Double, hasMarine: Boolean): String {
        return buildString {
            when (risk) {
                RiskLevel.EXTREME -> append("🔴 BAHAYA BANJIR ROB — ")
                RiskLevel.HIGH -> append("🟠 Waspada banjir rob — ")
                RiskLevel.MODERATE -> append("🟡 Potensi banjir rob — ")
                RiskLevel.LOW -> append("🟢 Risiko banjir rob rendah — ")
            }
            if (hasMarine) {
                append("Gelombang hingga %.1f m dengan swell %.1f m. ".format(wave, swell))
                append("Daerah pesisir dan pelabuhan perlu waspada.")
            } else {
                append("Data laut tidak tersedia untuk lokasi ini. Analisis berdasarkan data angin dan tekanan.")
            }
        }
    }

    private fun buildTidalFloodRecommendation(risk: RiskLevel, hasMarine: Boolean): String {
        if (!hasMarine) return "Data laut tidak tersedia. Pantau informasi BMKG untuk peringatan gelombang tinggi."
        return when (risk) {
            RiskLevel.EXTREME -> "Jauhi pantai dan pelabuhan. Nelayan dilarang melaut. Evakuasi pemukiman pesisir."
            RiskLevel.HIGH -> "Hindari aktivitas di pantai. Nelayan berhati-hati. Waspadai air pasang."
            RiskLevel.MODERATE -> "Waspada saat di pesisir. Nelayan perhatikan prakiraan gelombang."
            RiskLevel.LOW -> "Kondisi laut relatif aman. Tetap perhatikan prakiraan cuaca maritim."
        }
    }

    private fun buildCycloneDescription(risk: RiskLevel, pressure: Double, wind: Double, gusts: Double): String {
        return buildString {
            when (risk) {
                RiskLevel.EXTREME -> append("🔴 BAHAYA SIKLON — ")
                RiskLevel.HIGH -> append("🟠 Waspada siklon/badai — ")
                RiskLevel.MODERATE -> append("🟡 Indikasi cuaca siklonik — ")
                RiskLevel.LOW -> append("🟢 Tidak ada indikasi siklon — ")
            }
            append("Tekanan %d hPa, angin %.0f km/h (gust %.0f km/h). ".format(
                pressure.toInt(), wind, gusts
            ))
            if (risk >= RiskLevel.HIGH) {
                append("Kondisi atmosfer menunjukkan pola siklonik.")
            }
        }
    }

    private fun buildCycloneRecommendation(risk: RiskLevel): String {
        return when (risk) {
            RiskLevel.EXTREME -> "DARURAT! Segera cari perlindungan. Ikuti instruksi evakuasi. Jauhi pantai dan bangunan rapuh."
            RiskLevel.HIGH -> "Amankan properti, siapkan kebutuhan darurat. Pantau informasi BMKG secara intens."
            RiskLevel.MODERATE -> "Perhatikan perkembangan cuaca. Hindari aktivitas luar ruangan yang berisiko."
            RiskLevel.LOW -> "Kondisi normal. Tidak ada indikasi gangguan siklonik signifikan."
        }
    }

    private fun buildThunderstormDescription(risk: RiskLevel, cape: Double, gusts: Double, hasTs: Boolean): String {
        return buildString {
            when (risk) {
                RiskLevel.EXTREME -> append("🔴 BADAI PETIR HEBAT — ")
                RiskLevel.HIGH -> append("🟠 Waspada badai petir — ")
                RiskLevel.MODERATE -> append("🟡 Potensi badai petir — ")
                RiskLevel.LOW -> append("🟢 Risiko badai petir rendah — ")
            }
            append("CAPE ${cape.toInt()} J/kg, gust hingga %.0f km/h. ".format(gusts))
            if (hasTs) append("Kode cuaca mengindikasikan badai petir aktif.")
        }
    }

    private fun buildThunderstormRecommendation(risk: RiskLevel): String {
        return when (risk) {
            RiskLevel.EXTREME -> "Segera masuk bangunan! Jauhi pohon tinggi, tiang listrik, dan tempat terbuka."
            RiskLevel.HIGH -> "Hindari aktivitas luar ruangan. Cabut peralatan elektronik dari stop kontak."
            RiskLevel.MODERATE -> "Waspadai petir saat berada di luar. Siapkan perlindungan."
            RiskLevel.LOW -> "Kondisi relatif aman. Tetap waspada jika langit mendung gelap."
        }
    }

    private fun buildLandslideDescription(risk: RiskLevel, precip: Double, antecedent: Double, hours: Int): String {
        return buildString {
            when (risk) {
                RiskLevel.EXTREME -> append("🔴 BAHAYA LONGSOR — ")
                RiskLevel.HIGH -> append("🟠 Waspada longsor — ")
                RiskLevel.MODERATE -> append("🟡 Potensi longsor — ")
                RiskLevel.LOW -> append("🟢 Risiko longsor rendah — ")
            }
            append("Curah hujan %.0f mm selama $hours jam. ".format(precip))
            if (antecedent > 50) {
                append("Akumulasi 3 hari: %.0f mm — tanah mulai jenuh air.".format(antecedent))
            }
        }
    }

    private fun buildLandslideRecommendation(risk: RiskLevel): String {
        return when (risk) {
            RiskLevel.EXTREME -> "SEGERA EVAKUASI jika berada di lereng/perbukitan! Jauhi tebing dan aliran air."
            RiskLevel.HIGH -> "Siaga evakuasi. Perhatikan retakan tanah. Jauhi lereng curam saat hujan."
            RiskLevel.MODERATE -> "Waspadai tanda-tanda longsor: tanah retak, air keruh, suara gemuruh dari bukit."
            RiskLevel.LOW -> "Kondisi normal. Perhatikan jika hujan terus-menerus selama beberapa hari."
        }
    }

    private fun buildSubsidenceDescription(risk: RiskLevel, weeklyPrecip: Double, rainDays: Int): String {
        return buildString {
            when (risk) {
                RiskLevel.EXTREME -> append("🔴 WASPADA AMBLASAN — ")
                RiskLevel.HIGH -> append("🟠 Potensi tanah amblas — ")
                RiskLevel.MODERATE -> append("🟡 Indikasi tanah amblas — ")
                RiskLevel.LOW -> append("🟢 Risiko amblas rendah — ")
            }
            append("Akumulasi hujan 7 hari: %.0f mm, $rainDays hari hujan berturut-turut. ".format(weeklyPrecip))
            if (risk >= RiskLevel.MODERATE) {
                append("Genangan berkepanjangan dapat melemahkan struktur tanah.")
            }
        }
    }

    private fun buildSubsidenceRecommendation(risk: RiskLevel): String {
        return when (risk) {
            RiskLevel.EXTREME -> "Hindari jalan yang tergenang lama. Periksa fondasi bangunan. Laporkan retakan tanah."
            RiskLevel.HIGH -> "Waspadai genangan yang tidak surut. Perhatikan penurunan permukaan tanah."
            RiskLevel.MODERATE -> "Pantau genangan air di sekitar rumah. Pastikan drainase berfungsi baik."
            RiskLevel.LOW -> "Kondisi normal. Pastikan saluran air tidak tersumbat."
        }
    }
}

package com.weather.forecast.data.repository

import com.weather.forecast.data.ai.DisasterNeuralNetwork
import com.weather.forecast.data.ai.WeatherFeatureExtractor
import com.weather.forecast.data.ai.WeatherFeatures
import com.weather.forecast.data.ai.WeightDeltas
import com.weather.forecast.data.locale.AppLocaleManager
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
        weightDeltas: WeightDeltas? = null,
        terrainData: LandslideTerrainData? = null
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
        predictions.add(analyzeLandslide(hourly, dailyToday, weather?.daily, terrainData))
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
        weightDeltas: WeightDeltas? = null,
        terrainData: LandslideTerrainData? = null
    ): List<DisasterPrediction> {
        val predictions = mutableListOf<DisasterPrediction>()

        predictions.add(analyzeDailyFlood(daily, hourlyForDay, floodDaily))
        predictions.add(analyzeDailyTidalFlood(daily, hourlyForDay, marineDaily))
        predictions.add(analyzeDailyCyclone(daily, hourlyForDay))
        predictions.add(analyzeDailyThunderstorm(daily, hourlyForDay))
        predictions.add(analyzeDailyLandslide(daily, hourlyForDay, allDaily, dayIndex, terrainData))
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
        val s = AppLocaleManager.strings
        val highRisks = predictions.filter { it.riskLevel >= RiskLevel.HIGH }
        val moderateRisks = predictions.filter { it.riskLevel == RiskLevel.MODERATE }

        return buildString {
            if (highRisks.isEmpty() && moderateRisks.isEmpty()) {
                append(s.summaryAllClear)
            } else {
                if (highRisks.isNotEmpty()) {
                    val types = highRisks.joinToString(", ") { s.localized(it.type.label, it.type.labelId) }
                    val risk = s.localized(highRisks.first().riskLevel.label, highRisks.first().riskLevel.labelId)
                    append(s.summaryWarning(types, risk))
                }
                if (moderateRisks.isNotEmpty()) {
                    val types = moderateRisks.joinToString(", ") { s.localized(it.type.label, it.type.labelId) }
                    append(s.summaryAlsoWatch(types))
                }
                append(s.summaryStayAlert)
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
        val s = AppLocaleManager.strings
        val factors = mutableListOf<ContributingFactor>()
        var score = 0.0
        var dataPoints = 0

        // Faktor 1: Curah hujan kumulatif (bobot 0.35)
        val totalPrecip = daily?.precipitationSum ?: hourly.sumOf { it.precipitation }
        val precipScore = scored(totalPrecip, 100.0 to 1.0, 50.0 to 0.8, 20.0 to 0.5, 10.0 to 0.3, 5.0 to 0.15)
        score += factors.weighted(s.factorRainfall, "%.1f mm".format(totalPrecip), precipScore, 0.35)
        dataPoints++

        // Faktor 2: Intensitas hujan per jam tertinggi (bobot 0.25)
        val maxRainHourly = hourly.maxOfOrNull { it.rain + it.showers } ?: 0.0
        val intensityScore = scored(maxRainHourly, 50.0 to 1.0, 20.0 to 0.8, 10.0 to 0.5, 5.0 to 0.3)
        score += factors.weighted(s.factorRainIntensity, "%.1f mm/jam".format(maxRainHourly), intensityScore, 0.25)
        dataPoints++

        // Faktor 3: Debit sungai vs rata-rata (bobot 0.25)
        val floodDaily = flood?.dailyForecast?.firstOrNull()
        if (floodDaily != null && floodDaily.dischargeMean > 0) {
            val ratio = floodDaily.riverDischarge / floodDaily.dischargeMean
            val dischargeScore = scored(ratio, 5.0 to 1.0, 3.0 to 0.8, 2.0 to 0.5, 1.5 to 0.3)
            score += factors.weighted(
                s.factorRiverDischarge,
                "%.1f m³/s (%.1fx rata-rata)".format(floodDaily.riverDischarge, ratio),
                dischargeScore, 0.25
            )
            dataPoints++
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
        score += factors.weighted(
            s.factorAtmosphere,
            s.factorAtmosphereDesc(avgHumidity.toInt().toString(), avgPressure.toInt().toString()),
            atmosphereScore, 0.15
        )
        dataPoints++

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
        val s = AppLocaleManager.strings
        val factors = mutableListOf<ContributingFactor>()
        var score = 0.0
        var dataPoints = 0
        val hasMarineData = marine != null

        // Faktor 1: Tinggi gelombang (bobot 0.40)
        val waveHeight = marine?.current?.waveHeight ?: 0.0
        val waveDailyMax = marine?.dailyForecast?.firstOrNull()?.waveHeightMax ?: waveHeight
        val maxWave = maxOf(waveHeight, waveDailyMax)
        val waveScore = scored(maxWave, 4.0 to 1.0, 2.5 to 0.8, 1.5 to 0.5, 1.0 to 0.3)
        score += factors.weighted(s.factorWaveHeight, "%.1f m".format(maxWave), waveScore, 0.40)
        dataPoints++

        // Faktor 2: Swell wave (bobot 0.20)
        val swellHeight = marine?.current?.swellWaveHeight ?: 0.0
        val swellScore = scored(swellHeight, 3.0 to 1.0, 2.0 to 0.7, 1.0 to 0.4)
        score += factors.weighted(s.factorSwell, "%.1f m".format(swellHeight), swellScore, 0.20)
        if (swellHeight > 0) dataPoints++

        // Faktor 3: Angin kencang dari laut (bobot 0.20)
        val windSpeed = current?.windSpeed ?: hourly.firstOrNull()?.windSpeed ?: 0.0
        val windGusts = current?.windGusts ?: hourly.maxOfOrNull { it.windGusts } ?: 0.0
        val windScore = scored(windGusts, 70.0 to 1.0, 50.0 to 0.7, 35.0 to 0.4, 20.0 to 0.2)
        score += factors.weighted(s.factorWindGust, "%.0f / %.0f km/h".format(windSpeed, windGusts), windScore, 0.20)
        dataPoints++

        // Faktor 4: Tekanan rendah (bobot 0.20)
        val pressure = current?.pressure ?: hourly.firstOrNull()?.pressure ?: 1013.0
        val pressureScore = scoredBelow(pressure, 995.0 to 1.0, 1000.0 to 0.7, 1005.0 to 0.4, 1010.0 to 0.2)
        score += factors.weighted(s.factorAirPressure, "${pressure.toInt()} hPa", pressureScore, 0.20)
        dataPoints++

        val confidence = if (hasMarineData) (dataPoints.toDouble() / 4.0).coerceIn(0.5, 1.0) else 0.3
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
        val s = AppLocaleManager.strings
        val factors = mutableListOf<ContributingFactor>()
        var score = 0.0

        // Faktor 1: Tekanan sangat rendah (bobot 0.30)
        val pressure = current?.pressure
            ?: hourly.map { it.pressure }.filter { it > 0 }.minOrNull()
            ?: 1013.0
        val minPressure = hourly.map { it.pressure }.filter { it > 0 }.minOrNull() ?: pressure
        val effectivePressure = minOf(pressure, minPressure)
        val pressureScore = scoredBelow(effectivePressure, 980.0 to 1.0, 990.0 to 0.8, 1000.0 to 0.5, 1005.0 to 0.2)
        score += factors.weighted(s.factorMinPressure, "${effectivePressure.toInt()} hPa", pressureScore, 0.30)

        // Faktor 2: Angin sustained + gusts (bobot 0.35)
        val maxWindSpeed = maxOf(current?.windSpeed ?: 0.0, hourly.maxOfOrNull { it.windSpeed } ?: 0.0)
        val maxGusts = maxOf(current?.windGusts ?: 0.0, daily?.windGustsMax ?: 0.0, hourly.maxOfOrNull { it.windGusts } ?: 0.0)
        val windCycloneScore = when {
            maxWindSpeed > 119 -> 1.0
            maxWindSpeed > 89 -> 0.8
            maxWindSpeed > 63 -> 0.6
            maxGusts > 90 -> 0.5
            maxGusts > 70 -> 0.3
            maxGusts > 50 -> 0.15
            else -> 0.0
        }
        score += factors.weighted(s.factorWindSpeed, "%.0f km/h (gust %.0f)".format(maxWindSpeed, maxGusts), windCycloneScore, 0.35)

        // Faktor 3: CAPE tinggi (bobot 0.15)
        val maxCape = maxOf(current?.cape ?: 0.0, hourly.maxOfOrNull { it.cape } ?: 0.0)
        val capeScore = scored(maxCape, 3500.0 to 1.0, 2500.0 to 0.7, 1500.0 to 0.4, 1000.0 to 0.2)
        score += factors.weighted(s.factorCAPE, "${maxCape.toInt()} J/kg", capeScore, 0.15)

        // Faktor 4: Curah hujan masif (bobot 0.20)
        val totalPrecip = daily?.precipitationSum ?: hourly.sumOf { it.precipitation }
        val precipCycloneScore = scored(totalPrecip, 100.0 to 1.0, 50.0 to 0.7, 30.0 to 0.4)
        score += factors.weighted(s.factorRainfall, "%.1f mm".format(totalPrecip), precipCycloneScore, 0.20)

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
        val s = AppLocaleManager.strings
        val factors = mutableListOf<ContributingFactor>()
        var score = 0.0

        // Faktor 1: CAPE (bobot 0.35)
        val maxCape = maxOf(current?.cape ?: 0.0, hourly.maxOfOrNull { it.cape } ?: 0.0)
        val capeScore = scored(maxCape, 3500.0 to 1.0, 2500.0 to 0.8, 1500.0 to 0.5, 500.0 to 0.2)
        score += factors.weighted(s.factorCAPE, "${maxCape.toInt()} J/kg", capeScore, 0.35)

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
        score += factors.weighted(s.factorWMO, if (hasThunderstormCode) s.wmoDetected else s.wmoNone, wmoScore, 0.25)

        // Faktor 3: Wind shear (bobot 0.20)
        val maxGusts = maxOf(current?.windGusts ?: 0.0, hourly.maxOfOrNull { it.windGusts } ?: 0.0)
        val avgWind = hourly.map { it.windSpeed }.average().takeIf { !it.isNaN() } ?: 0.0
        val windShear = maxGusts - avgWind
        val shearScore = scored(windShear, 40.0 to 1.0, 30.0 to 0.7, 20.0 to 0.4, 10.0 to 0.2)
        score += factors.weighted(s.factorWindShear, "%.0f km/h".format(windShear), shearScore, 0.20)

        // Faktor 4: Freezing level rendah + CAPE → hujan es (bobot 0.20)
        val minFreezing = hourly.filter { it.freezingLevelHeight > 0 }
            .minOfOrNull { it.freezingLevelHeight } ?: 5000.0
        val hailScore = when {
            maxCape > 2000 && minFreezing < 2500 -> 1.0
            maxCape > 1500 && minFreezing < 3000 -> 0.6
            maxCape > 1000 && minFreezing < 3500 -> 0.3
            else -> 0.0
        }
        score += factors.weighted(
            s.factorHailPotential,
            "Freezing ${(minFreezing / 1000).toInt()} km, CAPE ${maxCape.toInt()}",
            hailScore, 0.20
        )

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
    //  TANAH LONGSOR — Enhanced Landslide Analysis
    // ══════════════════════════════════════════════════

    /**
     * Analisis risiko longsor dengan data terrain lengkap.
     *
     * Faktor-faktor yang digunakan:
     * 1. Kemiringan lereng (slope gradient) — dari Open-Elevation API
     * 2. Kelembaban tanah / soil saturation — dari Open-Meteo soil data
     * 3. Curah hujan kumulatif hari ini — dari data cuaca
     * 4. Hujan anteseden 3 hari — trigger utama longsor
     * 5. Intensitas hujan maksimum per jam — pemicu longsor cepat
     * 6. Durasi hujan terus-menerus — infiltrasi berlanjut
     * 7. Tutupan vegetasi (proxy) — stabilitas lereng
     * 8. Kelembaban udara — indikator tanah basah
     *
     * Referensi:
     * - Varnes (1978) — Slope Movement Classification
     * - PVMBG (2019) — Sistem Peringatan Dini Longsor Indonesia
     * - Caine (1980) — Rainfall intensity-duration threshold
     * - Van Zuidam (1985) — Geomorphological slope classification
     */
    private fun analyzeLandslide(
        hourly: List<HourlyWeatherData>,
        daily: DailyWeatherData?,
        allDaily: List<DailyWeatherData>?,
        terrain: LandslideTerrainData? = null
    ): DisasterPrediction {
        val s = AppLocaleManager.strings
        val factors = mutableListOf<ContributingFactor>()
        var score = 0.0
        var dataPoints = 0

        // Faktor 1: Kemiringan lereng (bobot 0.20)
        val slopeAngle = terrain?.slopeAngle ?: 0.0
        val slopeCategory = terrain?.slopeCategory ?: SlopeCategory.FLAT
        val slopeScore = slopeCategory.riskFactor
        score += factors.weighted(
            s.factorSlopeGradient,
            "%.1f° (%s)".format(slopeAngle, s.localized(slopeCategory.label, slopeCategory.labelId)),
            slopeScore, 0.20
        )
        if (terrain != null && terrain.elevationGrid.isNotEmpty()) dataPoints++

        // Faktor 2: Kejenuhan tanah (bobot 0.15)
        val saturationIndex = terrain?.soilSaturationIndex ?: run {
            val sm = hourly.map { it.soilMoistureShallow }.filter { it > 0 }
            if (sm.isNotEmpty()) (sm.average() / 0.50).coerceIn(0.0, 1.0) else 0.0
        }
        val saturationScore = scored(saturationIndex, 0.9 to 1.0, 0.75 to 0.7, 0.6 to 0.4, 0.4 to 0.2)
        score += factors.weighted(s.factorSoilSaturation, "%.0f%%".format(saturationIndex * 100), saturationScore, 0.15)
        if (saturationIndex > 0) dataPoints++

        // Faktor 3: Curah hujan kumulatif hari ini (bobot 0.15)
        val todayPrecip = daily?.precipitationSum ?: hourly.sumOf { it.precipitation }
        val precipScore = scored(todayPrecip, 100.0 to 1.0, 50.0 to 0.7, 20.0 to 0.4, 10.0 to 0.2)
        score += factors.weighted(s.factorRainfallToday, "%.1f mm".format(todayPrecip), precipScore, 0.15)
        dataPoints++

        // Faktor 4: Hujan kumulatif 3 hari sebelumnya (bobot 0.15)
        val recentDays = allDaily?.take(3) ?: emptyList()
        val antecedentRain = recentDays.sumOf { it.precipitationSum }
        val antecedentScore = scored(antecedentRain, 150.0 to 1.0, 100.0 to 0.8, 50.0 to 0.5, 20.0 to 0.2)
        score += factors.weighted(s.factorRain3Day, "%.0f mm".format(antecedentRain), antecedentScore, 0.15)
        dataPoints++

        // Faktor 5: Intensitas hujan per jam maks (bobot 0.10)
        val maxIntensity = terrain?.maxRainfallIntensity ?: (hourly.maxOfOrNull { it.rain + it.showers } ?: 0.0)
        val intensityScore = scored(maxIntensity, 50.0 to 1.0, 20.0 to 0.7, 10.0 to 0.4, 5.0 to 0.2)
        score += factors.weighted(s.factorRainIntensityMax, "%.1f mm/h".format(maxIntensity), intensityScore, 0.10)
        dataPoints++

        // Faktor 6: Durasi hujan terus-menerus (bobot 0.10)
        val rainHours = terrain?.continuousRainHours ?: hourly.count { it.precipitation > 0.5 }
        val durationScore = scored(rainHours.toDouble(), 18.0 to 1.0, 12.0 to 0.7, 8.0 to 0.4, 4.0 to 0.2)
        score += factors.weighted(s.factorRainDuration, "$rainHours h", durationScore, 0.10)
        dataPoints++

        // Faktor 7: Indeks tutupan vegetasi (bobot 0.05)
        val vegIndex = terrain?.vegetationIndex ?: 0.5
        val vegScore = scoredBelow(vegIndex, 0.2 to 0.8, 0.4 to 0.5, 0.6 to 0.2)
        score += factors.weighted(s.factorVegetation, "%.0f%%".format(vegIndex * 100), vegScore, 0.05)
        if (terrain != null) dataPoints++

        // Faktor 8: Kelembaban udara (bobot 0.10)
        val avgHumidity = hourly.map { it.humidity }.average().takeIf { !it.isNaN() } ?: 50.0
        val humidityScore = scored(avgHumidity, 95.0 to 0.8, 90.0 to 0.5, 85.0 to 0.3)
        score += factors.weighted(s.factorAvgHumidity, "${avgHumidity.toInt()}%", humidityScore, 0.10)
        dataPoints++

        // Confidence berdasarkan kelengkapan data
        val hasTerrainData = terrain != null && terrain.elevationGrid.isNotEmpty()
        val hasSoilData = saturationIndex > 0
        val baseConfidence = (dataPoints.toDouble() / 8.0).coerceIn(0.5, 1.0)
        val confidence = when {
            hasTerrainData && hasSoilData -> baseConfidence
            hasSoilData || hasTerrainData -> baseConfidence * 0.85
            else -> 0.6
        }

        val riskLevel = scoreToRiskLevel(score)
        return DisasterPrediction(
            type = DisasterType.LANDSLIDE,
            riskScore = score,
            riskLevel = riskLevel,
            confidence = confidence,
            factors = factors,
            description = buildLandslideDescription(riskLevel, todayPrecip, antecedentRain, rainHours,
                slopeAngle, saturationIndex, hasTerrainData),
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
        val s = AppLocaleManager.strings
        val factors = mutableListOf<ContributingFactor>()
        var score = 0.0

        // Faktor 1: Akumulasi hujan berkepanjangan (bobot 0.35)
        val weeklyPrecip = allDaily?.sumOf { it.precipitationSum } ?: 0.0
        val prolongedScore = scored(weeklyPrecip, 300.0 to 1.0, 200.0 to 0.7, 100.0 to 0.4, 50.0 to 0.2)
        score += factors.weighted(s.factorRain7Day, "%.0f mm".format(weeklyPrecip), prolongedScore, 0.35)

        // Faktor 2: Debit sungai tinggi berkepanjangan (bobot 0.25)
        val avgDischargeRatio = flood?.dailyForecast?.let { days ->
            val ratios = days.mapNotNull { d ->
                if (d.dischargeMean > 0) d.riverDischarge / d.dischargeMean else null
            }
            ratios.average().takeIf { !it.isNaN() }
        } ?: 1.0
        val dischargeScore = scored(avgDischargeRatio, 4.0 to 1.0, 2.5 to 0.6, 1.5 to 0.3)
        score += factors.weighted(s.factorDischargeRatio, "%.1fx rata-rata".format(avgDischargeRatio), dischargeScore, 0.25)

        // Faktor 3: Durasi genangan — hari hujan berturut-turut (bobot 0.25)
        val consecutiveRainDays = allDaily?.takeWhile { it.precipitationSum > 5 }?.size ?: 0
        val durationDayScore = scored(consecutiveRainDays.toDouble(), 5.0 to 1.0, 3.0 to 0.6, 2.0 to 0.3)
        score += factors.weighted(s.factorConsecutiveRain, "$consecutiveRainDays hari", durationDayScore, 0.25)

        // Faktor 4: Kelembaban tinggi berkepanjangan (bobot 0.15)
        val avgHumidity = hourly.map { it.humidity }.average().takeIf { !it.isNaN() } ?: 50.0
        val humidScore = scored(avgHumidity, 95.0 to 0.8, 90.0 to 0.5, 85.0 to 0.2)
        score += factors.weighted(s.factorHumidity, "${avgHumidity.toInt()}%", humidScore, 0.15)

        val riskLevel = scoreToRiskLevel(score)
        return DisasterPrediction(
            type = DisasterType.GROUND_SUBSIDENCE,
            riskScore = score,
            riskLevel = riskLevel,
            confidence = 0.6,
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
        val s = AppLocaleManager.strings
        var score = 0.0
        val totalPrecip = daily.precipitationSum
        val maxRain = hourly.maxOfOrNull { it.rain + it.showers } ?: 0.0

        score += scored(totalPrecip, 100.0 to 1.0, 50.0 to 0.8, 20.0 to 0.5, 10.0 to 0.3) * 0.40
        score += scored(maxRain, 50.0 to 1.0, 20.0 to 0.8, 10.0 to 0.5) * 0.30
        if (flood != null && flood.dischargeMean > 0) {
            val ratio = flood.riverDischarge / flood.dischargeMean
            score += scored(ratio, 5.0 to 1.0, 3.0 to 0.7, 2.0 to 0.4) * 0.30
        }

        val riskLevel = scoreToRiskLevel(score)
        return DisasterPrediction(
            type = DisasterType.FLOOD, riskScore = score, riskLevel = riskLevel,
            confidence = 0.7, factors = emptyList(),
            description = s.floodAnalysis("%.0f".format(totalPrecip)),
            recommendation = ""
        )
    }

    @Suppress("UNUSED_PARAMETER")
    private fun analyzeDailyTidalFlood(
        daily: DailyWeatherData,
        hourly: List<HourlyWeatherData>,
        marine: DailyMarineData?
    ): DisasterPrediction {
        val s = AppLocaleManager.strings
        var score = 0.0
        val wave = marine?.waveHeightMax ?: 0.0
        val swell = marine?.swellWaveHeightMax ?: 0.0

        score += scored(wave, 4.0 to 1.0, 2.5 to 0.8, 1.5 to 0.5, 1.0 to 0.3) * 0.40
        score += scored(swell, 3.0 to 1.0, 2.0 to 0.7, 1.0 to 0.4) * 0.25
        score += scored(daily.windGustsMax, 70.0 to 1.0, 50.0 to 0.6, 35.0 to 0.3) * 0.35

        val riskLevel = scoreToRiskLevel(score)
        return DisasterPrediction(
            type = DisasterType.TIDAL_FLOOD, riskScore = score, riskLevel = riskLevel,
            confidence = if (marine != null) 0.7 else 0.3, factors = emptyList(),
            description = s.tidalFloodAnalysis("%.1f".format(wave), "%.1f".format(swell)),
            recommendation = ""
        )
    }

    private fun analyzeDailyCyclone(
        daily: DailyWeatherData,
        hourly: List<HourlyWeatherData>
    ): DisasterPrediction {
        val s = AppLocaleManager.strings
        var score = 0.0
        val pressure = hourly.map { it.pressure }.filter { it > 0 }.minOrNull() ?: 1013.0
        val maxWind = daily.windSpeedMax
        val maxGusts = daily.windGustsMax
        val maxCape = hourly.maxOfOrNull { it.cape } ?: 0.0

        score += scoredBelow(pressure, 980.0 to 1.0, 990.0 to 0.8, 1000.0 to 0.5) * 0.30
        score += (when {
            maxWind > 119 -> 1.0; maxWind > 89 -> 0.8; maxWind > 63 -> 0.6
            maxGusts > 90 -> 0.5; maxGusts > 70 -> 0.3; else -> 0.0
        }) * 0.40
        score += scored(maxCape, 3500.0 to 1.0, 2500.0 to 0.6, 1500.0 to 0.3) * 0.15
        score += scored(daily.precipitationSum, 100.0 to 1.0, 50.0 to 0.5) * 0.15

        val riskLevel = scoreToRiskLevel(score)
        return DisasterPrediction(
            type = DisasterType.CYCLONE, riskScore = score, riskLevel = riskLevel,
            confidence = 0.7, factors = emptyList(),
            description = s.cycloneAnalysis("%.0f".format(maxWind), pressure.toInt().toString()),
            recommendation = ""
        )
    }

    private fun analyzeDailyThunderstorm(
        daily: DailyWeatherData,
        hourly: List<HourlyWeatherData>
    ): DisasterPrediction {
        val s = AppLocaleManager.strings
        var score = 0.0
        val maxCape = hourly.maxOfOrNull { it.cape } ?: 0.0
        val hasTs = hourly.any { it.weatherCode in listOf(95, 96, 99) } || daily.weatherCode in listOf(95, 96, 99)
        val avgWind = hourly.map { it.windSpeed }.average().takeIf { !it.isNaN() } ?: 0.0

        score += scored(maxCape, 3500.0 to 1.0, 2500.0 to 0.8, 1500.0 to 0.5) * 0.35
        score += (if (hasTs) 0.8 else 0.0) * 0.30
        score += scored(daily.windGustsMax - avgWind, 40.0 to 1.0, 25.0 to 0.5) * 0.20
        val minFreeze = hourly.filter { it.freezingLevelHeight > 0 }.minOfOrNull { it.freezingLevelHeight } ?: 5000.0
        score += (when {
            maxCape > 2000 && minFreeze < 2500 -> 1.0; maxCape > 1500 && minFreeze < 3000 -> 0.5; else -> 0.0
        }) * 0.15

        val riskLevel = scoreToRiskLevel(score)
        return DisasterPrediction(
            type = DisasterType.THUNDERSTORM, riskScore = score, riskLevel = riskLevel,
            confidence = 0.8, factors = emptyList(),
            description = s.thunderstormAnalysis(maxCape.toInt().toString()),
            recommendation = ""
        )
    }

    private fun analyzeDailyLandslide(
        daily: DailyWeatherData,
        hourly: List<HourlyWeatherData>,
        allDaily: List<DailyWeatherData>,
        dayIndex: Int,
        terrain: LandslideTerrainData? = null
    ): DisasterPrediction {
        val s = AppLocaleManager.strings
        var score = 0.0
        val todayPrecip = daily.precipitationSum
        val rainHours = hourly.count { it.precipitation > 0.5 }
        val antecedentRain = allDaily.take(dayIndex + 1).sumOf { it.precipitationSum }

        score += (terrain?.slopeCategory?.riskFactor ?: 0.0) * 0.15
        val satIdx = terrain?.soilSaturationIndex ?: run {
            val sm = hourly.map { it.soilMoistureShallow }.filter { it > 0 }
            if (sm.isNotEmpty()) (sm.average() / 0.50).coerceIn(0.0, 1.0) else 0.0
        }
        score += scored(satIdx, 0.9 to 1.0, 0.75 to 0.7, 0.6 to 0.4) * 0.10
        score += scored(todayPrecip, 100.0 to 1.0, 50.0 to 0.7, 20.0 to 0.4) * 0.20
        score += scored(rainHours.toDouble(), 18.0 to 1.0, 12.0 to 0.6, 6.0 to 0.3) * 0.15
        score += scored(antecedentRain, 150.0 to 1.0, 100.0 to 0.7, 50.0 to 0.4) * 0.20
        score += scored(hourly.maxOfOrNull { it.rain + it.showers } ?: 0.0, 50.0 to 1.0, 20.0 to 0.7, 10.0 to 0.4) * 0.10
        val humid = hourly.map { it.humidity }.average().takeIf { !it.isNaN() } ?: 50.0
        score += scored(humid, 95.0 to 0.8, 90.0 to 0.4) * 0.10

        val riskLevel = scoreToRiskLevel(score)
        return DisasterPrediction(
            type = DisasterType.LANDSLIDE, riskScore = score, riskLevel = riskLevel,
            confidence = if (terrain != null) 0.8 else 0.6, factors = emptyList(),
            description = s.landslideAnalysis("%.0f".format(todayPrecip), "%.0f".format(antecedentRain)),
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
        val s = AppLocaleManager.strings
        var score = 0.0
        val cumulPrecip = allDaily.take(dayIndex + 1).sumOf { it.precipitationSum }
        val rDays = allDaily.take(dayIndex + 1).count { it.precipitationSum > 5 }

        score += scored(cumulPrecip, 300.0 to 1.0, 200.0 to 0.7, 100.0 to 0.4) * 0.40
        score += scored(rDays.toDouble(), 5.0 to 1.0, 3.0 to 0.5) * 0.30
        if (flood != null && flood.dischargeMean > 0) {
            val ratio = flood.riverDischarge / flood.dischargeMean
            score += scored(ratio, 4.0 to 1.0, 2.5 to 0.5) * 0.30
        }

        val riskLevel = scoreToRiskLevel(score)
        return DisasterPrediction(
            type = DisasterType.GROUND_SUBSIDENCE, riskScore = score, riskLevel = riskLevel,
            confidence = 0.5, factors = emptyList(),
            description = s.subsidenceAnalysis("%.0f".format(cumulPrecip), rDays),
            recommendation = ""
        )
    }

    // ══════════════════════════════════════════════════
    //  HELPERS
    // ══════════════════════════════════════════════════

    private fun scoreToRiskLevel(score: Double): RiskLevel = when {
        score >= 0.7 -> RiskLevel.EXTREME
        score >= 0.45 -> RiskLevel.HIGH
        score >= 0.25 -> RiskLevel.MODERATE
        else -> RiskLevel.LOW
    }

    /** Stepped score: returns score paired with first threshold value exceeds (descending). */
    private fun scored(value: Double, vararg levels: Pair<Double, Double>): Double {
        for ((threshold, score) in levels) if (value > threshold) return score
        return 0.0
    }

    /** Stepped score (inverted): returns score for first threshold value falls below (ascending). */
    private fun scoredBelow(value: Double, vararg levels: Pair<Double, Double>): Double {
        for ((threshold, score) in levels) if (value < threshold) return score
        return 0.0
    }

    /** Add a ContributingFactor and return its weighted contribution to total score. */
    private fun MutableList<ContributingFactor>.weighted(
        name: String, value: String, contribution: Double, weight: Double
    ): Double {
        add(ContributingFactor(name, value, contribution, isElevating = contribution > 0.3))
        return contribution * weight
    }

    // ── Description Builders (delegating to AppStrings) ──

    private fun riskInt(risk: RiskLevel) = when (risk) {
        RiskLevel.EXTREME -> 3; RiskLevel.HIGH -> 2; RiskLevel.MODERATE -> 1; RiskLevel.LOW -> 0
    }

    private fun buildFloodDescription(risk: RiskLevel, precip: Double, maxRain: Double, flood: DailyFloodData?): String {
        val ratioStr = flood?.let {
            if (it.dischargeMean > 0) "%.1f".format(it.riverDischarge / it.dischargeMean) else null
        }
        return AppLocaleManager.strings.floodDesc(riskInt(risk), "%.0f".format(precip), "%.0f".format(maxRain), ratioStr)
    }

    private fun buildFloodRecommendation(risk: RiskLevel) = AppLocaleManager.strings.floodRec(riskInt(risk))

    private fun buildTidalFloodDescription(risk: RiskLevel, wave: Double, swell: Double, hasMarine: Boolean) =
        AppLocaleManager.strings.tidalFloodDesc(riskInt(risk), "%.1f".format(wave), "%.1f".format(swell), hasMarine)

    private fun buildTidalFloodRecommendation(risk: RiskLevel, hasMarine: Boolean) =
        AppLocaleManager.strings.tidalFloodRec(riskInt(risk), hasMarine)

    private fun buildCycloneDescription(risk: RiskLevel, pressure: Double, wind: Double, gusts: Double) =
        AppLocaleManager.strings.cycloneDesc(riskInt(risk), pressure.toInt().toString(), "%.0f".format(wind), "%.0f".format(gusts))

    private fun buildCycloneRecommendation(risk: RiskLevel) = AppLocaleManager.strings.cycloneRec(riskInt(risk))

    private fun buildThunderstormDescription(risk: RiskLevel, cape: Double, gusts: Double, hasTs: Boolean) =
        AppLocaleManager.strings.thunderstormDesc(riskInt(risk), cape.toInt().toString(), "%.0f".format(gusts), hasTs)

    private fun buildThunderstormRecommendation(risk: RiskLevel) = AppLocaleManager.strings.thunderstormRec(riskInt(risk))

    private fun buildLandslideDescription(risk: RiskLevel, precip: Double, antecedent: Double, hours: Int,
                                          slopeAngle: Double = 0.0, saturation: Double = 0.0, hasTerrain: Boolean = false) =
        AppLocaleManager.strings.landslideDesc(riskInt(risk), "%.0f".format(precip), "%.0f".format(antecedent), hours,
            slopeAngle, saturation, hasTerrain)

    private fun buildLandslideRecommendation(risk: RiskLevel) = AppLocaleManager.strings.landslideRec(riskInt(risk))

    private fun buildSubsidenceDescription(risk: RiskLevel, weeklyPrecip: Double, rainDays: Int) =
        AppLocaleManager.strings.subsidenceDesc(riskInt(risk), "%.0f".format(weeklyPrecip), rainDays)

    private fun buildSubsidenceRecommendation(risk: RiskLevel) = AppLocaleManager.strings.subsidenceRec(riskInt(risk))
}

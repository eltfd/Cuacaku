package com.weather.forecast.data.ai

import com.weather.forecast.data.model.*

/**
 * Weather Feature Extractor
 *
 * Mengekstrak dan menormalisasi 22 fitur meteorologi dari berbagai sumber data
 * menjadi vektor fitur [0, 1] yang siap diproses oleh neural network.
 *
 * Fitur diturunkan dari penelitian korelasi cuaca–bencana:
 * - Precipitation features (curah hujan, intensitas, durasi)
 * - Wind features (kecepatan, gust, wind shear)
 * - Atmospheric features (tekanan, kelembaban, CAPE, dew point)
 * - Marine features (gelombang, swell)
 * - Hydrological features (debit sungai)
 * - Soil features (kelembaban tanah, saturasi)
 * - Derived features (kode cuaca WMO, antecedent rainfall)
 *
 * Referensi normalisasi:
 * - WMO Guide to Meteorological Instruments and Methods of Observation (2018)
 * - BMKG Threshold Cuaca Ekstrem Indonesia
 */
object WeatherFeatureExtractor {

    const val FEATURE_COUNT = 22

    val FEATURE_NAMES = listOf(
        "precipTotal", "precipIntensity", "windSpeed", "windGusts", "windShear",
        "pressureLow", "pressureDrop", "humidity", "capeEnergy", "freezingLow",
        "cloudCover", "dewPointSpread", "waveHeight", "swellHeight", "dischargeRatio",
        "rainDuration", "antecedentRain", "consecutiveRain", "weatherSeverity", "temperatureHigh",
        "soilSaturation", "soilMoistureRate"
    )

    // ══════════════════════════════════════════════════
    //  Extraction for today (using current + hourly 24h)
    // ══════════════════════════════════════════════════

    fun extractForToday(
        weather: WeatherData?,
        waterData: WaterQualityData?
    ): WeatherFeatures {
        val hourly = weather?.hourly?.take(24) ?: emptyList()
        val current = weather?.current
        val daily = weather?.daily?.firstOrNull()
        val marine = waterData?.marine
        val flood = waterData?.flood
        val allDaily = weather?.daily ?: emptyList()
        val floodToday = flood?.dailyForecast?.firstOrNull()

        return extractCommon(
            hourly = hourly,
            precipTotal = daily?.precipitationSum ?: hourly.sumOf { it.precipitation },
            maxWind = maxOf(current?.windSpeed ?: 0.0, hourly.maxOfOrNull { it.windSpeed } ?: 0.0),
            maxGusts = maxOf(current?.windGusts ?: 0.0, daily?.windGustsMax ?: 0.0, hourly.maxOfOrNull { it.windGusts } ?: 0.0),
            maxCape = maxOf(current?.cape ?: 0.0, hourly.maxOfOrNull { it.cape } ?: 0.0),
            cloudCover = current?.cloudCover?.toDouble() ?: hourly.map { it.cloudCover.toDouble() }.average().takeIf { !it.isNaN() } ?: 50.0,
            temp = current?.temperature ?: hourly.firstOrNull()?.temperature ?: 25.0,
            dewPoint = current?.dewPoint ?: hourly.firstOrNull()?.dewPoint ?: 20.0,
            tempMax = daily?.temperatureMax ?: hourly.maxOfOrNull { it.temperature } ?: 25.0,
            waveHeight = marine?.current?.waveHeight ?: marine?.dailyForecast?.firstOrNull()?.waveHeightMax ?: 0.0,
            swellHeight = marine?.current?.swellWaveHeight ?: 0.0,
            dischargeRatio = if (floodToday != null && floodToday.dischargeMean > 0) floodToday.riverDischarge / floodToday.dischargeMean else 1.0,
            wmoCodes = listOfNotNull(current?.weatherCode) + hourly.map { it.weatherCode },
            antecedentDays = allDaily.take(3),
            hasWeather = weather != null,
            hasMarine = marine != null,
            hasFlood = flood != null,
            initPressure = current?.pressure ?: hourly.firstOrNull()?.pressure ?: 1013.0
        )
    }

    // ══════════════════════════════════════════════════
    //  Extraction for a specific day (7-day forecast)
    // ══════════════════════════════════════════════════

    fun extractForDay(
        dayIndex: Int,
        daily: DailyWeatherData,
        hourly: List<HourlyWeatherData>,
        marine: DailyMarineData?,
        flood: DailyFloodData?,
        allDaily: List<DailyWeatherData>
    ): WeatherFeatures {
        return extractCommon(
            hourly = hourly,
            precipTotal = daily.precipitationSum,
            maxWind = daily.windSpeedMax,
            maxGusts = daily.windGustsMax,
            maxCape = hourly.maxOfOrNull { it.cape } ?: 0.0,
            cloudCover = hourly.map { it.cloudCover.toDouble() }.average().takeIf { !it.isNaN() } ?: 50.0,
            temp = daily.temperatureMax,
            dewPoint = hourly.firstOrNull()?.dewPoint ?: 20.0,
            tempMax = daily.temperatureMax,
            waveHeight = marine?.waveHeightMax ?: 0.0,
            swellHeight = marine?.swellWaveHeightMax ?: 0.0,
            dischargeRatio = if (flood != null && flood.dischargeMean > 0) flood.riverDischarge / flood.dischargeMean else 1.0,
            wmoCodes = listOf(daily.weatherCode) + hourly.map { it.weatherCode },
            antecedentDays = allDaily.take(dayIndex + 1),
            hasWeather = true,
            hasMarine = marine != null,
            hasFlood = flood != null,
            initPressure = 1013.0
        )
    }

    // ══════════════════════════════════════════════════
    //  Shared extraction logic
    // ══════════════════════════════════════════════════

    private fun extractCommon(
        hourly: List<HourlyWeatherData>,
        precipTotal: Double, maxWind: Double, maxGusts: Double, maxCape: Double,
        cloudCover: Double, temp: Double, dewPoint: Double, tempMax: Double,
        waveHeight: Double, swellHeight: Double, dischargeRatio: Double,
        wmoCodes: List<Int>, antecedentDays: List<DailyWeatherData>,
        hasWeather: Boolean, hasMarine: Boolean, hasFlood: Boolean,
        initPressure: Double
    ): WeatherFeatures {
        val features = FloatArray(FEATURE_COUNT) { 0.5f }
        var dp = 0

        // [0] Precipitation total
        features[0] = norm(precipTotal, 0.0, 200.0); if (hasWeather) dp++
        // [1] Max rain intensity
        features[1] = norm(hourly.maxOfOrNull { it.rain + it.showers } ?: 0.0, 0.0, 50.0)
        if (hourly.isNotEmpty()) dp++
        // [2] Max wind speed
        features[2] = norm(maxWind, 0.0, 200.0); if (hasWeather) dp++
        // [3] Max gusts
        features[3] = norm(maxGusts, 0.0, 200.0); if (hasWeather) dp++
        // [4] Wind shear
        val avgWind = hourly.map { it.windSpeed }.average().takeIf { !it.isNaN() } ?: maxWind
        features[4] = norm(maxGusts - avgWind, 0.0, 80.0); if (hourly.isNotEmpty()) dp++
        // [5] Low pressure (inverted)
        val pressures = hourly.map { it.pressure }.filter { it > 0 }
        val minP = pressures.minOrNull() ?: initPressure
        features[5] = 1f - norm(minOf(initPressure, minP), 900.0, 1050.0); if (hasWeather) dp++
        // [6] Pressure drop
        features[6] = norm(if (pressures.size >= 2) (pressures.first() - pressures.last()).coerceAtLeast(0.0) else 0.0, 0.0, 30.0)
        if (pressures.size >= 2) dp++
        // [7] Humidity
        features[7] = norm(hourly.map { it.humidity.toDouble() }.average().takeIf { !it.isNaN() } ?: 50.0, 0.0, 100.0)
        if (hasWeather) dp++
        // [8] CAPE energy
        features[8] = norm(maxCape, 0.0, 5000.0); if (hasWeather) dp++
        // [9] Freezing level (inverted)
        val fz = hourly.filter { it.freezingLevelHeight > 0 }.minOfOrNull { it.freezingLevelHeight } ?: 5000.0
        features[9] = 1f - norm(fz, 0.0, 6000.0); if (hourly.any { it.freezingLevelHeight > 0 }) dp++
        // [10] Cloud cover
        features[10] = norm(cloudCover, 0.0, 100.0); if (hasWeather) dp++
        // [11] Dew point spread (inverted)
        features[11] = 1f - norm((temp - dewPoint).coerceAtLeast(0.0), 0.0, 30.0); if (hasWeather) dp++
        // [12] Wave height
        features[12] = if (hasMarine) { norm(waveHeight, 0.0, 10.0).also { dp++ } } else 0.5f
        // [13] Swell height
        features[13] = if (hasMarine) norm(swellHeight, 0.0, 5.0) else 0.5f
        // [14] Discharge ratio
        features[14] = if (hasFlood) { norm(dischargeRatio, 0.0, 10.0).also { dp++ } } else 0.5f
        // [15] Rain duration
        features[15] = norm(hourly.count { it.precipitation > 0.5 }.toDouble(), 0.0, 24.0)
        if (hourly.isNotEmpty()) dp++
        // [16] Antecedent rainfall
        features[16] = norm(antecedentDays.sumOf { it.precipitationSum }, 0.0, 300.0)
        if (antecedentDays.isNotEmpty()) dp++
        // [17] Consecutive rain days
        features[17] = norm(antecedentDays.count { it.precipitationSum > 5 }.toDouble(), 0.0, 7.0)
        if (antecedentDays.isNotEmpty()) dp++
        // [18] WMO severity
        features[18] = wmoCodes.maxOfOrNull { wmoSeverity(it) } ?: 0f; if (hasWeather) dp++
        // [19] High temperature
        features[19] = norm(tempMax, 20.0, 50.0); if (hasWeather) dp++
        // [20-21] Soil saturation & rate
        extractSoilFeatures(hourly, features) { dp++ }

        return WeatherFeatures(
            features = features,
            featureNames = FEATURE_NAMES,
            dataCompleteness = (dp.toDouble() / FEATURE_COUNT).coerceIn(0.0, 1.0),
            hasMarineData = hasMarine,
            hasFloodData = hasFlood,
            hasHourlyData = hourly.isNotEmpty()
        )
    }

    private inline fun extractSoilFeatures(hourly: List<HourlyWeatherData>, features: FloatArray, onData: () -> Unit) {
        val soilShallow = hourly.map { it.soilMoistureShallow }.filter { it > 0 }
        if (soilShallow.isEmpty()) return
        val avgShallow = soilShallow.average()
        val avgMedium = hourly.map { it.soilMoistureMedium }.filter { it > 0 }.average().takeIf { !it.isNaN() } ?: avgShallow
        val avgDeep = hourly.map { it.soilMoistureDeep }.filter { it > 0 }.average().takeIf { !it.isNaN() } ?: avgShallow
        features[20] = norm((avgShallow * 0.5 + avgMedium * 0.3 + avgDeep * 0.2) / 0.50, 0.0, 1.5)
        onData()
        if (soilShallow.size >= 6) {
            val firstHalf = soilShallow.take(soilShallow.size / 2).average()
            val secondHalf = soilShallow.drop(soilShallow.size / 2).average()
            features[21] = norm((secondHalf - firstHalf) / firstHalf.coerceAtLeast(0.01), -0.5, 1.0)
            onData()
        }
    }

    // ══════════════════════════════════════════════════
    //  Helpers
    // ══════════════════════════════════════════════════

    /** Min-max normalization ke [0, 1] */
    private fun norm(value: Double, min: Double, max: Double): Float {
        return ((value - min) / (max - min)).toFloat().coerceIn(0f, 1f)
    }

    /**
     * Konversi kode cuaca WMO ke skor keparahan [0, 1]
     * Berdasarkan WMO Code Table 4677
     */
    private fun wmoSeverity(code: Int): Float {
        return when (code) {
            95, 96, 99 -> 1.0f   // Thunderstorm + hail
            65, 67, 82 -> 0.8f   // Heavy rain / freezing
            63, 81, 86 -> 0.6f   // Moderate heavy precipitation
            61, 66, 80, 85 -> 0.4f // Light-moderate
            51, 53, 55, 71, 73, 75 -> 0.3f // Drizzle / snow
            45, 48 -> 0.2f       // Fog
            1, 2, 3 -> 0.05f     // Partly cloudy
            0 -> 0.0f            // Clear
            else -> 0.1f
        }
    }
}

/**
 * Vektor fitur hasil ekstraksi
 */
data class WeatherFeatures(
    /** 22 fitur ternormalisasi [0, 1] */
    val features: FloatArray,
    val featureNames: List<String>,
    /** Kelengkapan data (0–1), semakin tinggi = semakin akurat prediksi */
    val dataCompleteness: Double,
    val hasMarineData: Boolean,
    val hasFloodData: Boolean,
    val hasHourlyData: Boolean
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is WeatherFeatures) return false
        return features.contentEquals(other.features)
    }
    override fun hashCode() = features.contentHashCode()
}

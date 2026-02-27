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
    //  Ekstraksi untuk hari ini (menggunakan current + hourly 24h)
    // ══════════════════════════════════════════════════

    fun extractForToday(
        weather: WeatherData?,
        waterData: WaterQualityData?
    ): WeatherFeatures {
        val features = FloatArray(FEATURE_COUNT) { 0.5f } // Default: nilai netral
        var dataPoints = 0

        val hourly = weather?.hourly?.take(24) ?: emptyList()
        val current = weather?.current
        val daily = weather?.daily?.firstOrNull()
        val marine = waterData?.marine
        val flood = waterData?.flood

        // [0] Curah hujan total (mm) — norm by 200 mm
        val precipTotal = daily?.precipitationSum ?: hourly.sumOf { it.precipitation }
        features[0] = norm(precipTotal, 0.0, 200.0)
        if (weather != null) dataPoints++

        // [1] Intensitas hujan maks per jam (mm/h) — norm by 50
        val maxRain = hourly.maxOfOrNull { it.rain + it.showers } ?: 0.0
        features[1] = norm(maxRain, 0.0, 50.0)
        if (hourly.isNotEmpty()) dataPoints++

        // [2] Kecepatan angin maks (km/h) — norm by 200
        val maxWind = maxOf(
            current?.windSpeed ?: 0.0,
            hourly.maxOfOrNull { it.windSpeed } ?: 0.0
        )
        features[2] = norm(maxWind, 0.0, 200.0)
        if (weather != null) dataPoints++

        // [3] Gust maks (km/h) — norm by 200
        val maxGusts = maxOf(
            current?.windGusts ?: 0.0,
            daily?.windGustsMax ?: 0.0,
            hourly.maxOfOrNull { it.windGusts } ?: 0.0
        )
        features[3] = norm(maxGusts, 0.0, 200.0)
        if (weather != null) dataPoints++

        // [4] Wind shear (gust - rata2 wind) — norm by 80
        val avgWind = hourly.map { it.windSpeed }.average().takeIf { !it.isNaN() } ?: maxWind
        features[4] = norm(maxGusts - avgWind, 0.0, 80.0)
        if (hourly.isNotEmpty()) dataPoints++

        // [5] Tekanan rendah (inverted: lebih rendah = skor lebih tinggi) — range 900-1050
        val pressure = current?.pressure ?: hourly.firstOrNull()?.pressure ?: 1013.0
        val minPressure = hourly.map { it.pressure }.filter { it > 0 }.minOrNull() ?: pressure
        features[5] = 1f - norm(minOf(pressure, minPressure), 900.0, 1050.0)
        if (weather != null) dataPoints++

        // [6] Penurunan tekanan (rate drop dalam 24h) — norm by 30 hPa
        val pressures = hourly.map { it.pressure }.filter { it > 0 }
        val pressureDrop = if (pressures.size >= 2) {
            (pressures.first() - pressures.last()).coerceAtLeast(0.0)
        } else 0.0
        features[6] = norm(pressureDrop, 0.0, 30.0)
        if (pressures.size >= 2) dataPoints++

        // [7] Kelembaban rata-rata — norm by 100
        val avgHumidity = hourly.map { it.humidity.toDouble() }.average()
            .takeIf { !it.isNaN() }
            ?: current?.humidity?.toDouble() ?: 50.0
        features[7] = norm(avgHumidity, 0.0, 100.0)
        if (weather != null) dataPoints++

        // [8] CAPE maks (J/kg) — norm by 5000
        val maxCape = maxOf(
            current?.cape ?: 0.0,
            hourly.maxOfOrNull { it.cape } ?: 0.0
        )
        features[8] = norm(maxCape, 0.0, 5000.0)
        if (weather != null) dataPoints++

        // [9] Freezing level rendah (inverted) — norm by 6000 m
        val minFreezing = hourly.filter { it.freezingLevelHeight > 0 }
            .minOfOrNull { it.freezingLevelHeight } ?: 5000.0
        features[9] = 1f - norm(minFreezing, 0.0, 6000.0)
        if (hourly.any { it.freezingLevelHeight > 0 }) dataPoints++

        // [10] Tutupan awan — norm by 100
        val avgCloud = current?.cloudCover?.toDouble()
            ?: hourly.map { it.humidity.toDouble() }.average().takeIf { !it.isNaN() } ?: 50.0
        features[10] = norm(avgCloud, 0.0, 100.0)
        if (weather != null) dataPoints++

        // [11] Dew point spread (inverted: semakin kecil = semakin lembab) — norm by 30
        val temp = current?.temperature ?: hourly.firstOrNull()?.temperature ?: 25.0
        val dewPoint = current?.dewPoint ?: hourly.firstOrNull()?.dewPoint ?: 20.0
        features[11] = 1f - norm((temp - dewPoint).coerceAtLeast(0.0), 0.0, 30.0)
        if (weather != null) dataPoints++

        // [12] Tinggi gelombang (m) — norm by 10
        val waveHeight = marine?.current?.waveHeight
            ?: marine?.dailyForecast?.firstOrNull()?.waveHeightMax ?: 0.0
        features[12] = if (marine != null) norm(waveHeight, 0.0, 10.0) else 0.5f
        if (marine != null) dataPoints++

        // [13] Swell height (m) — norm by 5
        val swellHeight = marine?.current?.swellWaveHeight ?: 0.0
        features[13] = if (marine != null) norm(swellHeight, 0.0, 5.0) else 0.5f
        if (marine != null) dataPoints++

        // [14] Rasio debit sungai / rata-rata — norm by 10
        val floodToday = flood?.dailyForecast?.firstOrNull()
        val dischargeRatio = if (floodToday != null && floodToday.dischargeMean > 0) {
            floodToday.riverDischarge / floodToday.dischargeMean
        } else 1.0
        features[14] = if (flood != null) norm(dischargeRatio, 0.0, 10.0) else 0.5f
        if (flood != null) dataPoints++

        // [15] Durasi hujan (jam hujan / 24) — norm by 24
        val rainHours = hourly.count { it.precipitation > 0.5 }
        features[15] = norm(rainHours.toDouble(), 0.0, 24.0)
        if (hourly.isNotEmpty()) dataPoints++

        // [16] Antecedent rainfall 3 hari (mm) — norm by 300
        val allDaily = weather?.daily ?: emptyList()
        val antecedent = allDaily.take(3).sumOf { it.precipitationSum }
        features[16] = norm(antecedent, 0.0, 300.0)
        if (allDaily.isNotEmpty()) dataPoints++

        // [17] Hari hujan berturut — norm by 7
        val consecDays = allDaily.takeWhile { it.precipitationSum > 5 }.size
        features[17] = norm(consecDays.toDouble(), 0.0, 7.0)
        if (allDaily.isNotEmpty()) dataPoints++

        // [18] Keparahan kode WMO — skala 0-1
        val severity = maxOf(
            wmoSeverity(current?.weatherCode ?: 0),
            hourly.maxOfOrNull { wmoSeverity(it.weatherCode) } ?: 0f
        )
        features[18] = severity
        if (weather != null) dataPoints++

        // [19] Suhu tinggi — norm 20-50°C
        val tempMax = daily?.temperatureMax ?: hourly.maxOfOrNull { it.temperature } ?: 25.0
        features[19] = norm(tempMax, 20.0, 50.0)
        if (weather != null) dataPoints++

        // [20] Kejenuhan tanah (soil saturation index) — rata-rata kelembaban tanah / titik jenuh
        val soilShallow = hourly.map { it.soilMoistureShallow }.filter { it > 0 }
        val soilMedium = hourly.map { it.soilMoistureMedium }.filter { it > 0 }
        val soilDeep = hourly.map { it.soilMoistureDeep }.filter { it > 0 }
        val hasSoilData = soilShallow.isNotEmpty()
        if (hasSoilData) {
            val avgShallow = soilShallow.average()
            val avgMedium = soilMedium.average().takeIf { !it.isNaN() } ?: avgShallow
            val avgDeep = soilDeep.average().takeIf { !it.isNaN() } ?: avgShallow
            // Weighted: shallow 50%, medium 30%, deep 20% — divisi titik jenuh 0.50 m³/m³
            val saturation = (avgShallow * 0.5 + avgMedium * 0.3 + avgDeep * 0.2) / 0.50
            features[20] = norm(saturation, 0.0, 1.5)
            dataPoints++
        }

        // [21] Laju perubahan kelembaban tanah (rising = semakin jenuh)
        if (hasSoilData && soilShallow.size >= 6) {
            val firstHalf = soilShallow.take(soilShallow.size / 2).average()
            val secondHalf = soilShallow.drop(soilShallow.size / 2).average()
            val rate = (secondHalf - firstHalf) / firstHalf.coerceAtLeast(0.01)
            // rate > 0 = tanah semakin basah, norm by 100% increase
            features[21] = norm(rate, -0.5, 1.0)
            dataPoints++
        }

        return WeatherFeatures(
            features = features,
            featureNames = FEATURE_NAMES,
            dataCompleteness = (dataPoints.toDouble() / FEATURE_COUNT).coerceIn(0.0, 1.0),
            hasMarineData = marine != null,
            hasFloodData = flood != null,
            hasHourlyData = hourly.isNotEmpty()
        )
    }

    // ══════════════════════════════════════════════════
    //  Ekstraksi untuk per-hari (7 day forecast)
    // ══════════════════════════════════════════════════

    fun extractForDay(
        dayIndex: Int,
        daily: DailyWeatherData,
        hourly: List<HourlyWeatherData>,
        marine: DailyMarineData?,
        flood: DailyFloodData?,
        allDaily: List<DailyWeatherData>
    ): WeatherFeatures {
        val features = FloatArray(FEATURE_COUNT) { 0.5f }
        var dataPoints = 0

        features[0] = norm(daily.precipitationSum, 0.0, 200.0); dataPoints++
        features[1] = norm(hourly.maxOfOrNull { it.rain + it.showers } ?: 0.0, 0.0, 50.0)
        if (hourly.isNotEmpty()) dataPoints++
        features[2] = norm(daily.windSpeedMax, 0.0, 200.0); dataPoints++
        features[3] = norm(daily.windGustsMax, 0.0, 200.0); dataPoints++

        val avgW = hourly.map { it.windSpeed }.average().takeIf { !it.isNaN() } ?: daily.windSpeedMax
        features[4] = norm(daily.windGustsMax - avgW, 0.0, 80.0)
        if (hourly.isNotEmpty()) dataPoints++

        val minP = hourly.map { it.pressure }.filter { it > 0 }.minOrNull() ?: 1013.0
        features[5] = 1f - norm(minP, 900.0, 1050.0); dataPoints++

        val pl = hourly.map { it.pressure }.filter { it > 0 }
        features[6] = norm(if (pl.size >= 2) (pl.first() - pl.last()).coerceAtLeast(0.0) else 0.0, 0.0, 30.0)
        if (pl.size >= 2) dataPoints++

        features[7] = norm(hourly.map { it.humidity.toDouble() }.average().takeIf { !it.isNaN() } ?: 50.0, 0.0, 100.0); dataPoints++
        features[8] = norm(hourly.maxOfOrNull { it.cape } ?: 0.0, 0.0, 5000.0); dataPoints++

        val fz = hourly.filter { it.freezingLevelHeight > 0 }.minOfOrNull { it.freezingLevelHeight } ?: 5000.0
        features[9] = 1f - norm(fz, 0.0, 6000.0); dataPoints++

        features[10] = norm(hourly.map { it.humidity.toDouble() }.average().takeIf { !it.isNaN() } ?: 50.0, 0.0, 100.0)
        features[11] = 1f - norm((daily.temperatureMax - (hourly.firstOrNull()?.dewPoint ?: 20.0)).coerceAtLeast(0.0), 0.0, 30.0)

        features[12] = if (marine != null) { norm(marine.waveHeightMax, 0.0, 10.0).also { dataPoints++ } } else 0.5f
        features[13] = if (marine != null) norm(marine.swellWaveHeightMax, 0.0, 5.0) else 0.5f

        val dr = if (flood != null && flood.dischargeMean > 0) flood.riverDischarge / flood.dischargeMean else 1.0
        features[14] = if (flood != null) { norm(dr, 0.0, 10.0).also { dataPoints++ } } else 0.5f

        features[15] = norm(hourly.count { it.precipitation > 0.5 }.toDouble(), 0.0, 24.0)
        if (hourly.isNotEmpty()) dataPoints++

        val ante = allDaily.take(dayIndex + 1).sumOf { it.precipitationSum }
        features[16] = norm(ante, 0.0, 300.0); dataPoints++

        features[17] = norm(allDaily.take(dayIndex + 1).count { it.precipitationSum > 5 }.toDouble(), 0.0, 7.0); dataPoints++

        val sev = maxOf(
            wmoSeverity(daily.weatherCode),
            hourly.maxOfOrNull { wmoSeverity(it.weatherCode) } ?: 0f
        )
        features[18] = sev; dataPoints++

        features[19] = norm(daily.temperatureMax, 20.0, 50.0); dataPoints++

        // [20] Kejenuhan tanah (soil saturation)
        val soilShallow = hourly.map { it.soilMoistureShallow }.filter { it > 0 }
        val soilMedium = hourly.map { it.soilMoistureMedium }.filter { it > 0 }
        val soilDeep = hourly.map { it.soilMoistureDeep }.filter { it > 0 }
        val hasSoilData = soilShallow.isNotEmpty()
        if (hasSoilData) {
            val avgShallow = soilShallow.average()
            val avgMedium = soilMedium.average().takeIf { !it.isNaN() } ?: avgShallow
            val avgDeep = soilDeep.average().takeIf { !it.isNaN() } ?: avgShallow
            val saturation = (avgShallow * 0.5 + avgMedium * 0.3 + avgDeep * 0.2) / 0.50
            features[20] = norm(saturation, 0.0, 1.5)
            dataPoints++
        }

        // [21] Laju perubahan kelembaban tanah
        if (hasSoilData && soilShallow.size >= 6) {
            val firstHalf = soilShallow.take(soilShallow.size / 2).average()
            val secondHalf = soilShallow.drop(soilShallow.size / 2).average()
            val rate = (secondHalf - firstHalf) / firstHalf.coerceAtLeast(0.01)
            features[21] = norm(rate, -0.5, 1.0)
            dataPoints++
        }

        return WeatherFeatures(
            features = features,
            featureNames = FEATURE_NAMES,
            dataCompleteness = (dataPoints.toDouble() / FEATURE_COUNT).coerceIn(0.0, 1.0),
            hasMarineData = marine != null,
            hasFloodData = flood != null,
            hasHourlyData = hourly.isNotEmpty()
        )
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

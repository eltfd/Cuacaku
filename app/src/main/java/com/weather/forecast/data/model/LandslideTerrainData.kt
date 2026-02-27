package com.weather.forecast.data.model

/**
 * Landslide Terrain Analysis Data Models
 *
 * Menyimpan data analisis medan/topografi untuk prediksi longsor:
 * - Kemiringan lereng (slope gradient) dari data elevasi
 * - Kelembaban tanah (soil moisture) dari Open-Meteo
 * - Suhu tanah dari Open-Meteo
 * - Indeks vegetasi proxy dari kelembaban + suhu tanah
 * - Curah hujan kumulatif dan intensitas
 *
 * Data Sources:
 * - Open-Elevation API: elevasi → kemiringan
 * - Open-Meteo hourly: soil_moisture, soil_temperature
 * - Weather data: curah hujan, kelembaban udara
 */

/**
 * Data terrain lengkap untuk analisis longsor
 */
data class LandslideTerrainData(
    /** Elevasi lokasi user (m dpl) */
    val elevation: Double,
    /** Kemiringan lereng maksimum dari 4 arah (derajat, 0-90) */
    val slopeAngle: Double,
    /** Kategori kemiringan */
    val slopeCategory: SlopeCategory,
    /** Kelembaban tanah rata-rata 0-7cm (m³/m³) */
    val soilMoistureShallow: Double,
    /** Kelembaban tanah rata-rata 7-28cm (m³/m³) */
    val soilMoistureMedium: Double,
    /** Kelembaban tanah rata-rata 28-100cm (m³/m³) */
    val soilMoistureDeep: Double,
    /** Suhu tanah permukaan (°C) */
    val soilTemperature: Double,
    /** Indeks kejenuhan tanah (0.0 – 1.0) — derived dari soil moisture */
    val soilSaturationIndex: Double,
    /** Indeks vegetasi proxy (0.0 – 1.0) — estimasi dari NDVI proxy */
    val vegetationIndex: Double,
    /** Curah hujan kumulatif hari ini (mm) */
    val todayPrecipitation: Double,
    /** Curah hujan anteseden 3 hari (mm) */
    val antecedentRainfall: Double,
    /** Intensitas hujan maksimum per jam (mm/h) */
    val maxRainfallIntensity: Double,
    /** Durasi hujan terus-menerus (jam) */
    val continuousRainHours: Int,
    /** Detail elevasi grid 5 titik */
    val elevationGrid: List<ElevationPoint> = emptyList()
)

/**
 * Titik elevasi individual (untuk visualisasi)
 */
data class ElevationPoint(
    val label: String,  // "N", "S", "E", "W", "Center"
    val latitude: Double,
    val longitude: Double,
    val elevation: Double
)

/**
 * Kategori kemiringan lereng berdasarkan klasifikasi geomorfologi
 *
 * Referensi: Van Zuidam (1985) — Geomorphological Classification
 * dan PUSLITTANAK (2004) — Klasifikasi Lereng Indonesia
 */
enum class SlopeCategory(
    val label: String,
    val labelId: String,
    val minDegree: Double,
    val maxDegree: Double,
    val riskFactor: Double,  // 0.0 – 1.0 kontribusi ke risiko longsor
    val colorHex: Long
) {
    /** 0-2° — Datar/hampir datar, risiko longsor sangat rendah */
    FLAT(
        label = "Flat",
        labelId = "Datar",
        minDegree = 0.0, maxDegree = 2.0,
        riskFactor = 0.0,
        colorHex = 0xFF4CAF50
    ),

    /** 2-5° — Agak landai, risiko sangat rendah */
    GENTLE(
        label = "Gentle Slope",
        labelId = "Landai",
        minDegree = 2.0, maxDegree = 5.0,
        riskFactor = 0.05,
        colorHex = 0xFF66BB6A
    ),

    /** 5-15° — Bergelombang/miring, risiko rendah */
    MODERATE(
        label = "Moderate Slope",
        labelId = "Agak Curam",
        minDegree = 5.0, maxDegree = 15.0,
        riskFactor = 0.2,
        colorHex = 0xFFFFA726
    ),

    /** 15-30° — Curam, zona rawan longsor */
    STEEP(
        label = "Steep",
        labelId = "Curam",
        minDegree = 15.0, maxDegree = 30.0,
        riskFactor = 0.6,
        colorHex = 0xFFFF7043
    ),

    /** 30-45° — Sangat curam, zona bahaya longsor */
    VERY_STEEP(
        label = "Very Steep",
        labelId = "Sangat Curam",
        minDegree = 30.0, maxDegree = 45.0,
        riskFactor = 0.85,
        colorHex = 0xFFE53935
    ),

    /** >45° — Tebing/jurang, bahaya longsor/runtuh ekstrem */
    CLIFF(
        label = "Cliff/Escarpment",
        labelId = "Tebing/Jurang",
        minDegree = 45.0, maxDegree = 90.0,
        riskFactor = 1.0,
        colorHex = 0xFFB71C1C
    );

    companion object {
        fun fromAngle(degrees: Double): SlopeCategory {
            return entries.lastOrNull { degrees >= it.minDegree } ?: FLAT
        }
    }
}

/**
 * Indeks kejenuhan tanah — mengestimasi seberapa jenuh tanah
 * berdasarkan kelembaban tanah di berbagai kedalaman.
 *
 * Metode: rata-rata terbobot yang memprioritaskan lapisan dangkal
 * (yang paling relevan untuk longsor permukaan).
 *
 * Referensi:
 * - Field capacity tanah tipikal: 0.25 – 0.40 m³/m³
 * - Saturation point: 0.45 – 0.55 m³/m³
 * - Wilting point: 0.05 – 0.15 m³/m³
 */
object SoilSaturationCalculator {
    // Approximate saturation point for typical soils (m³/m³)
    private const val SATURATION_POINT = 0.50

    /**
     * Calculate soil saturation index (0.0 – 1.0).
     * weighted average across depths, normalized to saturation point.
     *
     * @param shallow 0-7cm moisture (m³/m³)
     * @param medium 7-28cm moisture (m³/m³)
     * @param deep 28-100cm moisture (m³/m³)
     */
    fun calculate(shallow: Double, medium: Double, deep: Double): Double {
        // Weighted: shallow 50%, medium 30%, deep 20%
        // (shallow matters most for surface landslides)
        val weighted = shallow * 0.50 + medium * 0.30 + deep * 0.20
        return (weighted / SATURATION_POINT).coerceIn(0.0, 1.0)
    }
}

/**
 * Vegetasi proxy estimator — estimasi tutupan vegetasi dari kondisi tanah
 *
 * Tanpa data satelit NDVI langsung, kita menggunakan proxy:
 * - Tanah yang sangat basah DAN hangat → cenderung ada vegetasi
 * - Tanah sangat kering → sedikit vegetasi
 * - Tanah terlalu jenuh air → mungkin area rawa/terbuka
 *
 * Ini adalah estimasi kasar — confidence rendah (~0.3)
 */
object VegetationProxyEstimator {
    /**
     * Estimate vegetation cover index (0.0 – 1.0).
     *
     * @param soilMoisture Average shallow soil moisture (m³/m³)
     * @param soilTemp Surface soil temperature (°C)
     * @param humidity Average air humidity (%)
     */
    fun estimate(soilMoisture: Double, soilTemp: Double, humidity: Double): Double {
        // Optimal growing conditions: moderate moisture, warm, humid
        val moistureScore = when {
            soilMoisture in 0.15..0.35 -> 1.0  // Optimal range
            soilMoisture in 0.10..0.15 || soilMoisture in 0.35..0.45 -> 0.6
            soilMoisture < 0.10 -> 0.2  // Too dry
            else -> 0.3  // Waterlogged
        }

        val tempScore = when {
            soilTemp in 15.0..30.0 -> 1.0   // Optimal growing temp
            soilTemp in 10.0..15.0 || soilTemp in 30.0..35.0 -> 0.6
            soilTemp < 5.0 -> 0.1
            else -> 0.3
        }

        val humidityScore = when {
            humidity > 70 -> 0.8
            humidity > 50 -> 0.6
            else -> 0.3
        }

        return (moistureScore * 0.4 + tempScore * 0.3 + humidityScore * 0.3).coerceIn(0.0, 1.0)
    }
}

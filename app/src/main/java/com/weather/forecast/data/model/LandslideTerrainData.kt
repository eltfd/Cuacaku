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
 * - SoilGrids ISRIC API: tipe tanah (clay, sand, silt, SOC)
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
    val elevationGrid: List<ElevationPoint> = emptyList(),
    // ── Soil Type dari SoilGrids ISRIC ──
    /** Kadar lempung rata-rata 0-30cm (g/kg, 0-1000) — null jika belum di-fetch */
    val clayContent: Double? = null,
    /** Kadar pasir rata-rata 0-30cm (g/kg, 0-1000) */
    val sandContent: Double? = null,
    /** Kadar debu rata-rata 0-30cm (g/kg, 0-1000) */
    val siltContent: Double? = null,
    /** Karbon organik tanah rata-rata 0-30cm (dg/kg) */
    val soilOrganicCarbon: Double? = null,
    /** Indeks stabilitas tanah (0.0-1.0) — derived, 0=sangat tidak stabil */
    val soilStabilityIndex: Double? = null
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

/**
 * Soil Stability Calculator — menghitung indeks stabilitas tanah dari komposisi
 *
 * Berdasarkan klasifikasi USDA Soil Texture Triangle:
 * - Tanah lempung (clay-rich): menahan air lebih lama → jenuh → licin → longsor
 * - Tanah berpasir (sandy): drainase cepat → kohesi rendah → ambles jika tergerus
 * - Tanah berimbang (loam): paling stabil → drainase baik + kohesi cukup
 *
 * Indeks stabilitas:
 *   0.0 = sangat tidak stabil (pure clay atau pure sand)
 *   1.0 = sangat stabil (balanced loam dengan SOC tinggi)
 *
 * Referensi:
 * - USDA Soil Survey Manual (2017)
 * - Hengl et al. (2017): SoilGrids250m
 * - PVMBG (2019): Korelasi tipe tanah–longsor Indonesia
 */
object SoilStabilityCalculator {

    /**
     * Menghitung indeks stabilitas tanah.
     *
     * @param clay Clay content (g/kg, 0-1000)
     * @param sand Sand content (g/kg, 0-1000)
     * @param silt Silt content (g/kg, 0-1000)
     * @param soc  Soil Organic Carbon (dg/kg, optional)
     * @return Stability index 0.0 (tidak stabil) – 1.0 (sangat stabil)
     */
    fun calculate(clay: Double, sand: Double, silt: Double, soc: Double = 0.0): Double {
        val total = clay + sand + silt
        if (total <= 0) return 0.5 // Unknown

        // Normalize to proportions (0-1)
        val clayPct = clay / total
        val sandPct = sand / total
        val siltPct = silt / total

        // Base stability from texture balance
        // Ideal: ~20% clay, ~40% sand, ~40% silt (loam)
        // Deviation from ideal reduces stability
        val clayPenalty = when {
            clayPct > 0.60 -> 0.15  // Heavy clay → very slippery when wet
            clayPct > 0.40 -> 0.35  // High clay → still problematic
            clayPct in 0.15..0.30 -> 0.85  // Optimal range
            clayPct < 0.10 -> 0.50  // Too little clay → no cohesion
            else -> 0.65
        }

        val sandPenalty = when {
            sandPct > 0.70 -> 0.20  // Very sandy → no cohesion, erosion
            sandPct > 0.50 -> 0.40  // Sandy → loosely packed
            sandPct in 0.30..0.50 -> 0.80  // Good drainage + some structure
            sandPct < 0.15 -> 0.50  // Too little sand → poor drainage
            else -> 0.70
        }

        val siltPenalty = when {
            siltPct > 0.60 -> 0.30  // High silt → easily eroded
            siltPct in 0.30..0.50 -> 0.80  // Good range
            else -> 0.60
        }

        // SOC bonus: organic matter improves soil structure
        // Typical SOC range: 0-500 dg/kg (0-50 g/kg)
        val socBonus = when {
            soc > 200 -> 0.15   // Very high organic → strong structure
            soc > 100 -> 0.10   // High organic
            soc > 50  -> 0.05   // Moderate organic
            else -> 0.0
        }

        return (clayPenalty * 0.40 + sandPenalty * 0.30 + siltPenalty * 0.30 + socBonus)
            .coerceIn(0.0, 1.0)
    }

    /**
     * Klasifikasi tipe tanah tekstural berdasarkan USDA Soil Texture Triangle.
     *
     * @param clay Clay content (g/kg)
     * @param sand Sand content (g/kg)
     * @param silt Silt content (g/kg)
     * @return Nama klasifikasi tanah (e.g. "Clay Loam", "Sandy", "Silty Clay")
     */
    fun classifyTexture(clay: Double, sand: Double, silt: Double): String {
        val total = clay + sand + silt
        if (total <= 0) return "Unknown"

        val c = clay / total * 100
        val s = sand / total * 100

        return when {
            c >= 40 && s <= 45 -> "Clay"
            c >= 27 && s <= 20 -> "Silty Clay"
            c >= 35 && s >= 45 -> "Sandy Clay"
            c >= 27 && s in 20.0..45.0 -> "Clay Loam"
            c in 12.0..27.0 && s < 50 -> "Loam"
            c < 12 && s >= 85 -> "Sand"
            c < 12 && s >= 70 -> "Loamy Sand"
            c < 27 && s >= 50 -> "Sandy Loam"
            c < 12 && s < 50 -> "Silt Loam"
            else -> "Loam"
        }
    }
}

package com.weather.forecast.data.model

/**
 * UI-ready Water Quality data models
 *
 * Menggabungkan data dari Marine API (laut) dan Flood API (sungai).
 */

/**
 * Data kualitas air lengkap
 */
data class WaterQualityData(
    val marine: MarineData?,
    val flood: FloodData?,
    val lastUpdated: Long = System.currentTimeMillis()
)

// ===================== MARINE (Laut) =====================

/**
 * Data kondisi laut (UI-ready)
 */
data class MarineData(
    val current: CurrentMarineData?,
    val hourlyForecast: List<HourlyMarineData>,
    val dailyForecast: List<DailyMarineData>
)

/**
 * Kondisi laut saat ini
 */
data class CurrentMarineData(
    val waveHeight: Double,
    val waveDirection: Int,
    val wavePeriod: Double,
    val swellWaveHeight: Double,
    val swellWaveDirection: Int,
    val swellWavePeriod: Double,
    val seaCondition: SeaCondition
) {
    val waveHeightFormatted: String
        get() = "%.1f m".format(waveHeight)

    val wavePeriodFormatted: String
        get() = "%.1f s".format(wavePeriod)

    val waveDirectionText: String
        get() = degreesToDirection(waveDirection)

    val swellHeightFormatted: String
        get() = "%.1f m".format(swellWaveHeight)
}

/**
 * Prakiraan laut per jam
 */
data class HourlyMarineData(
    val time: String,
    val hour: String,
    val waveHeight: Double,
    val waveDirection: Int,
    val wavePeriod: Double,
    val swellWaveHeight: Double,
    val seaCondition: SeaCondition
) {
    val waveHeightFormatted: String
        get() = "%.1f m".format(waveHeight)
}

/**
 * Prakiraan laut harian
 */
data class DailyMarineData(
    val date: String,
    val dayName: String,
    val waveHeightMax: Double,
    val waveDirectionDominant: Int,
    val wavePeriodMax: Double,
    val swellWaveHeightMax: Double,
    val seaCondition: SeaCondition,
    /** Prakiraan per jam */
    val hourlyForecasts: List<HourlyMarineData> = emptyList()
) {
    val waveHeightMaxFormatted: String
        get() = "%.1f m".format(waveHeightMax)

    val waveDirectionText: String
        get() = degreesToDirection(waveDirectionDominant)
}

// ===================== FLOOD (Sungai) =====================

/**
 * Data debit sungai (UI-ready)
 */
data class FloodData(
    val dailyForecast: List<DailyFloodData>
)

/**
 * Data debit sungai harian
 */
data class DailyFloodData(
    val date: String,
    val dayName: String,
    val riverDischarge: Double,
    val dischargeMean: Double,
    val dischargeMax: Double,
    val dischargeMin: Double,
    val floodRisk: FloodRisk
) {
    val dischargeFormatted: String
        get() = "%.1f m³/s".format(riverDischarge)

    val dischargeMeanFormatted: String
        get() = "%.1f m³/s".format(dischargeMean)
}

// ===================== ENUMS =====================

/**
 * Kondisi laut berdasarkan tinggi gelombang (Douglas Sea Scale)
 */
enum class SeaCondition(
    val label: String,
    val labelId: String,
    val description: String,
    val minHeight: Double,
    val maxHeight: Double,
    val colorHex: Long
) {
    CALM(
        label = "Calm",
        labelId = "Tenang",
        description = "Gelombang < 0.1m",
        minHeight = 0.0,
        maxHeight = 0.1,
        colorHex = 0xFF4CAF50
    ),
    SMOOTH(
        label = "Smooth",
        labelId = "Halus",
        description = "Gelombang 0.1-0.5m",
        minHeight = 0.1,
        maxHeight = 0.5,
        colorHex = 0xFF66BB6A
    ),
    SLIGHT(
        label = "Slight",
        labelId = "Sedikit Bergelombang",
        description = "Gelombang 0.5-1.25m",
        minHeight = 0.5,
        maxHeight = 1.25,
        colorHex = 0xFFFFC107
    ),
    MODERATE(
        label = "Moderate",
        labelId = "Sedang",
        description = "Gelombang 1.25-2.5m",
        minHeight = 1.25,
        maxHeight = 2.5,
        colorHex = 0xFFFF9800
    ),
    ROUGH(
        label = "Rough",
        labelId = "Kasar",
        description = "Gelombang 2.5-4m",
        minHeight = 2.5,
        maxHeight = 4.0,
        colorHex = 0xFFF44336
    ),
    VERY_ROUGH(
        label = "Very Rough",
        labelId = "Sangat Kasar",
        description = "Gelombang 4-6m",
        minHeight = 4.0,
        maxHeight = 6.0,
        colorHex = 0xFFD32F2F
    ),
    HIGH(
        label = "High",
        labelId = "Tinggi",
        description = "Gelombang 6-9m",
        minHeight = 6.0,
        maxHeight = 9.0,
        colorHex = 0xFF9C27B0
    ),
    VERY_HIGH(
        label = "Very High",
        labelId = "Sangat Tinggi",
        description = "Gelombang > 9m",
        minHeight = 9.0,
        maxHeight = 100.0,
        colorHex = 0xFF880E4F
    );

    companion object {
        fun fromWaveHeight(height: Double): SeaCondition {
            return entries.find { height >= it.minHeight && height < it.maxHeight } ?: VERY_HIGH
        }
    }
}

/**
 * Risiko banjir berdasarkan debit sungai
 * (threshold relatif terhadap rata-rata lokal)
 */
enum class FloodRisk(
    val label: String,
    val labelId: String,
    val colorHex: Long
) {
    LOW(
        label = "Low",
        labelId = "Rendah",
        colorHex = 0xFF4CAF50
    ),
    MODERATE(
        label = "Moderate",
        labelId = "Sedang",
        colorHex = 0xFFFF9800
    ),
    HIGH(
        label = "High",
        labelId = "Tinggi",
        colorHex = 0xFFF44336
    ),
    VERY_HIGH(
        label = "Very High",
        labelId = "Sangat Tinggi",
        colorHex = 0xFF880E4F
    );

    companion object {
        /**
         * Estimasi risiko berdasarkan rasio debit saat ini vs rata-rata
         */
        fun fromDischargeRatio(current: Double, mean: Double): FloodRisk {
            if (mean <= 0) return LOW
            val ratio = current / mean
            return when {
                ratio < 1.5 -> LOW
                ratio < 3.0 -> MODERATE
                ratio < 5.0 -> HIGH
                else -> VERY_HIGH
            }
        }
    }
}

// ===================== HELPER =====================

/**
 * Convert derajat ke arah mata angin
 */
fun degreesToDirection(degrees: Int): String {
    return when {
        degrees in 0..22 || degrees in 338..360 -> "U"
        degrees in 23..67 -> "TL"
        degrees in 68..112 -> "T"
        degrees in 113..157 -> "TG"
        degrees in 158..202 -> "S"
        degrees in 203..247 -> "BD"
        degrees in 248..292 -> "B"
        degrees in 293..337 -> "BL"
        else -> "-"
    }
}

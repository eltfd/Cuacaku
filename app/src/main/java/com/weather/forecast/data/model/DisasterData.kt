package com.weather.forecast.data.model

/**
 * UI-ready Disaster Prediction data models
 *
 * Analisis potensi bencana berbasis AI menggunakan multi-faktor:
 * - Data cuaca (CAPE, angin, hujan, tekanan, kelembaban)
 * - Data laut (gelombang, swell) → banjir rob
 * - Data sungai (debit) → banjir bandang
 *
 * Engine menggunakan fuzzy-logic scoring dengan bobot per faktor.
 */

/**
 * Container utama prakiraan bencana
 */
data class DisasterForecast(
    /** Prakiraan bencana hari ini */
    val todayPredictions: List<DisasterPrediction>,
    /** Prakiraan bencana 7 hari ke depan */
    val weeklyPredictions: List<DailyDisasterSummary>,
    /** Alert tertinggi saat ini */
    val highestAlert: DisasterType? = null,
    /** Level risiko tertinggi */
    val overallRiskLevel: RiskLevel = RiskLevel.LOW,
    /** Ringkasan narasi AI */
    val aiSummary: String = "",
    /** Timestamp */
    val lastUpdated: Long = System.currentTimeMillis()
)

/**
 * Prediksi untuk satu jenis bencana
 */
data class DisasterPrediction(
    val type: DisasterType,
    /** 0.0 – 1.0 (skor risiko terbobot) */
    val riskScore: Double,
    val riskLevel: RiskLevel,
    /** Confidence 0.0 – 1.0, tergantung kelengkapan data */
    val confidence: Double,
    /** Faktor-faktor yang berkontribusi */
    val factors: List<ContributingFactor>,
    /** Deskripsi narasi AI */
    val description: String,
    /** Rekomendasi tindakan */
    val recommendation: String
)

/**
 * Ringkasan harian untuk prakiraan 7 hari
 */
data class DailyDisasterSummary(
    val date: String,
    val dayName: String,
    /** Daftar prediksi bencana pada hari ini */
    val predictions: List<DisasterPrediction>,
    /** Level risiko tertinggi hari ini */
    val highestRisk: RiskLevel = RiskLevel.LOW
)

/**
 * Faktor yang berkontribusi ke prediksi bencana
 */
data class ContributingFactor(
    val name: String,
    val value: String,
    /** Kontribusi ke skor risiko (0.0 – 1.0) */
    val contribution: Double,
    /** Apakah faktor ini menaikkan risiko */
    val isElevating: Boolean
)

/**
 * Jenis bencana alam
 */
enum class DisasterType(
    val label: String,
    val labelId: String,
    val icon: String,
    val description: String,
    val colorHex: Long
) {
    FLOOD(
        label = "Flood",
        labelId = "Banjir",
        icon = "🌊",
        description = "Banjir akibat curah hujan tinggi dan debit sungai meningkat",
        colorHex = 0xFF1565C0
    ),
    TIDAL_FLOOD(
        label = "Tidal Flood",
        labelId = "Banjir Rob",
        icon = "🌊",
        description = "Banjir rob akibat gelombang tinggi dan pasang air laut",
        colorHex = 0xFF0D47A1
    ),
    CYCLONE(
        label = "Tropical Cyclone",
        labelId = "Siklon / Angin Topan",
        icon = "🌀",
        description = "Siklon tropis atau angin topan dengan angin kencang & tekanan rendah",
        colorHex = 0xFF880E4F
    ),
    THUNDERSTORM(
        label = "Severe Thunderstorm",
        labelId = "Badai Petir Hebat",
        icon = "⛈️",
        description = "Badai petir disertai hujan lebat, angin kencang, dan kemungkinan hujan es",
        colorHex = 0xFFBF360C
    ),
    LANDSLIDE(
        label = "Landslide",
        labelId = "Tanah Longsor",
        icon = "⛰️",
        description = "Potensi longsor akibat hujan terus-menerus yang meresap ke tanah",
        colorHex = 0xFF795548
    ),
    GROUND_SUBSIDENCE(
        label = "Ground Subsidence",
        labelId = "Tanah Amblas",
        icon = "🕳️",
        description = "Potensi amblasan tanah akibat genangan air berkepanjangan",
        colorHex = 0xFF5D4037
    );
}

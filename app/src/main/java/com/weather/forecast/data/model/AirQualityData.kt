package com.weather.forecast.data.model

/**
 * UI-ready Air Quality data models
 *
 * Digunakan untuk menampilkan data kualitas udara di UI.
 * Sudah diproses dari API response.
 */

/**
 * Data kualitas udara lengkap untuk satu lokasi
 */
data class AirQualityData(
    val current: CurrentAirQualityData,
    val hourlyForecast: List<HourlyAirQualityData>,
    val dailyForecast: List<DailyAirQualityData>,
    val lastUpdated: Long = System.currentTimeMillis()
)

/**
 * Data kualitas udara saat ini (UI-ready)
 */
data class CurrentAirQualityData(
    val aqi: Int,
    val aqiLevel: AqiLevel,
    val pm25: Double,
    val pm10: Double,
    val co: Double,
    val no2: Double,
    val so2: Double,
    val o3: Double,
    val uvIndex: Double,
    val dust: Double,
    val ammonia: Double
) {
    val aqiFormatted: String
        get() = "$aqi"

    val pm25Formatted: String
        get() = "%.1f µg/m³".format(pm25)

    val pm10Formatted: String
        get() = "%.1f µg/m³".format(pm10)

    val coFormatted: String
        get() = "%.1f µg/m³".format(co)

    val no2Formatted: String
        get() = "%.1f µg/m³".format(no2)

    val so2Formatted: String
        get() = "%.1f µg/m³".format(so2)

    val o3Formatted: String
        get() = "%.1f µg/m³".format(o3)

    val uvIndexFormatted: String
        get() = "%.1f".format(uvIndex)

    val dustFormatted: String
        get() = "%.1f µg/m³".format(dust)
}

/**
 * Data prakiraan kualitas udara per jam (UI-ready)
 */
data class HourlyAirQualityData(
    val time: String,
    val hour: String,
    val aqi: Int,
    val aqiLevel: AqiLevel,
    val pm25: Double,
    val pm10: Double,
    val co: Double,
    val no2: Double,
    val so2: Double,
    val o3: Double,
    val uvIndex: Double,
    /** Data angin dari cuaca (jika tersedia) */
    val windSpeed: Double = 0.0,
    val windDirection: Int = 0,
    val temperature: Double = 0.0,
    val humidity: Int = 0
) {
    val aqiFormatted: String
        get() = "$aqi"

    val pm25Formatted: String
        get() = "%.1f".format(pm25)

    val windDirectionText: String
        get() = com.weather.forecast.data.locale.AppLocaleManager.strings.windDirectionShort(windDirection)
}

/**
 * Data prakiraan kualitas udara harian (dirata-ratakan dari hourly)
 */
data class DailyAirQualityData(
    val date: String,
    val dayName: String,
    val avgAqi: Int,
    val maxAqi: Int,
    val minAqi: Int,
    val aqiLevel: AqiLevel,
    val avgPm25: Double,
    val avgPm10: Double,
    val maxUvIndex: Double,
    val avgWindSpeed: Double,
    val dominantWindDirection: Int,
    /** Prakiraan per jam untuk hari ini */
    val hourlyForecasts: List<HourlyAirQualityData> = emptyList()
) {
    val avgAqiFormatted: String
        get() = "$avgAqi"

    val avgPm25Formatted: String
        get() = "%.1f µg/m³".format(avgPm25)
}

/**
 * Level AQI berdasarkan US EPA standard
 *
 * Range | Level          | Deskripsi ID
 * ------|----------------|--------------------
 * 0-50  | Good           | Baik
 * 51-100| Moderate       | Sedang
 * 101-150| Unhealthy (SG)| Tidak Sehat (Sensitif)
 * 151-200| Unhealthy     | Tidak Sehat
 * 201-300| Very Unhealthy| Sangat Tidak Sehat
 * 301+  | Hazardous      | Berbahaya
 */
enum class AqiLevel(
    val label: String,
    val labelId: String,
    val description: String,
    val descriptionId: String,
    val minAqi: Int,
    val maxAqi: Int,
    val colorHex: Long
) {
    GOOD(
        label = "Good",
        labelId = "Baik",
        description = "Air quality is satisfactory",
        descriptionId = "Kualitas udara memuaskan, tidak ada risiko kesehatan",
        minAqi = 0,
        maxAqi = 50,
        colorHex = 0xFF4CAF50
    ),
    MODERATE(
        label = "Moderate",
        labelId = "Sedang",
        description = "Acceptable for most people",
        descriptionId = "Kualitas udara dapat diterima, namun beberapa polutan mungkin berpengaruh bagi kelompok sensitif",
        minAqi = 51,
        maxAqi = 100,
        colorHex = 0xFFFFEB3B
    ),
    UNHEALTHY_SENSITIVE(
        label = "Unhealthy for Sensitive Groups",
        labelId = "Tidak Sehat bagi Sensitif",
        description = "Sensitive groups may experience health effects",
        descriptionId = "Kelompok sensitif (anak-anak, lansia, penderita asma) mungkin mengalami efek kesehatan",
        minAqi = 101,
        maxAqi = 150,
        colorHex = 0xFFFF9800
    ),
    UNHEALTHY(
        label = "Unhealthy",
        labelId = "Tidak Sehat",
        description = "Everyone may begin to experience health effects",
        descriptionId = "Semua orang mungkin mulai mengalami efek kesehatan",
        minAqi = 151,
        maxAqi = 200,
        colorHex = 0xFFF44336
    ),
    VERY_UNHEALTHY(
        label = "Very Unhealthy",
        labelId = "Sangat Tidak Sehat",
        description = "Health alert: everyone may experience serious health effects",
        descriptionId = "Peringatan kesehatan: semua orang mungkin mengalami efek serius",
        minAqi = 201,
        maxAqi = 300,
        colorHex = 0xFF9C27B0
    ),
    HAZARDOUS(
        label = "Hazardous",
        labelId = "Berbahaya",
        description = "Health warning of emergency conditions",
        descriptionId = "Kondisi darurat kesehatan, semua orang kemungkinan terpengaruh",
        minAqi = 301,
        maxAqi = 500,
        colorHex = 0xFF880E4F
    );

    companion object {
        fun fromAqi(aqi: Int): AqiLevel {
            return entries.find { aqi in it.minAqi..it.maxAqi } ?: HAZARDOUS
        }
    }
}

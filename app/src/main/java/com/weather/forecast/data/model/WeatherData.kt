package com.weather.forecast.data.model

/**
 * UI-friendly weather data models
 * 
 * Models ini digunakan untuk menampilkan data di UI,
 * sudah diproses dari API response.
 */

/**
 * Data cuaca lengkap untuk satu lokasi
 */
data class WeatherData(
    val location: LocationInfo,
    val current: CurrentWeatherData,
    val hourly: List<HourlyWeatherData>,
    val daily: List<DailyWeatherData>,
    val currentPotential: WeatherPotential? = null,
    val lastUpdated: Long = System.currentTimeMillis()
)

/**
 * Info lokasi
 */
data class LocationInfo(
    val latitude: Double,
    val longitude: Double,
    val name: String,
    val fullName: String,
    val timezone: String
)

/**
 * Data cuaca saat ini (UI-ready)
 */
data class CurrentWeatherData(
    val temperature: Double,
    val apparentTemperature: Double,
    val humidity: Int,
    val weatherCode: Int,
    val weatherCondition: WeatherCondition,
    val windSpeed: Double,
    val windDirection: Int,
    val windGusts: Double,
    val pressure: Double,
    val cloudCover: Int,
    val precipitation: Double,
    val isDay: Boolean,
    val rain: Double = 0.0,
    val showers: Double = 0.0,
    val snowfall: Double = 0.0,
    val dewPoint: Double = 0.0,
    val cape: Double = 0.0
) {
    val temperatureFormatted: String
        get() = "${temperature.toInt()}°"
    
    val apparentTemperatureFormatted: String
        get() = "${apparentTemperature.toInt()}°"
    
    val humidityFormatted: String
        get() = "$humidity%"
    
    val windSpeedFormatted: String
        get() = "${windSpeed.toInt()} km/h"
    
    val windGustsFormatted: String
        get() = "${windGusts.toInt()} km/h"
    
    val pressureFormatted: String
        get() = "${pressure.toInt()} hPa"
    
    val dewPointFormatted: String
        get() = "${dewPoint.toInt()}°"
    
    val windDirectionText: String
        get() = degreesToCardinal(windDirection)
    
    val windDirectionFull: String
        get() = degreesToCardinalFull(windDirection)
}

/**
 * Data prakiraan per jam (UI-ready)
 */
data class HourlyWeatherData(
    val time: String,
    val hour: String,
    val temperature: Double,
    val apparentTemperature: Double,
    val humidity: Int,
    val weatherCode: Int,
    val weatherCondition: WeatherCondition,
    val precipitationProbability: Int,
    val precipitation: Double,
    val windSpeed: Double,
    val uvIndex: Double,
    val isDay: Boolean,
    val visibility: Double,
    val windDirection: Int = 0,
    val windGusts: Double = 0.0,
    val dewPoint: Double = 0.0,
    val cape: Double = 0.0,
    val freezingLevelHeight: Double = 0.0,
    val rain: Double = 0.0,
    val showers: Double = 0.0,
    val snowfall: Double = 0.0,
    val pressure: Double = 0.0,
    /** Soil moisture 0-7cm (m³/m³) */
    val soilMoistureShallow: Double = 0.0,
    /** Soil moisture 7-28cm (m³/m³) */
    val soilMoistureMedium: Double = 0.0,
    /** Soil moisture 28-100cm (m³/m³) */
    val soilMoistureDeep: Double = 0.0,
    /** Soil temperature at surface (°C) */
    val soilTemperature: Double = 0.0
) {
    val temperatureFormatted: String
        get() = "${temperature.toInt()}°"
    
    val precipitationProbabilityFormatted: String
        get() = "$precipitationProbability%"
    
    val windDirectionText: String
        get() = degreesToCardinal(windDirection)
}

/**
 * Data prakiraan harian (UI-ready)
 *
 * Setiap item harian berisi daftar prakiraan per jam (hourlyForecasts)
 * yang menampilkan detail cuaca untuk setiap jam dalam 1 hari tersebut.
 * Data per jam dikelompokkan berdasarkan tanggal dari response API.
 */
data class DailyWeatherData(
    val date: String,
    val dayName: String,
    val temperatureMax: Double,
    val temperatureMin: Double,
    val apparentTemperatureMax: Double,
    val apparentTemperatureMin: Double,
    val weatherCode: Int,
    val weatherCondition: WeatherCondition,
    val sunrise: String,
    val sunset: String,
    val uvIndexMax: Double,
    val precipitationSum: Double,
    val precipitationProbabilityMax: Int,
    val windSpeedMax: Double,
    val windGustsMax: Double,
    val windDirectionDominant: Int = 0,
    val rainSum: Double = 0.0,
    val showersSum: Double = 0.0,
    val snowfallSum: Double = 0.0,
    val precipitationHours: Double = 0.0,
    /** Potensi cuaca ekstrem untuk hari ini */
    val weatherPotential: WeatherPotential? = null,
    /** Prakiraan per jam (24 data) untuk hari ini */
    val hourlyForecasts: List<HourlyWeatherData> = emptyList()
) {
    val temperatureMaxFormatted: String
        get() = "${temperatureMax.toInt()}°"
    
    val temperatureMinFormatted: String
        get() = "${temperatureMin.toInt()}°"
    
    val precipitationProbabilityFormatted: String
        get() = "$precipitationProbabilityMax%"
    
    val windDirectionText: String
        get() = degreesToCardinal(windDirectionDominant)
}

// ──────────────────────────────────────────────────────────
// Weather Potential & Risk Models
// ──────────────────────────────────────────────────────────

/**
 * Potensi cuaca ekstrem — dihitung dari data CAPE, freezing level,
 * kecepatan angin, dan kode cuaca WMO.
 */
data class WeatherPotential(
    val stormRisk: RiskLevel,
    val heavyRainRisk: RiskLevel,
    val hailRisk: RiskLevel,
    val strongWindRisk: RiskLevel,
    val tornadoRisk: RiskLevel,
    val maxCape: Double = 0.0,
    val minFreezingLevel: Double = 0.0,
    val maxWindGusts: Double = 0.0,
    val maxPrecipitation: Double = 0.0,
    val alerts: List<WeatherAlert> = emptyList()
)

/**
 * Level risiko cuaca
 */
enum class RiskLevel(
    val label: String,
    val labelId: String,
    val colorHex: Long
) {
    LOW("Low", "Rendah", 0xFF4CAF50),
    MODERATE("Moderate", "Sedang", 0xFFFF9800),
    HIGH("High", "Tinggi", 0xFFFF5722),
    EXTREME("Extreme", "Ekstrem", 0xFFD32F2F);
}

/**
 * Peringatan cuaca yang aktif
 */
data class WeatherAlert(
    val type: AlertType,
    val risk: RiskLevel,
    val description: String,
    val descriptionId: String
)

/**
 * Tipe peringatan cuaca
 */
enum class AlertType(
    val label: String,
    val labelId: String,
    val icon: String
) {
    THUNDERSTORM("Thunderstorm", "Badai Petir", "thunderstorm"),
    HEAVY_RAIN("Heavy Rain", "Hujan Lebat", "rainy"),
    HAIL("Hail", "Hujan Es", "hail"),
    STRONG_WIND("Strong Wind", "Angin Kencang", "wind"),
    TORNADO("Tornado/Waterspout", "Puting Beliung", "tornado"),
    SNOWSTORM("Snowstorm", "Badai Salju", "snowy"),
    FREEZING_RAIN("Freezing Rain", "Hujan Beku", "freezing")
}

// ──────────────────────────────────────────────────────────
// Helper Functions
// ──────────────────────────────────────────────────────────

/** Degrees → short cardinal (Indonesian) */
fun degreesToCardinal(degrees: Int): String {
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

/** Degrees → full cardinal name (Indonesian) */
fun degreesToCardinalFull(degrees: Int): String {
    return when {
        degrees in 0..22 || degrees in 338..360 -> "Utara"
        degrees in 23..67 -> "Timur Laut"
        degrees in 68..112 -> "Timur"
        degrees in 113..157 -> "Tenggara"
        degrees in 158..202 -> "Selatan"
        degrees in 203..247 -> "Barat Daya"
        degrees in 248..292 -> "Barat"
        degrees in 293..337 -> "Barat Laut"
        else -> "-"
    }
}
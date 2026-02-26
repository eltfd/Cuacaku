package com.weather.forecast.data.model

/**
 * Weather Code Mapping sesuai WMO Weather interpretation codes
 * 
 * Reference: https://open-meteo.com/en/docs
 * 
 * WMO Code | Description
 * ---------|------------
 * 0        | Clear sky
 * 1, 2, 3  | Mainly clear, partly cloudy, overcast
 * 45, 48   | Fog and depositing rime fog
 * 51, 53, 55 | Drizzle: Light, moderate, dense
 * 56, 57   | Freezing Drizzle: Light, dense
 * 61, 63, 65 | Rain: Slight, moderate, heavy
 * 66, 67   | Freezing Rain: Light, heavy
 * 71, 73, 75 | Snow fall: Slight, moderate, heavy
 * 77       | Snow grains
 * 80, 81, 82 | Rain showers: Slight, moderate, violent
 * 85, 86   | Snow showers slight and heavy
 * 95       | Thunderstorm: Slight or moderate
 * 96, 99   | Thunderstorm with hail
 */
enum class WeatherCondition(
    val codes: List<Int>,
    val description: String,
    val descriptionId: String,
    val icon: String
) {
    CLEAR(
        codes = listOf(0),
        description = "Clear sky",
        descriptionId = "Cerah",
        icon = "sunny"
    ),
    MAINLY_CLEAR(
        codes = listOf(1),
        description = "Mainly clear",
        descriptionId = "Cerah Berawan",
        icon = "partly_cloudy"
    ),
    PARTLY_CLOUDY(
        codes = listOf(2),
        description = "Partly cloudy",
        descriptionId = "Berawan Sebagian",
        icon = "partly_cloudy"
    ),
    OVERCAST(
        codes = listOf(3),
        description = "Overcast",
        descriptionId = "Mendung",
        icon = "cloudy"
    ),
    FOG(
        codes = listOf(45, 48),
        description = "Fog",
        descriptionId = "Berkabut",
        icon = "fog"
    ),
    DRIZZLE_LIGHT(
        codes = listOf(51),
        description = "Light drizzle",
        descriptionId = "Gerimis Ringan",
        icon = "drizzle"
    ),
    DRIZZLE_MODERATE(
        codes = listOf(53),
        description = "Moderate drizzle",
        descriptionId = "Gerimis",
        icon = "drizzle"
    ),
    DRIZZLE_DENSE(
        codes = listOf(55),
        description = "Dense drizzle",
        descriptionId = "Gerimis Lebat",
        icon = "drizzle"
    ),
    FREEZING_DRIZZLE(
        codes = listOf(56, 57),
        description = "Freezing drizzle",
        descriptionId = "Gerimis Beku",
        icon = "drizzle"
    ),
    RAIN_SLIGHT(
        codes = listOf(61),
        description = "Slight rain",
        descriptionId = "Hujan Ringan",
        icon = "rainy"
    ),
    RAIN_MODERATE(
        codes = listOf(63),
        description = "Moderate rain",
        descriptionId = "Hujan",
        icon = "rainy"
    ),
    RAIN_HEAVY(
        codes = listOf(65),
        description = "Heavy rain",
        descriptionId = "Hujan Lebat",
        icon = "rainy"
    ),
    FREEZING_RAIN(
        codes = listOf(66, 67),
        description = "Freezing rain",
        descriptionId = "Hujan Beku",
        icon = "rainy"
    ),
    SNOW_SLIGHT(
        codes = listOf(71),
        description = "Slight snow",
        descriptionId = "Salju Ringan",
        icon = "snowy"
    ),
    SNOW_MODERATE(
        codes = listOf(73),
        description = "Moderate snow",
        descriptionId = "Salju",
        icon = "snowy"
    ),
    SNOW_HEAVY(
        codes = listOf(75, 77),
        description = "Heavy snow",
        descriptionId = "Salju Lebat",
        icon = "snowy"
    ),
    RAIN_SHOWERS_SLIGHT(
        codes = listOf(80),
        description = "Slight rain showers",
        descriptionId = "Hujan Rintik",
        icon = "rainy"
    ),
    RAIN_SHOWERS_MODERATE(
        codes = listOf(81),
        description = "Moderate rain showers",
        descriptionId = "Hujan Deras",
        icon = "rainy"
    ),
    RAIN_SHOWERS_VIOLENT(
        codes = listOf(82),
        description = "Violent rain showers",
        descriptionId = "Hujan Sangat Lebat",
        icon = "rainy"
    ),
    SNOW_SHOWERS(
        codes = listOf(85, 86),
        description = "Snow showers",
        descriptionId = "Hujan Salju",
        icon = "snowy"
    ),
    THUNDERSTORM(
        codes = listOf(95),
        description = "Thunderstorm",
        descriptionId = "Badai Petir",
        icon = "thunderstorm"
    ),
    THUNDERSTORM_HAIL(
        codes = listOf(96, 99),
        description = "Thunderstorm with hail",
        descriptionId = "Badai Petir dengan Hujan Es",
        icon = "thunderstorm"
    );

    companion object {
        /**
         * Get weather condition from WMO code
         */
        fun fromCode(code: Int): WeatherCondition {
            return entries.find { code in it.codes } ?: CLEAR
        }

        /**
         * Check if weather is severe (untuk notifikasi)
         */
        fun isSevereWeather(code: Int): Boolean {
            return code in listOf(65, 67, 75, 77, 82, 86, 95, 96, 99)
        }

        /**
         * Check if rain is expected
         */
        fun isRainy(code: Int): Boolean {
            return code in listOf(51, 53, 55, 56, 57, 61, 63, 65, 66, 67, 80, 81, 82, 95, 96, 99)
        }
    }
}

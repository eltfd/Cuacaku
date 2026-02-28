package com.weather.forecast.data.repository

import com.weather.forecast.data.api.RetrofitClient
import com.weather.forecast.data.model.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

/**
 * Weather Repository
 * 
 * Single source of truth untuk data cuaca.
 * Menggabungkan data dari Open-Meteo API dan Nominatim Geocoding.
 * 
 * Responsibilities:
 * - Fetch weather data dari API
 * - Fetch location name dari coordinates
 * - Transform API response ke UI-ready models
 * - Handle errors
 */
class WeatherRepository {

    private val weatherApi = RetrofitClient.weatherApi
    private val geocodingApi = RetrofitClient.geocodingApi

    /**
     * Get complete weather data untuk lokasi
     * 
     * @param latitude Location latitude
     * @param longitude Location longitude
     * @return Result<WeatherData> - Success dengan data atau Failure dengan exception
     */
    suspend fun getWeatherData(latitude: Double, longitude: Double): Result<WeatherData> {
        return withContext(Dispatchers.IO) {
            try {
                // Fetch weather dan location name secara parallel
                val weatherResponse = weatherApi.getWeather(latitude, longitude)
                val locationName = getLocationName(latitude, longitude)

                // Transform ke UI model
                val weatherData = transformToWeatherData(weatherResponse, locationName)
                Result.success(weatherData)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    /**
     * Get location name dari coordinates menggunakan Nominatim
     */
    private suspend fun getLocationName(latitude: Double, longitude: Double): Pair<String, String> {
        return try {
            val response = geocodingApi.reverseGeocode(latitude, longitude)
            val name = response.address?.getLocationName() ?: "Unknown"
            val fullName = response.address?.getFullLocation() ?: response.displayName

            // Update app locale based on detected country
            response.address?.countryCode?.let { countryCode ->
                com.weather.forecast.data.locale.AppLocaleManager.updateCountry(countryCode)
            }

            Pair(name, fullName)
        } catch (e: Exception) {
            Pair("Lat: $latitude", "Lon: $longitude")
        }
    }

    /**
     * Search lokasi berdasarkan nama
     */
    suspend fun searchLocation(query: String): Result<List<SearchResult>> {
        return withContext(Dispatchers.IO) {
            try {
                val results = geocodingApi.searchLocation(query)
                Result.success(results)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    /**
     * Transform API response ke UI-ready WeatherData
     */
    private fun transformToWeatherData(
        response: WeatherResponse,
        locationName: Pair<String, String>
    ): WeatherData {
        val current = response.currentWeather?.let { c ->
            CurrentWeatherData(
                temperature = c.temperature,
                apparentTemperature = c.apparentTemperature,
                humidity = c.humidity,
                weatherCode = c.weatherCode,
                weatherCondition = WeatherCondition.fromCode(c.weatherCode),
                windSpeed = c.windSpeed,
                windDirection = c.windDirection,
                windGusts = c.windGusts,
                pressure = c.pressure,
                cloudCover = c.cloudCover,
                precipitation = c.precipitation,
                isDay = c.isDay == 1,
                rain = c.rain,
                showers = c.showers,
                snowfall = c.snowfall,
                dewPoint = c.dewPoint ?: 0.0,
                cape = c.cape ?: 0.0
            )
        } ?: createDefaultCurrentWeather()

        val hourly = transformHourlyData(response.hourlyForecast)
        // Kelompokkan semua data per jam berdasarkan tanggal untuk prakiraan harian
        val hourlyByDate = transformAllHourlyDataByDate(response.hourlyForecast)
        val daily = transformDailyData(response.dailyForecast, hourlyByDate)

        // Hitung potensi cuaca saat ini menggunakan data 24 jam ke depan
        val currentPotential = calculateWeatherPotential(
            hourlies = hourly,
            weatherCode = current.weatherCode,
            windGustsMax = current.windGusts
        )

        return WeatherData(
            location = LocationInfo(
                latitude = response.latitude,
                longitude = response.longitude,
                name = locationName.first,
                fullName = locationName.second,
                timezone = response.timezone
            ),
            current = current,
            hourly = hourly,
            daily = daily,
            currentPotential = currentPotential
        )
    }

    /**
     * Transform hourly forecast data
     *
     * Mengambil data per jam dari jam sekarang sampai 24 jam ke depan
     * untuk ditampilkan di horizontal scroll "Prakiraan Per Jam" utama.
     */
    private fun transformHourlyData(hourly: HourlyForecast?): List<HourlyWeatherData> {
        if (hourly == null) return emptyList()

        val formatter = DateTimeFormatter.ISO_DATE_TIME
        val hourFormatter = DateTimeFormatter.ofPattern("HH:mm")
        val now = LocalDateTime.now()

        return hourly.time.mapIndexedNotNull { index, timeStr ->
            try {
                val time = LocalDateTime.parse(timeStr, formatter)
                
                // Hanya ambil data dari jam sekarang sampai 24 jam ke depan
                if (time.isBefore(now.minusHours(1)) || time.isAfter(now.plusHours(24))) {
                    return@mapIndexedNotNull null
                }

                HourlyWeatherData(
                    time = timeStr,
                    hour = time.format(hourFormatter),
                    temperature = hourly.temperature.getOrNull(index) ?: 0.0,
                    apparentTemperature = hourly.apparentTemperature.getOrNull(index) ?: 0.0,
                    humidity = hourly.humidity.getOrNull(index) ?: 0,
                    weatherCode = hourly.weatherCode.getOrNull(index) ?: 0,
                    weatherCondition = WeatherCondition.fromCode(hourly.weatherCode.getOrNull(index) ?: 0),
                    precipitationProbability = hourly.precipitationProbability.getOrNull(index) ?: 0,
                    precipitation = hourly.precipitation.getOrNull(index) ?: 0.0,
                    windSpeed = hourly.windSpeed.getOrNull(index) ?: 0.0,
                    uvIndex = hourly.uvIndex.getOrNull(index) ?: 0.0,
                    isDay = (hourly.isDay.getOrNull(index) ?: 1) == 1,
                    visibility = hourly.visibility.getOrNull(index) ?: 0.0,
                    windDirection = hourly.windDirection.getOrNull(index) ?: 0,
                    windGusts = hourly.windGusts?.getOrNull(index) ?: 0.0,
                    dewPoint = hourly.dewPoint?.getOrNull(index) ?: 0.0,
                    cape = hourly.cape?.getOrNull(index) ?: 0.0,
                    freezingLevelHeight = hourly.freezingLevelHeight?.getOrNull(index) ?: 0.0,
                    rain = hourly.rain.getOrNull(index) ?: 0.0,
                    showers = hourly.showers.getOrNull(index) ?: 0.0,
                    snowfall = hourly.snowfall.getOrNull(index) ?: 0.0,
                    pressure = hourly.pressureMsl?.getOrNull(index) ?: 0.0,
                    soilMoistureShallow = hourly.soilMoisture0to7?.getOrNull(index) ?: 0.0,
                    soilMoistureMedium = hourly.soilMoisture7to28?.getOrNull(index) ?: 0.0,
                    soilMoistureDeep = hourly.soilMoisture28to100?.getOrNull(index) ?: 0.0,
                    soilTemperature = hourly.soilTemperature0cm?.getOrNull(index) ?: 0.0,
                    cloudCover = hourly.cloudCover.getOrNull(index) ?: 0
                )
            } catch (e: Exception) {
                null
            }
        }
    }

    /**
     * Transform SEMUA data hourly dan kelompokkan berdasarkan tanggal
     *
     * Mengembalikan Map<String, List<HourlyWeatherData>> dimana key = tanggal (yyyy-MM-dd)
     * dan value = list 24 data per jam untuk hari tersebut.
     *
     * Digunakan untuk menyisipkan prakiraan per jam ke dalam setiap item prakiraan harian
     * sehingga pengguna bisa melihat detail cuaca setiap jam dalam 1 hari.
     */
    private fun transformAllHourlyDataByDate(hourly: HourlyForecast?): Map<String, List<HourlyWeatherData>> {
        if (hourly == null) return emptyMap()

        val formatter = DateTimeFormatter.ISO_DATE_TIME
        val hourFormatter = DateTimeFormatter.ofPattern("HH:mm")
        val dateFormatter = DateTimeFormatter.ISO_DATE

        val allHourly = hourly.time.mapIndexedNotNull { index, timeStr ->
            try {
                val time = LocalDateTime.parse(timeStr, formatter)

                HourlyWeatherData(
                    time = timeStr,
                    hour = time.format(hourFormatter),
                    temperature = hourly.temperature.getOrNull(index) ?: 0.0,
                    apparentTemperature = hourly.apparentTemperature.getOrNull(index) ?: 0.0,
                    humidity = hourly.humidity.getOrNull(index) ?: 0,
                    weatherCode = hourly.weatherCode.getOrNull(index) ?: 0,
                    weatherCondition = WeatherCondition.fromCode(hourly.weatherCode.getOrNull(index) ?: 0),
                    precipitationProbability = hourly.precipitationProbability.getOrNull(index) ?: 0,
                    precipitation = hourly.precipitation.getOrNull(index) ?: 0.0,
                    windSpeed = hourly.windSpeed.getOrNull(index) ?: 0.0,
                    uvIndex = hourly.uvIndex.getOrNull(index) ?: 0.0,
                    isDay = (hourly.isDay.getOrNull(index) ?: 1) == 1,
                    visibility = hourly.visibility.getOrNull(index) ?: 0.0,
                    windDirection = hourly.windDirection.getOrNull(index) ?: 0,
                    windGusts = hourly.windGusts?.getOrNull(index) ?: 0.0,
                    dewPoint = hourly.dewPoint?.getOrNull(index) ?: 0.0,
                    cape = hourly.cape?.getOrNull(index) ?: 0.0,
                    freezingLevelHeight = hourly.freezingLevelHeight?.getOrNull(index) ?: 0.0,
                    rain = hourly.rain.getOrNull(index) ?: 0.0,
                    showers = hourly.showers.getOrNull(index) ?: 0.0,
                    snowfall = hourly.snowfall.getOrNull(index) ?: 0.0,
                    pressure = hourly.pressureMsl?.getOrNull(index) ?: 0.0,
                    soilMoistureShallow = hourly.soilMoisture0to7?.getOrNull(index) ?: 0.0,
                    soilMoistureMedium = hourly.soilMoisture7to28?.getOrNull(index) ?: 0.0,
                    soilMoistureDeep = hourly.soilMoisture28to100?.getOrNull(index) ?: 0.0,
                    soilTemperature = hourly.soilTemperature0cm?.getOrNull(index) ?: 0.0,
                    cloudCover = hourly.cloudCover.getOrNull(index) ?: 0
                )
            } catch (e: Exception) {
                null
            }
        }

        // Kelompokkan berdasarkan tanggal (yyyy-MM-dd)
        return allHourly.groupBy { hourlyItem ->
            try {
                val time = LocalDateTime.parse(hourlyItem.time, formatter)
                time.toLocalDate().format(dateFormatter)
            } catch (e: Exception) {
                ""
            }
        }.filterKeys { it.isNotEmpty() }
    }

    /**
     * Transform daily forecast data
     *
     * Setiap DailyWeatherData dilengkapi dengan hourlyForecasts
     * berisi prakiraan per jam (24 data) untuk hari tersebut.
     *
     * @param daily DailyForecast dari API response
     * @param hourlyByDate Map data per jam yang sudah dikelompokkan berdasarkan tanggal
     */
    private fun transformDailyData(
        daily: DailyForecast?,
        hourlyByDate: Map<String, List<HourlyWeatherData>> = emptyMap()
    ): List<DailyWeatherData> {
        if (daily == null) return emptyList()

        val dateFormatter = DateTimeFormatter.ISO_DATE
        val locale = Locale("id", "ID")

        return daily.time.mapIndexedNotNull { index, dateStr ->
            try {
                val date = java.time.LocalDate.parse(dateStr, dateFormatter)
                val dayName = date.dayOfWeek.getDisplayName(TextStyle.FULL, locale)
                val dayHourly = hourlyByDate[dateStr] ?: emptyList()

                DailyWeatherData(
                    date = dateStr,
                    dayName = dayName,
                    temperatureMax = daily.temperatureMax.getOrNull(index) ?: 0.0,
                    temperatureMin = daily.temperatureMin.getOrNull(index) ?: 0.0,
                    apparentTemperatureMax = daily.apparentTemperatureMax.getOrNull(index) ?: 0.0,
                    apparentTemperatureMin = daily.apparentTemperatureMin.getOrNull(index) ?: 0.0,
                    weatherCode = daily.weatherCode.getOrNull(index) ?: 0,
                    weatherCondition = WeatherCondition.fromCode(daily.weatherCode.getOrNull(index) ?: 0),
                    sunrise = extractTime(daily.sunrise.getOrNull(index) ?: ""),
                    sunset = extractTime(daily.sunset.getOrNull(index) ?: ""),
                    uvIndexMax = daily.uvIndexMax.getOrNull(index) ?: 0.0,
                    precipitationSum = daily.precipitationSum.getOrNull(index) ?: 0.0,
                    precipitationProbabilityMax = daily.precipitationProbabilityMax.getOrNull(index) ?: 0,
                    windSpeedMax = daily.windSpeedMax.getOrNull(index) ?: 0.0,
                    windGustsMax = daily.windGustsMax.getOrNull(index) ?: 0.0,
                    windDirectionDominant = daily.windDirectionDominant.getOrNull(index) ?: 0,
                    rainSum = daily.rainSum.getOrNull(index) ?: 0.0,
                    showersSum = daily.showersSum.getOrNull(index) ?: 0.0,
                    snowfallSum = daily.snowfallSum.getOrNull(index) ?: 0.0,
                    precipitationHours = daily.precipitationHours?.getOrNull(index) ?: 0.0,
                    weatherPotential = calculateWeatherPotential(
                        hourlies = dayHourly,
                        weatherCode = daily.weatherCode.getOrNull(index) ?: 0,
                        windGustsMax = daily.windGustsMax.getOrNull(index) ?: 0.0
                    ),
                    hourlyForecasts = dayHourly
                )
            } catch (e: Exception) {
                null
            }
        }
    }

    /**
     * Extract time dari ISO datetime string
     */
    private fun extractTime(isoDateTime: String): String {
        return try {
            val dateTime = LocalDateTime.parse(isoDateTime, DateTimeFormatter.ISO_DATE_TIME)
            dateTime.format(DateTimeFormatter.ofPattern("HH:mm"))
        } catch (e: Exception) {
            isoDateTime.substringAfter("T").take(5)
        }
    }

    /**
     * Create default current weather jika API tidak mengembalikan data
     */
    private fun createDefaultCurrentWeather(): CurrentWeatherData {
        return CurrentWeatherData(
            temperature = 0.0,
            apparentTemperature = 0.0,
            humidity = 0,
            weatherCode = 0,
            weatherCondition = WeatherCondition.CLEAR,
            windSpeed = 0.0,
            windDirection = 0,
            windGusts = 0.0,
            pressure = 0.0,
            cloudCover = 0,
            precipitation = 0.0,
            isDay = true,
            rain = 0.0,
            showers = 0.0,
            snowfall = 0.0,
            dewPoint = 0.0,
            cape = 0.0
        )
    }

    // ──────────────────────────────────────────────────────────
    // Weather Potential Calculation
    // ──────────────────────────────────────────────────────────

    /**
     * Menghitung potensi cuaca ekstrem berdasarkan data per jam.
     *
     * CAPE (Convective Available Potential Energy):
     * - 0-300 J/kg → stabil, tanpa badai
     * - 300-1000 → sedikit tidak stabil, badai terisolasi mungkin
     * - 1000-2500 → tidak stabil sedang, badai petir kemungkinan besar
     * - 2500-3500 → sangat tidak stabil, badai hebat mungkin
     * - >3500 → ekstrem, badai sangat hebat
     *
     * Freezing Level + CAPE + Thunderstorm → potensi hujan es
     * CAPE tinggi + Wind shear tinggi → potensi puting beliung
     */
    private fun calculateWeatherPotential(
        hourlies: List<HourlyWeatherData>,
        weatherCode: Int,
        windGustsMax: Double
    ): WeatherPotential {
        if (hourlies.isEmpty()) {
            return WeatherPotential(
                stormRisk = RiskLevel.LOW,
                heavyRainRisk = RiskLevel.LOW,
                hailRisk = RiskLevel.LOW,
                strongWindRisk = RiskLevel.LOW,
                tornadoRisk = RiskLevel.LOW
            )
        }

        val maxCape = hourlies.maxOf { it.cape }
        val minFreezingLevel = hourlies.filter { it.freezingLevelHeight > 0 }
            .minOfOrNull { it.freezingLevelHeight } ?: 5000.0
        val maxGusts = maxOf(windGustsMax, hourlies.maxOf { it.windGusts })
        val maxPrecip = hourlies.maxOf { it.precipitation }
        val maxRain = hourlies.maxOf { it.rain }
        val hasThunderstormCode = weatherCode in listOf(95, 96, 99) ||
                hourlies.any { it.weatherCode in listOf(95, 96, 99) }
        val hasHailCode = weatherCode in listOf(96, 99) ||
                hourlies.any { it.weatherCode in listOf(96, 99) }
        val hasHeavyRainCode = weatherCode in listOf(65, 67, 82) ||
                hourlies.any { it.weatherCode in listOf(65, 67, 82) }

        // Wind shear indicator: difference between max gusts and avg wind speed
        val avgWindSpeed = hourlies.map { it.windSpeed }.average()
        val windShear = maxGusts - avgWindSpeed

        // === Storm Risk ===
        val stormRisk = when {
            hasThunderstormCode && maxCape > 2500 -> RiskLevel.EXTREME
            hasThunderstormCode || maxCape > 2500 -> RiskLevel.HIGH
            maxCape > 1000 -> RiskLevel.MODERATE
            maxCape > 300 -> RiskLevel.LOW
            else -> RiskLevel.LOW
        }

        // === Heavy Rain Risk ===
        val heavyRainRisk = when {
            hasHeavyRainCode && maxRain > 20 -> RiskLevel.EXTREME
            hasHeavyRainCode || maxRain > 15 -> RiskLevel.HIGH
            maxRain > 5 || maxPrecip > 10 -> RiskLevel.MODERATE
            maxPrecip > 2 -> RiskLevel.LOW
            else -> RiskLevel.LOW
        }

        // === Hail Risk ===
        // High CAPE + low freezing level + thunderstorm = hail potential
        val hailRisk = when {
            hasHailCode -> RiskLevel.EXTREME
            maxCape > 2000 && minFreezingLevel < 2500 && hasThunderstormCode -> RiskLevel.HIGH
            maxCape > 1500 && minFreezingLevel < 3000 -> RiskLevel.MODERATE
            maxCape > 1000 && minFreezingLevel < 3500 -> RiskLevel.LOW
            else -> RiskLevel.LOW
        }

        // === Strong Wind Risk ===
        val strongWindRisk = when {
            maxGusts > 90 -> RiskLevel.EXTREME   // 90+ km/h = damaging
            maxGusts > 70 -> RiskLevel.HIGH      // 70+ km/h = very strong
            maxGusts > 50 -> RiskLevel.MODERATE   // 50+ km/h = strong
            maxGusts > 35 -> RiskLevel.LOW        // 35+ km/h = moderate
            else -> RiskLevel.LOW
        }

        // === Tornado / Puting Beliung Risk ===
        // Requires: Very high CAPE + significant wind shear + thunderstorm
        val tornadoRisk = when {
            maxCape > 3000 && windShear > 40 && hasThunderstormCode -> RiskLevel.EXTREME
            maxCape > 2500 && windShear > 30 && hasThunderstormCode -> RiskLevel.HIGH
            maxCape > 2000 && windShear > 25 -> RiskLevel.MODERATE
            maxCape > 1500 && windShear > 20 -> RiskLevel.LOW
            else -> RiskLevel.LOW
        }

        // Build alerts list
        val alerts = mutableListOf<WeatherAlert>()

        if (stormRisk >= RiskLevel.MODERATE) {
            alerts.add(WeatherAlert(
                type = AlertType.THUNDERSTORM,
                risk = stormRisk,
                description = "Thunderstorm potential based on CAPE ${maxCape.toInt()} J/kg",
                descriptionId = "Potensi badai petir berdasarkan CAPE ${maxCape.toInt()} J/kg"
            ))
        }
        if (heavyRainRisk >= RiskLevel.MODERATE) {
            alerts.add(WeatherAlert(
                type = AlertType.HEAVY_RAIN,
                risk = heavyRainRisk,
                description = "Heavy rain expected, max ${maxRain.toInt()} mm/h",
                descriptionId = "Hujan lebat diperkirakan, maks ${maxRain.toInt()} mm/jam"
            ))
        }
        if (hailRisk >= RiskLevel.MODERATE) {
            alerts.add(WeatherAlert(
                type = AlertType.HAIL,
                risk = hailRisk,
                description = "Hail potential with freezing level at ${(minFreezingLevel/1000).toInt()} km",
                descriptionId = "Potensi hujan es dengan level beku di ${(minFreezingLevel/1000).toInt()} km"
            ))
        }
        if (strongWindRisk >= RiskLevel.MODERATE) {
            alerts.add(WeatherAlert(
                type = AlertType.STRONG_WIND,
                risk = strongWindRisk,
                description = "Strong wind gusts up to ${maxGusts.toInt()} km/h",
                descriptionId = "Hembusan angin kencang hingga ${maxGusts.toInt()} km/jam"
            ))
        }
        if (tornadoRisk >= RiskLevel.MODERATE) {
            alerts.add(WeatherAlert(
                type = AlertType.TORNADO,
                risk = tornadoRisk,
                description = "Tornado/waterspout conditions: CAPE ${maxCape.toInt()}, shear ${windShear.toInt()}",
                descriptionId = "Kondisi puting beliung: CAPE ${maxCape.toInt()}, geser angin ${windShear.toInt()}"
            ))
        }
        if (hourlies.any { it.snowfall > 0 } && maxGusts > 40) {
            alerts.add(WeatherAlert(
                type = AlertType.SNOWSTORM,
                risk = if (maxGusts > 60) RiskLevel.HIGH else RiskLevel.MODERATE,
                description = "Snowstorm conditions with wind gusts ${maxGusts.toInt()} km/h",
                descriptionId = "Kondisi badai salju dengan hembusan angin ${maxGusts.toInt()} km/jam"
            ))
        }

        return WeatherPotential(
            stormRisk = stormRisk,
            heavyRainRisk = heavyRainRisk,
            hailRisk = hailRisk,
            strongWindRisk = strongWindRisk,
            tornadoRisk = tornadoRisk,
            maxCape = maxCape,
            minFreezingLevel = minFreezingLevel,
            maxWindGusts = maxGusts,
            maxPrecipitation = maxPrecip,
            alerts = alerts
        )
    }

    /**
     * Hitung potensi cuaca untuk data saat ini (current weather)
     * menggunakan data hourly 24 jam ke depan.
     */
    fun calculateCurrentPotential(hourlyData: List<HourlyWeatherData>, currentWeatherCode: Int, currentWindGusts: Double): WeatherPotential {
        return calculateWeatherPotential(hourlyData, currentWeatherCode, currentWindGusts)
    }
}

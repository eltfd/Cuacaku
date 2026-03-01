package com.weather.forecast.data.repository

import android.content.Context
import android.content.SharedPreferences
import com.weather.forecast.data.api.ElevationApiService
import com.weather.forecast.data.api.RetrofitClient
import com.weather.forecast.data.api.SoilGridsApiService
import com.weather.forecast.data.model.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.withContext
import kotlin.math.atan
import kotlin.math.sqrt

/**
 * Landslide Terrain Repository
 *
 * Mengelola data terrain/topografi untuk analisis risiko longsor:
 *
 * ── Data Sources ──
 * 1. **Open-Elevation API** — Elevasi & kemiringan lereng (SRTM 30m)
 *    - Grid 5 titik (center + N/S/E/W)
 *    - Kalkulasi slope gradient dari selisih elevasi
 *    - Gratis, tanpa API key
 *
 * 2. **SoilGrids ISRIC API** — Tipe tanah (clay, sand, silt, SOC)
 *    - Global coverage, resolusi 250m
 *    - Gratis, tanpa API key
 *    - Cache 30 hari (tipe tanah relatif stabil)
 *
 * 3. **Open-Meteo Soil Data** — dari hourly weather data
 *    - soil_moisture_0_to_7cm, 7_to_28cm, 28_to_100cm
 *    - soil_temperature_0cm
 *    - Sudah termasuk di HourlyWeatherData
 *
 * 3. **Derived Data**
 *    - Soil Saturation Index — dari 3 lapisan soil moisture
 *    - Vegetation Proxy Index — estimasi dari kondisi tanah & udara
 *    - Soil Stability Index — dari komposisi clay/sand/silt
 *    - Rainfall metrics — dari hourly weather data
 *
 * ── Caching ──
 * Elevation data di-cache per lokasi (resolusi 0.01°)
 * karena topografi tidak berubah. Cache berlaku 7 hari.
 */
class LandslideTerrainRepository(context: Context) {

    private val elevationApi = RetrofitClient.elevationApi
    private val soilGridsApi = RetrofitClient.soilGridsApi
    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    companion object {
        private const val PREFS_NAME = "landslide_terrain"
        private const val KEY_CACHED_ELEVATION = "cached_elevation"
        private const val KEY_CACHED_SLOPE = "cached_slope"
        private const val KEY_CACHED_LAT = "cached_lat"
        private const val KEY_CACHED_LON = "cached_lon"
        private const val KEY_CACHE_TIME = "cache_time"

        // Soil type cache keys
        private const val KEY_CACHED_CLAY = "cached_clay"
        private const val KEY_CACHED_SAND = "cached_sand"
        private const val KEY_CACHED_SILT = "cached_silt"
        private const val KEY_CACHED_SOC = "cached_soc"
        private const val KEY_SOIL_CACHE_TIME = "soil_cache_time"
        private const val KEY_SOIL_CACHED_LAT = "soil_cached_lat"
        private const val KEY_SOIL_CACHED_LON = "soil_cached_lon"

        /** Cache elevasi selama 7 hari (topografi tidak berubah) */
        private const val ELEVATION_CACHE_MS = 7 * 24 * 60 * 60 * 1000L

        /** Cache soil type selama 30 hari (tipe tanah relatif stabil) */
        private const val SOIL_CACHE_MS = 30 * 24 * 60 * 60 * 1000L

        /** Grid spacing for slope calculation (km) */
        private const val GRID_SPACING_KM = 0.5

        /** Location change threshold for cache invalidation (degrees, ~1km) */
        private const val LOCATION_THRESHOLD = 0.01
    }

    /**
     * Mendapatkan data terrain lengkap untuk analisis longsor.
     *
     * Flow:
     * 1. Fetch/cache elevation grid → calculate slope
     * 2. Extract soil data dari hourly weather
     * 3. Calculate derived indices
     * 4. Build LandslideTerrainData
     *
     * @param latitude User latitude
     * @param longitude User longitude
     * @param hourly Hourly weather data (contains soil moisture/temp)
     * @param allDaily Daily data (for antecedent rainfall)
     * @param elevation Elevation from WeatherResponse (fallback)
     */
    suspend fun getTerrainData(
        latitude: Double,
        longitude: Double,
        hourly: List<HourlyWeatherData>,
        allDaily: List<DailyWeatherData>,
        elevation: Double = 0.0
    ): LandslideTerrainData {
        return withContext(Dispatchers.IO) {
            // 1. Elevation & slope (from cache or API)
            val slopeResult = fetchSlopeData(latitude, longitude, elevation)

            // 1b. Soil type (from cache or SoilGrids API)
            val soilTypeResult = fetchSoilType(latitude, longitude)

            // 2. Soil data from hourly weather
            val soilData = extractSoilData(hourly)

            // 3. Rainfall metrics
            val rainfallMetrics = extractRainfallMetrics(hourly, allDaily)

            // 4. Derived indices
            val saturationIndex = SoilSaturationCalculator.calculate(
                soilData.shallow, soilData.medium, soilData.deep
            )

            val avgHumidity = hourly.map { it.humidity.toDouble() }.average()
                .takeIf { !it.isNaN() } ?: 50.0

            val vegetationIndex = VegetationProxyEstimator.estimate(
                soilMoisture = soilData.shallow,
                soilTemp = soilData.temperature,
                humidity = avgHumidity
            )

            // 5. Soil stability index
            val stabilityIndex = if (soilTypeResult != null) {
                SoilStabilityCalculator.calculate(
                    soilTypeResult.clay, soilTypeResult.sand,
                    soilTypeResult.silt, soilTypeResult.soc
                )
            } else null

            LandslideTerrainData(
                elevation = slopeResult.centerElevation,
                slopeAngle = slopeResult.maxSlopeAngle,
                slopeCategory = SlopeCategory.fromAngle(slopeResult.maxSlopeAngle),
                soilMoistureShallow = soilData.shallow,
                soilMoistureMedium = soilData.medium,
                soilMoistureDeep = soilData.deep,
                soilTemperature = soilData.temperature,
                soilSaturationIndex = saturationIndex,
                vegetationIndex = vegetationIndex,
                todayPrecipitation = rainfallMetrics.todayPrecip,
                antecedentRainfall = rainfallMetrics.antecedentRain,
                maxRainfallIntensity = rainfallMetrics.maxIntensity,
                continuousRainHours = rainfallMetrics.continuousHours,
                elevationGrid = slopeResult.gridPoints,
                clayContent = soilTypeResult?.clay,
                sandContent = soilTypeResult?.sand,
                siltContent = soilTypeResult?.silt,
                soilOrganicCarbon = soilTypeResult?.soc,
                soilStabilityIndex = stabilityIndex
            )
        }
    }

    // ════════════════════════════════════════════════
    //  Elevation & Slope
    // ════════════════════════════════════════════════

    private data class SlopeResult(
        val centerElevation: Double,
        val maxSlopeAngle: Double,
        val gridPoints: List<ElevationPoint>
    )

    /**
     * Fetch elevation data dan hitung kemiringan lereng.
     *
     * Menggunakan grid 5 titik (center + 4 arah kardinal)
     * dengan jarak ~500m. Slope dihitung dari selisih elevasi
     * antara center dan masing-masing titik.
     */
    private suspend fun fetchSlopeData(
        latitude: Double,
        longitude: Double,
        fallbackElevation: Double
    ): SlopeResult {
        // Check cache
        val cached = getCachedSlope(latitude, longitude)
        if (cached != null) return cached

        return try {
            val gridPoints = ElevationApiService.generateSlopeGrid(
                latitude, longitude, GRID_SPACING_KM
            )
            val query = ElevationApiService.buildLocationsQuery(gridPoints)
            val response = elevationApi.getElevation(query)
            val results = response.results

            if (results != null && results.size >= 5) {
                val labels = listOf("Center", "N", "S", "E", "W")
                val elevationPoints = results.mapIndexed { i, r ->
                    ElevationPoint(
                        label = labels.getOrElse(i) { "?" },
                        latitude = r.latitude,
                        longitude = r.longitude,
                        elevation = r.elevation
                    )
                }

                val centerElev = results[0].elevation
                val distanceM = GRID_SPACING_KM * 1000.0 // Convert km to meters

                // Calculate slope angle from center to each cardinal point
                val slopes = (1..4).map { i ->
                    val elevDiff = kotlin.math.abs(centerElev - results[i].elevation)
                    Math.toDegrees(atan(elevDiff / distanceM))
                }

                val maxSlope = slopes.maxOrNull() ?: 0.0

                val result = SlopeResult(
                    centerElevation = centerElev,
                    maxSlopeAngle = maxSlope,
                    gridPoints = elevationPoints
                )

                // Cache result
                cacheSlope(latitude, longitude, result)
                result
            } else {
                // Fallback: use weather API elevation, assume gentle slope
                SlopeResult(
                    centerElevation = fallbackElevation,
                    maxSlopeAngle = 0.0,
                    gridPoints = emptyList()
                )
            }
        } catch (_: Exception) {
            // On error, use cached or fallback
            SlopeResult(
                centerElevation = fallbackElevation,
                maxSlopeAngle = getCachedSlopeAngle(),
                gridPoints = emptyList()
            )
        }
    }

    // ════════════════════════════════════════════════
    //  Soil Type (from SoilGrids ISRIC API)
    // ════════════════════════════════════════════════

    private data class SoilTypeResult(
        val clay: Double,  // g/kg (0-1000)
        val sand: Double,  // g/kg
        val silt: Double,  // g/kg
        val soc: Double    // dg/kg
    )

    /**
     * Fetch tipe tanah dari SoilGrids ISRIC API.
     *
     * Data di-cache 30 hari per lokasi (tipe tanah relatif stabil).
     * Mengambil rata-rata dari 3 kedalaman: 0-5cm, 5-15cm, 15-30cm
     * (sesuai zona akar dangkal yang paling relevan untuk longsor permukaan).
     *
     * @return SoilTypeResult atau null jika gagal
     */
    private suspend fun fetchSoilType(latitude: Double, longitude: Double): SoilTypeResult? {
        // Check cache
        val cached = getCachedSoilType(latitude, longitude)
        if (cached != null) return cached

        return try {
            val response = soilGridsApi.getSoilProperties(
                lat = latitude,
                lon = longitude,
                property = "clay,sand,silt,soc",
                depth = "0-5cm,5-15cm,15-30cm",
                value = "mean"
            )

            val layers = response.properties?.layers ?: return null

            // Extract mean value averaged across depths for each property
            fun getAvg(propName: String): Double {
                val layer = layers.find { it.name == propName } ?: return 0.0
                val values = layer.depths?.mapNotNull { it.values?.mean?.toDouble() } ?: return 0.0
                return if (values.isNotEmpty()) values.average() else 0.0
            }

            val result = SoilTypeResult(
                clay = getAvg("clay"),
                sand = getAvg("sand"),
                silt = getAvg("silt"),
                soc = getAvg("soc")
            )

            // Validate — clay+sand+silt should be reasonable
            val total = result.clay + result.sand + result.silt
            if (total < 100) return null  // Data too sparse or invalid

            // Cache result
            cacheSoilType(latitude, longitude, result)
            result
        } catch (_: Exception) {
            // Silently fail — soil type is supplementary data
            null
        }
    }

    private fun getCachedSoilType(latitude: Double, longitude: Double): SoilTypeResult? {
        val now = System.currentTimeMillis()
        val cacheTime = prefs.getLong(KEY_SOIL_CACHE_TIME, 0)
        if (now - cacheTime > SOIL_CACHE_MS) return null

        val cachedLat = prefs.getFloat(KEY_SOIL_CACHED_LAT, 0f).toDouble()
        val cachedLon = prefs.getFloat(KEY_SOIL_CACHED_LON, 0f).toDouble()
        if (kotlin.math.abs(latitude - cachedLat) > LOCATION_THRESHOLD ||
            kotlin.math.abs(longitude - cachedLon) > LOCATION_THRESHOLD) {
            return null
        }

        val clay = prefs.getFloat(KEY_CACHED_CLAY, Float.MIN_VALUE)
        if (clay == Float.MIN_VALUE) return null

        return SoilTypeResult(
            clay = clay.toDouble(),
            sand = prefs.getFloat(KEY_CACHED_SAND, 0f).toDouble(),
            silt = prefs.getFloat(KEY_CACHED_SILT, 0f).toDouble(),
            soc = prefs.getFloat(KEY_CACHED_SOC, 0f).toDouble()
        )
    }

    private fun cacheSoilType(latitude: Double, longitude: Double, result: SoilTypeResult) {
        prefs.edit()
            .putFloat(KEY_SOIL_CACHED_LAT, latitude.toFloat())
            .putFloat(KEY_SOIL_CACHED_LON, longitude.toFloat())
            .putFloat(KEY_CACHED_CLAY, result.clay.toFloat())
            .putFloat(KEY_CACHED_SAND, result.sand.toFloat())
            .putFloat(KEY_CACHED_SILT, result.silt.toFloat())
            .putFloat(KEY_CACHED_SOC, result.soc.toFloat())
            .putLong(KEY_SOIL_CACHE_TIME, System.currentTimeMillis())
            .apply()
    }

    // ════════════════════════════════════════════════
    //  Soil Data Extraction (from Open-Meteo hourly)
    // ════════════════════════════════════════════════

    private data class SoilData(
        val shallow: Double,   // 0-7cm
        val medium: Double,    // 7-28cm
        val deep: Double,      // 28-100cm
        val temperature: Double
    )

    private fun extractSoilData(hourly: List<HourlyWeatherData>): SoilData {
        // Average across available hours (usually 24h)
        val shallow = hourly.map { it.soilMoistureShallow }
            .filter { it > 0 }.average().takeIf { !it.isNaN() } ?: 0.0
        val medium = hourly.map { it.soilMoistureMedium }
            .filter { it > 0 }.average().takeIf { !it.isNaN() } ?: 0.0
        val deep = hourly.map { it.soilMoistureDeep }
            .filter { it > 0 }.average().takeIf { !it.isNaN() } ?: 0.0
        val temp = hourly.map { it.soilTemperature }
            .filter { it > -50.0 && it < 70.0 }.average().takeIf { !it.isNaN() } ?: 15.0

        return SoilData(shallow, medium, deep, temp)
    }

    // ════════════════════════════════════════════════
    //  Rainfall Metrics
    // ════════════════════════════════════════════════

    private data class RainfallMetrics(
        val todayPrecip: Double,
        val antecedentRain: Double,
        val maxIntensity: Double,
        val continuousHours: Int
    )

    private fun extractRainfallMetrics(
        hourly: List<HourlyWeatherData>,
        allDaily: List<DailyWeatherData>
    ): RainfallMetrics {
        val todayPrecip = hourly.sumOf { it.precipitation }
        val antecedentRain = allDaily.take(3).sumOf { it.precipitationSum }
        val maxIntensity = hourly.maxOfOrNull { it.rain + it.showers } ?: 0.0

        // Count continuous rain hours from latest
        var continuous = 0
        for (h in hourly.reversed()) {
            if (h.precipitation > 0.5) continuous++ else break
        }

        return RainfallMetrics(todayPrecip, antecedentRain, maxIntensity, continuous)
    }

    // ════════════════════════════════════════════════
    //  Caching
    // ════════════════════════════════════════════════

    private fun getCachedSlope(latitude: Double, longitude: Double): SlopeResult? {
        val now = System.currentTimeMillis()
        val cacheTime = prefs.getLong(KEY_CACHE_TIME, 0)
        if (now - cacheTime > ELEVATION_CACHE_MS) return null

        val cachedLat = prefs.getFloat(KEY_CACHED_LAT, 0f).toDouble()
        val cachedLon = prefs.getFloat(KEY_CACHED_LON, 0f).toDouble()

        // Check if location changed significantly
        if (kotlin.math.abs(latitude - cachedLat) > LOCATION_THRESHOLD ||
            kotlin.math.abs(longitude - cachedLon) > LOCATION_THRESHOLD) {
            return null
        }

        val elevation = prefs.getFloat(KEY_CACHED_ELEVATION, Float.MIN_VALUE).toDouble()
        val slope = prefs.getFloat(KEY_CACHED_SLOPE, Float.MIN_VALUE).toDouble()
        if (elevation == Float.MIN_VALUE.toDouble() && slope == Float.MIN_VALUE.toDouble()) return null

        return SlopeResult(
            centerElevation = elevation,
            maxSlopeAngle = slope,
            gridPoints = emptyList()  // Grid isn't cached for simplicity
        )
    }

    private fun cacheSlope(latitude: Double, longitude: Double, result: SlopeResult) {
        prefs.edit()
            .putFloat(KEY_CACHED_LAT, latitude.toFloat())
            .putFloat(KEY_CACHED_LON, longitude.toFloat())
            .putFloat(KEY_CACHED_ELEVATION, result.centerElevation.toFloat())
            .putFloat(KEY_CACHED_SLOPE, result.maxSlopeAngle.toFloat())
            .putLong(KEY_CACHE_TIME, System.currentTimeMillis())
            .apply()
    }

    private fun getCachedSlopeAngle(): Double {
        return prefs.getFloat(KEY_CACHED_SLOPE, 0f).toDouble()
    }
}
